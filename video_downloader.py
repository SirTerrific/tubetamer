"""
Background video downloader for local playback.

Downloads approved videos via yt-dlp (2-phase: video then subs).
Logs all download activity to db/logs/downloads.log.
"""

import asyncio
import logging
import os
import re
import time as _time
from datetime import datetime
from pathlib import Path
from typing import Optional

import yt_dlp

logger = logging.getLogger(__name__)

_VIDEO_ID_RE = re.compile(r'^[a-zA-Z0-9_-]{11}$')
_SAFE_LANG_RE = re.compile(r'^[a-zA-Z]{2,3}(-[a-zA-Z0-9]+)?$')

STATUS_PENDING = "pending"
STATUS_DOWNLOADING = "downloading"
STATUS_READY = "ready"
STATUS_FAILED = "failed"

# Permissive format strings — let yt-dlp pick best, ffmpeg remuxes to mp4
_QUALITY_FORMATS = {
    "360p":  "bestvideo[height<=360]+bestaudio/best[height<=360]/best",
    "480p":  "bestvideo[height<=480]+bestaudio/best[height<=480]/best",
    "720p":  "bestvideo[height<=720]+bestaudio/best[height<=720]/best",
    "1080p": "bestvideo[height<=1080]+bestaudio/best[height<=1080]/best",
    "best":  "bestvideo+bestaudio/best",
}

_DAY_SECONDS = 86400


def _setup_download_logger(log_dir: Path) -> logging.Logger:
    """Create a file logger for download operations."""
    log_dir.mkdir(parents=True, exist_ok=True)
    dl_log = logging.getLogger("brg.downloads")
    dl_log.setLevel(logging.DEBUG)
    dl_log.propagate = False  # don't flood main logs

    if not dl_log.handlers:
        fh = logging.handlers.RotatingFileHandler(
            log_dir / "downloads.log",
            maxBytes=5 * 1024 * 1024,  # 5 MB
            backupCount=3,
            encoding="utf-8",
        )
        fh.setFormatter(logging.Formatter(
            "%(asctime)s [%(levelname)s] %(message)s",
            datefmt="%Y-%m-%d %H:%M:%S",
        ))
        dl_log.addHandler(fh)

    return dl_log


# Need this import at module level for RotatingFileHandler
import logging.handlers


class VideoDownloader:
    """Manages background downloading of approved videos."""

    def __init__(
        self,
        video_dir: str,
        video_store,
        log_dir: str = "db/logs",
        max_storage_gb: float = 10.0,
        quality: str = "720p",
        max_concurrent: int = 2,
        ydl_timeout: int = 300,
        subtitle_langs: str = "en,fr",
        retention_days: int = 1,
    ):
        self.video_dir = Path(video_dir).resolve()  # absolute path to avoid CWD issues
        self.video_dir.mkdir(parents=True, exist_ok=True)
        self.subs_dir = self.video_dir / "subs"
        self.subs_dir.mkdir(parents=True, exist_ok=True)
        self.video_store = video_store
        self.max_storage_bytes = int(max_storage_gb * 1024 * 1024 * 1024)
        self.quality = quality
        self.max_concurrent = max_concurrent
        self.ydl_timeout = ydl_timeout
        self.subtitle_langs = subtitle_langs
        self.retention_days = max(retention_days, 0)
        self._semaphore = asyncio.Semaphore(max_concurrent)
        self._queue: asyncio.Queue = asyncio.Queue()
        self._workers: list[asyncio.Task] = []
        self._cleanup_task: Optional[asyncio.Task] = None
        self._running = False
        # Dedup: track video IDs currently in the queue or downloading
        self._active: set[str] = set()
        # Download progress: {video_id: {"percent": 45.2, "downloaded": "50MiB", "total": "120MiB", "speed": "5MiB/s", "eta": 30}}
        self._progress: dict[str, dict] = {}
        # Download file logger
        self.dl_log = _setup_download_logger(Path(log_dir))

    # --- Path helpers ---

    def video_path(self, video_id: str) -> Path:
        if not _VIDEO_ID_RE.match(video_id):
            raise ValueError(f"Invalid video ID: {video_id}")
        return self.video_dir / f"{video_id}.mp4"

    def subtitle_files(self, video_id: str) -> list[dict]:
        if not _VIDEO_ID_RE.match(video_id):
            return []
        result = []
        for f in self.subs_dir.iterdir():
            if not f.is_file() or not f.name.startswith(f"{video_id}.") or not f.name.endswith(".vtt"):
                continue
            lang = f.name[len(video_id) + 1:-4]
            if not lang or not _SAFE_LANG_RE.match(lang):
                continue
            result.append({
                "lang": lang, "label": _lang_label(lang),
                "path": f, "url": f"/api/subs/{video_id}/{lang}",
            })
        priority = {"en": 0, "fr": 1, "es": 2, "de": 3, "pt": 4}
        result.sort(key=lambda s: (priority.get(s["lang"].split("-")[0], 99), s["lang"]))
        return result

    def is_downloaded(self, video_id: str) -> bool:
        try: return self.video_path(video_id).is_file()
        except ValueError: return False

    def get_progress(self, video_id: str) -> dict:
        """Return current download progress for a video, or empty dict."""
        return self._progress.get(video_id, {})

    def get_file_size(self, video_id: str) -> int:
        try:
            p = self.video_path(video_id)
            return p.stat().st_size if p.is_file() else 0
        except (ValueError, OSError): return 0

    def storage_used_bytes(self) -> int:
        total = 0
        try:
            for f in self.video_dir.iterdir():
                if f.is_file(): total += f.stat().st_size
            for f in self.subs_dir.iterdir():
                if f.is_file(): total += f.stat().st_size
        except OSError: pass
        return total

    # --- Lifecycle ---

    async def start(self, num_workers: int = 2) -> None:
        self._running = True
        for i in range(num_workers):
            self._workers.append(asyncio.create_task(self._worker(i)))
        if self.retention_days > 0:
            self._cleanup_task = asyncio.create_task(self._daily_cleanup_loop())
        self.dl_log.info("=== Downloader started (workers=%d, quality=%s, retention=%dd, subs=%s) ===",
                         num_workers, self.quality, self.retention_days, self.subtitle_langs)
        logger.info("Video downloader started (workers=%d, retention=%dd, subs=%s, log=db/logs/downloads.log)",
                     num_workers, self.retention_days, self.subtitle_langs)

    async def stop(self) -> None:
        self._running = False
        for _ in self._workers:
            await self._queue.put(None)
        for task in self._workers:
            task.cancel()
        self._workers.clear()
        if self._cleanup_task:
            self._cleanup_task.cancel()
            self._cleanup_task = None
        self._active.clear()
        self.dl_log.info("=== Downloader stopped ===")

    # --- Queue (with dedup) ---

    async def enqueue(self, video_id: str, profile_id: str = "default") -> None:
        if not _VIDEO_ID_RE.match(video_id):
            return
        # Dedup: skip if already active
        if video_id in self._active:
            return
        # Already on disk
        if self.is_downloaded(video_id):
            self.video_store.set_download_status(video_id, STATUS_READY, profile_id=profile_id)
            return
        # Already actively downloading (DB check for cross-restart)
        current = self.video_store.get_download_status(video_id, profile_id=profile_id)
        if current == STATUS_DOWNLOADING:
            return

        self._active.add(video_id)
        self.video_store.set_download_status(video_id, STATUS_PENDING, profile_id=profile_id)
        await self._queue.put((video_id, profile_id))
        self.dl_log.info("QUEUED %s (profile=%s)", video_id, profile_id)

    async def _worker(self, worker_id: int) -> None:
        while self._running:
            try:
                item = await self._queue.get()
                if item is None:
                    break
                video_id, profile_id = item
                async with self._semaphore:
                    await self._download(video_id, profile_id)
            except asyncio.CancelledError:
                break
            except Exception as e:
                logger.error("Worker %d error: %s", worker_id, e)

    # --- Download (two-phase) ---

    async def _download(self, video_id: str, profile_id: str) -> None:
        if not _VIDEO_ID_RE.match(video_id):
            self._active.discard(video_id)
            return

        self.dl_log.info("START %s (profile=%s)", video_id, profile_id)
        start_time = _time.monotonic()

        # Storage check
        if self.storage_used_bytes() >= self.max_storage_bytes:
            await self._cleanup_storage()
            if self.storage_used_bytes() >= self.max_storage_bytes:
                self.dl_log.error("FAILED %s — storage full", video_id)
                self.video_store.set_download_status(video_id, STATUS_FAILED, profile_id=profile_id)
                self._active.discard(video_id)
                return

        self.video_store.set_download_status(video_id, STATUS_DOWNLOADING, profile_id=profile_id)
        output_path = self.video_path(video_id)

        # Use a subdirectory for temp files to isolate from final outputs
        tmp_dir = self.video_dir / "tmp"
        tmp_dir.mkdir(exist_ok=True)
        # ABSOLUTE path with %(id)s to avoid CWD issues and handle format IDs
        outtmpl = str(tmp_dir / f"{video_id}.%(ext)s")

        url = f"https://www.youtube.com/watch?v={video_id}"
        fmt = _QUALITY_FORMATS.get(self.quality, _QUALITY_FORMATS["720p"])

        video_opts = {
            'format': fmt,
            'outtmpl': outtmpl,
            'merge_output_format': 'mp4',
            'socket_timeout': self.ydl_timeout,
            'retries': 3,
            'fragment_retries': 3,
            'cachedir': False,
            'postprocessors': [{
                'key': 'FFmpegVideoConvertor',
                'preferedformat': 'mp4',
            }],
            'progress_hooks': [self._make_progress_hook(video_id)],
        }

        # Phase 1: video
        try:
            loop = asyncio.get_event_loop()
            self.dl_log.info("DOWNLOADING %s — phase 1 (video, format=%s)", video_id, self.quality)
            await loop.run_in_executor(None, _ydl_download, url, video_opts, self.dl_log)
        except Exception as e:
            elapsed = _time.monotonic() - start_time
            self.dl_log.error("FAILED %s — video download error after %.0fs: %s", video_id, elapsed, e)
            self.video_store.set_download_status(video_id, STATUS_FAILED, profile_id=profile_id)
            _cleanup_dir(tmp_dir, video_id)
            self._progress.pop(video_id, None)
            self._active.discard(video_id)
            return

        # Find the merged output in tmp dir
        actual = _find_output_file(tmp_dir, video_id)
        if not actual:
            self.dl_log.error("FAILED %s — no output file in tmp dir", video_id)
            self.video_store.set_download_status(video_id, STATUS_FAILED, profile_id=profile_id)
            _cleanup_dir(tmp_dir, video_id)
            self._progress.pop(video_id, None)
            self._active.discard(video_id)
            return

        actual.rename(output_path)
        size_mb = output_path.stat().st_size / (1024 * 1024)
        elapsed = _time.monotonic() - start_time
        self.dl_log.info("OK %s — video downloaded (%.1f MB, %.0fs)", video_id, size_mb, elapsed)

        # Clean up any leftover temp files (partial streams, etc.)
        _cleanup_dir(tmp_dir, video_id)

        # Phase 2: subtitles (best-effort)
        self._progress[video_id] = {"percent": 100, "status": "subtitles"}
        if self.subtitle_langs:
            try:
                self.dl_log.info("DOWNLOADING %s — phase 2 (subtitles: %s)", video_id, self.subtitle_langs)
                await loop.run_in_executor(None, self._download_subs, url, video_id)
            except Exception as e:
                self.dl_log.warning("SUBS FAILED %s (video OK): %s", video_id, e)

        self.video_store.set_download_status(video_id, STATUS_READY, profile_id=profile_id)
        total_elapsed = _time.monotonic() - start_time
        subs = self.subtitle_files(video_id)
        sub_langs = ", ".join(s["lang"] for s in subs) if subs else "none"
        self.dl_log.info("READY %s — %.1f MB, %d subtitle(s) [%s], total %.0fs",
                         video_id, size_mb, len(subs), sub_langs, total_elapsed)
        self._progress.pop(video_id, None)
        self._active.discard(video_id)

    def _make_progress_hook(self, video_id: str):
        """Return a yt-dlp progress_hooks callback that updates _progress."""
        def hook(d):
            if d.get("status") == "downloading":
                total = d.get("total_bytes") or d.get("total_bytes_estimate") or 0
                downloaded = d.get("downloaded_bytes", 0)
                pct = (downloaded / total * 100) if total > 0 else 0
                speed_raw = d.get("speed")
                speed = _format_bytes(speed_raw) + "/s" if speed_raw else ""
                eta = d.get("eta") or 0
                self._progress[video_id] = {
                    "percent": round(pct, 1),
                    "downloaded": _format_bytes(downloaded),
                    "total": _format_bytes(total) if total else "",
                    "speed": speed,
                    "eta": int(eta),
                    "status": "downloading",
                }
            elif d.get("status") == "finished":
                self._progress[video_id] = {
                    "percent": 100,
                    "status": "merging",
                    "downloaded": "",
                    "total": "",
                    "speed": "",
                    "eta": 0,
                }
        return hook

    def _download_subs(self, url: str, video_id: str) -> None:
        langs = self._parse_sub_langs()
        if not langs:
            return

        sub_opts = {
            'outtmpl': str(self.subs_dir / f"{video_id}.%(ext)s"),
            'skip_download': True,
            'writesubtitles': True,
            'writeautomaticsub': True,
            'subtitlesformat': 'vtt/best',
            'subtitleslangs': langs,
            'socket_timeout': 30,
            'cachedir': False,
            'postprocessors': [{
                'key': 'FFmpegSubtitlesConvertor',
                'format': 'vtt',
            }],
        }

        try:
            with yt_dlp.YoutubeDL(sub_opts) as ydl:
                ydl.download([url])
        except Exception as e:
            self.dl_log.debug("yt-dlp subtitle error for %s: %s", video_id, e)

        # Validate subtitle files
        sub_count = 0
        for f in self.subs_dir.iterdir():
            if not f.is_file() or not f.name.startswith(f"{video_id}."):
                continue
            if not f.name.endswith(".vtt"):
                f.unlink(missing_ok=True)
                continue
            lang = f.name[len(video_id) + 1:-4]
            if not lang or not _SAFE_LANG_RE.match(lang):
                f.unlink(missing_ok=True)
            else:
                sub_count += 1

        if sub_count:
            self.dl_log.info("SUBS OK %s — %d track(s)", video_id, sub_count)

    def _parse_sub_langs(self) -> list[str]:
        if not self.subtitle_langs:
            return []
        s = self.subtitle_langs.strip()
        if s.lower() == "all":
            return ['all']
        return [l.strip() for l in s.split(",") if l.strip()]

    # --- Daily cleanup ---

    async def _daily_cleanup_loop(self) -> None:
        while self._running:
            try:
                await self._daily_cleanup()
            except Exception as e:
                self.dl_log.error("Daily cleanup error: %s", e)
            await asyncio.sleep(_DAY_SECONDS)

    async def _daily_cleanup(self) -> None:
        cutoff = _time.time() - (self.retention_days * _DAY_SECONDS)
        removed = 0
        for f in self.video_dir.iterdir():
            if not f.is_file() or f.suffix != ".mp4":
                continue
            vid = f.stem
            if not _VIDEO_ID_RE.match(vid):
                f.unlink(missing_ok=True)
                continue
            if f.stat().st_mtime < cutoff:
                f.unlink(missing_ok=True)
                self._delete_subs(vid)
                self.video_store.clear_download_status(vid)
                removed += 1
        if removed:
            self.dl_log.info("CLEANUP — removed %d video(s) older than %d day(s)", removed, self.retention_days)

    async def _cleanup_storage(self) -> None:
        for f in self.video_dir.iterdir():
            if not f.is_file() or f.suffix != ".mp4":
                continue
            vid = f.stem
            if not _VIDEO_ID_RE.match(vid):
                f.unlink(missing_ok=True)
                continue
            if not self.video_store.is_video_approved_anywhere(vid):
                f.unlink(missing_ok=True)
                self._delete_subs(vid)
                self.dl_log.info("CLEANUP — removed non-approved: %s", vid)

        if self.storage_used_bytes() < self.max_storage_bytes:
            return

        files = sorted(
            (f for f in self.video_dir.iterdir() if f.is_file() and f.suffix == ".mp4"),
            key=lambda f: f.stat().st_mtime,
        )
        for f in files:
            if self.storage_used_bytes() < self.max_storage_bytes * 0.8:
                break
            vid = f.stem
            f.unlink(missing_ok=True)
            self._delete_subs(vid)
            if _VIDEO_ID_RE.match(vid):
                self.video_store.clear_download_status(vid)
            self.dl_log.info("CLEANUP — removed oldest: %s", vid)

    def _delete_subs(self, video_id: str) -> None:
        for f in self.subs_dir.glob(f"{video_id}.*.vtt"):
            f.unlink(missing_ok=True)

    async def delete_video_file(self, video_id: str) -> None:
        if not _VIDEO_ID_RE.match(video_id):
            return
        path = self.video_path(video_id)
        if path.is_file():
            path.unlink(missing_ok=True)
            self.dl_log.info("DELETED %s (revoked/denied)", video_id)
        self._delete_subs(video_id)
        self.video_store.clear_download_status(video_id)

    async def retry_failed(self) -> None:
        failed = self.video_store.get_videos_by_download_status(STATUS_FAILED)
        for v in failed:
            await self.enqueue(v["video_id"], profile_id=v.get("profile_id", "default"))
        if failed:
            self.dl_log.info("RE-QUEUED %d failed downloads", len(failed))


# --- Module-level helpers ---

def _ydl_download(url: str, opts: dict, dl_log: logging.Logger) -> None:
    """Run yt-dlp download with logging."""
    class _LogFilter(logging.Filter):
        """Route yt-dlp logs to our download log."""
        def filter(self, record):
            dl_log.log(record.levelno, "[yt-dlp] %s", record.getMessage())
            return True

    ydl_logger = logging.getLogger(f"yt-dlp.{id(opts)}")
    ydl_logger.addFilter(_LogFilter())
    opts_with_log = {**opts, 'logger': ydl_logger}

    with yt_dlp.YoutubeDL(opts_with_log) as ydl:
        ret = ydl.download([url])
        if ret != 0:
            raise RuntimeError(f"yt-dlp exit code: {ret}")


def _find_output_file(search_dir: Path, video_id: str) -> Optional[Path]:
    """Find the merged output file in a directory."""
    # After merge, yt-dlp produces {video_id}.mp4
    direct = search_dir / f"{video_id}.mp4"
    if direct.is_file():
        return direct
    # Fallback: any video file starting with this ID
    for ext in ('.mp4', '.mkv', '.webm'):
        for f in search_dir.glob(f"{video_id}*{ext}"):
            if f.is_file() and '.part' not in f.name:
                return f
    return None


def _cleanup_dir(search_dir: Path, video_id: str) -> None:
    """Remove all temp files for a video in a directory."""
    for f in search_dir.glob(f"{video_id}*"):
        try:
            f.unlink()
        except OSError:
            pass


def _lang_label(code: str) -> str:
    _LABELS = {
        "en": "English", "fr": "Français", "es": "Español", "de": "Deutsch",
        "pt": "Português", "it": "Italiano", "nl": "Nederlands", "ru": "Русский",
        "ja": "日本語", "ko": "한국어", "zh": "中文", "ar": "العربية",
        "hi": "हिन्दी", "tr": "Türkçe", "pl": "Polski", "sv": "Svenska",
        "nb": "Norsk", "da": "Dansk", "fi": "Suomi", "uk": "Українська",
    }
    return _LABELS.get(code.split("-")[0], code.upper())


def _format_bytes(b) -> str:
    """Format bytes into human-readable string."""
    if not b or b <= 0:
        return ""
    if b < 1024:
        return f"{b}B"
    if b < 1024 * 1024:
        return f"{b/1024:.0f}KB"
    if b < 1024 * 1024 * 1024:
        return f"{b/(1024*1024):.1f}MB"
    return f"{b/(1024*1024*1024):.2f}GB"
