package com.niuqu.chatbubble.chat.notification;

import com.niuqu.chatbubble.ChatBubbleClientSetup;
import com.niuqu.chatbubble.ChatBubbleScreen;
import com.niuqu.chatbubble.ChatMessageStore;
import com.niuqu.chatbubble.chat.MentionDetector;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

import java.util.*;

public class MentionNotificationController {
    public static final MentionNotificationController INSTANCE = new MentionNotificationController();

    // Fingerprint → last-seen timestamp, 1s TTL for self-echo dedup
    private final Map<String, Long> recentFingerprints = new LinkedHashMap<>() {
        protected boolean removeEldestEntry(Map.Entry<String, Long> e) {
            return size() > 32;
        }
    };

    private MentionNotificationController() {}

    public void onMessageCaptured(Text content, ChatMessageStore.SenderMeta meta,
                                   int messageIndex, String replySender) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        String localName = mc.player.getName().getString();
        boolean requireAt = ChatBubbleClientSetup.config().mentionRequireAt();
        String text = content.getString();

        if (!MentionDetector.isMentioned(text, localName, requireAt, replySender)) return;

        boolean isOwn = meta.rawPlayerName() != null
            && meta.rawPlayerName().equals(localName);
        boolean chatOpen = mc.currentScreen instanceof ChatBubbleScreen;

        ChatMessageStore.debugLog("[e33chat] Mention | sender="
            + (meta.rawPlayerName() != null ? meta.rawPlayerName() : "?")
            + " | chatOpen=" + chatOpen
            + " | own=" + isOwn
            + " | banner=" + ChatBubbleClientSetup.config().mentionBannerEnabled()
            + " | preview=" + text.substring(0, Math.min(40, text.length())));

        if (ChatBubbleClientSetup.config().mentionSoundEnabled()) {
            mc.getSoundManager().play(PositionedSoundInstance.master(
                SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 0.25f,
                0.8f * ChatBubbleClientSetup.config().soundVolume() / 100f));
        }

        if (ChatBubbleClientSetup.config().mentionBannerEnabled()) {
            enqueueDeduped(meta.senderUUID(), meta.senderName(), content, messageIndex,
                replySender != null ? MentionNotificationBanner.NotificationType.QUOTE
                    : MentionNotificationBanner.NotificationType.MENTION);
        }
    }

    public void onWhisperReceived(UUID senderUUID, Text senderName, Text content,
                                   int messageIndex) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        boolean chatOpen = mc.currentScreen instanceof ChatBubbleScreen;
        String senderStr = senderName.getString().replaceAll("§.", "");
        boolean isOwn = mc.player.getName().getString().equals(senderStr);

        ChatMessageStore.debugLog("[e33chat] Whisper banner | sender=" + senderStr
            + " | chatOpen=" + chatOpen
            + " | own=" + isOwn
            + " | enabled=" + ChatBubbleClientSetup.config().mentionWhisperBanner());

        if (!isOwn && ChatBubbleClientSetup.config().mentionSoundEnabled()) {
            mc.getSoundManager().play(PositionedSoundInstance.master(
                SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 0.25f,
                0.8f * ChatBubbleClientSetup.config().soundVolume() / 100f));
        }

        if (!isOwn && ChatBubbleClientSetup.config().mentionWhisperBanner()) {
            enqueueDeduped(senderUUID, senderName, content, messageIndex,
                MentionNotificationBanner.NotificationType.WHISPER);
        }
    }

    private void enqueueDeduped(UUID uuid, Text name, Text content, int index,
                                 MentionNotificationBanner.NotificationType type) {
        String fp = uuid + "\0" + content.getString();
        long now = System.currentTimeMillis();
        Long last = recentFingerprints.get(fp);
        if (last != null && now - last < 1000) {
            ChatMessageStore.debugLog("[e33chat] Banner deduped | fp="
                + fp.substring(0, Math.min(40, fp.length())));
            return;
        }
        recentFingerprints.put(fp, now);
        MentionNotificationBanner.INSTANCE.enqueue(uuid, name, content, index, type);
        ChatMessageStore.debugLog("[e33chat] Banner enqueued | queueSize="
            + MentionNotificationBanner.INSTANCE.pendingCount());
    }
}
