package com.niuqu.chatbubble.chat.notification;

import com.niuqu.chatbubble.ChatBubbleConfig;
import com.niuqu.chatbubble.ChatBubbleScreen;
import com.niuqu.chatbubble.ChatMessageStore;
import com.niuqu.chatbubble.chat.MentionDetector;
import com.niuqu.chatbubble.chat.notification.MentionNotificationBanner.NotificationType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.*;

public class MentionNotificationController {
    public static final MentionNotificationController INSTANCE = new MentionNotificationController();

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

        boolean isOwn = (meta.senderUUID() != null && meta.senderUUID().equals(mc.player.getUUID()))
            || (meta.rawPlayerName() != null && meta.rawPlayerName().equals(localName));
        boolean chatOpen = mc.screen instanceof ChatBubbleScreen;
        NotificationType type = (replySender != null && replySender.equals(localName))
            ? NotificationType.QUOTE : NotificationType.MENTION;
        // Self-notifications are opt-in (advanced config, testing aid)
        boolean selfNotify = isOwn && (type == NotificationType.QUOTE
            ? ChatBubbleConfig.OWN_QUOTE_NOTIFY.get() : ChatBubbleConfig.OWN_MENTION_NOTIFY.get());

        ChatMessageStore.debugLog(() -> "[e33chat] Mention | sender="
            + (meta.rawPlayerName() != null ? meta.rawPlayerName() : "?")
            + " | chatOpen=" + chatOpen
            + " | own=" + isOwn
            + " | type=" + type
            + " | sound=" + ChatBubbleConfig.MENTION_SOUND_ENABLED.get()
            + " | banner=" + ChatBubbleConfig.MENTION_BANNER_ENABLED.get()
            + " | preview=" + text.substring(0, Math.min(40, text.length())));

        if ((!isOwn || selfNotify) && ChatBubbleConfig.MENTION_SOUND_ENABLED.get()) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(
                SoundEvents.EXPERIENCE_ORB_PICKUP, 0.8f, 0.25f));
        }

        if ((!isOwn || selfNotify) && ChatBubbleConfig.MENTION_BANNER_ENABLED.get()) {
            enqueueDeduped(meta.senderUUID(), meta.senderName(), content, messageIndex, type);
        }
    }

    public void onWhisperReceived(UUID senderUUID, Component senderName, Component content,
                                   int messageIndex) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        boolean chatOpen = mc.screen instanceof ChatBubbleScreen;
        String senderStr = senderName.getString().replaceAll("§.", "");
        boolean isOwn = (senderUUID != null && senderUUID.equals(mc.player.getUUID()))
            || mc.player.getName().getString().equals(senderStr);

        ChatMessageStore.debugLog(() -> "[e33chat] Whisper banner | sender=" + senderStr
            + " | chatOpen=" + chatOpen
            + " | own=" + isOwn
            + " | enabled=" + ChatBubbleConfig.MENTION_WHISPER_BANNER.get());

        if (!isOwn && ChatBubbleConfig.SOUND_WHISPER.get()) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(
                SoundEvents.EXPERIENCE_ORB_PICKUP, 0.8f, 0.25f));
        }

        if (!isOwn && ChatBubbleConfig.MENTION_WHISPER_BANNER.get()) {
            enqueueDeduped(senderUUID, senderName, content, messageIndex, NotificationType.WHISPER);
        }
    }

    private void enqueueDeduped(UUID uuid, Component name, Component content, int index,
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
        MentionNotificationBanner.INSTANCE.enqueue(uuid, name, content, index, type);
        ChatMessageStore.debugLog(() -> "[e33chat] Banner enqueued | queueSize="
            + MentionNotificationBanner.INSTANCE.pendingCount());
    }
}
