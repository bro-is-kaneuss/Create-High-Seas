package com.BreadRes.highseas;

import com.BreadRes.highseas.physics.HSForceGroups;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;

@Mod(HighSeas.MOD_ID)
public final class HighSeas {
    public static final String MOD_ID = "highseas";

    public HighSeas(IEventBus modEventBus, ModContainer modContainer) {
        HSForceGroups.register();

        if (FMLEnvironment.dist == Dist.CLIENT) {
            com.BreadRes.highseas.client.HighSeasClientSetup.register(modContainer);
        }
    }
}