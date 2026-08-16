package com.niuqu.chatbubble.store;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.niuqu.chatbubble.store.ChatMessageStore.ChatMessage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
public final class HistoryStore {
    private HistoryStore() {}

    private static final Gson GSON = new Gson();

    public static File getHistoryFile(String worldKey) {
        // Keep Unicode (Chinese world names stay readable); only strip characters
        // that break file systems / path parsing. The SHA-256 short hash disambiguates
        // worlds whose sanitized names collide.
        String safe = worldKey.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_");
        return new File(MinecraftClient.getInstance().runDirectory,
            "e33chat/history/" + safe + "_" + sha256Short(worldKey) + ".json");
    }

    // Pre-2.2.3 files used an ASCII-only sanitizer + String.hashCode; load them for
    // migration when the new path does not exist yet
    public static File getLegacyHistoryFile(String worldKey) {
        String safe = worldKey.replaceAll("[^a-zA-Z0-9_.\\-]", "_");
        String hash = Integer.toHexString(worldKey.hashCode());
        return new File(MinecraftClient.getInstance().runDirectory,
            "e33chat/history/" + safe + "_" + hash + ".json");
    }

    static String sha256Short(String s) {
        try {
            byte[] d = java.security.MessageDigest.getInstance("SHA-256")
                .digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8; i++) sb.append(String.format("%02x", d[i]));
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(s.hashCode());
        }
    }

    // ---- Plain-text history lines: date-time \t sender \t content \t flags ----
    // Open in any text editor and it reads like a log. Plain text only — colors
    // and click/hover data are dropped; the decorated prefix still shows as literal
    // text (e.g. "[称号]E33EPUS"). Flags: M=own, S=system, W=whisper (combinable,
    // empty when none). Fields escape \t \n \\ so parsing is unambiguous.
    // Pre-2.2.3 JSONL lines (starting with '{') still load.

    // Commands that carry credentials must never land in the history file —
    // mirrors the AuthMe-family login/register aliases
    public static boolean isSensitiveCommand(String text) {
        if (text == null) return false;
        String s = Formatting.strip(text);
        if (s == null) return false;
        s = s.trim();
        if (!s.startsWith("/")) return false;
        int sp = s.indexOf(' ');
        String cmd = sp < 0 ? s.substring(1) : s.substring(1, sp);
        if (cmd.isEmpty()) return false;
        switch (cmd.toLowerCase(java.util.Locale.ROOT)) {
            case "login": case "l": case "register": case "reg":
            case "auth": case "password": case "passwd":
            case "changepassword": case "changepass": case "cp":
                return true;
            default:
                return false;
        }
    }

    public static String toLine(ChatMessageStore.ChatMessage msg) {
        if (isSensitiveCommand(msg.content().getString())) return null;
        // JSONL, one message per line. senderJson/contentJson are full styled
        // components (colors, click/hover events survive the reload) and uuid
        // lets avatars resolve for offline players after re-joining.
        java.util.Map<String, Object> obj = new java.util.LinkedHashMap<>();
        obj.put("time", msg.time());
        obj.put("uuid", msg.senderUUID() != null ? msg.senderUUID().toString() : "");
        String senderJson = null, contentJson = null;
        try {
            senderJson = Text.Serialization.toJsonString(msg.senderName(), registries());
            contentJson = Text.Serialization.toJsonString(msg.content(), registries());
        } catch (Throwable ignored) {
            // Component codecs unavailable (headless test env / broken registries):
            // fall back to plain-text fields; styled fields are omitted.
        }
        if (senderJson != null) obj.put("senderJson", senderJson);
        else obj.put("sender", msg.senderName().getString());
        if (contentJson != null) obj.put("contentJson", contentJson);
        else obj.put("content", msg.content().getString());
        obj.put("own", msg.isOwn());
        obj.put("system", msg.isSystem());
        if (msg.replyContent() != null) obj.put("replyContent", msg.replyContent());
        if (msg.replySender() != null) obj.put("replySender", msg.replySender());
        if (msg.rawPlayerName() != null) obj.put("rawPlayerName", msg.rawPlayerName());
        if (msg.whisper()) obj.put("whisper", true);
        if (msg.whisperPartner() != null) obj.put("whisperPartner", msg.whisperPartner());
        return GSON.toJson(obj);
    }

    public static ChatMessageStore.ChatMessage fromLine(String line) {
        if (line.startsWith("{")) return fromJsonLine(line);
        String[] parts = line.split("\t", -1);
        if (parts.length < 3) return null;
        long millis;
        try {
            millis = java.time.LocalDateTime.parse(parts[0], DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        } catch (Exception e) {
            return null;
        }
        String flags = parts.length > 3 ? parts[3] : "";
        String content = unescapeField(parts[2]);
        if (content.isBlank()) return null;
        boolean whisper = flags.contains("W");
        String partner = null;
        String replySender = null;
        String replyContent = null;
        if (whisper && parts.length > 4) partner = unescapeField(parts[4]);
        if (parts.length > 5) replySender = unescapeField(parts[5]);
        if (parts.length > 6) replyContent = unescapeField(parts[6]);
        return new ChatMessage(
            new UUID(0, 0),
            parseStyledText(unescapeField(parts[1])),
            parseStyledText(content),
            millis,
            flags.contains("M"),
            flags.contains("S"),
            replyContent, replySender, "", 1, null,
            whisper, partner
        );
    }

    // Legacy JSONL branch: one message per line as {"sender":...,"content":...}
    static ChatMessageStore.ChatMessage fromJsonLine(String line) {
        Map<String, Object> obj;
        try {
            obj = GSON.fromJson(line, new TypeToken<Map<String, Object>>(){}.getType());
        } catch (Exception e) {
            return null;
        }
        if (obj == null) return null;
        Object timeObj = obj.get("time");
        if (!(timeObj instanceof Number)) return null;
        UUID uuid = null;
        try { uuid = UUID.fromString(String.valueOf(obj.get("uuid"))); } catch (Exception ignored) {}
        Text senderName = componentFrom(obj, "senderJson", "sender");
        Text content = componentFrom(obj, "contentJson", "content");
        if (content == null || content.getString().isBlank()) return null;
        return new ChatMessage(
            uuid != null ? uuid : new UUID(0, 0),
            senderName != null ? senderName : Text.literal(""),
            content,
            ((Number) timeObj).longValue(),
            Boolean.TRUE.equals(obj.get("own")),
            Boolean.TRUE.equals(obj.get("system")),
            (String) obj.get("replyContent"),
            (String) obj.get("replySender"),
            "",
            1,
            (String) obj.get("rawPlayerName"),
            Boolean.TRUE.equals(obj.get("whisper")),
            (String) obj.get("whisperPartner")
        );
    }

    static Text componentFrom(Map<String, Object> obj, String jsonKey, String textKey) {
        String json = (String) obj.get(jsonKey);
        if (json != null) {
            try { return Text.Serialization.fromJson(json, registries()); } catch (Exception ignored) {}
        }
        String text = (String) obj.get(textKey);
        return text != null ? parseStyledText(text) : null;
    }
static net.minecraft.registry.RegistryWrapper.WrapperLookup registries() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null) {
            var world = mc.world;
            if (world != null) return world.getRegistryManager();
            var conn = mc.getNetworkHandler();
            if (conn != null) return conn.getRegistryManager();
        }
        try {
            return net.minecraft.registry.BuiltinRegistries.createWrapperLookup();
        } catch (Throwable ignored) {
            // Headless test fallback: an empty lookup serializes plain-text
            // components fine; registry-dependent hovers degrade instead of crashing
            return new net.minecraft.registry.RegistryWrapper.WrapperLookup() {
                @Override
                public java.util.stream.Stream<net.minecraft.registry.RegistryKey<? extends net.minecraft.registry.Registry<?>>> streamAllRegistryKeys() {
                    return java.util.stream.Stream.empty();
                }
                @Override
                public <T> java.util.Optional<net.minecraft.registry.RegistryWrapper.Impl<T>> getOptionalWrapper(
                        net.minecraft.registry.RegistryKey<? extends net.minecraft.registry.Registry<? extends T>> key) {
                    return java.util.Optional.empty();
                }
            };
        }
    }

    static String escapeField(String s) {
        return s.replace("\\", "\\\\").replace("\t", "\\t").replace("\r", "\\r").replace("\n", "\\n");
    }

    static String unescapeField(String s) {
        if (s.indexOf('\\') < 0) return s;
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char n = s.charAt(i + 1);
                if (n == 't') { out.append('\t'); i++; continue; }
                if (n == 'n') { out.append('\n'); i++; continue; }
                if (n == 'r') { out.append('\r'); i++; continue; }
                if (n == '\\') { out.append('\\'); i++; continue; }
            }
            out.append(c);
        }
        return out.toString();
    }

    // Section-sign codes ("§6...§r") back into a styled component; unknown codes
    // (e.g. a stray §x from a plugin) fall through as literal text
    public static Text parseStyledText(String s) {
        MutableText out = Text.empty();
        Style style = Style.EMPTY;
        StringBuilder buf = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '§' && i + 1 < s.length()) {
                if (buf.length() > 0) {
                    out.append(Text.literal(buf.toString()).fillStyle(style));
                    buf.setLength(0);
                }
                Style next = applySectionCode(style, s.charAt(i + 1));
                if (next == null) {
                    // Unknown code: keep it as literal text instead of swallowing it
                    buf.append(ch).append(s.charAt(i + 1));
                } else {
                    style = next;
                }
                i++;
            } else {
                buf.append(ch);
            }
        }
        if (buf.length() > 0) out.append(Text.literal(buf.toString()).fillStyle(style));
        return out;
    }

    static Style applySectionCode(Style style, char code) {
        switch (Character.toLowerCase(code)) {
            case '0': return style.withColor(Formatting.BLACK.getColorValue() != null ? Formatting.BLACK.getColorValue() : null);
            case '1': return style.withColor(Formatting.DARK_BLUE.getColorValue() != null ? Formatting.DARK_BLUE.getColorValue() : null);
            case '2': return style.withColor(Formatting.DARK_GREEN.getColorValue() != null ? Formatting.DARK_GREEN.getColorValue() : null);
            case '3': return style.withColor(Formatting.DARK_AQUA.getColorValue() != null ? Formatting.DARK_AQUA.getColorValue() : null);
            case '4': return style.withColor(Formatting.DARK_RED.getColorValue() != null ? Formatting.DARK_RED.getColorValue() : null);
            case '5': return style.withColor(Formatting.DARK_PURPLE.getColorValue() != null ? Formatting.DARK_PURPLE.getColorValue() : null);
            case '6': return style.withColor(Formatting.GOLD.getColorValue() != null ? Formatting.GOLD.getColorValue() : null);
            case '7': return style.withColor(Formatting.GRAY.getColorValue() != null ? Formatting.GRAY.getColorValue() : null);
            case '8': return style.withColor(Formatting.DARK_GRAY.getColorValue() != null ? Formatting.DARK_GRAY.getColorValue() : null);
            case '9': return style.withColor(Formatting.BLUE.getColorValue() != null ? Formatting.BLUE.getColorValue() : null);
            case 'a': return style.withColor(Formatting.GREEN.getColorValue() != null ? Formatting.GREEN.getColorValue() : null);
            case 'b': return style.withColor(Formatting.AQUA.getColorValue() != null ? Formatting.AQUA.getColorValue() : null);
            case 'c': return style.withColor(Formatting.RED.getColorValue() != null ? Formatting.RED.getColorValue() : null);
            case 'd': return style.withColor(Formatting.LIGHT_PURPLE.getColorValue() != null ? Formatting.LIGHT_PURPLE.getColorValue() : null);
            case 'e': return style.withColor(Formatting.YELLOW.getColorValue() != null ? Formatting.YELLOW.getColorValue() : null);
            case 'f': return style.withColor(Formatting.WHITE.getColorValue() != null ? Formatting.WHITE.getColorValue() : null);
            case 'k': return style.withObfuscated(true);
            case 'l': return style.withBold(true);
            case 'm': return style.withStrikethrough(true);
            case 'n': return style.withUnderline(true);
            case 'o': return style.withItalic(true);
            case 'r': return Style.EMPTY;
            default: return null;
        }
    }

    // Legacy file stores LocalTime (HH:mm:ss) with no date; anchor the file's
    // last-saved day on the file mtime and walk backwards: an earlier message
    // whose clock time is LATER than its successor crossed midnight
    public static List<ChatMessageStore.ChatMessage> loadLegacyFile(File f) {
        List<ChatMessage> out = new ArrayList<>();
        try (Reader r = new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8)) {
            List<Map<String, Object>> list = GSON.fromJson(r, new TypeToken<List<Map<String, Object>>>(){}.getType());
            if (list == null) return out;
            java.time.ZoneId zone = java.time.ZoneId.systemDefault();
            java.time.LocalDate day = java.time.Instant.ofEpochMilli(f.lastModified())
                .atZone(zone).toLocalDate();
            LocalTime latest = null;
            for (int i = list.size() - 1; i >= 0; i--) {
                Map<String, Object> obj = list.get(i);
                try {
                    UUID uuid = UUID.fromString((String) obj.get("senderUUID"));
                    Text senderName = null;
                    String snJson = (String) obj.get("senderNameJson");
                    if (snJson != null) {
                        try { senderName = Text.Serialization.fromJson(snJson, registries()); } catch (Exception ignored2) {}
                    }
                    if (senderName == null) senderName = Text.literal((String) obj.get("senderName"));
                    Text content = Text.Serialization.fromJson((String) obj.get("content"), registries());
                    if (content == null) content = Text.literal("");
                    if (content.getString().isBlank()) continue;
                    LocalTime t = LocalTime.parse((String) obj.get("time"), DateTimeFormatter.ISO_LOCAL_TIME);
                    if (latest != null && t.isAfter(latest)) day = day.minusDays(1);
                    latest = t;
                    long millis = java.time.LocalDateTime.of(day, t).atZone(zone).toInstant().toEpochMilli();
                    boolean isOwn = (Boolean) obj.getOrDefault("isOwn", false);
                    boolean isSystem = (Boolean) obj.getOrDefault("isSystem", false);
                    String replyContent = (String) obj.get("replyContent");
                    String replySender = (String) obj.get("replySender");
                    String rawPlayerName = (String) obj.get("rawPlayerName");
                    boolean whisper = Boolean.TRUE.equals(obj.get("whisper"));
                    String whisperPartner = (String) obj.get("whisperPartner");
                    out.add(0, new ChatMessage(uuid, senderName, content, millis,
                        isOwn, isSystem, replyContent, replySender, "", 1, rawPlayerName,
                        whisper, whisperPartner));
                } catch (Exception e) { com.mojang.logging.LogUtils.getLogger().warn("[e33chat] Failed to read/write chat history", e); }
            }
        } catch (Exception e) { com.mojang.logging.LogUtils.getLogger().warn("[e33chat] Failed to read/write chat history", e); }
        return out;
    }

    // Retention cleanup helper: files older than the configured days are dropped on
    // world join (0 = keep forever, the default)
    public static boolean isExpired(long fileMtime, long now, int retentionDays) {
        return retentionDays > 0 && now - fileMtime > retentionDays * 24L * 3600_000L;
    }
}
