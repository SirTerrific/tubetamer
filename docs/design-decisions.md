# Design Decisions

| Decision | Why |
|----------|-----|
| **yt-dlp instead of YouTube API** | No API key signup, no quotas, no billing. yt-dlp is a well-maintained open source tool that extracts video info directly. |
| **Telegram instead of a custom app** | You already have Telegram on your phone. Inline buttons make approve/deny a one-tap action. No separate parent app to install. |
| **SQLite instead of a database server** | Zero setup. The entire database is one file. No Postgres, no Redis, nothing else to install or maintain. |
| **Local playback (server downloads, tablet streams)** | YouTube embeds break on tablets without a signed-in Google account ("Sign in to confirm you're not a bot"). The server downloads approved videos via yt-dlp and streams them itself — no Google account, no ads, no embeds on the kid's device. Embed mode remains available as a fallback. |
| **youtube.com embeds (fallback mode)** | Previously used `youtube-nocookie.com` for privacy, but YouTube's bot detection now blocks anonymous embeds with a sign-in wall. Standard `youtube.com` embeds work with a signed-in browser session. |
| **Thumbnail proxy** | Thumbnails used to load from `i.ytimg.com` directly, which broke when Google's IP space was blocked for the tablet. The server now fetches and disk-caches them, served from `/thumb/...` — the kid's device needs zero Google access. |
| **Server-side rendering (not a SPA)** | Simpler to run, simpler to debug. Works on any browser, including older tablets. No JavaScript framework to maintain. |
| **DNS blocking for enforcement** | The app itself can't prevent a kid from opening youtube.com directly. Blocking YouTube at the DNS level (your router or DNS server) closes that gap completely. |
