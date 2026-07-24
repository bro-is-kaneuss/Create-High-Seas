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
                packet.maxZ()
        ));
    }

    public static SyncedFloodState get(UUID id) {
        return STATES.get(id);
    }

    public record SyncedFloodState(
            float fill,
            int minX,
            int minY,
            int minZ,
            int maxX,
            int maxY,
            int maxZ
    ) {
    }
}