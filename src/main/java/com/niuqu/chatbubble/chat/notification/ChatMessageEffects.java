package com.niuqu.chatbubble.chat.notification;

import com.niuqu.chatbubble.ChatBubbleClientSetup;
import com.niuqu.chatbubble.store.ChatMessageStore;
import net.minecraft.client.MinecraftClient;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

import java.util.UUID;

/**
 * 真实副作用实现（B4 上移）：横幅转发给 MentionNotificationController，
 * 公共/引用提示音直接播放。由 ChatBubbleClientSetup.onInitializeClient()
 * 注册为 ChatMessageStore 的观察者——store 不再直接依赖 Minecraft 单例。
 */
public class ChatMessageEffects implements ChatMessageStore.MessageEffectObserver {
    @Override
    public void onMentionOrQuote(Text content, ChatMessageStore.SenderMeta meta,
                                 int index, String replySender) {
        MentionNotificationController.INSTANCE.onMessageCaptured(content, meta, index, replySender);
    }

    @Override
    public void onWhisperReceived(UUID senderUUID, Text senderName, Text content, int index) {
        MentionNotificationController.INSTANCE.onWhisperReceived(senderUUID, senderName, content, index);
    }

    @Override
    public void onSystemMessage(Text content, int index) {
        MentionNotificationController.INSTANCE.onSystemMessage(content, index);
    }

    @Override
    public void onPublicChatSound() {
        playChime();
    }

    @Override
    public void onQuoteSound() {
        playChime();
    }

    private static void playChime() {
        var player = MinecraftClient.getInstance().player;
        if (player != null)
            player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), 0.6F * ChatBubbleClientSetup.config().soundVolume() / 100f, 1.0F);
    }
}
