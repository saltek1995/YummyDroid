package me.yummydroid.app.ui

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.util.Locale
import me.yummydroid.app.data.PosterCardSize
import me.yummydroid.app.data.ScheduleAnime

@Composable
internal fun ScheduleReadySection(
    schedule: List<ScheduleAnime>,
    precomputedDayGroups: List<ScheduleDayGroup>? = null,
    gridState: LazyGridState,
    cardSize: PosterCardSize,
    locale: Locale,
    focusFirstRequest: FocusFirstRequest,
    focusCurrentRequestNonce: Long,
    calendarFocusRequestNonce: Long = 0L,
    contentFocusEnabled: Boolean = true,
    showCalendarInGrid: Boolean = true,
    selectedEpochDay: Long,
    onSelectedEpochDayChange: (Long) -> Unit,
    currentFocusedIndex: () -> Int,
    onFocusedIndexChange: (Int) -> Unit,
    pinnedTopPadding: Dp = 0.dp,
    contentBottomPadding: Dp = 0.dp,
    onRegisterBackToTopHandler: ((HomeBackToTopHandler?) -> Unit)? = null,
    onExitHorizontalDirection: (VisualGridDirection) -> Boolean = { true },
    onExitUp: () -> Boolean = { false },
    onExitDown: () -> Boolean = { false },
    onOpenAnime: (Long) -> Unit,
) {
    ScheduleReadyGridRoot(
        ScheduleReadyParams(
            schedule = schedule,
            precomputedDayGroups = precomputedDayGroups,
            gridState = gridState,
            cardSize = cardSize,
            locale = locale,
            focusFirstRequest = focusFirstRequest,
            focusCurrentRequestNonce = focusCurrentRequestNonce,
            calendarFocusRequestNonce = calendarFocusRequestNonce,
            contentFocusEnabled = contentFocusEnabled,
            showCalendarInGrid = showCalendarInGrid,
            selectedEpochDay = selectedEpochDay,
            onSelectedEpochDayChange = onSelectedEpochDayChange,
            currentFocusedIndex = currentFocusedIndex,
            onFocusedIndexChange = onFocusedIndexChange,
            pinnedTopPadding = pinnedTopPadding,
            contentBottomPadding = contentBottomPadding,
            onRegisterBackToTopHandler = onRegisterBackToTopHandler,
            onExitHorizontalDirection = onExitHorizontalDirection,
            onExitUp = onExitUp,
            onExitDown = onExitDown,
            onOpenAnime = onOpenAnime,
        ),
    )
}
