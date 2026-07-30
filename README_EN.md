[简体中文](README.md)

# E33Chat

*Rebuilds the vanilla chat HUD in chat-app style*

**Client required, server optional**

| | Forge | NeoForge | Fabric |
|---|---|---|----|
| 1.20.1 | ✅ | — | — |
| 1.21.1 | — | ✅ | ✅ |

## Vanilla Improvements

- 💬 **Chat bubbles** — Messages with player heads and names
- 💾 **Chat history** — Saved per world / server, restored on rejoin
- 📝 **Input preserved** — Typed text kept when closing chat
- 🚫 **Anti-spam** — Consecutive duplicate messages merged with a counter

## Highlights

- 👥 **Whisper sidebar** — Online player list, click to whisper
- 🔍 **Chat search** — Keyword search with real-time matching
- @ **Mention autocomplete** — Type `@` for a popup player list, or left-click a player head
- 📌 **Quick chat** — Save and quick-fill common phrases
- 😊 **Emoji & kaomoji** — Emoji panel + kaomoji picker
- 📋 **Copy & quote reply** — Right-click a message to copy or quote reply
- 👤 **Player head actions** — Right-click a player head to teleport or whisper
- 🔔 **Preview & hints** — HUD preview at bottom-left; popup hints for @mentions, quotes, and system messages
- 🔊 **Notification sounds** — Per-type toggles for system, @ / quote, whisper, and public messages
- 🎨 **Themes** — Dark/light theme with customizable bubble color, text color, and corner radius
- 🕐 **Time separators** — Timestamp dividers at configurable intervals
- 🌈 **Colored messages** — Supports `&` color/format codes (for servers with a color plugin installed)

## Compatibility

- **No Chat Reports** and similar plugins — Automatically compatible since 2.1.0; no config option to enable.
- **CustomSkinLoader** — Install this mod to show offline players' heads.

## FAQ

**Server required?** No, but installing the server-side mod enables quote reply, @mention sync, chat history sync (new players receive recent messages), and the `use_tpa` option (player-head teleport uses `/tpa`).

**How to configure?** Gear icon (bottom-left) → Menu → Settings

- Client config: `config\e33chat-client.toml`

- Server config: `saves\<world>\serverconfig\e33chat-server.toml`

**Where is chat history?** `.minecraft\e33chat\history`

**How to disable chat history sync?** Set `history_enabled = false` in the world's server config and restart.

**Modpack?** Go ahead.

**Nickname plugins?** Partial support. Messages attribute to the real player when the plugin attaches a "click to whisper" event to nicknames or updates the tab-list name too. If a nickname shares nothing with the real name and neither channel exists, the message shows as a plain system line — the client has no source for the nickname→player mapping.

**Found a bug?** [Report it here](https://github.com/E33EPUS/E33Chat/issues)
