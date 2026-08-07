package com.niuqu.chatbubble.chat.notification;
import com.niuqu.chatbubble.ChatBubbleClientSetup;
import com.niuqu.chatbubble.ChatBubbleScreen;
import com.niuqu.chatbubble.ChatMessageStore;
import com.niuqu.chatbubble.ChatMessageStore.SenderMeta;
import com.niuqu.chatbubble.GuiCompat;
import com.niuqu.chatbubble.chat.MentionDetector;
import com.niuqu.chatbubble.chat.notification.MentionNotificationBanner.NotificationType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import java.util.*;
public class MentionNotificationController {
    public static final MentionNotificationController INSTANCE = new MentionNotificationController();
    private final Map<String, Long> recentFingerprints = new LinkedHashMap<>() {
        protected boolean removeEldestEntry(Map.Entry<String, Long> e) {
            return size() > 32;
        }
    };
    public void onMessageCaptured(Text content, SenderMeta meta, int messageIndex, String replySender) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        var cfg = ChatBubbleClientSetup.config();
        if (cfg == null) return;
        String localName = mc.player.getName().getString();
        String contentStr = content.getString();
        boolean isMention = MentionDetector.isMentioned(contentStr, localName, cfg.mentionRequireAt(), replySender);
        String fingerprint = (meta.senderName() != null ? meta.senderName().getString() : "") + "|" + contentStr;
        long now = System.currentTimeMillis();
        Long lastSeen = recentFingerprints.get(fingerprint);
        if (lastSeen != null && (now - lastSeen) < 3000) return;
        recentFingerprints.put(fingerprint, now);
        if (isMention && cfg.ownMentionNotify()) {
            Text senderName = meta.senderName() != null ? meta.senderName() : com.niuqu.chatbubble.Txt.literal("");
            enqueueDeduped(meta.senderUUID(), senderName, content, messageIndex, NotificationType.MENTION);
            if (cfg.mentionSoundEnabled()) {
                mc.getSoundManager().play(GuiCompat.uiSound(SoundEvents.BLOCK_NOTE_BLOCK_PLING, 1f, 1f));
            }
        }
        if (cfg.ownQuoteNotify() && replySender != null && replySender.equals(localName)) {
            Text senderName = meta.senderName() != null ? meta.senderName() : com.niuqu.chatbubble.Txt.literal("");
            enqueueDeduped(meta.senderUUID(), senderName, com.niuqu.chatbubble.Txt.literal(contentStr), messageIndex, NotificationType.QUOTE);
        }
    }
    public void onWhisperReceived(UUID senderUUID, Text senderName, Text content, int messageIndex) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        var cfg = ChatBubbleClientSetup.config();
        if (cfg == null || !cfg.ownWhisperNotify()) return;
        enqueueDeduped(senderUUID, senderName, content, messageIndex, NotificationType.WHISPER);
    }
    private void enqueueDeduped(UUID senderUUID, Text senderName, Text content, int messageIndex, NotificationType type) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        if (mc.currentScreen instanceof ChatBubbleScreen) return;
        var cfg = ChatBubbleClientSetup.config();
        if (cfg == null) return;
        MentionNotificationBanner.INSTANCE.enqueue(
            senderUUID,
            senderName,
            content,
            messageIndex,
            type
        );
    }
}
