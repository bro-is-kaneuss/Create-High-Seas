package com.BreadRes.highseas.network;

import com.BreadRes.highseas.HighSeas;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record HSFloodSyncPacket(
        UUID subLevelId,
        float fill,
        int minX,
        int minY,
        int minZ,
        int maxX,
        int maxY,
        int maxZ
) implements CustomPacketPayload {
    public static final Type<HSFloodSyncPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(HighSeas.MOD_ID, "flood_sync")
    );

    public static final StreamCodec<FriendlyByteBuf, HSFloodSyncPacket> STREAM_CODEC = StreamCodec.of(
            HSFloodSyncPacket::write,
            HSFloodSyncPacket::read
    );

    private static void write(FriendlyByteBuf buf, HSFloodSyncPacket packet) {
        buf.writeUUID(packet.subLevelId());
        buf.writeFloat(packet.fill());
        buf.writeInt(packet.minX());
        buf.writeInt(packet.minY());
        buf.writeInt(packet.minZ());
        buf.writeInt(packet.maxX());
        buf.writeInt(packet.maxY());
        buf.writeInt(packet.maxZ());
    }

    private static HSFloodSyncPacket read(FriendlyByteBuf buf) {
        return new HSFloodSyncPacket(
                buf.readUUID(),
                buf.readFloat(),
                buf.readInt(),
                buf.readInt(),
                buf.readInt(),
                buf.readInt(),
                buf.readInt(),
                buf.readInt()
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}