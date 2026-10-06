"""Cross-cutting checks for every bearer-protected /api/v1 endpoint."""

import logging

import pytest

from tests.test_api_v1 import _auth, _token, client, store  # noqa: F401 (fixtures)
from web.shared import limiter

VID = "dQw4w9WgXcQ"

# (method, path, json body) for every endpoint that needs a token.
PROTECTED = [
    ("GET", "/api/v1/me", None),
    ("GET", "/api/v1/home", None),
    ("GET", "/api/v1/catalog?row=all", None),
    ("GET", "/api/v1/time", None),
    ("GET", "/api/v1/search?q=cats", None),
    ("GET", "/api/v1/requests", None),
    ("POST", "/api/v1/requests", {"video_id": VID}),
    ("POST", f"/api/v1/videos/{VID}/play", None),
    ("POST", "/api/v1/heartbeat", {"video_id": VID, "seconds": 30, "position_seconds": 0}),
    ("GET", f"/api/stream/{VID}", None),
    ("GET", f"/api/download-status/{VID}", None),
]


@pytest.fixture(autouse=True)
def _no_rate_limit():
    limiter.enabled = False
    yield
    limiter.enabled = True


def _call(client, method, path, body, headers=None):
    if method == "GET":
        return client.get(path, headers=headers or {})
    return client.post(path, json=body or {}, headers=headers or {})


@pytest.mark.parametrize("method,path,body", PROTECTED)
def test_without_token(client, method, path, body):
    assert _call(client, method, path, body).status_code == 401


@pytest.mark.parametrize("method,path,body", PROTECTED)
def test_revoked_token(client, method, path, body):
    token = _token(client)
    assert client.post("/api/v1/auth/logout", headers=_auth(token)).status_code == 200
    resp = _call(client, method, path, body, _auth(token))
    assert resp.status_code == 401


@pytest.mark.parametrize("method,path,body", PROTECTED)
def test_garbage_token(client, method, path, body):
    resp = _call(client, method, path, body, {"Authorization": "Bearer not-a-real-token"})
    assert resp.status_code == 401


def test_token_never_logged(client, caplog):
    caplog.set_level(logging.DEBUG)
    token = _token(client)
    client.get("/api/v1/me", headers=_auth(token))
    client.get("/api/v1/home", headers=_auth(token))
    client.post("/api/v1/auth/logout", headers=_auth(token))
    client.get("/api/v1/me", headers=_auth(token))
    assert token not in caplog.text


def test_token_not_in_login_response_headers(client):
    resp = client.post("/api/v1/auth/login",
                       json={"profile_id": "default", "pin": "1234", "device_name": "TV"})
    token = resp.json()["token"]
    assert all(token not in v for v in resp.headers.values())
    assert "set-cookie" not in resp.headers
