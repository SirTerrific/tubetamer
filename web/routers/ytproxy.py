"""YouTube proxy routes: iframe API, widget API, and thumbnails.

Everything here exists so client devices (kid tablets) never need direct
network access to Google/YouTube — the server fetches on their behalf.
"""

import logging
import re
from pathlib import Path

import httpx
from fastapi import APIRouter, Request
from fastapi.responses import FileResponse, PlainTextResponse, Response

from web.cache import fetch_yt_scripts, yt_cache_stale

logger = logging.getLogger(__name__)

router = APIRouter()

_VIDEO_ID_RE = re.compile(r"^[a-zA-Z0-9_-]{11}$")
# hqdefault always exists; hq1-3 are the auto-generated preview frames
_THUMB_VARIANTS = {"default", "mqdefault", "hqdefault", "sddefault", "maxresdefault", "hq1", "hq2", "hq3"}


@router.get("/api/yt-iframe-api.js")
async def yt_iframe_api_proxy(request: Request):
    """Proxy the YouTube IFrame API loader with widget URL rewritten to local."""
    state = request.app.state
    if getattr(state, "yt_iframe_api_cache", None) is None or yt_cache_stale(state):
        await fetch_yt_scripts(state)
    if not getattr(state, "yt_iframe_api_cache", None):
        return PlainTextResponse("// iframe API unavailable", media_type="application/javascript")
    return PlainTextResponse(state.yt_iframe_api_cache, media_type="application/javascript")


@router.get("/api/yt-widget-api.js")
async def yt_widget_api_proxy(request: Request):
    """Proxy the YouTube widget API script."""
    state = request.app.state
    if getattr(state, "yt_widget_api_cache", None) is None or yt_cache_stale(state):
        await fetch_yt_scripts(state)
    if not getattr(state, "yt_widget_api_cache", None):
        return PlainTextResponse("// widget API unavailable", media_type="application/javascript")
    return PlainTextResponse(state.yt_widget_api_cache, media_type="application/javascript")


def _thumb_dir(request: Request) -> Path:
    return Path(getattr(request.app.state, "thumb_dir", "db/thumbs"))


@router.get("/thumb/{video_id}")
@router.get("/thumb/{video_id}/{variant}")
async def thumbnail_proxy(request: Request, video_id: str, variant: str = "hqdefault"):
    """Serve YouTube thumbnails from a server-side disk cache.

    The tablet requests /thumb/<id>; the server (which has YouTube access)
    fetches from i.ytimg.com once and caches forever — thumbnails for a
    given video never change.
    """
    if not _VIDEO_ID_RE.match(video_id) or variant not in _THUMB_VARIANTS:
        return Response(status_code=404)

    cache_dir = _thumb_dir(request)
    cached = cache_dir / f"{video_id}_{variant}.jpg"
    negative = cache_dir / f"{video_id}_{variant}.404"

    if negative.exists():
        return Response(status_code=404)

    if not cached.exists():
        cache_dir.mkdir(parents=True, exist_ok=True)
        # maxresdefault is missing for many videos — fall back to hqdefault
        candidates = [variant] if variant != "maxresdefault" else ["maxresdefault", "hqdefault"]
        content = None
        try:
            async with httpx.AsyncClient(timeout=10, follow_redirects=True) as client:
                for name in candidates:
                    resp = await client.get(f"https://i.ytimg.com/vi/{video_id}/{name}.jpg")
                    if resp.status_code == 200 and resp.content:
                        content = resp.content
                        break
        except httpx.HTTPError as exc:
            logger.warning("Thumbnail fetch failed for %s/%s: %s", video_id, variant, exc)
            return Response(status_code=502)
        if content is None:
            # negative cache: don't re-hit ytimg for variants that don't exist
            negative.touch()
            return Response(status_code=404)
        tmp = cached.with_suffix(".tmp")
        tmp.write_bytes(content)
        tmp.replace(cached)

    return FileResponse(
        cached,
        media_type="image/jpeg",
        headers={"Cache-Control": "public, max-age=604800, immutable"},
    )
