[简体中文](README.md) | [English](README_EN.md)

# E33Chat

<p align="center">
  <em>Rebuilds the vanilla chat HUD in chat-app style</em>
</p>

<p align="center">
  <img alt="MC" src="https://img.shields.io/badge/MC-1.20.1--1.21.1-green">
  <img alt="Loader" src="https://img.shields.io/badge/Loader-Forge%20%7C%20NeoForge%20%7C%20Fabric-orange">
  <img alt="Side" src="https://img.shields.io/badge/Side-Client%20required,%20server%20optional-blue">
  <img alt="Java" src="https://img.shields.io/badge/Java-17%2B%20%7C%2021%2B-yellow">
  <img alt="Version" src="https://img.shields.io/badge/Version-2.2.3-informational">
  <img alt="License" src="https://img.shields.io/badge/License-MIT-brightgreen">
</p>

E33Chat is a chat-enhancement mod that rebuilds the vanilla chat HUD in a chat-app style: bubbles with heads, @ mentions, a whisper sidebar, search, emoji & quick phrases, quote reply, notification banners, local chat history, and a fully reworked settings screen.

---

## Contents

- [Installation requirements](#installation-requirements)
- [Installation](#installation)
- [Quick start](#quick-start)
- [Features](#features)
- [Chat bubbles & message display](#chat-bubbles--message-display)
- [Whisper sidebar](#whisper-sidebar)
- [Mentions & notifications](#mentions--notifications)
- [Search, emoji & quick phrases](#search-emoji--quick-phrases)
- [Themes & settings](#themes--settings)
- [Server-side bonus](#server-side-bonus)
- [Message recognition & compatibility](#message-recognition--compatibility)
- [Compatibility](#compatibility)
- [Known limitations](#known-limitations)
- [Privacy & data](#privacy--data)
- [FAQ](#faq)
- [Troubleshooting](#troubleshooting)
- [Building from source](#building-from-source)
- [Reporting issues](#reporting-issues)
- [License](#license)

---

## Installation requirements

| Dependency | Type | Notes |
|---|---|---|
| Minecraft | Required | 1.20.1 (Forge) / 1.21.1 (NeoForge / Fabric) |
| Java | Required | 17+ (Forge 1.20.1) / 21+ (1.21.1) |
| Forge | Per platform | 47.0.0+ (1.20.1) |
| NeoForge | Per platform | 21.x (1.21.1) |
| Fabric Loader | Per platform | 0.19.3+ (1.21.1) |
| Fabric API | Per platform | 0.116.x+ (1.21.1) |
| CustomSkinLoader | Optional | Shows offline players' heads |

---

## Installation

1. Download the JAR for your platform from [Releases](https://github.com/E33EPUS/E33Chat/releases)
2. Drop it into `.minecraft/mods/` (match your loader — do not mix platform JARs)
3. Launch the game

---

## Quick start

1. Open chat and the E33Chat panel appears
2. Click the **gear** at the bottom-left → Menu → Settings
3. In the *Chat Screen* category adjust panel width, bubble color, corner radius, background blur
4. In the *Notifications* category configure the @ sound, banner, whisper sound and master volume

---

## Features

- 💬 **Chat bubbles** — Messages with player heads and names; custom bubble color, text color and corner radius; dark / light themes; panel background blur with adjustable opacity
- 🛠️ **Settings screen** — Five UI-element tabs (Chat Screen / HUD / Notifications / Sidebar / Advanced) with a collapsible sub-category tree, inline color palette, a live bubble preview that follows the corner radius, snapshot-based save / exit, and always-on scrollbars with eased smooth scrolling
- 💾 **Chat history** — Saved per world / server and restored on rejoin; plain-text log format (readable in any text editor); WeChat-style time separators with date across days; auto-saved every 30 s, a crash loses at most 30 s; consecutive duplicate messages merged (anti-spam)
- @ **Mention autocomplete** — Type `@` for a popup player list, or left-click a head to @ them; a sound + banner fires when you are @'d or quoted
- 👥 **Whisper sidebar** — Online player list, click a name to whisper; bouncing unread dot; separate whisper banner + sound; public / whisper split view
- 🔍 **Chat search** — Real-time matching of message content and sender name, jump with up / down, Chinese supported
- 😊 **Emoji & kaomoji** — Emoji panel + kaomoji picker, inserted at the cursor
- 📌 **Quick phrases** — Save common phrases and fill them with one click
- 📋 **Copy & quote reply** — Right-click a message to copy or quote-reply
- 👤 **Head actions** — Right-click a head to whisper / teleport, left-click to @
- 🔔 **Notification banner** — Slide-in popup at the top covering @ / quote / whisper / system, with a jump-to-mention button; master volume slider with per-type toggles
- 🗨️ **Vanilla chat box** — No custom HUD preview anymore; the vanilla chat renders as usual (shifted up clear of the HUD icon), so ChatHeads / ChatAnimation-style mods work out of the box
- 🌈 **Colored messages** — Supports `&` color / format codes, rendered locally without changing what is sent
- 📝 **Input preserved** — Typed text is kept when the chat closes

---

## Chat bubbles & message display

- Every message renders as a bubble with a head and player name
- Your bubbles sit on the right, others on the left, each color configurable
- Bubble corner radius 0–10 (0 = square)
- Whispers show as `<name>[私聊] content` and quote replies as `<name>[引用] content` (yellow tag); names keep server prefix decorations and team colors
- The vanilla chat box renders on the HUD as usual (shifted up 8 px clear of the E33Chat icon); ChatHeads / ChatAnimation-style mods work automatically
- A strong hint pops above the hotbar on @ / quote / system messages

---

## Whisper sidebar

- Online player list; click to start a whisper (auto-fills `/msg`)
- Unread whispers show the same bouncing dot as the sidebar
- Search box to filter players
- Public / whisper split view; the public tab shows the latest line
- Hide list: wildcard patterns to drop NPCs / bots from the list

---

## Mentions & notifications

- Type `@` for an online-player autocomplete list
- Left-click a player head to insert `@name`
- On @ or quote: a notification banner (slide-in, rounded, shadowed) + sound
- Separate banner and sound for whispers
- Optional "require @ prefix"
- Advanced: self-@ / self-quote / self-whisper notification toggles (off by default, for testing)
- The banner has a "jump to mention" button
- A master volume slider controls every notification sound

---

## Search, emoji & quick phrases

- Chat search: matches message content + sender name, real-time highlight, jump with up / down
- Emoji panel: emoji + kaomoji, inserted at the cursor
- Quick-phrase panel: save / manage phrases, click to fill

---

## Themes & settings

- Dark / light themes
- Custom bubble color, text color, corner radius, panel opacity, background blur
- Settings grouped into five UI-element tabs (Chat Screen / HUD / Notifications / Sidebar / Advanced) with a collapsible sub-category tree per tab
- Color rows have an inline preset palette + hex input; the *Bubbles & Text* page has a bubble preview that follows the corner radius live
- Snapshot on open, live preview, Save / Exit with a changed-count at the bottom, ESC prompts to confirm discard
- Always-on scrollbars on the tabs and the option list, eased smooth scrolling, draggable thumb / click-to-page on the track
- Numeric options accept typed input; sound volume uses a slider

---

## Server-side bonus

The server mod is optional. Installing it additionally enables:

- Server-side quote pending & sync
- Cross-client @ mention sync (Chinese names included)
- New players receive recent chat history on join
- `use_tpa`: head teleport uses `/tpa` instead

> History distribution and the `/tpa` switch must be enabled manually in the server config file.

---

## Message recognition & compatibility

E33Chat rebuilds the "who said this" layer of the chat HUD, aiming to tell player messages apart from system / broadcast ones:

- Three-layer guard classification: conservatively grey by default, only promoted to a bubble when the structure confirms player chat
- Auto-compatible with No Chat Reports and similar no-report plugins (since 2.1.0, no config needed)
- Echo suppression: your own messages are not shown again as grey lines
- Player identity is UUID-first with name fallback, easing same-name collisions on cracked servers
- Recognized formats: `Steve: hi`, `<Steve> hi`, `Steve >> hi`, suffix titles `Steve[LV.10]: hi`, legacy `§` color codes, bare Chinese short names `小明: 你好`
- Nickname plugins partially supported (see FAQ)

---

## Compatibility

| Mod / plugin | Status |
|---|---|
| No Chat Reports and similar no-report plugins | Auto-compatible since 2.1.0, no config needed |
| CustomSkinLoader | Shows offline players' heads once installed |
| ModernUI | Bounds-safe underlines / click regions on clickable text |
| Quark and similar item sharing | Item icons in system messages render correctly |
| ChatHeads, ChatAnimation | Work by default (the vanilla chat box keeps rendering, just shifted up clear of the HUD icon) |
| Nickname plugins | Partially supported, see [FAQ](#faq) |

---

## Known limitations

1. Only Forge 1.20.1, NeoForge 1.21.1 and Fabric 1.21.1 are supported
2. When a nickname shares nothing with the real name and the plugin attaches neither a "click to whisper" event nor a tab-list rename, the message shows as a grey system line
3. When the server rewrites player messages into a broadcast format isomorphic to chat (e.g. `系统>>Steve: xxx`), the client cannot reliably detect it
4. Chat formats with only whitespace (no separator) between name and content cannot be parsed
5. Same-name players colliding with system prefixes on cracked servers cannot be told apart in the extreme case
6. NCR-encrypted chat (very niche) is shown as ciphertext
7. Custom fonts may affect bubble width, wrapping and click regions
8. The whisper command format is up to the server; `/msg`, `/tell`, `/w` are not all guaranteed
9. Unicode arrow separators (`→`, `⇒`) are not auto-recognized — loosening this would misclassify comma broadcasts

---

## Privacy & data

> [!WARNING]
> Chat history is stored in plain text under `.minecraft/e33chat/history/` on your machine. **Do not use it on public or untrusted computers**, to avoid leaking sensitive information.

- Chat history stays on your machine only — it is never uploaded or sent to the author or any third party
- The server mod only relays messages (@ sync / quote sync / history sync); it collects no client data
- Sensitive commands carrying credentials (`/login`, `/register`, ...) are skipped and never written to the history file
- Whether to save history and whether to enable sync can both be turned off in config

---

## FAQ

### Is the server mod required?

No. Installing it additionally unlocks quote sync, @ mention sync, chat-history sync for new players, and head teleport via `/tpa`.

### How do I open settings?

Bottom-left gear → Menu → Settings.

Client config: `config/e33chat-client.toml` ｜ Server config: `saves/<world>/serverconfig/e33chat-server.toml`

### Where is chat history stored?

`.minecraft/e33chat/history/`, split per world / server. Plain-text format, one line per message (`time\tsender\tcontent`), readable in any text editor; time separators gain a date across days.

### How often is the history saved?

Every 30 seconds, plus on clean exits (world change / leaving the server). A crash or force-close loses at most the last 30 s; legacy JSON files are migrated automatically.

### How do I disable chat-history sync?

Set `history_enabled = false` in the server config and restart the server.

### How do I turn off background blur?

Settings → Chat Screen → Background blur, off.

### Why is a message shown as a grey line?

When the client is not sure a line was said by a player, it conservatively shows it as grey (see [Known limitations](#known-limitations)). Nickname plugins and unusual broadcast formats are common causes.

### What about `&` and `§` color codes?

The mod parses `&` into color locally (a server color plugin is needed for others to see the color). Outgoing text always sends the raw `&` and never `§`, so you are never kicked for color codes.

### Are nickname plugins supported?

Partially. A message is attributed correctly when either holds:

- The plugin attaches a "click to whisper" event to the nickname
- The plugin updates the tab-list name

When the nickname shares nothing with the real name and neither channel exists, the message shows as a grey system line.

### Can I include it in a modpack?

Yes, no extra permission needed.

---

## Troubleshooting

1. Confirm the Minecraft version matches the mod version
2. Confirm the mod loader is installed correctly
3. Confirm you have not mixed platform JARs (e.g. Forge and Fabric at once)
4. Back up `config/e33chat-client.toml`, then delete it to test for config corruption
5. Keep only E33Chat to isolate mod conflicts
6. Check for custom fonts or resource packs
7. Look for `[e33chat]` errors in `.minecraft/logs/latest.log`
8. When reporting, include versions, mod list, `latest.log`, screenshots and reproduction steps

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

- Forge 1.20.1: Java 17+, supports `--offline`
- NeoForge 1.21.1 / Fabric 1.21.1: Java 21+
- Run tests: `./gradlew cleanTest test --offline -PrunTests`

---

## Reporting issues

Open an [Issue](https://github.com/E33EPUS/E33Chat/issues) and, where possible, include:

- E33Chat version + Minecraft version + mod-loader version
- List of other chat-related mods
- GUI Scale + custom fonts / resource packs
- `.minecraft/logs/latest.log`
- Screenshots or video + stable reproduction steps

---

## License

[MIT License](LICENSE)

Copyright &copy; 2026 E33EPUS
