# YummyDroid

Native Android, tablet, and Android TV client for browsing and watching anime from YummyAnime.

## What is included

- Home catalogue from the documented `https://api.yani.tv` API.
- Search, filters, sorting, and authenticated user marks.
- Anime details with posters, metadata, genres, rating, views, screenshots, related anime, and description.
- Native Media3/ExoPlayer playback with source resolution, voice selection, quality selection, OP/ED skip prompts, PiP, and next/previous episode controls.
- Episode picker.
- Android TV launcher support, D-pad friendly focus states, and touch/mouse support for phones and tablets.
- Configurable site domains, default quality, decoder mode, and autoplay behavior.

The current build uses the public application token visible in the web client. For a distributable build, create a personal-use application token at `https://yummyani.me/dev/applications` and replace `APPLICATION_ID` in `YummyAnimeApi.kt`.

## Build

Open the folder in Android Studio or run:

```powershell
.\gradlew.bat :app:assembleRelease
```

If Android SDK is not installed, install it from Android Studio first or set `sdk.dir` in `local.properties`.
