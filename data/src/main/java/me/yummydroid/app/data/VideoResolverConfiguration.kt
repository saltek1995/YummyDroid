package me.yummydroid.app.data

import kotlinx.serialization.json.Json

internal val VIDEO_RESOLVER_JSON = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
}

internal object VideoStreamPatterns {
    val streamUrl = Regex(
        """(?:(?:https?:)?//|/)[^"'\s<>\\]+?(?:\.m3u8|\.mp4|\.mpd)(?:\?[^"'\s<>\\]*)?""",
        RegexOption.IGNORE_CASE,
    )
    val embeddedAbsoluteStreamUrl = Regex(
        """https?://[^"'\s<>\\]+?(?:\.m3u8|\.mp4|\.mpd)(?:\?[^"'\s<>\\]*)?""",
        RegexOption.IGNORE_CASE,
    )
    val dashHeight = Regex("""(?i)\b(?:height|maxHeight)\s*=\s*["'](\d+)["']""")
    val qualityHeight = Regex(
        """(?i)(?:^|[^\d])(2160|1440|1080|720|576|540|480|360|240|144)p(?:[^\d]|$)""",
    )
}

internal object SubtitleParsingPatterns {
    val webVttTiming = Regex(
        """^(\d{2,}:\d{2}:\d{2}\.\d{3}|\d{2}:\d{2}\.\d{3})\s*-->\s*(\d{2,}:\d{2}:\d{2}\.\d{3}|\d{2}:\d{2}\.\d{3})(.*)$""",
    )
    val webVttTimestampMapLocal = Regex("""(?i)\bLOCAL:([^,\s]+)""")
    val timing = Regex(
        """^\s*(?:\d+\s+)?(?:\d{1,2}:)?\d{1,2}:\d{2}[,.]\d{1,3}\s*-->\s*(?:\d{1,2}:)?\d{1,2}:\d{2}[,.]\d{1,3}(?:\s+.*)?$""",
    )
    val timingLine = Regex(
        """^\s*(?:\d+\s+)?((?:\d{1,2}:)?\d{1,2}:\d{2}[,.]\d{1,3})\s*-->\s*((?:\d{1,2}:)?\d{1,2}:\d{2}[,.]\d{1,3})(.*)$""",
    )
    val ttmlParagraph = Regex(
        """<p\b[^>]*>(.*?)</p>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    val ttmlParagraphWithAttributes = Regex(
        """<p\b([^>]*)>(.*?)</p>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    val htmlTag = Regex("""<[^>]+>""")
    val htmlSpaceEntity = Regex("""&(?:nbsp|#160|#xA0);""", RegexOption.IGNORE_CASE)
    val assBlankEscape = Regex("""\\[Nnh]""")
    val xmlTimeAttribute = Regex("""\b([A-Za-z_:][\w:.-]*)\s*=\s*["']([^"']+)["']""")
}
