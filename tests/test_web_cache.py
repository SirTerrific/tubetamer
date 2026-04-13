"""Tests for web/cache.py active-row filtering."""

from types import SimpleNamespace

from web.cache import build_active_row


def test_build_active_row_includes_allowlisted_channel_videos(video_store):
    """Active row shows all approved videos, including those from allowlisted channels."""
    video_store.add_channel("LEGO", "allowed")
    video_store.add_video(
        "lego1234567",
        "LEGO City Adventure",
        "LEGO",
        channel_id="UCP-Ng5SXUEt0VE-TXqRdL6g",
    )
    video_store.update_status("lego1234567", "approved")

    state = SimpleNamespace(video_store=video_store, word_filter_cache=None)

    active = build_active_row(state)
    assert any(v["video_id"] == "lego1234567" for v in active)
