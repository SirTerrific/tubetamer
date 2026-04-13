<p align="center">
  <img src="web/static/brg-logo.png" alt="TubeTamer" width="300">
</p>

<p align="center">
  <strong>YouTube approval system for kids — with a server that downloads the video for you.</strong><br>
  Your child searches and requests videos. You approve or deny from your phone via Telegram.<br>
  The server downloads the video locally. No YouTube on the tablet, no algorithm, no Firefox bot checks.
</p>

---

## Contents
- [What Is This?](#what-is-this)
- [Why This Fork?](#why-this-fork)
- [Features](#features)
- [Quick Start](#quick-start)
- [What You'll Need](#what-youll-need)
- [Documentation](#documentation)
- [License](#license)

## What Is This?

TubeTamer puts you in control of what your kids watch on YouTube — without standing over their shoulder.

Your child gets a simple web page on their tablet where they can search YouTube and request videos. Every request sends you a Telegram message with the video thumbnail, title, channel, and duration. You tap **Approve** or **Deny** right in the chat. If approved, the video plays on their tablet automatically.

**The key difference from standard YouTube embeds:** TubeTamer's server downloads the video to your machine first, then serves it locally to the kid's device. YouTube never touches the tablet. No bot checks, no "sign in to confirm you're not a bot", no Google account required, works with any browser including Firefox.

No YouTube account needed on the tablet. No ads. No algorithmic rabbit holes. No "up next" autoplay.

### How It Works

```
Kid's Tablet ──────────────────────────────────────────────────── Parent's Phone
      │                                                                  │
      │  1. Search & Request                                             │
      ▼                                                                  │
 TubeTamer Server ──── 2. Notify ──── Telegram Cloud ──── 3. Approve ──►│
      │                                                                  │
      │  4. Download video (yt-dlp + ffmpeg)                             │
      │     └─ stores locally on your server                             │
      │                                                                  │
      │  5. Stream video from server to tablet                           │
      ▼                                                                  │
 Kid's Tablet (plays video from local server — YouTube never contacted)
```

1. Kid opens TubeTamer on their tablet and searches for a video
2. They tap **Request** on the one they want to watch
3. You get a Telegram notification with thumbnail, title, channel, and duration
4. You tap **Approve** or **Deny** — the server queues the download immediately
5. The video downloads in the background (yt-dlp + ffmpeg, stored on your server)
6. The tablet streams the video directly from your server — no YouTube, no Google, no ads

> **Standard embed mode** is also available if you prefer not to use local downloads — works the same as the original project.

### Why I Built This

<details>
<summary>The short version: YouTube's algorithm was winning, and Google's new restrictions nearly killed the embed approach entirely.</summary>

I'm a father of a preteen son. I didn't want to block YouTube completely — YouTube is genuinely how I learn things myself, and I wanted my son to have that same ability to research topics, explore educational content, and develop the problem-solving habit of "let me figure this out." That's a skill I want him to have.

The problem was his feed. It was overrun with gamers screaming into microphones and brainrot content. I'd tell him to change the channel every time I walked by and heard one of those obnoxious gaming videos. He'd switch, but YouTube's algorithm would pull him right back within minutes. The algorithm is designed to keep kids glued — and it's very good at its job.

Every parental control I tried was either too restrictive (block YouTube entirely) or too permissive (YouTube Kids still recommends garbage). I needed the middle ground: let him explore and search freely, but give me the final say on what actually plays.

TubeTamer removes the algorithm entirely. There's no autoplay, no "up next" sidebar, no recommendation engine pulling him deeper. He searches for what he wants, I approve or deny, and the video plays and stops. Done. No rabbit holes.

Don't want them watching gaming content? Block those channels. Tired of a specific creator? One tap. You can allow the channels you trust (educational, science, building, nature) and block the ones you don't — and it sticks.

Now I curate his content and I can see the difference. He's not parroting gamer lingo back at me anymore. The stuff he watches is actually interesting — things he's curious about, things he's learning from. I pair this with Google Family Link on his device for general screen time, but TubeTamer is what controls YouTube specifically: daily time limits, scheduled access windows, and per-channel approval. Family Link says "the tablet turns off at 8pm." TubeTamer says "you can watch 2 hours of entertainment YouTube today, but educational content is unlimited — and only from channels I've approved."
</details>

## Why This Fork?

TubeTamer is a fork of [BrainRotGuard](https://github.com/GHJJ123/brainrotguard) by [@GHJJ123](https://github.com/GHJJ123), which is an excellent project and the direct inspiration for this one.

The original project uses standard YouTube embeds, which worked great — until Google tightened their bot detection in early 2025. Anonymous embedded playback started hitting "Sign in to confirm you're not a bot" walls, especially on Firefox and on devices without a signed-in Google account. This made the original approach unreliable for exactly the use case it was built for: a locked-down kid's tablet with no Google account.

The solution: **move the YouTube problem off the tablet entirely.** Instead of embedding YouTube on the kid's device, the TubeTamer server downloads the video using yt-dlp and serves it locally. The tablet never contacts YouTube — it just plays a video file from your home server. No bot checks. No sign-in walls. Works on Firefox. Works on any browser. Works without a Google account.

This fork adds:
- **Local video download and streaming** as the primary playback mode
- **Full Firefox compatibility** — no browser restrictions
- **Zero YouTube contact on the tablet** — complete isolation from Google's tracking

All the original features (multi-child profiles, Telegram approvals, time limits, channel lists, i18n) are preserved and included.

## Features

### For Kids
- **Works on any device and any browser** — Android tablet, iPad, Firefox, Chrome, Kindle Fire — no Google account required
- **Simple search** — type what you want, see results, tap Request
- **Instant playback** — approved videos stream from your server, no YouTube, no ads
- **Video library** — browse everything that's been approved before
- **Category browsing** — filter by educational or entertainment content with one tap
- **Channel browsing** — see latest videos from pre-approved channels without needing to request each one
- **YouTube Shorts** — dedicated Shorts row with portrait thumbnails and a 9:16 player
- **Thumbnail previews** — hover or scroll to cycle through multiple thumbnails before requesting
- **Dark theme** — easy on the eyes, designed for tablets

### For Parents
- **Telegram approval** — approve/deny from anywhere with one tap
- **Local video download** — server downloads approved videos using yt-dlp; tablet plays from your server
- **Channel allow/block lists** — trust a channel once, and new videos from it are auto-approved
- **Multi-child profiles** — separate PINs, watch history, and time budgets per child
- **Edu/Fun categories** — label channels and videos as educational or entertainment, each with its own daily time limit
- **Daily screen time limits** — set separate limits for educational and entertainment content, or a single global limit
- **Scheduled hours** — define when watching is allowed (e.g., 8am–7pm, not during school)
- **Per-day schedules** — different time windows and limits for each day of the week
- **Guided setup wizard** — `/time setup` walks through limit mode and schedule configuration with inline buttons
- **Bonus time** — grant extra minutes for today only (`/time add 30`)
- **Shorts control** — toggle YouTube Shorts visibility across the entire app
- **Watch activity log** — see what was watched, for how long, grouped by category
- **Localized UI and bot** — English and Norwegian support, with locale-aware or forced 12h/24h time display
- **Word filters** — block videos whose titles contain specific words
- **Search history** — see everything your child has searched for
- **Starter channels** — curated list of kid-friendly channels (edu + fun) to import on first boot
- **Update notifications** — automatic Telegram alert when a new version is available on GitHub
- **PIN lock** — optional PIN gate so only your kid can access the web UI on the right device

### Privacy & Security
- **100% self-hosted** — runs entirely on your own hardware inside your home network. No cloud service, no third-party accounts, no subscriptions
- **No API key needed** — uses [yt-dlp](https://github.com/yt-dlp/yt-dlp) for search, metadata, and download
- **No YouTube on the tablet** — local playback mode means the kid's device never contacts YouTube or Google
- **Single-file database** — all data is one SQLite file on your machine. Nothing phones home
- **Container runs as non-root** — Docker security best practice

## Quick Start

> **Prerequisites:** [Docker](https://docs.docker.com/get-docker/), a [Telegram bot token](https://core.telegram.org/bots#how-do-i-create-a-bot), and your [chat ID](docs/setup.md#step-2-get-your-chat-id). New to this? The **[full setup guide](docs/setup.md)** walks through everything step by step.

```bash
git clone https://github.com/SirTerrific/tubetamer.git
cd tubetamer
cp .env.example .env
cp config.example.yaml config.yaml
# Edit .env with your bot token and chat ID
docker compose up -d
```

Open `http://<your-server-ip>:8080` on the kid's tablet.

**Enable local video downloads** (recommended — removes YouTube from the tablet entirely):

In your `.env` file:
```
BRG_LOCAL_PLAYBACK=true
BRG_VIDEO_QUALITY=720p
```

**Pre-built image** (no build step, supports amd64 + arm64):
```bash
docker pull ghcr.io/sirterrific/tubetamer:latest
```
See the [full setup guide](docs/setup.md#using-the-pre-built-docker-image) for compose file details.

**Unraid:** Search for **TubeTamer** in Community Applications, or download the template manually:
```bash
wget -O /boot/config/plugins/dockerMan/templates-user/my-tubetamer.xml \
  https://raw.githubusercontent.com/SirTerrific/tubetamer/main/unraid-template.xml
```

**Important:** You'll also want to [lock down the kid's device](docs/setup.md#step-5-lock-down-the-kids-device) so they can't bypass TubeTamer by opening YouTube directly.

## What You'll Need

| Requirement | What It Is |
|-------------|-----------|
| **A computer that stays on** | Raspberry Pi, old laptop, or home server — anything running Docker |
| **Docker** | [Install Docker](https://docs.docker.com/get-docker/) |
| **Telegram account** | The messaging app where you'll receive approval requests |
| **Telegram bot token** | Created in 5 minutes via [@BotFather](https://core.telegram.org/bots#how-do-i-create-a-bot) |
| **Device lockdown** | [Family Link](https://families.google.com/familylink/) (Android) or [Screen Time](https://support.apple.com/en-us/HT208982) (iOS) to restrict browser access |

> **Why Telegram?** It was the easiest way to build instant notifications with approve/deny buttons that work from your phone. No custom app to develop, no push notification infrastructure to maintain — Telegram handles all of that.

> **Network note:** TubeTamer runs on your home network. Your child's device needs to be on the same network to access the web UI and stream videos. You can approve/deny from anywhere via Telegram — you don't need to be home for that part.

## Documentation

- **[Setup Guide](docs/setup.md)** — full walkthrough from Telegram bot creation to device lockdown
- [Configuration Reference](docs/configuration.md) — config.yaml options, environment variables, defaults
- [Locale Guide](i18n/locales/README.md) — how translations work and how to add a new language
- [Telegram Commands](docs/telegram-commands.md) — full command list for the parent bot
- [Troubleshooting](docs/troubleshooting.md) — common issues and fixes
- [Architecture](docs/architecture.md) — system diagrams and request flow
- [Design Decisions](docs/design-decisions.md) — why yt-dlp, Telegram, SQLite, etc.
- [Running Without Docker](docs/running-without-docker.md) — plain Python setup (no containers)

## Support

If you find TubeTamer useful:

[![Buy Me a Coffee](https://img.shields.io/badge/Buy%20Me%20a%20Coffee-support-yellow?logo=buymeacoffee)](https://buymeacoffee.com/menelikiii)

## License

[MIT](LICENSE) — use it however you want.
