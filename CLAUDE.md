# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

BrainRotGuard is a YouTube approval system for kids. It replaces algorithmic recommendations with a parent-controlled request flow: kids search and request videos via a web UI, parents approve/deny via Telegram, and approved videos play in a sandboxed environment. Supports multi-child profiles, time budgets, channel allow-lists, local video downloads, and i18n (English + Norwegian).

## Commands

```bash
# Run locally
pip install -r requirements.txt
python main.py [-c config.yaml] [-v|--log-level {debug|info|warning|error}]

# Run with Docker
docker compose up -d          # starts on http://localhost:8080

# Build Docker image
docker compose build

# Tests
pytest                        # all tests
pytest -v                     # verbose
pytest tests/test_config.py   # single file

# Deploy
./deploy.sh user@host [/opt/brainrotguard]
```

## Architecture

### Request Flow

```
Kid's tablet (FastAPI web UI) → requests video
  → FastAPI server → Telegram notification to parent
    → Parent approves/denies via inline buttons
      → Database updated → Web UI polls and reflects status
        → Video plays embedded (YouTube) or streamed (local playback)
```

### Core Components

- **`main.py`** — `BrainRotGuard` orchestrator: wires FastAPI, Telegram bot, video downloader; manages lifecycle and background tasks (channel backfill loop).
- **`config.py`** — Frozen dataclass hierarchy (`AppConfig`, `WebConfig`, `TelegramConfig`, etc.). Loads from YAML with `${ENV_VAR}` expansion, CLI args, and env vars (`BRG_*` prefix).
- **`data/video_store.py`** — SQLite with WAL mode, thread-safe via `threading.Lock`. Tables: `profiles`, `videos`, `watch_log`, `channels`, `settings`, `searches`, `download_status`.
- **`data/child_store.py`** — Wraps `VideoStore` by currying a `profile_id` into all operations. Per-child settings fall back to the default profile.
- **`bot/telegram_bot.py`** — Mixin-based bot: `ApprovalMixin`, `CommandsMixin`, `ChannelMixin`, `TimeLimitMixin`, `ActivityMixin`, `SetupMixin`. Inline button routing via `CallbackRoute` with regex patterns and `action:profile_id:video_id` callback data format (64-byte limit).
- **`web/app.py`** + **`web/routers/`** — FastAPI app with routers for search, catalog, watch, streaming, auth, profiles, PWA. Dependencies injected via `web/deps.py` (reads from `request.app.state`).
- **`youtube/extractor.py`** — Async yt-dlp wrapper. Runs extraction in thread pool (`asyncio.to_thread`). Validates video IDs (`^[a-zA-Z0-9_-]{11}$`) and thumbnail URLs (allowlisted CDN hosts).
- **`video_downloader.py`** — Background queue-based service for local playback. Semaphore-gated workers, dedup, progress tracking, storage cap enforcement, retention cleanup.

### Cross-Cutting Patterns

- **Async everywhere**: `asyncio.run()` in main, `asyncio.to_thread()` for blocking yt-dlp/ffmpeg calls, `asyncio.Semaphore` for download concurrency.
- **Web dependency injection**: All route dependencies via `Depends(get_*)` functions in `web/deps.py`, wired to `request.app.state` by `main.py`.
- **Localization**: `t(locale, key, **kwargs)` in `i18n/__init__.py`. English phrase is the translation key. Locale normalization handles variants (`no`/`nn`/`nb-no` → `nb`). Bot uses `self.tr()` wrapper.
- **Security**: Signed session cookies (itsdangerous), CSRF tokens, optional PIN gate middleware, rate limiting (slowapi with X-Forwarded-For), CSP headers, sandboxed YouTube iframes.
- **Caching**: TTL-based channel video cache (30 min default), catalog cache invalidated on status changes, daily shuffled Shorts list.
