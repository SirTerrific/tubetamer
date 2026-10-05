---
type: "Reference"
title: "Configuration"
openwiki_generated: true
verified:
  - by: openwiki/0.7.0
    at: 2026-10-05T04:15:14.909Z
---

# Configuration

TubeTamer reads its settings from a YAML file or, if there is none, from `BRG_*` environment variables. Everything is collected into one frozen-shape `Config` dataclass in `config.py`, which `main.py` loads at startup. For how the settings get into a container, see [Build, test and release](build-test-release.md). For where each setting is used, see [Architecture](architecture.md).

## Load order

`load_config(config_path)` in `config.py` looks for configuration in this order:

1. The path given with `-c/--config`. If that file does not exist, startup fails with `FileNotFoundError`.
2. With no path given: `config.yaml`, then `config.yml`, in the working directory.
3. If neither file exists: `Config.from_env()`, which builds the whole config from `BRG_*` variables.

YAML and env-only modes are separate. When a YAML file is loaded, a `BRG_*` variable only has an effect if the YAML refers to it as `${VAR}`. The exception is `BRG_BASE_URL`, which `WebConfig.__post_init__` reads whenever `web.base_url` is empty.

After loading, `load_config` normalizes `app.locale` and `app.time_format` through `i18n`. It logs a warning if `telegram.admin_chat_id` is empty ("bot commands will be unauthorized") or is not numeric.

## Environment variable expansion in YAML

`expand_env_vars()` walks the parsed YAML (dicts, lists, strings) and replaces `${VAR}` and `$VAR` with the value from the environment. An unset variable becomes an empty string. This is how `config.example.yaml` and the Docker setup pass secrets such as the bot token without writing them into the file.

## Sections

| Section | Dataclass | Contents |
|---|---|---|
| `app` | `AppConfig` | `locale`, `time_format`, `log_level` (one of `debug`, `info`, `warning`, `error`; anything else falls back to `info` with a warning) |
| `web` | `WebConfig` | `host`, `port`, `poll_interval` (ms between pending-page status checks), `pin` (empty means no PIN), `session_secret` (generated and stored if empty), `base_url` (used in Telegram links) |
| `telegram` | `TelegramConfig` | `bot_token`, `admin_chat_id` |
| `youtube` | `YouTubeConfig` | search result count, channel cache size and TTL, yt-dlp timeout, `shorts_enabled`, `metadata_lang` |
| `database` | `DatabaseConfig` | `path` of the SQLite file |
| `watch_limits` | `WatchLimitsConfig` | `daily_limit_minutes` (0 means unlimited), `timezone`, `notify_on_limit` |
| `local_playback` | `LocalPlaybackConfig` | `enabled`, `video_dir`, `max_storage_gb`, `quality`, `max_concurrent_downloads`, `download_timeout`, `subtitle_langs`, `retention_days` |

Several of these values are only defaults for the first run. At runtime, the parent can override settings per profile from Telegram. Those overrides are stored in the database, scoped per profile by `ChildStore` (keys are prefixed `{profile_id}:`). Time-based settings are read through `resolve_setting()` in `utils.py`, which checks a per-weekday key `{day}_{base_key}` first and then `{base_key}` (see [Data layer](data-layer.md)).

## Environment variables (env-only mode)

| Variable | Setting | Default |
|---|---|---|
| `BRG_LOCALE` | `app.locale` | `en` |
| `BRG_TIME_FORMAT` | `app.time_format` | `locale` |
| `BRG_LOG_LEVEL` | `app.log_level` | `info` |
| `BRG_WEB_HOST` | `web.host` | `0.0.0.0` |
| `BRG_WEB_PORT` | `web.port` | `8080` |
| `BRG_POLL_INTERVAL` | `web.poll_interval` | `3000` |
| `BRG_PIN` | `web.pin` | empty |
| `BRG_SESSION_SECRET` | `web.session_secret` | empty (generated) |
| `BRG_BASE_URL` | `web.base_url` | empty |
| `BRG_BOT_TOKEN` | `telegram.bot_token` | empty |
| `BRG_ADMIN_CHAT_ID` | `telegram.admin_chat_id` | empty |
| `BRG_YOUTUBE_MAX_RESULTS` | `youtube.search_max_results` | `50` |
| `BRG_CHANNEL_CACHE_RESULTS` | `youtube.channel_cache_results` | `200` |
| `BRG_CHANNEL_CACHE_TTL` | `youtube.channel_cache_ttl` (seconds) | `1800` |
| `BRG_YDL_TIMEOUT` | `youtube.ydl_timeout` (seconds) | `30` |
| `BRG_SHORTS_ENABLED` | `youtube.shorts_enabled` | `false` |
| `BRG_METADATA_LANG` | `youtube.metadata_lang` | empty |
| `BRG_DB_PATH` | `database.path` | `db/videos.db` |
| `BRG_DAILY_LIMIT_MINUTES` | `watch_limits.daily_limit_minutes` | `0` |
| `BRG_TIMEZONE` | `watch_limits.timezone` | `America/New_York` |
| `BRG_NOTIFY_ON_LIMIT` | `watch_limits.notify_on_limit` | `true` |
| `BRG_LOCAL_PLAYBACK` | `local_playback.enabled` | `false` |
| `BRG_VIDEO_DIR` | `local_playback.video_dir` | `db/videos` |
| `BRG_VIDEO_MAX_STORAGE_GB` | `local_playback.max_storage_gb` | `10` |
| `BRG_VIDEO_QUALITY` | `local_playback.quality` | `720p` |
| `BRG_VIDEO_MAX_CONCURRENT` | `local_playback.max_concurrent_downloads` | `2` |
| `BRG_VIDEO_DOWNLOAD_TIMEOUT` | `local_playback.download_timeout` (seconds) | `300` |
| `BRG_SUBTITLE_LANGS` | `local_playback.subtitle_langs` | `en,fr` |
| `BRG_VIDEO_RETENTION_DAYS` | `local_playback.retention_days` | `1` |

Boolean variables count as true only when they equal `true` (case-insensitive). Numeric variables are parsed with `int()`/`float()`, so an invalid value stops startup.

## CLI overrides

`main.py` accepts `-c/--config` (the file path), `-v/--verbose` (DEBUG logging) and `--log-level`. The log level is applied before `load_config` runs, so warnings raised while loading the config respect it. After loading, `--log-level`, or `-v`, takes priority over `app.log_level`.

## Related

- [Build, test and release](build-test-release.md): `docker-compose.yml`, `.env.example` and the Unraid template that set these variables.
- [Quickstart](quickstart.md): the minimum configuration for a first run.
- [Data layer](data-layer.md): where runtime setting overrides are stored.
