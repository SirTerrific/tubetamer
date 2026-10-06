"""HTTP middleware: security headers + PIN-based profile authentication."""

import logging

from fastapi import Request
from fastapi.responses import JSONResponse, RedirectResponse
from starlette.middleware.base import BaseHTTPMiddleware
from starlette.responses import Response

logger = logging.getLogger(__name__)

# API paths safe to access without PIN auth
_API_AUTH_EXEMPT = ("/api/status/", "/api/yt-iframe-api.js", "/api/yt-widget-api.js")
_ROOT_AUTH_EXEMPT = (
    "/manifest.webmanifest", "/service-worker.js",
    # Native clients: discovery, profile picker and login happen before a token exists.
    "/api/v1/info", "/api/v1/profiles", "/api/v1/auth/login",
)


def bearer_token(request: Request) -> str:
    """Return the token from an `Authorization: Bearer <token>` header, or ''."""
    header = request.headers.get("authorization", "")
    scheme, _, value = header.partition(" ")
    if scheme.lower() != "bearer":
        return ""
    return value.strip()


class SecurityHeadersMiddleware(BaseHTTPMiddleware):
    """Add security headers to all responses."""

    async def dispatch(self, request: Request, call_next) -> Response:
        response = await call_next(request)
        response.headers["X-Content-Type-Options"] = "nosniff"
        response.headers["X-Frame-Options"] = "DENY"
        response.headers["Referrer-Policy"] = "strict-origin-when-cross-origin"
        response.headers["Content-Security-Policy"] = (
            "default-src 'self'; "
            "manifest-src 'self'; "
            "script-src 'self' 'unsafe-inline' https://cdnjs.cloudflare.com; "
            "style-src 'self' 'unsafe-inline' https://cdnjs.cloudflare.com; "
            "img-src 'self' https://i.ytimg.com https://i1.ytimg.com https://i2.ytimg.com "
            "https://i3.ytimg.com https://i4.ytimg.com https://i9.ytimg.com https://img.youtube.com; "
            "frame-src https://www.youtube.com; "
            "connect-src 'self' https://cdnjs.cloudflare.com; "
            "media-src 'self' https://*.googlevideo.com; "
            "worker-src 'self'; "
            "object-src 'none'; "
            "base-uri 'self'"
        )
        return response


class PinAuthMiddleware(BaseHTTPMiddleware):
    """Require profile-based authentication when any profile has a PIN."""

    def __init__(self, app, pin: str = ""):
        super().__init__(app)
        self.pin = pin  # legacy single-PIN (used for backwards compat check)

    async def dispatch(self, request: Request, call_next) -> Response:
        # Allow unauthenticated access to login, static assets, and specific read-only APIs
        if request.url.path.startswith(("/login", "/static")):
            return await call_next(request)
        if request.url.path in _ROOT_AUTH_EXEMPT:
            return await call_next(request)
        if request.url.path.startswith(_API_AUTH_EXEMPT):
            return await call_next(request)

        vs = getattr(request.app.state, "video_store", None)

        # Native clients: a Bearer token replaces the cookie session. Never fall
        # back to the cookie when a token is sent, so a revoked token fails cleanly.
        token = bearer_token(request)
        if token:
            return await self._dispatch_bearer(request, call_next, vs, token)

        # Profile-based auth: check if child_id is in session
        if request.session.get("child_id"):
            return await call_next(request)

        # Auto-login: if only one profile and it has no PIN, set session directly
        profiles = []
        if vs:
            profiles = vs.get_profiles()
            if len(profiles) == 1 and not profiles[0]["pin"]:
                request.session["child_id"] = profiles[0]["id"]
                request.session["child_name"] = profiles[0]["display_name"]
                request.session["avatar_icon"] = profiles[0].get("avatar_icon") or ""
                request.session["avatar_color"] = profiles[0].get("avatar_color") or ""
                return await call_next(request)
            if not profiles:
                # No profiles at all — shouldn't happen after bootstrap, but handle gracefully
                return await call_next(request)

        # Legacy: if no profiles exist but PIN auth is disabled
        if not self.pin and (not vs or not profiles):
            return await call_next(request)

        # Return JSON 401 for API endpoints instead of redirect
        if request.url.path.startswith("/api/"):
            return JSONResponse({"error": "unauthorized"}, status_code=401)
        return RedirectResponse(url="/login", status_code=303)

    @staticmethod
    async def _dispatch_bearer(request: Request, call_next, vs, token: str) -> Response:
        profile = vs.resolve_device_token(token) if vs else None
        if not profile:
            return JSONResponse({"error": "invalid_token"}, status_code=401)

        # Routes read the profile from request.session. Give this request a
        # throwaway session holding the token's profile (same class as the real
        # one, e.g. Starlette's Session), then put the untouched original back
        # before the response goes out so SessionMiddleware never issues a cookie.
        original = request.scope.get("session")
        session_cls = type(original) if isinstance(original, dict) else dict
        request.scope["session"] = session_cls({
            "child_id": profile["id"],
            "child_name": profile["display_name"],
            "avatar_icon": profile.get("avatar_icon") or "",
            "avatar_color": profile.get("avatar_color") or "",
        })
        request.state.device_token = token
        request.state.auth_via_token = True
        try:
            return await call_next(request)
        finally:
            request.scope["session"] = original
