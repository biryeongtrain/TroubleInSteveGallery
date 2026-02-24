# Guide Hint System

## What Players See

- A rotating tip is shown every 10 seconds (`200` ticks) in chat.
- Role-specific guidance is shown when a round starts and roles are assigned.
- Context hints are triggered when:
  - the first corpse is discovered in a round,
  - a player opens the shop for the first time in that round,
  - overtime starts.

## Server Config

Configure rotating tips in `config/ttt/config.json` with `rotatingGuideTips`:

```json
"rotatingGuideTips": [
  {
    "title": "TTT TIP",
    "message": "Inspect corpses quickly to identify roles."
  },
  {
    "title": "TTT TIP",
    "message": "Use offhand swap to open the role shop."
  }
]
```

`message` is used as the chat message body text. Existing `actionBar` keys are still accepted for compatibility.

If the field is missing, built-in defaults are used.

## Player Command

- `/tts tips <enabled>`
  - `true`: enable periodic and contextual guide hints.
  - `false`: disable periodic and contextual guide hints.
- `/tts bgm <enabled>`
  - `true`: enable periodic lobby BGM playback for this player.
  - `false`: disable periodic lobby BGM playback for this player.
- `/tts guide`
  - Opens the guide category menu dialog.
- `/tts guide <category>`
  - Opens one category dialog directly.
  - Supported categories: `basic`, `innocent`, `traitor`, `detective`.
- `/tts guide basic <page>`
  - Opens a specific basic-rules page.
  - Example: `/tts guide basic 2`
- `/tts guide innocent <page>`
  - Opens a specific innocent guide page.
  - Example: `/tts guide innocent 2`
- `/tts guide traitor <page>`
  - Opens a specific traitor guide page.
  - Example: `/tts guide traitor 2`
- `/tts guide detective <page>`
  - Opens a specific detective guide page.
  - Example: `/tts guide detective 2`
- `/tts accuse <target>`
  - Broadcasts a traitor accusation to the whole server chat.
  - Only usable by alive players during active combat phases (`MIDDLE_GAME`, `OVER_TIME`).
  - Spectators cannot use this command.
  - Cooldown: `5` seconds per player.
- `/tts stats`
  - Opens your personal TTT stats dialog.
  - Includes kill/death, accusation hit rate, team-kill rate, and role-play counts.
- `/tts stats <player>`
  - Opens a stats dialog for the specified online player.
- `/t <target>`
  - Shortcut alias for `/tts accuse <target>`.
- `/tc <message>`
  - Traitor team chat command.
  - Only usable by alive traitors during active combat phases (`MIDDLE_GAME`, `OVER_TIME`).
  - Message is passed through English-keyboard-to-Korean conversion before sending.

## Admin Command

- `/tts_admin start <resetPoints> [recordReplay]`
- `/tts_admin start <resetPoints> <map> [recordReplay]`
  - Starts a round.
  - `resetPoints`: `true|false`
  - `recordReplay` (optional): `true|false` (default: `true`)
  - `map` (optional): round map id (for example `ttt:inferno`)
  - Round maps are auto-scanned from datapack resources under `data/*/map_template/*.nbt`.
  - Example: `/tts_admin start false ttt:inferno`
  - Example: `/tts_admin start false ttt:inferno false`

## Debug Command

- `/tts_debug guide_image_test [image_id]`
  - Opens an image-only debug dialog for alignment checks.
  - Default image id: `ttt:conveyor_example`
  - Example: `/tts_debug guide_image_test ttt:conveyor_example`

## Guide Dialog Access

- Press `G` (Server Dialog key) to open the quick guide menu.
- Press `ESC` -> `Server Links` and click `TTT 가이드 (/tts guide)` for the guide shortcut link.
- The top menu button opens your own TTT stats dialog (`/tts stats`).
- The menu provides category buttons:
  - 기본 규칙
  - 이노센트 팁
  - 트레이터 팁
  - 탐정 팁

## Datapack Guide Pages

Guide pages can be defined through datapacks per category:

- Path: `data/<namespace>/guide/basic/*.txt`
- Path: `data/<namespace>/guide/innocent/*.txt`
- Path: `data/<namespace>/guide/traitor/*.txt`
- Path: `data/<namespace>/guide/detective/*.txt`
- Files are loaded on server start and on datapack reload.
- Files are sorted by resource id path and shown in that order.
- Legacy `*.json` pages are still read for compatibility.

Example page:

```txt
### Section: PageInfo
title=라운드 목표
image=ttt:conveyor_example
image_description=라운드 핵심 목표 요약
### EndSection: PageInfo

- 이노센트/탐정은 트레이터를 모두 제거하면 승리
- 트레이터는 이노센트/탐정을 모두 제거하면 승리
```

PageInfo keys:

- `title` (`string`, optional): page title.
- `image` (`identifier`, optional): image id from `assets/<namespace>/textures/guide/image/<path>.png`.
- `image_description` (`string`, optional): text rendered under the image.
- `align` (`left|center|right`, optional): text alignment for this page (default `left`).
- `item_align` (`left|center|right`, optional): default text alignment for `### Item:` description (default `left`).

Body rules:

- Lines outside `### Section: PageInfo ... ### EndSection` are treated as body text.
- PolyFactory-style directives are accepted; unsupported directives are ignored safely.
- `<nl>` and `<nl2>` can be used as manual line-break markers.
- `### Align: <left|center|right>` can override alignment in body section.
- `### ItemAlign: <left|center|right>` can override default alignment for later `### Item:` directives.
- `### Header: <text>` adds a header body element at that position.
- `### Image: <namespace:path> [description]` adds an image body element at that position.
- `### Item: <namespace:path> [description]` adds an aligned item body element at that position.

`### Image:` can be used multiple times in one page. `image=` in PageInfo remains supported for a single top image.
`description` in `### Image:` is rendered with MiniMessage parsing, same as body text.
- `### Item:` can be used multiple times in one page. `description` is rendered with MiniMessage parsing.
- `title`, `image_description`, and body lines are rendered with MiniMessage parsing.

Player settings are persisted in custom data keys:

- `tts$tips_enabled`
- `tts$bgm_enabled`
