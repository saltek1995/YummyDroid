package me.yummydroid.app.data

import android.content.Context
import kotlinx.serialization.Serializable

class VideoSubscriptionHintStorage(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun read(userId: Long): List<VideoSubscriptionHint> {
        return prefs.getJsonOrNull<StoredVideoSubscriptionHints>(key(userId))
            ?.items
            ?.filter { it.isValid() }
            .orEmpty()
    }

    fun save(userId: Long, hints: List<VideoSubscriptionHint>) {
        prefs.putJson(
            key(userId),
            StoredVideoSubscriptionHints(
                items = hints
                    .filter { it.isValid() }
                    .distinctBy { it.identityKey }
                    .sortedWith(
                        compareBy<VideoSubscriptionHint> { it.animeId }
                            .thenBy { it.voiceKey }
                            .thenBy { it.playerId }
                            .thenBy { it.playerKey },
                    ),
            ),
        )
    }

    private fun key(userId: Long): String = "$KEY_PREFIX$userId"

    private companion object {
        const val PREFS_NAME = "yummydroid_video_subscription_hints"
        const val KEY_PREFIX = "hints_"
    }
}

@Serializable
data class VideoSubscriptionHint(
    val animeId: Long,
    val playerId: Long = 0L,
    val playerKey: String = "",
    val voiceKey: String = "",
    val voiceTitle: String = "",
    val title: String = "",
    val posterUrl: String = "",
) {
    val identityKey: String
        get() = "$animeId|$playerId|$playerKey|$voiceKey"

    fun isValid(): Boolean {
        return animeId > 0L && voiceKey.isNotBlank() && (playerId > 0L || playerKey.isNotBlank())
    }
}

@Serializable
private data class StoredVideoSubscriptionHints(
    val items: List<VideoSubscriptionHint> = emptyList(),
)
