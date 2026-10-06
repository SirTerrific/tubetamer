"""/devices: list and revoke Android TV sign-ins from Telegram."""

import asyncio

from tests.test_bot_localization import _DummyQuery, _DummyUpdate, _make_bot


def _buttons(markup):
    return [row[0] for row in markup.inline_keyboard]


def test_no_devices(tmp_path):
    bot, store = _make_bot(tmp_path, locale="en")
    try:
        update = _DummyUpdate("/devices", chat_id=-100123456)
        asyncio.run(bot._cmd_devices(update, None))
        text, kw = update.message.replies[0]
        assert "No connected devices" in text
        assert kw["reply_markup"] is None
    finally:
        store.close()


def test_lists_devices_without_tokens(tmp_path):
    bot, store = _make_bot(tmp_path, locale="en")
    try:
        store.create_profile("bob", "Bob")
        tok_a, _ = store.create_device_token("default", "Living room TV")
        store.create_device_token("bob", "")
        text, markup = bot._render_devices()
        assert "Living room TV" in text and "Default" in text
        assert "Unnamed device" in text and "Bob" in text
        assert tok_a not in text
        buttons = _buttons(markup)
        assert len(buttons) == 2
        assert all(b.callback_data.startswith("dev_revoke:") for b in buttons)
        assert all(tok_a not in b.callback_data for b in buttons)
    finally:
        store.close()


def test_revoke_button_revokes_and_refreshes(tmp_path):
    bot, store = _make_bot(tmp_path, locale="en")
    try:
        token, _ = store.create_device_token("default", "Shield")
        token_id = store.list_device_tokens()[0]["id"]
        query = _DummyQuery()

        async def _run():
            await bot._cb_device_revoke(query, token_id)
            await asyncio.sleep(0)

        asyncio.run(_run())
        assert store.resolve_device_token(token) is None
        assert store.list_device_tokens() == []
        assert "No connected devices" in query.edits[-1]["text"]
        assert query.answers == ["Device revoked."]

        query2 = _DummyQuery()

        async def _again():
            await bot._cb_device_revoke(query2, token_id)
            await asyncio.sleep(0)

        asyncio.run(_again())
        assert query2.answers == ["Already revoked."]
    finally:
        store.close()


def test_route_registered(tmp_path):
    from bot.callback_router import match_route
    bot, store = _make_bot(tmp_path, locale="en")
    try:
        route, args = match_route(bot._CALLBACK_ROUTES, ["dev_revoke", "7"])
        assert route.handler == "_cb_device_revoke"
        assert args == [7]
    finally:
        store.close()


def test_localized(tmp_path):
    bot, store = _make_bot(tmp_path, locale="fr")
    try:
        store.create_device_token("default", "Shield")
        text, markup = bot._render_devices()
        assert "Appareils connectés" in text
        assert _buttons(markup)[0].text.startswith("Révoquer")
    finally:
        store.close()
