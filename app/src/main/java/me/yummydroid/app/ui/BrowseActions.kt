package me.yummydroid.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import me.yummydroid.app.AuthUiState
import me.yummydroid.app.ui.components.clearFocusAfterTouch
import me.yummydroid.app.ui.theme.YummyColors
import me.yummydroid.app.ui.theme.yummyActionBorder
import me.yummydroid.app.ui.theme.yummyActionContentColor
import me.yummydroid.app.ui.theme.yummyActionSurfaceColor

// BrowseActionButtonCore
@Composable
internal fun BrowseActionIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    enabled: Boolean = true,
    badgeText: String? = null,
    focusLinks: BrowseActionFocusLinks = BrowseActionFocusLinks(),
) {
    val shape = RoundedCornerShape(8.dp)
    var focused by remember { mutableStateOf(false) }
    val inputModeManager = LocalInputModeManager.current
    val focusVisible = focused && inputModeManager.inputMode != InputMode.Touch
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        modifier = modifier
            .size(48.dp)
            .then(enabledActionModifier(enabled, shape, interactionSource, focusLinks, onClick) { focused = it }),
        color = yummyActionSurfaceColor(enabled = enabled, selected = active, focused = focusVisible),
        contentColor = yummyActionContentColor(enabled = enabled, selected = active, focused = focusVisible),
        border = yummyActionBorder(enabled = enabled, selected = active, focused = focusVisible),
        shape = shape,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(27.dp))
            if (enabled && badgeText != null) BrowseActionBadge(badgeText)
        }
    }
}

private fun enabledActionModifier(
    enabled: Boolean,
    shape: RoundedCornerShape,
    interactionSource: MutableInteractionSource,
    focusLinks: BrowseActionFocusLinks,
    onClick: () -> Unit,
    onFocusChanged: (Boolean) -> Unit,
): Modifier {
    if (!enabled) return Modifier.clip(shape)
    return Modifier
        .onFocusChanged { onFocusChanged(it.isFocused || it.hasFocus) }
        .clearFocusAfterTouch()
        .clip(shape)
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick,
        )
        .previewKeyHandling(focusLinks)
}

private fun Modifier.previewKeyHandling(focusLinks: BrowseActionFocusLinks): Modifier {
    if (!focusLinks.hasCustomKeyHandling) return this
    return onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        when (event.key) {
            Key.DirectionLeft -> focusLinks.leftFocusRequester.requestOr(focusLinks.consumeHorizontalEdgeKey)
            Key.DirectionRight -> focusLinks.rightFocusRequester.requestOr(focusLinks.consumeHorizontalEdgeKey)
            Key.DirectionUp -> focusLinks.upFocusRequester.requestOr(false)
            Key.DirectionDown -> focusLinks.downFocusRequester.requestOr(focusLinks.consumeDownKey)
            else -> false
        }
    }
}

private fun FocusRequester?.requestOr(fallback: Boolean): Boolean {
    return if (this == null) fallback else requestFocusSafely()
}

@Composable
private fun BoxScope.BrowseActionBadge(text: String) {
    Surface(
        color = YummyColors.offline,
        contentColor = MaterialTheme.colorScheme.onSecondary,
        shape = RoundedCornerShape(50),
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(top = 4.dp, end = 4.dp)
            .widthIn(min = 16.dp)
            .height(16.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 3.dp),
        )
    }
}

// BrowseActionFocus
internal enum class BrowseAction {
    Search,
    Filters,
    Downloads,
    Settings,
    Profile,
}

internal data class BrowseActionFocusLinks(
    val leftFocusRequester: FocusRequester? = null,
    val rightFocusRequester: FocusRequester? = null,
    val upFocusRequester: FocusRequester? = null,
    val downFocusRequester: FocusRequester? = null,
    val consumeDownKey: Boolean = false,
    val consumeHorizontalEdgeKey: Boolean = false,
) {
    val hasCustomKeyHandling: Boolean
        get() = leftFocusRequester != null ||
            rightFocusRequester != null ||
            upFocusRequester != null ||
            downFocusRequester != null ||
            consumeDownKey ||
            consumeHorizontalEdgeKey
}

internal data class BrowseActionFocusOptions(
    val entryFocusRequester: FocusRequester?,
    val upFocusRequester: FocusRequester?,
    val downFocusRequester: FocusRequester?,
    val consumeUpWhenNoRequester: Boolean,
    val consumeDownWhenNoRequester: Boolean,
    val consumeHorizontalEdgesWhenNoRequester: Boolean,
)

internal class BrowseActionNavigation(
    private val requesters: List<FocusRequester>,
    private val enabledActions: List<BrowseAction>,
    private val options: BrowseActionFocusOptions,
    private val focusedActionIndex: MutableIntState,
) {
    fun actionModifier(action: BrowseAction): Modifier {
        return Modifier
            .onFocusChanged { focusState -> updateFocusedAction(action, focusState.isFocused || focusState.hasFocus) }
            .focusRequester(actionRequester(action))
            .exitDownFocus()
    }

    fun focusLinks(action: BrowseAction): BrowseActionFocusLinks {
        return BrowseActionFocusLinks(
            leftFocusRequester = adjacentActionRequester(action, -1),
            rightFocusRequester = adjacentActionRequester(action, 1),
            upFocusRequester = options.upFocusRequester,
            downFocusRequester = options.downFocusRequester,
            consumeDownKey = options.downFocusRequester == null && options.consumeDownWhenNoRequester,
            consumeHorizontalEdgeKey = options.consumeHorizontalEdgesWhenNoRequester,
        )
    }

    fun containerModifier(modifier: Modifier): Modifier {
        val consumeUp = options.upFocusRequester == null && options.consumeUpWhenNoRequester
        if (!consumeUp && !options.consumeHorizontalEdgesWhenNoRequester) return modifier
        return modifier.onPreviewKeyEvent { event ->
            event.type == KeyEventType.KeyDown && consumeContainerKey(event.key, consumeUp)
        }
    }

    private fun consumeContainerKey(key: Key, consumeUp: Boolean): Boolean {
        return when (key) {
            Key.DirectionUp -> consumeUp && isFocusedActionEnabled()
            Key.DirectionLeft -> options.consumeHorizontalEdgesWhenNoRequester && isFocusedAtEdge(first = true)
            Key.DirectionRight -> options.consumeHorizontalEdgesWhenNoRequester && isFocusedAtEdge(first = false)
            else -> false
        }
    }

    private fun isFocusedAtEdge(first: Boolean): Boolean {
        val edge = if (first) enabledActions.firstOrNull() else enabledActions.lastOrNull()
        return focusedActionIndex.intValue == edge?.ordinal
    }

    private fun isFocusedActionEnabled(): Boolean {
        return enabledActions.any { it.ordinal == focusedActionIndex.intValue }
    }

    private fun updateFocusedAction(action: BrowseAction, focused: Boolean) {
        if (focused) {
            focusedActionIndex.intValue = action.ordinal
        } else if (focusedActionIndex.intValue == action.ordinal) {
            focusedActionIndex.intValue = -1
        }
    }

    private fun actionRequester(action: BrowseAction): FocusRequester {
        val entryAction = enabledActions.firstOrNull() ?: BrowseAction.Downloads
        return if (entryAction == action && options.entryFocusRequester != null) {
            options.entryFocusRequester
        } else {
            requesters[action.ordinal]
        }
    }

    private fun adjacentActionRequester(action: BrowseAction, delta: Int): FocusRequester? {
        val position = enabledActions.indexOf(action)
        if (position < 0) return null
        return enabledActions.getOrNull(position + delta)?.let(::actionRequester)
    }

    private fun Modifier.exitDownFocus(): Modifier {
        val requester = options.downFocusRequester ?: return this
        return focusProperties { down = requester }
    }
}

// BrowseActionLayout
internal data class BrowseActionBarState(
    val auth: AuthUiState,
    val activeFilters: Int,
    val activeSearch: Boolean,
    val activeFiltersPanel: Boolean,
    val activeSettings: Boolean,
    val activeDownloads: Boolean,
    val activeProfile: Boolean,
    val activeDownloadCount: Int,
    val searchEnabled: Boolean,
    val filtersEnabled: Boolean,
)

internal data class BrowseActionCallbacks(
    val onOpenSearch: () -> Unit,
    val onOpenFilters: () -> Unit,
    val onOpenSettings: () -> Unit,
    val onOpenDownloads: () -> Unit,
    val onOpenLogin: () -> Unit,
    val onOpenProfile: () -> Unit,
)

internal data class BrowseActionLayout(
    val modifier: Modifier,
    val spreadActions: Boolean,
    val stackActions: Boolean,
)

private data class BrowseActionPresentation(
    val icon: ImageVector,
    val contentDescription: String,
    val onClick: () -> Unit,
    val active: Boolean,
    val enabled: Boolean = true,
    val badgeText: String? = null,
)

private val StackedBrowseActionRows = listOf(
    listOf(BrowseAction.Search, BrowseAction.Filters, BrowseAction.Downloads),
    listOf(BrowseAction.Settings, BrowseAction.Profile),
)

@Composable
internal fun BrowseChromeActions(
    state: BrowseHomeChromeState,
    callbacks: BrowseActionCallbacks,
    modifier: Modifier = Modifier,
    spreadActions: Boolean = false,
    stackActions: Boolean = false,
    entryFocusRequester: FocusRequester? = null,
    upFocusRequester: FocusRequester? = null,
    downFocusRequester: FocusRequester? = null,
    consumeUpWhenNoRequester: Boolean = false,
    consumeDownWhenNoRequester: Boolean = false,
    consumeHorizontalEdgesWhenNoRequester: Boolean = false,
) {
    BrowseTopBarActionsContent(
        state = BrowseActionBarState(
            auth = state.auth,
            activeFilters = state.activeFilters,
            activeSearch = state.activeSearch,
            activeFiltersPanel = state.activeFiltersPanel,
            activeSettings = state.activeSettings,
            activeDownloads = state.activeDownloads,
            activeProfile = state.activeProfile,
            activeDownloadCount = state.activeDownloadCount,
            searchEnabled = state.catalogActionsEnabled,
            filtersEnabled = state.catalogActionsEnabled,
        ),
        callbacks = callbacks,
        layout = BrowseActionLayout(
            modifier = modifier,
            spreadActions = spreadActions,
            stackActions = stackActions,
        ),
        focus = BrowseActionFocusOptions(
            entryFocusRequester = entryFocusRequester,
            upFocusRequester = upFocusRequester,
            downFocusRequester = downFocusRequester,
            consumeUpWhenNoRequester = consumeUpWhenNoRequester,
            consumeDownWhenNoRequester = consumeDownWhenNoRequester,
            consumeHorizontalEdgesWhenNoRequester = consumeHorizontalEdgesWhenNoRequester,
        ),
    )
}

@Composable
internal fun BrowseTopBarActionsContent(
    state: BrowseActionBarState,
    callbacks: BrowseActionCallbacks,
    layout: BrowseActionLayout,
    focus: BrowseActionFocusOptions,
) {
    val requesters = remember { List(BrowseAction.entries.size) { FocusRequester() } }
    val focusedActionIndex = remember { mutableIntStateOf(-1) }
    val enabledActions = remember(state.searchEnabled, state.filtersEnabled) {
        BrowseAction.entries.filter { action ->
            action != BrowseAction.Search || state.searchEnabled
        }.filter { action ->
            action != BrowseAction.Filters || state.filtersEnabled
        }
    }
    val navigation = BrowseActionNavigation(requesters, enabledActions, focus, focusedActionIndex)

    if (layout.stackActions) {
        StackedBrowseActions(state, callbacks, layout.modifier, navigation)
    } else {
        InlineBrowseActions(state, callbacks, layout, navigation)
    }
}

@Composable
private fun StackedBrowseActions(
    state: BrowseActionBarState,
    callbacks: BrowseActionCallbacks,
    modifier: Modifier,
    navigation: BrowseActionNavigation,
) {
    Column(
        modifier = navigation.containerModifier(modifier),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        StackedBrowseActionRows.forEach { actions ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                BrowseActionItems(actions, state, callbacks, navigation)
            }
        }
    }
}

@Composable
private fun InlineBrowseActions(
    state: BrowseActionBarState,
    callbacks: BrowseActionCallbacks,
    layout: BrowseActionLayout,
    navigation: BrowseActionNavigation,
) {
    Row(
        modifier = navigation.containerModifier(layout.modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (layout.spreadActions) Arrangement.SpaceBetween else Arrangement.spacedBy(10.dp),
    ) {
        BrowseActionItems(BrowseAction.entries, state, callbacks, navigation)
    }
}

@Composable
private fun BrowseActionItems(
    actions: List<BrowseAction>,
    state: BrowseActionBarState,
    callbacks: BrowseActionCallbacks,
    navigation: BrowseActionNavigation,
) {
    actions.forEach { action -> BrowseActionItem(action, state, callbacks, navigation) }
}

@Composable
private fun BrowseActionItem(
    action: BrowseAction,
    state: BrowseActionBarState,
    callbacks: BrowseActionCallbacks,
    navigation: BrowseActionNavigation,
) {
    val presentation = browseActionPresentation(action, state, callbacks)
    BrowseActionIconButton(
        icon = presentation.icon,
        contentDescription = presentation.contentDescription,
        onClick = presentation.onClick,
        modifier = navigation.actionModifier(action),
        active = presentation.active,
        enabled = presentation.enabled,
        badgeText = presentation.badgeText,
        focusLinks = navigation.focusLinks(action),
    )
}

@Composable
private fun browseActionPresentation(
    action: BrowseAction,
    state: BrowseActionBarState,
    callbacks: BrowseActionCallbacks,
): BrowseActionPresentation = when (action) {
    BrowseAction.Search -> BrowseActionPresentation(
        icon = Icons.Default.Search,
        contentDescription = uiText(UiStringKey.Search),
        onClick = callbacks.onOpenSearch,
        active = state.searchEnabled && state.activeSearch,
        enabled = state.searchEnabled,
    )
    BrowseAction.Filters -> BrowseActionPresentation(
        icon = Icons.Default.FilterList,
        contentDescription = uiText(UiStringKey.Filters),
        onClick = callbacks.onOpenFilters,
        active = state.filtersEnabled && (state.activeFilters > 0 || state.activeFiltersPanel),
        enabled = state.filtersEnabled,
        badgeText = state.activeFilters
            .takeIf { state.filtersEnabled && it > 0 }
            ?.coerceAtMost(9)
            ?.toString(),
    )
    BrowseAction.Downloads -> BrowseActionPresentation(
        icon = Icons.Default.Download,
        contentDescription = uiText(UiStringKey.Downloads),
        onClick = callbacks.onOpenDownloads,
        active = state.activeDownloadCount > 0 || state.activeDownloads,
        badgeText = state.activeDownloadCount.takeIf { it > 0 }?.let { count ->
            if (count > 9) "9+" else count.toString()
        },
    )
    BrowseAction.Settings -> BrowseActionPresentation(
        icon = Icons.Default.Settings,
        contentDescription = uiText(UiStringKey.Settings),
        onClick = callbacks.onOpenSettings,
        active = state.activeSettings,
    )
    BrowseAction.Profile -> BrowseActionPresentation(
        icon = Icons.Default.AccountCircle,
        contentDescription = if (state.auth.profile == null) {
            uiText(UiStringKey.SignIn)
        } else {
            uiText(UiStringKey.Profile)
        },
        onClick = if (state.auth.profile == null) callbacks.onOpenLogin else callbacks.onOpenProfile,
        active = state.activeProfile,
        badgeText = state.auth.profile?.unreadNotifications?.notificationBadgeText(),
    )
}
