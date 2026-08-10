package com.BreadRes.highseas.physics;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.water_occlusion.WaterOcclusionContainer;
import dev.ryanhcode.sable.sublevel.water_occlusion.WaterOcclusionRegion;
import dev.ryanhcode.sable.util.BoundedBitVolume3i;
import net.minecraft.world.level.Level;

import java.util.*;

public final class HSWaterOcclusionBridge {
    private static final int UPDATE_INTERVAL_TICKS = 20;
    private static final int MAX_EXPANDED_SCAN_VOLUME = 2000000;

    private static final Map<SubLevel, RegionEntry> REGIONS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private static final Map<UUID, HSShipGeometry> GEOMETRY_CACHE =
            new HashMap<>();

    private HSWaterOcclusionBridge() {
    }

    public static void setGeometry(SubLevel subLevel, HSShipGeometry geometry) {
        if (geometry != null && subLevel != null) {
            UUID id = subLevel.getUniqueId();
            if (id != null) {
                GEOMETRY_CACHE.put(id, geometry);
            }
        }
    }

    public static void invalidateAll() {
        REGIONS.clear();
        GEOMETRY_CACHE.clear();
    }

    public static void update(Level level) {
        if (level == null) {
            return;
        }

        SubLevelContainer subLevelContainer = SubLevelContainer.getContainer(level);

        if (subLevelContainer == null) {
            clearLevel(level);
            return;
        }

        long gameTime = level.getGameTime();
        Set<SubLevel> live = new HashSet<>();

        for (SubLevel subLevel : subLevelContainer.getAllSubLevels()) {
            if (subLevel == null || subLevel.isRemoved()) {
                continue;
            }

            live.add(subLevel);
            update(level, subLevel, gameTime);
        }

        clearStale(level, live);
    }

    public static void update(Level level, SubLevel subLevel) {
        if (level == null || subLevel == null) {
            return;
        }

        update(level, subLevel, level.getGameTime());
    }

    private static void update(Level level, SubLevel subLevel, long gameTime) {
        if (subLevel.isRemoved() || subLevel.getLevel() != level) {
            remove(level, subLevel);
            return;
        }

        WaterOcclusionContainer<?> container = WaterOcclusionContainer.getContainer(level);

        if (container == null) {
            remove(level, subLevel);
            return;
        }

        BoundingBox3ic bounds = subLevel.getPlot().getBoundingBox();

        if (bounds == null || isEmpty(bounds)) {
            remove(level, subLevel);
            return;
        }

        long signature = signature(bounds);
        RegionEntry old = REGIONS.get(subLevel);

        if (old != null && !old.region().isDirty() && old.boundsSignature() == signature && gameTime < old.nextRebuildTime()) {
            return;
        }

        if (old != null) {
            container.removeRegion(old.region());
            REGIONS.remove(subLevel);
        }

        ScanResult result = scan(bounds, subLevel);

        if (result == null || result.occupiedCells() <= 0) {
            return;
        }

        WaterOcclusionRegion region = container.addRegion(result.volume());

        if (region == null) {
            return;
        }

        REGIONS.put(subLevel, new RegionEntry(
                region,
                signature,
                gameTime + UPDATE_INTERVAL_TICKS,
                result.occupiedCells()
        ));
    }

    private static ScanResult scan(BoundingBox3ic bounds, SubLevel subLevel) {
        int minX = bounds.minX();
        int minY = bounds.minY();
        int minZ = bounds.minZ();
        int maxX = bounds.maxX();
        int maxY = bounds.maxY();
        int maxZ = bounds.maxZ();

        long volume = (long)(maxX - minX + 1) * (long)(maxY - minY + 1) * (long)(maxZ - minZ + 1);

        if (volume <= 0L || volume > MAX_EXPANDED_SCAN_VOLUME) {
            return null;
        }

        UUID id = subLevel != null ? subLevel.getUniqueId() : null;
        HSShipGeometry geo = id != null ? GEOMETRY_CACHE.get(id) : null;

        if (geo == null) {
            return null;
        }

        BoundedBitVolume3i result = new BoundedBitVolume3i(minX, minY, minZ, maxX, maxY, maxZ);
        int occupied = 0;

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    HSCell cell = geo.get(x, y, z);

                    if (cell == null || cell.outside() || cell.solid()) {
                        continue;
                    }

                    result.setOccupied(x, y, z, true);
                    occupied++;
                }
            }
        }

        if (occupied <= 0) {
            return null;
        }

        return new ScanResult(result, occupied);
    }

    private static boolean isEmpty(BoundingBox3ic bounds) {
        return bounds.maxX() < bounds.minX()
                || bounds.maxY() < bounds.minY()
                || bounds.maxZ() < bounds.minZ();
    }

    private static void remove(Level level, SubLevel subLevel) {
        RegionEntry entry = REGIONS.remove(subLevel);

        if (entry == null) {
            return;
        }

        WaterOcclusionContainer<?> container = WaterOcclusionContainer.getContainer(level);

        if (container != null) {
            container.removeRegion(entry.region());
        }
    }

    private static void clearLevel(Level level) {
        WaterOcclusionContainer<?> container = WaterOcclusionContainer.getContainer(level);
        Iterator<Map.Entry<SubLevel, RegionEntry>> iterator;
        synchronized (REGIONS) {
            iterator = new java.util.ArrayList<>(REGIONS.entrySet()).iterator();
        }

        while (iterator.hasNext()) {
            Map.Entry<SubLevel, RegionEntry> entry = iterator.next();

            if (entry.getKey().getLevel() != level) {
                continue;
            }

            if (container != null) {
                container.removeRegion(entry.getValue().region());
            }

            iterator.remove();
        }
    }

    private static void clearStale(Level level, Set<SubLevel> live) {
        WaterOcclusionContainer<?> container = WaterOcclusionContainer.getContainer(level);
        Iterator<Map.Entry<SubLevel, RegionEntry>> iterator;
        synchronized (REGIONS) {
            iterator = new java.util.ArrayList<>(REGIONS.entrySet()).iterator();
        }

        while (iterator.hasNext()) {
            Map.Entry<SubLevel, RegionEntry> entry = iterator.next();
            SubLevel subLevel = entry.getKey();

            if (subLevel.getLevel() != level) {
                continue;
            }

            if (!subLevel.isRemoved() && live.contains(subLevel)) {
                continue;
            }

            if (container != null) {
                container.removeRegion(entry.getValue().region());
            }

            iterator.remove();
        }
    }

    private static long signature(BoundingBox3ic bounds) {
        long value = 1469598103934665603L;
        value = mix(value, bounds.minX());
        value = mix(value, bounds.minY());
        value = mix(value, bounds.minZ());
        value = mix(value, bounds.maxX());
        value = mix(value, bounds.maxY());
        value = mix(value, bounds.maxZ());
        return value;
    }

    private static long mix(long current, int value) {
        long result = current;
        result ^= value;
        result *= 1099511628211L;
        return result;
    }

    private record RegionEntry(
            WaterOcclusionRegion region,
            long boundsSignature,
            long nextRebuildTime,
            int occupiedCells
    ) {
    }

    private record ScanResult(
            BoundedBitVolume3i volume,
            int occupiedCells
    ) {
    }

    public static BoundedBitVolume3i getOcclusionVolume(SubLevel subLevel) {
        RegionEntry entry = REGIONS.get(subLevel);

        if (entry == null) {
            return null;
        }

        return entry.region().getVolume();
    }
}
