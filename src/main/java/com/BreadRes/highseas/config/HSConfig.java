package com.BreadRes.highseas.config;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class HSConfig {
    public static final ModConfigSpec COMMON_SPEC;
    public static final Common COMMON;

    static {
        var commonPair = new ModConfigSpec.Builder().configure(Common::new);
        COMMON = commonPair.getLeft();
        COMMON_SPEC = commonPair.getRight();
    }

    private HSConfig() {
    }

    public static void register(ModContainer container, IEventBus modBus) {
        container.registerConfig(ModConfig.Type.COMMON, COMMON_SPEC);
    }

    public static final class Common {
        Common(ModConfigSpec.Builder builder) {
            builder.comment("HighSeas settings").push("highseas");
            builder.pop();
        }
    }
}
