package com.niuqu.chatbubble.network;

import net.minecraft.network.PacketByteBuf;

import java.util.List;

/**
 * 共享 DTO：ServerConfigScreenPayload 与 ServerConfigSavePayload 的
 * 6 字段载荷（4 boolean + 2 List&lt;String&gt;）。
 *
 * 字段顺序即网络字节序契约（写序 = 读序），不得改动；payload ID 不变。
 */
public record ServerConfigDto(boolean useTpa, boolean historyEnabled, boolean templateDebug,
                              boolean mediaEnabled,
                              List<String> chatTemplates, List<String> whisperTemplates) {
    public static void encode(ServerConfigDto dto, PacketByteBuf buf) {
        buf.writeBoolean(dto.useTpa);
        buf.writeBoolean(dto.historyEnabled);
        buf.writeBoolean(dto.templateDebug);
        buf.writeBoolean(dto.mediaEnabled);
        ConfigSyncV2Payload.writeList(buf, dto.chatTemplates);
        ConfigSyncV2Payload.writeList(buf, dto.whisperTemplates);
    }

    public static ServerConfigDto decode(PacketByteBuf buf) {
        boolean useTpa = buf.readBoolean();
        boolean history = buf.readBoolean();
        boolean debug = buf.readBoolean();
        boolean media = buf.readBoolean();
        List<String> chat = ConfigSyncV2Payload.readList(buf);
        List<String> whisper = ConfigSyncV2Payload.readList(buf);
        return new ServerConfigDto(useTpa, history, debug, media, chat, whisper);
    }
}
