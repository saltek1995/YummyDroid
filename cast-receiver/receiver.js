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

function queueAvailability() {
    const queueManager = playerManager.getQueueManager();
    if (!queueManager) return { previous: false, next: false };
    const items = queueManager.getItems();
    const index = queueManager.getCurrentItemIndex();
    return {
        previous: index > 0,
        next: index >= 0 && index < items.length - 1,
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
    const showControls = !isIdle && (Boolean(playerData.displayStatus) || !isPlaying);

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

    const queue = queueAvailability();
    previousButton.disabled = !queue.previous;
    nextButton.disabled = !queue.next;

    const subtitles = subtitleState();
    captionsButton.disabled = !subtitles.available;
    captionsButton.setAttribute('aria-pressed', String(subtitles.active));

    const playLabel = isPlaying ? 'Пауза' : 'Воспроизвести';
    playPauseButton.setAttribute('aria-label', playLabel);
}

function togglePlayback() {
    if (playerData.state === cast.framework.ui.State.PLAYING) {
        playerManager.pause();
    } else {
        playerManager.play();
    }
}

function jumpQueue(offset) {
    const request = new cast.framework.messages.QueueUpdateRequestData();
    request.jump = offset;
    playerManager.sendLocalMediaRequest(request);
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

playPauseButton.addEventListener('click', togglePlayback);
previousButton.addEventListener('click', () => jumpQueue(-1));
nextButton.addEventListener('click', () => jumpQueue(1));
captionsButton.addEventListener('click', toggleCaptions);

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
    if (interactiveTarget && (event.key === 'Enter' || event.key === ' ')) return;
    if (event.target === timeline && (event.key === 'ArrowLeft' || event.key === 'ArrowRight')) return;
    if (event.key === 'MediaPlayPause' || event.key === 'Enter' || event.key === ' ') {
        event.preventDefault();
        togglePlayback();
    } else if (event.key === 'MediaPlay') {
        playerManager.play();
    } else if (event.key === 'MediaPause') {
        playerManager.pause();
    } else if (event.key === 'ArrowLeft') {
        event.preventDefault();
        seekBy(-10);
    } else if (event.key === 'ArrowRight') {
        event.preventDefault();
        seekBy(10);
    }
});

playerDataBinder.addEventListener(
    cast.framework.ui.PlayerDataEventType.ANY_CHANGE,
    updateInterface,
);

const receiverOptions = new cast.framework.CastReceiverOptions();
const playbackConfig = new cast.framework.PlaybackConfig();
const uiConfig = new cast.framework.ui.UiConfig();
playbackConfig.autoResumeDuration = 5;
uiConfig.touchScreenOptimizedApp = true;
receiverOptions.playbackConfig = playbackConfig;
receiverOptions.mediaElement = media;
receiverOptions.uiConfig = uiConfig;
receiverOptions.supportedCommands =
    cast.framework.messages.Command.ALL_BASIC_MEDIA |
    cast.framework.messages.Command.QUEUE_PREV |
    cast.framework.messages.Command.QUEUE_NEXT |
    cast.framework.messages.Command.STREAM_TRANSFER;
receiverOptions.versionCode = 2;

context.start(receiverOptions);
updateInterface();
