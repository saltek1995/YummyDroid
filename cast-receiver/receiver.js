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

let timelineSeeking = false;
let controlsVisibleUntil = 0;
let controlsVisibilityTimer = null;
let activeSenderId;

const CONTROL_VISIBILITY_MS = 5_000;
const CONTROL_NAMESPACE = 'urn:x-cast:me.yummydroid.control';
const NAVIGATION_KEYS = new Set(['ArrowLeft', 'ArrowRight', 'ArrowUp', 'ArrowDown']);

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

function updateInterface() {
    const state = playerData.state || cast.framework.ui.State.LAUNCHING;
    const payload = playbackPayload();
    const isIdle = state === cast.framework.ui.State.LAUNCHING ||
        (state === cast.framework.ui.State.IDLE && !playerData.media);
    const isLoading = state === cast.framework.ui.State.LOADING ||
        state === cast.framework.ui.State.BUFFERING;
    const isPlaying = state === cast.framework.ui.State.PLAYING;
    const showControls = !isIdle && (
        Boolean(playerData.displayStatus) ||
        !isPlaying ||
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
    source.hidden = source.textContent.length === 0;

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

    if (document.activeElement?.disabled) playPauseButton.focus();
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
            else if (!captionsButton.disabled) captionsButton.focus();
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
            if (!captionsButton.disabled) captionsButton.focus();
            return true;
        }
    }

    if (active === captionsButton) {
        if (key === 'ArrowUp') {
            if (!timeline.disabled) timeline.focus();
            else focusPlayPause();
            return true;
        }
        if (key === 'ArrowLeft' || key === 'ArrowRight') {
            focusPlayPause();
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

document.addEventListener('keydown', (event) => {
    const interactiveTarget = event.target?.tagName === 'BUTTON' || event.target?.tagName === 'INPUT';
    if (NAVIGATION_KEYS.has(event.key)) {
        requestControls();
        if (moveFocus(event.key)) event.preventDefault();
        return;
    }
    if (interactiveTarget && (event.key === 'Enter' || event.key === ' ')) {
        requestControls();
        return;
    }
    if (event.key === 'Enter' || event.key === ' ') {
        requestControls();
        focusPlayPause();
        event.preventDefault();
    } else if (event.key === 'MediaPlayPause') {
        event.preventDefault();
        togglePlayback();
    } else if (event.key === 'MediaPlay') {
        playerManager.play();
    } else if (event.key === 'MediaPause') {
        playerManager.pause();
    }
});

playerDataBinder.addEventListener(
    cast.framework.ui.PlayerDataEventType.ANY_CHANGE,
    updateInterface,
);

playerManager.addEventListener(
    cast.framework.events.EventType.REQUEST_LOAD,
    (event) => {
        activeSenderId = event.senderId;
    },
);

context.addEventListener(
    cast.framework.system.EventType.SENDER_CONNECTED,
    (event) => {
        if (!activeSenderId) activeSenderId = event.senderId;
    },
);

context.addEventListener(
    cast.framework.system.EventType.SENDER_DISCONNECTED,
    (event) => {
        if (activeSenderId === event.senderId) activeSenderId = undefined;
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
receiverOptions.versionCode = 3;

context.start(receiverOptions);
updateInterface();
