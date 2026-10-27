---
type: system
title: API v1 for native clients
description: The /api/v1 JSON API in web/routers/api_v1.py that serves the Android TV app. Covers login and bearer tokens, home and catalog paging, search and requests, the play gate, and heartbeats.
tags: [api, api-v1, auth, playback, android-tv, fastapi]
verified:
  - by: openwiki/0.7.2
    at: 2026-10-27T17:59:33.551Z
sources:
  - id: openwiki-source-cba3bac573272f68888275f9
    resource: repo://web/routers/api_v1.py
generated: { by: "claude-code", at: "2026-10-27T17:59:33.551Z" }
---

# API v1 for native clients

`web/routers/api_v1.py` exposes a JSON API under `/api/v1` for native clients, mainly the [Android TV app](android-tv.md). Browser routes and cookie sessions are unchanged. The API reuses the same helpers as the [web app](web-app.md): the catalog builders in `web/cache.py`, `run_search` and `submit_request` from the search router, and `process_heartbeat` from the watch router. Web and TV therefore apply the same rules.

## Authentication

- `GET /info` and `GET /profiles` need no token. `/info` reports app version, `api_version`, whether local playback is on, and the server locale, which the client uses as a connection test. `/profiles` lists profiles with `has_pin` and never sends a PIN.
- `POST /auth/login` takes `profile_id`, `pin` and `device_name`. It is limited to 5 per hour. A profile with a PIN needs a matching one, compared with `hmac.compare_digest`. Success creates a device token through `create_device_token` and returns it with its expiry and the public profile.
- Every other call sends `Authorization: Bearer <token>`. `PinAuthMiddleware` resolves the token to a profile and sets `request.state.device_token`. `POST /auth/logout` revokes the token used for the call.
- `GET /me` lets a client check its token.

## Catalog

`GET /home` returns the first page of each non-empty row in a fixed order (`active`, `edu`, `fun`, `shorts`), plus the channel list sorted by name. `shorts` appears only when enabled for the profile. `GET /catalog` pages one row, or one channel with `row=all&channel=<id>`, with `offset` and `limit` (at most 100). Video cards expose only the fields the TV needs, and thumbnails point at `/thumb/<id>`, which requires authentication.

## Search and requests

`GET /search` runs the profile's filtered YouTube search and tags each result with its request status. `POST /requests` follows the web form rules: allowlisted channels auto-approve, blocked channels auto-deny, and anything else notifies the parent. It answers 400 for `invalid` and 502 for `fetch_failed`. `GET /requests` lists the profile's pending, denied and explicitly requested approved videos, newest first, capped at 50. Search, request, home and catalog calls are rate limited.

## Playback gate

`POST /videos/{id}/play` decides whether a video can start:

1. An unknown or malformed id gives 404. A video that is not approved gives 403 `not_approved`.
2. `_gate` applies the same rules as `/watch`: the category budget (or the global daily budget), then the schedule window. It returns 403 with `time_up` (including other categories that still have time) or `outside_schedule`.
3. If local playback is disabled the answer is 409 `local_playback_disabled`.
4. If the file is not downloaded yet, it queues the download and returns 202 `pending` or `downloading`. The client polls `/api/download-status/{id}` and calls play again.
5. Otherwise it records the view, marks the video as being watched, and returns the stream URL, subtitles, resume position and remaining seconds (-1 when unlimited).

`GET /time` returns the remaining budgets and schedule.

## Heartbeats

`POST /heartbeat` reports watch time about every 30 s. The video must match the one marked as watching for that token, otherwise the answer is 409 `not_watching`. Watching state is keyed by a SHA-256 hash of the token and held in memory on the app state, so a server restart forgets it, and the client reopens the video. Reported seconds are clamped to 0–60. The accounting is the same as the web heartbeat, and `time_up` is true when the remaining budget reaches zero.

## Tests

`tests/test_api_v1.py`, `test_api_v1_requests.py` and `test_api_v1_playback.py` cover the endpoints. `test_api_v1_security.py` checks that calls without, with revoked, or with garbage tokens are rejected, and that tokens are never logged or echoed in response headers.
