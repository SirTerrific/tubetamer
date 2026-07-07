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
  locale: en             # UI/bot language: en or nb
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

- `locale` sets the language used by both the web UI and Telegram bot
- `time_format` controls how times are rendered in schedules, status messages, and other time-related labels

Supported canonical locales:

- `en` — English
- `nb` — Norwegian Bokmal

`locale` is normalized on load, so common variants such as `en-US`, `en_GB`, `nb-NO`, and `no` resolve to the supported internal locale automatically.

Supported time format values:

- `locale` — use the locale default
- `12h` — force 12-hour time
- `24h` — force 24-hour time

When `time_format` is set to `locale`, English defaults to 12-hour time and Norwegian defaults to 24-hour time.

For contributors adding another language, see the locale guide in [`i18n/locales/README.md`](../i18n/locales/README.md).

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
| `BRG_LOCALE` | UI/bot language (`en` or `nb`) | `en` |
| `BRG_TIME_FORMAT` | Time display format (`locale`, `12h`, `24h`) | `locale` |
| `BRG_LOG_LEVEL` | Log level (`debug`, `info`, `warning`, `error`) | `info` |
| `BRG_YOUTUBE_MAX_RESULTS` | Max search results | `50` |
| `BRG_CHANNEL_CACHE_RESULTS` | Videos cached per allowed channel | `200` |
| `BRG_CHANNEL_CACHE_TTL` | Seconds between channel cache refreshes | `1800` |
| `BRG_YDL_TIMEOUT` | Max seconds per yt-dlp operation | `30` |
| `BRG_SHORTS_ENABLED` | Shorts row on homepage | `false` |
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
