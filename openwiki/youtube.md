---
type: "Reference"
title: "YouTube extraction and downloads"
openwiki_generated: true
verified:
  - by: openwiki/0.7.0
    at: 2026-10-05T04:15:14.909Z
---

# YouTube extraction and downloads

TubeTamer never uses the YouTube Data API or an API key. Everything comes from `yt-dlp`, in two modules: `youtube/extractor.py` reads metadata (search, video info, channel pages) and `video_downloader.py` downloads approved videos for local playback. The [web app](web-app.md) and the [Telegram bot](telegram-bot.md) call them. For the main components, see [Architecture](architecture.md).

## Extractor (`youtube/extractor.py`)

All functions are `async`. Each one runs yt-dlp in a worker thread (`asyncio.to_thread`) under `asyncio.wait_for`, so a slow YouTube call cannot block the event loop. On timeout or failure they log and return `None` or `[]` instead of raising.

| Function | Returns |
|---|---|
| `extract_metadata(video_id, lang)` | One video: title, channel name and id, thumbnail, duration, view count, `is_short` |
| `search(query, max_results, lang)` | Videos from a `ytsearch<N>:` query, flat extraction. Entries whose id is not an 11-character video id (channels, playlists) are skipped |
| `fetch_channel_videos(name, max_results, channel_id, lang)` | Recent uploads of a channel |
| `fetch_channel_shorts(name, max_results, channel_id, lang)` | Recent Shorts from the channel's `/shorts` tab, every item marked `is_short` |
| `resolve_channel_handle(handle)` | `@handle` to channel name, channel id and handle |
| `resolve_handle_from_channel_id(channel_id)` | Channel id to its `@handle`, from `uploader_id` or the channel URL |
| `extract_video_id(url_or_id)` | A video id from a watch, `youtu.be` or `/shorts/` URL, or a bare id |

`YouTubeExtractor` wraps these functions behind the `YouTubeExtractorProtocol`, so tests can substitute a fake. The web app reads it from `app.state` through `get_extractor`.

### Fetching a channel

`fetch_channel_videos` first uses the channel id (given, or found with a channel-type search for the exact name) and reads `https://www.youtube.com/channel/<id>/videos`. If that returns nothing, it falls back to `ytsearch` for the name and keeps results whose channel matches the name exactly. The overall timeout for this function is twice the normal one. Shorts are read from the separate `/shorts` tab, and only when Shorts are enabled (see [Web app](web-app.md) for the cache that calls this).

### Shorts detection

A video is a Short when its URL contains `/shorts/` (`_is_short_url`). For flat results the check uses the entry's `url`. For single videos it uses `webpage_url`. Video ids must match `^[a-zA-Z0-9_-]{11}$`. `_safe_thumbnail` accepts a thumbnail URL only if it is `https` and its host is in `THUMB_ALLOWED_HOSTS`. Otherwise it builds a standard `https://i.ytimg.com/vi/<id>/hqdefault.jpg` URL from the video id.

### Settings

`configure_timeout(seconds)` sets the yt-dlp timeout (`youtube.ydl_timeout`, default 30 seconds). `configure_metadata_lang(lang)` sets the preferred language for titles and descriptions (`youtube.metadata_lang`). YouTube translates titles on channels that opt in, according to the request locale, so without it a French channel can come back with English titles. `_ydl_opts(lang)` adds `extractor_args={'youtube': {'lang': [...]}}` when a language applies, and a per-call `lang` takes priority. This is how the UI language selection gets translated titles (see [Localization](i18n.md)). Both are applied at startup from the [configuration](configuration.md).

## Downloader (`video_downloader.py`)

`VideoDownloader` is the optional local playback feature. When it is on, an approved video is downloaded to disk and served by `/api/stream/{id}`, so the child's device never contacts YouTube for playback.

### Queue and workers

- `start(num_workers)` launches worker tasks and, if `retention_days > 0`, a daily cleanup loop. `stop()` ends them.
- `enqueue(video_id, profile_id)` ignores invalid ids and videos already active. If the file exists it only marks the download `ready`. If the database says `downloading`, it skips, which also covers restarts. Otherwise it records `pending` and queues `(video_id, profile_id)`.
- Each worker takes one item and runs `_download` under an `asyncio.Semaphore` sized by `max_concurrent_downloads`.
- The bot hooks `on_video_approve` and `on_video_revoke` call `enqueue` and delete the file. Failed downloads are retried at startup (see [Architecture](architecture.md)).

### Download steps

1. If storage is at or above `max_storage_gb`, run `_cleanup_storage`. If it is still full, mark the video `failed`.
2. Mark it `downloading` and write to a `tmp/` subfolder of `video_dir`.
3. **Phase 1, video:** yt-dlp with a format string for the configured quality (`360p`, `480p`, `720p`, `1080p`, or `best`; an unknown value falls back to `720p`), merged to mp4 with ffmpeg, 3 retries and 3 fragment retries, and a progress hook that records percent, size, speed and ETA for the watch page's progress display. On error, it deletes the temporary files and marks `failed`.
4. The merged file is renamed to `<video_id>.mp4` and temporary files are removed.
5. **Phase 2, subtitles:** best-effort download of the languages in `subtitle_langs`. A subtitle failure does not fail the video.
6. Mark `ready`.

Status values (`pending`, `downloading`, `ready`, `failed`) are stored through `VideoStore.set_download_status` (see [Data layer](data-layer.md)) and read by `/api/download-status/{id}`.

### Storage cleanup

- **Daily:** with `retention_days` above 0, every 24 hours it deletes `.mp4` files older than that many days (by modification time), their subtitles and their download status. A value of 0 disables age-based deletion.
- **When full:** `_cleanup_storage` first deletes files that are not approved for any profile (`is_video_approved_anywhere`), and files whose name is not a valid video id. If usage is still at the limit, it removes the oldest files first (by modification time), with their subtitles and download status, until usage drops below 80% of the limit.

Downloads are logged to `db/logs/downloads.log` in addition to the normal application log.

## Related

- [Configuration](configuration.md): `youtube.*` and `local_playback.*` settings.
- [Build, test and release](build-test-release.md): the Docker image installs ffmpeg and a JavaScript runtime that yt-dlp needs.
