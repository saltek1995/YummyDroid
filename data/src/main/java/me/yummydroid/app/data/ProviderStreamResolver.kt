package me.yummydroid.app.data

import java.io.IOException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

// ProviderStreamResolver
internal class ProviderStreamResolver(
    private val client: OkHttpClient,
    private val playbackRequestHeaders: PlaybackRequestHeaders,
    private val subtitleMetadataParser: SubtitleMetadataParser,
    private val fallbackSiteBaseUrl: () -> String,
    private val json: Json,
) {
    suspend fun resolveKodik(
        sourceUrl: String,
        siteBaseUrl: String,
        preferredQuality: PreferredQuality,
    ): ResolvedVideoStream {
        val html = getText(sourceUrl, playbackRequestHeaders.iframe(sourceUrl, siteBaseUrl))
        val params = html.kodikParams()
        val form = FormBody.Builder()
            .add("d", params.domain)
            .add("d_sign", params.domainSign)
            .add("pd", params.playerDomain)
            .add("pd_sign", params.playerDomainSign)
            .add("ref", params.referer)
            .add("ref_sign", params.refererSign)
            .add("bad_user", "false")
            .add("cdn_is_working", "true")
            .add("type", params.type)
            .add("hash", params.hash)
            .add("id", params.id)
            .build()
        val request = Request.Builder()
            .url(KODIK_FTOR_URL)
            .headers(playbackRequestHeaders.kodikApi(sourceUrl).toOkHttpHeaders())
            .post(form)
            .build()

        val body = client.awaitRequiredResponseBody(request) { code -> "Kodik API returned HTTP $code" }
        val dto = json.decodeFromString<KodikFtorDto>(body)
        val stream = dto.bestStream(preferredQuality)
            ?: throw IOException("Kodik: HLS/MP4/DASH stream was not found")
        val selectedHeight = maxOfOrNull(stream.height, stream.url.detectVideoHeight())

        return ResolvedVideoStream(
            url = stream.url,
            mimeType = stream.mimeType ?: stream.url.mimeTypeFromKodikUrl(),
            headers = playbackRequestHeaders.kodikPlayback(stream.url),
            maxVideoHeight = selectedHeight,
            availableQualities = (dto.availableQualities() + stream.url.detectSourceQualities())
                .normalizedSourceQualities(),
            selectedVideoHeight = selectedHeight,
            skipPlaybackProbe = true,
            subtitles = subtitleMetadataParser.extractTracks(body, sourceUrl),
        )
    }

    suspend fun resolveAksor(
        sourceUrl: String,
        siteBaseUrl: String,
        preferredQuality: PreferredQuality,
    ): ResolvedVideoStream {
        val videoId = sourceUrl.toHttpUrlOrNull()
            ?.pathSegments
            ?.lastOrNull { it.isNotBlank() }
            ?: throw IOException("Aksor: missing video id")
        val origin = sourceUrl.urlOrigin() ?: AKSOR_ORIGIN
        val video = getJson<AksorVideoDto>(
            url = "$origin/api/video/$videoId",
            headers = playbackRequestHeaders.aksorApi(sourceUrl),
            providerName = "Aksor",
        )
        val stream = video.bestStream(preferredQuality)
            ?: throw IOException("Aksor: stream is unavailable")
        val streamUrl = stream.url.normalizeAgainst(sourceUrl)
        val selectedHeight = maxOfOrNull(stream.height, streamUrl.detectVideoHeight())

        return ResolvedVideoStream(
            url = streamUrl,
            mimeType = streamUrl.mimeTypeFromUrl(),
            headers = playbackRequestHeaders.playback(streamUrl, sourceUrl, siteBaseUrl),
            maxVideoHeight = selectedHeight,
            availableQualities = (video.qualities.availableQualities() + streamUrl.detectSourceQualities())
                .normalizedSourceQualities(),
            selectedVideoHeight = selectedHeight,
            skipPlaybackProbe = true,
        )
    }

    suspend fun resolveSibnet(sourceUrl: String, siteBaseUrl: String): ResolvedVideoStream {
        val html = getText(sourceUrl, playbackRequestHeaders.iframe(sourceUrl, siteBaseUrl))
        val streamUrl = html.extractSibnetStreamUrl(sourceUrl)
            ?: html.extractDirectStreamUrl(sourceUrl)
            ?: throw IOException("Sibnet: HLS/MP4/DASH stream was not found")

        return ResolvedVideoStream(
            url = streamUrl,
            mimeType = streamUrl.mimeTypeFromUrl(),
            headers = playbackRequestHeaders.playback(streamUrl, sourceUrl, siteBaseUrl),
            maxVideoHeight = streamUrl.detectVideoHeight(),
            skipPlaybackProbe = true,
        )
    }

    suspend fun resolveCvh(
        sourceUrl: String,
        video: VideoVariant,
        siteBaseUrl: String,
        preferredQuality: PreferredQuality,
    ): ResolvedVideoStream {
        val iframeUrl = sourceUrl.toHttpUrlOrNull()
            ?: throw IOException("CVH: invalid iframe URL")
        val titleId = iframeUrl.queryParameter("anime_id")?.takeIf { it.isNotBlank() }
            ?: throw IOException("CVH: anime_id was not found in iframe")
        val episode = iframeUrl.queryParameter("episode")?.toIntOrNull()
            ?: video.episode.toIntOrNull()
            ?: 1
        val season = iframeUrl.queryParameter("season")?.toIntOrNull()
        val priorityVoices = buildCvhVoiceCandidates(iframeUrl, video)

        val playlistUrl = CVH_PLAYLIST_URL.newBuilder()
            .addQueryParameter("pub", CVH_PUBLISHER_ID)
            .addQueryParameter("id", titleId)
            .addQueryParameter("aggr", CVH_AGGREGATOR)
            .build()
            .toString()
        val playlist = getJson<CvhPlaylistDto>(
            url = playlistUrl,
            headers = playbackRequestHeaders.cvhApi(sourceUrl),
            providerName = "CVH",
        )
        val selectedVideo = playlist.items.selectCvhItem(
            season = season,
            episode = episode,
            priorityVoices = priorityVoices,
        ) ?: throw IOException(
            "CVH: voice is unavailable for episode $episode: ${priorityVoices.firstOrNull().orEmpty()}",
        )

        val vkId = selectedVideo.vkId.takeIf { it.isNotBlank() }
            ?: throw IOException("CVH: episode has no vkId")
        val videoUrl = "$CVH_VIDEO_URL/$vkId"
        val cvhVideo = getJson<CvhVideoDto>(
            url = videoUrl,
            headers = playbackRequestHeaders.cvhApi(sourceUrl),
            providerName = "CVH",
        )
        val source = cvhVideo.sources?.bestStream(preferredQuality)
            ?: throw IOException("CVH: HLS/DASH/MP4 stream was not found")

        val selectedHeight = maxOfOrNull(source.height, source.url.detectVideoHeight())
        return ResolvedVideoStream(
            url = source.url,
            mimeType = source.mimeType,
            headers = playbackRequestHeaders.cvhPlayback(source.url, sourceUrl, siteBaseUrl),
            maxVideoHeight = selectedHeight,
            availableQualities = (cvhVideo.sources?.availableQualities().orEmpty() + source.url.detectSourceQualities())
                .normalizedSourceQualities(),
            selectedVideoHeight = selectedHeight,
            skipPlaybackProbe = true,
        )
    }

    fun getResponseBlocking(url: String, headers: Map<String, String>): HttpResponseSnapshot {
        return client.readResponseSnapshot(url, headers)
    }

    private suspend fun getText(url: String, headers: Map<String, String>): String {
        return readRequiredResponseBody(url, headers) { code -> "Player returned HTTP $code" }
    }

    private suspend inline fun <reified T> getJson(
        url: String,
        headers: Map<String, String>,
        providerName: String,
    ): T {
        val body = readRequiredResponseBody(url, headers) { code -> "$providerName API returned HTTP $code" }
        return json.decodeFromString(body)
    }

    private suspend fun readRequiredResponseBody(
        url: String,
        headers: Map<String, String>,
        errorMessage: (Int) -> String,
    ): String {
        return client.awaitRequiredResponseBody(url, headers, errorMessage)
    }

    private fun String.extractSibnetStreamUrl(baseUrl: String): String? {
        val normalized = replace("\\/", "/")
            .replace("&amp;", "&")
            .replace("\\u0026", "&")
        return SIBNET_PLAYER_SOURCE_REGEX
            .find(normalized)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim('"', '\'', ' ', '\\')
            ?.normalizeAgainst(baseUrl)
            ?.takeIf { it.isCapturedPlaybackUrl() }
    }

    private fun String.normalizeAgainst(baseUrl: String): String {
        return normalizeVideoUrlAgainstBase(baseUrl, fallbackSiteBaseUrl())
    }

    private companion object {
        const val CVH_PUBLISHER_ID = "745"
        const val CVH_AGGREGATOR = "mali"
        const val CVH_VIDEO_URL = "https://plapi.cdnvideohub.com/api/v1/player/sv/video"
        val CVH_PLAYLIST_URL = "https://plapi.cdnvideohub.com/api/v1/player/sv/playlist".toHttpUrl()
        const val KODIK_FTOR_URL = "https://kodikplayer.com/ftor"
        const val AKSOR_ORIGIN = "https://player.aksor.tv"
        val SIBNET_PLAYER_SOURCE_REGEX = Regex(
            """src\s*:\s*["']([^"']+\.(?:m3u8|mp4|mpd)(?:\?[^"']*)?)["']""",
            RegexOption.IGNORE_CASE,
        )
    }
}
