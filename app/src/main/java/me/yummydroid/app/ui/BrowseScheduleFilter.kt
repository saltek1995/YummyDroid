package me.yummydroid.app.ui

import me.yummydroid.app.data.ScheduleAnime

internal fun upcomingScheduleItems(
    items: List<ScheduleAnime>,
    nowSeconds: Long = System.currentTimeMillis() / 1000L,
): List<ScheduleAnime> = items.filter { item -> item.nextEpisodeAtSeconds > nowSeconds }
