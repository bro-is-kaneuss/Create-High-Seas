package com.BreadRes.highseas.physics;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public final class HSDensityTable {
    private static final Map<ResourceLocation, HSBlockDensity> OVERRIDES = new HashMap<>();
    private static final Map<TagKey<Block>, HSBlockDensity> CATEGORIES = new LinkedHashMap<>();

    private static final HSBlockDensity DEFAULT_ROCK = new HSBlockDensity(2.50, 1.0, true, true);
    private static final HSBlockDensity DEFAULT_WOOD = new HSBlockDensity(0.60, 1.0, true, true);
    private static final HSBlockDensity DEFAULT_METAL = new HSBlockDensity(7.80, 1.0, true, true);
    private static final HSBlockDensity DEFAULT_DIRT = new HSBlockDensity(1.50, 1.0, true, true);
    private static final HSBlockDensity DEFAULT_WOOL = new HSBlockDensity(0.15, 1.0, true, true);
    private static final HSBlockDensity DEFAULT_GLASS = new HSBlockDensity(2.50, 1.0, true, true);

    private HSDensityTable() {
    }

    public static void registerDefaults() {
        OVERRIDES.clear();
        CATEGORIES.clear();

        register(Blocks.AIR, 0.0, 0.0, false, false);
        register(Blocks.CAVE_AIR, 0.0, 0.0, false, false);
        register(Blocks.VOID_AIR, 0.0, 0.0, false, false);
        register(Blocks.WATER, 1.0, 0.0, false, false);
        register(Blocks.LAVA, 2.8, 0.0, false, false);
        register(Blocks.BARRIER, 0.0, 0.0, false, false);

        register(Blocks.IRON_BLOCK, 7.85);
        register(Blocks.COPPER_BLOCK, 8.90);
        register(Blocks.GOLD_BLOCK, 19.30);
        register(Blocks.NETHERITE_BLOCK, 21.50);
        register(Blocks.ANCIENT_DEBRIS, 15.00);
        register(Blocks.OBSIDIAN, 3.40);
        register(Blocks.CRYING_OBSIDIAN, 3.60);
        register(Blocks.BEDROCK, 100.00);

        register(Blocks.SPONGE, 0.12);
        register(Blocks.WET_SPONGE, 1.10);
        register(Blocks.HAY_BLOCK, 0.18);
        register(Blocks.SLIME_BLOCK, 0.30);
        register(Blocks.HONEY_BLOCK, 1.40);
        register(Blocks.GLASS, 2.50);
        register(Blocks.ICE, 0.92);
        register(Blocks.PACKED_ICE, 0.94);
        register(Blocks.BLUE_ICE, 0.95);

        regCategory(BlockTags.COAL_ORES, 2.30);
        regCategory(BlockTags.IRON_ORES, 3.50);
        regCategory(BlockTags.GOLD_ORES, 4.50);
        regCategory(BlockTags.COPPER_ORES, 3.30);
        regCategory(BlockTags.DIAMOND_ORES, 3.60);
        regCategory(BlockTags.EMERALD_ORES, 3.70);
        regCategory(BlockTags.REDSTONE_ORES, 3.20);
        regCategory(BlockTags.LAPIS_ORES, 3.00);

        regCategory(BlockTags.WOOL, 0.15);
        regCategory(BlockTags.PLANKS, 0.60);
        regCategory(BlockTags.LOGS, 0.70);
        regCategory(BlockTags.LEAVES, 0.20);
        regCategory(BlockTags.ICE, 0.92);
        regCategory(BlockTags.SAND, 1.60);
        regCategory(BlockTags.TERRACOTTA, 2.10);
        regCategory(BlockTags.CONCRETE_POWDER, 1.80);

        regCategory(BlockTags.BASE_STONE_NETHER, 1.10);
        regCategory(BlockTags.BASE_STONE_OVERWORLD, 2.65);
    }

    public static HSBlockDensity get(BlockState state) {
        if (state.isAir()) {
            return HSBlockDensity.AIR;
        }

        Block block = state.getBlock();
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);

        HSBlockDensity override = OVERRIDES.get(id);

        if (override != null) {
            return override;
        }

        for (Map.Entry<TagKey<Block>, HSBlockDensity> entry : CATEGORIES.entrySet()) {
            if (state.is(entry.getKey())) {
                return entry.getValue();
            }
        }

        return guessDensity(state);
    }

    public static double sampleHullDensity(ServerLevel level, double localX, double localY, double localZ) {
        int blockX = (int) Math.floor(localX);
        int blockY = (int) Math.floor(localY);
        int blockZ = (int) Math.floor(localZ);

        double totalDensity = 0.0;
        int solidCount = 0;

        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = 0; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos pos = new BlockPos(blockX + dx, blockY + dy, blockZ + dz);

                    if (!level.isLoaded(pos)) {
                        continue;
                    }

                    BlockState state = level.getBlockState(pos);

                    if (state.isAir() || state.getFluidState().is(net.minecraft.tags.FluidTags.WATER)) {
                        continue;
                    }

                    HSBlockDensity density = get(state);

                    if (!density.contributesMass()) {
                        continue;
                    }

                    totalDensity += density.density();
                    solidCount++;
                }
            }
        }

        if (solidCount <= 0) {
            return 1.0;
        }

        return totalDensity / solidCount;
    }

    private static HSBlockDensity guessDensity(BlockState state) {
        SoundType sound = state.getSoundType();

        if (sound == SoundType.METAL
                || sound == SoundType.COPPER
                || sound == SoundType.NETHERITE_BLOCK) {
            return DEFAULT_METAL;
        }

        if (sound == SoundType.WOOD
                || sound == SoundType.BAMBOO_WOOD
                || sound == SoundType.CHERRY_WOOD
                || sound == SoundType.NETHER_WOOD) {
            return DEFAULT_WOOD;
        }

        if (sound == SoundType.STONE
                || sound == SoundType.DEEPSLATE
                || sound == SoundType.NETHER_ORE
                || sound == SoundType.ANCIENT_DEBRIS) {
            return DEFAULT_ROCK;
        }
        if (sound == SoundType.GRAVEL
                || sound == SoundType.SAND
                || sound == SoundType.GRASS
                || sound == SoundType.ROOTED_DIRT
                || sound == SoundType.MUD) {
            return DEFAULT_DIRT;
        }

        if (sound == SoundType.WOOL) {
            return DEFAULT_WOOL;
        }

        if (sound == SoundType.GLASS) {
            return DEFAULT_GLASS;
        }

        return DEFAULT_ROCK;
    }

    private static void regCategory(TagKey<Block> tag, double density) {
        CATEGORIES.put(tag, new HSBlockDensity(density, 1.0, true, true));
    }

    public static void register(Block block, double density) {
        register(block, density, 1.0, true, true);
    }

    public static void register(Block block, double density, double volume, boolean displacesWater, boolean contributesMass) {
        OVERRIDES.put(BuiltInRegistries.BLOCK.getKey(block), new HSBlockDensity(density, volume, displacesWater, contributesMass));
    }

    public static void register(ResourceLocation id, double density) {
        register(id, density, 1.0, true, true);
    }

    public static void register(ResourceLocation id, double density, double volume, boolean displacesWater, boolean contributesMass) {
        OVERRIDES.put(id, new HSBlockDensity(density, volume, displacesWater, contributesMass));
    }
}