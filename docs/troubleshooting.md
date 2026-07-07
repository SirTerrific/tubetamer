# Troubleshooting

**The bot doesn't respond to commands**
- Make sure `BRG_BOT_TOKEN` is correct. You can verify by opening `https://api.telegram.org/botYOUR_TOKEN/getMe` in a browser — it should return your bot's info.
- Make sure `BRG_ADMIN_CHAT_ID` matches your account. The bot only responds to the configured admin.

**Videos won't play on the tablet (local playback mode)**
- Check the download finished: the video page shows a "downloading" state until the server has the file. See `docker compose logs` for yt-dlp errors.
- Make sure the **server** can reach YouTube — it's the server that downloads, not the tablet. If you use DNS blocking, whitelist the server's IP or give the container its own DNS (`dns: [8.8.8.8]` in docker-compose.yml).
- The tablet itself needs no YouTube/Google access at all in this mode.

**Videos won't play on the tablet (embed mode)**
- If local playback is disabled, the tablet plays YouTube embeds directly and needs `www.youtube.com` and `*.googlevideo.com` allowlisted.
- If you see "Sign in to confirm you're not a bot", sign into a Google account on the tablet's browser at `youtube.com`. The embed shares that session.
- Check the browser console for errors — some ad blockers interfere with embedded video playback.

**Thumbnails don't load**
- Since v1.2.0 thumbnails are served by the server from `/thumb/...` — the tablet doesn't contact Google for them. If they're missing, check that the **server** can reach `i.ytimg.com` and look for "Thumbnail fetch failed" in the logs.
- Cached thumbnails live in `db/thumbs`; deleting that folder is safe (they re-download on demand).

**"Connection refused" when opening the web UI**
- Confirm the container is running: `docker compose ps`
- Check logs for errors: `docker compose logs`
- Make sure port 8080 isn't blocked by a firewall on the host machine.

**Search returns no results**
- yt-dlp may need updating. Rebuild the container: `docker compose up -d --build`
- YouTube occasionally changes their site, which temporarily breaks yt-dlp until they release an update.

**The kid's tablet can still reach youtube.com**
- DNS changes can take a few minutes to propagate. Try clearing the tablet's DNS cache or restarting its WiFi.
- Make sure the DNS blocker is applied to the right device/network.
