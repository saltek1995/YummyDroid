const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const { resolve } = require('node:path');
const { test } = require('node:test');
const vm = require('node:vm');

// Exercise the shipped receiver, including event wiring, without a Cast device.
function receiverHarness() {
    const events = new Map();
    const timers = new Map();
    const sent = [];
    const seeks = [];
    const elements = new Map();
    let timerId = 0;
    let now = 1_000;
    const document = { activeElement: null };
    function element(id, tagName = 'BUTTON') {
        const classes = new Set();
        const callbacks = new Map();
        const result = {
            id, tagName, hidden: ['selection-menu', 'skip-controls'].includes(id), disabled: false,
            textContent: '', dataset: {}, children: [], attributes: {},
            buffered: { length: 0 },
            style: { setProperty() {} },
            classList: {
                toggle(key, enabled) { enabled ? classes.add(key) : classes.delete(key); },
                contains(key) { return classes.has(key); },
            },
            setAttribute(key, value) { this.attributes[key] = value; },
            addEventListener(key, callback) { callbacks.set(key, callback); },
            focus() { if (!this.disabled && !this.hidden) document.activeElement = this; },
            blur() { document.activeElement = document.body; },
            click() { if (!this.disabled && !this.hidden) callbacks.get('click')?.(); },
            appendChild(child) { this.children.push(child); },
            replaceChildren() {
                if (this.children.includes(document.activeElement)) document.activeElement = document.body;
                this.children = [];
            },
            querySelectorAll() { return this.children; },
            scrollIntoView() {},
        };
        return result;
    }
    document.body = element('body', 'BODY');
    document.activeElement = document.body;
    document.getElementById = (id) => {
        if (!elements.has(id)) elements.set(id, element(id));
        return elements.get(id);
    };
    document.createElement = (tag) => element('', tag.toUpperCase());
    const register = (kind) => (key, callback) => events.set(`${kind}:${key}`, callback);
    const playerData = { state: 'LAUNCHING', media: null, currentTime: 0, duration: 0 };
    const playerManager = {
        getTextTracksManager: () => ({ getTracks: () => [], getActiveIds: () => [] }),
        setSupportedMediaCommands() {},
        addEventListener: register('player'),
        setMessageInterceptor: register('interceptor'),
        seek: (position) => seeks.push(position),
        play() {}, pause() {},
    };
    const context = {
        getPlayerManager: () => playerManager,
        addCustomMessageListener: register('message'),
        addEventListener: register('system'),
        sendCustomMessage: (...args) => sent.push(args),
        getSenders: () => [], start() {}, stop() {},
    };
    const constants = new Proxy({}, { get: (_, name) => name });
    const sandbox = {
        document, console,
        Date: class extends Date { static now() { return now; } },
        setTimeout: (callback) => { const id = ++timerId; timers.set(id, callback); return id; },
        clearTimeout: (id) => timers.delete(id),
        setInterval() {}, requestAnimationFrame: (callback) => callback(),
        addEventListener: register('window'),
        cast: { framework: {
            CastReceiverContext: { getInstance: () => context },
            CastReceiverOptions: class {}, PlaybackConfig: class {},
            ui: {
                State: constants, PlayerDataEventType: constants,
                PlayerData: function () { return playerData; },
                PlayerDataBinder: class { addEventListener(...args) { register('data')(...args); } },
            },
            events: { EventType: constants, EndedReason: constants },
            system: { EventType: constants, MessageType: constants },
            messages: { MessageType: constants, Command: { ALL_BASIC_MEDIA: 1, STREAM_TRANSFER: 2 } },
        } },
    };
    sandbox.window = sandbox;
    const runtime = vm.createContext(sandbox);
    const directory = resolve(__dirname, '../../../../cast-receiver');
    vm.runInContext(readFileSync(resolve(directory, 'receiver.js'), 'utf8'), runtime);
    return {
        playerData, document, sent, seeks, elements,
        run: (source) => vm.runInContext(source, runtime),
        emit: (kind, event, payload = {}) => events.get(`${kind}:${event}`)?.(payload),
        advance: (milliseconds) => { now += milliseconds; },
    };
}

const selectionMessage = {
    type: 'selection-state',
    source: { options: [{ key: 'a', label: 'A' }, { key: 'b', label: 'B' }], selectedKey: 'a' },
};

function readyReceiver() {
    const receiver = receiverHarness();
    Object.assign(receiver.playerData, {
        state: 'PLAYING', duration: 100, currentTime: 10,
        media: { customData: { yummydroid: { animeTitle: 'Anime', hasNextEpisode: true } } },
    });
    receiver.emit('player', 'REQUEST_LOAD', { senderId: 'sender' });
    receiver.emit('message', 'urn:x-cast:me.yummydroid.control', { senderId: 'sender', data: selectionMessage });
    return receiver;
}

test('receiver starts idle and ready playback enables controls', () => {
    const idle = receiverHarness();
    assert.equal(idle.elements.get('receiver').classList.contains('receiver--idle'), true);
    const ready = readyReceiver();
    assert.equal(ready.elements.get('play-pause').disabled, false);
    assert.equal(ready.elements.get('next').hidden, false);
});

test('Back dismissal does not refocus invisible playback controls', () => {
    const receiver = readyReceiver();
    receiver.run('hideControls()');
    assert.equal(receiver.elements.get('receiver').classList.contains('receiver--controls-visible'), false);
    assert.equal(receiver.document.activeElement, receiver.document.body);
});

test('failed load exits the buffering spinner while source selection stays available', () => {
    const receiver = readyReceiver();
    receiver.playerData.state = 'BUFFERING';
    receiver.emit('player', 'ERROR');
    assert.equal(receiver.elements.get('receiver').classList.contains('receiver--loading'), false);
    assert.equal(receiver.elements.get('play-pause').disabled, true);
    assert.equal(receiver.elements.get('select-source').disabled, false);
});

test('disconnect clears stale selection controls and pending commands', () => {
    const receiver = readyReceiver();
    receiver.run("requestPlaybackSelection('source', 'b')");
    receiver.emit('system', 'SENDER_DISCONNECTED', { senderId: 'sender' });
    assert.equal(receiver.elements.get('select-source').hidden, true);
    assert.equal(receiver.elements.get('receiver').classList.contains('receiver--loading'), false);
});

test('state refresh preserves the focused selection row', () => {
    const receiver = readyReceiver();
    receiver.run("openSelectionMenu('source')");
    receiver.elements.get('selection-options').children[1].focus();
    receiver.emit('message', 'urn:x-cast:me.yummydroid.control', { senderId: 'sender', data: selectionMessage });
    assert.equal(receiver.document.activeElement.dataset.selectionKey, 'b');
});

test('stale sender updates cannot take ownership from the sender that loaded playback', () => {
    const receiver = readyReceiver();
    receiver.emit('message', 'urn:x-cast:me.yummydroid.control', { senderId: 'old-sender', data: selectionMessage });
    receiver.run("requestPlaybackSelection('source', 'b')");
    assert.equal(receiver.sent.at(-1)[1], 'sender');
});

test('episode navigation retains the wire command and seek is clamped', () => {
    const receiver = readyReceiver();
    assert.equal(receiver.run("requestEpisodeChange('next')"), true);
    const command = receiver.sent.at(-1);
    assert.equal(command[1], 'sender');
    assert.equal(command[2].type, 'episode-navigation');
    assert.equal(command[2].direction, 'next');
    receiver.run('seekBy(500)');
    receiver.run('seekBy(-500)');
    assert.deepEqual(receiver.seeks, [100, 0]);
});

test('D-pad follows center, timeline and selection rows with stable boundaries', () => {
    const receiver = readyReceiver();
    const focus = () => receiver.document.activeElement.id;
    receiver.elements.get('play-pause').focus();
    receiver.run("moveFocus('ArrowRight')");
    assert.equal(focus(), 'next');
    receiver.run("moveFocus('ArrowRight')");
    assert.equal(focus(), 'next');
    receiver.run("moveFocus('ArrowDown')");
    assert.equal(focus(), 'timeline');
    receiver.run("moveFocus('ArrowRight')");
    assert.deepEqual(receiver.seeks, [20]);
    receiver.run("moveFocus('ArrowDown')");
    assert.equal(focus(), 'select-source');
    receiver.run("moveFocus('ArrowUp'); moveFocus('ArrowUp')");
    assert.equal(focus(), 'play-pause');
});

test('skip row joins navigation and cancellation stops automatic skipping', () => {
    const receiver = readyReceiver();
    receiver.playerData.media.customData.yummydroid.video = {
        id: 1, animeId: 10, episode: '1', skipSegments: [{ kind: 'opening', startMs: 0, endMs: 60_000 }],
    };
    receiver.run('updateInterface()');
    assert.equal(receiver.document.activeElement.id, 'skip-segment');
    receiver.run("moveFocus('ArrowRight')");
    assert.equal(receiver.document.activeElement.id, 'watch-segment');
    receiver.run("moveFocus('ArrowUp')");
    assert.equal(receiver.document.activeElement.id, 'play-pause');
    receiver.run('skipController.cancelAutoCountdown()');
    receiver.advance(9_000);
    receiver.run('skipController.poll()');
    assert.deepEqual(receiver.seeks, []);
    receiver.run('skipController.skipActivePrompt()');
    assert.deepEqual(receiver.seeks, [60]);
});

test('new media load releases interrupted timeline dragging', () => {
    const receiver = readyReceiver();
    receiver.run('timelineSeeking = true');
    receiver.playerData.currentTime = 40;
    receiver.emit('player', 'REQUEST_LOAD', { senderId: 'sender' });
    assert.equal(receiver.elements.get('timeline').value, '400');
});

test('invalid messages cannot replace the active sender', () => {
    const receiver = readyReceiver();
    receiver.emit('message', 'urn:x-cast:me.yummydroid.control', { senderId: 'old-sender', data: '{invalid' });
    receiver.run("requestPlaybackSelection('source', 'b')");
    assert.equal(receiver.sent.at(-1)[1], 'sender');
});

test('finished playback keeps next episode as the primary navigation target', () => {
    const receiver = readyReceiver();
    receiver.playerData.duration = 0;
    receiver.emit('player', 'MEDIA_FINISHED', { endedReason: 'END_OF_STREAM' });
    assert.equal(receiver.document.activeElement.id, 'next');
    receiver.elements.get('select-source').focus();
    receiver.run("moveFocus('ArrowUp')");
    assert.equal(receiver.document.activeElement.id, 'next');
});
