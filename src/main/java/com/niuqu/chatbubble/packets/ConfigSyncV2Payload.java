package com.niuqu.chatbubble.packets;

import com.niuqu.chatbubble.ChatMessageStore;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Server -> client sync of server-side settings (v2: adds message-format templates).
 * Registered as a separate type so old clients never desync: NeoForge drops
 * unknown payload types harmlessly (registrar is optional()).
 */
public record ConfigSyncV2Payload(boolean useTpa, List<String> chatTemplates,
                                  List<String> whisperTemplates, boolean templateDebug)
        implements CustomPacketPayload {

    public static final Type<ConfigSyncV2Payload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("e33chat", "config_sync_v2"));

    public static final StreamCodec<ByteBuf, ConfigSyncV2Payload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public ConfigSyncV2Payload decode(ByteBuf buf) {
            boolean useTpa = buf.readBoolean();
            List<String> chat = readList(buf);
            List<String> whisper = readList(buf);
            boolean debug = buf.readBoolean();
            return new ConfigSyncV2Payload(useTpa, chat, whisper, debug);
        }

        @Override
        public void encode(ByteBuf buf, ConfigSyncV2Payload payload) {
            buf.writeBoolean(payload.useTpa());
            writeList(buf, payload.chatTemplates());
            writeList(buf, payload.whisperTemplates());
            buf.writeBoolean(payload.templateDebug());
        }
    };

    static List<String> readList(ByteBuf buf) {
        int count = buf.readInt();
        List<String> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) out.add(ByteBufCodecs.STRING_UTF8.decode(buf));
        return out;
    }

    static void writeList(ByteBuf buf, List<String> list) {
        buf.writeInt(list.size());
        for (String s : list) ByteBufCodecs.STRING_UTF8.encode(buf, s);
    }

    @Override
    public Type<ConfigSyncV2Payload> type() { return TYPE; }

    public static void handleClient(ConfigSyncV2Payload payload, IPayloadContext context) {
        context.enqueueWork(() -> ChatMessageStore.setServerConfig(
            payload.useTpa(), payload.chatTemplates(), payload.whisperTemplates(), payload.templateDebug()));
    }
}
