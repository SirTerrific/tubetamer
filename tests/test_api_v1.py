"""Tests for the native-client JSON API (/api/v1) and bearer-token auth."""

import re

import pytest

from data.child_store import ChildStore
from data.video_store import VideoStore
from web.shared import limiter
from tests.test_web_integration import AppClient, _create_test_app


@pytest.fixture(autouse=True)
def _no_rate_limit():
    limiter.enabled = False
    yield
    limiter.enabled = True


@pytest.fixture
def store(tmp_path):
    s = VideoStore(db_path=str(tmp_path / "test.db"))
    s.create_profile("default", "Alice", pin="1234")
    s.create_profile("bob", "Bob", pin="5678")
    yield s
    s.close()


@pytest.fixture
def client(store):
    return AppClient(_create_test_app(store), raise_server_exceptions=False)


def _token(client: AppClient, profile_id: str = "default", pin: str = "1234") -> str:
    resp = client.post("/api/v1/auth/login", json={"profile_id": profile_id, "pin": pin, "device_name": "Shield"})
    assert resp.status_code == 200, resp.text
    return resp.json()["token"]


def _cookie_login(client: AppClient, profile_id: str, pin: str) -> None:
    """Browser-style login for a chosen profile (the shared _login helper assumes one profile)."""
    page = client.get(f"/login?profile={profile_id}")
    csrf = re.search(r'name="csrf_token"\s+value="([^"]+)"', page.text).group(1)
    client.post("/login", data={"pin": pin, "profile_id": profile_id, "csrf_token": csrf}, follow_redirects=False)


def _auth(token: str) -> dict:
    return {"Authorization": f"Bearer {token}"}


class TestDiscovery:
    def test_info_without_auth(self, client):
        resp = client.get("/api/v1/info")
        assert resp.status_code == 200
        body = resp.json()
        assert body["app"] == "tubetamer"
        assert body["api_version"] == 1
        assert body["version"]

    def test_profiles_without_auth_hide_pins(self, client):
        resp = client.get("/api/v1/profiles")
        assert resp.status_code == 200
        profiles = {p["id"]: p for p in resp.json()["profiles"]}
        assert set(profiles) == {"default", "bob"}
        assert profiles["default"]["has_pin"] is True
        assert "pin" not in profiles["default"]
        assert "1234" not in resp.text


class TestLogin:
    def test_correct_pin_returns_token(self, client):
        resp = client.post("/api/v1/auth/login", json={"profile_id": "default", "pin": "1234"})
        assert resp.status_code == 200
        body = resp.json()
        assert body["token_type"] == "bearer"
        assert len(body["token"]) >= 32
        assert body["expires_at"]
        assert body["profile"] == {"id": "default", "display_name": "Alice", "avatar_icon": "", "avatar_color": ""}

    def test_wrong_pin_rejected(self, client):
        resp = client.post("/api/v1/auth/login", json={"profile_id": "default", "pin": "0000"})
        assert resp.status_code == 401
        assert resp.json()["error"] == "invalid_pin"

    def test_empty_pin_rejected_when_profile_has_pin(self, client):
        resp = client.post("/api/v1/auth/login", json={"profile_id": "default"})
        assert resp.status_code == 401

    def test_unknown_profile(self, client):
        resp = client.post("/api/v1/auth/login", json={"profile_id": "nobody", "pin": "1234"})
        assert resp.status_code == 404
        assert resp.json()["error"] == "profile_not_found"

    def test_pin_of_other_profile_rejected(self, client):
        resp = client.post("/api/v1/auth/login", json={"profile_id": "default", "pin": "5678"})
        assert resp.status_code == 401

    def test_profile_without_pin(self, client, store):
        store.create_profile("tom", "Tom", pin="")
        resp = client.post("/api/v1/auth/login", json={"profile_id": "tom"})
        assert resp.status_code == 200
        assert resp.json()["profile"]["id"] == "tom"

    def test_login_is_rate_limited(self, client):
        limiter.enabled = True
        limiter.reset()
        codes = [
            client.post("/api/v1/auth/login", json={"profile_id": "default", "pin": "0000"}).status_code
            for _ in range(6)
        ]
        limiter.reset()
        assert codes[:5] == [401] * 5
        assert codes[5] == 429

    def test_token_stored_hashed(self, client, store):
        token = _token(client)
        rows = store.conn.execute("SELECT token_hash, device_name FROM device_tokens").fetchall()
        assert len(rows) == 1
        assert rows[0]["token_hash"] != token
        assert token not in rows[0]["token_hash"]
        assert rows[0]["device_name"] == "Shield"


class TestBearer:
    def test_me_with_token(self, client):
        token = _token(client, "bob", "5678")
        resp = client.get("/api/v1/me", headers=_auth(token))
        assert resp.status_code == 200
        assert resp.json()["profile"]["id"] == "bob"

    def test_bearer_response_sets_no_cookie(self, store):
        c = AppClient(_create_test_app(store))
        token = _token(c)
        resp = c.get("/api/v1/me", headers=_auth(token))
        assert resp.status_code == 200
        assert "set-cookie" not in resp.headers

    def test_no_token_is_unauthorized(self, client):
        resp = client.get("/api/v1/me")
        assert resp.status_code == 401

    def test_invalid_token(self, client):
        resp = client.get("/api/v1/me", headers=_auth("not-a-real-token"))
        assert resp.status_code == 401
        assert resp.json()["error"] == "invalid_token"

    def test_invalid_token_does_not_fall_back_to_cookie(self, store):
        c = AppClient(_create_test_app(store))
        _cookie_login(c, "default", "1234")
        assert c.get("/api/v1/me").status_code == 200
        resp = c.get("/api/v1/me", headers=_auth("revoked-or-bogus"))
        assert resp.status_code == 401

    def test_logout_revokes_token(self, client):
        token = _token(client)
        assert client.post("/api/v1/auth/logout", headers=_auth(token)).json() == {"ok": True}
        assert client.get("/api/v1/me", headers=_auth(token)).status_code == 401

    def test_logout_without_token(self, client, store):
        c = AppClient(_create_test_app(store))
        _cookie_login(c, "default", "1234")
        assert c.post("/api/v1/auth/logout").status_code == 400

    def test_expired_token_rejected(self, client, store):
        token = _token(client)
        store.conn.execute("UPDATE device_tokens SET expires_at = datetime('now', '-1 minute')")
        store.conn.commit()
        assert client.get("/api/v1/me", headers=_auth(token)).status_code == 401

    def test_deleted_profile_invalidates_token(self, client, store):
        token = _token(client, "bob", "5678")
        store.delete_profile("bob")
        assert client.get("/api/v1/me", headers=_auth(token)).status_code == 401
        assert store.list_device_tokens("bob") == []

    def test_token_works_on_existing_api_and_isolates_profiles(self, client, store):
        alice = ChildStore(store, "default")
        alice.add_video("alicevid001", "Alice Video", "Chan A", duration=60)
        alice.update_status("alicevid001", "approved")
        bob = ChildStore(store, "bob")
        bob.add_video("bobvideo001", "Bob Video", "Chan B", duration=60)
        bob.update_status("bobvideo001", "approved")

        token = _token(client, "bob", "5678")
        resp = client.get("/api/catalog", headers=_auth(token))
        assert resp.status_code == 200
        ids = {v["video_id"] for v in resp.json()["videos"]}
        assert "bobvideo001" in ids
        assert "alicevid001" not in ids


class TestStoreTokens:
    def test_list_and_revoke_by_id(self, store):
        store.create_device_token("default", "TV salon")
        token_b, _ = store.create_device_token("bob", "TV chambre")
        live = store.list_device_tokens()
        assert {t["device_name"] for t in live} == {"TV salon", "TV chambre"}
        assert all("token_hash" not in t for t in live)

        bob_row = store.list_device_tokens("bob")[0]
        assert store.revoke_device_token_by_id(bob_row["id"]) is True
        assert store.revoke_device_token_by_id(bob_row["id"]) is False
        assert store.resolve_device_token(token_b) is None
        assert [t["device_name"] for t in store.list_device_tokens()] == ["TV salon"]

    def test_resolve_updates_last_used(self, store):
        token, _ = store.create_device_token("default")
        assert store.list_device_tokens()[0]["last_used_at"] is None
        assert store.resolve_device_token(token)["id"] == "default"
        assert store.list_device_tokens()[0]["last_used_at"] is not None


def _seed_catalog(store, app_state):
    """Two allowed channels (edu + fun) in the channel cache, one approved Short."""
    from web.cache import get_profile_cache
    store.add_channel("Sci", "allowed", channel_id="UCsci", category="edu")
    store.add_channel("Toys", "allowed", channel_id="UCtoys", category="fun")
    cache = get_profile_cache(app_state, "default")
    cache["channels"] = {
        "UCsci": [{"video_id": f"scivid{i:05d}", "title": f"Sci {i}", "channel_name": "Sci",
                   "channel_id": "UCsci", "duration": 300} for i in range(30)],
        "UCtoys": [{"video_id": "toysvid0001", "title": "Toys 1", "channel_name": "Toys",
                    "channel_id": "UCtoys", "duration": 120, "is_short": False}],
    }
    cache["id_to_name"] = {"UCsci": "Sci", "UCtoys": "Toys"}
    cache["updated_at"] = 1.0
    cs = ChildStore(store, "default")
    cs.add_video("activevid01", "Active One", "Toys", channel_id="UCtoys", duration=200)
    cs.update_status("activevid01", "approved")
    cs.add_video("shortvid001", "A Short", "Toys", channel_id="UCtoys", duration=30, is_short=True)
    cs.update_status("shortvid001", "approved")


class TestHome:
    def test_requires_auth(self, client):
        assert client.get("/api/v1/home").status_code == 401

    def test_rows_and_channels(self, client, store):
        _seed_catalog(store, client.app.state)
        token = _token(client)
        body = client.get("/api/v1/home?limit=10", headers=_auth(token)).json()
        rows = {r["id"]: r for r in body["rows"]}
        assert [r["id"] for r in body["rows"]] == ["active", "edu", "fun"]
        assert body["shorts_enabled"] is False
        assert rows["active"]["videos"][0]["video_id"] == "activevid01"
        assert rows["edu"]["total"] == 30 and len(rows["edu"]["videos"]) == 10
        assert rows["edu"]["has_more"] is True
        assert all(v["category"] == "edu" for v in rows["edu"]["videos"])
        assert {v["video_id"] for v in rows["fun"]["videos"]} >= {"toysvid0001", "activevid01"}
        card = rows["edu"]["videos"][0]
        assert set(card) == {"video_id", "title", "channel_name", "channel_id", "duration",
                             "category", "is_short", "progress_seconds", "thumbnail"}
        assert card["thumbnail"] == f"/thumb/{card['video_id']}"
        assert [c["name"] for c in body["channels"]] == ["Sci", "Toys"]

    def test_shorts_row_when_enabled(self, client, store):
        _seed_catalog(store, client.app.state)
        ChildStore(store, "default").set_setting("shorts_enabled", "true")
        token = _token(client)
        body = client.get("/api/v1/home", headers=_auth(token)).json()
        assert body["shorts_enabled"] is True
        shorts = next(r for r in body["rows"] if r["id"] == "shorts")
        assert shorts["videos"][0]["is_short"] is True

    def test_other_profile_sees_its_own_home(self, client, store):
        _seed_catalog(store, client.app.state)
        token = _token(client, "bob", "5678")
        body = client.get("/api/v1/home", headers=_auth(token)).json()
        assert body["rows"] == []
        assert body["channels"] == []


class TestCatalogV1:
    def test_pagination(self, client, store):
        _seed_catalog(store, client.app.state)
        token = _token(client)
        page = client.get("/api/v1/catalog?row=edu&offset=20&limit=10", headers=_auth(token)).json()
        assert len(page["videos"]) == 10 and page["has_more"] is False and page["total"] == 30

    def test_channel_filter(self, client, store):
        _seed_catalog(store, client.app.state)
        token = _token(client)
        page = client.get("/api/v1/catalog?channel=UCtoys", headers=_auth(token)).json()
        assert {v["channel_id"] for v in page["videos"]} == {"UCtoys"}

    def test_shorts_hidden_when_disabled(self, client, store):
        _seed_catalog(store, client.app.state)
        token = _token(client)
        page = client.get("/api/v1/catalog?row=shorts", headers=_auth(token)).json()
        assert page == {"videos": [], "total": 0, "has_more": False}

    def test_bad_row(self, client):
        token = _token(client)
        assert client.get("/api/v1/catalog?row=nope", headers=_auth(token)).status_code == 422
