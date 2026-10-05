---
type: "Guide"
title: "TubeTamer quickstart"
openwiki_generated: true
verified:
  - by: openwiki/0.7.0
    at: 2026-10-05T04:15:14.909Z
---

# TubeTamer quickstart

TubeTamer is a self-hosted YouTube approval system for children. The child searches for videos on a simple web page and requests the ones they want. The parent gets a Telegram message with thumbnail, title, channel and duration, and taps **Approve** or **Deny**. When local playback is on, the server downloads the approved video with yt-dlp and streams it to the child's device, so the tablet never has to contact YouTube. It is a fork of BrainRotGuard, and the current version is in `version.py` (1.3.3).

## Components

| Component | What it does | Page |
|---|---|---|
| Kid web app (FastAPI, Jinja2, PWA) | Catalog, search, watch page, history, PIN and profile login | [Kid-facing web app](web-app.md) |
| Telegram bot | Approvals, channels, time limits, activity, setup wizard | [Telegram bot](telegram-bot.md) |
| YouTube extractor and downloader (yt-dlp) | Search, metadata, channel pages, Shorts, local downloads | [YouTube extraction and downloads](youtube.md) |
| SQLite storage | One file with profiles, videos, channels, settings and logs | [Data layer](data-layer.md) |
| Localization | English, French and Norwegian for the web UI and the bot | [Localization](i18n.md) |
| Configuration | `config.yaml` or `BRG_*` environment variables | [Configuration](configuration.md) |
| Packaging | Docker image, tests, releases | [Build, test and release](build-test-release.md) |

## How the pieces fit

All of this runs in a single Python process started by `main.py`. It creates the database, the Telegram bot, the downloader and the FastAPI server (through uvicorn), and shuts them down on SIGINT or SIGTERM. A request travels like this:

1. The child opens the web app and searches. The server asks YouTube through yt-dlp and shows the results.
2. The child taps **Request**. The server stores the video as `pending` and has the bot message the parent.
3. The parent taps a button. The bot updates the database and, for an approval, tells the downloader to fetch the file.
4. The child's pending page polls the status. Once it is approved, it opens the watch page.
5. The watch page plays the local file (or a YouTube embed when local playback is off) and reports watch time, which the server checks against the schedule and daily limits.

See [Architecture](architecture.md) for the startup order and the shared state.

## Run it

You need Docker, a Telegram bot token from @BotFather, and your Telegram chat id.

```bash
git clone https://github.com/SirTerrific/tubetamer.git
cd tubetamer
cp .env.example .env
cp config.example.yaml config.yaml
# put the bot token and chat id in .env
docker compose up -d
```

Open `http://<server-ip>:8080` on the child's device. To enable downloads for local playback, set `local_playback.enabled: true` in `config.yaml`. A pre-built multi-arch image (amd64 and arm64) is published as `ghcr.io/sirterrific/tubetamer:latest`.

Without Docker, install `requirements.txt` and run `python main.py` (options: `-c`, `-v`, `--log-level`). Plain Python needs ffmpeg for downloads. See `docs/running-without-docker.md`.

On first run the bot sends a setup hub to the admin chat. Use `/setup` to add children, set time limits, import starter channels and choose the Shorts mode.

To stop the child from using YouTube directly, block `youtube.com` and `googlevideo.com` in your DNS (Pi-hole, AdGuard Home or the router), and whitelist the TubeTamer server so it can still download. The application cannot prevent direct access by itself.

## Design decisions in short

- **yt-dlp, not the YouTube API:** no key, quota or billing.
- **Telegram, not a custom app:** inline buttons make approvals one tap, with no parent app to install.
- **SQLite:** zero setup, the whole database is one file.
- **Local playback:** YouTube embeds can hit sign-in walls on devices without a Google account. Embed mode remains as a fallback.
- **Server-side thumbnails and server-side rendering:** the child's device needs no Google access and no JavaScript framework.

The full list is in `docs/design-decisions.md`.

## Where to go next

- Change a setting: [Configuration](configuration.md).
- Cut a release or fix the image: [Build, test and release](build-test-release.md).
- Add a language: [Localization](i18n.md).
- Understand a bot command: [Telegram bot](telegram-bot.md).
- Debug video loading, Shorts or downloads: [YouTube extraction and downloads](youtube.md).
- Change a page or the PWA: [Kid-facing web app](web-app.md).
- Change the schema: [Data layer](data-layer.md).
