"""Playback API for native clients: play, stream/subs with bearer, heartbeat, time."""

import pytest

from data.child_store import ChildStore
from tests.test_api_v1 import _auth, _token, client, store, _no_rate_limit  # noqa: F401  (fixtures)

VID = "playvid0001"


class _FakeDownloader:
    """Minimal stand-in for VideoDownloader backed by a temp directory."""

    def __init__(self, root):
        self.video_dir = root / "videos"
        self.subs_dir = root / "subs"
        self.video_dir.mkdir()
        self.subs_dir.mkdir()
        self.queued = []

    def video_path(self, video_id):
        return self.video_dir / f"{video_id}.mp4"

    def is_downloaded(self, video_id):
        return self.video_path(video_id).is_file()

    def subtitle_files(self, video_id):
        out = []
        for f in sorted(self.subs_dir.glob(f"{video_id}.*.vtt")):
            lang = f.name.split(".")[1]
            out.append({"lang": lang, "label": "English", "path": f, "url": f"/api/subs/{video_id}/{lang}"})
        return out

    def get_progress(self, video_id):
        return {"percent": 42}


@pytest.fixture
def player(tmp_path, store, client):
    """Local playback on, one approved downloaded video, and a token."""
    state = client.app.state
    dl = _FakeDownloader(tmp_path)
    state.local_playback_enabled = True
    state.video_downloader = dl

    async def _queue(video_id, profile_id):
        dl.queued.append((video_id, profile_id))
    state.download_on_approve = _queue

    cs = ChildStore(store, "default")
    cs.add_video(VID, "Play me", "Chan", duration=120, channel_id="UCplay")
    cs.update_status(VID, "approved")
    dl.video_path(VID).write_bytes(bytes(range(256)) * 4)  # 1024 bytes
    (dl.subs_dir / f"{VID}.en.vtt").write_text("WEBVTT\n\n00:00.000 --> 00:01.000\nhi\n")
    return {"dl": dl, "cs": cs, "token": _token(client)}


def _play(client, token, vid=VID):
    return client.post(f"/api/v1/videos/{vid}/play", headers=_auth(token))


class TestPlay:
    def test_ready(self, client, player):
        resp = _play(client, player["token"])
        assert resp.status_code == 200, resp.text
        body = resp.json()
        assert body["status"] == "ready"
        assert body["stream"] == f"/api/stream/{VID}"
        assert body["subtitles"] == [{"lang": "en", "label": "English", "url": f"/api/subs/{VID}/en"}]
        assert body["video"]["title"] == "Play me"
        assert body["resume_seconds"] == 0
        assert body["remaining_sec"] == -1

    def test_requires_token(self, client, player):
        assert client.post(f"/api/v1/videos/{VID}/play").status_code == 401

    def test_resume_position(self, client, player):
        player["cs"].update_playback_position(VID, 42)
        assert _play(client, player["token"]).json()["resume_seconds"] == 42

    def test_not_downloaded_queues_download(self, client, player):
        player["dl"].video_path(VID).unlink()
        resp = _play(client, player["token"])
        assert resp.status_code == 202
        assert resp.json()["status"] == "pending"
        assert player["dl"].queued == [(VID, "default")]

    def test_downloading_not_queued_twice(self, client, player):
        player["dl"].video_path(VID).unlink()
        player["cs"].set_download_status(VID, "downloading")
        resp = _play(client, player["token"])
        assert resp.status_code == 202
        assert resp.json()["status"] == "downloading"
        assert player["dl"].queued == []

    def test_local_playback_off(self, client, player):
        client.app.state.local_playback_enabled = False
        resp = _play(client, player["token"])
        assert resp.status_code == 409
        assert resp.json()["error"] == "local_playback_disabled"

    def test_pending_video_refused(self, client, player):
        player["cs"].add_video("pendvid0001", "Wait", "Chan")
        resp = _play(client, player["token"], "pendvid0001")
        assert resp.status_code == 403
        assert resp.json() == {"error": "not_approved", "status": "pending"}

    def test_other_profile_video_not_found(self, client, player):
        bob = _token(client, "bob", "5678")
        client.app.state.extractor.extract_metadata.return_value = None
        assert _play(client, bob).status_code == 404

    def test_bad_id(self, client, player):
        assert _play(client, player["token"], "bad").status_code == 404

    def test_time_up(self, client, player):
        player["cs"].set_setting("daily_limit_minutes", "1")
        player["cs"].record_watch_seconds(VID, 120)
        resp = _play(client, player["token"])
        assert resp.status_code == 403
        assert resp.json()["error"] == "time_up"

    def test_remaining_reported(self, client, player):
        player["cs"].set_setting("daily_limit_minutes", "10")
        assert _play(client, player["token"]).json()["remaining_sec"] == 600

    def test_outside_schedule(self, client, player, monkeypatch):
        import web.routers.api_v1 as api
        monkeypatch.setattr(api, "get_schedule_info", lambda **_: {
            "allowed": False, "unlock_time": "8:00", "start": "8:00", "end": "20:00"})
        resp = _play(client, player["token"])
        assert resp.status_code == 403
        assert resp.json()["error"] == "outside_schedule"
        assert resp.json()["unlock_time"] == "8:00"


class TestStreamWithBearer:
    def test_range_request(self, client, player):
        resp = client.get(f"/api/stream/{VID}", headers={**_auth(player["token"]), "Range": "bytes=10-19"})
        assert resp.status_code == 206
        assert resp.headers["content-range"] == "bytes 10-19/1024"
        assert resp.headers["accept-ranges"] == "bytes"
        assert resp.content == bytes(range(10, 20))

    def test_open_ended_range(self, client, player):
        resp = client.get(f"/api/stream/{VID}", headers={**_auth(player["token"]), "Range": "bytes=1000-"})
        assert resp.status_code == 206
        assert len(resp.content) == 24

    def test_full_file(self, client, player):
        resp = client.get(f"/api/stream/{VID}", headers=_auth(player["token"]))
        assert resp.status_code == 200
        assert len(resp.content) == 1024

    def test_without_token_refused(self, client, player):
        assert client.get(f"/api/stream/{VID}").status_code == 401

    def test_subtitles(self, client, player):
        resp = client.get(f"/api/subs/{VID}/en", headers=_auth(player["token"]))
        assert resp.status_code == 200
        assert resp.text.startswith("WEBVTT")

    def test_download_status(self, client, player):
        resp = client.get(f"/api/download-status/{VID}", headers=_auth(player["token"]))
        assert resp.json() == {"status": "ready"}


class TestHeartbeat:
    def _hb(self, client, token, **body):
        return client.post("/api/v1/heartbeat", headers=_auth(token), json={"video_id": VID, **body})

    def test_requires_play_first(self, client, player):
        assert self._hb(client, player["token"], seconds=30).status_code == 409

    def test_records_time_and_position(self, client, player):
        _play(client, player["token"])
        resp = self._hb(client, player["token"], seconds=30, position_seconds=75)
        assert resp.status_code == 200, resp.text
        assert resp.json() == {"remaining": -1, "time_up": False}
        assert player["cs"].get_video(VID)["resume_seconds"] == 75

    def test_seconds_clamped(self, client, player):
        player["cs"].set_setting("daily_limit_minutes", "10")
        _play(client, player["token"])
        assert self._hb(client, player["token"], seconds=5000).json()["remaining"] == 540

    def test_time_up_flag(self, client, player):
        player["cs"].set_setting("daily_limit_minutes", "1")
        _play(client, player["token"])
        assert self._hb(client, player["token"], seconds=60).json() == {"remaining": 0, "time_up": True}

    def test_outside_schedule(self, client, player, monkeypatch):
        _play(client, player["token"])
        import web.routers.watch as watch
        monkeypatch.setattr(watch, "get_schedule_info", lambda **_: {"allowed": False})
        resp = self._hb(client, player["token"], seconds=30)
        assert resp.status_code == 403
        assert resp.json() == {"error": "outside_schedule"}

    def test_watching_is_per_token(self, client, player):
        _play(client, player["token"])
        other = _token(client)
        assert self._hb(client, other, seconds=30).status_code == 409


class TestTime:
    def test_no_limits(self, client, player):
        body = client.get("/api/v1/time", headers=_auth(player["token"])).json()
        assert body == {"categories": None, "daily": None, "schedule": None, "next_start": None}

    def test_daily_limit(self, client, player):
        player["cs"].set_setting("daily_limit_minutes", "30")
        body = client.get("/api/v1/time", headers=_auth(player["token"])).json()
        assert body["daily"]["limit_min"] == 30
        assert body["daily"]["exceeded"] is False

    def test_category_limits(self, client, player):
        player["cs"].set_setting("edu_limit_minutes", "20")
        body = client.get("/api/v1/time", headers=_auth(player["token"])).json()
        assert body["categories"]["edu"]["limit_min"] == 20
        assert body["daily"] is None
