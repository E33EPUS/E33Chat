package com.niuqu.chatbubble.network;

import net.minecraft.network.PacketByteBuf;
//#if MC >= 12005
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
//#endif
import net.minecraft.util.Identifier;

/**
 * Client -> server: one chunk of a media upload (2.3.13 server-side media
 * hosting). The upload is split into DiskMediaStore.CHUNK_BYTES chunks so a
 * large image stays under the protocol packet size limit.
 */
public record MediaUploadPayload(long uploadId, int index, int totalChunks,
                                 int totalBytes, String contentType, byte[] chunk)
        //#if MC >= 12005
        implements CustomPayload {
        //#else
        //$$ {
        //#endif
    //#if MC >= 12005
    public static final CustomPayload.Id<MediaUploadPayload> ID =
        new CustomPayload.Id<>(
            //#if MC >= 12000
            Identifier.of("e33chat", "media_upload")
            //#else
            //$$ new Identifier("e33chat", "media_upload")
            //#endif
        );

    public static final PacketCodec<PacketByteBuf, MediaUploadPayload> CODEC = PacketCodec.of(
        //#if MC >= 26000
        (buf, value) -> {
        //#else
        //$$ (value, buf) -> {
        //#endif
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
    //#else
    //$$ public static final Identifier ID = new Identifier("e33chat", "media_upload");
    //#endif
}
