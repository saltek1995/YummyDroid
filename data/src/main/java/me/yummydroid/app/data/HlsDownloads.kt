package me.yummydroid.app.data

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import okhttp3.Request

// DownloadEpisodeRanges
data class DownloadEpisodeSlot(
    val key: String,
    val title: String,
    val order: Double?,
)

fun VideoVariant.downloadEpisodeSlot(): DownloadEpisodeSlot {
    return DownloadEpisodeSlot(
        key = matchingEpisodeKey,
        title = episode.trim().takeIf { it.isNotBlank() } ?: matchingEpisodeKey,
        order = episodeOrderValue(),
    )
}

fun downloadEpisodeSlotComparator(): Comparator<DownloadEpisodeSlot> {
    return compareBy<DownloadEpisodeSlot> { it.order ?: Double.MAX_VALUE }
        .thenBy { it.title }
        .thenBy { it.key }
}

fun Iterable<VideoVariant>.sortedDownloadEpisodeSlots(): List<DownloadEpisodeSlot> {
    return distinctBy { it.matchingEpisodeKey }
        .map { it.downloadEpisodeSlot() }
        .sortedWith(downloadEpisodeSlotComparator())
}

fun List<DownloadEpisodeSlot>.compactEpisodeRanges(): List<String> {
    if (isEmpty()) return emptyList()
    val ranges = mutableListOf<String>()
    var start = first()
    var previous = first()

    drop(1).forEach { current ->
        val contiguous = previous.order?.let { previousOrder ->
            current.order?.let { currentOrder ->
                isWholeNumber(previousOrder) &&
                    isWholeNumber(currentOrder) &&
                    currentOrder.toInt() == previousOrder.toInt() + 1
            }
        } == true
        if (contiguous) {
            previous = current
        } else {
            ranges += start.rangeTitle(previous)
            start = current
            previous = current
        }
    }
    ranges += start.rangeTitle(previous)
    return ranges
}

fun List<DownloadEpisodeSlot>.compactEpisodeNumberRanges(): List<IntRange> {
    return mapNotNull { slot ->
        slot.order
            ?.takeIf(::isWholeNumber)
            ?.toInt()
            ?.takeIf { it > 0 }
            ?.let { it..it }
    }.mergeEpisodeRanges()
}

fun List<IntRange>.mergeEpisodeRanges(): List<IntRange> {
    if (isEmpty()) return emptyList()
    val sorted = sortedWith(compareBy<IntRange> { it.first }.thenBy { it.last })
    val merged = mutableListOf<IntRange>()
    var current = sorted.first()
    sorted.drop(1).forEach { next ->
        if (next.first <= current.last + 1) {
            current = current.first..maxOf(current.last, next.last)
        } else {
            merged += current
            current = next
        }
    }
    merged += current
    return merged
}

fun IntRange.subtractEpisodeRanges(availableRanges: List<IntRange>): List<IntRange> {
    var cursor = first
    val missing = mutableListOf<IntRange>()
    availableRanges
        .mergeEpisodeRanges()
        .forEach { available ->
            if (available.last < cursor) return@forEach
            if (available.first > last) return@forEach
            if (available.first > cursor) {
                missing += cursor..minOf(available.first - 1, last)
            }
            cursor = maxOf(cursor, available.last + 1)
            if (cursor > last) return missing
        }
    if (cursor <= last) {
        missing += cursor..last
    }
    return missing
}

fun List<IntRange>.formatEpisodeRanges(limit: Int): String {
    val visible = take(limit)
    val suffix = if (size > limit) ", ..." else ""
    return visible.joinToString(", ") { range ->
        if (range.first == range.last) range.first.toString() else "${range.first}-${range.last}"
    } + suffix
}

fun isWholeNumber(value: Double): Boolean {
    return value % 1.0 == 0.0
}

private fun DownloadEpisodeSlot.rangeTitle(end: DownloadEpisodeSlot): String {
    val startTitle = order?.formatEpisodeNumber() ?: title
    val endTitle = end.order?.formatEpisodeNumber() ?: end.title
    return if (key == end.key) startTitle else "$startTitle-$endTitle"
}

private fun Double.formatEpisodeNumber(): String {
    val asInt = toInt()
    return if (isWholeNumber(this)) asInt.toString() else toString().trimEnd('0').trimEnd('.')
}

// DownloadFileTypes
internal fun String.fileExtensionForDownload(): String {
    val path = substringBefore('?').substringBefore('#').lowercase()
    return when {
        path.endsWith(".m3u8") -> "m3u8"
        path.endsWith(".mpd") -> "mpd"
        path.endsWith(".m4s") -> "m4s"
        path.endsWith(".ts") -> "ts"
        path.endsWith(".mp4") -> "mp4"
        path.endsWith(".mkv") -> "mkv"
        path.endsWith(".webm") -> "webm"
        else -> "mp4"
    }
}

internal fun String.mimeTypeFromFileName(): String? {
    val lower = lowercase()
    return when {
        lower.endsWith(".m3u8") -> "application/x-mpegURL"
        lower.endsWith(".mpd") -> "application/dash+xml"
        lower.endsWith(".mp4") -> "video/mp4"
        lower.endsWith(".m4s") -> "video/mp4"
        lower.endsWith(".ts") -> "video/mp2t"
        lower.endsWith(".mkv") -> "video/x-matroska"
        lower.endsWith(".webm") -> "video/webm"
        else -> null
    }
}

// DownloadQualitySelection
fun Iterable<VideoVariant>.downloadCoverageQualityTitles(
    resolvedQualities: List<PreferredQuality>,
): List<String> {
    val resolvedHeights = resolvedQualities.mapNotNull { it.height }
    val knownHeights = knownSourceQualityHeights()
    return (resolvedHeights + knownHeights)
        .distinct()
        .sortedDescending()
        .map { "${it}p" }
}

fun Iterable<VideoVariant>.sourceQualitiesForSameEpisodeVoice(
    currentVideo: VideoVariant,
): List<SourceQuality> {
    return filter { it.isSameEpisodeAs(currentVideo) && it.matchingVoiceKey == currentVideo.matchingVoiceKey }
        .flatMap { it.sourceQualities }
}

fun Iterable<VideoVariant>.knownSourceQualityHeights(): List<Int> {
    return flatMap { it.sourceQualities }
        .mapNotNull { it.height.validVideoQualityHeight() }
        .distinct()
        .sortedDescending()
}

fun VideoVariant.canMaybeProvideDownloadQuality(preferredQuality: PreferredQuality): Boolean {
    val height = preferredQuality.height ?: return true
    val qualities = sourceQualities
    return qualities.isEmpty() || qualities.any { it.height == height }
}

fun VideoVariant.hasDownloadedQuality(preferredQuality: PreferredQuality): Boolean {
    return offlineFiles.any { it.isCompletedDownload(preferredQuality) }
}

fun OfflineVideoFile.isCompletedDownload(preferredQuality: PreferredQuality): Boolean {
    return playbackUrl.isNotBlank() && bytes > 0L && matchesPreferredQuality(preferredQuality)
}

fun VideoVariant.maxKnownSourceQualityHeight(): Int {
    return listOf(this).knownSourceQualityHeights().maxOrNull() ?: 0
}

fun List<VideoVariant>.downloadCandidatesFor(requested: VideoVariant): List<VideoVariant> {
    val sameEpisode = filter { candidate ->
        candidate.animeId == requested.animeId && candidate.isSameEpisodeAs(requested)
    }.ifEmpty { listOf(requested) }
    val requestedVoiceKey = requested.matchingVoiceKey
    val sameVoiceEpisode = sameEpisode
        .filter { candidate -> candidate.matchingVoiceKey == requestedVoiceKey }
        .ifEmpty { listOf(requested) }

    return sameVoiceEpisode.sortedWith(
        compareByDescending<VideoVariant> { it.id == requested.id }
            .thenBy { it.index },
    )
}

fun List<VideoVariant>.downloadQualityCandidatesFor(
    requested: VideoVariant,
    allEpisodes: Boolean,
): List<VideoVariant> {
    if (!allEpisodes) return downloadCandidatesFor(requested)
    val requestedVoiceKey = requested.matchingVoiceKey
    return filter { candidate ->
        candidate.animeId == requested.animeId &&
            candidate.matchingVoiceKey == requestedVoiceKey
    }.ifEmpty { downloadCandidatesFor(requested) }
}

fun List<VideoVariant>.selectDownloadQualitySampleCandidate(): VideoVariant? {
    return minWithOrNull(downloadSampleComparator())
}

val VideoVariant.downloadSampleVoiceKey: String
    get() = downloadPlanVoiceKey

fun VideoVariant.sourceResolveIdentity(): String {
    if (id > 0L) return "id:$id"
    return listOf(
        animeId.toString(),
        matchingEpisodeKey,
        matchingVoiceKey,
        player.cleanVideoSourceLabel().lowercase(Locale.ROOT),
        url.sourceResolveFingerprint(),
        index.toString(),
    ).joinToString("|")
}

private fun downloadSampleComparator(): Comparator<VideoVariant> {
    return compareByDescending<VideoVariant> { it.maxKnownSourceQualityHeight() > 0 }
        .thenByDescending { it.maxKnownSourceQualityHeight() }
        .thenByDescending { it.episodeOrderValue() ?: Double.NEGATIVE_INFINITY }
        .thenBy { it.index }
        .thenBy { it.id }
}

private fun String.sourceResolveFingerprint(): String {
    return trim()
        .substringBefore('#')
        .lowercase(Locale.ROOT)
}

// DownloadSpeedLimiter
interface DownloadBandwidthLimiter {
    suspend fun throttle(bytes: Long)
}

object NoOpDownloadBandwidthLimiter : DownloadBandwidthLimiter {
    override suspend fun throttle(bytes: Long) = Unit
}

class DownloadSpeedLimiter(
    private val bytesPerSecondProvider: () -> Long,
    private val clockMs: () -> Long = { System.currentTimeMillis() },
    private val sleepMs: suspend (Long) -> Unit = { delay(it) },
) : DownloadBandwidthLimiter {
    private val lock = Any()
    private var windowStartMs = clockMs()
    private var windowBytes = 0L

    override suspend fun throttle(bytes: Long) {
        var remaining = bytes.coerceAtLeast(0L)
        while (remaining > 0L) {
            var unlimited = false
            val waitMs = synchronized(lock) {
                val limit = bytesPerSecondProvider().coerceAtLeast(0L)
                if (limit == 0L) {
                    unlimited = true
                    return@synchronized 0L
                }

                val now = clockMs()
                val elapsed = now - windowStartMs
                if (elapsed >= WINDOW_MS || elapsed < 0L) {
                    windowStartMs = now
                    windowBytes = 0L
                }

                val available = (limit - windowBytes).coerceAtLeast(0L)
                if (available > 0L) {
                    val granted = remaining.coerceAtMost(available)
                    windowBytes += granted
                    remaining -= granted
                    0L
                } else {
                    (WINDOW_MS - elapsed).coerceAtLeast(1L)
                }
            }

            if (unlimited) return
            if (waitMs > 0L) {
                sleepMs(waitMs)
            }
        }
    }

    private companion object {
        const val WINDOW_MS = 1_000L
    }
}

// FileSize
fun File.totalSizeBytes(): Long {
    return runCatching {
        when {
            !exists() -> 0L
            isFile -> length().coerceAtLeast(0L)
            isDirectory -> listFiles().orEmpty().sumOf { child -> child.totalSizeBytes() }
            else -> 0L
        }
    }.getOrDefault(0L)
}

// HlsDownloadPlanResolver
internal data class ResolvedHlsDownloadPlan(
    val plan: HlsSingleFilePlan,
    val qualityTitle: String,
)

internal fun YummyAnimeRepository.resolveHlsDownloadPlan(
    stream: ResolvedVideoStream,
    preferredQuality: PreferredQuality,
): ResolvedHlsDownloadPlan {
    val initialPlaylist = downloadText(stream.url, stream.headers)
    val variants = initialPlaylist.hlsVariants(stream.url)
    val selectedVariant = if (preferredQuality.height != null && variants.isNotEmpty()) {
        variants.selectExactQuality(preferredQuality)
            ?: throw IOException("HLS source does not contain ${preferredQuality.title} quality")
    } else {
        variants.selectForQuality(preferredQuality)
    }
    if (variants.isEmpty()) stream.requireExactDownloadQuality(preferredQuality)

    val mediaUrl = selectedVariant?.url ?: stream.url
    val mediaPlaylist = if (mediaUrl == stream.url) initialPlaylist else downloadText(mediaUrl, stream.headers)
    val plan = mediaPlaylist.toHlsSingleFilePlan(mediaUrl, selectedVariant?.bandwidth ?: 0)
    if (plan.segments.isEmpty()) {
        throw IOException("HLS playlist does not contain segments to download")
    }
    return ResolvedHlsDownloadPlan(
        plan = plan,
        qualityTitle = selectedVariant?.qualityTitle() ?: stream.qualityTitle(),
    )
}

// HlsDownloadProgress
internal fun hlsSegmentDownloadProgress(
    nextSegmentIndex: Int,
    segmentCount: Int,
    downloadedBytes: Long,
    sessionDownloadedBytes: Long,
    elapsedMs: Long,
    qualityTitle: String,
    voiceTitle: String,
): DownloadProgressInfo {
    val speed = (sessionDownloadedBytes * 1000L / elapsedMs.coerceAtLeast(1L)).coerceAtLeast(0L)
    val fraction = if (segmentCount > 0) {
        (nextSegmentIndex.toFloat() / segmentCount.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    return DownloadProgressInfo(
        fraction = fraction,
        downloadedBytes = downloadedBytes,
        totalBytes = -1L,
        bytesPerSecond = speed,
        qualityTitle = qualityTitle,
        voiceTitle = voiceTitle,
    )
}

// HlsDownloadSession
internal class HlsDownloadSession(
    val target: File,
    val plan: HlsSingleFilePlan,
    val qualityTitle: String,
    val voiceTitle: String,
    private val startedAtMs: Long = System.currentTimeMillis(),
    private val nowMs: () -> Long = System::currentTimeMillis,
) {
    val temp: File = target.partFile()
    private val stateFile = temp.hlsStateFile()
    private val signature = plan.signature()
    private var resumeState: HlsResumeState? = stateFile.readHlsResumeState(signature)
    private var sessionDownloadedBytes = 0L
    private var initWritten = resumeState?.initWritten ?: false
    var nextSegmentIndex: Int = resumeState?.nextSegmentIndex ?: 0
        private set

    fun prepareResume() {
        if (temp.exists() && temp.length() > 0L && resumeState == null) {
            temp.delete()
            stateFile.delete()
        }
    }

    fun pendingInitUrl(): String? = plan.initUrl?.takeUnless { initWritten }

    fun recordInit(payloadSize: Int) {
        sessionDownloadedBytes += payloadSize.toLong()
        initWritten = true
        stateFile.writeHlsResumeState(signature, initWritten, nextSegmentIndex)
    }

    fun recordSegment(index: Int, payloadSize: Int): DownloadProgressInfo {
        nextSegmentIndex = index + 1
        sessionDownloadedBytes += payloadSize.toLong()
        stateFile.writeHlsResumeState(signature, initWritten = true, nextSegmentIndex = nextSegmentIndex)
        return hlsSegmentDownloadProgress(
            nextSegmentIndex = nextSegmentIndex,
            segmentCount = plan.segments.size,
            downloadedBytes = temp.length().coerceAtLeast(0L),
            sessionDownloadedBytes = sessionDownloadedBytes,
            elapsedMs = (nowMs() - startedAtMs).coerceAtLeast(1L),
            qualityTitle = qualityTitle,
            voiceTitle = voiceTitle,
        )
    }

    fun complete() {
        stateFile.delete()
        temp.moveCompleteTo(target)
    }

    fun deletePartial() {
        temp.delete()
        stateFile.delete()
    }
}

// HlsDownloadWriter
internal suspend fun YummyAnimeRepository.writeHlsDownload(
    session: HlsDownloadSession,
    stream: ResolvedVideoStream,
    onProgress: (DownloadProgressInfo) -> Unit,
    isCancelled: () -> Boolean,
    bandwidthLimiter: DownloadBandwidthLimiter,
) {
    val keyCache = mutableMapOf<String, ByteArray>()
    FileOutputStream(session.temp, true).use { output ->
        session.pendingInitUrl()?.let { initUrl ->
            val bytes = downloadUrlBytes(initUrl, stream.headers, bandwidthLimiter)
            output.write(bytes)
            output.flush()
            session.recordInit(bytes.size)
        }
        while (session.nextSegmentIndex < session.plan.segments.size) {
            val index = session.nextSegmentIndex
            val segment = session.plan.segments[index]
            check(!isCancelled()) { "Download cancelled" }
            val payload = downloadHlsSegmentPayload(segment, index, session.plan, stream, keyCache, bandwidthLimiter)
            output.write(payload)
            output.flush()
            onProgress(session.recordSegment(index, payload.size))
        }
    }
}

private suspend fun YummyAnimeRepository.downloadHlsSegmentPayload(
    segment: HlsMediaSegment,
    index: Int,
    plan: HlsSingleFilePlan,
    stream: ResolvedVideoStream,
    keyCache: MutableMap<String, ByteArray>,
    bandwidthLimiter: DownloadBandwidthLimiter,
): ByteArray {
    val bytes = downloadUrlBytes(segment.url, stream.headers, bandwidthLimiter)
    val encryption = segment.encryption ?: return bytes
    return decryptHlsSegment(
        bytes = bytes,
        encryption = encryption,
        sequenceNumber = plan.mediaSequence + index,
        headers = stream.headers,
        keyCache = keyCache,
        bandwidthLimiter = bandwidthLimiter,
    )
}

// HlsOfflineMedia
internal data class HlsSingleFilePlan(
    val mediaSequence: Long,
    val initUrl: String?,
    val outputExtension: String,
    val variantBandwidth: Int,
    val segments: List<HlsMediaSegment>,
) {
    fun signature(): String {
        return buildString {
            append(mediaSequence)
            append('|').append(initUrl.orEmpty())
            append('|').append(outputExtension)
            append('|').append(variantBandwidth)
            segments.forEach { segment ->
                append('|').append(segment.url)
                append('@').append(segment.durationSeconds)
                append('@').append(segment.encryption?.method.orEmpty())
                append('@').append(segment.encryption?.keyUrl.orEmpty())
            }
        }
    }
}

internal data class HlsMediaSegment(
    val url: String,
    val encryption: HlsEncryption?,
    val durationSeconds: Double,
)

internal data class HlsEncryption(
    val method: String,
    val keyUrl: String?,
    val iv: ByteArray?,
)

internal fun String.toHlsSingleFilePlan(baseUrl: String, variantBandwidth: Int): HlsSingleFilePlan {
    val segments = mutableListOf<HlsMediaSegment>()
    var encryption: HlsEncryption? = null
    var initUrl: String? = null
    var mediaSequence = 0L
    var nextSegmentDuration = 0.0

    lineSequence().forEach { rawLine ->
        val line = rawLine.trim()
        when {
            line.startsWith("#EXT-X-MEDIA-SEQUENCE", ignoreCase = true) -> {
                mediaSequence = line.substringAfter(':', "").trim().toLongOrNull() ?: 0L
            }
            line.startsWith("#EXT-X-KEY", ignoreCase = true) -> {
                encryption = line.toHlsEncryption(baseUrl)
            }
            line.startsWith("#EXT-X-MAP", ignoreCase = true) -> {
                initUrl = line.hlsAttribute("URI")?.let { it.resolveUrlAgainst(baseUrl) }
            }
            line.startsWith("#EXTINF", ignoreCase = true) -> {
                nextSegmentDuration = line.substringAfter(':', "")
                    .substringBefore(',')
                    .trim()
                    .toDoubleOrNull()
                    ?: 0.0
            }
            line.isBlank() || line.startsWith("#") -> Unit
            else -> {
                segments += HlsMediaSegment(
                    url = line.resolveUrlAgainst(baseUrl),
                    encryption = encryption,
                    durationSeconds = nextSegmentDuration,
                )
                nextSegmentDuration = 0.0
            }
        }
    }

    val extension = when {
        initUrl != null -> "mp4"
        segments.any { it.url.fileExtensionForDownload() in setOf("m4s", "mp4") } -> "mp4"
        else -> "ts"
    }
    return HlsSingleFilePlan(
        mediaSequence = mediaSequence,
        initUrl = initUrl,
        outputExtension = extension,
        variantBandwidth = variantBandwidth,
        segments = segments,
    )
}

internal fun String.toHlsEncryption(baseUrl: String): HlsEncryption? {
    val method = hlsAttribute("METHOD").orEmpty()
    if (method.equals("NONE", ignoreCase = true)) return null
    val keyUrl = hlsAttribute("URI")?.let { it.resolveUrlAgainst(baseUrl) }
    return HlsEncryption(
        method = method,
        keyUrl = keyUrl,
        iv = hlsAttribute("IV")?.hexToBytes(),
    )
}

internal suspend fun YummyAnimeRepository.decryptHlsSegment(
    bytes: ByteArray,
    encryption: HlsEncryption,
    sequenceNumber: Long,
    headers: Map<String, String>,
    keyCache: MutableMap<String, ByteArray>,
    bandwidthLimiter: DownloadBandwidthLimiter,
): ByteArray {
    if (!encryption.method.equals("AES-128", ignoreCase = true)) {
        throw IOException("HLS ${encryption.method} is not supported for offline downloading")
    }
    val keyUrl = encryption.keyUrl ?: throw IOException("HLS encryption key was not found")
    val key = keyCache[keyUrl] ?: downloadUrlBytes(keyUrl, headers, bandwidthLimiter).also { keyCache[keyUrl] = it }
    if (key.size != 16) throw IOException("Invalid HLS encryption key")
    val iv = encryption.iv ?: sequenceNumber.toAesIv()
    val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
    cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
    return cipher.doFinal(bytes)
}

internal fun Long.toAesIv(): ByteArray {
    val result = ByteArray(16)
    var value = this
    for (index in 15 downTo 8) {
        result[index] = (value and 0xff).toByte()
        value = value ushr 8
    }
    return result
}

internal fun String.hexToBytes(): ByteArray? {
    val clean = removePrefix("0x").removePrefix("0X").trim()
    if (clean.length % 2 != 0) return null
    return runCatching {
        ByteArray(clean.length / 2) { index ->
            clean.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }.getOrNull()
}

// HlsPlaylist
internal data class HlsVariant(
    val height: Int?,
    val bandwidth: Int,
    val url: String,
)

internal fun String.selectBestHlsVariant(
    baseUrl: String,
    preferredQuality: PreferredQuality,
): HlsVariant? {
    return hlsVariants(baseUrl).selectForQuality(preferredQuality)
}

internal fun String.hlsVariants(baseUrl: String): List<HlsVariant> {
    val variants = mutableListOf<HlsVariant>()
    var pendingVariant: HlsVariantMetadata? = null
    lineSequence().forEach { rawLine ->
        val line = rawLine.trim()
        when {
            line.startsWith("#EXT-X-STREAM-INF", ignoreCase = true) -> {
                pendingVariant = line.toHlsVariantMetadata()
            }
            line.isNotBlank() && !line.startsWith("#") -> {
                pendingVariant?.let { metadata ->
                    variants += HlsVariant(
                        height = metadata.height,
                        bandwidth = metadata.bandwidth,
                        url = line.resolveUrlAgainst(baseUrl),
                    )
                }
                pendingVariant = null
            }
        }
    }
    return variants
}

internal fun String.hlsSourceQualities(): List<SourceQuality> {
    return lineSequence()
        .map { it.trim() }
        .filter { line -> line.startsWith("#EXT-X-STREAM-INF", ignoreCase = true) }
        .map { line ->
            val metadata = line.toHlsVariantMetadata()
            SourceQuality(
                height = metadata.height,
                bitrate = metadata.bandwidth,
            )
        }
        .toList()
        .normalizedSourceQualities()
}

internal fun List<HlsVariant>.selectForQuality(preferredQuality: PreferredQuality): HlsVariant? {
    return selectForPreferredQuality(
        preferredQuality = preferredQuality,
        height = { it.height },
        bitrate = { it.bandwidth },
    )
}

internal fun List<HlsVariant>.selectExactQuality(preferredQuality: PreferredQuality): HlsVariant? {
    val preferredHeight = preferredQuality.height ?: return selectForQuality(preferredQuality)
    return filter { it.height == preferredHeight }
        .maxWithOrNull(compareBy<HlsVariant> { it.bandwidth })
}

internal fun String.hlsAttribute(name: String): String? {
    val pattern = Regex("""(?i)(?:^|[:,])\s*$name=(?:"([^"]*)"|([^,]*))""")
    val match = pattern.find(this) ?: return null
    return match.groupValues.getOrNull(1)?.takeIf { it.isNotBlank() }
        ?: match.groupValues.getOrNull(2)?.takeIf { it.isNotBlank() }
}

private data class HlsVariantMetadata(
    val height: Int?,
    val bandwidth: Int,
)

private fun String.toHlsVariantMetadata(): HlsVariantMetadata {
    return HlsVariantMetadata(
        height = hlsResolutionHeightRegex
            .find(this)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull(),
        bandwidth = hlsBandwidthRegex
            .find(this)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?: 0,
    )
}

private val hlsResolutionHeightRegex = Regex("""(?i)RESOLUTION\s*=\s*\d+\s*x\s*(\d+)""")
private val hlsBandwidthRegex = Regex("""(?i)BANDWIDTH\s*=\s*(\d+)""")

// HlsPlaylistDownload
internal fun YummyAnimeRepository.downloadText(url: String, headers: Map<String, String>): String {
    val request = Request.Builder()
        .url(url)
        .headers(headers.toOkHttpHeaders())
        .build()
    return downloadClient.newCall(request).execute().use { response ->
        if (!response.isSuccessful) throw IOException("Download HTTP ${response.code}")
        response.body?.string().orEmpty().takeIf { it.isNotBlank() }
            ?: throw IOException("Empty playlist")
    }
}

// HlsResourceDownload
internal suspend fun YummyAnimeRepository.downloadUrlBytes(
    url: String,
    headers: Map<String, String>,
    bandwidthLimiter: DownloadBandwidthLimiter,
): ByteArray {
    var attempt = 0
    while (true) {
        try {
            return downloadUrlBytesOnce(url, headers, bandwidthLimiter)
        } catch (throwable: Throwable) {
            throwable.throwIfCancellation()
            attempt += 1
            if (attempt >= DOWNLOAD_RETRY_COUNT) throw throwable
            delay(DOWNLOAD_RETRY_DELAY_MS * attempt)
        }
    }
}

private suspend fun YummyAnimeRepository.downloadUrlBytesOnce(
    url: String,
    headers: Map<String, String>,
    bandwidthLimiter: DownloadBandwidthLimiter,
): ByteArray {
    val request = Request.Builder()
        .url(url)
        .headers(headers.toOkHttpHeaders())
        .build()
    return downloadClient.newCall(request).execute().use { response ->
        if (!response.isSuccessful) throw IOException("Download HTTP ${response.code}")
        val body = response.body ?: throw IOException("Empty HLS resource")
        body.byteStream().use { input ->
            input.readBytes(bandwidthLimiter)
        }
    }
}

private suspend fun InputStream.readBytes(
    bandwidthLimiter: DownloadBandwidthLimiter,
): ByteArray = ByteArrayOutputStream().use { output ->
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (true) {
        val read = read(buffer)
        if (read <= 0) break
        bandwidthLimiter.throttle(read.toLong())
        output.write(buffer, 0, read)
    }
    output.toByteArray()
}

// HlsSubtitleBodyAssembler
internal fun assembleHlsSubtitleBody(
    playlist: String,
    playlistUrl: String,
    loadSegment: (String) -> String,
): String? {
    val cueSegments = playlist.materializedSubtitleSegments(playlistUrl, loadSegment)
    val nonBlankSegments = cueSegments.filter { segment -> segment.body.isNotBlank() }
    if (nonBlankSegments.isEmpty()) return null

    val topLevelBlocks = cueSegments
        .flatMap { segment -> segment.topLevelBlocks }
        .distinct()
    val shouldShiftCueTimes = nonBlankSegments.shouldShiftWebVttCueTimes()
    val cues = nonBlankSegments
        .map { segment -> segment.normalizedWebVttCueBody(shouldShiftCueTimes).trim() }
        .filter(String::isNotBlank)
    return buildWebVttDocument(topLevelBlocks, cues)
}

private fun String.materializedSubtitleSegments(
    playlistUrl: String,
    loadSegment: (String) -> String,
): List<MaterializedSubtitleSegment> {
    if (trimStart().startsWith("WEBVTT", ignoreCase = true)) {
        return listOf(webVttCueBody().toMaterializedSegment())
    }
    return hlsSubtitleSegments(playlistUrl).map { segment ->
        val body = loadSegment(segment.url).webVttCueBody()
        body.toMaterializedSegment(
            offsetMs = segment.offsetMs,
            durationMs = segment.durationMs,
        )
    }
}

private fun WebVttCueBody.toMaterializedSegment(
    offsetMs: Long = 0L,
    durationMs: Long = 0L,
): MaterializedSubtitleSegment {
    return MaterializedSubtitleSegment(
        body = text,
        offsetMs = offsetMs,
        durationMs = durationMs,
        localMapMs = localMapMs,
        topLevelBlocks = topLevelBlocks,
    )
}

private fun buildWebVttDocument(
    topLevelBlocks: List<String>,
    cues: List<String>,
): String {
    return buildString {
        append("WEBVTT\n\n")
        if (topLevelBlocks.isNotEmpty()) {
            append(topLevelBlocks.joinToString("\n\n"))
            append("\n\n")
        }
        append(cues.joinToString("\n\n"))
        append('\n')
    }
}

// HlsSubtitlePlaylistParser
internal fun String.isHlsPlaylistUrl(): Boolean {
    val lower = substringBefore('?').substringBefore('#').lowercase()
    return lower.endsWith(".m3u8") || "mpegurl" in lower
}

internal fun String.hlsSubtitleSegments(baseUrl: String): List<HlsSubtitleSegment> {
    val segments = mutableListOf<HlsSubtitleSegment>()
    var offsetMs = 0L
    var pendingDurationMs = 0L

    lineSequence().forEach { rawLine ->
        val line = rawLine.trim()
        when {
            line.startsWith("#EXTINF", ignoreCase = true) -> {
                pendingDurationMs = line.substringAfter(':')
                    .substringBefore(',')
                    .toDoubleOrNull()
                    ?.let { (it * 1000.0).toLong() }
                    ?: 0L
            }
            line.isNotBlank() && !line.startsWith("#") -> {
                segments += HlsSubtitleSegment(
                    url = line.resolveUrlAgainst(baseUrl),
                    offsetMs = offsetMs,
                    durationMs = pendingDurationMs,
                )
                offsetMs += pendingDurationMs
                pendingDurationMs = 0L
            }
        }
    }

    return segments
}

internal fun String.looksLikeStandaloneHlsWebVttSegment(): Boolean {
    val normalized = replace("\uFEFF", "")
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .trimStart()
    if (!normalized.startsWith("WEBVTT", ignoreCase = true)) return false

    return normalized
        .lineSequence()
        .drop(1)
        .takeWhile { it.isNotBlank() }
        .any { line -> line.trim().startsWith("X-TIMESTAMP-MAP", ignoreCase = true) }
}

// HlsVideoDownloadEntry
internal suspend fun YummyAnimeRepository.downloadHlsAsSingleVideoFile(
    storage: OfflineAnimeStorage,
    video: VideoVariant,
    stream: ResolvedVideoStream,
    preferredQuality: PreferredQuality,
    onProgress: (DownloadProgressInfo) -> Unit,
    isCancelled: () -> Boolean,
    deletePartialOnCancel: () -> Boolean,
    bandwidthLimiter: DownloadBandwidthLimiter,
): File {
    val resolved = resolveHlsDownloadPlan(stream, preferredQuality)
    val startedAtMs = System.currentTimeMillis()
    val voiceTitle = video.downloadVoiceTitle()
    val target = storage.targetFile(
        video,
        resolved.plan.outputExtension,
        resolved.qualityTitle.ifBlank { "auto" },
    )
    if (target.isCompletedDownloadFile()) {
        onProgress(target.completedDownloadProgress(target.downloadQualityTitle(), voiceTitle))
        return target
    }

    val session = HlsDownloadSession(
        target = target,
        plan = resolved.plan,
        qualityTitle = resolved.qualityTitle,
        voiceTitle = voiceTitle,
        startedAtMs = startedAtMs,
    )
    session.prepareResume()
    try {
        writeHlsDownload(session, stream, onProgress, isCancelled, bandwidthLimiter)
        session.complete()
    } catch (throwable: Throwable) {
        throwable.throwIfCancellation()
        if (isCancelled() || throwable.message.equals("Download cancelled", ignoreCase = true)) {
            if (deletePartialOnCancel()) session.deletePartial()
        }
        throw throwable
    }

    onProgress(target.completedDownloadProgress(resolved.qualityTitle, voiceTitle))
    return target
}

// VideoDownloadFiles
internal fun ResolvedVideoStream.withSourceSubtitleVideo(video: VideoVariant): ResolvedVideoStream {
    val sourceKey = video.matchingSourceKey.takeIf { it.isNotBlank() && hasResolvedSubtitles } ?: return this
    return copy(sourceSubtitleSourceKeys = sourceSubtitleSourceKeys + sourceKey)
}

internal fun Throwable.throwIfCancellation() {
    if (this is CancellationException) throw this
}

internal fun File.partFile(): File {
    return File(parentFile, "$name.part")
}

internal fun File.hlsStateFile(): File {
    return File(parentFile, "$name.state")
}

internal data class HlsResumeState(
    val initWritten: Boolean,
    val nextSegmentIndex: Int,
)

internal fun File.readHlsResumeState(signature: String): HlsResumeState? {
    if (!exists()) return null
    val lines = runCatching { readLines() }.getOrNull() ?: return null
    if (lines.getOrNull(0) != signature) return null
    return HlsResumeState(
        initWritten = lines.getOrNull(1)?.toBooleanStrictOrNull() ?: false,
        nextSegmentIndex = lines.getOrNull(2)?.toIntOrNull()?.coerceAtLeast(0) ?: 0,
    )
}

internal fun File.writeHlsResumeState(
    signature: String,
    initWritten: Boolean,
    nextSegmentIndex: Int,
) {
    parentFile?.mkdirs()
    writeText(
        listOf(signature, initWritten.toString(), nextSegmentIndex.coerceAtLeast(0).toString())
            .joinToString("\n"),
    )
}

internal fun File.moveCompleteTo(target: File) {
    target.delete()
    if (!renameTo(target)) {
        inputStream().use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        delete()
    }
}

internal fun String.parseContentRangeTotal(): Long? {
    return substringAfter('/', "")
        .takeIf { it.isNotBlank() && it != "*" }
        ?.toLongOrNull()
}

internal fun ResolvedVideoStream.qualityTitle(): String {
    return selectedVideoHeight?.takeIf { it > 0 }?.let { "${it}p" }
        ?: maxVideoHeight?.takeIf { it > 0 }?.let { "${it}p" }
        ?: url.detectDownloadQualityHeight()?.let { "${it}p" }
        ?: ""
}

internal fun HlsVariant.qualityTitle(): String {
    return height.validVideoQualityHeight()?.let { "${it}p" }.orEmpty()
}

internal const val DOWNLOAD_RETRY_COUNT = 5
internal const val DOWNLOAD_RETRY_DELAY_MS = 700L

// VideoDownloadVoices
val VideoVariant.downloadPlanVoiceKey: String
    get() = matchingVoiceKey.ifBlank { groupKey.lowercase(Locale.ROOT) }

val VideoVariant.downloadPlanVoiceTitle: String
    get() = matchingVoiceTitle
        .ifBlank { dubbing.cleanVideoSourceLabel() }
        .ifBlank { groupTitle }
        .ifBlank { player.cleanVideoSourceLabel() }
        .ifBlank { "Voice" }

fun List<VideoVariant>.siteDefaultVideo(): VideoVariant? {
    return firstOrNull()
}

fun Iterable<VideoVariant>.siteOrderedVoiceKeys(): List<String> {
    val keys = LinkedHashSet<String>()
    forEach { video ->
        video.downloadPlanVoiceKey
            .takeIf { it.isNotBlank() }
            ?.let(keys::add)
    }
    return keys.toList()
}

fun Iterable<VideoVariant>.siteDefaultVoiceKey(): String? {
    return siteOrderedVoiceKeys().firstOrNull()
}

fun Iterable<VideoVariant>.siteVoiceOrderIndex(): Map<String, Int> {
    return siteOrderedVoiceKeys()
        .withIndex()
        .associate { (index, key) -> key to index }
}

fun List<VideoVariant>.downloadVoiceOptions(selectedVideo: VideoVariant?): List<VideoVariant> {
    val siteVoiceOrder = siteVoiceOrderIndex()
    return groupBy { it.downloadPlanVoiceKey }
        .values
        .mapNotNull { group ->
            group.minWithOrNull(
                compareBy<VideoVariant> { if (selectedVideo != null && it.groupKey == selectedVideo.groupKey) 0 else 1 }
                    .thenBy { sourceProviderRank(it.player) }
                    .thenByDescending { it.isOfflineAvailable }
                    .thenBy { it.index }
                    .thenBy { it.id },
            )
        }
        .sortedWith(
            compareBy<VideoVariant> {
                if (selectedVideo != null && it.downloadPlanVoiceKey == selectedVideo.downloadPlanVoiceKey) 0 else 1
            }
                .thenBy { siteVoiceOrder[it.downloadPlanVoiceKey] ?: Int.MAX_VALUE }
                .thenBy { it.downloadPlanVoiceTitle },
        )
}

fun List<VideoVariant>.downloadEpisodeCandidates(video: VideoVariant): List<VideoVariant> {
    return filter { it.isSameEpisodeAs(video) }.ifEmpty { listOf(video) }
}

fun VideoVariant.downloadVoiceEpisodeCount(videos: List<VideoVariant>): Int {
    return videos
        .asSequence()
        .filter { it.downloadPlanVoiceKey == downloadPlanVoiceKey }
        .map { it.matchingEpisodeKey }
        .distinct()
        .count()
        .coerceAtLeast(1)
}

fun VideoVariant.downloadedVoiceEpisodeCount(videos: List<VideoVariant>): Int {
    return downloadedEpisodeCountForVoice(videos)
}

fun VideoVariant.downloadedQualityEpisodeCount(
    videos: List<VideoVariant>,
    quality: PreferredQuality,
): Int {
    return videos
        .asSequence()
        .filter { it.downloadPlanVoiceKey == downloadPlanVoiceKey }
        .filter { candidate -> candidate.hasDownloadedQuality(quality) }
        .map { it.matchingEpisodeKey }
        .distinct()
        .count()
}
