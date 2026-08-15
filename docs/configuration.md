# Configuration Reference

All configuration lives in two files:

**`.env`** — your secrets (never share this file):
```
BRG_BOT_TOKEN=123456789:ABCdefGhIjKlMnOpQrStUvWxYz
BRG_ADMIN_CHAT_ID=987654321
BRG_PIN=1234
```

**`config.yaml`** — app behavior (references `.env` variables via `${VAR}` syntax):
```yaml
app:
  locale: en             # default UI/bot language: en, fr or nb
  time_format: locale    # locale default, or force 12h / 24h time display
  log_level: info        # debug, info, warning, or error

web:
  host: 0.0.0.0          # listen on all network interfaces
  port: 8080             # web UI port
  poll_interval: 3000    # how often pending page checks for updates (ms)
  pin: ${BRG_PIN}        # optional — remove this line to disable PIN
  # session_secret: auto-generated if not set

telegram:
  bot_token: ${BRG_BOT_TOKEN}
  admin_chat_id: ${BRG_ADMIN_CHAT_ID}

youtube:
  search_max_results: 50         # max results per search
  channel_cache_results: 200     # videos to cache per allowed channel
  channel_cache_ttl: 1800        # seconds between channel refreshes (default 30 min)
  ydl_timeout: 30                # seconds — max time for a single yt-dlp operation
  shorts_enabled: false          # Shorts row on homepage (also toggleable via /shorts)
  metadata_lang: ""              # language for video titles, e.g. "fr" (see below)

database:
  path: db/videos.db

watch_limits:
  daily_limit_minutes: 120       # 0 = unlimited (global fallback when no category limits set)
  timezone: America/New_York     # your local timezone
  notify_on_limit: true          # notify parent when limit is hit

# Local playback: the server downloads approved videos with yt-dlp and
# streams them itself — the kid's device needs zero YouTube/Google access.
local_playback:
  enabled: false                 # set to true to enable (recommended)
  video_dir: db/videos           # storage directory for downloaded videos
  max_storage_gb: 10             # auto-cleanup oldest files when limit reached
  quality: 720p                  # 360p, 480p, 720p, 1080p, best
  max_concurrent_downloads: 2
  download_timeout: 300          # seconds per download
  subtitle_langs: en,fr          # "all", "en,fr,es", or "" to disable subtitles
  retention_days: 1              # auto-delete video files after N days (0 = keep forever)
```

### Language and Time Format

Language and time display are configured under the `app:` section:

- `locale` sets the **default** language for the web UI and the language used by the Telegram bot
- `time_format` controls how times are rendered in schedules, status messages, and other time-related labels

Supported canonical locales:

- `en` — English
- `fr` — French
- `nb` — Norwegian Bokmal

The web UI also has an **EN/FR toggle** in the header. Picking a language there overrides `locale` **for that browser only** (stored in the session cookie), so different family members can use different languages on the same server. It switches the interface *and* video titles — see [Video Title Language](#video-title-language). The Telegram bot always follows the configured `locale`.

Norwegian is not in the toggle to keep it short, but it is fully supported: set `locale: nb` and it becomes the default for everyone, and it then appears in the toggle as the active language.

`locale` is normalized on load, so common variants such as `en-US`, `en_GB`, `fr-FR`, `fr_CA`, `nb-NO`, and `no` resolve to the supported internal locale automatically.

Supported time format values:

- `locale` — use the locale default
- `12h` — force 12-hour time
- `24h` — force 24-hour time

When `time_format` is set to `locale`, English defaults to 12-hour time; French and Norwegian default to 24-hour time.

For contributors adding another language, see the locale guide in [`i18n/locales/README.md`](../i18n/locales/README.md).

### Video Title Language

YouTube serves a *translated* title when a channel published one, choosing the language from the request. That is why a French channel's videos could show up as "How to prevent hair loss?" instead of "Comment éviter la perte de cheveux ?".

**In most cases you don't need to configure anything**: the header language toggle drives titles as well as the interface. Titles are cached per language in the `video_titles` table, filled in as videos are searched, as the channel cache refreshes, and by a one-off background fetch the first time a language is selected.

`youtube.metadata_lang` (or `BRG_METADATA_LANG`) sets the language used when no session language applies — the Telegram bot, the background channel refresh, and the first fetch of a newly requested video:

```yaml
youtube:
  metadata_lang: fr
```

Use a YouTube-supported code (`fr`, `es`, `de`, `nb`, …). Empty (the default) keeps YouTube's own choice.

**What this cannot do:** a channel that never published a translation has only its original title, so those videos keep it whatever language is selected. YouTube has nothing else to serve.

### Category Time Limits

Category limits are managed via Telegram commands, not config files. They're stored in the SQLite database:

- `/time edu 120` — 120 minutes/day for educational content
- `/time fun 60` — 60 minutes/day for entertainment content
- `/time edu off` — unlimited educational content
- `/time fun off` — unlimited entertainment content

When category limits are set, they replace the global `daily_limit_minutes`. When neither category limit is set, the global limit applies as a fallback.

Channels are tagged when allowlisted (`/channel allow @handle edu`) or recategorized later (`/channel cat <name> edu`). Individual videos are tagged during approval (Approve Edu / Approve Fun buttons) or toggled after approval.

### Environment Variables (no config.yaml)

If **no `config.yaml` exists**, everything falls back to environment variables. Note: when a `config.yaml` is present (the default Docker setup mounts one), these are ignored — except any referenced from the YAML via `${VAR}` syntax.

| Variable | Description | Default |
|----------|-------------|---------|
| `BRG_BOT_TOKEN` | Telegram bot token | *required* |
| `BRG_ADMIN_CHAT_ID` | Parent's Telegram chat ID | *required* |
| `BRG_WEB_HOST` | Web server bind address | `0.0.0.0` |
| `BRG_WEB_PORT` | Web server port | `8080` |
| `BRG_PIN` | Web UI access PIN (empty = no auth) | — |
| `BRG_SESSION_SECRET` | Session signing secret | auto-generated |
| `BRG_POLL_INTERVAL` | Pending page poll interval (ms) | `3000` |
| `BRG_LOCALE` | Default UI/bot language (`en`, `fr` or `nb`) | `en` |
| `BRG_TIME_FORMAT` | Time display format (`locale`, `12h`, `24h`) | `locale` |
| `BRG_LOG_LEVEL` | Log level (`debug`, `info`, `warning`, `error`) | `info` |
| `BRG_YOUTUBE_MAX_RESULTS` | Max search results | `50` |
| `BRG_CHANNEL_CACHE_RESULTS` | Videos cached per allowed channel | `200` |
| `BRG_CHANNEL_CACHE_TTL` | Seconds between channel cache refreshes | `1800` |
| `BRG_YDL_TIMEOUT` | Max seconds per yt-dlp operation | `30` |
| `BRG_SHORTS_ENABLED` | Shorts row on homepage | `false` |
| `BRG_METADATA_LANG` | Language for video titles (e.g. `fr`) | — |
| `BRG_DB_PATH` | SQLite database path | `db/videos.db` |
| `BRG_DAILY_LIMIT_MINUTES` | Global daily watch limit (0 = unlimited) | `0` |
| `BRG_TIMEZONE` | Timezone for watch limits | `America/New_York` |
| `BRG_NOTIFY_ON_LIMIT` | Notify parent when limit is hit | `true` |
| `BRG_LOCAL_PLAYBACK` | Enable local download + streaming | `false` |
| `BRG_VIDEO_DIR` | Storage directory for downloaded videos | `db/videos` |
| `BRG_VIDEO_MAX_STORAGE_GB` | Storage cap for downloads | `10` |
| `BRG_VIDEO_QUALITY` | Download quality (`360p`–`1080p`, `best`) | `720p` |
| `BRG_VIDEO_MAX_CONCURRENT` | Parallel downloads | `2` |
| `BRG_VIDEO_DOWNLOAD_TIMEOUT` | Seconds per download | `300` |
| `BRG_SUBTITLE_LANGS` | Subtitle languages (`all`, `en,fr`, `""`) | `en,fr` |
| `BRG_VIDEO_RETENTION_DAYS` | Auto-delete downloads after N days (0 = keep) | `1` |
