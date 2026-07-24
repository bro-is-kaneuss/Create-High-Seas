package com.BreadRes.highseas.client;

import com.BreadRes.highseas.HighSeas;
import com.BreadRes.highseas.physics.HSWaterOcclusionBridge;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

@EventBusSubscriber(modid = HighSeas.MOD_ID, value = Dist.CLIENT)
public final class HighSeasClient {
    private HighSeasClient() {
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY) {
            HSWaterOcclusionBridge.update(Minecraft.getInstance().level);
        }
    }

    @SubscribeEvent
    public static void onPlayerLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        HSWaterOcclusionBridge.invalidateAll();
        HSSubLevelBlockOcclusionCache.invalidate();
    }
}