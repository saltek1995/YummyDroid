package me.yummydroid.app.data

import java.util.Locale

fun sourceProviderRank(player: String): Int {
    val normalized = player.cleanVideoSourceLabel().lowercase(Locale.ROOT)
    return when {
        "cvh" in normalized || "cdnvideohub" in normalized -> 0
        "alloha" in normalized -> 1
        "kodik" in normalized -> 2
        "aksor" in normalized -> 3
        "sibnet" in normalized -> 4
        else -> 10
    }
}

fun String.cleanVideoSourceLabel(): String {
    var value = trim()
    knownVideoSourcePrefixRegexes.forEach { prefixRegex ->
        value = value.replace(
            regex = prefixRegex,
            replacement = "",
        ).trim()
    }
    return value
}

fun String.isKnownPlayerLabel(): Boolean {
    val key = cleanVideoSourceLabel().normalizedVoiceKey()
    return key in knownVideoPlayerLabelKeys
}

fun String.normalizedVoiceKey(): String {
    return lowercase(Locale.ROOT)
        .replace('\u0451', '\u0435')
        .replace(RU_VOICE_PREFIX_KEY, "")
        .replace(RU_SUBTITLES_PREFIX_KEY, "")
        .replace(RU_PLAYER_PREFIX_KEY, "")
        .replace(voiceKeySeparatorRegex, "")
        .trim()
}

private const val RU_VOICE_PREFIX_LABEL = "\u041e\u0437\u0432\u0443\u0447\u043a\u0430"
private const val RU_SUBTITLES_PREFIX_LABEL = "\u0421\u0443\u0431\u0442\u0438\u0442\u0440\u044b"
private const val RU_PLAYER_PREFIX_LABEL = "\u041f\u043b\u0435\u0435\u0440"
private const val RU_VOICE_PREFIX_KEY = "\u043e\u0437\u0432\u0443\u0447\u043a\u0430"
private const val RU_SUBTITLES_PREFIX_KEY = "\u0441\u0443\u0431\u0442\u0438\u0442\u0440\u044b"
private const val RU_PLAYER_PREFIX_KEY = "\u043f\u043b\u0435\u0435\u0440"

private val knownVideoSourcePrefixes = listOf(
    RU_VOICE_PREFIX_LABEL,
    RU_SUBTITLES_PREFIX_LABEL,
    RU_PLAYER_PREFIX_LABEL,
    "Voice",
    "Dubbing",
    "Subtitles",
    "Player",
)

private val knownVideoSourcePrefixRegexes = knownVideoSourcePrefixes.map { prefix ->
    Regex("""^\s*${Regex.escape(prefix)}\s*""", RegexOption.IGNORE_CASE)
}

internal val whitespaceRegex = Regex("""\s+""")
private val voiceKeySeparatorRegex = Regex("""[\s./|вЂў:_-]+""")

private val knownVideoPlayerLabelKeys = setOf(
    "alloha",
    "kodik",
    "cvh",
    "sibnet",
    "aksor",
    "hls",
    "mp4",
    "videocdn",
    "cdnvideohub",
    "videoframe",
    "aniboom",
)
