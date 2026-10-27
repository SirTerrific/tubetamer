"""Telegram bot fixes from the audit: escaping, /revoke cleanup, wizard expiry, /child args."""

import asyncio
import time

from bot.helpers import _channel_md_link, _esc, _md
from bot.telegram_bot import _PendingWizard
from tests.test_bot_localization import _DummyUpdate, _make_bot

ADMIN = -100123456


def _admin_update(text: str) -> _DummyUpdate:
    update = _DummyUpdate(text, chat_id=ADMIN)
    update.effective_user = type("User", (), {"id": ADMIN})()
    return update


def test_esc_keeps_user_text_literal():
    assert _esc("Mr. Beast_x [a](b) -1") == r"Mr\. Beast\_x \[a\]\(b\) \-1"
    assert _md("**" + _esc("Mr. Beast") + "**") == r"*Mr\. Beast*"


def test_channel_link_name_cannot_break_the_link():
    link = _channel_md_link("A]B", "UC1")
    assert link.startswith(r"[A\]B](")


def test_split_name_pin(tmp_path):
    bot, store = _make_bot(tmp_path)
    try:
        assert bot._split_name_pin(["Ola"]) == ("Ola", "")
        assert bot._split_name_pin(["Ola", "1234"]) == ("Ola", "1234")
        assert bot._split_name_pin(["Mary", "Jane"]) == ("Mary Jane", "")
        assert bot._split_name_pin(["Mary", "Jane", "1234"]) == ("Mary Jane", "1234")
    finally:
        store.close()


def test_child_add_with_spaced_name_and_pin(tmp_path):
    bot, store = _make_bot(tmp_path, locale="en")
    try:
        asyncio.run(bot._child_add(_admin_update("/child add"), ["Mary", "Jane", "1234"]))
        profile = store.get_profile("maryjane")
        assert profile["display_name"] == "Mary Jane" and profile["pin"] == "1234"
    finally:
        store.close()


def test_pending_wizard_state_expires():
    wizard = _PendingWizard()
    wizard[1] = {"step": "setup_simple"}
    assert wizard.live(1)["step"] == "setup_simple"
    wizard[1]["_t"] -= _PendingWizard.TTL + 1
    assert wizard.live(1) is None and 1 not in wizard


def test_expired_wizard_reply_is_ignored(tmp_path):
    bot, store = _make_bot(tmp_path, locale="en")
    try:
        bot._pending_wizard[ADMIN] = {"step": "setup_simple"}
        bot._pending_wizard[ADMIN]["_t"] = time.monotonic() - _PendingWizard.TTL - 5
        update = _admin_update("30")
        asyncio.run(bot._handle_wizard_reply(update, None))
        assert update.message.replies == []
        assert ADMIN not in bot._pending_wizard
        assert store.get_setting("daily_limit_minutes", "") == ""
    finally:
        store.close()


def _revoke_setup(tmp_path, other_approved: bool):
    bot, store = _make_bot(tmp_path, locale="en")
    store.create_profile("sibling", "Sibling")
    for pid in ("default", "sibling"):
        cs = bot._child_store(pid)
        cs.add_video("revokevid01", "A title", "Chan")
        cs.update_status("revokevid01", "approved" if (pid == "default" or other_approved) else "pending")
    calls = []
    bot.on_video_change = lambda: calls.append("change")

    async def _revoke(video_id):
        calls.append(("revoke", video_id))
    bot.on_video_revoke = _revoke
    return bot, store, calls


def test_revoke_command_removes_file_only_when_no_other_profile_has_it(tmp_path):
    bot, store, calls = _revoke_setup(tmp_path, other_approved=False)
    try:
        async def run():
            await bot._cmd_revoke(_admin_update("/revoke_revokevid01"), None)
            await asyncio.sleep(0)
        asyncio.run(run())
        assert "change" in calls and ("revoke", "revokevid01") in calls
    finally:
        store.close()


def test_revoke_command_keeps_file_another_profile_still_has(tmp_path):
    bot, store, calls = _revoke_setup(tmp_path, other_approved=True)
    try:
        async def run():
            await bot._cmd_revoke(_admin_update("/revoke_revokevid01"), None)
            await asyncio.sleep(0)
        asyncio.run(run())
        assert "change" in calls and ("revoke", "revokevid01") not in calls
    finally:
        store.close()


def test_approved_search_shows_one_page_without_page_buttons(tmp_path):
    bot, store = _make_bot(tmp_path, locale="en")
    try:
        cs = bot._child_store("default")
        for i in range(30):
            cs.add_video(f"lego{i:07d}", f"Lego {i}", "Chan")
            cs.update_status(f"lego{i:07d}", "approved")
        update = _admin_update("/approved lego")
        context = type("Ctx", (), {"args": ["lego"]})()
        asyncio.run(bot._cmd_approved(update, context))
        text, kwargs = update.message.replies[0]
        assert len(text) < 4096 and text.count("/revoke") == bot._APPROVED_PAGE_SIZE
        assert kwargs.get("reply_markup") is None
    finally:
        store.close()


def test_watch_command_shows_category_budgets(tmp_path):
    bot, store = _make_bot(tmp_path, locale="en")
    try:
        store.set_setting("edu_limit_minutes", "30")
        store.set_setting("fun_limit_minutes", "20")
        update = _admin_update("/watch")
        context = type("Ctx", (), {"args": []})()
        asyncio.run(bot._cmd_watch(update, context))
        text = update.message.replies[0][0]
        assert "0/30 min" in text and "0/20 min" in text
        assert "OFF" not in text
    finally:
        store.close()


def test_time_command_unicode_digit_is_a_usage_error_not_a_crash(tmp_path):
    bot, store = _make_bot(tmp_path, locale="en")
    try:
        update = _admin_update("/time edu ²")
        context = type("Ctx", (), {"args": ["edu", "²"]})()
        asyncio.run(bot._cmd_timelimit(update, context))
        assert update.message.replies  # answered, no ValueError
        assert store.get_setting("edu_limit_minutes", "") == ""
    finally:
        store.close()
