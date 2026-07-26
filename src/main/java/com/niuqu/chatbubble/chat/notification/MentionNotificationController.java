package com.niuqu.chatbubble.chat.notification;

import com.niuqu.chatbubble.ChatBubbleConfig;
import com.niuqu.chatbubble.ChatBubbleScreen;
import com.niuqu.chatbubble.ChatMessageStore;
import com.niuqu.chatbubble.chat.MentionDetector;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

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

    public void onMessageCaptured(Component content, ChatMessageStore.SenderMeta meta,
                                   int messageIndex, String replySender) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        String localName = mc.player.getName().getString();
        boolean requireAt = ChatBubbleConfig.MENTION_REQUIRE_AT.get();
        String text = content.getString();

        if (!MentionDetector.isMentioned(text, localName, requireAt, replySender)) return;

        boolean isOwn = meta.rawPlayerName() != null
            && meta.rawPlayerName().equals(localName);
        boolean chatOpen = mc.screen instanceof ChatBubbleScreen;

        ChatMessageStore.debugLog(() -> "[e33chat] Mention | sender="
            + (meta.rawPlayerName() != null ? meta.rawPlayerName() : "?")
            + " | chatOpen=" + chatOpen
            + " | own=" + isOwn
            + " | banner=" + ChatBubbleConfig.MENTION_BANNER_ENABLED.get()
            + " | preview=" + text.substring(0, Math.min(40, text.length())));

        if (!isOwn && ChatBubbleConfig.MENTION_SOUND_ENABLED.get()) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(
                SoundEvents.EXPERIENCE_ORB_PICKUP, 0.8f, 1.0f));
        }

        if (ChatBubbleConfig.MENTION_BANNER_ENABLED.get()) {
            enqueueDeduped(meta.senderUUID(), meta.senderName(), content, messageIndex);
        }
    }

    public void onWhisperReceived(UUID senderUUID, Component senderName, Component content,
                                   int messageIndex) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        boolean chatOpen = mc.screen instanceof ChatBubbleScreen;
        String senderStr = senderName.getString();
        boolean isOwn = mc.player.getName().getString().equals(senderStr);

        ChatMessageStore.debugLog(() -> "[e33chat] Whisper banner | sender=" + senderStr
            + " | chatOpen=" + chatOpen
            + " | own=" + isOwn
            + " | enabled=" + ChatBubbleConfig.MENTION_WHISPER_BANNER.get());

        if (!isOwn && ChatBubbleConfig.MENTION_SOUND_ENABLED.get()) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(
                SoundEvents.EXPERIENCE_ORB_PICKUP, 0.8f, 1.0f));
        }

        if (!isOwn && ChatBubbleConfig.MENTION_WHISPER_BANNER.get()) {
            Component whisperLabel = Component.literal("[私聊] ").append(senderName);
            enqueueDeduped(senderUUID, whisperLabel, content, messageIndex);
        }
    }

    private void enqueueDeduped(UUID uuid, Component name, Component content, int index) {
        String fp = uuid + "\0" + content.getString();
        long now = System.currentTimeMillis();
        Long last = recentFingerprints.get(fp);
        if (last != null && now - last < 1000) {
            ChatMessageStore.debugLog(() -> "[e33chat] Banner deduped | fp="
                + fp.substring(0, Math.min(40, fp.length())));
            return;
        }
        recentFingerprints.put(fp, now);
        MentionNotificationBanner.INSTANCE.enqueue(uuid, name, content, index);
        ChatMessageStore.debugLog(() -> "[e33chat] Banner enqueued | queueSize="
            + MentionNotificationBanner.INSTANCE.pendingCount());
    }
}
