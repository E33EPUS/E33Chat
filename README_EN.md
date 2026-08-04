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
  <img alt="Version" src="https://img.shields.io/badge/Version-2.3.0-informational">
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
- [UI texture customization (resource-pack overridable)](#ui-texture-customization-resource-pack-overridable)
- [Server-side bonus](#server-side-bonus)
- [Server message-format templates](#server-message-format-templates)
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
| Fabric Loader | Per platform | 0.16.0+ (1.21.1) |
| Fabric API | Per platform | Any compatible version (1.21.1) |
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
4. In the *Notifications* category configure the @ sound, banner, system-message banner, whisper sound and master volume

---

## Features

- 💬 **Chat bubbles** — Messages with player heads and names; custom bubble color, text color and corner radius; dark / light themes; panel background blur with adjustable opacity
- 🛠️ **Settings screen** — Five UI-element tabs (Chat Screen / HUD / Notifications / Sidebar / Advanced) with a collapsible sub-category tree, inline color palette, a live bubble preview that follows the corner radius, snapshot-based save / exit, and always-on scrollbars with eased smooth scrolling
- 💾 **Chat history** — Saved per world / server and restored on rejoin; plain-text log format (readable in any text editor); WeChat-style time separators with date across days; auto-saved every 30 s
- ✅ **Anti-spam** — Consecutive duplicate messages merged with a counter (on by default)
- @ **Mention autocomplete** — Type `@` for a popup player list, or left-click a head to @ them; a sound + banner fires when you are @'d or quoted
- 👥 **Whisper sidebar** — Online player list, click a name to whisper; bouncing unread dot; separate whisper banner + sound; public / whisper split view
- 🔍 **Chat search** — Real-time matching of message content and sender name, jump with up / down, Chinese supported
- 😊 **Emoji & kaomoji** — Emoji panel + kaomoji picker, inserted at the cursor
- 📌 **Quick phrases** — Save common phrases and fill them with one click
- 📋 **Copy & quote reply** — Right-click a message to copy or quote-reply
- 👤 **Head actions** — Right-click a head to whisper / teleport, left-click to @
- 🔔 **Notification banner** — Slide-in popup at the top covering @ / quote / whisper / system (system banner on by default), with a jump-to-mention button; master volume slider with per-type toggles
- 🗨️ **Vanilla chat box** — No custom HUD preview anymore; the vanilla chat renders as usual (shifted up clear of the HUD icon), so ChatHeads / ChatAnimation-style mods work out of the box
- 🌈 **Colored messages** — Supports `&` color / format codes, rendered locally without changing what is sent
- 📝 **Input preserved** — Typed text is kept when the chat closes
- 🧩 **Message-format templates** — The server declares its chat format so the mod can parse plugin/NCR-rewritten lines (configured via `/e33chat gui`)

---

## Chat bubbles & message display

- Every message renders as a bubble with a head and player name
- Your bubbles sit on the right, others on the left, each color configurable
- Bubble corner radius 0–10 (0 = square)
- Whispers show as `<name>[PM] content` and quote replies as `<name>[Quote] content` (yellow tag); names keep server prefix decorations and team colors
- The vanilla chat box renders on the HUD as usual (shifted up 8 px clear of the E33Chat icon); ChatHeads / ChatAnimation-style mods work automatically
- A banner pops in at the top on @ / quote / whisper / system messages

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
- System-message banner is on by default (toggle in the notification settings)
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

## UI texture customization (resource-pack overridable)

UI structural elements, icons and state highlights all render from textures; the defaults are 16×16 solid-color PNGs baked into the JAR (SimpleTexture, lazy-loaded). Drop a PNG at the same path into a resource pack and the element is overridden — colors, patterns and gradients all come from the image. F3+T hot-reloads, no restart needed.

**Path convention**: `assets/e33chat/textures/gui/{dark|light}/<element>.png`. Default textures are 16×16 solid-color stretch; hover/selected/close state highlights are texture-driven too (overridable since v2.2.8).

| Element | File | Rendering notes |
|---|---|---|
| Panel bg / title bar / bottom bar / sidebar / divider / input | `panel_bg.png` etc. | stretch, since v2.2.4 |
| Context menu / popup / whisper bar / config bg / content bg | `context_menu_bg.png` etc. | stretch, v2.2.4–2.2.5 |
| Scrollbar track / thumb | `scrollbar_track.png` `scrollbar_thumb.png` | stretch, dynamic alpha channel |
| Time separator | `time_sep_bg.png` | stretch, base color overridable |
| "Copied" toast | `toast_bg.png` | stretch × dynamic alpha (2.2.4 black-block fixed: bake opaque + alpha channel) |
| Quick-chat scrollbar | `quick_scrollbar_track.png` `quick_scrollbar_thumb.png` | white × tint (theme color / hover state) |
| Strong-hint bar | `strong_hint_bg.png` | stretch, base color overridable |
| State highlights (hover/selected/close) | `hover_bg.png` `sidebar_selected.png` `sidebar_hover.png` `context_hover.png` `close_bg.png` `close_hover.png` | stretch, overridable since v2.2.8 |
| Icons (30) | `settings.png` `copy.png` etc. | 16×16 original size, overridable |

> ⚠️ **Chat bubbles / quote blocks / @-mention banner** are SDF-rounded (shader math) — smooth at any corner-radius config, but **not resource-pack overridable**, since they are not texture-driven. (2.2.8+ tried 9-slice texturing; sampling mismatch/upscaling issues, reverted to SDF.)

**Example**: the `E33Chat-Texture-Demo` pack (in the test server's version resourcepacks dir) shows time-separator and toast overrides — enable it in-game via Options → Resource Packs → Available; F3+T hot-reloads, no restart needed.

---

## Server-side bonus

The server mod is optional. Installing it additionally enables:

- Server-side quote pending & sync
- Cross-client @ mention sync (Chinese names included)
- New players receive recent chat history on join
- `use_tpa`: head teleport uses `/tpa` instead
- Message-format templates: the server declares its chat format and syncs it to every client (see below)

> History distribution and the `/tpa` switch must be enabled manually in the server config file; templates are configured via `/e33chat gui` or commands (or by editing the server config file directly).

---

## Server message-format templates

Plugins (or No Chat Reports-style no-report mods) often rewrite chat lines: title prefixes, arrows instead of colons, colored names. E33Chat's built-in guards can only tell "a player is speaking" — they cannot read the exact name and body out of an unfamiliar format. **A template declares the format to the client**: where the display name, the body (and for whispers, the sender/receiver) sit. A template hit parses exactly and skips the guard chain.

### Enabling

Install E33Chat on the server → after players join, an OP runs `/e33chat gui` to open the **server-config screen**, adds templates in the Chat Templates / Whisper Templates categories, and saves. Templates sync automatically to every client (including players who join later).

### Quick start

1. **Generate from a message** — click "Generate from message…", paste a real line with a name (e.g. `[VIP]Steve: hello`), and the template is inferred automatically
2. **One-click presets** — the preset section lists common plugin defaults (EssentialsX / DeluxeChat / CMI, ...); click `+` to add
3. **Preview** — type any message in the preview box to see the parsed name and body instantly
4. **Type manually** — copy the shape you see in chat; separators go in verbatim

### Field placeholders

| Placeholder | Meaning |
|---|---|
| `{display_name}` | Player display name (full decorated name with titles) |
| `{name}` | Player name (equivalent to `{display_name}`; use one of the two) |
| `{prefix}` | Prefix decoration (titles, ...) |
| `{content}` | Message body (exactly one; may sit anywhere — suffix styles work too) |
| `{sender}` / `{target}` | Whisper sender / receiver (marking the template as whisper) |
| `{sep}` | Auto-matches `>>` / `:` / `：` / `»` / `>` or plain spaces — one template covers many separator styles |

### Commands (OP)

```
/e33chat template list                      # list templates
/e33chat template set chat <template>       # add a chat template
/e33chat template set whisper <template>    # add a whisper template
/e33chat template remove chat <index>       # remove
/e33chat template clear chat                # clear (fall back to guards)
/e33chat template test chat <index> <text>  # dry-run parse
```

An empty template list disables templates and falls back to the guards — everything works as before.

---

## Message recognition & compatibility

E33Chat rebuilds the "who said this" layer of the chat HUD, aiming to tell player messages apart from system / broadcast ones. System-channel messages are judged in this order (first hit wins):

1. **Classifier** — known translation keys (whisper / public chat / broadcast) route deterministically
2. **Echo suppression** — your own messages are not shown again as grey lines
3. **Template layer** (2.2.6+) — when the server configured templates, parse exactly per the declaration; see [Server message-format templates](#server-message-format-templates)
4. **Whisper keywords** — whispers embedded in system lines (`whisper` / `whispers` / `msg` / `pm` / `tell`, before the first colon)
5. **Click events** — when the message carries a "click to whisper" structure, attribute by the real name in the command (nickname-server antenna)
6. **Decorated player line** — name anchor + separator structure (`Steve: hi`, `<Steve> hi`, `Steve >> hi`, suffix titles, legacy `§` codes, bare Chinese short names)
7. **Grey fallback** — when none confirms, conservatively show as a grey system line, never misattribute

Supporting mechanics:
- Auto-compatible with No Chat Reports and similar no-report plugins (since 2.1.0, no config needed)
- Player identity is UUID-first with name fallback, easing same-name collisions on cracked servers
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
| Chat-format plugins (EssentialsChat / CMI / DeluxeChat, ...) | Adaptable via server-configured templates (with common presets), see [Server message-format templates](#server-message-format-templates) |

---

## Known limitations

1. Only Forge 1.20.1, NeoForge 1.21.1 and Fabric 1.21.1 are supported
2. When a nickname shares nothing with the real name and the plugin attaches neither a "click to whisper" event nor a tab-list rename, the message shows as a grey system line (templates cannot help either — the name gate shares the same resolution source as the guards)
3. When the server rewrites player messages into a broadcast format isomorphic to chat (e.g. `Server>>Steve: xxx`), the client cannot reliably detect it
4. Chat formats with only whitespace (no separator) between name and content cannot be parsed
5. Same-name players colliding with system prefixes on cracked servers cannot be told apart in the extreme case
6. NCR-encrypted chat (very niche) is shown as ciphertext
7. Custom fonts may affect bubble width, wrapping and click regions
8. The whisper command format is up to the server; `/msg`, `/tell`, `/w` are not all guaranteed
9. Unicode arrow separators (`→`, `⇒`) are not auto-recognized — loosening this would misclassify comma broadcasts
10. Templates only act on **system-channel** messages (the channel NCR / legacy plugins / hybrid servers downgrade player chat to); decorative-format plugins that keep the signed channel stay reliable on their own. Template edge cases:
    - A decoration containing a separator (e.g. a colon in a title) may truncate the name — change the separator or reorder the template
    - A completely unknown new player (never seen online) whispering cannot resolve the name and falls back to the guards
    - `{prefix}` adjacent to `{display_name}` without an anchor is absorbed into the display name (same behavior as the guard path)

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

### The server changed the chat format (plugins/NCR) and messages don't line up?

Use **message-format templates** (2.2.6+): as an OP run `/e33chat gui`, paste a real chat line in the Chat Templates tab via "Generate from message…", or add a common format from the presets, then save — it syncs to the whole server. An empty template list falls back to the guards.

### The template still doesn't match?

Templates require the name to resolve to a player (online / seen / yourself). A decoration containing a separator may truncate the name (change the separator); a nickname sharing nothing with the real name, while offline, is beyond both templates and guards and shows as grey — a known boundary.

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
