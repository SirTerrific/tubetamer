---
type: "Reference"
title: "Telegram bot"
openwiki_generated: true
verified:
  - by: openwiki/0.7.0
    at: 2026-10-05T04:15:14.909Z
---

# Telegram bot

The bot is the parent's control panel. A child requests a video in the web app, the bot sends the parent a message with buttons, and the parent approves, denies or blocks from the chat. The parent also manages channels, time limits, filters and child profiles from it. The user-facing command list is in `docs/telegram-commands.md`. This page covers how the code is organized. For how the bot is started and wired to the web app, see [Architecture](architecture.md).

## Structure

The `TubeTamerBot` class in `bot/telegram_bot.py` is built from six mixins, each in its own file:

| Mixin | File | Responsibility |
|---|---|---|
| `SetupMixin` | `bot/setup.py` | `/setup` hub and onboarding wizard (children, channels, time, Shorts) |
| `ApprovalMixin` | `bot/approval.py` | `notify_new_request`, approve/deny/revoke buttons, child selection, auto-approve |
| `ChannelMixin` | `bot/channels.py` | `/channel`, allowlist and blocklist, starter channels |
| `TimeLimitMixin` | `bot/timelimits.py` | `/time`, schedules, per-day overrides, bonus minutes, `/time setup` wizard, limit-reached notice |
| `CommandsMixin` | `bot/commands.py` | `/child`, `/start`, `/help`, `/shorts`, `/autoload`, `/pending`, `/approved`, `/revoke_<id>`, `/stats`, `/changelog` |
| `ActivityMixin` | `bot/activity.py` | `/watch`, `/logs`, `/search`, `/filter` |

`bot/helpers.py` holds the shared helpers: `_md` (markdown to Telegram MarkdownV2 through `telegramify_markdown`), `_answer_bg` (answers a callback query in the background so it never delays the message edit), `_edit_msg`, `_nav_row` (Back/Next pagination row) and `_channel_md_link`.

The constructor takes the bot token, admin chat id, the `VideoStore`, the config and the path to `starter-channels.yaml`. It reads locale and time format from the config (see [Localization](i18n.md)) and exposes `tr()`, `cat_label()`, `day_label()` and `fmt_time()` for the mixins.

## Lifecycle

`start()` builds a `python-telegram-bot` application with an `HTTPXRequest` that sets explicit timeouts, registers the handlers below, then initializes and starts polling with `drop_pending_updates=True`. If the channel list is empty, which means a first run, it sends the setup hub to the admin and remembers the message in `_pending_wizard`. It then starts `_version_check_loop`. `stop()` cancels that task and shuts the application down.

Registered handlers:

- `CommandHandler` for `start`, `help`, `pending`, `approved`, `stats`, `logs`, `channel`, `search`, `filter`, `watch`, `time`, `changelog`, `shorts`, `autoload`, `child` and `setup`.
- A `MessageHandler` for text that matches `/revoke_` followed by an 11-character video id, which is the revoke link shown in lists.
- A `MessageHandler` for plain text that is not a command, handled by `_handle_wizard_reply`, which carries on a wizard step when the bot is waiting for typed input.
- One `CallbackQueryHandler`, `_handle_callback`, for every inline button.

### Admin check

`_check_admin()` accepts an update only when the chat id or the user id equals `admin_chat_id`. This covers both a direct message with the admin and a group chat that is the admin chat. If `admin_chat_id` is empty, nobody is authorized. `_require_admin()` also replies with "This bot is for the parent/admin only." to others. Commands and callbacks both go through it.

### Update notifications

`_version_check_loop` waits 60 seconds, then checks the latest GitHub release of `SirTerrific/tubetamer` every 12 hours (`_UPDATE_CHECK_INTERVAL`). It tells the admin once, stores `last_notified_version` in settings, and then stops checking.

## Callback routing

Inline button data is a colon-separated string, such as `prefix:arg:arg`. `bot/callback_router.py` provides a declarative table:

- A `CallbackRoute` is a frozen dataclass with the `prefix`, the `handler` method name, `min_parts` and `max_parts`, optional allowed values per position (`constraints`), indexes to convert to `int` (`int_parts`), `rejoin_from` for a last argument that may itself contain colons (times, channel names), `answer` (auto-answer text) and `pass_update`.
- `match_route(routes, parts)` returns the first matching route in registration order, with the parsed arguments, or `None`.

`_handle_callback` runs the admin check, answers `noop` buttons (the blank placeholders from `_nav_row`), then matches the route. It auto-answers the query, and calls the handler with the parsed arguments. A `ValueError` or `IndexError` shows "Invalid callback." If no route matches, the data is treated as a video action in `action:profile_id:video_id` form. The older two-part `action:video_id` form means the `default` profile. The actions are `approve`, `approve_edu`, `approve_fun`, `deny`, `revoke`, `allowchan`, `allowchan_edu`, `allowchan_fun`, `blockchan`, `setcat_edu` and `setcat_fun`, and they are handled by `_cb_video_action` in `bot/approval.py`.

## Multiple children

Child-scoped commands go through `_with_child_context`. With one profile the command runs directly. With several, the bot shows buttons to choose a child, and `_pending_cmd` holds the command that is waiting. Each handler then gets a `ChildStore` for that profile (see [Data layer](data-layer.md)). Headers add the child's name only when more than one profile exists (`_ctx_label`).

## Approval flow

1. The web app calls `notify_new_request(video, profile_id)` (see [Web app](web-app.md)).
2. The message shows the title, a link to the channel and to the video (Shorts use the `/shorts/` URL), the duration, the child's name when there are several profiles, and a note when another child already has the video approved.
3. Buttons: **Approve (Edu)** and **Approve (Fun)** to tag the budget category, **Deny**, **Allow Ch (Edu/Fun)** to allowlist the whole channel and approve, and **Block Channel**.
4. After approval, the buttons become **Revoke** and a category toggle.
5. The bot calls the registered hooks (`on_video_approve`, `on_video_revoke`, `on_video_change`, `on_channel_change`) so the web app can start a download or clear its caches.

## Channels

`/channel` lists allowlisted channels with buttons. Subcommands allow, block, unallow, unblock and change category. `/channel starter` pages through the list loaded from `starter-channels.yaml` for one-tap import. `_resolve_channel_bg` can look up a channel's handle in the background and save it with `update_channel_handle`.

## Time limits

`/time` reads and writes settings through `ChildStore`, per profile. Settings can be per weekday (`mon_...`) and are read through `resolve_setting` (see [Data layer](data-layer.md)). The bot enforces that a child has either category limits (edu and fun) or one global limit. Setting one clears the other (`_auto_clear_mode`), and `_cb_switch_confirm` handles the confirmation button for switching modes in the wizard. `notify_time_limit_reached` messages the parent at most once a day for each profile and category. The `/time setup` wizard (`_cb_setup_*` callbacks) edits the schedule, per-day overrides and limits with buttons.

## Activity and filters

`/watch` shows watch time by edu and fun with progress bars for today, yesterday or N days ago. `/logs` and `/search` show paged reports, with page buttons (`_cb_logs_page`, `_cb_search_page`). `/filter add|remove` edits the `word_filters` table, which hides matching titles in the catalog, Shorts, requests and search.

## Setup wizard

`/setup` (also `/start`) shows a hub with sub-menus for children, channels, time and Shorts. Each sub-menu has its own `_cb_onboard_*` callbacks. Steps that need typed text (a child name or PIN) send a `ForceReply` prompt and store the step in `_pending_wizard` by chat id, and `_handle_wizard_reply` continues from there.

## Related

- [Configuration](configuration.md): `telegram.bot_token` and `telegram.admin_chat_id`.
- [Localization](i18n.md): every bot string goes through `tr()`.
