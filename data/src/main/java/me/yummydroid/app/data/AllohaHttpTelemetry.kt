package me.yummydroid.app.data

import java.io.Closeable
import java.io.IOException
import java.util.ArrayDeque
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToLong
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MultipartBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okio.BufferedSink

/** Browser HTTP identity is independent of the edge token used for media requests. */
data class AllohaHttpTelemetryDescriptor(
    val providerPageUrl: String,
    val headers: Map<String, String>,
    val token: String,
    val domain: String,
    val fileId: String,
    val statType: String = "mgpo",
    val environment: JsonObject,
    val statInfo: JsonObject,
    val isTrailer: Boolean = false,
    val initialEnvelope: JsonObject? = null,
    val observedEvents: List<JsonObject> = emptyList(),
    val observedStatPercents: Set<Int> = emptySet(),
    val initialStatSent: Boolean = false,
    val pageEpochMs: Long,
    val adBlock: Boolean? = null,
    val endpointHeaders: Map<String, Map<String, String>> = emptyMap(),
)

/**
 * Continues the captured HTTP view, without replaying browser/ad events or sending media requests.
 * The website samples /events every 10s, flushes every 30s and has a separate legacy /stat channel.
 * HTTP errors are deliberately isolated from the media/control session.
 */
internal class AllohaHttpTelemetry(
    private val descriptor: AllohaHttpTelemetryDescriptor,
    client: OkHttpClient,
    private val now: () -> Long = System::currentTimeMillis,
    private val heartbeatIntervalMs: Long = 10_000,
    private val flushIntervalMs: Long = 30_000,
    private val maxEventQueue: Int = 1_000,
    private val closeGraceMs: Long = 5_000,
    private val autoSchedule: Boolean = true,
) : Closeable {
    private val lock = Any()
    private val page = descriptor.providerPageUrl.toHttpUrl()
    private val origin = page.newBuilder().encodedPath("/").query(null).fragment(null).build()
    private val http = client.newBuilder().followRedirects(false).followSslRedirects(false)
        .retryOnConnectionFailure(false).callTimeout(closeGraceMs, TimeUnit.MILLISECONDS).build()
    private val scheduler = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "alloha-http-telemetry").apply { isDaemon = true }
    }
    private val events = ArrayDeque<JsonObject>()
    private data class Pending(val path: String, val body: RequestBody)
    private val pending = ArrayDeque<Pending>()
    private var inFlight: Call? = null
    private var timer: ScheduledFuture<*>? = null
    private var closing = false
    private var stopped = false
    private var finished = false
    private var restriction: Int? = null
    private var sentRequests = 0
    private var failedRequests = 0
    private var discardedRequests = 0
    private var droppedEvents = 0L
    private val envelope = descriptor.initialEnvelope ?: JsonObject(emptyMap())
    private val baseline = envelope["handoff"] as? JsonObject ?: JsonObject(emptyMap())
    private val committed = descriptor.observedEvents.ifEmpty {
        (envelope["events"] as? JsonArray)?.filterIsInstance<JsonObject>().orEmpty()
    }
    private val observedTypes = committed.mapNotNull { it.text("event_type") }.toMutableSet()
    private var clientSessionId = envelope.text("clientSessionId") ?: newId()
    private var clientRequestId = envelope.text("clientRequestId") ?: newId()
    private val playerInitAt = envelope.number("playerInitAt")?.toLong() ?: now()
    private val environment = envelope["env"] as? JsonObject ?: descriptor.environment
    private val lastHeartbeat = committed.lastOrNull { it.text("event_type") == "heartbeat" }
    private val lastEvent = committed.lastOrNull()
    private var playedSeconds = baseline.number("playedSeconds") ?: max(
        committed.sumOf { it.number("playedSecondsDelta") ?: 0.0 },
        committed.maxOfOrNull { it.number("watchTimeSec") ?: 0.0 } ?: 0.0,
    )
    private var lastHeartbeatPlayed = baseline.number("lastHeartbeatPlayed")
        ?: committed.sumOf { it.number("playedSecondsDelta") ?: 0.0 }
    private var inheritedBytes = (baseline.number("bytesLoadedTotal")
        ?: lastHeartbeat?.number("bytesLoadedTotal") ?: 0.0).toLong().coerceAtLeast(0)
    private var inheritedDrops = (baseline.number("droppedFrames")
        ?: lastHeartbeat?.number("droppedFrames") ?: 0.0).toLong().coerceAtLeast(0)
    private var lastHeartbeatBytes = (baseline.number("lastHeartbeatBytes")
        ?: lastHeartbeat?.number("bytesLoadedTotal") ?: 0.0).toLong().coerceAtLeast(0)
    private var lastHeartbeatDrops = (baseline.number("lastHeartbeatDropped")
        ?: lastHeartbeat?.number("droppedFrames") ?: 0.0).toLong().coerceAtLeast(0)
    private var rebufferCount = (baseline.number("rebufferCount")
        ?: lastHeartbeat?.number("rebufferCountSession") ?: 0.0).toLong().coerceAtLeast(0)
    private var rebufferTotalMs = (baseline.number("rebufferTotalMs")
        ?: lastHeartbeat?.number("rebufferTotalMsSession") ?: 0.0).toLong().coerceAtLeast(0)
    private var rebufferStartedAt: Long? = null
    private var pauseStartedAt: Long? = lastEvent?.takeIf { it.text("event_type") == "pause" }
        ?.number("at")?.toLong()
    private var playAttemptAt = committed.firstOrNull { it.text("event_type") == "view_start" }
        ?.number("at")?.toLong()
    private var viewStarted = observedTypes.any { it in setOf("view_start", "first_frame", "heartbeat") }
    private var firstFrame = observedTypes.any { it in setOf("first_frame", "heartbeat") }
    private var ready = observedTypes.any { it in setOf("playback_ready", "first_frame", "heartbeat") }
    private var fileLoadComplete = observedTypes.any { it in setOf("file_load_complete", "first_frame", "heartbeat") }
    private var initializedState = false
    private var hasCapturedBaseline = true
    private var nativeBytesAtViewStart = 0L
    private var nativeDropsAtViewStart = 0L
    private var state = PlaybackRuntimeState()
    private var knownQuality = lastEvent?.number("quality")?.toInt()
    private var knownAudio = lastEvent?.text("audioTrack")
    private var knownSubtitle = lastEvent?.text("subtitle")
    private var previousPositionMs: Long? = null
    private var skipNextDelta = true
    private var seeking = false
    private var seekFromMs = 0L
    private var switchStartedAt: Long? = null
    private var switchType: String? = null
    private var statStarted = descriptor.initialStatSent
    private val eventPercents = committed.filter { it.text("event_type") == "view_percent" }
        .mapNotNull { it.number("percent")?.toInt() }.toMutableSet()
    private val statPercents = descriptor.observedStatPercents.toMutableSet()
    private var nextHeartbeatAt = nextBoundary(now(), heartbeatIntervalMs)
    private var nextFlushAt = nextBoundary(now(), flushIntervalMs)

    init {
        require(descriptor.token.isNotBlank())
        require(heartbeatIntervalMs > 0 && flushIntervalMs > 0 && closeGraceMs > 0 && maxEventQueue > 0)
        startTimerLocked()
    }

    fun diagnostics(): String = synchronized(lock) {
        "httpSent=$sentRequests httpFailed=$failedRequests httpRestricted=$restriction " +
            "httpPending=${pending.size} httpActive=${inFlight != null} " +
            "eventQueue=${events.size} droppedEvents=$droppedEvents discardedHttp=$discardedRequests"
    }

    fun update(next: PlaybackRuntimeState) = synchronized(lock) {
        if (finished && !stopped && !closing && restriction == null && next.playWhenReady &&
            (next.isPlaying || next.positionMs < state.positionMs)) resetViewLocked()
        if (!accepting()) return@synchronized
        val previous = state
        val hadState = initializedState
        val timestamp = now()
        val oldPosition = previousPositionMs
        if (!skipNextDelta && oldPosition != null && previous.isPlaying && !previous.isSeeking &&
            !next.isSeeking && !seeking) {
            val delta = (next.positionMs - oldPosition) / 1_000.0
            if (delta > 0 && delta < 2) playedSeconds += delta
        }
        skipNextDelta = false
        previousPositionMs = next.positionMs
        state = next
        initializedState = true
        if (next.playWhenReady && !viewStarted) startView(timestamp)
        if (next.playWhenReady && !statStarted) {
            statStarted = true
            queueStat(null)
        }
        val previousIntent = if (hadState) previous.playWhenReady else
            if (hasCapturedBaseline) (lastEvent?.get("paused") as? JsonPrimitive)?.booleanOrNull?.not() else null
        if (previousIntent != null && previousIntent != next.playWhenReady || previousIntent == null && next.playWhenReady) {
            if (next.playWhenReady) {
                emit("play", obj("pauseDurationMs" to pauseStartedAt?.let { elapsed(timestamp, it) }))
                pauseStartedAt = null
            } else if (viewStarted) {
                pauseStartedAt = timestamp
                emit("pause")
            }
        }
        val previousQuality = knownQuality
        if (next.qualityHeight != null) {
            if (previousQuality != null && next.qualityHeight != previousQuality) {
                beginSwitch("quality", timestamp)
                emit("quality_switch", obj("qualityBefore" to previousQuality,
                    "qualityAfter" to next.qualityHeight,
                    "isUpscale" to (next.qualityHeight > previousQuality).takeIf { it },
                    "isDownscale" to (next.qualityHeight < previousQuality).takeIf { it },
                    "bandwidthEstimate" to next.bandwidthEstimate))
            }
            knownQuality = next.qualityHeight
        }
        val previousAudio = knownAudio
        if (next.audioTrackLabel != null) {
            if (previousAudio != null && next.audioTrackLabel != previousAudio) {
                beginSwitch("audio", timestamp)
                emit("audio_switch", obj("audioBefore" to previousAudio, "audioAfter" to next.audioTrackLabel))
            }
            knownAudio = next.audioTrackLabel
        }
        // A source reload temporarily has no selected tracks; that is not a subtitle toggle.
        if (next.renderedFirstFrame || next.isPlaying) {
            if (knownSubtitle != next.subtitleLanguage) {
                emit("subtitle_change", obj("subtitleBefore" to knownSubtitle,
                    "subtitleAfter" to next.subtitleLanguage))
                knownSubtitle = next.subtitleLanguage
            }
        }
        if (hadState && next.speed != previous.speed) {
            emit("rate_change", obj("rateBefore" to previous.speed, "rateAfter" to next.speed))
        }
        if (next.isSeeking && !seeking) beginSeek(previous.positionMs)
        if (!next.isSeeking && seeking) endSeek(next.positionMs)
        if (next.isBuffering && !previous.isBuffering && firstFrame && !seeking) {
            rebufferStartedAt = timestamp
            emit("rebuffer_start", obj("bandwidthEstimate" to next.bandwidthEstimate,
                "bufferLengthAtStart" to seconds(next.bufferedDurationMs)))
        } else if (!next.isBuffering && rebufferStartedAt != null) {
            val duration = elapsed(timestamp, requireNotNull(rebufferStartedAt))
            rebufferStartedAt = null
            rebufferCount++
            rebufferTotalMs += duration
            emit("rebuffer_end", obj("rebufferDurationMs" to duration, "rebufferCountTotal" to rebufferCount,
                "rebufferTotalMsSession" to rebufferTotalMs, "bandwidthEstimate" to next.bandwidthEstimate))
        }
        if (viewStarted && !ready && next.durationMs > 0 && next.bufferedDurationMs > 0) {
            ready = true
            emit("playback_ready", obj("timeToPlaybackReadyMs" to playAttemptAt?.let { elapsed(timestamp, it) },
                "bandwidthEstimate" to next.bandwidthEstimate))
        }
        if (next.renderedFirstFrame && viewStarted && !firstFrame) {
            firstFrame = true
            val firstFrameMs = playAttemptAt?.let { elapsed(timestamp, it) }
            emit("first_frame", obj("timeToFirstFrameMs" to firstFrameMs,
                "adDurationMs" to null, "viewStartSource" to "playing"))
            if (!fileLoadComplete) {
                fileLoadComplete = true
                emit("file_load_complete", obj("fileChangeType" to "initial", "viewStartSource" to "playing",
                    "totalFileChangeMs" to null, "backendMs" to null, "manifestLoadMs" to null,
                    "userWaitMs" to null, "playToFirstFrameMs" to firstFrameMs,
                    "adDurationMs" to null, "segmentLoadMs" to null, "autoplay" to null))
            }
        }
        if (switchType != null && switchStartedAt != timestamp && next.isPlaying && !next.isBuffering &&
            next.renderedFirstFrame && (next.positionMs > previous.positionMs || !previous.isPlaying)) {
            emit("switch_complete", obj("switchType" to switchType,
                "switchToFirstFrameMs" to switchStartedAt?.let { elapsed(timestamp, it) },
                "manifestLoadMs" to null, "segmentLoadMs" to null,
                "bandwidthEstimate" to next.bandwidthEstimate))
            switchType = null
            switchStartedAt = null
        }
        if (viewStarted && next.durationMs > 0 && (next.isPlaying || next.positionMs > 0)) {
            val exactPercent = next.positionMs.toDouble() / next.durationMs * 100
            for (percent in MILESTONES) {
                if (floor(exactPercent) >= percent && eventPercents.add(percent)) {
                    emit("view_percent", obj("percent" to percent, "watchTimeSec" to playedSeconds.roundToLong()))
                }
            }
            val statPercent = exactPercent.roundToLong().toInt()
            if (statPercent in MILESTONES && statPercents.add(statPercent)) queueStat(statPercent)
        }
    }

    fun seek(positionMs: Long) = synchronized(lock) {
        if (finished && !stopped && !closing && restriction == null) resetViewLocked()
        if (!accepting()) return@synchronized
        if (!seeking) beginSeek(state.positionMs)
        state = state.copy(positionMs = positionMs, isSeeking = true)
        previousPositionMs = positionMs
        skipNextDelta = true
        // PlayerCore reports the discontinuity before its seeking-state update. Complete only
        // when a subsequent runtime snapshot confirms seeking has stopped.
    }

    fun playbackError(errorCode: String) = synchronized(lock) {
        if (!accepting()) return@synchronized
        // This is the site's load-failure event. Native errors cannot supply browser/HLS internals.
        emit("file_load_failed", obj("fileChangeType" to "initial",
            "failedAfterMs" to playAttemptAt?.let { elapsed(now(), it) },
            "reason" to errorCode.filter { it.isLetterOrDigit() || it == '_' }.take(80)))
        flushLocked(true)
    }

    fun ended() = synchronized(lock) {
        if (!accepting()) return@synchronized
        finishLocked(true)
    }

    override fun close(): Unit = synchronized(lock) {
        if (closing || stopped) return@synchronized
        if (!finished) finishLocked(false)
        closing = true
        timer?.cancel(false)
        timer = null
        if (inFlight == null && pending.isEmpty()) stopLocked()
        else scheduler.schedule({ synchronized(lock) { stopLocked() } }, closeGraceMs, TimeUnit.MILLISECONDS)
    }

    /** Clock-driven entry point keeps cadence tests independent of wall-clock playback. */
    internal fun tick() = synchronized(lock) {
        if (!accepting()) return@synchronized
        val timestamp = now()
        if (timestamp >= nextHeartbeatAt) {
            nextHeartbeatAt = nextBoundary(timestamp, heartbeatIntervalMs)
            if (initializedState && firstFrame && state.playWhenReady) heartbeat()
        }
        if (timestamp >= nextFlushAt) {
            nextFlushAt = nextBoundary(timestamp, flushIntervalMs)
            flushLocked(false)
        }
    }

    internal fun flush() = synchronized(lock) { if (accepting()) flushLocked(false) }

    private fun startTimerLocked() {
        if (autoSchedule && timer == null && !stopped && !closing) {
            timer = scheduler.scheduleAtFixedRate({ runCatching { tick() } }, 250, 250, TimeUnit.MILLISECONDS)
        }
    }

    private fun resetViewLocked() {
        finished = false
        clientSessionId = newId()
        clientRequestId = newId()
        nativeBytesAtViewStart = state.completedMediaBytes
        nativeDropsAtViewStart = state.droppedVideoFrames
        inheritedBytes = 0
        inheritedDrops = 0
        playedSeconds = 0.0
        lastHeartbeatPlayed = 0.0
        lastHeartbeatBytes = 0
        lastHeartbeatDrops = 0
        rebufferCount = 0
        rebufferTotalMs = 0
        rebufferStartedAt = null
        pauseStartedAt = null
        playAttemptAt = null
        switchStartedAt = null
        switchType = null
        viewStarted = false
        firstFrame = false
        ready = false
        fileLoadComplete = false
        initializedState = false
        hasCapturedBaseline = false
        knownQuality = null
        knownAudio = null
        knownSubtitle = null
        previousPositionMs = null
        skipNextDelta = true
        seeking = false
        statStarted = false
        eventPercents.clear()
        statPercents.clear()
        nextHeartbeatAt = nextBoundary(now(), heartbeatIntervalMs)
        nextFlushAt = nextBoundary(now(), flushIntervalMs)
        startTimerLocked()
    }

    private fun startView(timestamp: Long) {
        viewStarted = true
        playAttemptAt = timestamp
        emit("view_start", obj("fileChangeType" to "initial", "autoplay" to null,
            "resumeFrom" to seconds(state.positionMs), "startupQuality" to state.qualityHeight,
            "startupAudio" to state.audioTrackLabel, "startupBitrate" to state.videoBitrate,
            "startupFrameRate" to state.videoFrameRate, "renderWidth" to null, "renderHeight" to null))
        emit("startup", obj("fileChangeType" to "initial", "playerStartupMs" to elapsed(timestamp, playerInitAt),
            "manifestLoadMs" to null, "userWaitMs" to null, "bandwidthEstimate" to state.bandwidthEstimate,
            "startupBitrate" to state.videoBitrate))
    }

    private fun beginSwitch(type: String, timestamp: Long) {
        flushLocked(false)
        clientRequestId = newId()
        switchType = type
        switchStartedAt = timestamp
    }

    private fun beginSeek(fromMs: Long) {
        seeking = true
        seekFromMs = fromMs
        skipNextDelta = true
        emit("seek_start", obj("seekFrom" to seconds(fromMs)))
    }

    private fun endSeek(toMs: Long) {
        seeking = false
        skipNextDelta = false
        emit("seek_end", obj("seekFrom" to seconds(seekFromMs), "seekTo" to seconds(toMs),
            "seekDistance" to (toMs - seekFromMs) / 1_000.0))
    }

    private fun heartbeat() {
        val bytes = inheritedBytes + (state.completedMediaBytes - nativeBytesAtViewStart).coerceAtLeast(0)
        val drops = inheritedDrops + (state.droppedVideoFrames - nativeDropsAtViewStart).coerceAtLeast(0)
        emit("heartbeat", obj("bandwidthEstimate" to state.bandwidthEstimate,
            "droppedFrames" to drops, "droppedFramesDelta" to (drops - lastHeartbeatDrops).coerceAtLeast(0),
            "playedSecondsDelta" to (max(0.0, playedSeconds - lastHeartbeatPlayed) * 1_000).roundToLong() / 1_000.0,
            "isBuffering" to state.isBuffering, "isSeeking" to (seeking || state.isSeeking),
            "playbackRate" to state.speed, "currentBitrate" to state.videoBitrate,
            "currentFrameRate" to state.videoFrameRate, "videoWidth" to state.videoWidth,
            "videoHeight" to state.videoHeight, "isUpscaling" to null,
            "rebufferCountSession" to rebufferCount, "rebufferTotalMsSession" to rebufferTotalMs,
            "bytesLoadedTotal" to bytes, "bytesLoadedDelta" to (bytes - lastHeartbeatBytes).coerceAtLeast(0)))
        lastHeartbeatPlayed = playedSeconds
        lastHeartbeatBytes = bytes
        lastHeartbeatDrops = drops
    }

    private fun finishLocked(completed: Boolean) {
        if (finished) return
        if (viewStarted) {
            if (state.playWhenReady) {
                state = state.copy(playWhenReady = false, isPlaying = false)
                emit("pause")
            }
            emit("view_finish", obj("watchTimeSec" to playedSeconds.roundToLong(), "completed" to completed))
        }
        flushLocked(true)
        finished = true
        timer?.cancel(false)
        timer = null
    }

    private fun emit(type: String, extra: JsonObject = JsonObject(emptyMap())) {
        if (stopped || closing || restriction != null) return
        if (events.size >= maxEventQueue) {
            events.removeFirst()
            droppedEvents++
        }
        val timestamp = now()
        events.addLast(JsonObject(obj("url" to null, "manifestUrl" to state.manifestUrl,
            "currentTime" to seconds(state.positionMs), "duration" to state.durationMs.takeIf { it > 0 }?.let(::seconds),
            "bufferLength" to seconds(state.bufferedDurationMs), "paused" to !state.playWhenReady,
            "audioTrack" to state.audioTrackLabel, "quality" to state.qualityHeight,
            "subtitle" to state.subtitleLanguage, "event_type" to type,
            "at" to timestamp, "since" to elapsed(timestamp, descriptor.pageEpochMs)) + extra))
    }

    private fun flushLocked(force: Boolean) {
        if (events.isEmpty() || stopped || restriction != null) return
        val payload = obj("token" to descriptor.token, "domain" to descriptor.domain,
            "isTrailer" to descriptor.isTrailer, "dropped" to droppedEvents, "playerInitAt" to playerInitAt,
            "clientSessionId" to clientSessionId, "clientRequestId" to clientRequestId,
            "env" to environment, "events" to JsonArray(events.toList())).toString()
        events.clear()
        droppedEvents = 0
        val body = if (force) MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("token", descriptor.token).addFormDataPart("payload", payload).build()
        else FormBody.Builder().add("token", descriptor.token).add("payload", payload).build().withUtf8FormType()
        enqueueLocked(Pending("events", body))
    }

    private fun queueStat(percent: Int?) {
        if (descriptor.fileId.isBlank()) return
        val body = FormBody.Builder().add("id", descriptor.fileId).add("token", descriptor.token)
            .add("domain", descriptor.domain).add("url", descriptor.providerPageUrl)
            .add("type", descriptor.statType).add("ab", descriptor.adBlock?.toString().orEmpty())
        percent?.let { body.add("percent", it.toString()) }
        fun addFields(prefix: String, value: JsonElement) {
            when (value) {
                is JsonObject -> value.forEach { (name, child) -> addFields("$prefix[$name]", child) }
                is JsonArray -> value.forEachIndexed { index, child -> addFields("$prefix[$index]", child) }
                else -> body.add(prefix, formValue(value))
            }
        }
        addFields("info", descriptor.statInfo)
        enqueueLocked(Pending("stat", body.build().withUtf8FormType()))
    }

    private fun enqueueLocked(request: Pending) {
        if (stopped || restriction != null) return
        if (pending.size >= MAX_PENDING_REQUESTS) {
            pending.removeFirst()
            discardedRequests++
        }
        pending.addLast(request)
        sendNextLocked()
    }

    private fun sendNextLocked() {
        if (stopped || restriction != null || inFlight != null) return
        if (pending.isEmpty()) {
            if (closing) stopLocked()
            return
        }
        val outgoing = pending.removeFirst()
        val url = origin.newBuilder().encodedPath("/${outgoing.path}").build()
        try {
            val builder = Request.Builder().url(url).post(outgoing.body)
            val capturedHeaders = descriptor.endpointHeaders["/${outgoing.path}"] ?: descriptor.headers
            capturedHeaders.forEach { (name, value) ->
                if (name.lowercase() !in setOf("host", "content-length", "content-type", "connection", "transfer-encoding")) {
                    builder.header(name, value)
                }
            }
            if (capturedHeaders.keys.none { it.equals("Referer", true) }) builder.header("Referer", descriptor.providerPageUrl)
            if (capturedHeaders.keys.none { it.equals("Origin", true) }) builder.header("Origin", origin.toString().removeSuffix("/"))
            val call = http.newCall(builder.build())
            inFlight = call
            sentRequests++
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) = synchronized(lock) {
                    if (inFlight !== call) return@synchronized
                    inFlight = null
                    failedRequests++
                    sendNextLocked()
                }
                override fun onResponse(call: Call, response: Response) {
                    val code = response.use { it.code }
                    synchronized(lock) {
                        if (inFlight !== call) return@synchronized
                        inFlight = null
                        if (code !in 200..299) failedRequests++
                        if (code == 403 || code == 429) {
                            restriction = code
                            stopLocked()
                        } else sendNextLocked()
                    }
                }
            })
        } catch (_: Exception) {
            inFlight = null
            failedRequests++
            sendNextLocked()
        }
    }

    private fun stopLocked() {
        stopped = true
        timer?.cancel(false)
        timer = null
        inFlight?.cancel()
        inFlight = null
        pending.clear()
        events.clear()
        scheduler.shutdownNow()
    }

    private fun accepting() = !stopped && !closing && !finished && restriction == null
    private fun nextBoundary(timestamp: Long, interval: Long): Long =
        timestamp + interval - ((timestamp - playerInitAt).coerceAtLeast(0) % interval)

    private companion object {
        val MILESTONES = setOf(0, 10, 30, 60, 97)
        const val MAX_PENDING_REQUESTS = 16
        fun FormBody.withUtf8FormType(): RequestBody {
            val form = this
            return object : RequestBody() {
                override fun contentType() = "application/x-www-form-urlencoded;charset=UTF-8".toMediaType()
                override fun contentLength() = form.contentLength()
                override fun writeTo(sink: BufferedSink) = form.writeTo(sink)
            }
        }
        fun newId() = UUID.randomUUID().toString().replace("-", "")
        fun elapsed(timestamp: Long, since: Long) = (timestamp - since).coerceAtLeast(0)
        fun seconds(ms: Long) = ms.coerceAtLeast(0) / 1_000.0
        fun formValue(value: JsonElement) = (value as? JsonPrimitive)?.contentOrNull.orEmpty()
        fun JsonObject.text(key: String) = (get(key) as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
        fun JsonObject.number(key: String) = (get(key) as? JsonPrimitive)?.doubleOrNull?.takeIf { it.isFinite() }
        fun obj(vararg pairs: Pair<String, Any?>): JsonObject = JsonObject(pairs.associate { (key, value) ->
            key to when (value) {
                null -> JsonNull
                is JsonElement -> value
                is Boolean -> JsonPrimitive(value)
                is Number -> if (value.toDouble().isFinite()) JsonPrimitive(value) else JsonNull
                is String -> JsonPrimitive(value)
                else -> error("Unsupported telemetry value")
            }
        })
    }
}
