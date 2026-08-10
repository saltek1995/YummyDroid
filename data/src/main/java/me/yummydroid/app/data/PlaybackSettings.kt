package me.yummydroid.app.data

enum class PreferredQuality(
    val title: String,
    val height: Int?,
) {
    Auto("Auto", null),
    P2160("2160p", 2160),
    P1440("1440p", 1440),
    P1080("1080p", 1080),
    P720("720p", 720),
    P576("576p", 576),
    P540("540p", 540),
    P480("480p", 480),
    P360("360p", 360),
    P240("240p", 240),
    P144("144p", 144);

    companion object {
        fun fromName(name: String): PreferredQuality? = entries.firstOrNull { it.name == name }
        fun fromHeight(height: Int?): PreferredQuality? = entries.firstOrNull { it.height == height }
    }
}

enum class PlayerDecoderMode(
    val title: String,
) {
    Auto("Auto"),
    Hardware("Hardware"),
    Software("Software");

    companion object {
        fun fromName(name: String): PlayerDecoderMode? = entries.firstOrNull { it.name == name }
    }
}

enum class PlayerBufferPreset(
    val title: String,
    val minBufferMs: Int,
    val maxBufferMs: Int,
    val playbackBufferMs: Int,
    val rebufferMs: Int,
    val prepareFallbackThresholdMs: Long,
    val switchFallbackThresholdMs: Long,
) {
    Compact(
        title = "Compact",
        minBufferMs = 15_000,
        maxBufferMs = 30_000,
        playbackBufferMs = 1_000,
        rebufferMs = 2_000,
        prepareFallbackThresholdMs = 6_000L,
        switchFallbackThresholdMs = 2_500L,
    ),
    Standard(
        title = "Standard",
        minBufferMs = 35_000,
        maxBufferMs = 70_000,
        playbackBufferMs = 1_500,
        rebufferMs = 3_000,
        prepareFallbackThresholdMs = 10_000L,
        switchFallbackThresholdMs = 3_000L,
    ),
    Large(
        title = "Large",
        minBufferMs = 70_000,
        maxBufferMs = 140_000,
        playbackBufferMs = 2_000,
        rebufferMs = 4_000,
        prepareFallbackThresholdMs = 16_000L,
        switchFallbackThresholdMs = 4_000L,
    ),
    Maximum(
        title = "Maximum",
        minBufferMs = 120_000,
        maxBufferMs = 240_000,
        playbackBufferMs = 2_500,
        rebufferMs = 5_000,
        prepareFallbackThresholdMs = 24_000L,
        switchFallbackThresholdMs = 5_000L,
    );

    companion object {
        fun fromName(name: String): PlayerBufferPreset? = entries.firstOrNull { it.name == name }
    }
}

enum class PlayerSpeed(
    val title: String,
    val value: Float,
) {
    X075("0.75x", 0.75f),
    Normal("1x", 1f),
    X125("1.25x", 1.25f),
    X15("1.5x", 1.5f),
    X175("1.75x", 1.75f),
    X2("2x", 2f);

    companion object {
        fun fromName(name: String): PlayerSpeed? = entries.firstOrNull { it.name == name }
    }
}

