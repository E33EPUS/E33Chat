package com.niuqu.chatbubble.mixin;

import com.mojang.authlib.GameProfile;
import com.niuqu.chatbubble.ChatBubbleConfig;
import com.niuqu.chatbubble.ChatMessageStore;
import com.niuqu.chatbubble.ChatMessageStore.SenderMeta;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.chat.ChatListener;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.PlayerChatMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(value = ChatListener.class, priority = 500)
public class ChatListenerMixin {

    @Inject(method = "handlePlayerChatMessage", at = @At("HEAD"))
    private void onPlayerChat(PlayerChatMessage message, GameProfile gameProfile,
                              ChatType.Bound bound, CallbackInfo ci) {
        UUID senderId = gameProfile.getId();
        Component raw = message.decoratedContent();
        String rawStr = raw.getString();
        if (rawStr.startsWith("xaero-waypoint:")
            || rawStr.startsWith("xaero_waypoint:")
            || rawStr.startsWith("xaero_waypoint_add:")) {
            return;
        }

        if (ChatBubbleConfig.CHAT_REPORT_COMPAT.get()) {
            String name = gameProfile.getName();
            String pattern = "<" + name + "> ";
            int idx = rawStr.indexOf(pattern);
            if (idx >= 0) {
                String displayName = (rawStr.substring(0, idx) + name).trim();
                String cleanContent = rawStr.substring(idx + pattern.length());
                ChatMessageStore.setPendingMeta(new SenderMeta(
                    senderId != null ? senderId : new UUID(0, 0),
                    Component.literal(displayName),
                    Component.literal(cleanContent),
                    false,
                    name
                ));
                return;
            }
        }

        ChatMessageStore.setPendingMeta(new SenderMeta(
            senderId != null ? senderId : new UUID(0, 0),
            Component.literal(gameProfile.getName()),
            raw,
            false,
            gameProfile.getName()
        ));
    }

    @Inject(method = "handleDisguisedChatMessage", at = @At("HEAD"))
    private void onDisguisedChat(Component message, ChatType.Bound bound, CallbackInfo ci) {
        String msgStr = message.getString();
        if (msgStr.startsWith("xaero-waypoint:")
            || msgStr.startsWith("xaero_waypoint:")
            || msgStr.startsWith("xaero_waypoint_add:")) {
            return;
        }
        boolean hasSender = bound.name() != null;
        ChatMessageStore.setPendingMeta(new SenderMeta(
            new UUID(0, 0),
            hasSender ? bound.name() : Component.translatable("e33chat.sender.system"),
            message,
            !hasSender,
            hasSender ? bound.name().getString() : null
        ));
    }

    @Inject(method = "handleSystemMessage", at = @At("HEAD"))
    private void onSystemChat(Component message, boolean overlay, CallbackInfo ci) {
        if (overlay) return;

        if (ChatBubbleConfig.CHAT_REPORT_COMPAT.get()) {
            String text = message.getString();
            var connection = Minecraft.getInstance().player.connection;
            String foundName = null;
            int nameStart = -1, contentStart = -1;
            if (connection != null) {
                for (var info : connection.getOnlinePlayers()) {
                    String name = info.getProfile().getName();
                    String pattern = "<" + name + "> ";
                    int idx = text.indexOf(pattern);
                    if (idx >= 0) {
                        foundName = name;
                        nameStart = idx;
                        contentStart = idx + pattern.length();
                        break;
                    }
                }
            }
            if (foundName != null) {
                UUID senderId = ChatMessageStore.lookupPlayerUUID(foundName);
                String displayName = (text.substring(0, nameStart) + foundName).trim();
                String cleanContent = text.substring(contentStart);
                ChatMessageStore.setPendingMeta(new SenderMeta(
                    senderId,
                    Component.literal(displayName),
                    Component.literal(cleanContent),
                    false,
                    foundName
                ));
                return;
            }
            boolean isSystem = !ChatBubbleConfig.SYSTEM_CHAT_AS_BUBBLE.get();
            ChatMessageStore.setPendingMeta(new SenderMeta(
                new UUID(0, 0),
                Component.translatable("e33chat.sender.system"),
                message,
                isSystem,
                null
            ));
            return;
        }

        boolean isSystem = !ChatBubbleConfig.SYSTEM_CHAT_AS_BUBBLE.get();
        ChatMessageStore.setPendingMeta(new SenderMeta(
            new UUID(0, 0),
            Component.translatable("e33chat.sender.system"),
            message,
            isSystem,
            null
        ));
    }
}
