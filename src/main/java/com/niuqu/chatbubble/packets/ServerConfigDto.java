package com.niuqu.chatbubble.packets;

import io.netty.buffer.ByteBuf;

import java.util.List;

/**
 * 共享 DTO：ServerConfigScreenPayload 与 ServerConfigSavePayload 的
 * 8 字段载荷（6 boolean + 2 List&lt;String&gt;）。
 *
 * 字段顺序即网络字节序契约（写序 = 读序），不得改动；payload 类型 ID 不变。
 */
public record ServerConfigDto(boolean useTpa, boolean historyEnabled, boolean templateDebug,
                              boolean mediaEnabled, boolean mediaAutoClean, boolean easyBotCompat,
                              List<String> chatTemplates, List<String> whisperTemplates) {
    public static void encode(ServerConfigDto dto, ByteBuf buf) {
        buf.writeBoolean(dto.useTpa);
        buf.writeBoolean(dto.historyEnabled);
        buf.writeBoolean(dto.templateDebug);
        buf.writeBoolean(dto.mediaEnabled);
        buf.writeBoolean(dto.mediaAutoClean);
        buf.writeBoolean(dto.easyBotCompat);
        ConfigSyncV2Payload.writeList(buf, dto.chatTemplates);
        ConfigSyncV2Payload.writeList(buf, dto.whisperTemplates);
    }

    public static ServerConfigDto decode(ByteBuf buf) {
        boolean useTpa = buf.readBoolean();
        boolean history = buf.readBoolean();
        boolean debug = buf.readBoolean();
        boolean media = buf.readBoolean();
        boolean autoClean = buf.readBoolean();
        boolean easyBot = buf.readBoolean();
        List<String> chat = ConfigSyncV2Payload.readList(buf);
        List<String> whisper = ConfigSyncV2Payload.readList(buf);
        return new ServerConfigDto(useTpa, history, debug, media, autoClean, easyBot, chat, whisper);
    }
}
