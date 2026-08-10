package me.yummydroid.app.ui

import java.time.LocalDate

internal fun visibleItems(firstIndex: Int, lastIndex: Int): List<VisibleScheduleCalendarItem> {
    return (firstIndex..lastIndex).map { index ->
        VisibleScheduleCalendarItem(
            index = index,
            offsetPx = (index - firstIndex) * 106,
            sizePx = 96,
        )
    }
}

internal fun scheduleDayGroups(vararg dates: LocalDate): List<ScheduleDayGroup> {
    return dates.map { date ->
        ScheduleDayGroup(
            date = date,
            epochDay = date.toEpochDay(),
            items = emptyList(),
        )
    }
}
