---
type: system
title: Android TV client
description: Native Android TV app under android-tv/ that talks to the server's /api/v1. Covers wiring, token storage, the API client, the player's heartbeat and block logic, and the CI that tests it.
tags: [android-tv, kotlin, client, api-v1, player, ci]
verified:
  - by: openwiki/0.7.2
    at: 2026-10-27T17:59:33.551Z
sources:
  - id: openwiki-source-18058d84d84929bd61eb1d92
    resource: repo://.github/workflows/android-tv.yaml
  - id: openwiki-source-61e4d03ad16787ae12183d90
    resource: repo://android-tv/app/src/main/java/com/sirterrific/tubetamer/api/TubeTamerApi.kt
  - id: openwiki-source-3fc23b2ada7471b519ede428
    resource: repo://android-tv/app/src/main/java/com/sirterrific/tubetamer/AppContainer.kt
  - id: openwiki-source-bcc31fd0669385cf5818c4a4
    resource: repo://android-tv/app/src/main/java/com/sirterrific/tubetamer/data/SessionStore.kt
  - id: openwiki-source-7e490aa3dc389badcad94de1
    resource: repo://android-tv/app/src/main/java/com/sirterrific/tubetamer/data/TokenCipher.kt
  - id: openwiki-source-b58b87c3b87f0ffe821bec5c
    resource: repo://android-tv/app/src/main/java/com/sirterrific/tubetamer/ui/PlayerViewModel.kt
generated: { by: "claude-code", at: "2026-10-27T17:59:33.551Z" }
---

# Android TV client

`android-tv/` holds a native Kotlin app for TV. A child browses approved videos, searches, requests new ones and plays them. The app holds no business rules. The server decides budgets, schedules and approval, and the app reacts to what it says. The server side is described in [API v1](api-v1.md).

## Wiring

`AppContainer` is manual dependency wiring, one instance per process:

- one `OkHttpClient` (5 s connect, 20 s read timeout);
- `TubeTamerApi`, the typed client for `/api/v1`;
- `SessionStore`, backed by `TokenCipher`;
- `AuthRepository`;
- an image loader.

Thumbnails under `/thumb/...` need authentication. The image loader therefore uses a derived client with `Credentials.interceptor`. That interceptor adds the bearer header only when the request host and port match the signed-in server, so the token is never sent to other hosts.

## Session and token storage

`SessionStore` keeps server URL, encrypted token, signed-in profile and locale in DataStore preferences. Changing the server URL drops the stored token and profile, because a token issued by another server is meaningless. `TokenCipher` encrypts the token with an AES-256-GCM key generated inside the Android Keystore. `decrypt` returns null when data is damaged or the key is gone, for example after a restore on another device, and the app then behaves as signed out.

## API client

`TubeTamerApi` wraps each endpoint and returns an `ApiResult`: `Ok`, `HttpError`, `NetworkError` or `BadResponse`. Some non-2xx statuses carry a meaningful body and come back as `Ok`: `play` accepts 403, 404 and 409, and `heartbeat` accepts 403. This lets the UI show a specific reason, such as time up or outside schedule, instead of a generic error. Home rows and channel pages are fetched with `catalog` in pages of 24.

## Player behaviour

`PlayerViewModel` plays one video at a time:

1. It calls `play`. A refusal with an error code becomes a `Blocked` state (`TIME_UP`, `OUTSIDE_SCHEDULE`, `NOT_APPROVED`, `NO_LOCAL_PLAYBACK`, `NOT_FOUND`).
2. If the server is still downloading, it polls `downloadStatus` every 3 s and shows progress. It gives up after four consecutive `not_found` answers, and a 401 expires the session.
3. When the video is ready it builds the stream URL, subtitles and resume position, and passes the bearer header to the player.
4. While playing it counts seconds and sends a heartbeat every 30 s of real playback, or when the local countdown reaches zero.

Heartbeat failures are handled so watch time is neither lost nor double counted. On 401 the session is marked expired. On 409 (the server restarted and forgot the video) the seconds are kept and the video is reopened. On 400 or 403 the player is blocked. On 429, 5xx or network errors the seconds are added to the next report.

## Localization and tests

The UI language follows the locale reported by the server and stored in the session. See [i18n](i18n.md). JVM unit tests cover URL normalization, the API client, locale and screen helpers. `ScreensUiTest` holds Compose UI tests that run on an emulator.

## CI

`.github/workflows/android-tv.yaml` ("Android TV Tests") runs on pushes to `main` and on pull requests that touch `android-tv/**`, and can also be started by hand. The `unit` job runs `testDebugUnitTest` and `assembleDebug` on JDK 21. The `ui` job needs `unit`, is skipped for pull requests, and runs `connectedDebugAndroidTest` on an API 30 Android TV emulator. See [build, test, release](build-test-release.md).
