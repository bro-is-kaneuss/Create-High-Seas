package com.BreadRes.highseas.physics;

import dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertyHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

public final class HSBuoyancyMaterial {
    private static final double EPSILON = 1.0E-6;

    private HSBuoyancyMaterial() {
    }

    public static Sample sample(BlockGetter level, BlockPos pos, BlockState state) {
        if (state.isAir()) {
            return Sample.EMPTY;
        }

        double mass = PhysicsBlockPropertyHelper.getMass(level, pos, state);
        double volume = PhysicsBlockPropertyHelper.getVolume(state);

        if (mass <= EPSILON || volume <= EPSILON) {
            return new Sample(
                    mass,
                    volume,
                    0.0,
                    0.0,
                    WeightClass.WEIGHTLESS,
                    false
            );
        }

        double density = mass / volume;
        double displacement = volume;
        WeightClass weightClass = classifyMass(mass);

        return new Sample(
                mass,
                volume,
                density,
                displacement,
                weightClass,
                true
        );
    }

    public static Sample sampleMulti(BlockGetter level, int x, int y, int z) {
        double totalMass = 0.0;
        double totalVolume = 0.0;
        int count = 0;

        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = 0; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos pos = new BlockPos(x + dx, y + dy, z + dz);
                    BlockState state = level.getBlockState(pos);

                    if (state.isAir() || state.getFluidState().is(net.minecraft.tags.FluidTags.WATER)) {
                        continue;
                    }

                    Sample s = sample(level, pos, state);

                    if (!s.contributes()) {
                        continue;
                    }

                    totalMass += s.mass();
                    totalVolume += s.volume();
                    count++;
                }
            }
        }

        if (count <= 0 || totalVolume <= EPSILON) {
            return Sample.EMPTY;
        }

        double avgDensity = totalMass / totalVolume;
        WeightClass wc = classifyMass(totalMass / count);

        return new Sample(
                totalMass / count,
                totalVolume / count,
                avgDensity,
                totalVolume / count,
                wc,
                true
        );
    }

    public static WeightClass classifyMass(double mass) {
        if (mass <= EPSILON) {
            return WeightClass.WEIGHTLESS;
        }

        if (mass <= 0.25) {
            return WeightClass.SUPER_LIGHT;
        }

        if (mass <= 0.5) {
            return WeightClass.LIGHT;
        }

        if (mass < 2.0) {
            return WeightClass.NORMAL;
        }

        if (mass < 4.0) {
            return WeightClass.HEAVY;
        }

        return WeightClass.SUPER_HEAVY;
    }

    public static double buoyancyEfficiency(double density) {
        if (density <= EPSILON) {
            return 0.0;
        }

        return Mth.clamp(1.0 / density, 0.12, 3.0);
    }

    public static double draftFactor(double density) {
        if (density <= EPSILON) {
            return 0.0;
        }

        return Mth.clamp(density, 0.25, 4.0);
    }

    public enum WeightClass {
        WEIGHTLESS,
        SUPER_LIGHT,
        LIGHT,
        NORMAL,
        HEAVY,
        SUPER_HEAVY
    }

    public record Sample(
            double mass,
            double volume,
            double density,
            double displacement,
            WeightClass weightClass,
            boolean contributes
    ) {
        public static final Sample EMPTY = new Sample(
                0.0,
                0.0,
                0.0,
                0.0,
                WeightClass.WEIGHTLESS,
                false
        );
    }
}