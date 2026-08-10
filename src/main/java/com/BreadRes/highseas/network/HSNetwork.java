package com.BreadRes.highseas.network;

import com.BreadRes.highseas.HighSeas;
import com.BreadRes.highseas.client.HSClientFloodStates;
import com.BreadRes.highseas.physics.HSFloodState;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.UUID;
import java.util.WeakHashMap;

@EventBusSubscriber(modid = HighSeas.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class HSNetwork {
    private static final int SYNC_INTERVAL_TICKS = 5;
    private static final WeakHashMap<ServerSubLevel, Long> LAST_SYNC = new WeakHashMap<>();

    private HSNetwork() {
    }

    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");

        registrar.playToClient(
                HSFloodSyncPacket.TYPE,
                HSFloodSyncPacket.STREAM_CODEC,
                HSNetwork::handleFloodSync
        );
    }

    private static void handleFloodSync(HSFloodSyncPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> HSClientFloodStates.accept(packet));
    }

    public static void syncFlood(ServerLevel level, ServerSubLevel subLevel, HSFloodState state, BoundingBox3ic bounds) {
        if (level == null || subLevel == null || state == null || bounds == null) {
            return;
        }

        UUID id = subLevel.getUniqueId();

        if (id == null) {
            return;
        }

        long gameTime = level.getGameTime();
        Long last = LAST_SYNC.get(subLevel);

        if (last != null && gameTime - last < SYNC_INTERVAL_TICKS) {
            return;
        }

        LAST_SYNC.put(subLevel, gameTime);

        float fill = (float) clamp(state.fill(), 0.0, 1.0);

        byte[] bitmap = state.floodedCells();

        HSFloodSyncPacket packet = new HSFloodSyncPacket(
                id,
                fill,
                bounds.minX(),
                bounds.minY(),
                bounds.minZ(),
                bounds.maxX(),
                bounds.maxY(),
                bounds.maxZ(),
                bitmap
        );

        for (ServerPlayer player : level.players()) {
            PacketDistributor.sendToPlayer(player, packet);
        }
    }

    private static double clamp(double value, double min, double max) {
        if (value < min) {
            return min;
        }

        if (value > max) {
            return max;
        }

        return value;
    }
}