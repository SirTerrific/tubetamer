"""JSON API for native clients (Android TV), under /api/v1.

Auth: `POST /api/v1/auth/login` trades a profile id + PIN for a bearer token.
Every other v1 call sends `Authorization: Bearer <token>`; PinAuthMiddleware
resolves it to the profile. Browser routes and cookie sessions are unchanged.
"""

import hmac

from fastapi import APIRouter, Request
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field

from version import __version__
from web.middleware import bearer_token
from web.shared import limiter

router = APIRouter(prefix="/api/v1")

API_VERSION = 1


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
