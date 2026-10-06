"""PWA endpoints served from the app root for installability and standalone mode."""

from pathlib import Path

from fastapi import APIRouter, Request
from fastapi.responses import FileResponse, Response

from web.shared import static_dir

router = APIRouter()


@router.get("/manifest.webmanifest", include_in_schema=False)
async def web_manifest():
    """Serve the install manifest from the app root."""
    return Response(
        content=(static_dir / "manifest.webmanifest").read_text(encoding="utf-8"),
        media_type="application/manifest+json",
        headers={"Cache-Control": "public, max-age=3600"},
    )


@router.get("/service-worker.js", include_in_schema=False)
async def service_worker():
    """Serve the service worker from the app root so it can control the full site."""
    return Response(
        content=(static_dir / "service-worker.js").read_text(encoding="utf-8"),
        media_type="application/javascript",
        headers={
            "Cache-Control": "no-cache",
            "Service-Worker-Allowed": "/",
        },
    )


TV_APK_PATH = "/app/tubetamer.apk"


@router.api_route(TV_APK_PATH, methods=["GET", "HEAD"], include_in_schema=False)
async def tv_apk(request: Request):
    """The Android TV app, for installing on the TV with the Downloader app.

    No authentication: the APK holds no secret (the server address and the
    token are entered or issued on the TV). 404 until the parent puts the
    file at `web.tv_apk` (default db/tubetamer.apk).
    """
    cfg = getattr(request.app.state, "web_config", None)
    path = Path(getattr(cfg, "tv_apk", "") or "db/tubetamer.apk")
    if not path.is_file():
        return Response("TV app not installed on this server.", status_code=404, media_type="text/plain")
    return FileResponse(
        path,
        media_type="application/vnd.android.package-archive",
        filename="tubetamer.apk",
        headers={"Cache-Control": "no-cache"},
    )
