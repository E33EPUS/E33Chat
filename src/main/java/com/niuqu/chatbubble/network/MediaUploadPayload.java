package com.niuqu.chatbubble.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Client -> server: one chunk of a media upload (2.3.13 server-side media
 * hosting). The upload is split into DiskMediaStore.CHUNK_BYTES chunks so a
 * large image stays under the protocol packet size limit.
 */
public record MediaUploadPayload(long uploadId, int index, int totalChunks,
                                 int totalBytes, String contentType, byte[] chunk)
        implements CustomPayload {

    public static final CustomPayload.Id<MediaUploadPayload> ID =
        new CustomPayload.Id<>(Identifier.of("e33chat", "media_upload"));

    public static final PacketCodec<PacketByteBuf, MediaUploadPayload> CODEC = PacketCodec.of(
        (value, buf) -> {
            buf.writeLong(value.uploadId);
            buf.writeInt(value.index);
            buf.writeInt(value.totalChunks);
            buf.writeInt(value.totalBytes);
            buf.writeString(value.contentType != null ? value.contentType : "");
            buf.writeByteArray(value.chunk);
        },
        buf -> new MediaUploadPayload(
            buf.readLong(),
            buf.readInt(),
            buf.readInt(),
            buf.readInt(),
            buf.readString(),
            buf.readByteArray()
        )
    );

    @Override
    public Id<MediaUploadPayload> getId() { return ID; }
}
