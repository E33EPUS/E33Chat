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
import java.util.*;

public class MentionNotificationController {
    public static final MentionNotificationController INSTANCE = new MentionNotificationController();

    private final Map<String, Long> recentFingerprints = new LinkedHashMap<>() {
        protected boolean removeEldestEntry(Map.Entry<String, Long> e) {
            return size() > 32;
        }
    };

    private MentionNotificationController() {}

    public void onMessageCaptured(Text content, SenderMeta meta, int messageIndex, String replySender) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        String localName = mc.player.getName().getString();
        var cfg = ChatBubbleClientSetup.config();
        if (cfg == null) return;
        boolean requireAt = cfg.mentionRequireAt();
        String text = content.getString();

        if (!MentionDetector.isMentioned(text, localName, requireAt, replySender)) return;

        boolean isOwn = (meta.senderUUID() != null && meta.senderUUID().equals(mc.player.getUuid()))
            || (meta.rawPlayerName() != null && meta.rawPlayerName().equals(localName));
        boolean chatOpen = mc.currentScreen instanceof ChatBubbleScreen;
        NotificationType type = (replySender != null && replySender.equals(localName))
            ? NotificationType.QUOTE : NotificationType.MENTION;
        boolean selfNotify = isOwn && (type == NotificationType.QUOTE
            ? cfg.ownQuoteNotify()
            : cfg.ownMentionNotify());

        ChatMessageStore.debugLog(() -> "[e33chat] Mention | sender="
            + (meta.rawPlayerName() != null ? meta.rawPlayerName() : "?")
            + " | chatOpen=" + chatOpen
            + " | own=" + isOwn
            + " | type=" + type
            + " | sound=" + cfg.mentionSoundEnabled()
            + " | banner=" + cfg.mentionBannerEnabled()
            + " | selfNotify=" + selfNotify
            + " | preview=" + text.substring(0, Math.min(40, text.length())));

        if ((!isOwn || selfNotify) && cfg.mentionSoundEnabled()) {
            float vol = 0.25f * cfg.soundVolume() / 100f;
            mc.getSoundManager().play(GuiCompat.uiSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, vol, 0.25f));
        }

        if ((!isOwn || selfNotify) && cfg.mentionBannerEnabled()) {
            enqueueDeduped(meta.senderUUID(), meta.senderName(), meta.rawPlayerName(),
                content, messageIndex, type);
        }
    }

    public void onWhisperReceived(UUID senderUUID, Text senderName, String rawPlayerName,
                                  Text content, int messageIndex) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        var cfg = ChatBubbleClientSetup.config();
        if (cfg == null) return;

        boolean chatOpen = mc.currentScreen instanceof ChatBubbleScreen;
        String senderStr = senderName.getString().replaceAll("§.", "");
        boolean isOwn = (senderUUID != null && senderUUID.equals(mc.player.getUuid()))
            || mc.player.getName().getString().equals(senderStr);

        ChatMessageStore.debugLog(() -> "[e33chat] Whisper banner | sender=" + senderStr
            + " | chatOpen=" + chatOpen
            + " | own=" + isOwn
            + " | soundWhisper=" + cfg.soundWhisper()
            + " | banner=" + cfg.mentionWhisperBanner());

        boolean selfNotify = isOwn && cfg.ownWhisperNotify();
        if ((!isOwn || selfNotify) && cfg.soundWhisper()) {
            float vol = 0.25f * cfg.soundVolume() / 100f;
            mc.getSoundManager().play(GuiCompat.uiSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, vol, 0.25f));
        }

        if ((!isOwn || selfNotify) && cfg.mentionWhisperBanner()) {
            enqueueDeduped(senderUUID, senderName, rawPlayerName, content, messageIndex,
                NotificationType.WHISPER);
        }
    }

    // System messages (server broadcasts/deaths/joins) pop the same banner as
    // @/whisper/quote; no sender name — the [系统] label is the name row.
    public void onSystemMessage(Text content, int messageIndex) {
        if (MinecraftClient.getInstance().player == null) return;
        var cfg = ChatBubbleClientSetup.config();
        if (cfg == null || !cfg.systemBannerEnabled()) return;
        enqueueDeduped(new UUID(0, 0), com.niuqu.chatbubble.Txt.empty(), null,
            content, messageIndex, NotificationType.SYSTEM);
    }

    private void enqueueDeduped(UUID uuid, Text name, String rawPlayerName, Text content, int index,
                                 NotificationType type) {
        String fp = uuid + "\0" + content.getString();
        long now = System.currentTimeMillis();
        Long last = recentFingerprints.get(fp);
        if (last != null && now - last < 1000) {
            ChatMessageStore.debugLog(() -> "[e33chat] Banner deduped | fp="
                + fp.substring(0, Math.min(40, fp.length())));
            return;
        }
        recentFingerprints.put(fp, now);
        MentionNotificationBanner.INSTANCE.enqueue(uuid, name, rawPlayerName, content, index, type);
        ChatMessageStore.debugLog(() -> "[e33chat] Banner enqueued | queueSize="
            + MentionNotificationBanner.INSTANCE.pendingCount());
    }
}
