'use strict';

(function registerYummyCastSkipController(global) {
    const COUNTDOWN_SECONDS = 8;
    const MIN_REMAINING_MS = 1_500;
    const CLUSTER_TOLERANCE_MS = 2_000;
    const POLL_MS = 500;
    const SKIP_LABEL = '\u041f\u0440\u043e\u043f\u0443\u0441\u0442\u0438\u0442\u044c';
    const WATCH_LABEL = '\u0421\u043c\u043e\u0442\u0440\u0435\u0442\u044c';

    class YummyCastSkipController {
        constructor(options) {
            this.controls = options.controls;
            this.skipButton = options.skipButton;
            this.watchButton = options.watchButton;
            this.timeline = options.timeline;
            this.seekTo = options.seekTo;
            this.onPromptShown = options.onPromptShown;
            this.onPromptDismissed = options.onPromptDismissed;
            this.bindingKey = '';
            this.markerKey = '';
            this.segments = [];
            this.dismissedKeys = new Set();
            this.activePrompt = null;
            this.currentPositionMs = 0;
            this.skipEnabled = true;

            this.skipButton.addEventListener('click', () => this.skipActivePrompt());
            this.watchButton.addEventListener('click', () => this.watchActivePrompt());
            this.watchButton.textContent = WATCH_LABEL;
            global.setInterval(() => this.poll(), POLL_MS);
        }

        get visible() {
            return !this.controls.hidden;
        }

        get buttons() {
            return this.visible ? [this.skipButton, this.watchButton] : [];
        }

        update(payload, currentPositionMs, durationMs) {
            const segments = normalizeSegments(payload?.video?.skipSegments, durationMs);
            const bindingKey = playbackBindingKey(payload, segments);
            if (bindingKey !== this.bindingKey) {
                this.bindingKey = bindingKey;
                this.dismissedKeys = new Set();
                this.clearActivePrompt(false);
            }
            this.segments = segments;
            this.currentPositionMs = Math.max(0, Number(currentPositionMs) || 0);
            this.skipEnabled = payload?.skipOpeningsAndEndings !== false;
            this.renderTimelineSegments(segments, durationMs);
            this.poll();
        }

        cancelAutoCountdown() {
            if (!this.activePrompt?.autoSkipEnabled) return;
            this.activePrompt.autoSkipEnabled = false;
            this.skipButton.textContent = SKIP_LABEL;
        }

        dismissForInterfaceHide() {
            if (this.activePrompt) this.clearActivePrompt(true);
        }

        poll() {
            if (!this.skipEnabled || this.segments.length === 0) {
                this.clearActivePrompt(false);
                return;
            }
            if (this.activePrompt) {
                if (!hasUsefulSkip(this.activePrompt, this.currentPositionMs)) {
                    this.clearActivePrompt(true);
                    return;
                }
                this.updateCountdown();
                return;
            }
            const segment = this.segments.find((candidate) =>
                !this.dismissedKeys.has(candidate.key) && hasUsefulSkip(candidate, this.currentPositionMs));
            if (segment) this.showPrompt(segment);
        }

        showPrompt(segment) {
            const cluster = connectedCluster(this.segments, segment);
            const now = Date.now();
            this.activePrompt = {
                activeStartMs: Math.min(...cluster.map((item) => item.startMs)),
                targetEndMs: Math.max(...cluster.map((item) => item.endMs)),
                dismissKeys: cluster.map((item) => item.key),
                deadlineMs: now + COUNTDOWN_SECONDS * 1_000,
                autoSkipEnabled: true,
            };
            this.controls.hidden = false;
            this.updateCountdown(now);
            this.onPromptShown();
        }

        updateCountdown(now = Date.now()) {
            const prompt = this.activePrompt;
            if (!prompt?.autoSkipEnabled) return;
            const remainingMs = prompt.deadlineMs - now;
            if (remainingMs <= 0) {
                this.skipActivePrompt();
                return;
            }
            const seconds = Math.min(COUNTDOWN_SECONDS, Math.ceil(remainingMs / 1_000));
            this.skipButton.textContent = `${SKIP_LABEL} ${seconds}`;
        }

        skipActivePrompt() {
            const prompt = this.activePrompt;
            if (!prompt) return;
            const targetEndMs = prompt.targetEndMs;
            this.clearActivePrompt(true);
            if (this.currentPositionMs < targetEndMs) this.seekTo(targetEndMs);
            this.onPromptDismissed();
        }

        watchActivePrompt() {
            if (!this.activePrompt) return;
            this.clearActivePrompt(true);
            this.onPromptDismissed();
        }

        clearActivePrompt(markDismissed) {
            if (markDismissed && this.activePrompt) {
                this.activePrompt.dismissKeys.forEach((key) => this.dismissedKeys.add(key));
            }
            this.activePrompt = null;
            this.controls.hidden = true;
            this.skipButton.textContent = SKIP_LABEL;
        }

        renderTimelineSegments(segments, durationMs) {
            const duration = Number(durationMs) || 0;
            const markerKey = `${duration}|${segments.map((segment) => segment.key).join(';')}`;
            if (markerKey === this.markerKey) return;
            this.markerKey = markerKey;
            const gradients = duration > 0
                ? segments.map((segment) => {
                    const start = segment.startMs * 100 / duration;
                    const end = segment.endMs * 100 / duration;
                    return `linear-gradient(to right, transparent ${start}%, ` +
                        `var(--skip-zone) ${start}%, var(--skip-zone) ${end}%, transparent ${end}%)`;
                })
                : [];
            this.timeline.style.setProperty('--timeline-segments', gradients.join(', ') || 'none');
        }
    }

    function normalizeSegments(value, durationMs) {
        const duration = Number(durationMs) || 0;
        const unique = new Map();
        if (!Array.isArray(value)) return [];
        for (const candidate of value) {
            const kind = String(candidate?.kind || '').toLowerCase();
            const rawStartMs = Number(candidate?.startMs);
            const rawEndMs = Number(candidate?.endMs);
            if (!Number.isFinite(rawStartMs) || !Number.isFinite(rawEndMs)) continue;
            const startMs = Math.max(0, Math.min(duration > 0 ? duration : rawStartMs, rawStartMs));
            const endMs = Math.max(0, Math.min(duration > 0 ? duration : rawEndMs, rawEndMs));
            if (endMs <= startMs) continue;
            const key = `${kind}:${startMs}:${endMs}`;
            unique.set(key, { kind, startMs, endMs, key });
        }
        return Array.from(unique.values()).sort((left, right) =>
            left.startMs - right.startMs || left.endMs - right.endMs || left.kind.localeCompare(right.kind));
    }

    function playbackBindingKey(payload, segments) {
        const video = payload?.video || {};
        return [
            video.animeId,
            video.id,
            video.episode,
            video.dubbing,
            video.player,
            segments.map((segment) => segment.key).join(';'),
        ].join('|');
    }

    function hasUsefulSkip(segment, positionMs) {
        const startMs = segment.activeStartMs ?? segment.startMs;
        const endMs = segment.targetEndMs ?? segment.endMs;
        return positionMs >= startMs && positionMs < endMs && endMs - positionMs > MIN_REMAINING_MS;
    }

    function connectedCluster(segments, seed) {
        const sameKind = segments.filter((segment) => segment.kind === seed.kind);
        let startMs = seed.startMs;
        let endMs = seed.endMs;
        let changed = true;
        while (changed) {
            changed = false;
            for (const segment of sameKind) {
                const connected = segment.startMs <= endMs + CLUSTER_TOLERANCE_MS &&
                    segment.endMs + CLUSTER_TOLERANCE_MS >= startMs;
                if (!connected) continue;
                const expandedStart = Math.min(startMs, segment.startMs);
                const expandedEnd = Math.max(endMs, segment.endMs);
                if (expandedStart !== startMs || expandedEnd !== endMs) changed = true;
                startMs = expandedStart;
                endMs = expandedEnd;
            }
        }
        return sameKind.filter((segment) =>
            segment.startMs <= endMs + CLUSTER_TOLERANCE_MS &&
            segment.endMs + CLUSTER_TOLERANCE_MS >= startMs);
    }

    global.YummyCastSkipController = YummyCastSkipController;
})(window);
