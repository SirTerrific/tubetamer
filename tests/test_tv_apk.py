"""/app/tubetamer.apk: the Android TV app, downloadable without a session."""

import pytest

from data.video_store import VideoStore
from tests.test_web_integration import AppClient, _create_test_app


@pytest.fixture
def store(tmp_path):
    s = VideoStore(db_path=str(tmp_path / "test.db"))
    s.create_profile("default", "Alice", pin="1234")
    yield s
    s.close()


@pytest.fixture
def client(store):
    return AppClient(_create_test_app(store), raise_server_exceptions=False)


def test_missing_apk_is_404(client, tmp_path):
    client.app.state.web_config.tv_apk = str(tmp_path / "nope.apk")
    resp = client.get("/app/tubetamer.apk", follow_redirects=False)
    assert resp.status_code == 404


def test_serves_apk_without_login(client, tmp_path):
    apk = tmp_path / "tubetamer.apk"
    apk.write_bytes(b"PK\x03\x04fake-apk")
    client.app.state.web_config.tv_apk = str(apk)
    resp = client.get("/app/tubetamer.apk", follow_redirects=False)
    assert resp.status_code == 200
    assert resp.content == b"PK\x03\x04fake-apk"
    assert resp.headers["content-type"] == "application/vnd.android.package-archive"
    assert "tubetamer.apk" in resp.headers["content-disposition"]


def test_head_supported(client, tmp_path):
    apk = tmp_path / "tubetamer.apk"
    apk.write_bytes(b"PK")
    client.app.state.web_config.tv_apk = str(apk)
    resp = client.request("HEAD", "/app/tubetamer.apk", follow_redirects=False)
    assert resp.status_code == 200


def test_other_app_paths_still_need_login(client):
    resp = client.get("/app/other.apk", follow_redirects=False)
    assert resp.status_code in (303, 401, 404)
    assert resp.status_code != 200


def test_default_path():
    from config import WebConfig
    assert WebConfig().tv_apk == "db/tubetamer.apk"
