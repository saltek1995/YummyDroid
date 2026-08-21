'use strict';

const context = cast.framework.CastReceiverContext.getInstance();
const playerManager = context.getPlayerManager();
const playerData = new cast.framework.ui.PlayerData();
const playerDataBinder = new cast.framework.ui.PlayerDataBinder(playerData);
const receiver = document.getElementById('receiver');
const media = document.getElementById('media');
const title = document.getElementById('media-title');
const subtitle = document.getElementById('media-subtitle');
const source = document.getElementById('source');
const currentTime = document.getElementById('current-time');
const duration = document.getElementById('duration');
const timeline = document.getElementById('timeline');
const skipControls = document.getElementById('skip-controls');
const skipButton = document.getElementById('skip-segment');
const watchButton = document.getElementById('watch-segment');
const previousButton = document.getElementById('previous');
const nextButton = document.getElementById('next');
const captionsButton = document.getElementById('captions');
const playPauseButton = document.getElementById('play-pause');
const selectionMenu = document.getElementById('selection-menu');
const selectionMenuTitle = document.getElementById('selection-menu-title');
const selectionOptions = document.getElementById('selection-options');

const selectionControls = {
    voice: {
        button: document.getElementById('select-voice'),
        caption: document.getElementById('voice-caption'),
        value: document.getElementById('voice-value'),
    },
    source: {
        button: document.getElementById('select-source'),
        caption: document.getElementById('source-caption'),
        value: document.getElementById('source-value'),
    },
    quality: {
        button: document.getElementById('select-quality'),
        caption: document.getElementById('quality-caption'),
        value: document.getElementById('quality-value'),
    },
};

const selectionState = {
    voice: { title: 'Озвучка', options: [], selectedKey: null },
    source: { title: 'Источник', options: [], selectedKey: null },
    quality: { title: 'Качество', options: [], selectedKey: null },
};

let timelineSeeking = false;
let controlsVisibleUntil = 0;
let controlsVisibilityTimer = null;
let activeSenderId;
let selectionStateSenderId;
let openSelectionType;
let selectionPending = false;
let selectionPendingTimer = null;
let pendingSelection = null;
let episodeChangePending = false;
let episodeChangePendingTimer = null;
let autoAdvanceRequested = false;
let loadFailed = false;
let playbackEnded = false;
let lastPlaybackPayload = null;
let remoteNavigationActive = false;
let receiverStopping = false;
let receiverStopNotified = false;
let controlsDismissed = false;
let skipController;

const CONTROL_VISIBILITY_MS = 5_000;
const CONTROL_NAMESPACE = 'urn:x-cast:me.yummydroid.control';
const RECEIVER_SUPPORTED_COMMANDS =
    cast.framework.messages.Command.ALL_BASIC_MEDIA |
    cast.framework.messages.Command.STREAM_TRANSFER;
const NAVIGATION_KEYS = new Set(['ArrowLeft', 'ArrowRight', 'ArrowUp', 'ArrowDown']);
const SELECTION_TYPES = ['voice', 'source', 'quality'];
const REMOTE_KEY_BY_CODE = new Map([
    [4, 'Back'],
    [8, 'Back'],
    [13, 'Enter'],
    [23, 'Enter'],
    [27, 'Back'],
    [37, 'ArrowLeft'],
    [38, 'ArrowUp'],
    [39, 'ArrowRight'],
    [40, 'ArrowDown'],
    [66, 'Enter'],
    [461, 'Back'],
    [10009, 'Back'],
]);

function formatTime(seconds) {
    const totalSeconds = Math.max(0, Math.floor(Number(seconds) || 0));
    const hours = Math.floor(totalSeconds / 3600);
    const minutes = Math.floor((totalSeconds % 3600) / 60);
    const remainder = totalSeconds % 60;
    if (hours > 0) {
        return `${hours}:${String(minutes).padStart(2, '0')}:${String(remainder).padStart(2, '0')}`;
    }
    return `${minutes}:${String(remainder).padStart(2, '0')}`;
}

function playbackPayload() {
    const payload = playerData.media?.customData?.yummydroid || null;
    if (payload) lastPlaybackPayload = payload;
    return payload || lastPlaybackPayload;
}

function mediaSubtitle(payload) {
    if (playerData.subtitle) return playerData.subtitle;
    if (playerData.metadata?.subtitle) return playerData.metadata.subtitle;
    const parts = [payload?.video?.dubbing, payload?.video?.episode]
        .map((value) => String(value || '').trim())
        .filter(Boolean);
    return parts.join(' · ');
}

function mediaSource(payload) {
    return String(payload?.video?.player || '').replace(/^Player\s+/i, '').trim();
}

function episodeAvailability(payload) {
    return {
        previous: payload?.hasPreviousEpisode === true,
        next: payload?.hasNextEpisode === true,
    };
}

function hasInteractiveSender() {
    return Boolean(activeSenderId) ||
        SELECTION_TYPES.some((type) => selectionState[type].options.length > 0);
}

function publishReceiverMediaCommands(broadcastStatus = true) {
    playerManager.setSupportedMediaCommands(RECEIVER_SUPPORTED_COMMANDS, broadcastStatus);
}

function activateRemoteNavigation() {
    publishReceiverMediaCommands();
    requestControls();
    requestAnimationFrame(() => {
        updateInterface();
        focusPrimaryControl();
    });
}

function playbackNeedsSelection(
    state = playerData.state || cast.framework.ui.State.LAUNCHING,
    hasMedia = Boolean(playerData.media),
    total = Number(playerData.duration) || 0,
    interactive = hasInteractiveSender(),
) {
    const notReady = state === cast.framework.ui.State.LAUNCHING ||
        state === cast.framework.ui.State.LOADING ||
        state === cast.framework.ui.State.BUFFERING ||
        state === cast.framework.ui.State.IDLE;
    return interactive && (loadFailed || playbackEnded || !hasMedia || (notReady && total <= 0));
}

function subtitleState() {
    const textTracksManager = playerManager.getTextTracksManager();
    const tracks = textTracksManager.getTracks();
    return {
        available: tracks.length > 0,
        active: textTracksManager.getActiveIds().length > 0,
    };
}

function bufferedPosition(total) {
    if (total <= 0 || media.buffered.length === 0) return 0;
    let bufferedEnd = 0;
    for (let index = 0; index < media.buffered.length; index += 1) {
        bufferedEnd = Math.max(bufferedEnd, media.buffered.end(index));
    }
    return Math.min(total, bufferedEnd);
}

function updateTimeline(current, total) {
    if (timelineSeeking) return;
    const progress = total > 0 ? Math.round((current / total) * 1000) : 0;
    const clampedProgress = Math.max(0, Math.min(1000, progress));
    const buffered = total > 0
        ? Math.max(clampedProgress, Math.round((bufferedPosition(total) / total) * 1000))
        : 0;
    const clampedBuffered = Math.max(0, Math.min(1000, buffered));
    timeline.value = String(clampedProgress);
    timeline.disabled = total <= 0;
    timeline.style.setProperty('--timeline-progress', `${clampedProgress / 10}%`);
    timeline.style.setProperty('--timeline-buffered', `${clampedBuffered / 10}%`);
}

function selectedOption(group) {
    return group.options.find((option) => option.key === group.selectedKey) || group.options[0] || null;
}

function updateSelectionControls() {
    let visibleSelectionCount = 0;
    for (const type of SELECTION_TYPES) {
        const group = selectionState[type];
        const control = selectionControls[type];
        const selected = selectedOption(group);
        control.caption.textContent = group.title;
        control.value.textContent = selected?.label || '';
        control.button.hidden = group.options.length === 0;
        control.button.disabled = group.options.length < 2;
        control.button.setAttribute('aria-label', `${group.title}: ${selected?.label || ''}`);
        if (!control.button.hidden) visibleSelectionCount += 1;
    }
    source.hidden = visibleSelectionCount > 0 || source.textContent.length === 0;
}

function updateInterface() {
    const state = playerData.state || cast.framework.ui.State.LAUNCHING;
    const payload = playbackPayload();
    const hasMedia = Boolean(playerData.media);
    const current = Number(playerData.currentTime) || 0;
    const total = Number(playerData.duration) || 0;
    skipController?.update(payload, current * 1_000, total * 1_000);
    const interactive = hasInteractiveSender();
    const needsSelection = playbackNeedsSelection(state, hasMedia, total, interactive);
    const isIdle = !interactive && (
        state === cast.framework.ui.State.LAUNCHING ||
        (state === cast.framework.ui.State.IDLE && !hasMedia)
    );
    const isLoading = state === cast.framework.ui.State.LOADING ||
        state === cast.framework.ui.State.BUFFERING ||
        selectionPending ||
        episodeChangePending ||
        (interactive && !hasMedia && !playbackEnded);
    const isPlaying = state === cast.framework.ui.State.PLAYING;
    const showControls = !isIdle && !controlsDismissed && (
        Boolean(playerData.displayStatus) ||
        !isPlaying ||
        !selectionMenu.hidden ||
        skipController?.visible ||
        Date.now() < controlsVisibleUntil
    );

    receiver.classList.toggle('receiver--idle', isIdle);
    receiver.classList.toggle('receiver--loading', isLoading);
    receiver.classList.toggle('receiver--playing', isPlaying);
    receiver.classList.toggle('receiver--controls-visible', showControls);

    title.textContent = playerData.title || payload?.animeTitle || 'YummyDroid';
    subtitle.textContent = mediaSubtitle(payload);
    subtitle.hidden = subtitle.textContent.length === 0;
    source.textContent = mediaSource(payload);
    updateSelectionControls();

    currentTime.textContent = formatTime(current);
    duration.textContent = formatTime(total);
    updateTimeline(current, total);

    const episodes = episodeAvailability(payload);
    previousButton.hidden = !episodes.previous;
    previousButton.disabled = !episodes.previous;
    nextButton.hidden = !episodes.next;
    nextButton.disabled = !episodes.next;

    const subtitles = subtitleState();
    captionsButton.disabled = !subtitles.available;
    captionsButton.setAttribute('aria-pressed', String(subtitles.active));

    playPauseButton.disabled = !hasMedia || needsSelection;
    const playLabel = isPlaying ? 'Пауза' : 'Воспроизвести';
    playPauseButton.setAttribute('aria-label', playLabel);

    const active = document.activeElement;
    if (
        active?.disabled ||
        active?.hidden ||
        ((isLoading || needsSelection) && availableCenterButtons().includes(active)) ||
        (active === document.body && interactive)
    ) {
        focusPrimaryControl();
    }
}

function requestControls(refresh = true) {
    controlsDismissed = false;
    controlsVisibleUntil = Date.now() + CONTROL_VISIBILITY_MS;
    if (controlsVisibilityTimer !== null) clearTimeout(controlsVisibilityTimer);
    controlsVisibilityTimer = setTimeout(() => {
        controlsVisibilityTimer = null;
        updateInterface();
    }, CONTROL_VISIBILITY_MS);
    if (refresh) updateInterface();
}

function hideControls() {
    controlsDismissed = true;
    controlsVisibleUntil = 0;
    if (controlsVisibilityTimer !== null) clearTimeout(controlsVisibilityTimer);
    controlsVisibilityTimer = null;
    skipController?.dismissForInterfaceHide();
    document.activeElement?.blur?.();
    updateInterface();
}

function controlsAreVisible() {
    return receiver.classList.contains('receiver--controls-visible');
}

function availableCenterButtons() {
    return [previousButton, playPauseButton, nextButton]
        .filter((button) => !button.hidden && !button.disabled);
}

function availableSkipButtons() {
    return skipController?.buttons || [];
}

function availableBottomControls() {
    const selectors = SELECTION_TYPES
        .map((type) => selectionControls[type].button)
        .filter((button) => !button.hidden && !button.disabled);
    if (!captionsButton.disabled) selectors.push(captionsButton);
    return selectors;
}

function preferredSelectionControl() {
    const sourceControl = selectionControls.source.button;
    if (!sourceControl.hidden && !sourceControl.disabled) return sourceControl;
    return availableBottomControls()[0];
}

function focusPrimaryControl() {
    const skipControlsAvailable = availableSkipButtons();
    if (skipControlsAvailable.length > 0) {
        skipControlsAvailable[0].focus();
        return;
    }
    if (playbackEnded) {
        const episodeControl = !nextButton.hidden && !nextButton.disabled
            ? nextButton
            : (!previousButton.hidden && !previousButton.disabled ? previousButton : null);
        if (episodeControl) {
            episodeControl.focus();
            return;
        }
    }
    const selectionControl = preferredSelectionControl();
    if (
        playPauseButton.disabled ||
        playbackNeedsSelection() ||
        receiver.classList.contains('receiver--loading')
    ) {
        selectionControl?.focus();
        return;
    }
    playPauseButton.focus();
}

function moveFocus(key) {
    const active = document.activeElement;
    const centerButtons = availableCenterButtons();
    const centerIndex = centerButtons.indexOf(active);
    const skipButtons = availableSkipButtons();
    const skipIndex = skipButtons.indexOf(active);

    if (centerIndex >= 0) {
        if (key === 'ArrowLeft' || key === 'ArrowRight') {
            const offset = key === 'ArrowLeft' ? -1 : 1;
            const targetIndex = Math.max(0, Math.min(centerButtons.length - 1, centerIndex + offset));
            centerButtons[targetIndex].focus();
            return true;
        }
        if (key === 'ArrowDown') {
            if (skipButtons.length > 0) skipButtons[0].focus();
            else if (!timeline.disabled) timeline.focus();
            else availableBottomControls()[0]?.focus();
            return true;
        }
        return key === 'ArrowUp';
    }

    if (skipIndex >= 0) {
        if (key === 'ArrowLeft' || key === 'ArrowRight') {
            const offset = key === 'ArrowLeft' ? -1 : 1;
            const targetIndex = Math.max(0, Math.min(skipButtons.length - 1, skipIndex + offset));
            skipButtons[targetIndex].focus();
            return true;
        }
        if (key === 'ArrowUp') {
            playPauseButton.focus();
            return true;
        }
        if (key === 'ArrowDown') {
            if (!timeline.disabled) timeline.focus();
            else availableBottomControls()[0]?.focus();
            return true;
        }
    }

    if (active === timeline) {
        if (key === 'ArrowLeft' || key === 'ArrowRight') {
            seekBy(key === 'ArrowLeft' ? -10 : 10);
            return true;
        }
        if (key === 'ArrowUp') {
            if (skipButtons.length > 0) skipButtons[0].focus();
            else focusPrimaryControl();
            return true;
        }
        if (key === 'ArrowDown') {
            availableBottomControls()[0]?.focus();
            return true;
        }
    }

    const bottomControls = availableBottomControls();
    const bottomIndex = bottomControls.indexOf(active);
    if (bottomIndex >= 0) {
        if (key === 'ArrowLeft' || key === 'ArrowRight') {
            const offset = key === 'ArrowLeft' ? -1 : 1;
            const targetIndex = Math.max(0, Math.min(bottomControls.length - 1, bottomIndex + offset));
            bottomControls[targetIndex].focus();
            return true;
        }
        if (key === 'ArrowUp') {
            if (!timeline.disabled) timeline.focus();
            else focusPrimaryControl();
            return true;
        }
        return key === 'ArrowDown';
    }

    focusPrimaryControl();
    return true;
}

function togglePlayback() {
    if (playerData.state === cast.framework.ui.State.PLAYING) {
        playerManager.pause();
    } else {
        playerManager.play();
    }
}

function requestEpisodeChange(direction) {
    const availability = episodeAvailability(playbackPayload());
    if (!activeSenderId || !availability[direction]) return false;
    setEpisodeChangePending();
    context.sendCustomMessage(CONTROL_NAMESPACE, activeSenderId, {
        type: 'episode-navigation',
        direction,
    });
    return true;
}

function requestAutomaticNextEpisode(event) {
    if (event.endedReason !== cast.framework.events.EndedReason.END_OF_STREAM) return;
    playbackEnded = true;
    loadFailed = false;
    const payload = playbackPayload();
    if (payload?.autoplayNextEpisode !== true || payload?.hasNextEpisode !== true) {
        requestControls();
        focusPrimaryControl();
        return;
    }
    if (autoAdvanceRequested) {
        requestControls();
        focusPrimaryControl();
        return;
    }
    if (requestEpisodeChange('next')) {
        autoAdvanceRequested = true;
        requestControls();
    }
    focusPrimaryControl();
}

function toggleCaptions() {
    const textTracksManager = playerManager.getTextTracksManager();
    const activeIds = textTracksManager.getActiveIds();
    if (activeIds.length > 0) {
        textTracksManager.setActiveByIds([]);
        return;
    }
    const firstTrack = textTracksManager.getTracks()[0];
    if (firstTrack) textTracksManager.setActiveByIds([firstTrack.trackId]);
}

function seekBy(seconds) {
    const total = Number(playerData.duration) || 0;
    const current = Number(playerData.currentTime) || 0;
    if (total <= 0) return;
    playerManager.seek(Math.max(0, Math.min(total, current + seconds)));
}

function normalizeSelectionGroup(value, fallbackTitle) {
    const options = Array.isArray(value?.options)
        ? value.options
            .map((option) => ({
                key: String(option?.key || '').trim(),
                label: String(option?.label || '').trim(),
            }))
            .filter((option) => option.key && option.label)
        : [];
    return {
        title: String(value?.title || fallbackTitle).trim() || fallbackTitle,
        options,
        selectedKey: value?.selectedKey == null ? null : String(value.selectedKey),
    };
}

function applySelectionState(message) {
    selectionState.voice = normalizeSelectionGroup(message.voice, 'Озвучка');
    selectionState.source = normalizeSelectionGroup(message.source, 'Источник');
    selectionState.quality = normalizeSelectionGroup(message.quality, 'Качество');
    if (
        selectionPending &&
        pendingSelection &&
        selectionState[pendingSelection.type]?.selectedKey === pendingSelection.key
    ) {
        clearSelectionPending();
    }
    if (openSelectionType) {
        const group = selectionState[openSelectionType];
        if (group.options.length < 2) closeSelectionMenu();
        else renderSelectionMenu(openSelectionType);
    }
    updateInterface();
    activateRemoteNavigation();
}

function setSelectionPending(type, key) {
    selectionPending = true;
    pendingSelection = { type, key };
    if (selectionPendingTimer !== null) clearTimeout(selectionPendingTimer);
    selectionPendingTimer = setTimeout(() => {
        selectionPendingTimer = null;
        selectionPending = false;
        pendingSelection = null;
        updateInterface();
    }, 20_000);
    updateInterface();
}

function clearSelectionPending() {
    selectionPending = false;
    pendingSelection = null;
    if (selectionPendingTimer !== null) clearTimeout(selectionPendingTimer);
    selectionPendingTimer = null;
}

function setEpisodeChangePending() {
    episodeChangePending = true;
    if (episodeChangePendingTimer !== null) clearTimeout(episodeChangePendingTimer);
    episodeChangePendingTimer = setTimeout(() => {
        episodeChangePendingTimer = null;
        episodeChangePending = false;
        updateInterface();
    }, 20_000);
    updateInterface();
}

function clearEpisodeChangePending() {
    episodeChangePending = false;
    if (episodeChangePendingTimer !== null) clearTimeout(episodeChangePendingTimer);
    episodeChangePendingTimer = null;
}

function renderSelectionMenu(type) {
    const group = selectionState[type];
    selectionMenuTitle.textContent = group.title;
    selectionOptions.replaceChildren();
    for (const option of group.options) {
        const button = document.createElement('button');
        button.className = 'receiver__selection-option';
        button.type = 'button';
        button.textContent = option.label;
        button.dataset.selectionKey = option.key;
        button.setAttribute('role', 'option');
        button.setAttribute('aria-selected', String(option.key === group.selectedKey));
        button.addEventListener('click', () => requestPlaybackSelection(type, option.key));
        selectionOptions.appendChild(button);
    }
}

function openSelectionMenu(type) {
    const group = selectionState[type];
    if (!group || group.options.length < 2) return;
    openSelectionType = type;
    renderSelectionMenu(type);
    selectionMenu.hidden = false;
    requestControls();
    const rows = Array.from(selectionOptions.querySelectorAll('button'));
    const selectedRow = rows.find((row) => row.dataset.selectionKey === group.selectedKey);
    (selectedRow || rows[0])?.focus();
}

function closeSelectionMenu() {
    const type = openSelectionType;
    openSelectionType = undefined;
    selectionMenu.hidden = true;
    selectionOptions.replaceChildren();
    if (type) selectionControls[type].button.focus();
    requestControls();
}

function moveSelectionMenuFocus(key) {
    const rows = Array.from(selectionOptions.querySelectorAll('button'));
    if (rows.length === 0) return true;
    const currentIndex = Math.max(0, rows.indexOf(document.activeElement));
    if (NAVIGATION_KEYS.has(key)) {
        const offset = key === 'ArrowUp' || key === 'ArrowLeft' ? -1 : 1;
        const targetIndex = Math.max(0, Math.min(rows.length - 1, currentIndex + offset));
        rows[targetIndex].focus();
        rows[targetIndex].scrollIntoView({ block: 'nearest' });
    }
    return true;
}

function requestPlaybackSelection(type, key) {
    if (!activeSenderId) return;
    const group = selectionState[type];
    if (!group || !group.options.some((option) => option.key === key)) return;
    if (group.selectedKey === key) {
        closeSelectionMenu();
        return;
    }
    context.sendCustomMessage(CONTROL_NAMESPACE, activeSenderId, {
        type: 'playback-selection',
        selectionType: type,
        key,
    });
    closeSelectionMenu();
    setSelectionPending(type, key);
}

function requestSelectionState() {
    if (!activeSenderId || selectionStateSenderId === activeSenderId) return;
    selectionStateSenderId = activeSenderId;
    context.sendCustomMessage(CONTROL_NAMESPACE, activeSenderId, {
        type: 'selection-state-request',
    });
}

function notifySenderReceiverStopping() {
    if (receiverStopNotified || context.getSenders().length === 0) return;
    try {
        context.sendCustomMessage(CONTROL_NAMESPACE, undefined, {
            type: 'receiver-stopping',
        });
        receiverStopNotified = true;
    } catch (_) {
        // The Cast session can disappear before the receiver shutdown event arrives.
    }
}

function dismissInterfaceForBack() {
    remoteNavigationActive = false;
    if (openSelectionType) {
        closeSelectionMenu();
        return true;
    }
    if (controlsAreVisible()) {
        hideControls();
        return true;
    }
    return false;
}

function stopReceiverApplication() {
    if (receiverStopping) return;
    receiverStopping = true;
    clearSelectionPending();
    clearEpisodeChangePending();
    notifySenderReceiverStopping();
    context.stop();
}

function normalizedRemoteKey(event) {
    const aliases = {
        Accept: 'Enter',
        Back: 'Back',
        Backspace: 'Back',
        BrowserBack: 'Back',
        Down: 'ArrowDown',
        Escape: 'Back',
        GoBack: 'Back',
        Left: 'ArrowLeft',
        OK: 'Enter',
        Right: 'ArrowRight',
        Select: 'Enter',
        Up: 'ArrowUp',
        ' ': 'Enter',
    };
    return aliases[event.key] || REMOTE_KEY_BY_CODE.get(Number(event.keyCode || event.which)) || event.key;
}

function activateFocusedControl() {
    requestControls();
    const active = document.activeElement;
    if (active?.tagName === 'BUTTON' && !active.disabled && !active.hidden) {
        active.click();
        return true;
    }
    focusPrimaryControl();
    return false;
}

function directionalSeekKey(request) {
    const relativeTime = Number(request.relativeTime);
    if (!Number.isFinite(relativeTime) || relativeTime === 0) return null;
    return relativeTime < 0 ? 'ArrowLeft' : 'ArrowRight';
}

function interceptPlayPause(request) {
    if (!remoteNavigationActive) return request;
    remoteNavigationActive = false;
    activateFocusedControl();
    return null;
}

function interceptSeek(request) {
    const key = directionalSeekKey(request);
    if (!key || (document.activeElement === timeline && !playbackNeedsSelection())) return request;
    requestControls();
    skipController.cancelAutoCountdown();
    remoteNavigationActive = true;
    if (openSelectionType) moveSelectionMenuFocus(key);
    else moveFocus(key);
    return null;
}

function interceptStop(request) {
    if (dismissInterfaceForBack()) return null;
    notifySenderReceiverStopping();
    return request;
}

function consumeKeyEvent(event) {
    event.preventDefault();
    event.stopPropagation();
    event.stopImmediatePropagation();
}

skipController = new window.YummyCastSkipController({
    controls: skipControls,
    skipButton,
    watchButton,
    timeline,
    seekTo: (positionMs) => playerManager.seek(positionMs / 1_000),
    onPromptShown: () => {
        requestControls(false);
        skipButton.focus();
    },
    onPromptDismissed: hideControls,
});

playPauseButton.addEventListener('click', () => {
    togglePlayback();
    requestControls();
});
previousButton.addEventListener('click', () => {
    requestEpisodeChange('previous');
    requestControls();
});
nextButton.addEventListener('click', () => {
    requestEpisodeChange('next');
    requestControls();
});
captionsButton.addEventListener('click', () => {
    toggleCaptions();
    requestControls();
});
for (const type of SELECTION_TYPES) {
    selectionControls[type].button.addEventListener('click', () => openSelectionMenu(type));
}

timeline.addEventListener('input', () => {
    skipController.cancelAutoCountdown();
    timelineSeeking = true;
    const total = Number(playerData.duration) || 0;
    const preview = total * Number(timeline.value) / 1000;
    currentTime.textContent = formatTime(preview);
    timeline.style.setProperty('--timeline-progress', `${Number(timeline.value) / 10}%`);
});

timeline.addEventListener('change', () => {
    const total = Number(playerData.duration) || 0;
    if (total > 0) playerManager.seek(total * Number(timeline.value) / 1000);
    timelineSeeking = false;
});

window.addEventListener('keydown', (event) => {
    const key = normalizedRemoteKey(event);
    if (NAVIGATION_KEYS.has(key)) {
        consumeKeyEvent(event);
        requestControls();
        skipController.cancelAutoCountdown();
        remoteNavigationActive = true;
        if (openSelectionType) moveSelectionMenuFocus(key);
        else moveFocus(key);
        return;
    }
    if (key === 'Back') {
        consumeKeyEvent(event);
        if (!dismissInterfaceForBack()) stopReceiverApplication();
        return;
    }
    if (key === 'Enter') {
        consumeKeyEvent(event);
        remoteNavigationActive = false;
        activateFocusedControl();
        return;
    }
    if (event.key === 'MediaPlayPause') {
        consumeKeyEvent(event);
        togglePlayback();
    } else if (event.key === 'MediaPlay') {
        playerManager.play();
    } else if (event.key === 'MediaPause') {
        playerManager.pause();
    }
}, true);

window.addEventListener('keyup', (event) => {
    const key = normalizedRemoteKey(event);
    if (
        NAVIGATION_KEYS.has(key) ||
        key === 'Enter' ||
        key === 'Back' ||
        event.key === 'MediaPlayPause'
    ) {
        consumeKeyEvent(event);
    }
}, true);

playerDataBinder.addEventListener(
    cast.framework.ui.PlayerDataEventType.ANY_CHANGE,
    updateInterface,
);

playerManager.addEventListener(
    cast.framework.events.EventType.REQUEST_LOAD,
    (event) => {
        activeSenderId = event.senderId;
        autoAdvanceRequested = false;
        playbackEnded = false;
        loadFailed = false;
        remoteNavigationActive = false;
        receiverStopNotified = false;
        clearEpisodeChangePending();
        activateRemoteNavigation();
        requestSelectionState();
    },
);

playerManager.addEventListener(
    cast.framework.events.EventType.ERROR,
    () => {
        loadFailed = true;
        playbackEnded = false;
        clearSelectionPending();
        clearEpisodeChangePending();
        activateRemoteNavigation();
    },
);

playerManager.addEventListener(
    cast.framework.events.EventType.PLAYING,
    () => {
        loadFailed = false;
        playbackEnded = false;
        clearSelectionPending();
        clearEpisodeChangePending();
    },
);

playerManager.addEventListener(
    cast.framework.events.EventType.MEDIA_FINISHED,
    requestAutomaticNextEpisode,
);

playerManager.addEventListener(
    cast.framework.events.EventType.REQUEST_STOP,
    stopReceiverApplication,
);

context.addCustomMessageListener(CONTROL_NAMESPACE, (event) => {
    activeSenderId = event.senderId || activeSenderId;
    const message = typeof event.data === 'string'
        ? (() => {
            try {
                return JSON.parse(event.data);
            } catch (_) {
                return null;
            }
        })()
        : event.data;
    if (message?.type === 'selection-state') {
        selectionStateSenderId = activeSenderId;
        applySelectionState(message);
    }
});

context.addEventListener(
    cast.framework.system.EventType.SENDER_CONNECTED,
    (event) => {
        if (!activeSenderId) activeSenderId = event.senderId;
        receiverStopNotified = false;
        activateRemoteNavigation();
        requestSelectionState();
    },
);

context.addEventListener(
    cast.framework.system.EventType.SHUTDOWN,
    notifySenderReceiverStopping,
);

window.addEventListener('pagehide', notifySenderReceiverStopping);

playerManager.setMessageInterceptor(
    cast.framework.messages.MessageType.PLAY,
    interceptPlayPause,
);
playerManager.setMessageInterceptor(
    cast.framework.messages.MessageType.PAUSE,
    interceptPlayPause,
);
playerManager.setMessageInterceptor(
    cast.framework.messages.MessageType.SEEK,
    interceptSeek,
);
playerManager.setMessageInterceptor(
    cast.framework.messages.MessageType.STOP,
    interceptStop,
);
context.addEventListener(
    cast.framework.system.EventType.SENDER_DISCONNECTED,
    (event) => {
        if (activeSenderId === event.senderId) {
            activeSenderId = undefined;
            selectionStateSenderId = undefined;
        }
    },
);

const receiverOptions = new cast.framework.CastReceiverOptions();
const playbackConfig = new cast.framework.PlaybackConfig();
playbackConfig.autoResumeDuration = 5;
receiverOptions.playbackConfig = playbackConfig;
receiverOptions.mediaElement = media;
receiverOptions.customNamespaces = {
    [CONTROL_NAMESPACE]: cast.framework.system.MessageType.JSON,
};
receiverOptions.supportedCommands = RECEIVER_SUPPORTED_COMMANDS;
receiverOptions.versionCode = 9;

context.start(receiverOptions);
publishReceiverMediaCommands(false);
updateInterface();
