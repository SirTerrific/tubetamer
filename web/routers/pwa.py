"""PWA endpoints served from the app root for installability and standalone mode."""

from fastapi import APIRouter
from fastapi.responses import Response

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
