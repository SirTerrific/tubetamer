"""Video streaming routes: serve locally downloaded videos with HTTP Range support."""

import logging
import os
import re
from pathlib import Path

from fastapi import APIRouter, Request
from fastapi.responses import Response, StreamingResponse
from starlette.status import HTTP_200_OK, HTTP_206_PARTIAL_CONTENT, HTTP_404_NOT_FOUND, HTTP_416_REQUESTED_RANGE_NOT_SATISFIABLE

from web.shared import limiter
from web.deps import get_child_store

logger = logging.getLogger(__name__)

router = APIRouter()

_VIDEO_ID_RE = re.compile(r'^[a-zA-Z0-9_-]{11}$')
_RANGE_RE = re.compile(r'^bytes=(\d+)-(\d*)$')
_CHUNK_SIZE = 1024 * 1024  # 1 MB chunks


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

    file_size = file_path.stat().st_size
    headers = {
        "Accept-Ranges": "bytes",
        "Content-Type": "video/mp4",
        "Cache-Control": "private, max-age=3600",
        "X-Content-Type-Options": "nosniff",
    }

    # Parse Range header
    range_header = request.headers.get("range")
    if range_header:
        match = _RANGE_RE.match(range_header)
        if not match:
            return Response(
                status_code=HTTP_416_REQUESTED_RANGE_NOT_SATISFIABLE,
                headers={"Content-Range": f"bytes */{file_size}"},
            )

        start = int(match.group(1))
        end_str = match.group(2)
        end = int(end_str) if end_str else file_size - 1

        # Validate range
        if start >= file_size or end >= file_size or start > end:
            return Response(
                status_code=HTTP_416_REQUESTED_RANGE_NOT_SATISFIABLE,
                headers={"Content-Range": f"bytes */{file_size}"},
            )

        content_length = end - start + 1
        headers["Content-Range"] = f"bytes {start}-{end}/{file_size}"
        headers["Content-Length"] = str(content_length)

        return StreamingResponse(
            _file_range_iterator(file_path, start, end),
            status_code=HTTP_206_PARTIAL_CONTENT,
            headers=headers,
            media_type="video/mp4",
        )

    # No Range header: full file
    headers["Content-Length"] = str(file_size)
    return StreamingResponse(
        _file_iterator(file_path),
        status_code=HTTP_200_OK,
        headers=headers,
        media_type="video/mp4",
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


async def _file_iterator(path: Path):
    """Yield chunks of a file."""
    with open(path, "rb") as f:
        while True:
            chunk = f.read(_CHUNK_SIZE)
            if not chunk:
                break
            yield chunk


async def _file_range_iterator(path: Path, start: int, end: int):
    """Yield chunks of a file within a byte range."""
    remaining = end - start + 1
    with open(path, "rb") as f:
        f.seek(start)
        while remaining > 0:
            chunk_size = min(_CHUNK_SIZE, remaining)
            chunk = f.read(chunk_size)
            if not chunk:
                break
            remaining -= len(chunk)
            yield chunk
