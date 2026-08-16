package com.niuqu.chatbubble.chat.capture;

import com.niuqu.chatbubble.chat.MessagePresentation;
import com.niuqu.chatbubble.store.ChatMessageStore;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.UUID;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

/**
 * Unified guard-layer orchestration shared by the system/disguised channels.
 *
 * Extracted from ChatListenerMixin during the 2.3.14 restructure: the two
 * channels previously carried ~50 duplicated lines each for the Layer-3
 * decorated-name parse; the name-area extraction helpers moved along.
 */
public final class ChatPipeline {
    private ChatPipeline() {}

    // Pulls styled server prefixes out of the decorated line: "[Group]<Steve> hi" -> "[Group]Steve"
    public static Text extractDecoratedName(Text fullLine, String contentStr,
                                                 String rawName, Text fallback) {
        if (contentStr == null || contentStr.isEmpty()) return fallback;
        String fullStr = fullLine.getString();
        int idx = fullStr.lastIndexOf(contentStr);
        if (idx <= 0) return fallback;
        return cleanNameArea(fullLine, 0, idx, rawName, fallback);
    }

    public static Text cleanNameArea(Text fullLine, int a, int b,
                                          String rawName, Text fallback) {
        String fullStr = fullLine.getString();
        while (a < b && Character.isWhitespace(fullStr.charAt(a))) a++;
        while (b > a) {
            char ch = fullStr.charAt(b - 1);
            if (Character.isWhitespace(ch) || ch == ':' || ch == '：' || ch == '»') b--;
            else break;
        }
        if (b <= a) return fallback;
        Text nameArea = ChatMessageStore.sliceStyled(fullLine, a, b);
        String ns = nameArea.getString();
        if (rawName != null && !rawName.isEmpty()) {
            String bracketed = "<" + rawName + ">";
            int p = ns.indexOf(bracketed);
            if (p >= 0) {
                var out = Text.empty();
                if (p > 0) out.append(ChatMessageStore.sliceStyled(nameArea, 0, p));
                out.append(ChatMessageStore.sliceStyled(nameArea, p + 1, p + 1 + rawName.length()));
                int tail = p + bracketed.length();
                if (tail < ns.length()) out.append(ChatMessageStore.sliceStyled(nameArea, tail, ns.length()));
                return out;
            }
            // Team-decorated names sit inside the brackets: "<[Team]Steve>" -> "[Team]Steve"
            if (ns.length() > 2 && ns.charAt(0) == '<' && ns.charAt(ns.length() - 1) == '>') {
                return ChatMessageStore.sliceStyled(nameArea, 1, ns.length() - 1);
            }
        }
        return nameArea;
    }

    /**
     * Layer 3: parse a decorated player line — text-level fallback for servers
     * that strip click events. Returns null when the line is not a player line
     * (or is a broadcast sentence with a whitespace-only gap).
     */
    public static ChatMessageStore.SenderMeta tryParsePlayerLine(
            Text message, String text, String logTag) {
        var connection = MinecraftClient.getInstance().player.networkHandler;
        if (connection == null) return null;
        var namesSet = new LinkedHashSet<String>();
        connection.getPlayerList().forEach(info -> {
            for (String cand : ChatClassifier.nameCandidates(info)) namesSet.add(cand);
        });
        namesSet.addAll(ChatMessageStore.knownNameVariants());
        var onlineNames = new ArrayList<>(namesSet);
        var parsed = MessagePresentation.parseDecoratedPlayerLine(text, onlineNames);
        if (!parsed.isPresent()) return null;
        var pl = parsed.orElseThrow();
        // 偏移来自 parser（双侧剥 § 后的映射），嵌色名 S§6t§beve 也正确
        int nameIdx = pl.nameStart();
        int nameEnd = pl.nameEnd();
        int contentStart = pl.contentStart();
        // Whitespace-only gap = broadcast sentence (Steve joined the game),
        // not chat: server chat formats always separate name and content
        if (MessagePresentation.isWhitespaceOnlyGap(text, nameEnd, contentStart)) {
            ChatMessageStore.debugLog(() -> "[e33chat] " + logTag + "(line skip: broadcast sentence) | text='" + text + "'");
            return null;
        }
        var info = connection.getPlayerList().stream()
            .filter(i -> {
                for (String cand : ChatClassifier.nameCandidates(i))
                    if (cand.equals(pl.playerName())) return true;
                return false;
            }).findFirst().orElse(null);
        UUID uid;
        if (info != null) {
            uid = info.getProfile().getId();
        } else {
            UUID su = ChatMessageStore.findSeenUuid(pl.playerName());
            uid = su != null ? su : new UUID(0, 0);
        }
        Text displayName = extractDecoratedName(message, pl.content(), pl.playerName(),
            Text.literal((text.substring(0, nameIdx) + pl.playerName()).trim()));
        Text contentComp = ChatMessageStore.sliceStyled(message, contentStart, text.length());
        ChatMessageStore.debugLog(() -> "[e33chat] " + logTag + "(player line) | name=" + pl.playerName() + " | content='" + pl.content() + "'");
        return new ChatMessageStore.SenderMeta(
            uid, displayName, contentComp, false,
            info != null ? info.getProfile().getName() : pl.playerName(),
            false, null);
    }
}
