package com.niuqu.chatbubble.packets;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * 共享 DTO：ServerConfigScreenPacket（id 5）与 ServerConfigSavePacket（id 6）的
 * 6 字段载荷（4 boolean + 2 List&lt;String&gt;）。
 *
 * 字段顺序即网络字节序契约（写序 = 读序），不得改动；包 ID 5/6 不变。
 */
public record ServerConfigDto(boolean useTpa, boolean historyEnabled, boolean templateDebug,
                              boolean mediaEnabled,
                              List<String> chatTemplates, List<String> whisperTemplates) {
    public static void encode(ServerConfigDto dto, FriendlyByteBuf buf) {
        buf.writeBoolean(dto.useTpa);
        buf.writeBoolean(dto.historyEnabled);
        buf.writeBoolean(dto.templateDebug);
        buf.writeBoolean(dto.mediaEnabled);
        buf.writeCollection(dto.chatTemplates, FriendlyByteBuf::writeUtf);
        buf.writeCollection(dto.whisperTemplates, FriendlyByteBuf::writeUtf);
    }

    public static ServerConfigDto decode(FriendlyByteBuf buf) {
        boolean useTpa = buf.readBoolean();
        boolean history = buf.readBoolean();
        boolean debug = buf.readBoolean();
        boolean media = buf.readBoolean();
        List<String> chat = new ArrayList<>(buf.readCollection(ArrayList::new, FriendlyByteBuf::readUtf));
        List<String> whisper = new ArrayList<>(buf.readCollection(ArrayList::new, FriendlyByteBuf::readUtf));
        return new ServerConfigDto(useTpa, history, debug, media, chat, whisper);
    }
}
