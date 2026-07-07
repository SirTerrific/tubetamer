# Setup Guide

Full walkthrough for getting TubeTamer running. If you already know your way around Docker and Telegram bots, the [Quick Start in the README](../README.md#quick-start) may be all you need.

## Contents
- [Step 1: Create a Telegram Bot](#step-1-create-a-telegram-bot)
- [Step 2: Get Your Chat ID](#step-2-get-your-chat-id)
- [Step 3: Install TubeTamer](#step-3-install-tubetamer)
- [Step 4: Start It Up](#step-4-start-it-up)
- [Step 5: Block YouTube on the Network](#step-5-block-youtube-on-the-network)
- [Using the Pre-Built Docker Image](#using-the-pre-built-docker-image)
- [Running Without Docker](#running-without-docker)

## Step 1: Create a Telegram Bot

1. Open Telegram and search for **@BotFather**
2. Send `/newbot`
3. Choose a name (e.g., "TubeTamer") and a username (e.g., "myTubeTamer_bot")
4. BotFather gives you a **token** — it looks like `123456789:ABCdefGhIjKlMnOpQrStUvWxYz`. Copy it, you'll need it in Step 3.

## Step 2: Get Your Chat ID

1. Open a new chat with your bot — search for `@yourbotname_bot` in Telegram, or go to `https://t.me/yourbotname_bot` in your browser. Press **Start**, then send any message (e.g., "hello")
2. Open this URL in your browser (replace `YOUR_TOKEN` with the token from Step 1):
   ```
   https://api.telegram.org/botYOUR_TOKEN/getUpdates
   ```
3. Look for `"chat":{"id":123456789}` in the response — that number is your **chat ID**

## Step 3: Install TubeTamer

```bash
# Download the project
git clone https://github.com/SirTerrific/tubetamer.git
cd tubetamer

# Create your secrets file
cp .env.example .env
# Create your config file
cp config.example.yaml config.yaml
```

Edit the `.env` file and fill in your values:
```
BRG_BOT_TOKEN=123456789:ABCdefGhIjKlMnOpQrStUvWxYz
BRG_ADMIN_CHAT_ID=987654321
BRG_PIN=1234
```
You can optionally edit the defaults in the config.yaml — notably `local_playback.enabled: true` to have the server download and stream videos itself (recommended, see Step 5).

| Setting | What to put | Required? |
|---------|------------|-----------|
| `BRG_BOT_TOKEN` | The token from Step 1 | Yes |
| `BRG_ADMIN_CHAT_ID` | The chat ID from Step 2 | Yes |
| `BRG_PIN` | A PIN code kids enter to use the web UI. Leave empty to skip. | No |

## Step 4: Start It Up

```bash
docker compose up -d
```

That's it. Open `http://<your-server-ip>:8080` on the kid's tablet.

To check that it's running:
```bash
docker compose logs -f
```

To stop it:
```bash
docker compose down
```

To update to a new version:
```bash
git pull
docker compose up -d --build
```

## Step 5: Block YouTube on the Network

With TubeTamer's local playback mode, you can block `youtube.com` **entirely** on your kid's device — no Google account, no YouTube app, no sign-in walls. The kid has full browser access to the rest of the internet; only YouTube is blocked. TubeTamer itself is not affected because the server downloads the video, not the tablet.

### How it works

```
Kid's device                TubeTamer server
youtube.com ──► BLOCKED ✗   youtube.com ──► allowed (whitelisted or different DNS)
googlevideo.com ► BLOCKED ✗  googlevideo.com ► allowed
TubeTamer UI ──► OK ✓       downloads via yt-dlp ──► streams back to tablet
```

The kid opens any browser (Firefox, Chrome, etc.), goes to YouTube — blocked. Goes to TubeTamer — works. The server handles all YouTube communication on their behalf.

Since v1.2.0 this includes **thumbnails**: the server fetches and caches them from YouTube's image CDN (`i.ytimg.com`) and serves them from `/thumb/...`. You can block Google's entire IP space for the kid's device — no exceptions needed for images.

### Block via AdGuard Home or Pi-hole

Add these to your blocklist:

```
youtube.com
www.youtube.com
m.youtube.com
googlevideo.com
ytimg.com
```

Then **whitelist your TubeTamer server's IP** in the client settings so it bypasses the block. In AdGuard Home: **Filters > DNS blocklists > Client settings**, or add a rewrite/exception for the server's IP.

Alternatively, configure your TubeTamer server to use a public DNS resolver directly (e.g. `8.8.8.8`) so it doesn't go through your local Pi-hole/AdGuard:

```yaml
# docker-compose.yml — under the service
dns:
  - 8.8.8.8
  - 8.8.4.4
```

### Block via router

Most routers (OpenWRT, pfSense, OPNsense, Firewalla) support per-device or per-group DNS filtering. Apply the YouTube blocklist to the kid's device MAC address or VLAN, and leave the TubeTamer server unrestricted.

### Block the YouTube app

Don't forget to also uninstall or block the YouTube and YouTube Kids apps on the tablet — DNS blocking only affects the browser, not apps that bypass it.

### Install TubeTamer as a PWA (optional but recommended)

Adding TubeTamer to the home screen as a PWA gives it a standalone app icon and a cleaner fullscreen experience:

- **Android**: Open Chrome, go to `http://<your-server-ip>:8080`, tap menu (three dots) > **Add to Home screen**
- **iOS/iPad**: Open Safari, go to `http://<your-server-ip>:8080`, tap **Share** > **Add to Home Screen**

## Using the Pre-Built Docker Image

If you don't want to build from source, you can pull the pre-built image from GitHub Container Registry. It supports both `amd64` and `arm64` (Raspberry Pi, Unraid, etc.).

```bash
docker pull ghcr.io/sirterrific/tubetamer:latest
```

Then use the example compose file instead of building locally:

```bash
# Download config and env templates
curl -O https://raw.githubusercontent.com/SirTerrific/tubetamer/main/config.example.yaml
curl -O https://raw.githubusercontent.com/SirTerrific/tubetamer/main/.env.example
curl -O https://raw.githubusercontent.com/SirTerrific/tubetamer/main/docker-compose.example.yml

# Set up your config
cp config.example.yaml config.yaml
cp .env.example .env
# Edit .env with your bot token and chat ID

# Start it
docker compose -f docker-compose.example.yml up -d
```

To update to a new version:
```bash
docker compose -f docker-compose.example.yml pull
docker compose -f docker-compose.example.yml up -d
```

## Running Without Docker

See [Running Without Docker](running-without-docker.md) for a plain Python setup.
