"""Shared web infrastructure: Jinja2 templates + slowapi rate limiter.

Neutral module with no imports from web.* — safe for all web modules to import.
"""

from fastapi import Request
from fastapi.templating import Jinja2Templates
from jinja2 import pass_context
from pathlib import Path
from slowapi import Limiter
from slowapi.util import get_remote_address

from version import __version__
from youtube.extractor import format_duration
from i18n import app_name, t, category_label, day_label, format_time, html_lang

templates_dir = Path(__file__).parent / "templates"
static_dir = Path(__file__).parent / "static"

templates = Jinja2Templates(directory=str(templates_dir))


def _rate_limit_key(request: Request) -> str:
    """Prefer proxy-forwarded client IPs when present."""
    forwarded_for = request.headers.get("x-forwarded-for", "")
    if forwarded_for:
        client_ip = forwarded_for.split(",", 1)[0].strip()
        if client_ip:
            return client_ip
    real_ip = request.headers.get("x-real-ip", "").strip()
    if real_ip:
        return real_ip
    return get_remote_address(request)


limiter = Limiter(key_func=_rate_limit_key)


@pass_context
def _jinja_t(ctx, key: str, **kwargs):
    return t(ctx.get("locale", "en"), key, **kwargs)


@pass_context
def _jinja_cat_label(ctx, category: str, short: bool = False):
    return category_label(category, ctx.get("locale", "en"), short=short)


@pass_context
def _jinja_day_label(ctx, day: str, short: bool = False):
    return day_label(day, ctx.get("locale", "en"), short=short)


@pass_context
def _jinja_fmt_time(ctx, hhmm: str):
    return format_time(hhmm, ctx.get("locale", "en"), time_format=ctx.get("time_format", "locale"))


@pass_context
def _jinja_html_lang(ctx):
    return html_lang(ctx.get("locale", "en"))


@pass_context
def _jinja_app_name(ctx):
    return app_name(ctx.get("locale", "en"))

# Template globals
templates.env.globals["format_duration"] = format_duration
templates.env.globals["app_version"] = __version__
templates.env.globals["t"] = _jinja_t
templates.env.globals["cat_label"] = _jinja_cat_label
templates.env.globals["day_label"] = _jinja_day_label
templates.env.globals["fmt_time"] = _jinja_fmt_time
templates.env.globals["html_lang"] = _jinja_html_lang
templates.env.globals["app_name"] = _jinja_app_name


def register_filters():
    """Register custom Jinja2 filters. Called once after helpers are importable."""
    from web.helpers import format_views
    templates.env.filters["format_views"] = format_views
