(function () {
    'use strict';

    var input = window.__yummyBootstrap || {};

    function bitLength(value) {
        return value === 0 ? 0 : Math.floor(Math.log2(value)) + 1;
    }

    function trailingZeros(value, zeroValue) {
        if (value === 0) return zeroValue;
        var result = 0;
        while ((value & 1) === 0) {
            result++;
            value = Math.floor(value / 2);
        }
        return result;
    }

    function regroup(value, groupForPosition, groupOrder) {
        var length = value.length;
        if (length <= 1) return value;
        var counts = {};
        var index;
        for (index = 0; index < length; index++) {
            var group = groupForPosition(index);
            counts[group] = (counts[group] || 0) + 1;
        }
        var offset = 0;
        var parts = {};
        groupOrder.forEach(function (group) {
            var count = counts[group] || 0;
            parts[group] = { value: value.slice(offset, offset + count), index: 0 };
            offset += count;
        });
        var output = '';
        for (index = 0; index < length; index++) {
            var part = parts[groupForPosition(index)];
            output += part.value.charAt(part.index++);
        }
        return output;
    }

    function zy(value) {
        var length = value.length;
        if (length <= 1) return value;
        var bits = Math.ceil(Math.log2(length));
        var order = [];
        for (var group = bits; group >= 0; group--) order.push(group);
        return regroup(value, function (index) { return bitLength(index); }, order);
    }

    function zZ(value) {
        var length = value.length;
        if (length <= 1) return value;
        var bits = Math.ceil(Math.log2(length));
        var order = [];
        for (var group = 0; group <= bits; group++) order.push(group);
        return regroup(value, function (index) { return trailingZeros(index, bits); }, order);
    }

    function isPrime(value) {
        if (value < 2) return false;
        for (var divisor = 2; divisor * divisor <= value; divisor++) {
            if (value % divisor === 0) return false;
        }
        return true;
    }

    function z9(value) {
        var length = value.length;
        if (length <= 1) return value;
        var prime = length + 1;
        while (!isPrime(prime)) prime++;
        var order = [];
        var seen = {};
        var position = 0;
        while (order.length < length) {
            position = (position + 2) % prime;
            if (position < length && !seen[position]) {
                seen[position] = true;
                order.push(position);
            }
        }
        var output = new Array(length);
        for (var index = 0; index < length; index++) output[order[index]] = value.charAt(index);
        return output.join('');
    }

    function borth(fingerprint, seed) {
        return fingerprint + '|' + z9(zZ(zy(String(seed))));
    }

    var algorithms = { bitLength: bitLength, regroup: regroup, zy: zy, zZ: zZ, z9: z9, borth: borth };
    if (input.testOnly === true) {
        window.__yummyBootstrapAlgorithms = algorithms;
        return;
    }

    var startedAt = performance.now();
    var pageEpochMs = Math.round(performance.timeOrigin || (Date.now() - performance.now()));
    var metadataAt = null;
    var settled = false;
    var timers = [];
    function later(fn, milliseconds) {
        var timer = setTimeout(fn, milliseconds);
        timers.push(timer);
        return timer;
    }
    function clearTimers() {
        timers.forEach(clearTimeout);
        timers = [];
    }
    function relativeTiming(name) {
        return Math.round(performance.now() - startedAt);
    }
    function report(method, value) {
        var bridge = window.YummyBootstrapBridge;
        if (bridge && typeof bridge[method] === 'function') bridge[method](JSON.stringify(value));
    }
    function fail(stage, status, error) {
        if (settled) return;
        settled = true;
        clearTimers();
        var payload = { nonce: input.captureNonce, stage: stage };
        if (status !== undefined) payload.status = status;
        if (error) {
            payload.error = String(error); payload.stack = error.stack || null;
            if (error.response) payload.response = error.response;
        }
        report('failed', payload);
    }

    function fingerprint() {
        var values = [];
        try { values.push((navigator.userAgent || '').replace(/\d+(?:[._]\d+)+/g, '')); } catch (_) { values.push(undefined); }
        try { values.push(Intl.DateTimeFormat().resolvedOptions().timeZone); } catch (_) { values.push(undefined); }
        try { values.push(String(Math.max(screen.width || 0, screen.height || 0)) + 'x' + String(Math.min(screen.width || 0, screen.height || 0))); } catch (_) { values.push(''); }
        try { values.push(navigator.language || navigator.userLanguage); } catch (_) { values.push(undefined); }
        try { values.push(navigator.deviceMemory); } catch (_) {}
        try { values.push(navigator.hardwareConcurrency); } catch (_) {}
        try {
            var canvas = document.createElement('canvas');
            var context = canvas.getContext('2d');
            context.textBaseline = 'top'; context.font = '16px sans-serif'; context.fillText('fp', 2, 2);
            values.push(canvas.toDataURL());
        } catch (_) {}
        try {
            if (!('WebGLRenderingContext' in window)) throw new Error('WebGL unavailable');
            var gl = null, glCanvas = document.createElement('canvas');
            ['webgl2', 'webgl', 'experimental-webgl'].some(function (name) {
                gl = glCanvas.getContext(name, { powerPreference: 'high-performance', failIfMajorPerformanceCaveat: true });
                return !!gl;
            });
            if (gl) {
                var extension = gl.getExtension('WEBGL_debug_renderer_info');
                if (extension) values.push(gl.getParameter(extension.UNMASKED_VENDOR_WEBGL) + '|' + gl.getParameter(extension.UNMASKED_RENDERER_WEBGL));
            }
        } catch (_) {}
        return audioLength().then(function (length) {
            if (length !== null) values.push(length);
            var source = values.join('||');
            var webCrypto = window.crypto;
            if (!webCrypto || !webCrypto.subtle) throw new Error('SHA-256 is unavailable');
            return webCrypto.subtle.digest('SHA-256', new TextEncoder().encode(source)).then(function (digest) {
                return Array.prototype.map.call(new Uint8Array(digest), function (byte) { return byte.toString(16).padStart(2, '0'); }).join('');
            });
        });
    }

    function audioLength() {
        return new Promise(function (resolve) {
            var done = false;
            var finish = function (value) { if (!done) { done = true; resolve(value); } };
            try {
                var AudioContext = window.OfflineAudioContext || window.webkitOfflineAudioContext;
                if (!AudioContext) return finish(null);
                var context = new AudioContext(1, 44100, 44100);
                var oscillator = context.createOscillator();
                oscillator.connect(context.destination); oscillator.start(0);
                context.startRendering().then(function (buffer) { finish(buffer.length); }, function () { finish(null); });
                later(function () { finish(null); }, 1500);
            } catch (_) { finish(null); }
        });
    }

    function av1Capability() {
        var config = { type: 'file', audio: { contentType: 'audio/mp4;codecs=mp4a.40.2', channels: '2', bitrate: 320000, samplerate: 48000 }, video: { contentType: 'video/mp4;codecs=av01.0.12M.08', height: 2160, width: 3840, framerate: 60, bitrate: 11000000 } };
        try {
            if (!navigator.mediaCapabilities || !navigator.mediaCapabilities.decodingInfo) throw new Error('unavailable');
            var capability = navigator.mediaCapabilities.decodingInfo(config);
            return new Promise(function (resolve) {
                var done = false;
                var finish = function (value) { if (!done) { done = true; resolve(value); } };
                capability.then(function (info) { finish(!!(info.supported && info.smooth)); }, function () { finish(false); });
                later(function () { finish(false); }, 3000);
            });
        } catch (_) {
            try { return Promise.resolve(document.createElement('video').canPlayType('video/mp4;codecs=av01.0.12M.08') === 'probably'); } catch (_) { return Promise.resolve(false); }
        }
    }

    function wasmProbe() {
        try { return Promise.resolve(typeof WebAssembly === 'object' && typeof WebAssembly.instantiate === 'function' && !!new WebAssembly.Module(new Uint8Array([0, 97, 115, 109, 1, 0, 0, 0]))); } catch (_) { return Promise.resolve(false); }
    }
    function serviceWorkerProbe() {
        if (!navigator.serviceWorker || !navigator.serviceWorker.getRegistration) return Promise.resolve(false);
        function register() {
            var url;
            try {
                url = URL.createObjectURL(new Blob(['self.addEventListener("install",function(){self.skipWaiting()});self.addEventListener("activate",function(){})'], { type: 'application/javascript' }));
                return navigator.serviceWorker.register(url, { scope: './' }).then(function (registration) {
                    var available = !!(registration.installing || registration.waiting || registration.active);
                    return new Promise(function (resolve) { later(function () { registration.unregister().then(function () { URL.revokeObjectURL(url); resolve(available); }, function () { URL.revokeObjectURL(url); resolve(available); }); }, 100); });
                }, function () { URL.revokeObjectURL(url); return false; });
            } catch (_) { if (url) URL.revokeObjectURL(url); return false; }
        }
        try { return navigator.serviceWorker.getRegistration().then(function (existing) { return existing ? true : register(); }, register); }
        catch (_) { return register(); }
    }
    function webSocketProbe(url) {
        if (!url) return Promise.resolve(null);
        return new Promise(function (resolve) {
            try {
                var socket = new WebSocket(url);
                socket.onopen = function () { try { socket.close(); } catch (_) {} };
                socket.onerror = function () {};
                resolve(true);
            } catch (_) { resolve(false); }
        });
    }
    function cryptoProbe(algorithm, extractable, usages) {
        var webCrypto = window.crypto;
        if (!webCrypto || !webCrypto.subtle) return Promise.resolve(false);
        try { return webCrypto.subtle.generateKey(algorithm, extractable, usages).then(function () { return true; }, function () { return false; }); }
        catch (_) { return Promise.resolve(false); }
    }
    function cryptoCapabilities() {
        var webCrypto = window.crypto;
        if (!webCrypto || !webCrypto.subtle) return Promise.resolve([false, false, false]);
        return cryptoProbe({ name: 'RSA-OAEP', modulusLength: 4096, publicExponent: new Uint8Array([1, 0, 1]), hash: 'SHA-256' }, true, ['encrypt', 'decrypt']).then(function (oaep) {
            return cryptoProbe({ name: 'RSA-PSS', modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: 'SHA-256' }, true, ['sign', 'verify']).then(function (pss) {
                return cryptoProbe({ name: 'AES-GCM', length: 256 }, false, ['encrypt', 'decrypt']).then(function (aes) { return [oaep, pss, aes]; });
            });
        });
    }

    function parameter(value) { return value === undefined || value === null ? '' : value; }
    function contentOnlyFetch(url, options) {
        var target = new URL(url, location.href);
        if (target.hostname === 'imasdk.googleapis.com' && target.pathname === '/cekh8i') {
            // Apply the same advertising deny policy as content-only discovery. The
            // probe observes a real policy rejection; no advertising request is sent.
            return Promise.reject(new TypeError('Advertising request blocked'));
        }
        return fetch(url, options);
    }
    function bootstrap() {
        var fileList = input.fileList || {};
        var active = fileList.active || {};
        var collection = fileList.type === 'trailer' ? 'trailers' : 'movies';
        var params = new URLSearchParams();
        var user = input.userParam || {};
        var fp = fingerprint();
        var av1 = av1Capability();
        var adProbe = contentOnlyFetch('https://imasdk.googleapis.com/cekh8i', { method: 'HEAD', mode: 'no-cors' })
            .then(function (response) { return response.redirected; }, function () { return true; });
        var capabilities = Promise.all([wasmProbe(), serviceWorkerProbe(), webSocketProbe(input.probeWebSocketUrl), cryptoCapabilities(), adProbe]);
        return Promise.all([fp, av1]).then(function (initial) {
            params.set('token', parameter(user.token)); params.set('av1', parameter(initial[1]));
            params.set('autoplay', parameter(user.autoplay)); params.set('audio', parameter(user.audio)); params.set('subtitle', parameter(user.subtitle));
            var headers = { 'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8', 'X-Requested-With': 'XMLHttpRequest', 'Borth': borth(initial[0], input.viewportSeed) };
            report('trace', { nonce: input.captureNonce, url: '/bnsi/' + collection + '/' + encodeURIComponent(active.id), headers: headers, body: params.toString() });
            return fetch('/bnsi/' + collection + '/' + encodeURIComponent(active.id), { method: 'POST', credentials: 'include', headers: headers, body: params.toString() });
        }).then(function (response) {
            return response.text().then(function (body) {
                if (!response.ok) {
                    var error = new Error('metadata status'); error.status = response.status;
                    error.response = { body: body.slice(0, 65536), url: response.url,
                        headers: Array.from(response.headers.entries()) };
                    throw error;
                }
                return body;
            });
        }).then(function (body) {
            metadataAt = relativeTiming();
            report('metadata', { nonce: input.captureNonce, body: body });
            var metadata;
            try { metadata = JSON.parse(body); } catch (_) { var parseError = new Error('metadata parse'); parseError.stage = 'parse'; throw parseError; }
            return capabilities.then(function (capability) {
                var resolution = { screenWidth: screen.width, screenHeight: screen.height, windowWidth: window.innerWidth, windowHeight: window.innerHeight, devicePixelRatio: window.devicePixelRatio };
                var connection = navigator.connection || {};
                var statInfo = { wasm: capability[0], sw: capability[1], resolution: resolution, webSocket: capability[2], platform: navigator.platform || false };
                var keys = capability[3];
                var statType = (input.movie && input.movie.type === 'trailer' ? 't' : 'm') + (keys[2] ? 'g' : '') + (keys[1] ? 'p' : '') + (keys[0] ? 'o' : '');
                settled = true; clearTimers();
                report('ready', { nonce: input.captureNonce, metadata: metadata, environment: { connectionType: connection.effectiveType || null, downlink: connection.downlink || null, pixelRatio: window.devicePixelRatio || null, screenW: screen.width, screenH: screen.height }, statInfo: statInfo, statType: statType, adBlock: capability[4], languages: navigator.languages || [navigator.language], pageEpochMs: pageEpochMs, timings: { metadata: metadataAt, ready: relativeTiming() } });
            });
        }).then(null, function (error) { fail(error.stage || 'metadata', error.status, error); });
    }

    bootstrap();
}());
