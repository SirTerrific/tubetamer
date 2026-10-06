"""Search and video requests for native clients (/api/v1/search, /api/v1/requests)."""

import pytest

from data.child_store import ChildStore
from tests.test_api_v1 import _auth, _token, client, store  # noqa: F401 (fixtures)
from web.shared import limiter


@pytest.fixture(autouse=True)
def _no_rate_limit():
    limiter.enabled = False
    yield
    limiter.enabled = True


def _ex(client):
    return client.app.state.extractor


class TestSearch:
    def test_requires_token(self, client):
        assert client.get("/api/v1/search?q=cats").status_code == 401

    def test_results_carry_status(self, client, store):
        token = _token(client)
        resp = client.get("/api/v1/search?q=cats", headers=_auth(token))
        assert resp.status_code == 200
        body = resp.json()
        assert body["error"] == ""
        assert [v["video_id"] for v in body["videos"]] == ["abc12345678"]
        card = body["videos"][0]
        assert card["status"] == ""
        assert card["thumbnail"] == "/thumb/abc12345678"
        # Once requested, the same result shows its status.
        ChildStore(store, "default").add_video("abc12345678", "Search Result 1", "Result Channel")
        body = client.get("/api/v1/search?q=cats", headers=_auth(token)).json()
        assert body["videos"][0]["status"] == "pending"

    def test_status_is_per_profile(self, client, store):
        ChildStore(store, "bob").add_video("abc12345678", "Search Result 1", "Result Channel")
        body = client.get("/api/v1/search?q=cats", headers=_auth(_token(client))).json()
        assert body["videos"][0]["status"] == ""

    def test_empty_query(self, client):
        body = client.get("/api/v1/search?q=%20", headers=_auth(_token(client))).json()
        assert body == {"videos": [], "error": ""}
        _ex(client).search.assert_not_called()

    def test_blocked_channel_filtered(self, client, store):
        ChildStore(store, "default").add_channel("Result Channel", "blocked")
        body = client.get("/api/v1/search?q=cats", headers=_auth(_token(client))).json()
        assert body["videos"] == []

    def test_filtered_word_blocks_query(self, client, store):
        store.add_word_filter("cats")
        body = client.get("/api/v1/search?q=cats", headers=_auth(_token(client))).json()
        assert body["videos"] == []
        _ex(client).search.assert_not_called()

    def test_shorts_hidden_when_disabled(self, client):
        _ex(client).search.return_value = [{
            "video_id": "short123456", "title": "S", "channel_name": "C",
            "duration": 30, "is_short": True,
        }]
        body = client.get("/api/v1/search?q=cats", headers=_auth(_token(client))).json()
        assert body["videos"] == []

    def test_bad_link(self, client):
        _ex(client).extract_metadata.return_value = None
        body = client.get("/api/v1/search?q=https://youtu.be/dQw4w9WgXcQ",
                          headers=_auth(_token(client))).json()
        assert body == {"videos": [], "error": "fetch_failed"}


class TestRequest:
    def _post(self, client, token, vid="dQw4w9WgXcQ"):
        return client.post("/api/v1/requests", json={"video_id": vid}, headers=_auth(token))

    def test_requires_token(self, client):
        assert client.post("/api/v1/requests", json={"video_id": "dQw4w9WgXcQ"}).status_code == 401

    def test_new_request_is_pending_and_notifies(self, client, store):
        resp = self._post(client, _token(client))
        assert resp.status_code == 200
        body = resp.json()
        assert body["status"] == "pending"
        assert body["video"]["video_id"] == "dQw4w9WgXcQ"
        assert ChildStore(store, "default").get_video("dQw4w9WgXcQ")["status"] == "pending"
        assert ChildStore(store, "bob").get_video("dQw4w9WgXcQ") is None
        client.app.state.notify_callback.assert_awaited_once()

    def test_repeat_pending_renotifies(self, client):
        token = _token(client)
        self._post(client, token)
        self._post(client, token)
        assert client.app.state.notify_callback.await_count == 2

    def test_allowlisted_channel_auto_approves(self, client, store):
        ChildStore(store, "default").add_channel("Test Channel", "allowed", channel_id="UCtest123")
        body = self._post(client, _token(client)).json()
        assert body["status"] == "approved"
        client.app.state.notify_callback.assert_not_awaited()

    def test_blocked_channel_auto_denies(self, client, store):
        ChildStore(store, "default").add_channel("Test Channel", "blocked", channel_id="UCtest123")
        body = self._post(client, _token(client)).json()
        assert body["status"] == "denied"

    def test_accepts_link(self, client):
        body = self._post(client, _token(client), "https://www.youtube.com/watch?v=dQw4w9WgXcQ").json()
        assert body["video"]["video_id"] == "dQw4w9WgXcQ"

    def test_invalid_id(self, client):
        resp = self._post(client, _token(client), "not a video")
        assert resp.status_code == 400
        assert resp.json() == {"error": "invalid"}

    def test_fetch_failed(self, client):
        _ex(client).extract_metadata.return_value = None
        resp = self._post(client, _token(client))
        assert resp.status_code == 502
        assert resp.json() == {"error": "fetch_failed"}


class TestRequestList:
    def test_requires_token(self, client):
        assert client.get("/api/v1/requests").status_code == 401

    def test_lists_own_requests_with_status(self, client, store):
        cs = ChildStore(store, "default")
        cs.add_video("pending0001", "P", "Chan")
        cs.add_video("denied00001", "D", "Chan")
        cs.update_status("denied00001", "denied")
        cs.add_video("approved001", "A", "Chan")
        cs.update_status("approved001", "approved")
        ChildStore(store, "bob").add_video("bobvideo001", "B", "Chan")
        body = client.get("/api/v1/requests", headers=_auth(_token(client))).json()
        got = {r["video_id"]: r["status"] for r in body["requests"]}
        assert got == {"pending0001": "pending", "denied00001": "denied", "approved001": "approved"}
        assert all(r["requested_at"] for r in body["requests"])

    def test_channel_approvals_not_listed(self, client, store):
        cs = ChildStore(store, "default")
        cs.add_channel("Fun Chan", "allowed")
        cs.add_video("chanvid0001", "C", "Fun Chan")
        cs.update_status("chanvid0001", "approved")
        body = client.get("/api/v1/requests", headers=_auth(_token(client))).json()
        assert body["requests"] == []
