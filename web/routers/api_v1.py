"""JSON API for native clients (Android TV), under /api/v1.

Auth: `POST /api/v1/auth/login` trades a profile id + PIN for a bearer token.
Every other v1 call sends `Authorization: Bearer <token>`; PinAuthMiddleware
resolves it to the profile. Browser routes and cookie sessions are unchanged.
"""

import hmac

from fastapi import APIRouter, Query, Request
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field

from version import __version__
from web.cache import build_active_row, build_catalog, build_shorts_catalog, get_profile_cache
from web.deps import get_child_store
from web.helpers import localize_titles, shorts_enabled
from web.middleware import bearer_token
from web.shared import limiter

router = APIRouter(prefix="/api/v1")

API_VERSION = 1

# Home rows, in display order. "active" = approved videos not finished yet
# (same as the web homepage's Active row); edu/fun split the channel catalog.
ROW_IDS = ("active", "edu", "fun", "shorts")
_ACTIVE_MAX = 200


def _card(v: dict) -> dict:
    """Stable video card for clients. Only fields the TV needs, no internals."""
    vid = v.get("video_id", "")
    return {
        "video_id": vid,
        "title": v.get("title") or "",
        "channel_name": v.get("channel_name") or "",
        "channel_id": v.get("channel_id") or "",
        "duration": int(v.get("duration") or 0),
        "category": v.get("category") or "fun",
        "is_short": bool(v.get("is_short")),
        "progress_seconds": int(v.get("progress_seconds") or 0),
        "thumbnail": f"/thumb/{vid}",
    }


def _row_videos(request: Request, row: str, channel: str = "") -> list[dict]:
    """Full, filtered video list behind one row, for the current profile."""
    state = request.app.state
    profile_id = get_child_store(request).profile_id
    if row == "active":
        return build_active_row(state, limit=_ACTIVE_MAX, profile_id=profile_id, channel_filter=channel)
    if row == "shorts":
        shorts = build_shorts_catalog(state, profile_id=profile_id)
        if channel:
            shorts = [v for v in shorts if channel in (v.get("channel_id"), v.get("channel_name"))]
        return shorts
    catalog = build_catalog(state, channel_filter=channel, profile_id=profile_id)
    if row in ("edu", "fun"):
        catalog = [v for v in catalog if v.get("category", "fun") == row]
    return catalog


def _page(request: Request, videos: list[dict], offset: int, limit: int) -> dict:
    page = localize_titles(request, [dict(v) for v in videos[offset:offset + limit]])
    return {
        "videos": [_card(v) for v in page],
        "total": len(videos),
        "has_more": offset + limit < len(videos),
    }


def _public_profile(p: dict) -> dict:
    """Profile fields safe to send to a client. Never includes the PIN."""
    return {
        "id": p["id"],
        "display_name": p["display_name"],
        "avatar_icon": p.get("avatar_icon") or "",
        "avatar_color": p.get("avatar_color") or "",
    }


@router.get("/info")
async def api_info(request: Request):
    """Server discovery, used by the client's connection test. No auth."""
    state = request.app.state
    return {
        "app": "tubetamer",
        "version": __version__,
        "api_version": API_VERSION,
        "local_playback": bool(getattr(state, "local_playback_enabled", False)),
        "locale": getattr(state, "locale", None) or "en",
    }


@router.get("/profiles")
async def api_profiles(request: Request):
    """Profiles for the picker. No auth, no PINs (the web /login page shows the same)."""
    vs = request.app.state.video_store
    profiles = vs.get_profiles() if vs else []
    return {
        "profiles": [
            {**_public_profile(p), "has_pin": bool(p["pin"])} for p in profiles
        ]
    }


class LoginBody(BaseModel):
    profile_id: str = Field(min_length=1, max_length=50)
    pin: str = Field("", max_length=20)
    device_name: str = Field("", max_length=100)


@router.post("/auth/login")
@limiter.limit("5/hour")
async def api_login(request: Request, body: LoginBody):
    """Check the PIN and issue a bearer token for the profile."""
    vs = request.app.state.video_store
    profile = vs.get_profile(body.profile_id) if vs else None
    if not profile:
        return JSONResponse({"error": "profile_not_found"}, status_code=404)

    stored_pin = profile["pin"] or ""
    if stored_pin and not (body.pin and hmac.compare_digest(body.pin, stored_pin)):
        return JSONResponse({"error": "invalid_pin"}, status_code=401)

    token, expires_at = vs.create_device_token(profile["id"], body.device_name.strip())
    return {
        "token": token,
        "token_type": "bearer",
        "expires_at": expires_at,
        "profile": _public_profile(profile),
    }


@router.post("/auth/logout")
async def api_logout(request: Request):
    """Revoke the token used for this call."""
    token = bearer_token(request)
    if not token:
        return JSONResponse({"error": "no_token"}, status_code=400)
    request.app.state.video_store.revoke_device_token(token)
    return {"ok": True}


@router.get("/me")
async def api_me(request: Request):
    """Profile behind the current token (or cookie session). Lets a client check its token."""
    vs = request.app.state.video_store
    child_id = request.session.get("child_id", "default")
    profile = vs.get_profile(child_id) if vs else None
    if not profile:
        return JSONResponse({"error": "unauthorized"}, status_code=401)
    return {"profile": _public_profile(profile)}


@router.get("/home")
@limiter.limit("60/minute")
async def api_home(request: Request, limit: int = Query(24, ge=1, le=100)):
    """Home screen: first page of each non-empty row, plus the channel list.

    Rows come back in ROW_IDS order; the client labels them by id. Shorts only
    appear when they are enabled for the profile.
    """
    cs = get_child_store(request)
    show_shorts = shorts_enabled(request, cs)
    rows = []
    for row_id in ROW_IDS:
        if row_id == "shorts" and not show_shorts:
            continue
        videos = _row_videos(request, row_id)
        if videos:
            rows.append({"id": row_id, **_page(request, videos, 0, limit)})

    cache = get_profile_cache(request.app.state, cs.profile_id)
    id_to_name = cache.get("id_to_name", {})
    channels = [
        {"id": key, "name": id_to_name.get(key, key), "video_count": len(vids)}
        for key, vids in cache.get("channels", {}).items()
    ]
    channels.sort(key=lambda c: c["name"].casefold())
    return {
        "rows": rows,
        "channels": channels,
        "shorts_enabled": show_shorts,
    }


@router.get("/catalog")
@limiter.limit("90/minute")
async def api_v1_catalog(
    request: Request,
    row: str = Query("all", pattern="^(all|active|edu|fun|shorts)$"),
    channel: str = Query("", max_length=200),
    offset: int = Query(0, ge=0),
    limit: int = Query(24, ge=1, le=100),
):
    """Next pages of a home row, or of one channel (row=all&channel=<id>)."""
    if row == "shorts" and not shorts_enabled(request, get_child_store(request)):
        return {"videos": [], "total": 0, "has_more": False}
    return _page(request, _row_videos(request, row, channel), offset, limit)
