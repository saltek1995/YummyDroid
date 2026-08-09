package me.yummydroid.app

import android.app.SearchManager
import android.content.Intent
import android.os.Bundle
import me.yummydroid.app.data.VideoVariant

internal data class MainActivityRequest(
    val searchQuery: String? = null,
    val openProfileNotifications: Boolean = false,
    val animeId: Long = 0L,
    val animeTitle: String = "",
    val video: VideoVariant? = null,
)

internal fun Intent.toMainActivityRequest(): MainActivityRequest {
    return MainActivityRequest(
        searchQuery = searchQueryExtra(),
        openProfileNotifications = requestsProfileNotifications(),
        animeId = extras.animeIdExtra(),
        animeTitle = extras.animeTitleExtra(),
        video = extras.videoExtra(),
    )
}

private fun Intent.searchQueryExtra(): String? {
    return if (action == Intent.ACTION_SEARCH) {
        getStringExtra(SearchManager.QUERY)?.trim()?.takeIf { it.isNotBlank() }
    } else {
        null
    }
}

private fun Bundle?.animeIdExtra(): Long {
    return this?.getLong(EXTRA_ANIME_ID, 0L)?.takeIf { it > 0L } ?: 0L
}

private fun Bundle?.animeTitleExtra(): String {
    return this?.getString(EXTRA_ANIME_TITLE)?.trim().orEmpty()
}

private fun Bundle?.videoExtra(): VideoVariant? {
    val extras = this ?: return null
    val url = extras.getString(EXTRA_VIDEO_URL)?.takeIf { it.isNotBlank() } ?: return null
    return VideoVariant(
        id = extras.getLong(EXTRA_VIDEO_ID, 0L),
        animeId = extras.getLong(EXTRA_VIDEO_ANIME_ID, 0L),
        player = extras.getString(EXTRA_VIDEO_PLAYER)?.takeIf { it.isNotBlank() } ?: "External",
        dubbing = extras.getString(EXTRA_VIDEO_DUBBING)?.takeIf { it.isNotBlank() } ?: "Video",
        episode = extras.getString(EXTRA_VIDEO_EPISODE)?.takeIf { it.isNotBlank() } ?: "1",
        url = url,
        index = extras.getInt(EXTRA_VIDEO_INDEX, 1),
        durationSeconds = null,
        views = 0L,
    )
}

private const val EXTRA_ANIME_ID = "anime_id"
private const val EXTRA_VIDEO_ID = "video_id"
private const val EXTRA_VIDEO_ANIME_ID = "video_anime_id"
private const val EXTRA_VIDEO_INDEX = "video_index"
private const val EXTRA_VIDEO_URL = "video_url"
private const val EXTRA_VIDEO_PLAYER = "video_player"
private const val EXTRA_VIDEO_DUBBING = "video_dubbing"
private const val EXTRA_VIDEO_EPISODE = "video_episode"
private const val EXTRA_ANIME_TITLE = "anime_title"
