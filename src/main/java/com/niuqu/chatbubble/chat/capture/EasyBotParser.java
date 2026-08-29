package com.niuqu.chatbubble.chat.capture;

import com.niuqu.chatbubble.store.ChatMessageStore;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.text.Text;

/**
 * Built-in parser for EasyBot QQ group messages relayed into the game as
 * system broadcasts.
 *
 * EasyBot's default relay template looks like:
 *   [群名] <昵称(QQ号)> 内容
 * The angle-bracket form is a strong structural signal, and the optional QQ
 * number makes false positives unlikely. This parser only runs when the server
 * enables {@code easybot_compat}; server templates ({@code {external}}) remain
 * available as an override when a server owner customizes the EasyBot template.
 */
public final class EasyBotParser {
    private EasyBotParser() {}

    // [prefix] <name(123456)> content  — prefix must be a short bracket label.
    // (?s) lets content contain newlines, matching TemplateMatcher behaviour.
    private static final Pattern DEFAULT_FORMAT = Pattern.compile(
        "^\\[([^\\]]+)\\]\\s*<([^>]*)>\\s*(?s:(.*))$");

    // QQ numbers are 5-12 digits, optionally wrapped in parentheses at the end
    // of the angle-bracket name area: "昵称(123456)" or just "123456".
    private static final Pattern QQ_AT_END = Pattern.compile("\\(?(\\d{5,12})\\)?$");

    private static final Set<String> BROADCAST_LABELS = Set.of(
        "系统", "公告", "服务器", "广播", "提示", "通知",
        "system", "server", "notice", "broadcast", "announcement", "alert");

    public static ChatMessageStore.SenderMeta tryParse(Text message, String text) {
        if (text == null || text.isEmpty()) return null;
        Matcher m = DEFAULT_FORMAT.matcher(text);
        if (!m.matches()) return null;

        String groupName = m.group(1).trim();
        String nameArea = m.group(2).trim();
        if (groupName.isEmpty() || nameArea.isEmpty()) return null;

        String nick = null;
        String qq = null;
        Matcher qm = QQ_AT_END.matcher(nameArea);
        if (qm.find()) {
            qq = qm.group(1);
            String before = nameArea.substring(0, qm.start()).trim();
            // Keep only the part before the opening parenthesis, if any.
            int paren = before.lastIndexOf('(');
            if (paren >= 0) before = before.substring(0, paren).trim();
            if (!before.isEmpty()) nick = before;
        } else if (nameArea.matches("\\d{5,12}")) {
            qq = nameArea;
        } else {
            nick = nameArea;
        }

        // Without a QQ number, require the bracket label not to be a generic
        // broadcast label so ordinary "[公告] <Server> ..." lines stay system.
        if (qq == null && isBroadcastLabel(groupName)) return null;

        String displayName = (nick != null && !nick.isEmpty()) ? nick : qq;
        if (displayName == null || displayName.isEmpty()) return null;
        String rawPlayerName = qq != null ? qq : displayName;

        int contentStart = m.start(3);
        int contentEnd = m.end(3);
        Text contentComp = ChatMessageStore.sliceStyled(message, contentStart, contentEnd);
        Text nameComp = Text.literal(displayName);
        return new ChatMessageStore.SenderMeta(
            new UUID(0, 0), nameComp, contentComp, false,
            rawPlayerName, false, null);
    }

    private static boolean isBroadcastLabel(String s) {
        String zone = s.trim();
        while (zone.length() >= 2) {
            char open = zone.charAt(0);
            char close = zone.charAt(zone.length() - 1);
            if ((open == '[' && close == ']') || (open == '【' && close == '】')
                || (open == '<' && close == '>') || (open == '(' && close == ')')) {
                zone = zone.substring(1, zone.length() - 1).trim();
            } else {
                break;
            }
        }
        return !zone.isEmpty() && BROADCAST_LABELS.contains(zone.toLowerCase(java.util.Locale.ROOT));
    }
}
