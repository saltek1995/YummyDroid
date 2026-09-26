package me.yummydroid.app.data

import java.util.Locale
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async

/** Per-resolution work: browser cleanup must not cancel a subtitle already being fetched. */
internal class SubtitlePreparation(
    private val scope: CoroutineScope,
    private val fetch: suspend (String, Map<String, String>) -> HttpResponseSnapshot,
) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<SubtitlePreparation>
    val cacheGeneration = SubtitleCacheAccess.generation()

    private data class RequestKey(val uri: String, val headers: Map<String, String>)
    private val requests = mutableMapOf<RequestKey, Deferred<HttpResponseSnapshot?>>()
    // Preserve sequential optional requests and stop the queue on the first restriction.
    private var previousRequest: Deferred<HttpResponseSnapshot?>? = null

    fun prefetch(tracks: List<ResolvedSubtitleTrack>) {
        tracks.filter { track ->
            track.headers.isNotEmpty() && track.uri.startsWith("https://", true) &&
                track.uri.subtitleMimeTypeFromUrl() == "text/vtt"
        }.forEach { request(it.uri, it.headers) }
    }

    suspend fun response(uri: String, headers: Map<String, String>): HttpResponseSnapshot? =
        request(uri, headers).await()

    @Synchronized
    fun contains(uri: String, headers: Map<String, String>): Boolean =
        requests.containsKey(RequestKey(uri, headers.mapKeys { it.key.lowercase(Locale.ROOT) }))

    @Synchronized
    private fun request(uri: String, headers: Map<String, String>): Deferred<HttpResponseSnapshot?> {
        val key = RequestKey(uri, headers.mapKeys { it.key.lowercase(Locale.ROOT) })
        return requests.getOrPut(key) {
            val previous = previousRequest
            scope.async(Dispatchers.IO) {
                previous?.await()
                try {
                    withOptionalPlaybackSubtitles<HttpResponseSnapshot?>(null) { fetch(uri, headers) }
                } catch (failure: Exception) {
                    failure.throwIfCancellation()
                    // Memoize failures too: postprocessing must not retry an optional load.
                    null
                }
            }.also { previousRequest = it }
        }
    }
}
