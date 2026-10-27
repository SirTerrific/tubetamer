"""Downloader: restart recovery, failure cleanup, own thread pool."""

import asyncio

import video_downloader as vd
from data.video_store import VideoStore
from video_downloader import VideoDownloader

VID = "dlvideo0001"


def _setup(tmp_path):
    store = VideoStore(str(tmp_path / "v.db"))
    store.create_profile("default", "Default")
    store.add_video(VID, "T", "Chan")
    store.update_status(VID, "approved")
    dl = VideoDownloader(str(tmp_path / "videos"), store, log_dir=str(tmp_path / "logs"))
    return store, dl


def test_downloading_row_left_by_a_restart_is_queued_again(tmp_path):
    store, dl = _setup(tmp_path)
    try:
        store.set_download_status(VID, vd.STATUS_DOWNLOADING)  # what a crash leaves behind
        asyncio.run(dl.retry_failed())
        assert dl._queue.qsize() == 1
        assert store.get_download_status(VID) == vd.STATUS_PENDING
    finally:
        store.close()


def test_enqueue_skips_a_video_already_active_in_this_process(tmp_path):
    store, dl = _setup(tmp_path)
    try:
        asyncio.run(dl.enqueue(VID))
        asyncio.run(dl.enqueue(VID))
        assert dl._queue.qsize() == 1
    finally:
        store.close()


def test_unexpected_error_marks_failed_and_frees_the_video(tmp_path, monkeypatch):
    store, dl = _setup(tmp_path)
    try:
        class _Broken:
            def rename(self, target):
                raise OSError("disk locked")

        monkeypatch.setattr(vd, "_ydl_download", lambda *a: None)
        monkeypatch.setattr(vd, "_find_output_file", lambda *a: _Broken())
        dl._active.add(VID)

        try:
            asyncio.run(dl._download(VID, "default"))
        except OSError:
            pass

        assert store.get_download_status(VID) == vd.STATUS_FAILED
        assert VID not in dl._active
    finally:
        store.close()


def test_downloads_run_in_their_own_thread_pool(tmp_path, monkeypatch):
    store, dl = _setup(tmp_path)
    try:
        seen = []
        monkeypatch.setattr(vd, "_ydl_download", lambda *a: seen.append(__import__("threading").current_thread().name))
        monkeypatch.setattr(vd, "_find_output_file", lambda *a: None)

        async def run():
            await dl.start(num_workers=1)
            await dl._download(VID, "default")
            await dl.stop()
        asyncio.run(run())
        assert seen and seen[0].startswith("download")
    finally:
        store.close()


def test_video_id_regex_rejects_trailing_newline():
    assert vd._VIDEO_ID_RE.match("AAAAAAAAAAA")
    assert not vd._VIDEO_ID_RE.match("AAAAAAAAAAA\n")


def test_ytdlp_logger_goes_to_the_download_log(tmp_path):
    store, dl = _setup(tmp_path)
    try:
        records = []
        dl.dl_log.handlers.clear()

        import logging

        class _H(logging.Handler):
            def emit(self, record):
                records.append(record.getMessage())
        dl.dl_log.addHandler(_H())
        dl.dl_log.setLevel(logging.INFO)
        log = vd._YdlLogger(dl.dl_log)
        log.debug("a")
        log.warning("b")
        log.error("c")
        assert records == ["[yt-dlp] a", "[yt-dlp] b", "[yt-dlp] c"]
    finally:
        store.close()
