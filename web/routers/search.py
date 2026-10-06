"""Search and video request routes."""

from fastapi import APIRouter, Request, Form, Query
from fastapi.responses import HTMLResponse, RedirectResponse

from web.shared import templates, limiter
from web.deps import get_child_store, get_extractor
from web.helpers import (
    VIDEO_ID_RE, _ERROR_MESSAGES,
    base_ctx, get_csrf_token, validate_csrf, shorts_enabled, current_locale,
)
from web.cache import (
    get_word_filter_patterns, title_matches_filter, invalidate_catalog_cache,
)
from youtube.extractor import extract_video_id
from i18n import t

router = APIRouter()

# Guard against duplicate notifications from concurrent requests for the same video
_pending_requests: set[tuple[str, str]] = set()  # (profile_id, video_id)


async def run_search(request: Request, q: str) -> tuple[list[dict], bool]:
    """Search YouTube for the current profile: (results, fetch_failed).

    Applies word filters, blocked channels and the Shorts setting, and logs
    the query. Shared by the web page and the native-client API.
    """
    state = request.app.state
    cs = get_child_store(request)
    extractor = get_extractor(request)

    # Block search queries that contain filtered words
    word_patterns = get_word_filter_patterns(state)
    if word_patterns and any(p.search(q) for p in word_patterns):
        cs.record_search(q, 0)
        return [], False

    video_id = extract_video_id(q)
    fetch_failed = False
    search_lang = current_locale(request)

    if video_id:
        metadata = await extractor.extract_metadata(video_id, lang=search_lang)
        results = [metadata] if metadata else []
        if not metadata:
            fetch_failed = True
    else:
        yt_cfg = state.youtube_config
        max_results = yt_cfg.search_max_results if yt_cfg else 10
        results = await extractor.search(q, max_results=max_results, lang=search_lang)

    # Remember the titles YouTube served for this language so already-stored
    # videos can be shown in it later without re-fetching.
    vs = getattr(state, "video_store", None)
    if vs and results:
        vs.save_titles(search_lang, {
            r["video_id"]: r["title"]
            for r in results
            if r.get("video_id") and r.get("title")
        })

    # Filter out blocked channels
    blocked = cs.get_blocked_channels_set()
    if blocked:
        results = [r for r in results if r.get('channel_name', '').lower() not in blocked]

    # Filter out videos with blocked words in title (word-boundary match)
    if word_patterns:
        results = [
            r for r in results
            if not any(p.search(r.get('title', '')) for p in word_patterns)
        ]

    # Hide Shorts from search when disabled
    if not shorts_enabled(request, cs):
        results = [r for r in results if not r.get('is_short')]

    # Log search query
    cs.record_search(q, len(results))
    return results, fetch_failed


@router.get("/search", response_class=HTMLResponse)
@limiter.limit("10/minute")
async def search_videos(request: Request, q: str = Query("", max_length=200)):
    """Search results via yt-dlp."""
    if not q:
        return RedirectResponse(url="/", status_code=303)

    results, fetch_failed = await run_search(request, q)
    locale = current_locale(request)
    error_message = t(locale, _ERROR_MESSAGES["fetch_failed"]) if fetch_failed else ""
    return templates.TemplateResponse(request, "search.html", {
        **base_ctx(request),
        "results": results,
        "query": q,
        "csrf_token": get_csrf_token(request),
        "error_message": error_message,
    })


def _add_from_metadata(cs, metadata: dict) -> dict:
    return cs.add_video(
        video_id=metadata['video_id'],
        title=metadata['title'],
        channel_name=metadata['channel_name'],
        thumbnail_url=metadata.get('thumbnail_url'),
        duration=metadata.get('duration'),
        channel_id=metadata.get('channel_id'),
        is_short=metadata.get('is_short', False),
        yt_view_count=metadata.get('view_count'),
    )


async def submit_request(request: Request, video_id: str) -> tuple[str, str, bool]:
    """Request a video for the current profile: (outcome, video_id, existed).

    outcome is "approved", "pending", "denied", "invalid" or "fetch_failed";
    existed is True when the profile already had the video. Allowlisted
    channels auto-approve (and trigger the local download), blocked channels
    auto-deny, anything else notifies the parent. A repeat request for a
    pending video re-sends the notification.
    Shared by the web form and the native-client API.
    """
    extracted_id = extract_video_id(video_id)
    if extracted_id:
        video_id = extracted_id
    if not VIDEO_ID_RE.match(video_id):
        return "invalid", video_id, False

    state = request.app.state
    cs = get_child_store(request)
    extractor = get_extractor(request)
    profile_id = cs.profile_id

    existing = cs.get_video(video_id)
    if existing:
        if existing["status"] == "pending" and state.notify_callback:
            await state.notify_callback(existing, profile_id)
        return existing["status"], video_id, True

    # Prevent duplicate notifications from concurrent requests for the same video
    req_key = (profile_id, video_id)
    if req_key in _pending_requests:
        return "pending", video_id, True
    _pending_requests.add(req_key)

    try:
        metadata = await extractor.extract_metadata(video_id)
        if not metadata:
            return "fetch_failed", video_id, False

        channel_name = metadata['channel_name']
        channel_id = metadata.get('channel_id') or ""

        # Check if channel is blocked -> auto-deny
        if cs.is_channel_blocked(channel_name, channel_id=channel_id):
            _add_from_metadata(cs, metadata)
            cs.update_status(video_id, "denied")
            invalidate_catalog_cache(state)
            return "denied", video_id, False

        # Check if channel is allowlisted -> auto-approve
        if cs.is_channel_allowed(channel_name, channel_id=channel_id):
            _add_from_metadata(cs, metadata)
            cs.update_status(video_id, "approved")
            invalidate_catalog_cache(state)
            # Trigger local download if enabled
            dl_cb = getattr(state, "download_on_approve", None)
            if dl_cb:
                await dl_cb(video_id, profile_id)
            return "approved", video_id, False

        video = _add_from_metadata(cs, metadata)
        if state.notify_callback:
            await state.notify_callback(video, profile_id)
        return "pending", video_id, False
    finally:
        _pending_requests.discard(req_key)


@router.post("/request")
@limiter.limit("10/minute")
async def request_video(
    request: Request,
    video_id: str = Form(..., max_length=100),
    csrf_token: str = Form(""),
):
    """Submit video for approval."""
    if not validate_csrf(request, csrf_token):
        return RedirectResponse(url="/", status_code=303)

    outcome, video_id, existed = await submit_request(request, video_id)
    if outcome == "invalid":
        return RedirectResponse(url="/?error=invalid_video", status_code=303)
    if outcome == "fetch_failed":
        return RedirectResponse(url="/?error=fetch_failed", status_code=303)
    if outcome == "approved":
        return RedirectResponse(url=f"/watch/{video_id}", status_code=303)
    if outcome == "denied" and not existed:
        return templates.TemplateResponse(request, "denied.html", {
            **base_ctx(request),
            "video": get_child_store(request).get_video(video_id),
        })
    return RedirectResponse(url=f"/pending/{video_id}", status_code=303)
