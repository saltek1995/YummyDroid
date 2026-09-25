// Runs against the production document-start script in an isolated, offline browser.
// Local file URLs on Windows carry a drive prefix; normalize only this offline fixture.
const HarnessURL = window.URL;
window.URL = class extends HarnessURL {
    get pathname() { return super.pathname.replace(/^\/[A-Za-z]:/, ''); }
};
window.captureHarness = { reports: [], sockets: [], timers: [], sends: 0, fetches: 0 };
window.__yummyExpectedParentOrigin = location.origin;
navigator.sendBeacon = () => { captureHarness.beacons = (captureHarness.beacons || 0) + 1; return true; };
window.YummyResolverBridge = { captureSession: raw => captureHarness.reports.push(JSON.parse(raw)) };
window.setInterval = fn => captureHarness.timers.push(fn);
window.clearInterval = () => {};
window.WebSocket = class FakeSocket {
    static OPEN = 1;
    constructor(url, protocols) { this.url = url; this.protocols = protocols; this.listeners = {}; captureHarness.sockets.push(this); }
    addEventListener(type, fn) { (this.listeners[type] ||= []).push(fn); }
    send(body) { if (this.failSend) throw new Error('not open'); this.sent = body; return 'sent'; }
    emit(data) { (this.listeners.message || []).forEach(fn => fn({ data: JSON.stringify(data) })); }
};
window.XMLHttpRequest = class FakeXHR {
    constructor() { this.listeners = []; this.endListeners = []; this.status = 200; }
    addEventListener(type, fn) { if (type === 'load') this.listeners.push(fn); if (type === 'loadend') this.endListeners.push(fn); }
    open(method, url) { this.url = url; return undefined; }
    send() { if (this.failSend) throw Error('xhr failed'); captureHarness.sends++; this.listeners.forEach(fn => fn.call(this)); if (!this.deferEnd) this.endListeners.splice(0).forEach(fn => fn.call(this)); return 'xhr-sent'; }
};
window.fetch = () => {
    captureHarness.fetches++;
    if (captureHarness.rejectFetch) {
        captureHarness.rejectFetch = false;
        captureHarness.fetchPromise = Promise.reject(Error('offline failure'));
        return captureHarness.fetchPromise;
    }
    if (captureHarness.deferFetch) {
        captureHarness.deferFetch = false;
        captureHarness.fetchPromise = new Promise(resolve => { captureHarness.resolveFetch = resolve; });
        return captureHarness.fetchPromise;
    }
    captureHarness.fetchPromise = Promise.resolve(new Response(JSON.stringify({ time: 9876543210123, pnr: 'fetch-route', pnk: 'fetch-key' })));
    return captureHarness.fetchPromise;
};
window.runCaptureHarness = async () => {
    const check = (condition, message) => { if (!condition) throw new Error(message); };
    const url = 'wss://socket.test/stream?sid=one&v=2.1&t=10';
    const ws = new WebSocket(url, ['example']);
    check(WebSocket.OPEN === 1 && ws instanceof WebSocket, 'constructor compatibility');
    check(ws.protocols[0] === 'example', 'subprotocol preservation');
    const template = JSON.stringify({ type: 'playback_start', track_id: 'audio-7', resolution: '720' });
    check(ws.send(template) === 'sent' && ws.sent === template, 'send must be transparent');
    const reportsBeforeFailedSend = captureHarness.reports.length;
    ws.failSend = true;
    let sendThrew = false;
    try { ws.send(JSON.stringify({ type: 'init' })); } catch (_) { sendThrew = true; }
    check(sendThrew && captureHarness.reports.length === reportsBeforeFailedSend, 'failed send must not be captured as initialized');
    ws.failSend = false;
    ws.send(JSON.stringify({ type: 'init' }));
    check(captureHarness.reports.at(-1).startupEvent === 'init', 'successful init must be captured');
    ws.emit({ type: 'config_update', edge_hash: 'first-token' });
    const newer = new WebSocket('wss://socket.test/stream?sid=two&v=2.1&t=20');
    newer.send(template);
    const count = captureHarness.reports.length;
    ws.emit({ type: 'config_update', edge_hash: 'stale-token' });
    check(captureHarness.reports.length === count, 'old socket must not replace the active token');
    newer.emit({ type: 'config_update', edge_hash: 'new-token' });
    const unrelated = new WebSocket('wss://ads.test/socket');
    unrelated.emit({ type: 'config_update', edge_hash: 'unrelated-token' });
    check(captureHarness.reports.at(-1).token === 'new-token', 'unrelated sockets must be ignored');
    window.player = { reloadManifestQuery: { query: 'fallback', guard: 'guard', getStreamToken: () => 'dynamic' } };
    captureHarness.timers.forEach(fn => fn());
    check(captureHarness.reports.at(-1).token === 'dynamic', 'getter must be called, not cloned');
    const container = document.createElement('div');
    container.innerHTML = '<video></video><button class="allplay__control--overlaid" data-allplay="play">Play</button>' +
        '<button class="allplay__control--overlaid" data-allplay="play-large-ads">Ad</button>';
    document.body.append(container);
    let normalClicks = 0, adClicks = 0, firstClickObserved = false;
    document.addEventListener('click', () => { firstClickObserved = true; }, {capture:true});
    container.querySelector('[data-allplay="play"]').onclick = () => { check(firstClickObserved, 'provider first-click handler order'); normalClicks++; };
    container.querySelector('[data-allplay="play-large-ads"]').onclick = () => { adClicks++; };
    Object.assign(player, {ready:true,media:container.querySelector('video'),elements:{container}});
    captureHarness.timers.forEach(fn => fn());
    check(normalClicks === 0, 'must wait for HTTP listeners');
    window.storageAvailable = () => true;
    player.eventListeners = [{type:'play',element:document,callback:function() { return '/stat'; }}];
    captureHarness.timers.forEach(fn => fn());
    check(normalClicks === 0, 'unrelated stat listener must not enable startup');
    player.eventListeners[0].element = container;
    captureHarness.timers.forEach(fn => fn());
    captureHarness.timers.forEach(fn => fn());
    check(normalClicks === 1 && adClicks === 0, 'discovery starts only exact normal player control once');
    const promise = fetch(new URL('/bnsi/movies/123', location.href).href);
    check(promise === captureHarness.fetchPromise, 'fetch promise identity');
    const fetchResponse = await promise;
    await new Promise(resolve => setTimeout(resolve, 20));
    check(captureHarness.reports.some(value => value.expiresAt === 9876543210123), 'server expiry capture');
    check(captureHarness.reports.some(value => value.pnr === 'fetch-route' && value.pnk === 'fetch-key'), 'fetch advertised session capture');
    check(!fetchResponse.bodyUsed, 'capture must only consume a cloned fetch body');
    check((await fetchResponse.json()).pnk === 'fetch-key', 'original fetch response remains readable');
    check(captureHarness.fetches === 1, 'capture must not repeat HTTP requests');
    const xhr = new XMLHttpRequest();
    xhr.responseURL = new URL('/bnsi/movies/123', location.href).href;
    xhr.responseType = 'json'; xhr.response = { time: 9876543210999, pnr: 'xhr-route', pnk: 'xhr-key' };
    check(xhr.send() === 'xhr-sent' && captureHarness.sends === 1, 'XHR transparency');
    check(captureHarness.reports.at(-1).expiresAt === 9876543210999, 'JSON XHR expiry');
    check(captureHarness.reports.at(-1).pnr === 'xhr-route' && captureHarness.reports.at(-1).pnk === 'xhr-key', 'XHR advertised session capture');
    check(xhr.response.pnr === 'xhr-route' && xhr.response.pnk === 'xhr-key', 'XHR body unchanged');
    const textXhr = new XMLHttpRequest();
    textXhr.responseURL = new URL('/bnsi/trailers/123', location.href).href;
    textXhr.responseType = ''; textXhr.responseText = JSON.stringify({ pnr: 'text-route', pnk: 'text-key' });
    const originalText = textXhr.responseText;
    check(textXhr.send() === 'xhr-sent' && captureHarness.sends === 2, 'text XHR transparency');
    check(captureHarness.reports.at(-1).pnr === 'text-route' && captureHarness.reports.at(-1).pnk === 'text-key', 'text XHR advertised session capture without time');
    check(textXhr.responseText === originalText, 'text XHR body unchanged');
    check(captureHarness.reports.some(value => value.startTemplate === template), 'session template capture');
    const providerEvents = new URL('/events', location.href).href;
    const providerStat = new URL('/stat', location.href).href;
    const envelope = {token:'http-token', domain:'site.test', clientSessionId:'http-view', clientRequestId:'request',
        playerInitAt:1234, env:{pixelRatio:1}, events:[{event_type:'view_start', at:5678}]};
    const body = new URLSearchParams({token:'http-token', payload:JSON.stringify(envelope)});
    const eventPromise = fetch(providerEvents, {method:'POST', body});
    check(eventPromise === captureHarness.fetchPromise, 'telemetry promise identity');
    check(captureHarness.reports.at(-1).telemetry.envelope.clientSessionId === 'http-view', 'HTTP view preserved');
    const failed = new XMLHttpRequest(); failed.open('POST', providerStat); failed.failSend = true;
    const failedCount = captureHarness.reports.length;
    try { failed.send('id=file&token=http-token&domain=site.test&percent=97'); } catch (_) {}
    check(captureHarness.reports.length === failedCount, 'failed XHR cannot claim a sent milestone');
    const stat = new XMLHttpRequest(); stat.open('POST', providerStat);
    stat.send('id=file&token=http-token&domain=site.test&type=mgpo&info[wasm]=true&info[resolution][screenWidth]=1280');
    check(captureHarness.reports.at(-1).telemetry.statInfo.resolution.screenWidth === 1280, 'nested stat info');
    const beaconBody = new FormData(); beaconBody.append('id','file'); beaconBody.append('token','http-token');
    beaconBody.append('domain','site.test'); beaconBody.append('percent','10');
    check(navigator.sendBeacon(providerStat, beaconBody), 'beacon result');
    check(captureHarness.reports.at(-1).telemetry.percents[0] === 10, 'beacon progress');
    const beforeCrossOrigin = captureHarness.reports.length;
    fetch('https://unrelated.test/events', {method:'POST',body});
    check(captureHarness.reports.length === beforeCrossOrigin, 'cross-origin telemetry excluded');
    await Promise.resolve();
    captureHarness.rejectFetch = true;
    const failedFetchCount = captureHarness.fetches;
    await fetch(providerEvents, {method:'POST',body}).catch(() => {});
    check(captureHarness.fetches === failedFetchCount + 1, 'failed telemetry is never retried by capture');
    check(captureHarness.reports.at(-1).telemetry.deliveryFailed === true, 'failure retained as diagnostic only');
    captureHarness.deferFetch = true;
    const deferred = fetch(providerEvents, {method:'POST',body});
    const deferredXhr = new XMLHttpRequest(); deferredXhr.open('POST', providerStat); deferredXhr.deferEnd = true;
    deferredXhr.send('id=file&token=http-token&domain=site.test&percent=30');
    check(deferred === captureHarness.fetchPromise, 'pending fetch preserves promise identity');
    let handoff;
    YummyResolverBridge.handoffSession = raw => { handoff = JSON.parse(raw); };
    window.dispatchEvent(new MessageEvent('message', {source:window.parent,origin:location.origin,data:'__yummySessionHandoff'}));
    check(!handoff, 'handoff must wait for submitted HTTP request');
    captureHarness.resolveFetch(new Response('', {status:200}));
    await deferred;
    check(!handoff, 'handoff must also wait for XHR loadend');
    deferredXhr.endListeners.splice(0).forEach(fn => fn.call(deferredXhr));
    await Promise.resolve();
    check(handoff.telemetry.envelope.clientRequestId === 'request', 'final snapshot preserves IDs');
    const counts = [captureHarness.fetches,captureHarness.sends,captureHarness.beacons];
    await fetch(providerEvents, {method:'POST',body}); stat.send(body); navigator.sendBeacon(providerStat,beaconBody);
    check(JSON.stringify(counts) === JSON.stringify([captureHarness.fetches,captureHarness.sends,captureHarness.beacons]), 'retired provider sends suppressed');
    fetch('https://unrelated.test/events', {method:'POST',body});
    check(captureHarness.fetches === counts[0]+1, 'unrelated transport survives handoff');
};
