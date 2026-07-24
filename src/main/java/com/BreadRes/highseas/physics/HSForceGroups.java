package com.BreadRes.highseas.physics;

import com.BreadRes.highseas.HighSeas;
import dev.ryanhcode.sable.api.physics.force.ForceGroup;
import dev.ryanhcode.sable.api.physics.force.ForceGroups;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class HSForceGroups {
    private static boolean registered = false;
    private static ForceGroup archimedes;
    private static ForceGroup wind;
    private static ForceGroup floodWater;

    private HSForceGroups() {
    }

    public static void register() {
        if (registered) {
            return;
        }

        registered = true;

        ResourceLocation archimedesId = ResourceLocation.fromNamespaceAndPath(HighSeas.MOD_ID, "archimedes");
        ResourceLocation windId = ResourceLocation.fromNamespaceAndPath(HighSeas.MOD_ID, "wind");
        ResourceLocation floodWaterId = ResourceLocation.fromNamespaceAndPath(HighSeas.MOD_ID, "flood_water");

        archimedes = ForceGroups.REGISTRY.getOptional(archimedesId).orElseGet(() -> Registry.register(
                ForceGroups.REGISTRY,
                archimedesId,
                new ForceGroup(
                        Component.translatable("force_group.highseas.archimedes"),
                        Component.translatable("force_group.highseas.archimedes.description"),
                        0x2F8FC9,
                        true
                )
        ));

        wind = ForceGroups.REGISTRY.getOptional(windId).orElseGet(() -> Registry.register(
                ForceGroups.REGISTRY,
                windId,
                new ForceGroup(
                        Component.translatable("force_group.highseas.wind"),
                        Component.translatable("force_group.highseas.wind.description"),
                        0x9BCBFF,
                        true
                )
        ));

        floodWater = ForceGroups.REGISTRY.getOptional(floodWaterId).orElseGet(() -> Registry.register(
                ForceGroups.REGISTRY,
                floodWaterId,
                new ForceGroup(
                        Component.translatable("force_group.highseas.flood_water"),
                        Component.translatable("force_group.highseas.flood_water.description"),
                        0x1C4F8C,
                        true
                )
        ));
    }

    public static ForceGroup archimedes() {
        if (!registered) {
            register();
        }

        return archimedes;
    }

    public static ForceGroup wind() {
        if (!registered) {
            register();
        }

        return wind;
    }

    public static ForceGroup floodWater() {
        if (!registered) {
            register();
        }

        return floodWater;
    }
}