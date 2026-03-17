"""Tests for web/cache.py request-row filtering."""

from types import SimpleNamespace

from web.cache import build_requests_row


def test_build_requests_row_excludes_allowlisted_name_without_channel_id(video_store):
    video_store.add_channel("LEGO", "allowed")
    video_store.add_video(
        "lego1234567",
        "LEGO City Adventure",
        "LEGO",
        channel_id="UCP-Ng5SXUEt0VE-TXqRdL6g",
    )
    video_store.update_status("lego1234567", "approved")

    state = SimpleNamespace(video_store=video_store, word_filter_cache=None)

    assert build_requests_row(state) == []
