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
        int maxZ,
        byte[] floodedCells
) implements CustomPacketPayload {
    public static final Type<HSFloodSyncPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(HighSeas.MOD_ID, "flood_sync")
    );

    public static final StreamCodec<FriendlyByteBuf, HSFloodSyncPacket> STREAM_CODEC = StreamCodec.of(
            HSFloodSyncPacket::write,
            HSFloodSyncPacket::read
    );

    public static byte[] buildBitmap(
            int minX, int minY, int minZ,
            int maxX, int maxY, int maxZ,
            java.util.function.BiPredicate<Integer, Integer> isFlooded
    ) {
        int sizeX = maxX - minX + 1;
        int sizeY = maxY - minY + 1;
        int sizeZ = maxZ - minZ + 1;
        int totalCells = sizeX * sizeY * sizeZ;
        byte[] bitmap = new byte[(totalCells + 7) / 8];

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    int index = ((x - minX) * sizeY * sizeZ) + ((y - minY) * sizeZ) + (z - minZ);
                    if (isFlooded.test(x, z)) {
                        bitmap[index / 8] |= (byte) (1 << (index % 8));
                    }
                }
            }
        }

        return bitmap;
    }

    public boolean isFlooded(int x, int y, int z) {
        if (floodedCells == null) return false;
        if (x < minX || x > maxX || y < minY || y > maxY || z < minZ || z > maxZ) return false;
        int sizeY = maxY - minY + 1;
        int sizeZ = maxZ - minZ + 1;
        int index = ((x - minX) * sizeY * sizeZ) + ((y - minY) * sizeZ) + (z - minZ);
        int byteIndex = index / 8;
        if (byteIndex < 0 || byteIndex >= floodedCells.length) return false;
        return (floodedCells[byteIndex] & (1 << (index % 8))) != 0;
    }

    private static void write(FriendlyByteBuf buf, HSFloodSyncPacket packet) {
        buf.writeUUID(packet.subLevelId());
        buf.writeFloat(packet.fill());
        buf.writeInt(packet.minX());
        buf.writeInt(packet.minY());
        buf.writeInt(packet.minZ());
        buf.writeInt(packet.maxX());
        buf.writeInt(packet.maxY());
        buf.writeInt(packet.maxZ());

        byte[] bitmap = packet.floodedCells();
        if (bitmap == null) {
            buf.writeVarInt(0);
        } else {
            buf.writeVarInt(bitmap.length);
            buf.writeByteArray(bitmap);
        }
    }

    private static HSFloodSyncPacket read(FriendlyByteBuf buf) {
        UUID id = buf.readUUID();
        float fill = buf.readFloat();
        int minX = buf.readInt();
        int minY = buf.readInt();
        int minZ = buf.readInt();
        int maxX = buf.readInt();
        int maxY = buf.readInt();
        int maxZ = buf.readInt();

        int bitmapLen = buf.readVarInt();
        byte[] bitmap = bitmapLen > 0 ? buf.readByteArray(bitmapLen) : new byte[0];

        return new HSFloodSyncPacket(id, fill, minX, minY, minZ, maxX, maxY, maxZ, bitmap);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
