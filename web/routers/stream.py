"""Video streaming routes: serve locally downloaded videos with HTTP Range support."""

import logging
import os
import re
from pathlib import Path

from fastapi import APIRouter, Request
from fastapi.responses import FileResponse, JSONResponse, Response
from starlette.status import HTTP_404_NOT_FOUND

from web.shared import limiter
from web.deps import get_child_store

logger = logging.getLogger(__name__)

router = APIRouter()

_VIDEO_ID_RE = re.compile(r'^[a-zA-Z0-9_-]{11}\Z')


def _blocked(request: Request, cs, video: dict):
    """403 when the time budget or schedule blocks playback (same gate as /watch)."""
    from web.routers.api_v1 import _gate
    block, _ = _gate(cs, request.app.state.wl_config, video)
    return JSONResponse(block, status_code=403) if block else None


def _get_downloader(request: Request):
    """Get the video downloader from app state."""
    return getattr(request.app.state, "video_downloader", None)


@router.get("/api/stream/{video_id}")
@limiter.limit("60/minute")
async def stream_video(request: Request, video_id: str):
    """Stream a locally downloaded video with Range support for seeking.

    Security:
    - video_id validated by regex (no path traversal)
    - Only serves files for approved videos
    - Files served from a fixed directory (no user-controlled paths)
    """
    # Validate video ID strictly
    if not _VIDEO_ID_RE.match(video_id):
        return Response(status_code=HTTP_404_NOT_FOUND)

    # Check video is approved for this profile
    cs = get_child_store(request)
    video = cs.get_video(video_id)
    if not video or video["status"] != "approved":
        return Response(status_code=HTTP_404_NOT_FOUND)
    block = _blocked(request, cs, video)
    if block:
        return block

    # Get downloader and verify file exists
    downloader = _get_downloader(request)
    if not downloader:
        return Response(status_code=HTTP_404_NOT_FOUND)

    file_path = downloader.video_path(video_id)
    if not file_path.is_file():
        return Response(status_code=HTTP_404_NOT_FOUND)

    # Verify the resolved path is within the video directory (defense in depth)
    try:
        resolved = file_path.resolve()
        video_dir_resolved = downloader.video_dir.resolve()
        if not str(resolved).startswith(str(video_dir_resolved)):
            logger.warning("Path traversal attempt blocked: %s", video_id)
            return Response(status_code=HTTP_404_NOT_FOUND)
    except (OSError, ValueError):
        return Response(status_code=HTTP_404_NOT_FOUND)

    # FileResponse handles Range (open-ended, suffix, multi-range), 416, and reads
    # the file off the event loop.
    return FileResponse(
        file_path,
        media_type="video/mp4",
        headers={
            "Cache-Control": "private, max-age=3600",
            "X-Content-Type-Options": "nosniff",
        },
    )


@router.get("/api/download-status/{video_id}")
@limiter.limit("30/minute")
async def download_status(request: Request, video_id: str):
    """Check the download status of a video."""
    from fastapi.responses import JSONResponse

    if not _VIDEO_ID_RE.match(video_id):
        return JSONResponse({"status": "not_found"})

    cs = get_child_store(request)
    video = cs.get_video(video_id)
    if not video or video["status"] != "approved":
        return JSONResponse({"status": "not_found"})

    downloader = _get_downloader(request)
    if not downloader:
        return JSONResponse({"status": "disabled"})

    if downloader.is_downloaded(video_id):
        return JSONResponse({"status": "ready"})

    dl_status = cs.get_download_status(video_id)
    result = {"status": dl_status or "not_found"}

    # Include progress data when downloading
    if dl_status == "downloading":
        progress = downloader.get_progress(video_id)
        if progress:
            result["percent"] = progress.get("percent", 0)
            result["downloaded"] = progress.get("downloaded", "")
            result["total"] = progress.get("total", "")
            result["speed"] = progress.get("speed", "")
            result["eta"] = progress.get("eta", 0)
            result["phase"] = progress.get("status", "downloading")

    return JSONResponse(result)


_LANG_RE = re.compile(r'^[a-zA-Z]{2,3}(-[a-zA-Z0-9]+)?$')


@router.get("/api/subs/{video_id}/{lang}")
@limiter.limit("60/minute")
async def serve_subtitle(request: Request, video_id: str, lang: str):
    """Serve a WebVTT subtitle file."""
    if not _VIDEO_ID_RE.match(video_id) or not _LANG_RE.match(lang):
        return Response(status_code=HTTP_404_NOT_FOUND)

    cs = get_child_store(request)
    video = cs.get_video(video_id)
    if not video or video["status"] != "approved":
        return Response(status_code=HTTP_404_NOT_FOUND)
    block = _blocked(request, cs, video)
    if block:
        return block

    downloader = _get_downloader(request)
    if not downloader:
        return Response(status_code=HTTP_404_NOT_FOUND)

    sub_path = downloader.subs_dir / f"{video_id}.{lang}.vtt"
    if not sub_path.is_file():
        return Response(status_code=HTTP_404_NOT_FOUND)

    # Path traversal defense
    try:
        if not str(sub_path.resolve()).startswith(str(downloader.subs_dir.resolve())):
            return Response(status_code=HTTP_404_NOT_FOUND)
    except (OSError, ValueError):
        return Response(status_code=HTTP_404_NOT_FOUND)

    return Response(
        content=sub_path.read_bytes(),
        media_type="text/vtt",
        headers={
            "Cache-Control": "private, max-age=3600",
            "X-Content-Type-Options": "nosniff",
        },
    )
