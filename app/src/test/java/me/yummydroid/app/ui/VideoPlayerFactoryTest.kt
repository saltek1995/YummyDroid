package me.yummydroid.app.ui

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import me.yummydroid.app.data.SourceQuality

class VideoPlayerFactoryTest {
    @Test
    fun playbackIntentIsSetBeforeRemoteMediaLoad() {
        val calls = mutableListOf<String>()
        val player = Proxy.newProxyInstance(
            Player::class.java.classLoader,
            arrayOf(Player::class.java),
        ) { _, method, arguments ->
            when (method.name) {
                "setPlayWhenReady" -> calls += "play:${arguments?.first()}"
                "setMediaItem" -> calls += "media"
                "prepare" -> calls += "prepare"
            }
            primitiveDefault(method.returnType)
        } as Player

        player.prepareMediaItemForPlayback(
            mediaItem = MediaItem.Builder().setMediaId("video").build(),
            startPositionMs = 0L,
            playWhenReady = true,
        )

        assertEquals(listOf("play:true", "media", "prepare"), calls)
    }

    @Test
    fun missingBitrateUsesHighDefinitionFallback() {
        assertEquals(12_000_000L, initialVideoBitrateEstimate(emptyList()))
        assertEquals(12_000_000L, initialVideoBitrateEstimate(listOf(SourceQuality(height = 1080))))
    }

    @Test
    fun declaredBitrateGetsSelectionHeadroom() {
        val qualities = listOf(
            SourceQuality(height = 720, bitrate = 3_000_000),
            SourceQuality(height = 1080, bitrate = 8_000_000),
        )

        assertEquals(16_000_000L, initialVideoBitrateEstimate(qualities))
    }

    @Test
    fun estimateIsBoundedForExtremeMetadata() {
        assertEquals(
            50_000_000L,
            initialVideoBitrateEstimate(listOf(SourceQuality(height = 2160, bitrate = Int.MAX_VALUE))),
        )
    }

    private fun primitiveDefault(type: Class<*>): Any? = when (type) {
        Boolean::class.javaPrimitiveType -> false
        Int::class.javaPrimitiveType -> 0
        Long::class.javaPrimitiveType -> 0L
        Float::class.javaPrimitiveType -> 0f
        Double::class.javaPrimitiveType -> 0.0
        else -> null
    }
}
