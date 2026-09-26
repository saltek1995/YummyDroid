# Alloha: interruption after several minutes

Investigation started 2026-09-19 against v1.4.64 (`dbdbbf4`). The earlier six-minute
Alloha smoke test did not establish long-session reliability. CVH's progressive
MP4 idle-read fix is separate and is not changed here.

## Observed failure

One Alloha HLS stream, 1080p, normal playback speed, Maximum buffer (120/240 s),
Android TV API 36 emulator, production resolver, native player and session code:

- Native WebSocket upgrade succeeded with HTTP 101 at 12:51:30 UTC. No other
  upgrade was observed before the media failure.
- At 12:55:48 UTC, about 259 seconds after starting the player, the next HLS
  request returned HTTP 403. The previous request in the same refill returned
  HTTP 200. There were 73 observed native HTTP attempts, including discovery.
- About 126 seconds of playable samples remained. The visible interruption
  would therefore occur later, around the reported six-minute interval; the
  test stopped on the first rejection instead of draining/retrying.
- One MediaItem load, three loading starts, no rebuffer or audio underrun before
  the test stopped. This is an actual HTTP rejection, not a local parser error
  or a demonstrated exhaustion of WebSocket reconnect attempts.
- A subsequent discovery attempt stopped after its first observed CDN 403.
  This request had no `Accepts-Controls` header, but may have been a subtitle
  request. The old log lacks resource classification, so it does not prove
  that video authentication was absent or that the whole provider was blocked.

The run followed an approximately 2.5-minute run stopped to add WebSocket
instrumentation. Its traffic is a confound when considering rate limits.
Request count/timing alone does not prove either rate limiting or token expiry.
Raw logs and live arguments remain in ignored `build/player-audit/`.

## Comparison with captured website code

The locally captured `alloha-app.js` provides these concrete differences:

- Website HLS `maxBufferLength=60`, `maxMaxBufferLength=90`. Its TV byte cap is
  80 MB; the forward duration limits remain 60–90 seconds. Native Maximum can
  download 240 seconds ahead, creating larger bursts and longer idle periods.
  No corresponding provider request-rate limit has been established.
- Both use 30-second `playing` messages and the same message fields. Browser
  resolution and track ID are explicitly converted to strings, matching native
  JSON types. Every native media open reads the current session token.
- The website retains its current media token when its bounded WebSocket
  reconnect attempts are exhausted. Native code previously invalidated all
  subsequent media requests locally after this limit, even with a token.
- The website checks metadata's absolute `time` every 120 seconds. When due,
  it fetches new metadata and rebuilds HLS at the current position, retaining
  the WebSocket/SID. This is not merely a metadata refresh. Neither a ten-minute
  metadata lifetime nor a requirement to replace healthy native media has been
  established from the available evidence.

Relevant source locations: website buffer settings near original character
offset 184269, socket handling near 436900, string conversion in `Bh` near
438650, metadata reload near 513690 and deadline timer near 532630. These are
offsets into a local captured minified artifact, not public API contracts.

## Bounded correction and diagnostics

Socket reconnect exhaustion now stops further connection attempts while keeping
an existing media token usable, as the website does. Without a token, exhaustion
remains terminal. Seek cannot reset the exhausted retry budget. Explicit service
restriction handling is unchanged. This corrects a separately reproduced local
defect; it is **not yet the demonstrated cause of the real media 403 above**.

Alloha's ordinary HTTP 403 fragment retries are also bounded to four retries
(five attempts including the original). Previously Media3 could retry throughout
the remaining buffered minutes. This is a per-load limit, not the website's
complete recovery budget: the website can recreate HLS once for the current URL
and then try its finite manifest alternatives. Explicit `Retry-After` and 429
handling keep precedence; CVH and other providers keep their existing behavior.
This bounds rejected traffic and does not by itself repair the initial refusal.

Session diagnostics expose only operational counters and status codes: successful
connections/attempts, received messages, token updates, messages sent, retries,
last handshake status, exception class and close code. No credentials are logged.
The live harness can run 18 minutes, records separate WebSocket handshakes, token
version numbers on media requests and resource extensions, and stops on rejection.

Local regression validation: the existing-token exhaustion test failed on the
old code and passes with the correction. The 15 session tests, seven capture
tests and three native session-binding tests passed. These tests do not substitute
for the pending long real-source comparison.

## Follow-up comparison and current limit of the evidence

As of 13:12 UTC on 2026-09-19, no successful long run had completed:

- At 13:10:18 UTC, after more than 12 minutes without native CDN requests,
  native discovery still stopped with `SourceHttpRestricted` inside WebView.
- A direct top-level website navigation was an invalid comparison: Alloha
  displayed "content not found" and created no video/socket. That run was
  stopped and excluded. The browser harness was corrected to use the same
  site-origin iframe context as the production resolver.
- At 13:12:27 UTC, that corrected website-only run received HTTP 403 from
  `0a-2e-7c-r400.vkvideo.cloud`, without a `Retry-After` response header.
  It was stopped immediately. No native media player, native session takeover,
  or Maximum-buffer policy was active in this run.

This establishes that the refusal subsequently affected the provider's own
browser player in the same environment. It does **not** prove an IP ban, a
specific rate limit, token expiry, or the cause of the first media 403. The
website's initial rejected resource was not classified as video versus subtitle.
Requests to that source were paused rather than polling a refusing CDN.

The opt-in native harness also accepts `liveBuffer=allohaWebsite`: a 90/90-second
profile for comparison, leaving production buffer settings unchanged. Hls.js's
actual target is `min(max(8 * maxBufferSize / maxBitrate, 60), 90)` seconds;
with ordinary bitrates it reaches 90 and resumes loading as soon as forward
buffer falls below that target. A 60/90 native hysteresis would not reproduce
that steady refill pattern. Request observations now record start times as well
as response-header latency; header latency alone is not segment download time.

`LiveAllohaWebsiteTest` is opt-in, uses one iframe and the real website player,
observes frame-local playback and WebSocket counters. Its initial stop-on-first-403
guard was subsequently corrected: two ordinary 403 responses may be recovered by
the real site logic; a third stops further traffic. A 429 or Retry-After stops
immediately. It does not modify the site's buffer policy or retry configuration.
Real URLs, live arguments, logs and screenshots stay ignored.

### Follow-up resource classification

Some initial browser refusals were optional `.vtt` requests without a controls
token, not video requests. They cannot establish a media restriction. The website
comparison now supplies an empty local WEBVTT response for these optional requests;
the native harness has a matching opt-in `liveIgnoreSubtitles=true` switch.

At 13:33 UTC the first episode's real website player still received two manifest
403 responses, including a retry after a new `config_update` token. Its WebSocket
opened and delivered four config messages. The metadata renewal deadline remained
about 5 hours 45 minutes away. Fresh public metadata contained the same iframe
credential parameters. This does not establish an IP ban or explain the original
native failure.

A second episode (anime 20021, video 1211944) on another CDN node was started at
13:36 UTC in the website player. It successfully reached 18 minutes of playback
at about 13:54 UTC, at 1080p with about 90 seconds of forward buffer, one connected
socket and multiple token updates. The instrumentation result is `OK (1 test)`;
no video 403 occurred. Native playback of this same episode began afterward with
the unchanged Maximum buffer. Tests run sequentially, with only one active media
stream.

### Same-episode native failure after the successful website run

Native Maximum playback of video 1211944 began at 13:54:15 UTC. At 13:58:21 it
received a fragment 403, with 125,420 ms still buffered and playback at about
241 seconds. The next visible interruption would therefore have occurred near
six minutes. The test stopped at the first rejected fragment: 67 HTTP attempts,
three loading starts, one MediaItem load, no rebuffer or audio underrun beforehand.

The control socket stayed connected (one successful 101 handshake, no reconnects).
It received three valid token updates, including one changed token; the most recent
update was only 5.8 seconds old. The rejected request used controls token version 2,
the same current version as the immediately preceding successful fragment. Thus
this reproduction does not support a disconnected socket or a stale initial token
as its cause. Metadata renewal was still approximately 5h45m away at startup.
The 403 body was HTML with `forbidden`, without Retry-After.

A single subsequent attempt to resolve this episode for a 90/90-second comparison
was rejected at discovery; no media run occurred. Requests to it were stopped.
A different cached legitimate episode (AniBaza episode 11 of the same anime)
began native 90/90-second testing at 14:01:30 UTC. This changes the episode as well
as the buffer policy, so its outcome alone is not a strict same-episode A/B proof.

That 90/90-second run also failed, at 14:07:38 UTC: playback position 366,296 ms,
90,341 ms buffered, 80 HTTP attempts, 61 loading starts, one MediaItem load, and
no prior rebuffer/audio underrun. The socket had one successful connection, four
valid config messages, one token change, and a last-update age of 7.2 seconds.
Consequently a 90-second buffer cap does **not** fix the problem. The experimental
production profile change and its dedicated tests were removed; production buffer
settings remain unchanged. The opt-in 90/90 test mode remains as comparison evidence.

## Startup handoff correction and remaining validation gap

The real native runs recorded `playback_start` and subsequent `playing`, but no
`resumed`: `update(playWhenReady=true)` occurred before the asynchronous WebSocket
handshake, so `send()` discarded that event. `onOpen()` did not synchronize the
already-stored playback intent. This ordering is now covered by a delayed-handshake
test and corrected: an opened socket receives the current playing intent.

Discovery now records successfully sent startup events after the underlying
browser `send()`, rather than treating an attempted send as delivered. A native
session completes `init` once if discovery never observed it; reconnects do not
repeat an already-completed init. Startup-event observations are cleared when SID
changes. These are protocol/lifecycle corrections, **not yet a demonstrated fix
for the long-session 403**.

The post-change live attempts did not reach sustained playback:

- Video 1211936 (episode 10) returned a stream without a runtime descriptor; its
  first native manifest request was rejected with `X-VD: token_decrypt`. This was
  a startup refusal, not the previously observed mid-playback failure, whose
  `X-VD` value was not recorded.
- Video 1211948 (episode 13) was rejected during native discovery. At 14:23–14:24
  its real website player also received two manifest 403 responses and never
  opened an observed playback socket or started video. The stalled comparison
  was stopped; it is not counted as a successful long run.
- Clearing only the test application's emulator data and trying the original
  video 469107 still produced a discovery refusal. No user TV data was changed.

Further live requests were stopped until the next controlled run. These observations establish an external
validation blocker for the tested URLs, not an IP-ban diagnosis or proof that the
startup correction repairs the original failure. A successful sustained native
run on an available source remains required before calling the issue resolved.

Final local validation: 16 session tests, nine capture tests, ten load-error policy
tests, and three session-binding tests passed. The offline browser script harness
also passed, including failed-send transparency. Debug APK, instrumentation APK,
and Android lint passed. CVH/other-provider buffer behavior and user buffer presets
are unchanged. No release was published.

## Resumed same-episode comparison

After a cooldown, a single fresh anime metadata request returned unchanged iframe
URLs for the tested episodes. At 14:50:18 UTC video 1211944 became playable and a
new native run began with the startup handoff correction and original Maximum
buffer (120/240 seconds). Discovery had observed `playback_start` and `init`;
captured and native-selected resolution/audio identifiers both matched `1080/1`.
The opened native socket sent `playback_start`, then the previously lost `resumed`.
This isolates a real protocol difference from the failed native baseline; the
missing-init branch was not needed in this run. The harness stops at the first
media rejection and records the safe `X-VD` reason if present.

At 14:57:35 UTC this run failed too: its first rejected fragment returned
HTTP 403 with the explicit CDN reason `X-VD: session_blocked`. Position was
434,602 ms (7m15s); the connected socket had four config updates, one token
change, last-update age 76 seconds, and events `playback_start=1`, `resumed=1`,
`playing=14`. Several preceding fragments used the same current token successfully.
The test stopped immediately, with one MediaItem load. The startup correction
therefore does **not** resolve the long-session failure. Unlike the earlier
startup `token_decrypt` rejection, this is a measured mid-playback session block.

## Isolated telemetry comparison blocked at startup

The website generates HTTP `/events` heartbeats every ten seconds and flushes
them every thirty seconds, separately from WS `playing`. Native playback lacks
this telemetry. Its response is ignored by the website, but server-side use
cannot be determined from client code. To test necessity before implementing it,
the opt-in website harness can suppress only HTTPS POST `/events` on the exact
source host, returning a local JSON response and counting suppressed requests.
No telemetry is synthesized or transmitted by this experiment.

At 15:01:54 UTC the same episode's website immediately received manifest
HTTP 403 with `X-VD: client_blocked`, before playback or any suppressed events.
The test stopped on that first refusal. This is **not** a result about telemetry;
source requests were stopped again to avoid extending the restriction. The
server's scope and expiration for this client block are unknown.

Offline review also corrected two pending-handshake cases: a pause while
reconnecting is delivered after opening, and ending an episode cancels an
unfinished upgrade so it cannot send a late playback-start. Replay opens a fresh
connection. Both regression tests passed. The focused suite now has 40 passing
tests (18 session, nine capture, ten policy, three binding); Android lint passed.
The existing Maximum preset and CVH buffering code remain unchanged. These local
checks do not establish that the Alloha session block is fixed.

## Public documentation search (2026-09-19)

- https://alloha.tv/ redirects to https://alloha.tv/login. Public access shows
  only a login/access-request page, not a transport specification.
- https://github.com/Anixora/Server/blob/main/docs/ALLOHA_PROTOCOL.md contains
  third-party protocol research dated 2026-06-14. It describes transient browser
  state, guard verification and WebSocket session binding, and records why its
  legacy direct-link integration is disabled. This is an independent research
  lead, not the provider's specification or proof of our exact block trigger.
- https://kinobox.tv/docs/ documents its Alloha iframe integration and parameters.
  It does not document native transport/session continuation or CDN denial codes.
- https://github.com/ElectroMyStyle/alloha-sdk-go documents a third-party catalog
  API client (search/metadata); it is not a native media playback protocol.
- https://github.com/yummyanime/yummy-lampa-plugin documents another integration
  of the same YummyAnime Alloha source. Its 0.27.0 changelog retains the browser
  WebSocket; 0.27.2 reports an approximately eight-minute stall in a twelve-minute
  test and adds an assumed lifetime with proactive refresh. That is evidence of
  a similar symptom in another client, not proof of token expiry. A fixed-lifetime
  refresh must not be copied: it violates the requirement to preserve healthy URLs
  and does not identify the trigger for our measured `session_blocked` response.

No public provider definition of `X-VD: session_blocked` / `client_blocked`, nor
an authoritative timeout/rate-limit contract, was found in this search. The
third-party protocol notes do not establish which native difference caused the
observed refusal and must not be presented as that conclusion.

### YummyTV reference comparison

The requested reference has a dedicated document:
https://github.com/Helandy/YummyTV/blob/main/docs/alloha-player.md
Source snapshot examined: `140eeb2e5cd59bc217ca4cfde20a292bef413d11`.

- It retains WebView, but reports broken WebSocket updates and guessed refresh
  timing. Retention alone is not a proven fix.
- It retracts the one-consumer master claim found in the Lampa README.
- It reports header inconsistencies and blocking after repeated session starts.
- Its synthetic heartbeat does not use the actual native playback state.

YummyDroid already merges browser headers and runtime authentication. Logs prove
client hints are present; consistency of their values still needs wire capture.
Our failing session received updates, unlike the reference's documented failure.

No reference establishes the server's exact reason for our seven-minute block.
Reference source files are ignored audit artifacts and were never executed.

## Rejected graceful transport handoff experiment

The prior native handoff destroyed WebView without waiting for its WebSocket
close. The experimental candidate closed captured browser sockets with code 1000,
suppresses browser reconnects and subsequent sends, and waits for a clean close
acknowledgement before returning the native session descriptor. A final captured
token is read after acknowledgement, with an exact socket-identity check. The
provider's own intentional teardown also clears its onclose handler first.

Discovery and release have separate bounded deadlines; release waits at most
five seconds. This deadline does not expire media URLs or refresh working streams.
Cancellation and failed/late acknowledgement cannot open a native session.

This addresses an uncoordinated transport transition, not a demonstrated cause
of session_blocked. The provider may impose additional requirements on SID reuse.
The experiment has been removed from production after the failed long test below;
its source and local test are saved only in ignored audit artifacts.

A second website-only attempt at 15:25:29 UTC again failed on the first manifest
with X-VD: client_blocked, before playback or any suppressed events. It stopped
immediately; this still provides no result about the /events hypothesis.

After the user changed IP, the real native test ran from 15:43:37 to 15:53:13 UTC.
The browser close acknowledgement was clean (1000), taking 107 ms. At playback
position 573086 ms the CDN returned `403 session_blocked` again. The test stopped
on that first rejection: 130 HTTP attempts, six loading starts, peak buffer
236940 ms, one media-item load, no preceding rebuffer or audio underrun. The
socket stayed healthy (one connection, five configuration updates); six preceding
fragments succeeded using the same current token version as the rejected one.
Therefore graceful handoff alone **does not fix the reported problem**.

Wire identity comparison found a separate concrete difference: website requests
use `Accept-Language: en-US,en;q=0.9`, while native playback inserts the generic
Russian language fallback. User-Agent and client hints match the same WebView.
This is not yet evidence that language causes the session rejection.

## Browser language parity

The 15:57:33 UTC attempt to test the measured browser language stopped in browser
discovery with SourceHttpRestricted, before native playback. This attempt cannot
establish whether language parity fixes the long-session block. No further live
requests were made during the local work below.

Alloha discovery now captures navigator.languages and preserves an observed
Accept-Language from signed browser media requests when available. Otherwise it
reconstructs Chromium M120+'s expansion and q weights using the actual WebView
package version (not the overridden User-Agent). Unknown/older versions retain
the existing fallback. The result is applied to Alloha media and WebSocket
requests; other providers are unchanged.

The actual Android WebView/local HTTPS integration test passed: the computed
header exactly matched the header received by MockWebServer. Unit tests cover
regional grouping, deduplication, low-priority weighting and invalid input.
This validates the request correction, not resolution of session_blocked.

Primary implementation reference:
https://chromium.googlesource.com/chromium/src/+/refs/tags/120.0.6099.211/net/http/http_util.cc

After the next user-reported IP change, the production browser-header build was
tested at 16:12 UTC. Discovery again stopped with SourceHttpRestricted in 2.72 s,
before native playback or any native media request. No automatic retry followed.
The exact HTTP status/denial header was not retained by that test's discovery
error log, so this is not a new measured client_blocked diagnosis.

Independent route checks confirmed matching host/emulator public IPv4 without
publishing either address. One fresh Yani catalog lookup returned the same iframe
URL after HTTPS normalization. Offline inspection of 20 WebView HTTP cache files
found no cached /bnsi/movies or /bnsi/trailers metadata entry. These checks made
no additional Alloha requests and do not establish the restriction's scope.

## Full website recording, 2026-09-25

This separate, explicitly authorized diagnostic uses Alloha's own iframe player
in Android TV WebView. It does not run the native resolver/player in parallel.
`LiveAllohaWebsiteTest` now waits on a local capture gate while displaying only
`about:blank`; the provider is opened only after successful CDP Network and
Runtime enable acknowledgements. `liveFullEpisode=true` requires an actual media
`ended` event and checks observed playback against the full media duration.
The diagnostic stops on the first HTTP 403/429. It uses the actual WebView UA by
default, does not suppress `/events` or subtitles, and retains ten seconds after
completion for the site's final messages. Normal release checks do not run it.

The user explicitly authorized retaining raw secrets. The ignored directory
`build/player-audit/alloha-full-20260925/` contains `raw.jsonl` with original CDP
messages before transformation, including exact headers, cookies, POST bodies,
WebSocket payloads, and retrieved response bodies. `sanitized.jsonl` is a separate
analysis copy. Neither archive belongs in Git. Response-body limits are 8 MB for
scripts, 2 MB for documents/metadata/playlists and 64 KiB for keys; video fragment
payloads are not archived. Request/response headers, timing and transfer sizes
are recorded for those fragments. Body retrieval failures remain explicit.

### Confirmed protocol differences

The site's HTTP `/events` channel is separate from its WebSocket. Its credential
comes from `userParam.token`; it is distinct from the WS session ID, edge token,
and guard. The observed metadata POST token and both `/events` token fields are
equal. The native descriptor does not capture this credential or the HTTP view
identifiers, and the resolver destroys WebView before native playback. Native
`AllohaPlaybackSession` consequently continues WS messages without the site's
HTTP playback reporting.

Ordinary HTTP requests encode `token` and a JSON `payload` as a URL-encoded form.
The payload has `token`, `domain`, `isTrailer`, `dropped`, `playerInitAt`,
`clientSessionId`, `clientRequestId`, `env`, and `events`. The site records
heartbeat samples every ten seconds and normally batches requests every thirty
seconds. Forced flushes use multipart FormData/sendBeacon. Request identity also
includes the actual browser headers and full provider-frame referrer; this run's
observed `/events` requests do not require a Cookie header.

HTTP event fields differ from WS fields: fractional `currentTime`, numeric
`quality`, string `audioTrack`, and nullable `subtitle`; WS uses integer
`current_time`, string resolution/audio and `-1` for disabled subtitles. HTTP
`bufferLength` sums future portions of all buffered ranges. Played time excludes
seek jumps, accumulating only positive media-time deltas below two seconds while
playing and not seeking. Byte totals increment on successful HLS FRAG_LOADED;
the application's counter of all incoming network bytes is not equivalent.

A faithful native implementation needs the actual duration, buffered ranges,
completed-fragment bytes, frame/drop counters, bandwidth/format information,
seeking/buffering transitions, view identifiers, and clock/counter baselines
across browser handoff. Replaying startup events or inventing zero metrics would
not reproduce the observed protocol. These differences do not, by themselves,
prove which server check causes `session_blocked`.

The same provider origin also receives legacy `/stat` (singular), a second
native parity gap distinct from `/events` and diagnostic `/stats` (plural).
Six observed POSTs returned HTTP 200 with captured JSON bodies. The first was
initial reporting; the others reported percent 0, 10, 30, 60 and 97. Fields were
`id`, the same HTTP `token`, `domain`, `url`, `type=mgpo`, `ab`, `percent`, and
`info` containing wasm/service-worker/WebSocket support, platform and screen/
window dimensions/pixel ratio. Capture offsets were 41.489, 46.286, 180.980,
465.261, 891.090 and 1416.598 seconds. These are observed progress milestones,
not a proven server blocking deadline. Native session parity work must include
this channel in its investigation. Six `/lists.php` POSTs go to a different
origin with advertisement-module fields; do not conflate them with provider
session heartbeats. All 63 POSTs in the recording completed with HTTP 200.

The captured application script is byte-identical to the earlier cached
`alloha-app.js` (SHA-256 prefix `740e8af3fc3d`). It uses `edge_hash` from WS
configuration and ignores `ttl` and `edge_priority` for token replacement.
Repeated configuration messages may contain the same token. The native WS
token replacement and thirty-second heartbeat broadly match this behavior.
Native playback position is also updated every second, so listener-only stale
position is not the explanation.

### Completed run

The single full-episode run passed on 2026-09-25 at 21:04:17.930 UTC (September
26 locally). Instrumentation recorded `playedMs=1420179 durationMs=1420179` and
`OK (1 test)`. CDP independently recorded `ended=true` at the same media duration.
There was no HTTP 403/429, provider WS reconnect, or playback restart.

- 590 recorded requests, including website dependencies and preflights; 242
  authenticated media requests matched the latest observed WS token, zero
  mismatches. Normal fragment request spacing had a median of six seconds.
- Four changes after the first WS token assignment, at capture offsets
  401.492, 641.499, 1001.481, and 1241.478 seconds. Configuration messages were
  more frequent (14 total). None of these four transitions had a media request
  in flight; each subsequent request used the new token. The authorization guard
  remained unchanged. Cookies/query fields did not change at these boundaries.
- The main media connection used HTTP/1.1 and was reused across all four token
  rotations. Its 470 observed responses comprised 234 GETs and 236 OPTIONS;
  469 responses reported connection reuse. This does not require reconnecting
  for each segment or each token change.
- The website kept roughly 90 seconds buffered (sampled peak 95.13 seconds),
  using HLS maxBufferLength=60, maxMaxBufferLength=90 and maxBufferSize=150000000.
  These are observed site settings, not evidence that reducing native buffering
  fixes the block; the earlier native 90-second trial failed.
- Fifty `/events` responses were HTTP 200. Seventy-one response bodies were
  saved. The only two body retrieval errors were OPTIONS preflights; actual
  manifest GET bodies and essential metadata were captured.
- Final `/events` at capture offset 1466.270 seconds contains heartbeat, pause
  and `view_finish`: `completed=true`, `watchTimeSec=1420`,
  `currentTime=duration=1420.179999`, `bufferLength=0`. It finished with HTTP 200.
  WS `ended` followed at 1466.271 seconds with `current_time=1420`. Across the
  run, HTTP carried 142 heartbeats, five view-percent milestones and one finish.
  No `/stats` or `/errors` requests were observed. The final provider WS close
  acknowledgement was not captured: the target disconnected about 10.74
  seconds after `ended` during WebView teardown. This limits conclusions about
  transport-close telemetry, not about full playback or the accepted finish.
- Two media requests were canceled at startup; no media rejection occurred.
  The unrelated initial HEAD returned 404; two image loads failed, and final
  navigation was canceled during teardown. These are retained rather than
  presented as a completely error-free network trace.

A durable copy, including collector/analyzer sources, test harness, input and
SHA-256 inventory, is outside the repository at
`C:/Users/saltek1995/.codex/diagnostics/YummyDroid/alloha-full-20260925/`.
Its raw archive is 23,632,809 bytes, SHA-256
`72699f95ecfb7bf51dc65332077604f325ceb4e5ace189bf74004f8e2acac82f`.
It survives `gradle clean`. The successful website baseline does not establish
that the application's native long-session block is fixed.

## Native HTTP session continuation (September 26 implementation)

The native session now continues both observed HTTP reporting channels in
addition to the provider WebSocket. `/events` samples playback every ten seconds
and batches every thirty seconds; `/stat` retains the captured initial report
and the site's rounded progress milestones. The HTTP token remains distinct
from the socket SID, media edge token, and guard. There is no timer-based media
URL replacement. Only actual provider updates replace the edge token.

Discovery captures submitted forms, the original view/request identifiers,
environment and capability results, and endpoint-specific browser headers.
Cookies are looked up for each endpoint rather than copied from the iframe
path. Alloha uses the real installed WebView user agent consistently for
discovery, media, HTTP reporting, and the socket handshake.

The scoped normal Play control is invoked at most once, after the real video,
runtime query, player readiness, HTTP initialization and registered `/stat`
play listener exist. This uses the provider's own first-click and HLS startup
handlers. The final handoff waits for already-started fetch/XHR submissions;
the retired browser cannot emit another finish after native ownership begins.
The discovery deadline remains bounded. Failure diagnostics report readiness
booleans, never credentials.

Native reporting observes actual playback, buffering, seeking, quality/audio/
subtitle changes, first frame, completed media-load bytes and decoder counters.
It does not alter buffer sizing, renderers, volume or media request scheduling.
Media-item generations reject queued callbacks from previous loads. The
captured translation label is retained while its provider track ID is selected.
HTTP reporting has one in-flight request, bounded queues and no automatic retry;
403/429 stops reporting without introducing another media retry loop.

The handoff inherits submitted counter checkpoints. The site's private,
not-yet-submitted accumulator is not accessible; no fabricated snapshot is
supplied. Unavailable browser/HLS timing and rendering values remain null.
This is an explicit limitation, not proof of a blocking cause.

Validation includes local HTTP fixtures, capture-script fixtures, session and
metrics regression tests, CVH/native buffering regression tests, and an actual
Android WebView fixture whose responses are entirely intercepted. The latter
confirmed normal one-shot startup and that pending HTTP submissions prevent
premature handoff (`OK (1 test)`, 1.559 seconds). It made no provider requests.

The first native diagnostic attempt stopped at discovery after thirty seconds,
before any native media requests, because passive extraction did not trigger
the site's HTTP initialization. It did not observe a 403. The one-shot normal
startup above fixes that demonstrated integration defect. Its failed trace is
retained separately from subsequent playback runs.

### Completed native full-episode run

The corrected native implementation passed the same complete episode on the
Android TV API 36 emulator on September 25 at 22:09:13 UTC (September 26
locally), using the application's **Maximum** buffer setting and 1080p.
Instrumentation reported `OK (1 test)` after 1,436.369 seconds. Playback reached
`STATE_ENDED`: observed `playedMs=1420192`, `durationMs=1420180` (23m40s).

- One MediaItem load, one successful WebSocket connection, no reconnects,
  no HTTP 403/429, no playback errors, no post-startup rebuffers, and zero
  audio underruns. Twelve loading starts exercised repeated stop/refill cycles;
  peak buffered duration was 242,053 ms.
- Four actual socket token changes. Media requests used five successive token
  values, including the final rotation; all observed media HTTP responses were
  200. Their first request offsets were 8.440, 441.253, 699.256, 1035.246 and
  1227.253 seconds. The HTTP reporting token and view/request identifiers stayed
  unchanged throughout this continuous playback.
- 47 recorded `/events` responses and four continued `/stat` responses were
  HTTP 200. Native `/stat` milestones were 10, 30, 60 and 97; discovery had
  already submitted the initial/zero reports. The trace contains 139 submitted
  native heartbeats, five view-percent events and exactly one `view_finish`.
- Final `view_finish` was accepted with HTTP 200: `completed=true`,
  `watchTimeSec=1420`, `currentTime=1420.192`, `duration=1420.18`. The small
  12 ms end-position overshoot is the native player's reported value. The
  provider socket sent `ended` once. No fake replay followed player cleanup.
- This was not a completely failure-free transport trace: eleven media
  `IOException`s were followed by HTTP 200 for the same resource within
  248–1295 ms, without surfacing as loader errors or interrupting playback.
  The reporting component counted one failed call out of 52. That call did
  not reach the network-interceptor journal, so its underlying exception is
  not established. Later reports and final completion were accepted. No
  reporting retry loop or player restart was introduced.

The private archive is
`C:/Users/saltek1995/.codex/diagnostics/YummyDroid/alloha-native-20260926-telemetry-v2/`.
It contains raw HTTP credentials/bodies, runtime and instrumentation logs,
input, APK hashes, production-source snapshot, analyzer and summary. Raw HTTP
size is 2,218,124 bytes, SHA-256
`748a1fa7cfe056e499a5d6558a809c1e320ec3bea659ac8658f6a68d15064529`.
These private artifacts are outside Git. This proves the observed continuous
playback scenario on the emulator; it does not reveal the provider's private
blocking rule or establish every device/network combination.

Final offline validation passed: 49 Alloha data tests and 56 player/session/
buffering regression tests, zero failures or skips. Debug app/test APK assembly
and both app/data lint tasks completed successfully. No live provider requests
were part of these checks.

Release validation additionally exercised the complete unit-test suite. Its
MP4 retry fixture found that Media3's cumulative error count included an earlier
connection EOF in the four-response Alloha 403 budget. The policy now discounts
known preceding non-403 errors per load task, resets on task completion/count
reset, and remains idempotent when evaluated twice. Four consecutive 403
retries remain the limit. This failure-path correction was verified using local
MP4/HLS integration fixtures and focused task-isolation tests; it did not
require another real-provider playback.
The final complete unit-test run passed 997 app tests and 408 data tests,
with zero failures, errors or skips.

## Advertising-free discovery correction

After 1.4.68, a TV report exposed audible advertising during hidden discovery
and failure to start the episode. The prior completed playback did not cover
that startup branch. The cached provider constructs its advertising manager
when `config.ads.enabled` is true; a normal Play action can start preroll,
pause content, and chain ads. These actions compete with the resolver's bounded
30-second preparation. The exact missing readiness field on the reported TV
was not captured, so preroll timeout is not asserted as the sole proven cause.

Discovery now disables the provider's advertising configuration before its
constructor runs. The document-start hook narrowly recognizes the lexical
`const config = JSON.parse(...)` configuration, changes only `ads.enabled`,
and restores `JSON.parse` after that one match. Ordinary JSON, parse failures,
revivers, authentication values and other player configuration are preserved.
Both one-shot Play and native handoff require confirmation that the actual
player has advertising disabled. Main playback, real capability probes,
WebSocket initialization, `/events`, `/stat` and pending-request drain remain
on the provider's normal content path; no ad-completion events are invented.

The same-origin RmpVast SDK and known advertising service are denied locally.
The independent IMA ad-detection fetch rejects locally, letting the provider
record the real blocked outcome rather than faking a successful probe.
These rules apply only to the hidden Alloha view, not other providers or the
native media client. Hidden content preparation uses WebView-level mute when
available. Older WebViews also mute at the actual media `play()` call after
the provider's mobile/TV `gain()` initialization, which otherwise restores
volume and clears mute.

Offline browser checks cover an initially enabled ad configuration, unchanged
non-ad configuration, JSON/reviver behavior, one normal content start, genuine
ad-probe rejection without network traffic, and the provider volume-reset
sequence. An Android WebView fixture verifies configuration-before-construction,
blocked SDK, no ad requests, real HTTP capture and pending-submission handoff.
The same Android instrumentation run also covers reset-confirm focus recovery,
reset-only panel disappearance, cancellation focus, and empty Downloads Dpad
navigation. All five cases passed on the final debug APK, including the
older-WebView audio fallback assertion. All 997 app and 409 data unit tests
passed without failures or skips; debug builds and both module lint checks
also passed.

A separately authorized single real-source run on Android TV API 36 / WebView
151 completed the entire 1,420.180-second episode at 1080p with Maximum buffer.
Discovery took 6.909 seconds. The test reached ENDED after 1,420.182 seconds
played: one media load, 12 loading starts, peak buffer 240.224 seconds, zero
rebuffers, loader errors, audio underruns or terminal failures. There were no
403/429 responses. The final view_finish reported completed=true and
watchTimeSec=1420; all 47 captured /events and four /stat responses were 200.
The WebSocket stayed connected and rotated tokens throughout playback.

This does not mean every transport attempt succeeded: the raw journal records
11 recovered media IOException attempts, followed by HTTP 200 for the same
resource, and the session counter records one failed telemetry submission.
Neither interrupted playback. Raw credentials, token transitions, request
bodies, logs and APK hashes are retained only in the private diagnostic archive
`alloha-noads-20260926`, outside the repository.

The full live run used native WebView mute and the advertising-disabled startup
path. The additional media.play mute fallback was added during that run and
verified afterward in the offline browser and final Android fixture; it was
not the binary used for the full-episode run. These emulator results do not
establish compatibility with every TV WebView version.

## Startup timing follow-up after 1.4.69

Session updates, accepted media hosts and header-only updates no longer restart
the optional metadata/subtitle idle window. Actual new metadata still gets its
existing bounded discovery window; content-only configuration, session
credentials and pending HTTP handoff remain required. Stage logs contain only
names and elapsed times, not source URLs or credentials.

Two explicitly authorized 15-second native startup diagnostics completed without
403/429, reloads or rebuffering. The first measured 5,432 ms of stream resolution
and 7,647 ms postprocessing (13,083 ms total); the second measured 5,713 ms and
1,638 ms respectively (7,354 ms total). They are observations, not a controlled
before/after performance comparison. The old 6,909 ms full-episode run did not
record these individual stages.

The second diagnostic includes OkHttp transport events: first subtitle DNS took
2 ms, TCP connection 1,009 ms, TLS 142 ms; the two response bodies finished in
149 ms and 226 ms after their request headers started. The earlier 7.6-second
postprocessing delay had no transport events and cannot retrospectively be
assigned to DNS/TCP/TLS. These are external VTT subtitles, not embedded tracks.

A website comparator then loaded the same episode in the same Android TV
WebView engine with its default browser identity and original provider code.
No app resolver, subtitle validation, HTTP suppression or ad-disabling hook was
used. Relative to iframe navigation, the normal provider Play control was
clicked at 1,553 ms, main-video metadata arrived at 5,059 ms and the main video
emitted playing at 5,515 ms (3,962 ms after Play). It played at least 15 seconds
and the test passed; no other-media playing event was observed. Native totals
above stop at resolver completion, so they are not native first-frame times.

An earlier comparator attempt clicked at 693 ms before the stream query was
ready and did not start the video; it also used the legacy Chrome/120 override.
That attempt is excluded from the comparison. The startup diagnostic now waits
for the provider's actual source query before clicking once. Private archives
retain both attempts and all native request/transport data outside Git.

## Separating Alloha bootstrap from browser playback

Offline analysis of the same website capture places the metadata response at
1,261 ms, the session handshake at about 1,558 ms, and the first `/events`
submission at 5,555 ms. The main video started at 5,515 ms. The present resolver
waits for the browser's HTTP envelope, which normally becomes observable when
the browser reports its first frame. This creates a second media startup before
native playback; the delay is not a mandatory five-second provider timer.
The cached application schedules HTTP flushes every 30 seconds and heartbeats
every 10 seconds. Its five-second bootstrap timeout races WebSocket connection
and completes early when the connection opens.

The browser-player-free adapter is now implemented in `AllohaBootstrapResolver`,
`AllohaBootstrapProtocol` and the packaged `alloha-bootstrap.js`. Its stages are:

1. Parse current provider HTML as data: user parameters, active file, viewport
   seed, movie type and current application bundle URL.
2. Run actual browser fingerprint and capability probes in the provider origin.
   Fingerprinting combines browser identity, timezone, dimensions, language,
   device capabilities, canvas, WebGL and successful offline audio rendering.
   Compute SHA-256 and the provider's three viewport-seed permutations for the
   metadata request's `Borth` header. The permutations were reproduced offline
   and matched the archived request.
3. Obtain the current provider-supplied guard from the served compatible bundle.
   It is absent from the metadata response. Never hardcode the archived guard.
   The reference bundle has a direct return and also dormant challenge code;
   changed challenge logic must fail closed instead of guessing credentials.
4. Make the normal metadata POST with the real token, AV1 result, autoplay,
   audio and subtitle values. Its observed `hlsSource` contains plain quality
   URLs, while `pnr`/`pnk` identify the actual control session.
5. Let the native control connection acquire its own edge token. Create fresh
   client reporting IDs with actual environment/probe results, empty observed
   startup events, and report native playback events only. Client reporting
   identity is created locally; it is not issued by the first `/events` reply.

This still needs fresh provider bootstrap data and genuine browser probes, but
does not construct Allplay/HLS, load browser media, or run advertising scripts.
The current provider guard is extracted only after a normalized full-bundle hash
check. A changed unsupported program falls back before any metadata POST; HTTP
errors and restrictions do not retry through the browser-player path. Older
WebViews with an unknown language-header format also use compatibility discovery
before any metadata request. All other providers retain their existing resolver.

### Subtitle work overlapped with bootstrap

Alloha resolutions now start sequential optional WebVTT requests when discovery
provides explicit URLs and request headers. One resolution-owned coordinator
shares responses with browser interception and final subtitle materialization.
It retains successes and failures by URI plus headers, preserves the optional
403 restriction boundary, and cancels with the outer resolution rather than
successful WebView cleanup. Cache generation is captured before prefetch so
clearing the subtitle cache cannot publish an older in-flight response later.
Other providers and the native buffering/media-source implementation are
unchanged. Final playback still receives validated local subtitle files.

One separately authorized live startup diagnostic passed 15 seconds of native
playback: stream resolution 4,563 ms, postprocessing 75 ms. Exactly two VTT GETs
returned 200, starting at 2,560/2,702 ms and completing at 2,698/2,859 ms of the
diagnostic clock, before session discovery ended. No 403/429, reloads or playback
errors were reported. Different network conditions prevent treating the total
startup difference as a controlled benchmark; the captured overlap itself is
direct evidence that subtitle HTTP work is no longer sequential after discovery.
Private capture: `alloha-startup-overlap-20260926`, outside Git.

This live binary predates the final deterministic request-queue ordering and
cache-generation publication guard; those are covered by offline checks. It
does not establish long-playback acceptance of the new player-free adapter; that
is a separate diagnostic described below.


### Minimal-bootstrap request identity and live acceptance

The first minimal-bootstrap diagnostic received the current HTML and program
with 200 but failed before video playback. A bounded diagnostic with WebView
request/error capture then identified metadata HTTP 404 (not 403). Offline
comparison found that the metadata URL, five form parameters, current page token,
Origin, Referer, browser identity and Borth fingerprint matched the website.
Executing the original archived provider permutation functions against both fresh
page seeds independently reproduced the exact outgoing Borth suffix.

The initial native HTML GET, however, omitted Chromium's Accept-Language and
iframe Fetch Metadata/X-Requested-With fields. The resolver now obtains languages
from the actual parent WebView before creating the iframe and uses matching
initial request headers. The metadata form Content-Type now matches the website
exactly. After these corrections, one new session resolved and began native
playback. This establishes that the corrected request combination works; it does
not isolate which individual initial header the service checks. HTTP failures now
retain their numeric status instead of being flattened to a generic IOException.

In that run, session bootstrap took 1,707 ms and validated subtitle postprocessing
took 1,704 ms: total resolution 3,415 ms. The first actual native video frame was
reported 7,129 ms after resolution began. These are distinct milestones and are
not a claim of sub-two-second video startup. Browser metadata request-to-response
was about 360 ms, so replacing that request's transport is not justified by this
measurement. The previous website comparator's first frame was 5,515 ms; different
network conditions and subtitle/native decoder work mean this is not a controlled
end-to-end speed comparison. What has been eliminated is the hidden browser media
startup before native playback.

The opt-in full-episode Maximum-buffer diagnostic requires the new bootstrap-ready
marker, no inherited browser edge token, and no inherited browser playback events.
Its raw private archive includes current provider HTML/program, metadata request
and response, browser errors, native HTTP headers/status/timing, event/stat bodies
and edge-token versions. These records are outside Git. No raw token is printed
in ordinary runtime logs or this document.

The compatibility path for an unknown WebView language-header format and the
translation-selection regression were tightened after this live APK was built.
They do not alter the tested selection: this page declares its first translation
as default and all translations offer the same quality set. Selection tests cover
a lower-resolution default beside a higher-resolution nondefault, and the case
where no translation is marked as default. Other translations are never mirrors.

Private run: `alloha-bootstrap-identity-20260926`. Full-episode playback passed:

- 1,420,187 ms played against a 1,420,180 ms duration (23:40), at 1080p and the
  Maximum buffer preset; the actual native decoder rendered the entire episode.
- One MediaItem load, 12 loading starts, peak buffer 237,319 ms, zero rebuffers,
  zero audio underruns, no playback errors or watchdog/fallback triggers.
- One successful control connection, five edge-token versions used by media
  requests, one HTTP reporting session, 142 heartbeats and one completed
  `view_finish`; no 403/429 or other HTTP error responses.
- Eleven stale idle HTTP/1.1 CDN connections closed before response headers. The
  HTTP client recovered the same resource in 260–1,298 ms, always receiving 200;
  none surfaced as a player load error or interrupted playback. This is distinct
  from retrying a provider rejection and confirms recovery after a full buffer.
- No provider browser media or advertising code ran during bootstrap. The test
  explicitly rejected fallback to browser-player discovery for this run.

The final `check :app:assembleDebug :app:assembleDebugAndroidTest` passed after
the compatibility/selection tightening and additional private HTTP-error-body
diagnostics. Debug unit reports contain 1,423 passed tests and one intentionally
skipped opt-in archive fixture. Fifteen offline Android tests passed on the final
APK: four bootstrap cases (including real WebView fetch and a single rejected
metadata request), language identity, compatibility handoff, paused switching,
downloads/details navigation and player-error focus. These final checks made no
real provider requests. The private archive retains the exact long-test APK and
its hash separately from the final APK.
