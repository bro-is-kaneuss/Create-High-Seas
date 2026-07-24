package com.BreadRes.highseas;

import com.BreadRes.highseas.config.HSConfig;
import com.BreadRes.highseas.physics.HSDensityTable;
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
        HSConfig.register(modContainer, modEventBus);

        HSForceGroups.register();
        HSDensityTable.registerDefaults();

        if (FMLEnvironment.dist == Dist.CLIENT) {
            com.BreadRes.highseas.client.HighSeasClientSetup.register(modContainer);
        }
    }
}