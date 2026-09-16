// Runs against the production document-start script in an isolated, offline browser.
window.captureHarness = { reports: [], sockets: [], timers: [], sends: 0, fetches: 0 };
window.YummyResolverBridge = { captureSession: raw => captureHarness.reports.push(JSON.parse(raw)) };
window.setInterval = fn => captureHarness.timers.push(fn);
window.clearInterval = () => {};
window.WebSocket = class FakeSocket {
    static OPEN = 1;
    constructor(url, protocols) { this.url = url; this.protocols = protocols; this.listeners = {}; captureHarness.sockets.push(this); }
    addEventListener(type, fn) { (this.listeners[type] ||= []).push(fn); }
    send(body) { this.sent = body; return 'sent'; }
    emit(data) { (this.listeners.message || []).forEach(fn => fn({ data: JSON.stringify(data) })); }
};
window.XMLHttpRequest = class FakeXHR {
    constructor() { this.listeners = []; }
    addEventListener(type, fn) { if (type === 'load') this.listeners.push(fn); }
    send() { captureHarness.sends++; this.listeners.forEach(fn => fn.call(this)); return 'xhr-sent'; }
};
window.fetch = () => {
    captureHarness.fetches++;
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
    const promise = fetch('https://alloha.test/bnsi/movies/123');
    check(promise === captureHarness.fetchPromise, 'fetch promise identity');
    const fetchResponse = await promise;
    await new Promise(resolve => setTimeout(resolve, 20));
    check(captureHarness.reports.some(value => value.expiresAt === 9876543210123), 'server expiry capture');
    check(captureHarness.reports.some(value => value.pnr === 'fetch-route' && value.pnk === 'fetch-key'), 'fetch advertised session capture');
    check(!fetchResponse.bodyUsed, 'capture must only consume a cloned fetch body');
    check((await fetchResponse.json()).pnk === 'fetch-key', 'original fetch response remains readable');
    check(captureHarness.fetches === 1, 'capture must not repeat HTTP requests');
    const xhr = new XMLHttpRequest();
    xhr.responseURL = 'https://alloha.test/bnsi/movies/123';
    xhr.responseType = 'json'; xhr.response = { time: 9876543210999, pnr: 'xhr-route', pnk: 'xhr-key' };
    check(xhr.send() === 'xhr-sent' && captureHarness.sends === 1, 'XHR transparency');
    check(captureHarness.reports.at(-1).expiresAt === 9876543210999, 'JSON XHR expiry');
    check(captureHarness.reports.at(-1).pnr === 'xhr-route' && captureHarness.reports.at(-1).pnk === 'xhr-key', 'XHR advertised session capture');
    check(xhr.response.pnr === 'xhr-route' && xhr.response.pnk === 'xhr-key', 'XHR body unchanged');
    const textXhr = new XMLHttpRequest();
    textXhr.responseURL = 'https://alloha.test/bnsi/trailers/123';
    textXhr.responseType = ''; textXhr.responseText = JSON.stringify({ pnr: 'text-route', pnk: 'text-key' });
    const originalText = textXhr.responseText;
    check(textXhr.send() === 'xhr-sent' && captureHarness.sends === 2, 'text XHR transparency');
    check(captureHarness.reports.at(-1).pnr === 'text-route' && captureHarness.reports.at(-1).pnk === 'text-key', 'text XHR advertised session capture without time');
    check(textXhr.responseText === originalText, 'text XHR body unchanged');
    check(captureHarness.reports.some(value => value.startTemplate === template), 'session template capture');
};
