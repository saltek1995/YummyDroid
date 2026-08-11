package me.yummydroid.app.ui

import androidx.compose.runtime.MutableIntState
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

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
