# YummyDroid

<p align="center">
  <img src="app/src/main/res/drawable-nodpi/app_banner_full.png" alt="YummyDroid banner" width="760">
</p>

<p align="center">
  <img src="app/src/main/res/drawable-nodpi/yummy_logo.png" alt="YummyDroid logo" width="96">
</p>

<p align="center">
  <a href="https://github.com/saltek1995/YummyDroid/releases/latest">Latest release</a>
  ·
  <a href="https://api.yani.tv/swagger">YummyAnime API</a>
</p>

YummyDroid is a native Android and Android TV client for YummyAnime. The app is built for one codebase across phones, tablets, TV boxes, and Android TV: touch, mouse, keyboard, physical remotes, virtual remotes, media keys, PiP, and fullscreen playback are treated as first-class input modes.

## Highlights

- Adaptive catalogue UI for phone, tablet, and TV layouts.
- Search, voice search, sorting, multi-select filters, user marks, and offline-only filtering.
- Anime cards with rating, views, posters, descriptions, screenshots, metadata, studios, directors, genres, watch order, comments, ratings, trailers, collections, and recommendations.
- Native Media3/ExoPlayer playback with source auto-selection, quality selection, voice selection, OP/ED skip prompts, next episode autoplay, hardware/software decoder settings, PiP controls, and watch progress sync.
- Authentication with profile, marks, favourites, ratings, video subscriptions, and links to the active site domain.
- Site-domain failover with editable domain list.
- Offline mode: downloaded episodes are bound to their anime card, cached anime data can be opened without internet, and local files are preferred over online playback.
- Background downloads with a device notification, a dedicated downloads section, "download all episodes", and quality choice before saving media.
- GitHub Releases based update flow for APK distribution.

## Offline Playback

When an episode is downloaded, YummyDroid stores the anime card, episode list, metadata, and local media reference together. Opening the same card later works from local cache if the site is unavailable, and launching the downloaded episode plays the local file first.

The filter window includes `Доступно офлайн`. If the site or internet connection is unavailable and cached anime exists, the catalogue automatically falls back to the offline list and shows an `Оффлайн` indicator. Manually enabling the offline filter does not show that warning indicator.

## Screens And Controls

The interface is designed around quick scanning and remote-friendly focus:

- Top catalogue sections: catalogue, top, schedule, collections, library, downloads.
- Search and filters are opened by buttons, not permanently occupying screen space.
- Episode buttons include preview images and offline status.
- Player controls use classic episode-switch icons, focused timeline scrubbing, PiP actions, and hidden system bars in fullscreen playback.

## Installation

Download the latest APK from:

https://github.com/saltek1995/YummyDroid/releases/latest

APK files are named as:

```text
YummyDroid-<version>.apk
```

## Build

Requirements:

- Android Studio or Android SDK installed.
- JDK 21.

Build a release APK:

```powershell
.\gradlew.bat :app:assembleRelease
```

Run Kotlin compilation check:

```powershell
.\gradlew.bat :app:compileDebugKotlin
```

## Project Notes

The app uses the documented YummyAnime API and native Android playback components. Player source resolving is implemented in this repository and is not copied from other YummyAnime clients.

Release APKs are signed during the Gradle release build and are published through this repository's GitHub Releases.

## License And Content

YummyDroid is a client application. Anime metadata and video availability depend on YummyAnime and third-party playback sources. The project does not bundle anime content.
