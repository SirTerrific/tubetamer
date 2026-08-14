"""Profile management routes: avatar customization and UI language."""

import asyncio

from fastapi import APIRouter, Request
from fastapi.responses import JSONResponse

from i18n import SUPPORTED_LOCALES, normalize_locale
from web.cache import backfill_titles_for_locale
from web.shared import limiter
from web.helpers import AVATAR_ICONS, AVATAR_COLORS

router = APIRouter()

# asyncio only holds weak references to tasks, so a fire-and-forget task can be
# garbage collected mid-run. Keep them here until they finish.
_background_tasks: set[asyncio.Task] = set()


@router.post("/api/locale")
@limiter.limit("30/minute")
async def set_locale(request: Request):
    """Switch the UI language for this browser session."""
    try:
        body = await request.json()
    except Exception:
        return JSONResponse({"error": "invalid json"}, status_code=400)

    requested = str(body.get("locale", ""))
    if requested not in SUPPORTED_LOCALES:
        return JSONResponse({"error": "unsupported locale"}, status_code=400)

    lang = normalize_locale(requested)
    request.session["locale"] = lang

    # First switch to a language has no stored titles yet — fetch them in the
    # background so the catalog catches up without blocking this response.
    vs = getattr(request.app.state, "video_store", None)
    profile_id = request.session.get("child_id", "default")
    if vs and lang not in vs.get_title_langs():
        task = asyncio.create_task(backfill_titles_for_locale(request.app.state, profile_id, lang))
        _background_tasks.add(task)
        task.add_done_callback(_background_tasks.discard)

    return JSONResponse({"ok": True, "locale": lang})


@router.post("/api/avatar")
@limiter.limit("30/minute")
async def update_avatar(request: Request):
    """Update the current profile's avatar icon and/or color."""
    child_id = request.session.get("child_id")
    vs = request.app.state.video_store
    if not child_id or not vs:
        return JSONResponse({"error": "unauthorized"}, status_code=401)

    try:
        body = await request.json()
    except Exception:
        return JSONResponse({"error": "invalid json"}, status_code=400)

    icon = body.get("icon", "")
    color = body.get("color", "")

    if icon and icon not in AVATAR_ICONS:
        return JSONResponse({"error": "invalid icon"}, status_code=400)
    if color and color not in AVATAR_COLORS:
        return JSONResponse({"error": "invalid color"}, status_code=400)

    vs.update_profile_avatar(
        child_id,
        icon=icon if icon else None,
        color=color if color else None,
    )

    if icon:
        request.session["avatar_icon"] = icon
    if color:
        request.session["avatar_color"] = color

    return JSONResponse({"ok": True})
