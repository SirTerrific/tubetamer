"""Catalog API route: paginated video listing."""

from fastapi import APIRouter, Request, Query
from fastapi.responses import JSONResponse

from web.shared import limiter
from web.helpers import VIDEO_ID_RE
from web.cache import build_active_row, build_catalog, build_shorts_catalog, get_profile_cache

router = APIRouter()


@router.get("/api/catalog")
@limiter.limit("90/minute")
async def api_catalog(
    request: Request,
    offset: int = Query(0, ge=0),
    limit: int = Query(24, ge=1, le=100),
    channel: str = Query("", max_length=200),
    category: str = Query("", max_length=10),
    dismissed: str = Query("", max_length=4000),
    shorts: bool = Query(False),
    active: bool = Query(False),
    requests: bool = Query(False),
):
    """Paginated catalog of all watchable videos for the current profile."""
    state = request.app.state
    profile_id = request.session.get("child_id", "default")
    if active or requests:
        full = build_active_row(state, limit=200, profile_id=profile_id)
        if channel:
            full = [
                v for v in full
                if v.get("channel_id") == channel or v.get("channel_name") == channel
            ]
        if dismissed:
            dismissed_ids = {item for item in dismissed.split(",") if item and VIDEO_ID_RE.match(item)}
            if dismissed_ids:
                full = [v for v in full if v.get("video_id") not in dismissed_ids]
    elif shorts:
        full = build_shorts_catalog(state, profile_id=profile_id)
    else:
        full = build_catalog(state, channel_filter=channel, profile_id=profile_id)
    if category:
        full = [v for v in full if v.get("category", "fun") == category]
    page = full[offset:offset + limit]
    return JSONResponse({
        "videos": page,
        "has_more": offset + limit < len(full),
        "total": len(full),
    })


@router.get("/api/catalog/status")
@limiter.limit("60/minute")
async def api_catalog_status(request: Request):
    """Lightweight status for homepage cache refresh polling."""
    state = request.app.state
    profile_id = request.session.get("child_id", "default")
    cache = get_profile_cache(state, profile_id)
    channels = cache.get("channels", {})
    return JSONResponse({
        "updated_at": cache.get("updated_at", 0.0),
        "channel_count": len(channels),
        "ready_channel_count": sum(1 for vids in channels.values() if vids),
    })
