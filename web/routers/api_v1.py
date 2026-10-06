"""JSON API for native clients (Android TV), under /api/v1.

Auth: `POST /api/v1/auth/login` trades a profile id + PIN for a bearer token.
Every other v1 call sends `Authorization: Bearer <token>`; PinAuthMiddleware
resolves it to the profile. Browser routes and cookie sessions are unchanged.
"""

import hashlib
import hmac

from fastapi import APIRouter, Query, Request
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field

from version import __version__
from web.cache import build_active_row, build_catalog, build_shorts_catalog, get_profile_cache
from web.deps import get_child_store
from web.helpers import (
    VIDEO_ID_RE, HeartbeatRequest, get_category_time_info, get_next_start_time,
    get_schedule_info, get_time_limit_info, localize_titles, resolve_video_category, shorts_enabled,
)
from web.middleware import bearer_token
from web.routers.watch import get_or_auto_approve, process_heartbeat
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


# --- Playback -----------------------------------------------------------------

def _watch_key(request: Request) -> str | None:
    """Per-token key for the video being watched (bearer calls have no lasting session)."""
    token = getattr(request.state, "device_token", None)
    return "t:" + hashlib.sha256(token.encode()).hexdigest() if token else None


def _set_watching(request: Request, video_id: str) -> None:
    key = _watch_key(request)
    if key is None:
        request.session["watching"] = video_id
        return
    state = request.app.state
    if not hasattr(state, "native_watching"):
        state.native_watching = {}
    state.native_watching[key] = video_id


def _get_watching(request: Request) -> str | None:
    key = _watch_key(request)
    if key is None:
        return request.session.get("watching")
    return getattr(request.app.state, "native_watching", {}).get(key)


def _time_status(cs, wl_cfg) -> dict:
    """Budgets and schedule for the profile, as the TV shows them."""
    cat_info = get_category_time_info(store=cs, wl_cfg=wl_cfg)
    return {
        "categories": cat_info["categories"] if cat_info else None,
        "daily": None if cat_info else get_time_limit_info(store=cs, wl_cfg=wl_cfg),
        "schedule": get_schedule_info(store=cs, wl_cfg=wl_cfg),
        "next_start": get_next_start_time(store=cs, wl_cfg=wl_cfg),
    }


def _gate(cs, wl_cfg, video: dict) -> tuple[dict | None, int]:
    """(block, remaining_sec) for playing `video` now. Same rules as /watch:
    category budget (or global budget), then the schedule window.
    remaining_sec is -1 when no limit applies."""
    cat = resolve_video_category(video, store=cs)
    ts = _time_status(cs, wl_cfg)
    remaining = -1
    if ts["categories"]:
        budget = ts["categories"].get(cat, {})
        if budget.get("exceeded"):
            available = [
                {"category": c, "remaining_min": info["remaining_min"]}
                for c, info in ts["categories"].items() if c != cat and not info["exceeded"]
            ]
            return {"error": "time_up", "category": cat, "next_start": ts["next_start"],
                    "available": available}, 0
        if budget.get("limit_min", 0) > 0:
            remaining = budget["remaining_sec"]
    elif ts["daily"]:
        if ts["daily"]["exceeded"]:
            return {"error": "time_up", "category": "", "next_start": ts["next_start"],
                    "available": []}, 0
        remaining = ts["daily"]["remaining_sec"]
    sched = ts["schedule"]
    if sched and not sched["allowed"]:
        return {"error": "outside_schedule", "unlock_time": sched["unlock_time"],
                "start": sched["start"], "end": sched["end"]}, remaining
    return None, remaining


@router.get("/time")
@limiter.limit("30/minute")
async def api_time(request: Request):
    """Remaining budgets and the schedule window for the current profile."""
    return _time_status(get_child_store(request), request.app.state.wl_config)


@router.post("/videos/{video_id}/play")
@limiter.limit("30/minute")
async def api_play(request: Request, video_id: str):
    """Start watching a video.

    200 {"status": "ready", stream, subtitles, resume_seconds, remaining_sec}
    202 {"status": "pending"|"downloading"}: download queued; poll
        /api/download-status/{id}, then call this again.
    403 not_approved | time_up | outside_schedule, 404 not_found,
    409 local_playback_disabled (the TV only plays files from the server).
    """
    if not VIDEO_ID_RE.match(video_id):
        return JSONResponse({"error": "not_found"}, status_code=404)
    state = request.app.state
    cs = get_child_store(request)
    video = await get_or_auto_approve(request, cs, video_id)
    if not video:
        return JSONResponse({"error": "not_found"}, status_code=404)
    if video["status"] != "approved":
        return JSONResponse({"error": "not_approved", "status": video["status"]}, status_code=403)

    block, remaining = _gate(cs, state.wl_config, video)
    if block:
        return JSONResponse(block, status_code=403)

    if not getattr(state, "local_playback_enabled", False):
        return JSONResponse({"error": "local_playback_disabled"}, status_code=409)
    downloader = getattr(state, "video_downloader", None)
    if not downloader or not downloader.is_downloaded(video_id):
        raw = cs.get_download_status(video_id)
        dl_cb = getattr(state, "download_on_approve", None)
        if dl_cb and raw not in ("downloading", "ready"):
            await dl_cb(video_id, cs.profile_id)
        return JSONResponse({"status": "downloading" if raw == "downloading" else "pending"},
                            status_code=202)

    cs.record_view(video_id)
    _set_watching(request, video_id)
    localize_titles(request, [video])
    return {
        "status": "ready",
        "video": _card(video),
        "stream": f"/api/stream/{video_id}",
        "subtitles": [
            {"lang": s["lang"], "label": s["label"], "url": s["url"]}
            for s in downloader.subtitle_files(video_id)
        ],
        "resume_seconds": int(video.get("resume_seconds") or 0),
        "remaining_sec": remaining,
    }


@router.post("/heartbeat")
@limiter.limit("30/minute")
async def api_heartbeat(request: Request, body: HeartbeatRequest):
    """Watch time report from a native player, ~every 30 s while playing.

    Same accounting as /api/watch-heartbeat. `time_up` is true once the budget
    covering this video is used up: the client stops playback.
    """
    vid = body.video_id
    if not VIDEO_ID_RE.match(vid):
        return JSONResponse({"error": "invalid"}, status_code=400)
    if _get_watching(request) != vid:
        return JSONResponse({"error": "not_watching"}, status_code=409)
    seconds = min(max(body.seconds, 0), 60)
    resp, status = await process_heartbeat(request, vid, seconds, body.position_seconds)
    if status == 200:
        resp["time_up"] = resp["remaining"] == 0
    return JSONResponse(resp, status_code=status)
