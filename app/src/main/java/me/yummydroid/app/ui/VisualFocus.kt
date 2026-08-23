package me.yummydroid.app.ui

import androidx.compose.foundation.focusGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusProperties
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.yummydroid.app.InputAction
import me.yummydroid.app.InputActionEvent

// DpadFocusPolicy
internal fun InputActionEvent.shouldInitializeFocusBeforePlatformDispatch(
    layerHadPointerInput: Boolean,
    touchInputMode: Boolean,
): Boolean {
    if (action !in DpadFocusActions) return false
    return followsPointerInput || layerHadPointerInput || touchInputMode
}

private val DpadFocusActions = setOf(
    InputAction.Up,
    InputAction.Down,
    InputAction.Left,
    InputAction.Right,
    InputAction.Confirm,
)

// FocusRequesterExtensions
internal fun FocusRequester.requestFocusSafely(): Boolean {
    return runCatching { requestFocus() }.getOrDefault(false)
}

// UiControlCoordinator
internal enum class UiControlOperation(
    internal val channel: Channel,
    internal val mode: Mode,
) {
    NavigationLatest(Channel.Navigation, Mode.Latest),
    NavigationSerial(Channel.Navigation, Mode.Serial),
    PageTransitionLatest(Channel.PageTransition, Mode.Latest),
    ContentScrollLatest(Channel.ContentScroll, Mode.Latest),
    RelocationLatest(Channel.Relocation, Mode.Latest),
    InputModeLatest(Channel.InputMode, Mode.Latest),
    PlaybackLatest(Channel.Playback, Mode.Latest),
    ;

    internal enum class Channel(val interactive: Boolean) {
        Navigation(true),
        PageTransition(true),
        ContentScroll(true),
        Relocation(true),
        InputMode(true),
        Playback(false),
    }
    internal enum class Mode { Latest, Serial }
}

internal class UiControlCoordinator {
    private data class RunningOperation(val owner: Any, val job: Job)

    private val runningOperations = mutableMapOf<UiControlOperation.Channel, RunningOperation>()
    private val channelLocks = UiControlOperation.Channel.entries.associateWith { Mutex() }

    @Synchronized
    fun isActive(operation: UiControlOperation): Boolean {
        return runningOperations[operation.channel]?.job?.isActive == true
    }

    @Synchronized
    fun launch(
        scope: CoroutineScope,
        owner: Any,
        operation: UiControlOperation,
        block: suspend () -> Unit,
    ): Boolean {
        val running = runningOperations[operation.channel]
        if (
            operation.mode == UiControlOperation.Mode.Serial &&
            running?.job?.isActive == true &&
            running.owner === owner
        ) {
            return false
        }
        running?.job?.cancel()

        lateinit var launched: Job
        launched = scope.launch(start = CoroutineStart.LAZY) {
            channelLocks.getValue(operation.channel).withLock { block() }
        }
        runningOperations[operation.channel] = RunningOperation(owner, launched)
        launched.invokeOnCompletion {
            synchronized(this) {
                if (runningOperations[operation.channel]?.job === launched) {
                    runningOperations.remove(operation.channel)
                }
            }
        }
        launched.start()
        return true
    }

    @Synchronized
    fun cancel(owner: Any, operation: UiControlOperation) {
        val running = runningOperations[operation.channel] ?: return
        if (running.owner !== owner) return
        runningOperations.remove(operation.channel)
        running.job.cancel()
    }

    @Synchronized
    fun cancel(operation: UiControlOperation) {
        runningOperations.remove(operation.channel)?.job?.cancel()
    }

    @Synchronized
    fun cancelAll() {
        val jobs = runningOperations.values.map(RunningOperation::job)
        runningOperations.clear()
        jobs.forEach(Job::cancel)
    }

    @Synchronized
    fun cancelInteractive() {
        val jobs = UiControlOperation.Channel.entries
            .filter(UiControlOperation.Channel::interactive)
            .mapNotNull { channel -> runningOperations.remove(channel)?.job }
        jobs.forEach(Job::cancel)
    }
}

internal val LocalUiControlCoordinator = staticCompositionLocalOf<UiControlCoordinator> {
    error("UiControlCoordinator is not provided")
}

internal val LocalUiControlEffectsEnabled = staticCompositionLocalOf { true }

internal fun shouldRunUiControlEffect(
    layerEnabled: Boolean,
    effectEnabled: Boolean,
): Boolean = layerEnabled && effectEnabled

@Composable
internal fun UiControlEffect(
    vararg keys: Any?,
    operation: UiControlOperation = UiControlOperation.NavigationLatest,
    enabled: Boolean = true,
    block: suspend () -> Unit,
) {
    val uiControls = LocalUiControlCoordinator.current
    val layerEnabled = LocalUiControlEffectsEnabled.current
    val shouldRun = shouldRunUiControlEffect(layerEnabled, enabled)
    val scope = rememberCoroutineScope()
    val owner = remember { Any() }
    val currentBlock by rememberUpdatedState(block)

    LaunchedEffect(uiControls, operation, shouldRun, *keys) {
        if (shouldRun) {
            uiControls.launch(scope, owner, operation) { currentBlock() }
        } else {
            uiControls.cancel(owner, operation)
        }
    }
    DisposableEffect(uiControls, owner, operation) {
        onDispose { uiControls.cancel(owner, operation) }
    }
}

// VisualFocusModifiers
internal fun Modifier.visualFocusGridItem(
    state: VisualFocusGridState,
    index: Int,
    horizontal: Boolean = true,
    vertical: Boolean = false,
    leftExit: FocusRequester? = null,
    rightExit: FocusRequester? = null,
    upExit: FocusRequester? = null,
    downExit: FocusRequester? = null,
    blockKey: Any? = null,
    blockEntryIndex: Int = index,
    consumeDisabledAxis: Boolean = false,
    blockedDirections: Set<VisualGridDirection> = emptySet(),
    focusKey: Any? = null,
): Modifier {
    val configuration = VisualFocusItemConfiguration(
        navigation = VisualFocusNavigationPolicy(
            horizontal = horizontal,
            vertical = vertical,
            blockedDirections = blockedDirections.toSet(),
            consumeDisabledAxis = consumeDisabledAxis,
        ),
        leftExit = leftExit,
        rightExit = rightExit,
        upExit = upExit,
        downExit = downExit,
        blockKey = blockKey,
        blockEntryIndex = blockEntryIndex,
        focusKey = focusKey,
    )
    return then(Modifier.visualFocusGridItemModifier(state, index, configuration))
}

internal fun Modifier.visualFocusGridItemIfPresent(
    state: VisualFocusGridState?,
    index: Int,
    blockKey: Any? = null,
    blockEntryIndex: Int = index,
): Modifier {
    if (state == null) return this
    return visualFocusGridItem(
        state = state,
        index = index,
        horizontal = true,
        vertical = true,
        blockKey = blockKey,
        blockEntryIndex = blockEntryIndex,
    )
}

private fun Modifier.visualFocusGridItemModifier(
    state: VisualFocusGridState,
    index: Int,
    configuration: VisualFocusItemConfiguration,
): Modifier {
    return composed {
        val requester = state.requester(index) ?: return@composed Modifier
        DisposableEffect(state, index) {
            onDispose { state.clearBounds(index) }
        }
        Modifier
            .focusRequester(requester)
            .onFocusChanged { focusState ->
                state.updateFocusedIndex(
                    index = index,
                    focused = focusState.isFocused || focusState.hasFocus,
                )
            }
            .onGloballyPositioned { coordinates ->
                val rect = coordinates.boundsInWindow(clipBounds = false)
                state.updateBounds(
                    index = index,
                    bounds = configuration.toBounds(index, rect),
                    coordinates = coordinates,
                )
            }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) {
                    return@onPreviewKeyEvent false
                }
                handleManagedDpadNavigationKey(
                    key = event.key,
                    ownsDirection = configuration.navigation::owns,
                ) { direction ->
                    if (configuration.navigation.canNavigate(direction)) {
                        state.requestFocusTarget(
                            index = index,
                            direction = direction,
                            exit = configuration.exit(direction),
                        )
                    }
                }
            }
    }
}

internal fun virtualBlockEntryToMaterialize(
    source: VisualFocusBounds?,
    target: VisualFocusBounds?,
    direction: VisualGridDirection,
    materializedEntryIndex: Int?,
    entryHasBounds: Boolean,
): Int? {
    if (!direction.isVertical()) return null
    val targetBlockKey = target?.blockKey ?: return null
    if (source?.blockKey == targetBlockKey) return null
    val entryIndex = materializedEntryIndex ?: return null
    return entryIndex.takeIf { it != target.index && !entryHasBounds }
}

internal fun Modifier.focusEntryGroup(entry: FocusRequester?): Modifier {
    if (entry == null) return focusGroup()
    return focusProperties {
        onEnter = { entry.requestFocusSafely() }
    }.focusGroup()
}

internal data class VisualFocusNavigationPolicy(
    val horizontal: Boolean,
    val vertical: Boolean,
    val blockedDirections: Set<VisualGridDirection> = emptySet(),
    val consumeDisabledAxis: Boolean = false,
) {
    fun canNavigate(direction: VisualGridDirection): Boolean {
        if (direction in blockedDirections) return false
        return when (direction) {
            VisualGridDirection.Left,
            VisualGridDirection.Right -> horizontal
            VisualGridDirection.Up,
            VisualGridDirection.Down -> vertical
        }
    }

    fun owns(direction: VisualGridDirection): Boolean {
        return direction in blockedDirections || canNavigate(direction) || consumeDisabledAxis
    }
}

private data class VisualFocusItemConfiguration(
    val navigation: VisualFocusNavigationPolicy,
    val leftExit: FocusRequester?,
    val rightExit: FocusRequester?,
    val upExit: FocusRequester?,
    val downExit: FocusRequester?,
    val blockKey: Any?,
    val blockEntryIndex: Int,
    val focusKey: Any?,
) {
    fun exit(direction: VisualGridDirection): FocusRequester? {
        return when (direction) {
            VisualGridDirection.Left -> leftExit
            VisualGridDirection.Right -> rightExit
            VisualGridDirection.Up -> upExit
            VisualGridDirection.Down -> downExit
        }
    }

    fun toBounds(index: Int, rect: Rect): VisualFocusBounds {
        return VisualFocusBounds(
            index = index,
            left = rect.left,
            top = rect.top,
            right = rect.right,
            bottom = rect.bottom,
            blockKey = blockKey,
            blockEntryIndex = blockEntryIndex,
            horizontal = navigation.horizontal,
            vertical = navigation.vertical,
            consumeDisabledAxis = navigation.consumeDisabledAxis,
            focusKey = focusKey ?: blockKey?.let {
                VisualFocusRestoreKey(it, blockEntryIndex)
            },
        )
    }
}

private data class VisualFocusRestoreKey(
    val blockKey: Any,
    val blockEntryIndex: Int,
)

// VisualFocusRetentionState
internal class VisualFocusRetentionState(private val size: Int) {
    private val focusedIndexState = mutableIntStateOf(-1)
    private val lastFocusedIndexState = mutableIntStateOf(-1)
    private val lastFocusedKeyState = mutableStateOf<Any?>(null)

    val focusedIndex: Int? get() = focusedIndexState.intValue.takeIf(::contains)
    val lastFocusedIndex: Int? get() = lastFocusedIndexState.intValue.takeIf(::contains)
    val lastFocusedKey: Any? get() = lastFocusedKeyState.value

    fun focus(index: Int, focusKey: Any?) {
        focusedIndexState.intValue = index
        lastFocusedIndexState.intValue = index
        lastFocusedKeyState.value = focusKey
    }

    fun clearFocusedIndex(index: Int) {
        if (focusedIndexState.intValue == index) {
            focusedIndexState.intValue = -1
        }
    }

    fun updateLastFocusedKey(focusKey: Any) {
        lastFocusedKeyState.value = focusKey
    }

    private fun contains(index: Int): Boolean = index in 0 until size
}

// VisualFocusTargetRegistry
internal class VisualFocusTargetRegistry(
    size: Int,
    private val allowLoosePerpendicularMatch: Boolean,
) {
    private val targets = List(size) { VisualFocusTargetSlot() }
    private val blockEntryMaterializers = mutableMapOf<Any, VisualFocusBlockEntryMaterializer>()
    private var nextMaterializerRegistrationId = 0L

    val size: Int get() = targets.size

    fun contains(index: Int): Boolean = index in targets.indices

    fun requester(index: Int): FocusRequester? = targets.getOrNull(index)?.requester

    fun bounds(index: Int): VisualFocusBounds? = targets.getOrNull(index)?.bounds

    fun hasBounds(index: Int): Boolean = bounds(index) != null

    fun updateBounds(
        index: Int,
        bounds: VisualFocusBounds,
        coordinates: LayoutCoordinates,
    ): Any? {
        val target = targets.getOrNull(index) ?: return null
        target.coordinates = coordinates
        if (target.bounds == bounds) return null
        target.bounds = bounds
        return bounds.focusKey
    }

    fun clearBounds(index: Int) {
        targets.getOrNull(index)?.clearLayout()
    }

    fun registerBlockEntryMaterializer(
        blockKey: Any,
        entryIndex: Int,
        materialize: () -> Unit,
    ): Long {
        val registrationId = ++nextMaterializerRegistrationId
        blockEntryMaterializers[blockKey] = VisualFocusBlockEntryMaterializer(
            registrationId = registrationId,
            entryIndex = entryIndex,
            materialize = materialize,
        )
        return registrationId
    }

    fun unregisterBlockEntryMaterializer(blockKey: Any, registrationId: Long): Boolean {
        val registered = blockEntryMaterializers[blockKey]
        if (registered?.registrationId != registrationId) return false
        blockEntryMaterializers.remove(blockKey)
        return true
    }

    fun focusTarget(
        index: Int,
        direction: VisualGridDirection,
        exit: FocusRequester?,
    ): FocusRequester? {
        val target = focusTargetIndex(index, direction)
        return when {
            target != null -> requester(target)
            exit != null -> exit
            else -> null
        }
    }

    fun requestFocusTarget(
        index: Int,
        direction: VisualGridDirection,
        exit: FocusRequester?,
    ): VisualFocusRequestResult {
        val target = focusTargetIndex(index, direction)
        return when {
            target != null -> requestResolvedTarget(index, target, direction)
            exit != null -> if (exit.requestFocusSafely()) VisualFocusRequestResult.Focused else VisualFocusRequestResult.Failed
            contains(index) -> VisualFocusRequestResult.Consumed
            else -> VisualFocusRequestResult.Failed
        }
    }

    private fun requestResolvedTarget(
        sourceIndex: Int,
        targetIndex: Int,
        direction: VisualGridDirection,
    ): VisualFocusRequestResult {
        val source = bounds(sourceIndex)
        val target = bounds(targetIndex)
        val materializer = target?.blockKey?.let(blockEntryMaterializers::get)
        val materializedEntryIndex = virtualBlockEntryToMaterialize(
            source = source,
            target = target,
            direction = direction,
            materializedEntryIndex = materializer?.entryIndex,
            entryHasBounds = materializer?.let { entry -> hasBounds(entry.entryIndex) } == true,
        )
        if (materializer != null && materializedEntryIndex != null) {
            materializer.materialize()
            return VisualFocusRequestResult.Materializing(materializedEntryIndex)
        }
        return if (requester(targetIndex)?.requestFocusSafely() == true) {
            VisualFocusRequestResult.Focused
        } else {
            VisualFocusRequestResult.Failed
        }
    }

    fun requestFirstAvailableFocus(): Boolean {
        return availableFocusIndexes().any { index ->
            requester(index)?.requestFocusSafely() == true
        }
    }

    fun requestFocusByKey(focusKey: Any?): Boolean? {
        if (focusKey == null) return null
        val target = currentBounds().firstOrNull { bounds -> bounds.focusKey == focusKey }
            ?: return false
        return requestFocusAt(target.index)
    }

    fun requestFocusAt(index: Int): Boolean {
        if (!contains(index) || !hasBounds(index)) return false
        return requester(index)?.requestFocusSafely() == true
    }

    private fun availableFocusIndexes(): List<Int> {
        return currentBounds()
            .sortedWith(compareBy<VisualFocusBounds> { it.top }.thenBy { it.left })
            .map { it.index }
            .ifEmpty { targets.indexesWithBounds() }
            .ifEmpty { targets.indices.toList() }
    }

    private fun focusTargetIndex(index: Int, direction: VisualGridDirection): Int? {
        return visualFocusDirectionalTarget(
            bounds = currentBounds(),
            sourceIndex = index,
            direction = direction,
            allowLoosePerpendicularMatch = allowLoosePerpendicularMatch,
        )
    }

    private fun currentBounds(): Collection<VisualFocusBounds> {
        return targets.mapIndexedNotNull { index, target ->
            val bounds = target.bounds ?: return@mapIndexedNotNull null
            currentBounds(target, bounds).copy(index = index).takeIf { it.hasUsableSize() }
        }
    }

    private fun currentBounds(target: VisualFocusTargetSlot, bounds: VisualFocusBounds): VisualFocusBounds {
        val itemCoordinates = target.coordinates ?: return bounds
        return runCatching {
            val rect = itemCoordinates.boundsInWindow(clipBounds = false)
            bounds.copy(
                left = rect.left,
                top = rect.top,
                right = rect.right,
                bottom = rect.bottom,
            )
        }.getOrDefault(bounds)
    }

}

internal sealed interface VisualFocusRequestResult {
    data object Focused : VisualFocusRequestResult
    data object Consumed : VisualFocusRequestResult
    data object Failed : VisualFocusRequestResult
    data class Materializing(val targetIndex: Int) : VisualFocusRequestResult
}

private data class VisualFocusBlockEntryMaterializer(
    val registrationId: Long,
    val entryIndex: Int,
    val materialize: () -> Unit,
)

private fun VisualGridDirection.isVertical(): Boolean {
    return this == VisualGridDirection.Up || this == VisualGridDirection.Down
}

private class VisualFocusTargetSlot(
    val requester: FocusRequester = FocusRequester(),
    var bounds: VisualFocusBounds? = null,
    var coordinates: LayoutCoordinates? = null,
) {
    fun clearLayout() {
        bounds = null
        coordinates = null
    }
}

private fun List<VisualFocusTargetSlot>.indexesWithBounds(): List<Int> {
    return indices.filter { index -> this[index].bounds != null }
}
