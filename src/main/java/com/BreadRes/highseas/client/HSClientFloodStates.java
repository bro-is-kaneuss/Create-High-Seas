package com.BreadRes.highseas.client;

import com.BreadRes.highseas.network.HSFloodSyncPacket;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class HSClientFloodStates {
    private static final Map<UUID, SyncedFloodState> STATES = new HashMap<>();

    private HSClientFloodStates() {
    }

    public static void accept(HSFloodSyncPacket packet) {
        STATES.put(packet.subLevelId(), new SyncedFloodState(
                packet.fill(),
                packet.minX(),
                packet.minY(),
                packet.minZ(),
                packet.maxX(),
                packet.maxY(),
                packet.maxZ(),
                packet.floodedCells()
        ));
    }

    public static SyncedFloodState get(UUID id) {
        return STATES.get(id);
    }

    public static Map<UUID, SyncedFloodState> all() {
        return STATES;
    }

    public record SyncedFloodState(
            float fill,
            int minX,
            int minY,
            int minZ,
            int maxX,
            int maxY,
            int maxZ,
            byte[] floodedCells
    ) {
        public boolean isFlooded(int x, int y, int z) {
            if (floodedCells == null || floodedCells.length == 0) return false;
            if (x < minX || x > maxX || y < minY || y > maxY || z < minZ || z > maxZ) return false;
            int sizeY = maxY - minY + 1;
            int sizeZ = maxZ - minZ + 1;
            int index = ((x - minX) * sizeY * sizeZ) + ((y - minY) * sizeZ) + (z - minZ);
            int byteIndex = index / 8;
            if (byteIndex < 0 || byteIndex >= floodedCells.length) return false;
            return (floodedCells[byteIndex] & (1 << (index % 8))) != 0;
        }
    }
}
