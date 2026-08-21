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

const CONTROL_VISIBILITY_MS = 5_000;
const CONTROL_NAMESPACE = 'urn:x-cast:me.yummydroid.control';
const NAVIGATION_KEYS = new Set(['ArrowLeft', 'ArrowRight', 'ArrowUp', 'ArrowDown']);
const SELECTION_TYPES = ['voice', 'source', 'quality'];

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
    return playerData.media?.customData?.yummydroid || null;
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

function subtitleState() {
    const textTracksManager = playerManager.getTextTracksManager();
    const tracks = textTracksManager.getTracks();
    return {
        available: tracks.length > 0,
        active: textTracksManager.getActiveIds().length > 0,
    };
}

function updateTimeline(current, total) {
    if (timelineSeeking) return;
    const progress = total > 0 ? Math.round((current / total) * 1000) : 0;
    const clampedProgress = Math.max(0, Math.min(1000, progress));
    timeline.value = String(clampedProgress);
    timeline.disabled = total <= 0;
    timeline.style.setProperty('--timeline-progress', `${clampedProgress / 10}%`);
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
    const isIdle = state === cast.framework.ui.State.LAUNCHING ||
        (state === cast.framework.ui.State.IDLE && !playerData.media);
    const isLoading = state === cast.framework.ui.State.LOADING ||
        state === cast.framework.ui.State.BUFFERING || selectionPending;
    const isPlaying = state === cast.framework.ui.State.PLAYING;
    const showControls = !isIdle && (
        Boolean(playerData.displayStatus) ||
        !isPlaying ||
        !selectionMenu.hidden ||
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

    const current = Number(playerData.currentTime) || 0;
    const total = Number(playerData.duration) || 0;
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

    const playLabel = isPlaying ? 'Пауза' : 'Воспроизвести';
    playPauseButton.setAttribute('aria-label', playLabel);

    const active = document.activeElement;
    if (active?.disabled || active?.hidden) playPauseButton.focus();
}

function requestControls() {
    controlsVisibleUntil = Date.now() + CONTROL_VISIBILITY_MS;
    if (controlsVisibilityTimer !== null) clearTimeout(controlsVisibilityTimer);
    controlsVisibilityTimer = setTimeout(() => {
        controlsVisibilityTimer = null;
        updateInterface();
    }, CONTROL_VISIBILITY_MS);
    updateInterface();
}

function availableCenterButtons() {
    return [previousButton, playPauseButton, nextButton]
        .filter((button) => !button.hidden && !button.disabled);
}

function availableBottomControls() {
    const selectors = SELECTION_TYPES
        .map((type) => selectionControls[type].button)
        .filter((button) => !button.hidden && !button.disabled);
    if (!captionsButton.disabled) selectors.push(captionsButton);
    return selectors;
}

function focusPlayPause() {
    playPauseButton.focus();
}

function moveFocus(key) {
    const active = document.activeElement;
    const centerButtons = availableCenterButtons();
    const centerIndex = centerButtons.indexOf(active);

    if (centerIndex >= 0) {
        if (key === 'ArrowLeft' || key === 'ArrowRight') {
            const offset = key === 'ArrowLeft' ? -1 : 1;
            const targetIndex = Math.max(0, Math.min(centerButtons.length - 1, centerIndex + offset));
            centerButtons[targetIndex].focus();
            return true;
        }
        if (key === 'ArrowDown') {
            if (!timeline.disabled) timeline.focus();
            else availableBottomControls()[0]?.focus();
            return true;
        }
        return key === 'ArrowUp';
    }

    if (active === timeline) {
        if (key === 'ArrowLeft' || key === 'ArrowRight') {
            seekBy(key === 'ArrowLeft' ? -10 : 10);
            return true;
        }
        if (key === 'ArrowUp') {
            focusPlayPause();
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
            else focusPlayPause();
            return true;
        }
        return key === 'ArrowDown';
    }

    focusPlayPause();
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
    if (!activeSenderId || !availability[direction]) return;
    context.sendCustomMessage(CONTROL_NAMESPACE, activeSenderId, {
        type: 'episode-navigation',
        direction,
    });
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
    if (key === 'ArrowUp' || key === 'ArrowDown') {
        const offset = key === 'ArrowUp' ? -1 : 1;
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

function consumeKeyEvent(event) {
    event.preventDefault();
    event.stopPropagation();
    event.stopImmediatePropagation();
}

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
    if (NAVIGATION_KEYS.has(event.key)) {
        consumeKeyEvent(event);
        requestControls();
        if (openSelectionType) moveSelectionMenuFocus(event.key);
        else moveFocus(event.key);
        return;
    }
    if (openSelectionType && (event.key === 'Escape' || event.key === 'Backspace')) {
        consumeKeyEvent(event);
        closeSelectionMenu();
        return;
    }
    if (event.key === 'Enter' || event.key === ' ') {
        consumeKeyEvent(event);
        requestControls();
        const active = document.activeElement;
        if (active?.tagName === 'BUTTON') active.click();
        else focusPlayPause();
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
    if (
        NAVIGATION_KEYS.has(event.key) ||
        event.key === 'Enter' ||
        event.key === ' ' ||
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
        requestSelectionState();
    },
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
        requestSelectionState();
    },
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
receiverOptions.supportedCommands =
    cast.framework.messages.Command.ALL_BASIC_MEDIA |
    cast.framework.messages.Command.STREAM_TRANSFER;
receiverOptions.versionCode = 4;

context.start(receiverOptions);
updateInterface();
