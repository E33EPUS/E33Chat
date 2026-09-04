package com.niuqu.chatbubble.chat.capture;

import com.niuqu.chatbubble.store.ChatMessageStore;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.network.chat.Component;

/**
 * Built-in parser for EasyBot QQ group messages relayed into the game as
 * system broadcasts.
 *
 * EasyBot's Minecraft-side mod is only a renderer: the "[群名]" / "<昵称>" part
 * of a line is assembled bot-side, so the exact shape depends on the server's
 * template. Shapes seen in the wild:
 *   [群名] <昵称(QQ号)> 内容      (EasyBot's default template)
 *   [群名] <昵称> 内容
 *   <昵称> 内容                   (group label removed from the template)
 *   <昵称（群名片）> 内容
 * The leading [label] is therefore optional — the angle-bracket name followed
 * by the content is the structural signal. Server templates ({@code {external}})
 * remain available as an explicit override when a server owner customizes the
 * EasyBot template beyond these shapes.
 */
public final class EasyBotParser {
    private EasyBotParser() {}

    // (?:[label])? <name> content  — (?s) lets content span newlines,
    // matching TemplateMatcher behaviour.
    private static final Pattern RELAY_FORMAT = Pattern.compile(
        "^(?:\\[([^\\]]*)\\]\\s*)?<([^>]*)>\\s*(?s:(.*))$");

    // QQ numbers are 5-12 digits, optionally wrapped in parentheses (half- or
    // full-width) at the end of the angle-bracket name area: "昵称(123456)".
    private static final Pattern QQ_AT_END = Pattern.compile("[（(]?(\\d{5,12})[)）]?$");

    // Longest plausible sender name — beyond this the line is not a relay.
    private static final int MAX_NAME = 32;

    private static final Set<String> BROADCAST_LABELS = Set.of(
        "系统", "公告", "服务器", "广播", "提示", "通知",
        "system", "server", "notice", "broadcast", "announcement", "alert");

    public static ChatMessageStore.SenderMeta tryParse(Component message, String text) {
        if (text == null || text.isEmpty()) return null;
        Matcher m = RELAY_FORMAT.matcher(text);
        if (!m.matches()) return null;

        String groupName = m.group(1) == null ? "" : m.group(1).trim();
        String nameArea = m.group(2) == null ? "" : m.group(2).trim();
        String content = m.group(3);
        if (nameArea.isEmpty() || content == null || content.isBlank()) return null;
        if (nameArea.length() > MAX_NAME || nameArea.indexOf('\n') >= 0) return null;

        String nick = null;
        String qq = null;
        Matcher qm = QQ_AT_END.matcher(nameArea);
        if (qm.find()) {
            qq = qm.group(1);
            String before = nameArea.substring(0, qm.start()).trim();
            // Keep only the part before the opening parenthesis, if any.
            int paren = before.lastIndexOf('(');
            if (paren < 0) paren = before.lastIndexOf('（');
            if (paren >= 0) before = before.substring(0, paren).trim();
            if (!before.isEmpty()) nick = before;
        } else if (nameArea.matches("\\d{5,12}")) {
            qq = nameArea;
        } else {
            nick = nameArea;
        }

        String displayName = (nick != null && !nick.isEmpty()) ? nick : qq;
        if (displayName == null || displayName.isEmpty()) return null;

        // Without a QQ number the line carries no strong EasyBot signal, so
        // generic broadcast labels ("[公告] <Server> ...", "<系统> ...") stay
        // system messages.
        if (qq == null && (isBroadcastLabel(groupName) || isBroadcastLabel(displayName))) return null;

        // A locally known player relayed through a system packet keeps its
        // profile UUID (and therefore its skin) only on the player path —
        // step aside so ChatPipeline can claim the line instead.
        if (isKnownPlayer(displayName)) return null;

        String rawPlayerName = qq != null ? qq : displayName;

        Component contentComp = ChatMessageStore.sliceStyled(message, m.start(3), m.end(3));
        Component nameComp = Component.literal(displayName);
        return new ChatMessageStore.SenderMeta(
            new UUID(0, 0), nameComp, contentComp, false,
            rawPlayerName, false, null);
    }

    /**
     * Exact-match only: {@link ChatClassifier#resolveOnlinePlayer} also does a
     * substring fallback, which would hand every QQ nickname containing a
     * player name back to the player path (and then drop it entirely).
     */
    private static boolean isKnownPlayer(String displayName) {
        try {
            if (ChatMessageStore.knownNameVariants().contains(displayName)) return true;
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc == null || mc.player == null || mc.player.connection == null) return false;
            for (net.minecraft.client.multiplayer.PlayerInfo info : mc.player.connection.getOnlinePlayers()) {
                for (String cand : ChatClassifier.nameCandidates(info)) {
                    if (cand.equalsIgnoreCase(displayName)) return true;
                }
            }
        } catch (Throwable t) {
            // Headless (unit tests) or a broken world — treat as "not a player".
            return false;
        }
        return false;
    }

    private static boolean isBroadcastLabel(String s) {
        String zone = s.trim();
        while (zone.length() >= 2) {
            char open = zone.charAt(0);
            char close = zone.charAt(zone.length() - 1);
            if ((open == '[' && close == ']') || (open == '【' && close == '】')
                || (open == '<' && close == '>') || (open == '(' && close == ')')
                || (open == '（' && close == '）')) {
                zone = zone.substring(1, zone.length() - 1).trim();
            } else {
                break;
            }
        }
        return !zone.isEmpty() && BROADCAST_LABELS.contains(zone.toLowerCase(java.util.Locale.ROOT));
    }
}
