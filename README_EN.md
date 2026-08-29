[简体中文](README.md) | [English](README_EN.md)

<h1 align="center">E33Chat</h1>

<p align="center">
  <em>Rebuilds the vanilla chat HUD in chat-app style</em>
</p>

<p align="center">
  <img alt="MC" src="https://img.shields.io/badge/MC-1.20.1--1.21.1-green">
  <img alt="Loader" src="https://img.shields.io/badge/Loader-Forge%20%7C%20NeoForge%20%7C%20Fabric-orange">
  <img alt="Side" src="https://img.shields.io/badge/Side-Client%20required,%20server%20optional-blue">
  <img alt="Java" src="https://img.shields.io/badge/Java-17%2B%20%7C%2021%2B-yellow">
  <img alt="Version" src="https://img.shields.io/badge/Version-2.3.16-informational">
  <img alt="Downloads" src="https://img.shields.io/github/downloads/E33EPUS/E33Chat/total">
  <img alt="License" src="https://img.shields.io/badge/License-MIT-brightgreen">
</p>

E33Chat is a chat-enhancement mod that rebuilds the vanilla chat HUD in a chat-app style: bubbles with heads, @ mentions, a whisper sidebar, search, emoji & quick phrases, image messages, quote reply, notification banners, local chat history, and a fully reworked settings screen.

---

## Contents

- [Installation](#installation)
- [Quick start](#quick-start)
- [Features](#features)
- [Usage](#usage)
- [Server-side bonus](#server-side-bonus)
- [Compatibility](#compatibility)
- [Known limitations](#known-limitations)
- [Privacy & data](#privacy--data)
- [FAQ](#faq)
- [Troubleshooting](#troubleshooting)
- [Building from source](#building-from-source)
- [Changelog](#changelog)
- [Reporting issues](#reporting-issues)
- [License](#license)

---

## Installation

| Dependency | Type | Notes |
|---|---|---|
| Minecraft | Required | 1.20.1 (Forge) / 1.21.1 (NeoForge / Fabric) |
| Java | Required | 17+ (Forge 1.20.1) / 21+ (1.21.1) |
| Forge | Per platform | 47.0.0+ (1.20.1) |
| NeoForge | Per platform | 21.x (1.21.1) |
| Fabric Loader | Per platform | 0.16.0+ (1.21.1) |
| Fabric API | Per platform | Any compatible version (1.21.1) |
| CustomSkinLoader | Optional | Shows offline players' heads |

1. Download the JAR for your platform from [Releases](https://github.com/E33EPUS/E33Chat/releases)
2. Drop it into `.minecraft/mods/` (match your loader — do not mix platform JARs)
3. Launch the game

---

## Quick start

1. Open chat and the E33Chat panel appears; click the **gear** at the bottom-left → Menu → Settings
2. In *Chat Screen* adjust panel width (400–1600 physical pixels — resizing the window never changes the real width; enable *Fullscreen Panel* to fill the whole screen), bubble color, corner radius, message gap and avatar size (panel blur is off by default — enable manually if wanted)
3. In *Notifications* configure the @ sound, banner, whisper sound and master volume; banner position is adjustable (`banner_offset_x/y`) to avoid other HUD elements
4. Send images: the **upload button** / **Ctrl+V paste** / **drag & drop** — they upload and send automatically

---

## Features

- 💬 **Chat bubbles** — Heads and names; colors / text color / corner radius / theme adjustable; avatars top-aligned, shown on the first message of a same-sender run only (QQ-style); message gap 4px in-group, 12px between groups
- 🖼️ **Image messages** — `[[CICode]]` / `[[ChatUpgrade]]` render natively in bubbles (ChatImage interop), click opens the original; anti-flood rate limiting + receive toggle
- ☁️ **Server-side media hosting** — With E33Chat on the server, images are stored there permanently; otherwise the mod falls back to a third-party host automatically
- 😀 **Custom emote pack** — Drop images into `config/e33chat/emotes/` (up to 10), or Ctrl+V a clipboard image; click to send
- @ **Mention autocomplete** — Type `@` for a player list, left-click a head to @ them; sound + banner when you are @'d or quoted
- 👥 **Whisper sidebar** — Online player list, unread dots, public / whisper split view, NPC hide list
- 🔍 **Search & emoji & quick phrases** — Real-time search (Chinese supported), emoji / kaomoji panel, one-click phrases
- 📋 **Copy & quote reply** — Right-click a message to copy / quote; right-click a head to whisper / teleport / block
- 🚫 **Block players** — Messages vanish completely (vanilla chat / bubbles / banners / sounds), instant, never restored on rejoin
- 🔔 **Notification banner** — Covers @ / quote / whisper / system; master volume slider + per-type toggles + position offset
- 🎬 **Animation styles** — Panel / banner / popup / message configured independently (SLIDE / FADE / ZOOM / NONE), popups animate on open and close
- 🗨️ **Vanilla chat box kept** — Renders as usual (shifted up clear of the HUD icon); ChatHeads / ChatAnimation work out of the box
- 💾 **Chat history** — Saved per world / server, JSONL preserves colors and click events (off by default); auto-saved every 30 s
- 🛠️ **Settings screen** — 5 tabs + collapsible categories + live preview + snapshot save / exit; every option is GUI-adjustable
- ✅ Anti-spam merge · 📝 input preserved · 🌈 local `&` color rendering · 🧩 server message-format templates

---

## Usage

### Chat display

- Your bubbles sit right, others left, colors configurable; bubble corner radius 0–10 (default 4)
- Whispers show as `<name>[PM] content`, quotes as `<name>[Quote] content` (yellow tag); server prefix decorations and team colors are kept
- Message gap: consecutive same-sender messages within 5 minutes use `message_gap` × 2/3 (default 4px); sender change / timeout / system / time separator use ×2 (default 12px)

### Images & media

- **Send**: upload button / Ctrl+V / drag & drop; scaled to ≤2048px and re-encoded; uploads queue serially (up to 8), one Enter is enough, failures restore the input
- **Hosts**: default uguu.se (~3h expiry); with server hosting on, images go to the server (`e33chat://media/<id>`, permanent); custom hosts via `upload_url` and friends (multipart POST; response URL from the body, or `json:<field path>`)
- **Receive**: image codes render natively, legacy history images reload automatically; anti-abuse = sliding-window rate limit + 64-entry LRU texture cache + pre-decode scaling; "Receive images" off renders plain `[Image]` text and never downloads

### Sidebar & notifications

- Sidebar: click a name to whisper, bouncing unread dots, search filter, split view, wildcard hide list (e.g. `*[NPC]*`)
- Banners: @ / quote / whisper / system, system on by default; "jump to mention" button; position offset ±1000px to avoid HUD overlap
- Optional "require @ prefix"; self-notification toggles (off by default, testing aid); master volume slider

### Animation & appearance

- Four animation groups, each SLIDE / FADE / ZOOM / NONE: panel (SLIDE), banner (SLIDE), popup (FADE), message (FADE); `animation=false` disables all
- Popups: 200ms fade-in, 150ms eased close (ESC / icon toggle / outside click all animate); banner: 250ms in, 150ms out
- The popup family (settings / emoji / quick-chat / search / @ popup / context menu) uses SDF corners + shadow + 1px border; quote-block radius 8
- Panel blur `blur_enabled` is off by default (lowest value-per-cost)

### Settings & textures

- Dark / light themes; 5 tabs (Chat Screen / HUD / Notifications / Sidebar / Advanced), snapshot save / exit with ESC confirm
- **Resource-pack override**: UI elements and icons render from textures at `assets/e33chat/textures/gui/{dark|light}/<element>.png`, F3+T hot-reloads
- ⚠️ **Popup backgrounds stopped being texture-driven in 2.3.16** (SDF-rounded, semantic theme colors instead); chat bubbles / quote blocks / @ banner are SDF-rendered by design — neither is resource-pack overridable

---

## Server-side bonus

The server mod is optional. Installing it additionally enables:

- Quote sync and cross-client @ mention sync (Chinese names included)
- New players receive recent chat history on join (`history_enabled`, off by default)
- Head teleport via `/tpa` (`use_tpa`, off by default)
- **Server-side media hosting** (`media_enabled`, on by default): images stored permanently (8MB/file, 512MB total quota, random UUID IDs, per-player throttling)
- **Message-format templates**: the server declares its chat format and syncs it to every client, so plugin/NCR-rewritten lines parse correctly (`/e33chat gui`; "Generate from message…" or one-click presets; placeholders `{display_name}` `{prefix}` `{external}` `{content}` `{sender}` `{target}` `{sep}`)
- **EasyBot group-message compatibility** (`easybot_compat`, on by default): parses QQ group messages relayed by EasyBot into player bubbles and renders EasyBot/ChatImage CICode images inside bubbles. The default format `[Group] <Nick(QQ#)> content` is auto-detected; if the EasyBot template is customized, override it with an `{external}` chat template (the server-config presets include the EasyBot format, see [EasyBot template guide](#easybot-template-guide))

Server config: `saves/<world>/serverconfig/e33chat-server.toml` (Fabric: `.json`) ｜ OP commands: `/e33chat template list|set|remove|clear|test` ｜ `/e33chat gui` for the graphical config

### EasyBot template guide

- E33Chat has built-in recognition for EasyBot's default format `[Group] <Nick(QQ#)> content`; `easybot_compat` is on by default, so it works out of the box
- If you changed the "sync template (to server)" in EasyBot, add an `{external}` chat template in `/e33chat gui` → Chat Templates to override
- Common examples:

| EasyBot sync-template output | E33Chat chat template |
|---|---|
| `[Group] <Nick(QQ#)> content` | `[{prefix}] <{external}> {content}` |
| `[Group] Nick: content` | `[{prefix}] {external}{sep}{content}` |
| `Nick >> content` | `{external}{sep}{content}` |

- Or add it by command: `/e33chat template set chat "[{prefix}] <{external}> {content}"`

---

## Compatibility

| Mod / plugin | Status |
|---|---|
| No Chat Reports and similar no-report plugins | Auto-compatible since 2.1.0, no config needed |
| CustomSkinLoader | Shows offline players' heads once installed |
| ChatImage / ChatUpgrade (image protocols) | Native interop |
| EasyBot (QQ group-server bridge) | `easybot_compat` is on by default; group messages become player bubbles automatically; CICode images render in bubbles |
| IMBlocker | Auto-adapted (command input switches to English) |
| ModernUI | Bounds-safe underlines / click regions |
| Quark and similar item sharing | Item icons render correctly |
| ChatHeads, ChatAnimation | Work by default |
| Nickname plugins | Partially supported, see [FAQ](#faq) |
| Chat-format plugins (EssentialsChat / CMI / DeluxeChat, ...) | Adaptable via server templates (common presets included) |

---

## Known limitations

1. Only Forge 1.20.1, NeoForge 1.21.1 and Fabric 1.21.1 are supported
2. A nickname sharing nothing with the real name, with neither a "click to whisper" event nor a tab-list rename, shows as a grey system line (templates cannot help either)
3. Formats with only whitespace between name and content cannot be parsed; NCR-encrypted chat shows as ciphertext
4. The default host expires files after ~3h (server hosting is permanent but has a 512MB quota — keep an eye on it)
5. Popup background texture overrides stopped working in 2.3.16 (see [Usage](#usage)); the scrollbar stays solid fill

---

## Privacy & data

> [!WARNING]
> Chat history is stored in plain text under `.minecraft/e33chat/history/` (JSONL) on your machine. **Do not use it on public or untrusted computers.**

- Chat history stays on your machine only, is off by default and is never uploaded; sensitive commands (`/login`, `/register`, ...) are skipped
- **Images**: images you send go to a third-party host (default uguu.se, ~3h expiry) or — with server hosting on — to the server's storage; the client never uploads anything on its own
- The server mod only relays (@ / quote / history / media) and collects no client data; saving, sync and hosting can all be turned off in config

---

## FAQ

**Is the server mod required?** No. Installing it unlocks quote sync, @ sync, history sync, `/tpa` teleport and media hosting.

**How do I open settings?** Bottom-left gear → Menu → Settings. Client config: `config/e33chat/e33chat-client.toml` (Fabric: `.json`) — every option is GUI-adjustable.

**Is chat history saved by default?** No (`chat_history = false`). Enable it in Settings → Chat → Chat History; saved every 30 s and on clean exits, a crash loses at most the last 30 s.

**How do I enable background blur?** `blur_enabled` is off by default since 2.3.16 (lowest value-per-cost; 2.3.5 taught a frame-drop lesson). Enable it in settings.

**Panel width and fullscreen?** `panel_width` (default 1000) counts physical screen pixels: set 800 and the panel stays a stable 800 physical pixels wide at any window size / GUI scale — it never widens or misaligns when the window is resized (it clamps to the window width when the window is narrower). Range 400–1600. To fill the whole screen instead, enable `panel_fullscreen` (off by default); it ignores `panel_width` and keeps the sidebar usable.

**Where do my images go?** With server hosting on (default) → the server, permanent. Otherwise → a third-party host (uguu.se, ~3h expiry). Both configurable.

**Why is a message shown as grey?** When the client is not sure a line was said by a player, it conservatively shows it as grey (see [Known limitations](#known-limitations)); nickname plugins and unusual broadcast formats are common causes.

**Are nickname plugins supported?** Partially: attribution works when the nickname carries a "click to whisper" event or the tab list is renamed; otherwise grey.

**The server changed the chat format and messages don't line up?** Use message-format templates: as an OP run `/e33chat gui`, paste a real chat line via "Generate from message…" or add a preset, save — it syncs to the whole server; an empty template list falls back to the guards.

**How do I restore the vanilla chat?** Settings → Chat Screen → turn off "Enable E33Chat" (`enabled = false`); removing the mod restores everything.

**Can I include it in a modpack?** Yes, no extra permission needed.

---

## Troubleshooting

1. Confirm the MC version, loader and JAR platform match; do not mix platform JARs
2. Back up and delete `config/e33chat/e33chat-client.toml` to test for corruption; keep only E33Chat to isolate conflicts
3. Image uploads failing: check `upload_url` and your network; the default host uguu.se is unreachable on some networks — switch hosts or enable server hosting
4. Look for `[e33chat]` errors in `.minecraft/logs/latest.log`
5. When reporting, include versions, mod list, `latest.log`, screenshots and reproduction steps

---

## Building from source

```bash
git clone https://github.com/E33EPUS/E33Chat.git
cd E33Chat

# Forge 1.20.1 (default branch)
./gradlew build

# NeoForge 1.21.1
git checkout Neoforge-1.21.1
./gradlew build

# Fabric 1.21.1
git checkout Fabric-1.21.1
./gradlew build
```

- Forge 1.20.1: Java 17+, supports `--offline`; NeoForge / Fabric: Java 21+
- Run tests: `./gradlew cleanTest test --offline -PrunTests` (Forge / NeoForge need `-PrunTests`; Fabric does not)
- The three branches are isomorphic — keep changes in sync across all three (the README is the same text in all three repos too)

---

## Changelog

The full bilingual changelog lives in [CHANGELOG.md](CHANGELOG.md).

---

## Reporting issues

Open an [Issue](https://github.com/E33EPUS/E33Chat/issues) and include versions, loader, mod list, `latest.log`, screenshots or video, and reproduction steps.

---

## License

[MIT License](LICENSE)

Copyright &copy; 2026 E33EPUS
