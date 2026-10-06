---
type: "Reference"
title: "Kid-facing web app"
openwiki_generated: true
verified:
  - by: openwiki/0.7.0
    at: 2026-10-05T04:15:14.909Z
---

# Kid-facing web app

The web app is what the child sees on a tablet or computer: a catalog of approved videos, search, a watch page and a history page. It is a FastAPI application in `web/`, rendered with Jinja2 templates and installable as a PWA. Requests for new videos go to the parent through the [Telegram bot](telegram-bot.md). For startup and shared state, see [Architecture](architecture.md).

## Application setup (`web/app.py`)

`web/app.py` creates the `FastAPI` app, mounts `/static`, registers the custom Jinja filters and includes nine routers: auth, profile, ytproxy, catalog, pages, pwa, search, watch and stream. A `lifespan` handler starts `channel_cache_loop` as a background task when the app starts. `RateLimitExceeded` from `slowapi` returns a localized message with status 429 and `Retry-After: 5`, as JSON under `/api/` and as a small HTML page elsewhere.

`main.py` (see [Architecture](architecture.md)) adds the two middlewares, the session middleware and the shared objects the routers read from `app.state`. The `web/deps.py` helpers (`get_child_store`, `get_web_config`, `get_wl_config`, `get_youtube_config`, `get_extractor`, `get_notify_cb`, `get_time_limit_cb`) give routers typed access to that state. `get_child_store(request)` returns a `ChildStore` for the session's profile (see [Data layer](data-layer.md)).

## Middleware (`web/middleware.py`)

**`SecurityHeadersMiddleware`** adds to every response: `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Referrer-Policy: strict-origin-when-cross-origin` and a Content-Security-Policy. The policy allows own origin by default, scripts and styles from `'self'`, inline and `cdnjs.cloudflare.com`, images from `ytimg.com` hosts, frames from `www.youtube.com`, media from `*.googlevideo.com`, and forbids `object-src` and foreign `base-uri`.

**`PinAuthMiddleware`** requires a profile login whenever one is needed:

- `/login`, `/static`, `/manifest.webmanifest`, `/service-worker.js`, `/api/status/` and the two YouTube script proxies need no authentication.
- A session with `child_id` passes.
- If there is exactly one profile and it has no PIN, the middleware signs the session in as that profile automatically. With no profiles, or no legacy PIN configured, requests pass through.
- Otherwise `/api/` paths get `401 {"error": "unauthorized"}` and other paths a 303 redirect to `/login`.
- A request with `Authorization: Bearer <token>` (the Android TV app) is checked against the `device_tokens` table instead of the cookie. A valid token runs the request as its profile in a throwaway session, so no cookie is read or set; an invalid, revoked or expired token gets a 401 and never falls back to the cookie. `/api/v1/info`, `/api/v1/profiles` and `/api/v1/auth/login` need no authentication.

Sessions use Starlette's `SessionMiddleware` (signed cookie, 24-hour `max_age`, `same_site="strict"`). The secret is generated and stored on first run unless configured, see [Configuration](configuration.md).

## Routers (`web/routers/`)

| Router | Routes | Purpose |
|---|---|---|
| `auth.py` | `GET/POST /login`, `GET /switch-profile` | Profile picker and PIN check, then switching profile |
| `profile.py` | `POST /api/locale`, `POST /api/avatar` | UI language ([Localization](i18n.md)) and avatar icon and color |
| `pages.py` | `/`, `/activity`, `/requests`, `/history`, `GET /api/history` | Home page with the catalog, today's activity and time budget, the child's requests, and watch history in date groups |
| `catalog.py` | `GET /api/catalog`, `GET /api/catalog/status` | Paged catalog for "Show more" and infinite scroll, and cache status |
| `search.py` | `GET /search`, `POST /request` | Search, and submitting an approval request |
| `watch.py` | `/pending/{id}`, `/watch/{id}`, `/api/status/{id}`, `POST /api/watch-heartbeat` | Waiting page, watch page, status polling, watch time reporting |
| `stream.py` | `/api/stream/{id}`, `/api/download-status/{id}`, `/api/subs/{id}/{lang}` | Local playback: the downloaded file, download progress and subtitles |
| `ytproxy.py` | `/api/yt-iframe-api.js`, `/api/yt-widget-api.js`, `/thumb/{id}[/{variant}]` | YouTube player scripts and thumbnails served by the server |
| `pwa.py` | `/manifest.webmanifest`, `/service-worker.js` | PWA files served from the root path |
| `api_v1.py` | `/api/v1/...` | JSON API for native clients, see below |

### Search and requests

`/search` queries YouTube through the extractor (see [YouTube integration](youtube.md)) and drops results from blocked channels and titles that match a word filter. It hides Shorts unless Shorts are enabled for the profile. It also logs the query for the parent's activity report. `POST /request` stores the video as `pending` and calls the notify callback so the bot messages the parent. The child is sent to `/pending/{id}`, which polls `/api/status/{id}` every few seconds (`poll_interval`) and moves to the watch page after approval.

### Watching and time limits

`/watch/{id}` only plays approved videos. Pending or denied videos get the `pending.html` or `denied.html` page, and unknown ones go back to `/`. When the category or global budget is used up it renders `timesup.html`. Outside the allowed schedule it renders `outsidehours.html`. While a local download is still running it renders `downloading.html`. The page remembers the video in the session as `watching`, and its player script sends `POST /api/watch-heartbeat` periodically (also with `sendBeacon` when the page closes). The server:

- accepts it only for the video in `session["watching"]`, and only if it is approved;
- rejects it with 403 outside the schedule window;
- clamps the reported seconds to 0..60, and counts nothing if a heartbeat arrives faster than the minimum interval (evicting stale entries from the tracking dict as it goes);
- records the seconds and the playback position (for resume);
- checks the per-category budget (edu or fun) or the global budget, calls the time-limit callback once the budget is exceeded (the bot then notifies the parent) and returns the remaining seconds to the page.

### Local playback and streaming

With local playback on, approved videos are downloaded by yt-dlp to disk (see [YouTube integration](youtube.md)). `/api/stream/{id}` serves the file with HTTP Range support, so seeking works. Requests are limited to 60 per minute and the id must match a strict regex. It serves only videos approved for the session's profile, from the fixed video directory, with a resolved-path check as defense in depth. If the file is missing or the range is invalid, the response is a 404 or a 416. Otherwise the watch page embeds the YouTube player.

### Thumbnail proxy

The child's device may not reach YouTube. `/thumb/{id}` fetches `i.ytimg.com/vi/{id}/{variant}.jpg` once, stores it under `db/thumbs` (configurable through `thumb_dir`) and serves it from disk with a one-week immutable cache header. `maxresdefault` falls back to `hqdefault`. A missing variant leaves a `.404` marker so YouTube is not asked again. Writes use a temporary file and an atomic replace.

### Native client API (`/api/v1`)

The Android TV app (`android-tv/`) talks to these JSON routes. Video cards carry only `video_id`, `title`, `channel_name`, `channel_id`, `duration`, `category`, `is_short`, `progress_seconds` and a `thumbnail` path (`/thumb/{id}`, fetched with the same bearer).

| Route | Auth | Purpose |
|---|---|---|
| `GET /api/v1/info` | none | App name, version, `api_version`, local playback flag, default locale |
| `GET /api/v1/profiles` | none | Profiles for the picker, with `has_pin` but never the PIN |
| `POST /api/v1/auth/login` | none, 5/hour | Profile id + PIN for a 90-day bearer token, stored only as a SHA-256 hash |
| `POST /api/v1/auth/logout` | bearer | Revokes the token |
| `GET /api/v1/me` | bearer | Profile behind the token |
| `GET /api/v1/home?limit=` | bearer | First page of each non-empty row (`active`, `edu`, `fun`, `shorts` when enabled) and the allowed channels |
| `GET /api/v1/catalog?row=&channel=&offset=&limit=` | bearer | Next pages of a row, or of one channel with `row=all&channel=<id>` |

Rows reuse the web builders (`build_active_row`, `build_catalog`, `build_shorts_catalog`), so denied videos, word filters and the Shorts setting apply exactly as on the web home page.

## Channel and catalog cache (`web/cache.py`)

The home page must load quickly, so videos from allowed channels are fetched in the background.

- `channel_cache_loop` waits 5 seconds after start, then refreshes every `channel_cache_ttl` seconds (default 1800).
- `_refresh_channel_cache_for_profile` fetches the latest videos of each allowed channel, and Shorts when enabled, for that profile.
- Builders assemble the page rows: `build_catalog`, `build_active_row`, `build_requests_row`, `build_shorts_catalog`. They apply word filters and mark playback progress.
- `invalidate_channel_cache` and `invalidate_catalog_cache` drop cached data when the parent changes channels or video status through the bot hooks.
- `backfill_titles_for_locale` stores translated titles after a language switch.
- YouTube player scripts are cached for 24 hours (`_YT_CACHE_TTL`) so the tablet can load them through `/api/yt-*-api.js`.

## Templates and static files

`web/templates/base.html` is the shared layout: the header (logo, search, History and Activity links, language switch, avatar menu), the PWA meta tags, the stylesheet and the `buymeacoffee` footer link. The other pages extend it. Static files live in `web/static/` (`style.css`, icons, `thumb-preview.js`, `service-worker.js`).

`style.css` places the header with a flex row and breakpoints near 768 px and 1100 px. Between those widths it hides the History and Activity text labels, so only the icons remain, and it keeps the search bar centered.

## PWA and service worker

`/manifest.webmanifest` and `/service-worker.js` are served from the root so the worker controls the whole site. The worker (`web/static/service-worker.js`) pre-caches the static assets on install, deletes old caches on activate, and answers `/static/*` and the manifest with stale-while-revalidate. Other requests, including pages and APIs, always go to the network.

The cache names carry a version (`tubetamer-static-v5`, `tubetamer-runtime-v5`). Bump it whenever a cached static file such as `style.css` changes, otherwise installed clients keep the old file.

## Related

- [YouTube integration](youtube.md): the extractor and the downloader the routes call.
- [Localization](i18n.md): language selection.
- [Telegram bot](telegram-bot.md): where requests and limit notices go.
