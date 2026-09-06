# Refactoring audit

Working baseline: `9d140e9` (2026-09-05). Scope: all application/data Kotlin,
Cast receiver, UI resources and runtime entry points. The audit and consolidation
are complete for this working-tree change. Verification supports the scenarios
listed below; hardware and live-provider limitations remain explicit.

Final production source count: **85 Kotlin + 1 JavaScript = 86 files**, down from
148 (**62 fewer, 41.9% reduction**). No production files were added to spread
functions for scoring. Shared decisions now belong to domain owners; the ownership
contract is documented in `adr/0001-application-ownership-boundaries.md`.

Latest complete Android gate: **814 app + 251 data tests in each of debug/release**,
zero failures/errors/skips, full `check` and debug APK assembly passed. The final
verification section records runtime evidence and the authorized Repowise policy.
Sections marked intermediate or follow-up below retain the audit chronology.

## Baseline

- Repowise weighted health: **8.05**, code-only **8.04**, unweighted **8.50**.
- Maintainability **9.53**, performance **9.98**; 157 indexed files, 62,135 NLOC.
- Production Kotlin: **146 files** (124 app, 22 data); receiver: 2 JavaScript files.
- Initial `gradlew check --no-build-cache --console=plain`: passed (most tasks
  already up-to-date). Changed behavior must execute its tests again.
- Repowise has no coverage map. Its dead-code list includes live same-package
  Kotlin declarations and manifest entry points; deletion requires verification.
- Baseline above used the original scoring rules, including historical
  defect/churn penalties. On 2026-09-05 the owner explicitly selected overall
  Health 9+ and authorized discarding historical penalties. Project health rules
  now disable the ten Git-history detectors; Git history and structural rules
  are unchanged. Scores under these two configurations are not directly comparable.
  Function scattering and history-erasing renames remain prohibited.

## Confirmed behavior

- A quality selected manually in the player persists across episodes of that
  anime, including Auto. General settings are the default before manual choice.
  Retain the existing in-process lifetime; persistence across app restarts was
  not requested.

## Review ledger

| Area | Status / evidence |
| --- | --- |
| Playback session and failure handling | Reviewed. Replaced ambiguous null outcome with Ignored/Recovering/Failed owned by session coordinator. Tests cover stale provider/anime/episode/voice failures, a retained surface during resolution, terminal failure and fallback with position/quality preserved. |
| Quality selection and player controls | Session preference now owns manual selection, including Auto and selection equal to the current default. Replaced four competing UI effects with one. Offline files use the shared height fallback policy. Popup ownership moved from a leaking global map to its PlayerView; focus callbacks cannot steal focus from a replacement popup. Resize invalidates cached popup geometry. Touch-opened popups also suspend underlying control focus, keeping subsequent D-pad selection inside the popup. Unit tests and debug compilation passed. |
| Progress/history and account synchronization | Progress publication is pure; cache/storage effects no longer occur inside a StateFlow retry. Tests cover retained history, stale anime updates and unchanged player routes. Cancelled history resolution propagates cancellation. Explicit logout and rejected/missing cached authentication now share operation cancellation and guest state; guest reload follows credential removal. Notification loads and mutations now share one serial queue. Failed edits restore the exact previous list/count while preserving unrelated profile changes, and system notification state is persisted only after backend success. Tests cover rollback, queued operations, refresh ordering and session cancellation. Phone runtime checks passed for test-account login, declined local-history upload, profile display, restoration after process restart, and logout from an account-filtered library back to the unfiltered catalog. Missing password/email keyboard types were corrected; stale cross-account responses are covered by storage and coordinator tests. |
| Cast receiver | Unified terminal playback phase and pending selection ownership, corrected error/loading and disconnected-sender behavior, menu focus preservation and navigation rows. The separate skip script is consolidated into the receiver. Twelve VM tests pass against shipped JavaScript with a fake Cast SDK/DOM; real Cast hardware is not yet verified. |
| Navigation, browse, details and focus | Reviewed and consolidated by domain. Phone/TV catalog, details, schedule, navigation and focus checks passed. Player popups and settings radio rows have one input owner. Comment drafts clear only after confirmed submission; stale account/anime responses are rejected. |
| Downloads and offline media | Audited queue identity, maintenance barriers, cancellation/error accounting, live limits, automatic resume, response validation, source cooldown and new single-choice wizard. Plan source restrictions survive storage and both resume paths. Local HTTP tests cover failure branches; phone/TV runtime covers the wizard and earlier queue/cache/resume scenarios. Heavy provider transfers are deliberately not repeated. |
| API, storage, resolvers and subtitles | Reviewed account/language/cache generations, HTTP cancellation and reader draining, per-response offline origin, atomic media publication, subtitle cache invalidation, and WebView request ownership. Provider parsing, fallback order and metadata selection reviewed against existing fixtures. Real repository/local HTTP tests pass; live-provider limits are listed below. |
| Settings, localization, theme and shared visual components | Reviewed live download limits, cache clearing, responsive scale/columns, localized bytes and episode/vote plurals, theme/gradient/focus cache lifetimes and shared surfaces. One complete settings writer replaces competing Activity writes. Phone 50/100/130 percent and phone/TV language round trips passed. |
| File consolidation | Production Kotlin reduced from 146 to 85 files; Cast JavaScript from two files to one. Existing declarations and runtime class names retained. Source bodies are grouped by domain, with behavior changes listed separately above. |

## Intermediate verification

- Full `check --no-build-cache --console=plain` passed after each consolidation
  wave; the second executed 46 tasks. At that point 770 app and 212 data tests
  passed in each of debug/release. Subsequent focused debug checks also pass.
- Current APK was installed with `adb install -r` on phone and TV emulators.
  Catalog appearance is preserved; details are readable in both layouts and TV
  video/controls rendered. Manual 480p selection in episode two remained 480p in episode three.
  Back during controller fade now follows requested visibility, avoiding repeated
  hide actions; two quick Back presses returned from the visible player to details.
  Playback was stopped after every runtime verification.
- Live CLI health after the second consolidation: average **7.43**,
  maintainability **9.49**, performance **9.97**, 96 indexed files. This includes
  receiver focus, popup and authentication cleanup; maximum CCN is 9 for the
  receiver (baseline 30) and 8 for player menus (baseline 11). This is the historical-rules score and is retained for transparency.
- MCP health still reports committed-index findings for moved code; live
  `repowise health --format json` is used to inspect working-tree changes.
  Reports/logs/screenshots are kept under ignored `build/`, outside production.

## History-independent Health policy

The authorized overall Health target uses the same source scope and detectors,
except for `developer_congestion`, `knowledge_loss`, `hidden_coupling`,
`function_hotspot`, `code_age_volatility`, `ownership_risk`, `churn_risk`,
`change_entropy`, `co_change_scatter`, and `prior_defect`. These consume Git
activity/ownership/fix history. No production file exclusions were added.
Existing test/coverage settings predate this audit. Before changing the rules,
reconstructing the live report from its finding impacts reproduced 7.431;
retaining only its organizational deductions yielded 7.895 at the same NLOC.
This diagnoses the historical contribution, not an achieved refactor score.

The first live run under the authorized current-code rules reported overall
Health **9.55**, hotspot Health **9.51**, maintainability **9.49**, performance
**9.97** across 96 indexed files. The target metric is reached under these rules;
completion still requires the remaining audit and regression checks. This run
includes notification transaction changes and predates queue locking.

## Concurrency follow-up (intermediate)

- Auth storage now publishes token/profile together after successful login,
  compares the expected token before background refresh/clear, and updates only
  the unread field for the active account. Nine storage/API tests cover stale
  responses after logout, old 401s during a new login, failed profile fetch after
  login, notification field isolation and token rotation with cache fallback.
  API tests execute the real repository/transport using a local OkHttp interceptor.
  Phone login via keyboard Done, profile display, restoration after force-stop
  and logout back to the login dialog also passed on the session-change APK.
- Download-service completion now checks the latest command lease on Main and
  Android `stopSelfResult(startId)`, preserving intents queued before their task
  rows exist. Existing serial-operation tests verify stale/latest lease ordering.
  Confirm rapid pause/resume, persisted queue restoration, and fallback-provider
  identity in offline metadata at runtime. A missing requested video ID now
  produces a failed result even when other episodes exist, instead of claiming
  that the unavailable episode was already downloaded; the regression test passes.

Final verification must include executed unit tests, full check, APK compilation,
available phone/TV runtime checks, updated Repowise metrics and a review of the
complete diff. Hardware-dependent behavior must be reported separately from
unit/build evidence.

Latest intermediate run after account/queue fixes: overall Health **9.52**,
hotspot **9.47**, maintainability **9.48**, performance **9.97**, 96 indexed files.
`check :app:assembleDebug --no-build-cache --console=plain` passed in 46 seconds
with 38 tasks executed. A subsequent focused download test run passed after the
missing-target fix. Runtime images are under `build/verification/*-latest.png`.

## Download/cache maintenance follow-up

The user confirmed that cache cleanup cancels the entire queue before deleting
all cached content. They additionally approved cancelling related downloads when
deleting one anime/episode while keeping unrelated downloads running.

- `DownloadCommandCoordinator` in the existing service file owns admission before
  Android delivers an intent, queued commands, individual video writers, and the
  maintenance barrier. Cleanup cancels and joins affected writers before touching
  files. Once cancellation starts, the queue/file transaction finishes even when
  the initiating screen closes. New requests wait for applicable maintenance.
- Episode deletion excludes its episode identity from existing batches, including
  alternate provider IDs; other workers continue. Persisted plans drop removed
  episodes and emptied plans cannot remain resumable queue entries.
- Offline metadata has one shared transaction lock across UI/service storage
  instances. Active artifact registration protects freshly completed files from
  orphan cleanup. Deleting the final completed episode preserves unrelated files
  still being written under the same anime directory.
- JSON caches use a temporary file and atomic replacement; readers coordinate
  with replacement for platforms that cannot replace an open destination.
- Source-quality and anime-content storage instances share their cache owners. Clearing from one
  instance invalidates all others and cannot resurrect previously cached entries.
- Coil memory/disk caches are cleared through Coil; its live journal directory
  is excluded from generic directory deletion. This latest change is compiled;
  final full check/runtime validation still follows.
- Tests cover drain ordering, waiting intents, cancellation of one batch member,
  continued unrelated work, future re-download requests, persisted-plan cleanup,
  shared index registrations and concurrent JSON readers/writers.

`check :app:assembleDebug --no-build-cache --console=plain` passed in 69 seconds
(42 executed tasks) after download/registry changes. Overall Repowise Health
remained **9.52** (96 indexed files). Subsequent source-cache/Coil changes passed
focused source-cache tests and APK compilation and still require the final gate.

Phone runtime: started a real two-episode plan; the first writer reached
39,772,070 bytes while Running. Clearing cache through Settings emptied persisted
queue JSON, removed the plan and episode artifacts, and stopped DownloadService.
A subsequent check still found an empty queue and only the empty offline index.
Screenshot: `build/verification/phone-cache-cleared.png`. This device check
predates the source-quality/Coil follow-up. No playback remains running.

The user approved applying speed and parallelism changes to the active queue.
`DownloadExecutionLimits` replaces the fixed semaphore and speed-settings polling.
Settings storage emits updates to service observers; one limiter governs every
video writer. Increasing capacity admits waiting writers, lowering it retains
existing writers and blocks new ones until capacity is available. Batch workers
use the common maximum rather than capturing a smaller worker count at startup.
Two limiter tests cover live increases/decreases and cancellation without leaked
slots; a settings test covers observation across independent storage instances.

The final gate for this iteration passed in 60 seconds with 49 tasks executed:
790 app and 230 data tests in each of debug/release, with no failures/skips.
Repowise: overall **9.53**, hotspot **9.47**, maintainability **9.49**, performance
**9.97**, 96 indexed files. `git diff --check` returned zero. This run includes
Coil, both shared cache owners, live limits, and 141 unused import removals.
Ten package-only test source files were also removed; executable tests retained.
Phone runtime on that APK also passed: a three-episode plan initially had one
Running video and two Queued videos. Changing parallelism from one to two started
the second video without restarting the service. Reducing to one kept both active
writers and the third queued; both active writers subsequently reported bytes.
Cleanup then emptied the queue, removed episode files and stopped the service.
After closing Settings and scrolling, catalog images still loaded correctly.
Evidence: `phone-live-limits-raised.png`, `phone-live-limits-lowered.png`,
`phone-cache-cleared-latest.png`, `phone-after-cache-catalog-latest.png` under
`build/verification`. No downloads or playback were left running. Parallelism was
restored to one; the speed setting remained five MB/s.


## Batch outcomes, update cancellation and background account follow-up

The owner approved distinguishing cancelled episodes from failed ones and
cancelling the APK update download before cache deletion.

- Batch execution retains unavailable requested episodes as errors; an unresolved
  plan can no longer claim everything was already downloaded. Completed and
  individually cancelled episode identities are pruned from the stored retry plan.
  Summary results count completed/cancelled/failed episodes separately, preserve
  pauses, and use localized Russian, English and Ukrainian messages.
- Update downloads use the same command admission/maintenance coordinator as media
  downloads. A replacement waits for the previous writer to drain. Cancellation
  removes partial APK files and prevents installation. Only a complete response
  atomically replaces an existing APK; cache maintenance also clears pending install
  state and the update notification before deleting files.
- The full gate after these changes passed in 40 seconds (47 executed tasks), with
  796 app and 230 data tests in each debug/release variant. Health was **9.52**,
  hotspot **9.47**, maintainability **9.48**, performance **9.97** (96 indexed files).
  Logs: `build/plan-update-full-check.log`, `build/repowise-health-plan-update.json`.
- A background notification request now captures token/profile together and publishes
  local effects under the session owner's lock. A stale response or rejection cannot
  update or clear a new account. The same lock covers persisted unread counts and
  Android badge changes. UI reads, mutations and background checks share one mutex.
- The owner approved separate notification history per account. Initialization,
  seen events, check spacing and unread snapshots use profile-scoped keys. Returning
  to an account retains its history. Unattributed legacy global history is not
  assigned to a potentially different account: the first check establishes a quiet
  baseline of existing notifications, while later new events generate alerts.
- Focused tests cover account switch/logout during a response, current versus stale
  authentication rejection, successful publication, retained login on network error,
  separate notification histories and serialization across coordinator instances.

Remaining audit findings to resolve:
- Review durable background download scheduling under Android 12+ start restrictions
  and Android 15/16 execution quotas, beyond deferring rejected starts until the
  next visible session. Official platform references:
  https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
  https://developer.android.com/develop/background-work/background-tasks/data-transfer-options
- Continue API/resolver and responsive UI/settings/theme audit. File
  consolidation alone is not evidence those areas have been fully reviewed.

## Subtitle publication, interrupted queue and responsive follow-up

The account gate `build/background-notification-full-check.log` passed in 37 seconds
with 798 app and 233 data tests per variant; Health stayed **9.52**.

- Subtitle files now reuse atomic file publication instead of deleting the old
  destination and falling back to copying. Concurrent readers coordinate with
  replacement; rejected writes retain the old file. Cancellation propagates from
  subtitle fetches instead of becoming a missing optional track.
- The owner approved automatic continuation after reopening the app. Persisted
  Running/Queued tasks restore as Interrupted, distinct from a manual pause.
  A batch resumes once through its summary; manually paused batches remain paused.
  The foreground Activity requests resumption; network policy still gates writers.
  Shared unfinished/waiting properties drive queue retention, actions and visibility.
- If Android rejects a service restart from a background network callback, the queue
  restores its previous state instead of claiming a nonexistent writer is Queued.
  The next visible app session retries it. Durable background scheduling remains an
  audit item; this does not claim unrestricted background resume.
- Download state labels use the enum's resource mapping for Compose and runtime
  notices, removing a second hardcoded English mapping. Localized text memoization
  compares actual arguments rather than a potentially colliding hash.
- The owner approved reducing catalog/schedule columns when scaled card content no
  longer fits. Both grids cap their preferred column count by usable width after
  padding/gaps and the selected card size's readable minimum.
- The owner clarified that episode zero is valid. Schedule captions now use
  "Episode N is already out" in all three languages, including episode zero;
  a zero value is not converted into an unreleased/unknown state.

The latest full gate (`build/auto-resume-layout-full-check.log`) passed in
32 seconds (36 executed tasks): **800 app + 235 data tests per debug/release**,
no failures/errors/skips, lint and debug APK assembly passed.

Runtime note: the phone emulator using SwiftShader indirect crashed twice while
applying scale; Windows recorded qemu-system-x86_64-headless.exe access violations.
The same AVD was restarted on port 5584 with `-gpu host`. Scale 50% and 130% then
applied successfully. The original port 5554 is offline. TV remains on port 5582.
Before the latest card-width change, 130% exposed clipped view counters; current
APK runtime validation follows. TV settings child Back returned to Settings and
schedule D-pad navigation moved focus to the next card. No player was opened.

Runtime follow-up on the new APK confirmed readable 130% cards with one column,
and the TV schedule showed both episode 1 and episode 0 correctly. Scale was then
restored to 100%. Evidence: `phone-scale-130-columns-fixed.png`,
`phone-scale-restored-100.png`, `tv-schedule-episode-zero-fixed.png`.

A real two-episode download reached 105,944,425 bytes before force-stop. Reopening
resumed its plan automatically; a subsequent manual pause remained paused after
another force-stop/reopen. This uncovered duplicate child rows when a resumed plan
selected a different provider video ID. The fix gives a plan episode a stable
episode key, updates its existing row to the chosen source, and merges inactive
legacy rows representing that episode. Other plans/episodes keep separate rows.
Focused tests cover provider/voice changes, legacy row migration and episode zero.
The test queue and its partial files were cleared; DownloadService is stopped.

Delivered resume intents now check that their queue entry still exists and is
runnable before processing. A command admitted during cache maintenance cannot
recreate a removed queue entry. Its full gate passed in 27 seconds before the
episode-identity follow-up. The episode identity gate passed with **802 app + 235
data tests per variant** and Health **9.51**. Runtime then confirmed that episode
one retained task ID 2 while its provider video ID changed from 1037143 to 1000280.
Only one summary and two children remained; the resumed writer reached 120,132,624
bytes. Evidence: `episode-identity-before.json`, `episode-identity-after.json`,
`episode-identity-running.json`, `phone-download-stable-episode-resume.png`.
Cache cleanup again emptied the queue and left only the empty offline index, with
no DownloadService running (`phone-cache-cleared-stable-episode.png`).

## HTTP cancellation, response identity and localization (2026-09-06)

- One coroutine HTTP owner now closes the OkHttp call on cancellation and waits
  for the response reader to finish. API requests, asynchronous provider requests,
  direct/HLS downloads, release checks and APK downloads use it. Socket tests stall
  both before headers and during the body, then verify prompt cancellation and
  reader completion. No unjoined callback can continue writing after cleanup.
- Direct download resume validates Content-Range offsets and final file size.
  HTTP 416 no longer promotes an arbitrary partial file to a completed download;
  an incompatible rejected partial is discarded so the existing retry starts fresh.
- Content fetches capture language, account and cache generation. Account changes,
  language changes (including a round trip) and cache clearing prevent stale writes.
  Tests execute real repository/API/cache paths and prove that a subsequent fresh
  request still populates its proper cache partition. Failed quality discovery no
  longer deletes a potentially newer successful result from another request.
- Existing plan episode identity survives an individual explicit retry as well.
- Cache size now uses the shared localized byte formatter. Episode headings share
  a formatter across video cards and download runtime; download storage rows also
  use translated captions. Russian/Ukrainian episode and vote plural resources were
  corrected; English uses its own singular rule rather than Slavic endings. Tests
  cover zero, teens, 21/22, negative values and Long.MIN_VALUE.
- Removed the unused legacy card minimum-width field; layout owns the only active
  card-width policy. No production files were added.

`build/http-cache-localization-full-check.log` passed in **50 seconds**, with 49
executed tasks: **802 app + 240 data tests per debug/release**, no failures/skips,
lint and debug APK assembly passed. The obsolete cache formatter test was removed
with its duplicate implementation; shared byte formatter tests remain.
`git diff --check` returned zero. Live Health: **9.50**, hotspot **9.45**,
maintainability **9.48**, performance **9.97**, 96 indexed files under the authorized
history-independent rules (`build/repowise-health-http-cache.json`).

## Provider restrictions and source lifecycle

The user explained that heavy transfers or parallel requests can cause sources to
temporarily return 403. The longer runtime download reached 188,301,734 bytes, then
failed with a source HTTP 403. This is not recorded as successful complete-download
validation or proof of an application defect. The queue/partials were cleared and
both emulators are stopped; further source checks use local responses.

The owner approved a five-minute queue-wide provider cooldown after download HTTP
403, immediate alternative-source fallback, and automatic continuation afterward.
The first rejection persists the deadline across service/app restarts. Additional
rejections during the same interval do not extend it. Source request policy gates
subsequent HTTP requests, and HLS/direct retries propagate the cooldown immediately.
Playback outside downloading and unrelated API authentication are outside this rule.

Waiting downloads release their concurrency slot, preserve partial data, and do
not consume ordinary error retries. They remain cancellable, respect manual/network
pause, and show a localized waiting message. Tests verify cross-episode/voice and
repository sharing, deadline expiry, alternative selection, no repeated request
before expiry, freed slots, automatic retry and interruption during waiting.

Source URL probing and generic/post-processed stream reads now use the same HTTP
lifetime owner. Domain candidates, known hosts and cached selection are one atomic
snapshot; a response for a replaced configuration cannot reinstall a removed domain.
Normal completed calls are not cancelled; stalled calls still cancel and drain.
The HTTP policy and domain/cancellation regression tests pass.

`build/source-cooldown-full-check.log` passed in **77 seconds**, 45 tasks executed:
**804 app + 245 data tests per debug/release**, no failures/skips, lint and APK
assembly passed. Live Health **9.51**, hotspot **9.46**, maintainability **9.48**,
performance **9.97**, 96 indexed files (`build/repowise-health-source-cooldown.json`).
Production remains **85 Kotlin + 1 JavaScript files**. This APK has not been used
for another real-provider transfer, in accordance with the user's throttling note.


## HTTP/WebView ownership and Android timeout follow-up

- Foreground-service timeout callbacks now stop Android services promptly while
  non-cancellable maintenance drains download writers. Episode work becomes
  interrupted and retains progress; manually paused work remains paused. APK
  timeout cancels its writer and publishes the localized failure state.
- A real API 36 emulator test temporarily set the data-sync timeout to five
  seconds and connected the update downloader to a stalled local TLS socket.
  Android logged its timeout; the service disappeared without an app fatal error
  or APK publication. The setting was restored to its original null value and
  the app stopped (`build/verification/local-fgs-timeout.json`).
- Repository content now carries its own offline-fallback origin. Concurrent
  catalog/details responses no longer compete through one mutable boolean.
- Subtitle cache publication/cleanup uses one generation guard. Late HTTP bodies
  and captured JavaScript bodies cannot recreate cleared files. WebView's blocking
  interception boundary owns cancellable HTTP children; termination drains them.
  Relevant native WebView 403 responses also participate in provider cooldown.
- Direct resume rejects overflowing Content-Range numbers. Completed media uses
  shared atomic replacement; a missing replacement preserves a valid destination.
  Local socket, interceptor and real filesystem regression tests pass.

## Download wizard and single-choice follow-up

The owner's final wizard is voice -> source -> episodes -> quality, with exactly
one selected voice, source and quality. UI state stores scalar choices; priority
controls and obsolete multiple-choice plumbing were removed in the existing file.
Repeating a selection leaves it selected. Voice changes choose an available source;
episode ranges are validated against that source. Episode zero remains valid.

Quality discovery receives one episode from the selected voice/source/range and
starts only on the quality step. Returning with unchanged inputs reuses the result.
A local repository test records exactly one manifest request and no requests for
other episodes, voices, or subtitles. Stale probe/build results cannot enable the
Download action. Errors no longer leave a competing preparation spinner visible.

New plan items persist the source restriction. Batch workers and individual resume
both honor it, including replacement provider video IDs. Existing saved plans retain
their historical configuration. Tests cover persistence and disallowed fallback.

Runtime on the phone confirmed exclusive voice/source/quality selection, repeated
selection, source-filtered series 1-11, a 1-2 range, the two-episode summary, and
720/480/360 quality switching with exactly one checked item. Back/Next retained
720p. TV D-pad focus and voice/source rendering passed. No media transfer was
started; queues are empty and both app processes were stopped. Screenshots:
`phone-download-single-voice.png`, `phone-download-single-source.png`,
`phone-download-single-quality-retained.png`, `tv-download-single-voice.png`,
`tv-download-single-source.png`, under ignored `build/verification/`.

An earlier wizard runtime check caught a localized cooldown-format crash caused by
reading a formatted resource without its argument. Formatting now occurs only in
its error branch with the remaining-minute value; the subsequent runtime passed.

Latest full gate: `build/download-single-choice-final-check.log`, **8 seconds**,
11 tasks executed after the preceding compile/lint run. **810 app + 249 data tests
per debug/release**, no failures/errors/skips; full check and debug APK passed.
`git diff --check` passed. Live Repowise overall **9.48**, hotspot **9.43**,
maintainability **9.48**, performance **9.97**, 96 indexed files under the authorized
rules (`build/repowise-health-single-choice.json`). Production remains **85 Kotlin
and 1 JavaScript files**, down from 148 total. Overall audit remains in progress.


## Settings decoding, provider interruption and comment submission

Settings now decode from one SharedPreferences snapshot, safely handling mismatched
stored types and retaining valid unrelated fields. This removes typed Android
getters that could throw on startup; the old in-memory test double had concealed
that difference. A proxy-based adapter test requires one snapshot read and rejects
any typed getter. Legacy long-valued scales clamp before narrowing to Int.

Phone runtime injected three wrong-typed settings into the existing preferences,
then confirmed that the catalog opened without an app fatal. The exact original
settings bytes were restored and the process stopped. Evidence:
`build/verification/settings-corruption-runtime.json` and
`phone-corrupt-settings-recovered.png`. Android's typed-getter contract is documented
at https://developer.android.com/reference/android/content/SharedPreferences.

Settings radio rows now have a single input owner and expose radio semantics on
the row. The inner marker does not create another clickable/focusable target. TV
runtime confirmed zero nested clickable nodes and consecutive D-pad focus moving
to different language rows (`tv-settings-single-radio-focus.png`). No language
was changed; the app was stopped. The settings gate passed in 34 seconds with
46 executed tasks before the following resolver and comment changes.

Provider cooldown is now propagated through domain probing, CVH's WebView fallback,
and stream/metadata post-processing. A paused provider cannot invalidate the cached
catalog domain, start a redundant runtime resolver, or silently become missing
metadata. Local HTTP tests check one rejection, the original exception, no WebView
fallback, preserved domain selection, and rejection during the second manifest
read. Skipped playback probes still perform no request. The resolver gate passed
in 25 seconds with 810 app + 253 data tests per variant.

The owner approved keeping a comment draft until successful submission and blocking
repeated submission while pending. AnimeCommentSubmissionCoordinator now owns that
operation state and account/anime identity. The form clears only the acknowledged
text; edits made during sending survive success. Failure/cancellation releases the
pending state. Logout clears it; late responses cannot overwrite a new account's
submission. Captcha retries retain their original anime/account and cannot post an
old draft into a newly opened card. No real comments were posted during verification.

Three coroutine tests cover failure/retry/double press, edits during sending,
completion after navigation, cancelled account work and stale retry targets. The
latest full gate `build/comment-submission-final-check.log` passed in **26 seconds**,
21 tasks executed, with **813 app + 253 data tests per debug/release**, no failures,
errors or skips. Debug APK assembly and diff whitespace checks passed. Production
still has **85 Kotlin + 1 JavaScript files**. The broader audit remains active.

Live health after these changes: overall **9.47**, hotspot **9.42**,
maintainability **9.48**, performance **9.97**, 96 indexed files, same authorized
rules (`build/repowise-health-comment-submission.json`).

## Final verification and limits

The last settings change removes the Activity's separate language/scale writes.
AppSettingsRuntime saves the full normalized snapshot; Activity recreation observes
that save and compares it with the configuration actually attached to the Activity.
Obsolete partial settings APIs and their two obsolete tests were removed. Complete
storage round-trip/observation coverage remains; a new test verifies that only
language or scale requires recreation, while unrelated settings do not.

The final Android gate, `build/settings-single-writer-check.log`, passed in
**37 seconds**, 46 tasks executed: **814 app + 251 data tests per debug/release**,
no failures/errors/skips, full `check` and `:app:assembleDebug` with
`--no-build-cache`. The APK is
`app/build/outputs/apk/debug/YummyDroid-1.4.44-debug.apk`.

That APK was installed on both emulators. Phone runtime verified scale
100 -> 130 -> 100 and Russian -> English -> Russian. At 130 percent the catalog
uses one readable column; at 100 percent it returns to two. TV language switching
and catalog rendering also passed. Final stored phone settings are Russian/100;
both queues are empty and both application processes were stopped. Evidence:
`build/verification/settings-single-writer-runtime.json`,
`phone-saved-settings-scale-130.png`, `phone-saved-settings-english.png`, and
`tv-settings-single-writer-restored.png`.

Final review compared the complete changed-file set with the baseline, using the
recorded consolidation map to separate moved bodies from behavior changes. The
remaining provider branches, shared surface/focus code, settings transitions and
changed resource wiring were reviewed. The only removed Kotlin type names are the
four replaced download configuration/limiter types. Every application class named
by the Android manifest still resolves, including the Cast options provider.
No conflict markers, duplicate localized string names or dangling references to
the removed Cast script were found. `git diff --check` passed. No dependencies,
manifest permissions, version numbers or release configuration changed.

Repowise remains above the owner's overall 9+ target under the approved
history-independent rules. The final report is `build/repowise-health-final.json`;
it records overall **9.48**, hotspot **9.42**, maintainability **9.48**,
performance **9.97**, 96 indexed files. No
production exclusions or severity changes were introduced. Git history is retained.

Verification limits:

- Cast behavior is covered by twelve passing local VM tests against the shipped
  receiver (`build/cast-final-tests.log`); no physical Cast device was available.
- Real-source playback and queue activity were exercised earlier in the audit,
  but complete sustained media downloading is not claimed. A provider returned
  HTTP 403 during a large transfer; no further stress transfers were performed.
  Cancellation, resume, range validation and cooldown branches use local HTTP,
  filesystem and coroutine tests. The final wizard checks did not start a transfer.
- No real comments were posted. Submission, failure, retry, duplicate press,
  account change and stale response behavior use local coordinator tests.
- At audit completion the change was prepared in the working tree; publication
  was subsequently requested by the owner.

## Release 1.4.45 verification

The requested release increments versionName to **1.4.45** and versionCode to
**460**. `check :app:assembleRelease --no-build-cache --console=plain` passed in
61 seconds, with 51 tasks executed. The same 814 app + 251 data tests pass in
each variant. Log: `build/release-1.4.45-check.log`.

The minified release APK is **7,247,824 bytes**, is not debuggable, and passes
`apksigner verify`. Its signing certificate matches the published 1.4.44 APK,
whose download was verified against GitHub's SHA-256 digest. New APK SHA-256:
`f109c94346245f339e233384f0aeb6784a1f876739953647bc5ab6aacf06a571`.

This release APK upgraded both phone and TV installations successfully. Catalog
and settings screens opened without an application fatal; both processes were
stopped afterward. Evidence: `build/release-1.4.45/verification.json` and the two
device smoke reports in that directory. Release metadata uses tag `v1.4.45`,
title `YummyDroid 1.4.45` and an empty release body, following the existing policy.

## Release 1.4.46: download presentation, live local state and offline playback

Source choices in the download planner show episode coverage for the selected
voice without extra provider requests. Its footer stays on one row at 360 dp and
130% interface scale. Download cards use less padding and place transfer metrics
beside their actions; duplicate status text is omitted. Plan subtitles include
voice, source and quality. A completed download no longer resets an open planner
or invalidates its quality probe merely because local file metadata changed.

Downloaded episode labels use ranges, including episode zero, gaps and specials.
OfflineAnimeStorage now publishes a shared revision after successful media
mutations. Details reconcile that snapshot on completion, deletion and route
restoration, replacing transient queue-completion detection and remote refreshes.
Runtime checks observed `1`, then `1-2`, then `1-3` after returning to a cached
card without restarting the process; deletion immediately removed the label.

Back from an anime opened through Subscriptions restores that dialog and its
saveable grid state. The return target is bound to the account and navigation
depth, including the case where the underlying route is the same anime. Runtime
verification used the owner's test account and a temporary subscription; that
subscription was removed and the test session logged out afterward.

Offline mode now follows Android network availability and verified site recovery.
Route caches no longer store connectivity. Cached or late responses cannot turn
online operations back on. Offline details load local metadata, images use cache
only, and remote extras/history/player metadata requests are cancelled or skipped.
Player voice/episode/source/quality choices contain downloaded variants only.
Local playback bypasses provider resolution, and local failure cannot start an
online fallback. Unit tests reject any provider or metadata call on this path,
including stale online selections, a locked source and loss of connectivity while
resolving a stream.

On the phone emulator with Wi-Fi and mobile data disabled, a cold start opened
saved details without DNS errors/retry UI. A valid generated MP4 played locally,
and Next opened downloaded episode 2; the controls showed `AnimeVost`, episode
`2 of 2`, and advancing time. Test media and queue entries were removed, scale
returned to 100%, density restored, and network connectivity re-enabled. Both
phone and TV processes were stopped after release upgrade smoke checks.

`check :app:assembleRelease :app:assembleDebug --no-build-cache --max-workers=2`
passed: **826 app + 253 data tests in each variant**, lint and minified packaging.
The initial unrestricted parallel run lost a Gradle worker's localhost connection;
the repeat passed without code changes. Logs are
`build/release-1.4.46-check-retry.log` and `build/release-1.4.46/verification.json`.
The temporary debug instrumentation is absent from the final APK manifest.

Repowise reports overall **9.45**, hotspot **9.42**, maintainability **9.44** and
performance **9.96**, with the same 96 indexed files and 86 production code files.
No new production code files, exclusions or scoring changes were introduced.
Report: `build/repowise-health-1.4.46.json`.

Release versionName is **1.4.46**, versionCode **461**. APK size: **7,247,824 bytes**;
SHA-256: `0bf9397a84388e58e3d8c5963621e564f77f544fa6c4e378bb101fb3b25044ba`.
Signature verification passed and both device upgrades succeeded. The release
uses tag `v1.4.46`, title `YummyDroid 1.4.46` and an empty body.

## Release 1.4.47: centralized navigation and scroll boundaries

`AppNavigationController` now owns application input, modal/player registrations,
focus scopes and cancellable UI work. The Activity translates platform events;
the root binding connects route actions. The former separate mutable input state,
per-button native key listeners, search replay queue and toolbar requester graph
were removed. Screens declare focus targets and policies through the controller.
There are no new production files; production code is smaller overall.

The audit found competing platform/manual focus searches, diagonal horizontal
fallbacks, stale adapter disposal, delayed popup-anchor restoration and grid math
that skipped a partially filled final row. These paths now use live bounds and
active scopes. Left/Right stay in the current visual row, including phone toolbar
reflow at 130%; Up/Down reach partial rows and nested card actions. Disabled and
detached targets are excluded. Explicit browse-section switching at horizontal
grid edges and episode page switching are preserved as requested.

Every vertical scroll container declares its scroll region. When traversal runs
out of targets, Up/Down scroll the remaining content without changing focus.
Nested regions drain inside out, and modal boundaries isolate the background.
The runtime fixture reached exactly 4,794 px on the phone and 4,400 px on TV,
then returned to zero, retaining the same focused button throughout.

The native player consumes normalized actions centrally: transport Left/Right
move between Previous, Play/Pause and Next; unavailable row edges stay put.
Popup Back restores its anchor synchronously. Joystick axes follow the same
dispatch path as D-pad keys, matching key-up events are consumed, and repeated
discrete media commands cannot toggle twice. Loading/ready adapter disposal
cannot unregister a newer adapter.

Text-field arrows edit the cursor only with the field focused and IME visible;
otherwise they traverse adjacent controls. Search Back closes the actual IME
before the panel. Runtime input inserted a character inside a multiword query,
then moved to the microphone after the keyboard closed. Settings sliders retain
horizontal value adjustment; selected picker entries receive initial focus.

`check :app:assembleRelease :app:assembleDebug --no-build-cache --max-workers=2`
passed in 1m 44s: **837 app + 253 data tests in each variant**, lint and minified
packaging. Tests cover geometry, scope ownership, scroll boundaries, repeat
handling, text editing and registration replacement. Runtime checks exercised
TV D-pad/joystick events and phone portrait/landscape at 100% and 130%, player
transport and popup anchors, search, settings and the reflowed toolbar. These
used generated local media; no physical remote or Cast device was exercised.
Temporary instrumentation and media are absent from the final APK/device data.
Scale was restored to 100%, fixture history removed and both processes stopped.

Repowise before commit reported overall **9.42**. Reindexing release commit
`1a00648` reports overall **9.41**, hotspot **9.36**, maintainability **9.41** and
performance **9.96**, with 96 indexed and 86 production code files. The 9+ target
is retained without exclusions, scoring changes or splitting production files.
Reports: `build/repowise-health-1.4.47-committed.json`,
`build/release-1.4.47-check.log` and `build/release-1.4.47/verification.json`.

VersionName is **1.4.47**, versionCode **462**. The APK is **7,247,824 bytes**,
not debuggable, and passes signature verification with the same certificate as
the published 1.4.46 APK. SHA-256:
`0174fa59cc09854aef285fb0c42b0703c153052c7665a94a71daf3737546d5c1`.
Both emulator upgrades opened catalog and settings and returned with Back without
an application fatal. Release metadata uses tag `v1.4.47`, title
`YummyDroid 1.4.47` and an empty body.


## Release 1.4.48: player lifetime, responsiveness and poster prefetch

The exit-layer cache recreated a removed player under a new composition key.
That hidden instance restarted playback at the route's saved position, explaining
the audio heard after Back. Player routes now leave composition immediately;
ordinary screen transitions retain their existing cache. The session is the sole
release owner, including CastPlayer's owned local instance. The former second
release sent pause/stop/clear commands to an already terminated playback thread.
Runtime logs now show one initialization and one release per session, without
that background restart or dead-thread warnings.

Loading, resume-choice and ready playback share one movable native PlayerView.
This removes repeated themed XML inflation and native controller construction
on weak hardware. The binding changes with presentation state; geometry changes
still select the proper layout. Both emulator shell-cycle checks retained one
view through loading/ready/resume/ready, and full-app checks covered phone
rotation, TV transport focus, local playback, one online DASH session and Back.
No physical TV remote or Cast receiver was available. Online transport latency
and device decoder costs remain outside a universal responsiveness guarantee.

Intermediate download progress previously serialized the whole queue, updated
notifications and published application state after every 8 KiB read. A gate
owned by each attempt now publishes at 250 ms intervals, with immediate
source/voice/quality changes and completion. Cancellation checks still run on
every callback. A deterministic 1,000-callback burst produces four intermediate
updates plus immediate completion; new attempts and metadata changes bypass
that interval. Terminal state and partial-file resume ownership are unchanged.

Posters, backdrops and screenshot thumbnails outside the fixed catalog texture
cache use measured decode bounds rather than full-size originals. A 2048x3072
fixture decoded to 400x600 on TV and 525x788 on the phone at the tested bounds;
fullscreen screenshot viewing remains a separate request. Catalog/history and
schedule share one poster prefetch path and the same request builder/cache key
as visible cards. It warms one viewport beyond visible rows, accounts for TV's
calendar header, skips obsolete queued work and stops for inactive screens.
One worker avoids bursts; failed speculative requests do not become visible
errors. The catalog requests its next API page before the viewport buffer runs
out, including touch scrolling. History and schedule already have complete data
snapshots, so their extra work is image warming only. Offline policy is retained.

In the isolated runtime check, visible indices 0-11 had decoded indices 0-23;
after scrolling to 20-31, indices 32-43 were already warm. Disabling prefetch and
scrolling to 40-51 did not load indices 52 onward. The same checks passed with
phone and TV column layouts. StrictMode recorded no main-thread disk/network
violations during the inspected settings, filters, history and schedule flows.

`app/src/main/baseline-prof.txt` contains measured profile data, not another code
module. It was captured on API 36 from a non-debuggable, unminified build and
filtered against the compiled app/data class and method signatures: 7,840 rules,
with instrumentation and DEX-only generated symbols excluded. The normal R8
build rewrites/compiles it with dependency profiles into 8,909-byte baseline.prof
and 1,326-byte baseline.profm assets. ProfileInstaller returned success (1).
The generation/installation procedure follows the Android Developers
[manual profile workflow](https://developer.android.com/topic/performance/baselineprofiles/manually-create-measure).

Five cold first-display measurements on the TV emulator were 611/893/825/827/860
ms with `verify`, and 401/562/836/650/543 ms with `speed-profile` (medians 827 and
562 ms). This compares the same final APK's complete bundled profile against
uncompiled execution, not the incremental contribution of app rules alone.
The OS file cache was warm and an emulator is not a benchmark of the user's TV.
Evidence: `build/release-1.4.48/startup-benchmark.json`.

`check :app:assembleRelease :app:assembleDebug --no-build-cache --max-workers=2`
passed in 2m 1s: **844 app + 253 data tests in each variant**, lint and minified
packaging. Temporary instrumentation is absent from both manifest and DEX.
Both final APK upgrades opened catalog, settings and downloads without a fatal;
fixture media/history were removed, network/phone rotation restored and both
processes stopped. Existing unrelated local history was preserved.

VersionName is **1.4.48**, versionCode **463**. The APK is **7,264,208 bytes**, is
not debuggable, and verifies with the same signing certificate as prior releases.
SHA-256: `4585b7b3e1914fcf079694a557ac64635e683c8ed1208f4962578aff0a716ad9`.
Verification records are under `build/release-1.4.48/`. Release metadata uses tag
`v1.4.48`, title `YummyDroid 1.4.48` and an empty body.

Repowise after the player, download and prefetch commits reports overall **9.40**,
hotspot **9.35**, maintainability **9.41** and performance **9.96**. The repository
still has 96 indexed files and 86 production code files (85 Kotlin plus the Cast
receiver script). The added profile is generated data; no production code was
split into new files, and scoring rules/exclusions were not changed.
