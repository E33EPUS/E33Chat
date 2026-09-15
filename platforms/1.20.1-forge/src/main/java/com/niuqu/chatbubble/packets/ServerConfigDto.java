package com.niuqu.chatbubble.packets;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * 共享 DTO：ServerConfigScreenPacket（id 5）与 ServerConfigSavePacket（id 6）的
 * 9 字段载荷（7 boolean + 2 List&lt;String&gt;）。
 *
 * 字段顺序即网络字节序契约（写序 = 读序），不得改动既有字段位置；2.4.10 在
 * easyBotCompat 之后追加 groupsEnabled（两端同版本才兼容）。包 ID 5/6 不变。
 */
public record ServerConfigDto(boolean useTpa, boolean historyEnabled, boolean templateDebug,
                              boolean mediaEnabled, boolean mediaAutoClean, boolean easyBotCompat,
                              boolean groupsEnabled,
                              List<String> chatTemplates, List<String> whisperTemplates) {
    public static void encode(ServerConfigDto dto, FriendlyByteBuf buf) {
        buf.writeBoolean(dto.useTpa);
        buf.writeBoolean(dto.historyEnabled);
        buf.writeBoolean(dto.templateDebug);
        buf.writeBoolean(dto.mediaEnabled);
        buf.writeBoolean(dto.mediaAutoClean);
        buf.writeBoolean(dto.easyBotCompat);
        buf.writeBoolean(dto.groupsEnabled);
        buf.writeCollection(dto.chatTemplates, FriendlyByteBuf::writeUtf);
        buf.writeCollection(dto.whisperTemplates, FriendlyByteBuf::writeUtf);
    }

    public static ServerConfigDto decode(FriendlyByteBuf buf) {
        boolean useTpa = buf.readBoolean();
        boolean history = buf.readBoolean();
        boolean debug = buf.readBoolean();
        boolean media = buf.readBoolean();
        boolean autoClean = buf.readBoolean();
        boolean easyBot = buf.readBoolean();
        boolean groups = buf.readBoolean();
        List<String> chat = new ArrayList<>(buf.readCollection(ArrayList::new, FriendlyByteBuf::readUtf));
        List<String> whisper = new ArrayList<>(buf.readCollection(ArrayList::new, FriendlyByteBuf::readUtf));
        return new ServerConfigDto(useTpa, history, debug, media, autoClean, easyBot, groups, chat, whisper);
    }
}
