package me.yummydroid.app.data

import java.util.Locale

private val ongoingStatusTokens = listOf(
    "\u043e\u043d\u0433\u043e",
    "ongoing",
    "\u0430\u043d\u043e\u043d\u0441",
    "\u043d\u0435 \u0432\u044b\u0448",
)

private val releasedStatusTokens = listOf(
    "\u0432\u044b\u0448\u0435\u043b",
    "\u0432\u044b\u0448\u043b\u043e",
    "\u0437\u0430\u0432\u0435\u0440\u0448",
    "released",
    "completed",
    "complete",
    "finished",
)

fun AnimeDetails.isFullyReleased(): Boolean {
    val normalizedStatus = status
        .lowercase(Locale.ROOT)
        .replace('\u0451', '\u0435')

    if (ongoingStatusTokens.any(normalizedStatus::contains)) return false
    return releasedStatusTokens.any(normalizedStatus::contains)
}

fun AnimeDetails.canShowVideoSubscriptions(): Boolean = !isFullyReleased()
