package me.yummydroid.app.data

import java.io.IOException
import java.util.Base64
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

internal fun String.detectSourceQualities(): List<SourceQuality> {
    val qualities = mutableListOf<SourceQuality>()
    qualities += hlsSourceQualities()
    VideoStreamPatterns.dashHeight.findAll(this).forEach { match ->
        match.groupValues.getOrNull(1)?.toIntOrNull()?.let { height ->
            qualities += SourceQuality(height = height)
        }
    }
    VideoStreamPatterns.qualityHeight.findAll(this).forEach { match ->
        match.groupValues.getOrNull(1)?.toIntOrNull()?.let { height ->
            qualities += SourceQuality(height = height)
        }
    }
    return qualities.normalizedSourceQualities()
}

internal data class KodikParams(
    val type: String,
    val id: String,
    val hash: String,
    val domain: String,
    val domainSign: String,
    val playerDomain: String,
    val playerDomainSign: String,
    val referer: String,
    val refererSign: String,
)

internal fun String.kodikParams(): KodikParams {
    val type = extractKodikValue("type")
        ?: extractKodikVInfoValue("type")
        ?: throw IOException("Kodik: type was not found")
    val id = extractKodikVInfoValue("id")
        ?: extractKodikValue("videoId")
        ?: throw IOException("Kodik: id was not found")
    val hash = extractKodikVInfoValue("hash")
        ?: throw IOException("Kodik: hash was not found")

    return KodikParams(
        type = type,
        id = id,
        hash = hash,
        domain = extractKodikValue("domain") ?: throw IOException("Kodik: domain was not found"),
        domainSign = extractKodikValue("d_sign") ?: throw IOException("Kodik: d_sign was not found"),
        playerDomain = extractKodikValue("pd") ?: "kodikplayer.com",
        playerDomainSign = extractKodikValue("pd_sign") ?: throw IOException("Kodik: pd_sign was not found"),
        referer = extractKodikValue("ref") ?: DEFAULT_SITE_BASE_URL,
        refererSign = extractKodikValue("ref_sign") ?: throw IOException("Kodik: ref_sign was not found"),
    )
}

private fun String.extractKodikValue(name: String): String? {
    val doubleQuoted = Regex("""var\s+$name\s*=\s*"([^"]*)"""").find(this)?.groupValues?.getOrNull(1)
    if (!doubleQuoted.isNullOrBlank()) return doubleQuoted
    val singleQuoted = Regex("""var\s+$name\s*=\s*'([^']*)'""").find(this)?.groupValues?.getOrNull(1)
    return singleQuoted?.takeIf { it.isNotBlank() }
}

private fun String.extractKodikVInfoValue(name: String): String? {
    return Regex("""vInfo\.$name\s*=\s*['"]([^'"]+)['"]""")
        .find(this)
        ?.groupValues
        ?.getOrNull(1)
        ?.takeIf { it.isNotBlank() }
}

@Serializable
internal data class KodikFtorDto(
    val link: String = "",
    val links: Map<String, List<KodikLinkDto>> = emptyMap(),
) {
    fun availableQualities(): List<SourceQuality> {
        val qualities = links.keys.mapNotNull { key ->
            key.toIntOrNull().validVideoQualityHeight()?.let { SourceQuality(height = it) }
        }
        return (qualities + link.detectSourceQualities()).normalizedSourceQualities()
    }

    fun bestStream(preferredQuality: PreferredQuality): KodikStream? {
        linkStreams()
            .selectForPreferredQuality(
                preferredQuality = preferredQuality,
                height = { it.height },
            )
            ?.let { return it }

        return directLinkStream()
    }

    private fun linkStreams(): List<KodikStream> {
        return links.entries.flatMap { (quality, links) ->
            val height = quality.toIntOrNull()
            links.mapNotNull { link ->
                link.src
                    .takeIf { it.isNotBlank() }
                    ?.let { src ->
                        KodikStream(
                            url = src.decodeKodikUrl().normalizeKodikUrl(),
                            mimeType = link.type.takeIf { it.isNotBlank() },
                            height = height,
                        )
                    }
            }
        }
    }

    private fun directLinkStream(): KodikStream? {
        return link.takeIf { it.isNotBlank() }?.let {
            KodikStream(
                url = it.normalizeKodikUrl(),
                mimeType = it.mimeTypeFromKodikUrl(),
                height = it.detectKodikHeight(),
            )
        }
    }
}

@Serializable
internal data class KodikLinkDto(
    val src: String = "",
    val type: String = "",
)

internal data class KodikStream(
    val url: String,
    val mimeType: String?,
    val height: Int?,
)

@Serializable
internal data class AksorVideoDto(
    val qualities: AksorQualitiesDto = AksorQualitiesDto(),
) {
    fun bestStream(preferredQuality: PreferredQuality): AksorStream? = qualities.bestStream(preferredQuality)
}

@Serializable
internal data class AksorQualitiesDto(
    val q4k: String? = null,
    val q2k: String? = null,
    val q1080: String? = null,
    val q720: String? = null,
    val q480: String? = null,
    val q360: String? = null,
) {
    fun availableQualities(): List<SourceQuality> {
        return streams().availableSourceQualities(
            url = { it.url },
            height = { it.height },
        )
    }

    fun bestStream(preferredQuality: PreferredQuality): AksorStream? {
        return streams()
            .filter { it.url.isNotBlank() }
            .selectForPreferredQuality(
                preferredQuality = preferredQuality,
                height = { it.height },
            )
    }

    private fun streams(): List<AksorStream> {
        return listOf(
            AksorStream(q4k.orEmpty(), 2160),
            AksorStream(q2k.orEmpty(), 1440),
            AksorStream(q1080.orEmpty(), 1080),
            AksorStream(q720.orEmpty(), 720),
            AksorStream(q480.orEmpty(), 480),
            AksorStream(q360.orEmpty(), 360),
        )
    }
}

internal data class AksorStream(
    val url: String,
    val height: Int,
)

private fun String.decodeKodikUrl(): String {
    val rotated = map { char ->
        when (char) {
            in 'A'..'Z' -> {
                val shifted = char.code + 18
                if (shifted <= 'Z'.code) shifted.toChar() else (shifted - 26).toChar()
            }
            in 'a'..'z' -> {
                val shifted = char.code + 18
                if (shifted <= 'z'.code) shifted.toChar() else (shifted - 26).toChar()
            }
            else -> char
        }
    }.joinToString("")
    val padded = rotated.padEnd(rotated.length + ((4 - rotated.length % 4) % 4), '=')
    return runCatching {
        String(Base64.getDecoder().decode(padded), Charsets.UTF_8)
    }.getOrDefault(this)
}

private fun String.normalizeKodikUrl(): String {
    return when {
        startsWith("//") -> "https:$this"
        startsWith("/") -> "https://kodikplayer.com$this"
        else -> this
    }
}

internal fun String.mimeTypeFromKodikUrl(): String? {
    val lower = lowercase()
    return when {
        ".m3u8" in lower -> "application/x-mpegURL"
        ".mpd" in lower -> "application/dash+xml"
        ".mp4" in lower -> "video/mp4"
        else -> null
    }
}

private fun String.detectKodikHeight(): Int? {
    return Regex("""(?i)(2160|1440|1080|720|576|540|480|360|240|144)p""")
        .find(this)
        ?.groupValues
        ?.getOrNull(1)
        ?.toIntOrNull()
}

internal data class AllohaRuntimeStream(
    val url: String,
    val height: Int,
    val mirrorIndex: Int,
)

internal fun cvhPlaylistItemMatchesEpisode(
    requestedSeason: Int?,
    requestedEpisode: Int,
    itemSeason: Int?,
    itemEpisode: Int?,
): Boolean {
    val episode = itemEpisode ?: 1
    if (episode != requestedEpisode) return false
    return requestedSeason == null || (itemSeason ?: 1) == requestedSeason
}

internal fun cvhFallbackEpisodeForMissingRequestedEpisode(
    requestedEpisode: Int,
    availableEpisodes: List<Int?>,
): Int? {
    val fallbackEpisode = availableEpisodes
        .map { it ?: 1 }
        .filter { it in 1 until requestedEpisode }
        .maxOrNull()
        ?: return null
    return fallbackEpisode.takeIf { requestedEpisode - it == 1 }
}

@Serializable
internal data class CvhPlaylistDto(
    val items: List<CvhItemDto> = emptyList(),
)

@Serializable
internal data class CvhItemDto(
    @SerialName("vkId") val vkId: String = "",
    @SerialName("voiceStudio") val voiceStudio: String? = null,
    @SerialName("voiceType") val voiceType: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
)

@Serializable
internal data class CvhVideoDto(
    val sources: CvhSourcesDto? = null,
)

@Serializable
internal data class CvhSourcesDto(
    @SerialName("hlsUrl") val hlsUrl: String = "",
    @SerialName("dashUrl") val dashUrl: String = "",
    @SerialName("mpeg4kUrl") val mpeg4kUrl: String = "",
    @SerialName("mpeg2kUrl") val mpeg2kUrl: String = "",
    @SerialName("mpegQhdUrl") val mpegQhdUrl: String = "",
    @SerialName("mpegFullHdUrl") val mpegFullHdUrl: String = "",
    @SerialName("mpegHighUrl") val mpegHighUrl: String = "",
    @SerialName("mpegMediumUrl") val mpegMediumUrl: String = "",
    @SerialName("mpegLowUrl") val mpegLowUrl: String = "",
    @SerialName("mpegLowestUrl") val mpegLowestUrl: String = "",
    @SerialName("mpegTinyUrl") val mpegTinyUrl: String = "",
) {
    fun availableQualities(): List<SourceQuality> {
        return (
            mpegStreams().availableSourceQualities(
                url = { it.url },
                height = { it.height },
            ) +
                hlsUrl.takeIf { it.isNotBlank() }?.detectSourceQualities().orEmpty() +
                dashUrl.takeIf { it.isNotBlank() }?.detectSourceQualities().orEmpty()
            ).normalizedSourceQualities()
    }

    fun bestStream(preferredQuality: PreferredQuality): CvhStream? {
        val mpegStreams = mpegStreams()
        val highestKnownHeight = mpegStreams
            .asSequence()
            .filter { it.url.isNotBlank() }
            .mapNotNull { it.height }
            .maxOrNull()
        adaptiveStreams(highestKnownHeight).firstOrNull()?.let { return it }

        return mpegStreams
            .filter { it.url.isNotBlank() }
            .selectForPreferredQuality(
                preferredQuality = preferredQuality,
                height = { it.height },
            )
    }

    private fun adaptiveStreams(highestKnownHeight: Int?): List<CvhStream> {
        return listOf(
            CvhStream(hlsUrl, "application/x-mpegURL", highestKnownHeight),
            CvhStream(dashUrl, "application/dash+xml", highestKnownHeight),
        ).filter { it.url.isNotBlank() }
    }

    private fun mpegStreams(): List<CvhStream> {
        return listOf(
            CvhStream(mpeg4kUrl, "video/mp4", 2160),
            CvhStream(mpeg2kUrl, "video/mp4", 1440),
            CvhStream(mpegQhdUrl, "video/mp4", 1440),
            CvhStream(mpegFullHdUrl, "video/mp4", 1080),
            CvhStream(mpegHighUrl, "video/mp4", 720),
            CvhStream(mpegMediumUrl, "video/mp4", 480),
            CvhStream(mpegLowUrl, "video/mp4", 360),
            CvhStream(mpegLowestUrl, "video/mp4", 240),
            CvhStream(mpegTinyUrl, "video/mp4", 144),
        )
    }
}

internal data class CvhStream(
    val url: String,
    val mimeType: String,
    val height: Int?,
)

private fun <T> Iterable<T>.availableSourceQualities(
    url: (T) -> String,
    height: (T) -> Int?,
): List<SourceQuality> {
    return mapNotNull { stream ->
        height(stream)
            ?.takeIf { url(stream).isNotBlank() }
            ?.let { SourceQuality(height = it) }
    }.normalizedSourceQualities()
}
