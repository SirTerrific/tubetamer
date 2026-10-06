# Android TV app

The TubeTamer TV app lets a child watch approved videos on an Android TV box
(tested target: NVIDIA Shield) with their own profile and PIN. It is a separate
app called **TubeTamer**: it does not replace or touch the YouTube app.

The TV never contacts YouTube. The server downloads approved videos and streams
them to the TV, so the TV's small storage never fills up, and the same rules
apply as on the web app: approvals, word filters, blocked channels, time limits,
schedule and Shorts setting.

## What the server needs

- **Local playback on**: `local_playback.enabled: true` in `config.yaml`
  (or `BRG_LOCAL_PLAYBACK=true`). Without it the TV shows
  "Local playback is off on the server".
- **A child profile** (`/child add <name> [pin]` in Telegram). The TV signs in
  with the profile and its PIN.
- **The same network as the TV.** The app talks plain HTTP, like the web app;
  anyone on the network could read a TV's token. Keep TubeTamer on the home
  network, or put it behind an HTTPS reverse proxy and use the `https://`
  address on the TV.

## Build the APK

Requirements: Android Studio (for its SDK and its JDK 21), on the PC that builds.

```bash
cd android-tv
# Windows (Git Bash): use Android Studio's JDK for this command only
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
./gradlew assembleRelease
```

The APK lands in `android-tv/app/build/outputs/apk/release/app-release.apk`.

### Release signing key (once)

Android only installs signed APKs, and only accepts an update signed with the
**same key** as the installed app. Create the key once, keep it safe, and never
commit it (`*.jks` and `keystore.properties` are in `android-tv/.gitignore`).

1. Create the key. `keytool` asks for the passwords; choose them yourself:

   ```bash
   cd android-tv
   "$JAVA_HOME/bin/keytool" -genkeypair -v -keystore tubetamer-release.jks \
     -alias tubetamer -keyalg RSA -keysize 4096 -validity 10000
   ```

2. Create `android-tv/keystore.properties` with the passwords you chose:

   ```properties
   storeFile=tubetamer-release.jks
   storePassword=...
   keyAlias=tubetamer
   keyPassword=...
   ```

3. Back up `tubetamer-release.jks` and the passwords somewhere safe (password
   manager). If they are lost, the TV app must be uninstalled before a new
   build can be installed.

Without `keystore.properties`, `assembleRelease` produces an unsigned APK that
Android refuses to install. For a local check of the minified build only,
`./gradlew assembleRelease -PdebugSignedRelease` signs it with the debug key;
do not install that build on the child's TV, since later updates signed with
the release key would not install over it.

## Install on the TV (no ADB needed)

### From the TubeTamer server (recommended)

1. Put the APK on the server. With Docker:

   ```bash
   docker cp android-tv/app/build/outputs/apk/release/app-release.apk tubetamer:/app/db/tubetamer.apk
   ```

   The file lives in the `db` volume, so it survives container updates. Another
   location can be set with `web.tv_apk` (or `BRG_TV_APK`).
2. Check it from a browser: `http://<server>:8080/app/tubetamer.apk` downloads
   the file. This URL needs no PIN: the APK holds no secret.
3. On the TV, install **Downloader** (by AFTVnews) from the Play Store.
4. In Downloader, enter `http://<server>:8080/app/tubetamer.apk`.
5. When Android asks, allow **Install unknown apps** for Downloader, then
   install. You can turn that permission off again afterwards.

### USB stick

Copy the APK to a USB stick, plug it into the TV, and open it with a file
manager app (for example *File Commander* or *X-plore*). The file manager needs
the **Install unknown apps** permission.

### Send Files to TV

Install **Send Files to TV** on the TV and on a phone or PC, send the APK, then
open it on the TV.

### Updating

Install the new APK the same way, over the existing app. The child stays signed
in. The update must be signed with the same release key.

## First start on the TV

1. **Server address**: enter the address shown in the browser when you open
   TubeTamer, for example `192.168.1.10:8080` (`http://` is added if missing).
2. **Who's watching?**: pick the child's profile, then type the PIN with the
   remote.
3. The home screen shows the child's videos, the Educational and Entertainment
   rows, Shorts when enabled, and the channels. **Search** and **My requests**
   are in the header.

The app uses the server's language (`app.locale`), so menus match the video
titles. **Switch profile** goes back to the profile picker.

## Parent controls

- Approvals arrive in Telegram like requests from the web app.
- `/devices` lists the TVs signed in (device, child, last use) with a
  **Revoke** button for each. A revoked TV goes back to the profile picker.
- Time limits and the schedule are counted on the server: when the budget runs
  out during a video, the TV stops and shows when videos are available again.

## Troubleshooting

**"No answer from the server"**: the TV cannot reach the address. Check the
server runs, the port (default 8080), and that the TV is on the same network.
Try the address in a browser on another device.

**"This address is not a TubeTamer server" / "too old"**: the address points to
another service, or the server predates the TV app (v1.4.0 or later needed).

**"Local playback is off on the server"**: set `local_playback.enabled: true`
and restart the server.

**"Preparing the video…" stays at 0 %**: the server is downloading it. Check
the server can reach YouTube and look for yt-dlp errors in `docker compose logs`.

**Back at the profile picker without doing anything**: the TV's sign-in was
revoked with `/devices` or expired (one year after sign-in). Pick the profile and type the PIN.

**"App not installed"** when updating: the new APK is signed with a different
key. Uninstall TubeTamer from the TV, then install again.

## Manual test checklist

Run on the TV (or the Android TV emulator) before a release:

1. Fresh install: server address, wrong PIN refused, right PIN opens home.
2. Home rows and channel grid load; D-pad moves focus visibly; Back from a
   channel returns to the same card.
3. Play a downloaded video: full screen, subtitles if any, resume position,
   remaining-time badge. Back returns to the same card with an updated
   progress bar.
4. Play a video not downloaded yet: "Preparing the video… N %", then playback.
5. Search: results with status badges; ask for a video, confirm, badge turns
   "Waiting for approval"; approve in Telegram, badge turns "Approved" within
   10 s; it plays.
6. My requests lists pending, denied and approved requests.
7. Time limit reached during playback: playback stops with the time-up screen.
   Outside the schedule: the outside-hours screen.
8. `/devices` → Revoke: the TV returns to the profile picker on its next call.
9. Restart the TV app: it reopens signed in, in the server's language.
