---
type: "Reference"
title: "Data layer"
openwiki_generated: true
verified:
  - by: openwiki/0.7.2
    at: 2026-10-27T17:59:33.551Z
sources:
  - id: openwiki-source-9d466315e1f0ff671c570f26
    resource: repo://bot/telegram_bot.py
  - id: openwiki-source-5a63c691eaf81cc8c127492d
    resource: repo://data/child_store.py
  - id: openwiki-source-15171a1dd4ef264a3ac2757c
    resource: repo://data/starter_channels.py
  - id: openwiki-source-50563eb1b4c838bc9eb8dc1c
    resource: repo://data/video_store.py
  - id: openwiki-source-833e692518af9eeaf8564cc6
    resource: repo://main.py
  - id: openwiki-source-53688c65a9b4548093faff14
    resource: repo://utils.py
generated: { by: "claude-code", at: "2026-10-27T17:59:33.551Z" }
---

# Data layer

TubeTamer keeps all state in one SQLite file (default `db/videos.db`). `VideoStore` owns the connection and the schema, `ChildStore` gives each child profile a scoped view of it, and `starter_channels` loads a suggested-channels list from YAML. For how these fit with the rest of the app, see [Architecture](architecture.md). For the database path setting, see [Configuration](configuration.md).

## VideoStore (`data/video_store.py`)

`VideoStore(db_path)` creates the parent directory if needed, opens the database with `check_same_thread=False` and `sqlite3.Row` rows, and enables `PRAGMA journal_mode=WAL`. A `threading.Lock` guards access, because the web server, the Telegram bot and the background workers share the same connection.

`_create_tables()` runs on every start and uses `CREATE TABLE IF NOT EXISTS` for these tables:

| Table | Purpose |
|---|---|
| `device_tokens` | Bearer tokens for native clients such as the [Android TV app](android-tv.md). Only the SHA-256 of a token is stored; the profile, device name, expiry, last use and revocation time are kept alongside |
| `profiles` | One row per child: display name, PIN, avatar icon and color |
| `videos` | Approval state per video and profile: `pending`, `approved` or `denied`, view count, resume position, download status. Unique on `(video_id, profile_id)` |
| `watch_log` | One row per watch event, used for daily time limits and the history page |
| `channels` | Channel rules per profile: `allowed` or `blocked`, handle, category. Unique on `(channel_name, profile_id)` |
| `settings` | Key/value store with an update timestamp |
| `search_log` | Search queries and result counts, shown in the parent's activity report |
| `word_filters` | Blocked words, global or per profile |
| `video_titles` | Translated titles per video and language (YouTube serves a translated title when the channel published one) |

Indexes cover `watch_log(watched_at)`, `watch_log(video_id)`, `search_log(searched_at)` and `videos(status)`.

### Migrations

There is no migration framework. Older databases are upgraded in place at startup:

- `_add_column_if_missing(table, column, type)` adds columns introduced later (for example `channel_id`, `handle`, `category`, `is_short`, `profile_id`, `avatar_icon`, `avatar_color`, `yt_view_count`, `resume_seconds`, `download_status`). It only accepts table and column names from the `_ALLOWED_TABLES` and `_ALLOWED_COLUMNS` allow-lists, since names cannot be bound as SQL parameters.
- `_migrate_profile_id()` moves pre-multi-child data to the `default` profile and, when needed, rebuilds `videos` through `_rebuild_videos_table()` so the unique key includes `profile_id`.

### Method groups

The store has roughly 80 methods, grouped by what they manage:

- Videos: add, status lookups, approved/pending/denied lists with paging, fuzzy title search, view counts, resume position.
- Channels: allow, block, lists, handle and channel-id backfill (`get_channels_missing_handles`, `get_channels_missing_ids`, `update_channel_handle`).
- Watch time: `record_watch_seconds`, daily totals and breakdowns by category.
- Settings: `get_setting` and `set_setting`.
- Local playback: `get_download_status`, `set_download_status`, `get_videos_by_download_status`, `clear_download_status`.
- Device tokens: `create_device_token` issues a random URL-safe token (365-day lifetime by default) and returns it once. `resolve_device_token` accepts only a known, unrevoked, unexpired token whose profile still exists, and refreshes `last_used_at` at most once per hour. `revoke_device_token`, `revoke_device_token_by_id` (parent-side) and `list_device_tokens` (never returns hashes) complete the set.
- Maintenance: `prune_old_data` removes old `watch_log` and `search_log` rows at startup, along with device tokens that have been revoked or expired for more than 30 days. `close` closes the connection.

## ChildStore (`data/child_store.py`)

`ChildStore(store, profile_id)` wraps a `VideoStore` and passes `profile_id` to every child-scoped method, so handlers never need to repeat it. Web routes get one through `get_child_store(request)` using the session's `child_id` (default `default`). The Telegram bot builds one per profile it manages.

Settings are the special case. `ChildStore.set_setting` writes the key as `{profile_id}:{key}`. `get_setting` reads the prefixed key first. For the `default` profile only, it falls back to the bare key, so settings saved before profiles existed still apply.

Time-limit settings are read through `resolve_setting()` in `utils.py`, which checks a per-weekday key (`{day}_{base_key}`) before the base key.

## Starter channels (`data/starter_channels.py`)

`load_starter_channels(path)` reads `starter-channels.yaml` and returns a list of dicts with `handle`, `name`, `category` and `description`. It returns `[]` when the file is missing or malformed. It skips an entry, with a logged warning, when:

- the `handle` is missing or fails the handle regex;
- the `category` is missing or not one of the valid categories (`edu` or `fun`).

`main.py` passes the repository's `starter-channels.yaml` path to `TubeTamerBot`, which loads the list once at startup. The Telegram channel setup flow (`bot/channels.py`) uses it to offer one-tap channel approval. See [Telegram bot](telegram-bot.md).

## Related

- [Web app](web-app.md): how requests reach the store.
- [Architecture](architecture.md): startup order and shared state.
