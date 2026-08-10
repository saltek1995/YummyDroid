package me.yummydroid.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import java.util.Locale

@Composable
internal fun ScheduleCalendarBlock(
    dayGroups: List<ScheduleDayGroup>,
    selectedEpochDay: Long,
    locale: Locale,
    modifier: Modifier = Modifier,
    focusRequestNonce: Long = 0L,
    focusEnabled: Boolean = true,
    onCalendarFocusChanged: (Boolean) -> Unit = {},
    onExitUp: () -> Boolean,
    onExitDown: () -> Boolean,
    onSelectDay: (Long) -> Unit,
) {
    val runtime = rememberScheduleCalendarRuntime(
        dayGroups = dayGroups,
        selectedEpochDay = selectedEpochDay,
        locale = locale,
        onSelectDay = onSelectDay,
    )
    ScheduleCalendarEffects(
        runtime = runtime,
        focusRequestNonce = focusRequestNonce,
        focusEnabled = focusEnabled,
    )
    ScheduleCalendarContent(
        runtime = runtime,
        modifier = modifier,
        focusEnabled = focusEnabled,
        onCalendarFocusChanged = onCalendarFocusChanged,
        onExitUp = onExitUp,
        onExitDown = onExitDown,
    )
}
