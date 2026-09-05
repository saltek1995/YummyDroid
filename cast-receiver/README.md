# YummyDroid Cast Receiver

The production receiver is deployed by GitHub Pages from this directory.

Receiver URL:

`https://saltek1995.github.io/YummyDroid/`

Register it as a Custom Receiver in the Google Cast SDK Developer Console.
The Android sender package is `me.yummydroid.app`. Do not associate an Android
TV package: the Web Receiver must remain available on every Cast device even
when YummyDroid was installed from an APK.

Receiver playback, selection, focus and OP/ED controls live in `receiver.js`.
Run the receiver regression tests from the repository root with Node.js 22+:

```sh
node app/src/test/js/cast-receiver.test.cjs
```

These tests execute the shipped script against a simulated Cast SDK and DOM.
They cover state transitions and commands; an actual Cast device is still
needed to verify decoding, network behavior and remote hardware input.
