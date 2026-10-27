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


def test_annotate_categories_video_category_beats_channel_category(tmp_path):
    from data.child_store import ChildStore
    from data.video_store import VideoStore
    from web.helpers import annotate_categories
    vs = VideoStore(str(tmp_path / "c.db"))
    try:
        vs.create_profile("default", "Default")
        cs = ChildStore(vs, "default")
        cs.add_channel("Chan", "allowed", channel_id="UC1", category="edu")
        videos = [{"channel_id": "UC1", "category": "fun"}, {"channel_id": "UC1"}, {"channel_name": "Other"}]
        annotate_categories(videos, cs)
        assert [v["category"] for v in videos] == ["fun", "edu", "fun"]
    finally:
        vs.close()


def test_failed_channel_fetch_keeps_the_previous_cache(tmp_path):
    import asyncio
    from types import SimpleNamespace
    from unittest.mock import AsyncMock
    from data.video_store import VideoStore
    from web.cache import _refresh_channel_cache_for_profile
    vs = VideoStore(str(tmp_path / "c.db"))
    try:
        vs.create_profile("default", "Default")
        vs.add_channel("Chan", "allowed", channel_id="UC1")
        extractor = AsyncMock()
        extractor.fetch_channel_videos.return_value = [{"video_id": "abcdefghijk", "title": "T"}]
        extractor.fetch_channel_shorts.return_value = []
        state = SimpleNamespace(video_store=vs, channel_caches={}, extractor=extractor,
                                youtube_config=None, locale="en")
        asyncio.run(_refresh_channel_cache_for_profile(state, "default"))
        assert state.channel_caches["default"]["channels"]["UC1"]

        extractor.fetch_channel_videos.side_effect = RuntimeError("timeout")
        asyncio.run(_refresh_channel_cache_for_profile(state, "default"))
        assert state.channel_caches["default"]["channels"]["UC1"]  # not wiped
    finally:
        vs.close()
