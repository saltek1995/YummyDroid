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

private const val BrowseSearchActionIndex = 0
private const val BrowseFiltersActionIndex = 1
private const val BrowseDownloadsActionIndex = 2
private const val BrowseSettingsActionIndex = 3
private const val BrowseProfileActionIndex = 4
private const val BrowseActionButtonCount = 5

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

@Composable
internal fun BrowseTopBarActions(
    onOpenSearch: () -> Unit,
    onOpenFilters: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDownloads: () -> Unit,
    auth: AuthUiState,
    activeFilters: Int,
    activeSearch: Boolean,
    activeFiltersPanel: Boolean = false,
    activeSettings: Boolean = false,
    activeDownloads: Boolean = false,
    activeProfile: Boolean = false,
    activeDownloadCount: Int,
    searchEnabled: Boolean = true,
    filtersEnabled: Boolean = true,
    onOpenLogin: () -> Unit,
    onOpenProfile: () -> Unit,
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
    val actionFocusRequesters = remember { List(BrowseActionButtonCount) { FocusRequester() } }
    var focusedActionIndex by remember { mutableIntStateOf(-1) }
    val visibleActiveFilters = if (filtersEnabled) activeFilters else 0
    val visibleActiveSearch = searchEnabled && activeSearch
    val visibleFiltersPanel = filtersEnabled && activeFiltersPanel
    val enabledActionIndexes = remember(searchEnabled, filtersEnabled) {
        buildList {
            if (searchEnabled) add(BrowseSearchActionIndex)
            if (filtersEnabled) add(BrowseFiltersActionIndex)
            add(BrowseDownloadsActionIndex)
            add(BrowseSettingsActionIndex)
            add(BrowseProfileActionIndex)
        }
    }
    val entryActionIndex = enabledActionIndexes.firstOrNull() ?: BrowseDownloadsActionIndex
    fun actionRequester(actionIndex: Int): FocusRequester {
        return if (entryActionIndex == actionIndex && entryFocusRequester != null) {
            entryFocusRequester
        } else {
            actionFocusRequesters[actionIndex]
        }
    }
    fun adjacentActionRequester(actionIndex: Int, delta: Int): FocusRequester? {
        val currentPosition = enabledActionIndexes.indexOf(actionIndex)
        if (currentPosition < 0) return null
        return enabledActionIndexes
            .getOrNull(currentPosition + delta)
            ?.let(::actionRequester)
    }
    fun Modifier.exitDownFocus(): Modifier {
        val requester = downFocusRequester ?: return this
        return focusProperties { down = requester }
    }
    fun Modifier.actionEdgeGuards(): Modifier {
        val firstActionIndex = enabledActionIndexes.firstOrNull()
        val lastActionIndex = enabledActionIndexes.lastOrNull()
        val consumeActionUp = upFocusRequester == null && consumeUpWhenNoRequester
        if (!consumeActionUp && !consumeHorizontalEdgesWhenNoRequester) return this
        return onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (event.key) {
                Key.DirectionUp -> consumeActionUp && focusedActionIndex in enabledActionIndexes
                Key.DirectionLeft -> consumeHorizontalEdgesWhenNoRequester && focusedActionIndex == firstActionIndex
                Key.DirectionRight -> consumeHorizontalEdgesWhenNoRequester && focusedActionIndex == lastActionIndex
                else -> false
            }
        }
    }
    val consumeActionDown = downFocusRequester == null && consumeDownWhenNoRequester
    val consumeActionHorizontalEdge = consumeHorizontalEdgesWhenNoRequester

    fun actionModifier(actionIndex: Int): Modifier {
        return Modifier
            .onFocusChanged { focusState ->
                if (focusState.isFocused || focusState.hasFocus) {
                    focusedActionIndex = actionIndex
                } else if (focusedActionIndex == actionIndex) {
                    focusedActionIndex = -1
                }
            }
            .focusRequester(actionRequester(actionIndex))
            .exitDownFocus()
    }

    fun actionFocusLinks(actionIndex: Int): BrowseActionFocusLinks {
        return BrowseActionFocusLinks(
            leftFocusRequester = adjacentActionRequester(actionIndex, -1),
            rightFocusRequester = adjacentActionRequester(actionIndex, 1),
            upFocusRequester = upFocusRequester,
            downFocusRequester = downFocusRequester,
            consumeDownKey = consumeActionDown,
            consumeHorizontalEdgeKey = consumeActionHorizontalEdge,
        )
    }

    if (stackActions) {
        Column(
            modifier = modifier.actionEdgeGuards(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                BrowseSearchActionButton(
                    visibleActiveSearch,
                    searchEnabled,
                    onOpenSearch,
                    actionModifier(BrowseSearchActionIndex),
                    focusLinks = actionFocusLinks(BrowseSearchActionIndex),
                )
                BrowseFiltersActionButton(
                    visibleActiveFilters,
                    visibleFiltersPanel,
                    filtersEnabled,
                    onOpenFilters,
                    actionModifier(BrowseFiltersActionIndex),
                    focusLinks = actionFocusLinks(BrowseFiltersActionIndex),
                )
                BrowseDownloadsActionButton(
                    activeDownloadCount,
                    activeDownloads,
                    onOpenDownloads,
                    actionModifier(BrowseDownloadsActionIndex),
                    focusLinks = actionFocusLinks(BrowseDownloadsActionIndex),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                BrowseSettingsActionButton(
                    activeSettings,
                    onOpenSettings,
                    actionModifier(BrowseSettingsActionIndex),
                    focusLinks = actionFocusLinks(BrowseSettingsActionIndex),
                )
                BrowseProfileActionButton(
                    auth,
                    activeProfile,
                    onOpenLogin,
                    onOpenProfile,
                    actionModifier(BrowseProfileActionIndex),
                    focusLinks = actionFocusLinks(BrowseProfileActionIndex),
                )
            }
        }
        return
    }

    Row(
        modifier = modifier.actionEdgeGuards(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (spreadActions) Arrangement.SpaceBetween else Arrangement.spacedBy(10.dp),
    ) {
        BrowseSearchActionButton(
            visibleActiveSearch,
            searchEnabled,
            onOpenSearch,
            actionModifier(BrowseSearchActionIndex),
            focusLinks = actionFocusLinks(BrowseSearchActionIndex),
        )
        BrowseFiltersActionButton(
            visibleActiveFilters,
            visibleFiltersPanel,
            filtersEnabled,
            onOpenFilters,
            actionModifier(BrowseFiltersActionIndex),
            focusLinks = actionFocusLinks(BrowseFiltersActionIndex),
        )
        BrowseDownloadsActionButton(
            activeDownloadCount,
            activeDownloads,
            onOpenDownloads,
            actionModifier(BrowseDownloadsActionIndex),
            focusLinks = actionFocusLinks(BrowseDownloadsActionIndex),
        )
        BrowseSettingsActionButton(
            activeSettings,
            onOpenSettings,
            actionModifier(BrowseSettingsActionIndex),
            focusLinks = actionFocusLinks(BrowseSettingsActionIndex),
        )
        BrowseProfileActionButton(
            auth,
            activeProfile,
            onOpenLogin,
            onOpenProfile,
            actionModifier(BrowseProfileActionIndex),
            focusLinks = actionFocusLinks(BrowseProfileActionIndex),
        )
    }
}

@Composable
private fun BrowseActionIconButton(
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
            .then(
                if (enabled) {
                    Modifier
                        .onFocusChanged { focusState ->
                            focused = focusState.isFocused || focusState.hasFocus
                        }
                        .clearFocusAfterTouch()
                        .clip(shape)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = onClick,
                        )
                        .then(focusLinks.previewKeyHandlingModifier())
                } else {
                    Modifier.clip(shape)
                },
            ),
        color = yummyActionSurfaceColor(enabled = enabled, selected = active, focused = focusVisible),
        contentColor = yummyActionContentColor(enabled = enabled, selected = active, focused = focusVisible),
        border = yummyActionBorder(enabled = enabled, selected = active, focused = focusVisible),
        shape = shape,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(27.dp))
            if (enabled && badgeText != null) {
                BrowseActionBadge(badgeText)
            }
        }
    }
}

private fun BrowseActionFocusLinks.previewKeyHandlingModifier(): Modifier {
    if (!hasCustomKeyHandling) return Modifier
    return Modifier.onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        when (event.key) {
            Key.DirectionLeft -> if (leftFocusRequester != null) {
                leftFocusRequester.requestFocusSafely()
            } else {
                consumeHorizontalEdgeKey
            }
            Key.DirectionRight -> if (rightFocusRequester != null) {
                rightFocusRequester.requestFocusSafely()
            } else {
                consumeHorizontalEdgeKey
            }
            Key.DirectionUp -> upFocusRequester?.requestFocusSafely() == true
            Key.DirectionDown -> if (downFocusRequester != null) {
                downFocusRequester.requestFocusSafely()
            } else {
                consumeDownKey
            }
            else -> false
        }
    }
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

@Composable
internal fun BrowseSettingsActionButton(
    activeSettings: Boolean,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    focusLinks: BrowseActionFocusLinks = BrowseActionFocusLinks(),
) {
    BrowseActionIconButton(
        icon = Icons.Default.Settings,
        contentDescription = uiText(UiStringKey.Settings),
        onClick = onOpenSettings,
        modifier = modifier,
        active = activeSettings,
        focusLinks = focusLinks,
    )
}

@Composable
internal fun BrowseSearchActionButton(
    activeSearch: Boolean,
    enabled: Boolean,
    onOpenSearch: () -> Unit,
    modifier: Modifier = Modifier,
    focusLinks: BrowseActionFocusLinks = BrowseActionFocusLinks(),
) {
    BrowseActionIconButton(
        icon = Icons.Default.Search,
        contentDescription = uiText(UiStringKey.Search),
        onClick = onOpenSearch,
        modifier = modifier,
        active = activeSearch,
        enabled = enabled,
        focusLinks = focusLinks,
    )
}

@Composable
internal fun BrowseFiltersActionButton(
    activeFilters: Int,
    activeFiltersPanel: Boolean,
    enabled: Boolean,
    onOpenFilters: () -> Unit,
    modifier: Modifier = Modifier,
    focusLinks: BrowseActionFocusLinks = BrowseActionFocusLinks(),
) {
    BrowseActionIconButton(
        icon = Icons.Default.FilterList,
        contentDescription = uiText(UiStringKey.Filters),
        onClick = onOpenFilters,
        modifier = modifier,
        active = activeFilters > 0 || activeFiltersPanel,
        enabled = enabled,
        badgeText = activeFilters.takeIf { it > 0 }?.coerceAtMost(9)?.toString(),
        focusLinks = focusLinks,
    )
}

@Composable
internal fun BrowseDownloadsActionButton(
    activeDownloadCount: Int,
    activeDownloads: Boolean,
    onOpenDownloads: () -> Unit,
    modifier: Modifier = Modifier,
    focusLinks: BrowseActionFocusLinks = BrowseActionFocusLinks(),
) {
    BrowseActionIconButton(
        icon = Icons.Default.Download,
        contentDescription = uiText(UiStringKey.Downloads),
        onClick = onOpenDownloads,
        modifier = modifier,
        active = activeDownloadCount > 0 || activeDownloads,
        badgeText = activeDownloadCount.takeIf { it > 0 }?.let { count ->
            if (count > 9) "9+" else count.toString()
        },
        focusLinks = focusLinks,
    )
}

@Composable
internal fun BrowseProfileActionButton(
    auth: AuthUiState,
    activeProfile: Boolean,
    onOpenLogin: () -> Unit,
    onOpenProfile: () -> Unit,
    modifier: Modifier = Modifier,
    focusLinks: BrowseActionFocusLinks = BrowseActionFocusLinks(),
) {
    val unreadNotifications = auth.profile?.unreadNotifications ?: 0
    BrowseActionIconButton(
        icon = Icons.Default.AccountCircle,
        contentDescription = if (auth.profile == null) uiText(UiStringKey.SignIn) else uiText(UiStringKey.Profile),
        onClick = if (auth.profile == null) onOpenLogin else onOpenProfile,
        modifier = modifier,
        active = activeProfile,
        badgeText = unreadNotifications.notificationBadgeText(),
        focusLinks = focusLinks,
    )
}
