package com.niuqu.chatbubble.chat.capture;

import com.niuqu.chatbubble.chat.MessagePresentation;
import com.niuqu.chatbubble.store.ChatMessageStore;
import java.util.UUID;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

/**
 * Whisper detection on the text layer (NCR-converted servers where the chat
 * type is stripped): online-player scan first, seen-cache fallback for
 * offline senders. Keyword gate via MessagePresentation.
 *
 * Extracted from ChatListenerMixin during the 2.3.14 restructure; behaviour
 * unchanged.
 */
public final class WhisperDetector {
    private WhisperDetector() {}

    public static ChatMessageStore.SenderMeta detectWhisperInSystemMessage(String text, String logTag) {
        var connection = MinecraftClient.getInstance().player.networkHandler;
        if (connection == null) return null;
        // G3: 消息嵌 legacy 色码（S§6t§beve）时整条剥 § 再做名字锚点匹配
        String clean = text.replaceAll("§.", "");
        for (var info : connection.getPlayerList()) {
            String profile = info.getProfile().getName();
            for (String cand : ChatClassifier.nameCandidates(info)) {
                int idx = clean.indexOf(cand);
                if (idx >= 0 && idx < 30) {
                    if (MessagePresentation.hasWhisperKeywordBeforeColon(clean)) {
                        String content = MessagePresentation.extractWhisperContent(clean, cand);
                        UUID senderId = info.getProfile().getId();
                        ChatMessageStore.debugLog(() -> "[e33chat] System(" + logTag + ") | text='" + clean + "' | name=" + cand + " | content='" + content + "'");
                        return new ChatMessageStore.SenderMeta(
                            senderId,
                            Text.literal(cand),
                            Text.literal(content),
                            false,
                            profile,
                            true, profile
                        );
                    }
                }
            }
        }
        // cache fallback: try seen (offline) players
        for (var sp : ChatMessageStore.knownNameVariants()) {
            int idx = clean.indexOf(sp);
            if (idx >= 0 && idx < 30) {
                if (MessagePresentation.hasWhisperKeywordBeforeColon(clean)) {
                    UUID su = ChatMessageStore.findSeenUuid(sp);
                    if (su != null) {
                        String content = MessagePresentation.extractWhisperContent(clean, sp);
                        ChatMessageStore.debugLog(() -> "[e33chat] System(" + logTag + "/cache) | text='" + clean + "' | name=" + sp + " | content='" + content + "'");
                        return new ChatMessageStore.SenderMeta(
                            su,
                            Text.literal(sp),
                            Text.literal(content),
                            false,
                            sp,
                            true, sp
                        );
                    }
                }
            }
        }
        return null;
    }
}
