package com.BreadRes.highseas.physics;

import dev.eriksonn.aeronautics.index.AeroTags;
import dev.ryanhcode.sable.api.physics.force.QueuedForceGroup;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.physics.chunk.VoxelNeighborhoodState;
import dev.ryanhcode.sable.util.LevelAccelerator;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.joml.Vector3d;

import java.util.*;

public final class HSFloodingSystem {
    public static final double WATER_DENSITY = 1.0;
    public static final double FLOOD_RATE = 2.8;
    public static final double OVERTOPPING_RATE = 0.25;
    private static final double SABLE_BUOYANCY_COMPENSATION = 0.0;

    private static final double GRAVITY = 9.81;
    private static final double MAX_FLOOD_IMPULSE = 12000.0;
    private static final double BREACH_DEPTH_MARGIN = 0.30;
    private static final double FLOOD_FILL_WATER_MARGIN = 0.05;

    private static final int SCAN_INTERVAL_TICKS = 20;
    private static final int MAX_SCAN_VOLUME = 2000000;

    private static final WeakHashMap<ServerSubLevel, HSFloodState> STATES = new WeakHashMap<>();
    private static final WeakHashMap<ServerSubLevel, HSShipGeometry> GEOMETRIES = new WeakHashMap<>();

    private HSFloodingSystem() {
    }

    public static HSFloodState update(
            ServerLevel level,
            ServerSubLevel subLevel,
            double timeStep,
            BoundingBox3ic localBounds
    ) {
        HSFloodState state = STATES.computeIfAbsent(subLevel, ignored -> new HSFloodState());
        long gameTime = level.getGameTime();

        if (gameTime - state.lastScanTime() >= SCAN_INTERVAL_TICKS) {
            scan(level, subLevel, localBounds, state);
        }

        state.tick(timeStep);

        HSWaterOcclusionBridge.update(level, subLevel);

        return state;
    }

    public static void applyFloodWeight(ServerSubLevel subLevel, HSFloodState state, double timeStep) {
        if (state == null) {
            return;
        }

        double mass = state.extraWaterMass();

        if (mass <= 0.0001 || Double.isNaN(mass) || Double.isInfinite(mass)) {
            return;
        }

        Vector3d localDown = new Vector3d(0.0, -1.0, 0.0);
        subLevel.logicalPose().transformNormalInverse(localDown);
        safeNormalize(localDown, 0.0, -1.0, 0.0);

        double floodWeightImpulse = mass * GRAVITY * timeStep;
        double compensationImpulse = mass * GRAVITY * SABLE_BUOYANCY_COMPENSATION * timeStep;

        Vector3d impulse = localDown.mul(floodWeightImpulse + compensationImpulse);
        clampLength(impulse, MAX_FLOOD_IMPULSE);

        if (impulse.lengthSquared() <= 0.0001) {
            return;
        }

        QueuedForceGroup forceGroup = subLevel.getOrCreateQueuedForceGroup(HSForceGroups.floodWater());
        forceGroup.applyAndRecordPointForce(state.localCenter(), impulse);
    }

    public static HSFloodState get(ServerSubLevel subLevel) {
        return STATES.get(subLevel);
    }

    private static HSShipGeometry geometry(
        ServerSubLevel subLevel,
        BoundingBox3ic bounds
    ) {

        int expandedMinX = bounds.minX() - 1;
        int expandedMinY = bounds.minY() - 1;
        int expandedMinZ = bounds.minZ() - 1;
        int expandedMaxX = bounds.maxX() + 1;
        int expandedMaxY = bounds.maxY() + 1;
        int expandedMaxZ = bounds.maxZ() + 1;

        HSShipGeometry geometry = GEOMETRIES.get(subLevel);

        if (geometry == null
            || geometry.minX() != expandedMinX
            || geometry.minY() != expandedMinY
            || geometry.minZ() != expandedMinZ
            || geometry.maxX() != expandedMaxX
            || geometry.maxY() != expandedMaxY
            || geometry.maxZ() != expandedMaxZ)
        {
            geometry = new HSShipGeometry(expandedMinX, expandedMinY, expandedMinZ,
                                          expandedMaxX, expandedMaxY, expandedMaxZ);
            GEOMETRIES.put(subLevel, geometry);
        }

        return geometry;

    }

    private static void buildGeometry(
        ServerLevel level,
        LevelAccelerator accelerator,
        HSShipGeometry geometry
    ) {

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int x = geometry.minX(); x <= geometry.maxX(); x++) {

            for (int y = geometry.minY(); y <= geometry.maxY(); y++) {

                for (int z = geometry.minZ(); z <= geometry.maxZ(); z++) {

                    HSCell cell = geometry.get(x, y, z);

                    cursor.set(x, y, z);

                    boolean passable =
                            isFloodPassable(accelerator, level, cursor);

                    cell.setSolid(!passable);

                    if (!geometry.outsideComputed()) {
                        cell.setOutside(false);
                    }

                    cell.setFlooded(false);

                    cell.setBreach(false);

                    cell.setCompartment(-1);
                }

            }

        }

    }

    private static boolean isBoundary(
        int x,
        int y,
        int z,
        HSShipGeometry geometry
    ) {

        return x == geometry.minX()
                || x == geometry.maxX()
                || y == geometry.minY()
                || y == geometry.maxY()
                || z == geometry.minZ()
                || z == geometry.maxZ();
    }

    private static void visitOutside(
        HSShipGeometry geometry,
        ArrayDeque<HSCell> queue,
        int x,
        int y,
        int z
    ) {

        HSCell next = geometry.get(x, y, z);

        if (next == null)
            return;

        if (!next.passable())
            return;

        if (next.outside())
            return;

        next.setOutside(true);

        if (isAdjacentToSolid(geometry, x, y, z))
            return;

        queue.add(next);
    }

    private static boolean isAdjacentToSolid(HSShipGeometry geometry, int x, int y, int z) {
        return geometry.get(x + 1, y, z) != null && geometry.get(x + 1, y, z).solid()
            || geometry.get(x - 1, y, z) != null && geometry.get(x - 1, y, z).solid()
            || geometry.get(x, y + 1, z) != null && geometry.get(x, y + 1, z).solid()
            || geometry.get(x, y - 1, z) != null && geometry.get(x, y - 1, z).solid()
            || geometry.get(x, y, z + 1) != null && geometry.get(x, y, z + 1).solid()
            || geometry.get(x, y, z - 1) != null && geometry.get(x, y, z - 1).solid();
    }

    private static void markOutsideCells(
        HSShipGeometry geometry
    ) {

        ArrayDeque<HSCell> queue = new ArrayDeque<>();

        for (int x = geometry.minX(); x <= geometry.maxX(); x++) {
            for (int y = geometry.minY(); y <= geometry.maxY(); y++) {
                for (int z = geometry.minZ(); z <= geometry.maxZ(); z++) {

                    if (!isBoundary(x, y, z, geometry))
                        continue;

                    HSCell cell = geometry.get(x, y, z);

                    if (cell == null)
                        continue;

                    if (!cell.passable())
                        continue;

                    if (cell.outside())
                        continue;

                    cell.setOutside(true);
                    queue.add(cell);
                }
            }
        }

        while (!queue.isEmpty()) {

            HSCell cell = queue.removeFirst();

            visitOutside(geometry, queue, cell.x() + 1, cell.y(), cell.z());
            visitOutside(geometry, queue, cell.x() - 1, cell.y(), cell.z());

            visitOutside(geometry, queue, cell.x(), cell.y() + 1, cell.z());
            visitOutside(geometry, queue, cell.x(), cell.y() - 1, cell.z());

            visitOutside(geometry, queue, cell.x(), cell.y(), cell.z() + 1);
            visitOutside(geometry, queue, cell.x(), cell.y(), cell.z() - 1);
        }
    }

    private static void computeCompartments(
        HSShipGeometry geometry
    ) {

        int nextId = 0;

        ArrayDeque<HSCell> queue = new ArrayDeque<>();

        for (int x = geometry.minX(); x <= geometry.maxX(); x++) {
            for (int y = geometry.minY(); y <= geometry.maxY(); y++) {
                for (int z = geometry.minZ(); z <= geometry.maxZ(); z++) {

                    HSCell start = geometry.get(x, y, z);

                    if (start == null)
                        continue;

                    if (!start.passable())
                        continue;

                    if (start.outside())
                        continue;

                    if (start.compartment() != -1)
                        continue;

                    start.setCompartment(nextId);

                    queue.add(start);

                    while (!queue.isEmpty()) {

                        HSCell cell = queue.removeFirst();

                        floodCompartment(geometry, queue, cell.x()+1, cell.y(), cell.z(), nextId);
                        floodCompartment(geometry, queue, cell.x()-1, cell.y(), cell.z(), nextId);

                        floodCompartment(geometry, queue, cell.x(), cell.y()+1, cell.z(), nextId);
                        floodCompartment(geometry, queue, cell.x(), cell.y()-1, cell.z(), nextId);

                        floodCompartment(geometry, queue, cell.x(), cell.y(), cell.z()+1, nextId);
                        floodCompartment(geometry, queue, cell.x(), cell.y(), cell.z()-1, nextId);

                    }

                    nextId++;

                }
            }
        }
    }

    private static void floodCompartment(
        HSShipGeometry geometry,
        ArrayDeque<HSCell> queue,
        int x,
        int y,
        int z,
        int id
    ) {

        HSCell cell = geometry.get(x, y, z);

        if (cell == null)
            return;

        if (!cell.passable())
            return;

        if (cell.outside())
            return;

        if (cell.compartment() != -1)
            return;

        cell.setCompartment(id);

        queue.add(cell);

    }

    private static void scan(
            ServerLevel level,
            ServerSubLevel subLevel,
            BoundingBox3ic bounds,
            HSFloodState state
    ) {
        int minX = bounds.minX();
        int minY = bounds.minY();
        int minZ = bounds.minZ();
        int maxX = bounds.maxX();
        int maxY = bounds.maxY();
        int maxZ = bounds.maxZ();

        int sizeX = maxX - minX + 1;
        int sizeY = maxY - minY + 1;
        int sizeZ = maxZ - minZ + 1;

        long volume = (long) sizeX * sizeY * sizeZ;

        if (volume <= 0 || volume > MAX_SCAN_VOLUME) {
            state.resetIfNoVolume(level.getGameTime(), centerOf(bounds));
            return;
        }

        LevelAccelerator accelerator = new LevelAccelerator(level);

        HSShipGeometry geometry = geometry(subLevel, bounds);

        buildGeometry(level, accelerator, geometry);

        if (!geometry.outsideComputed()) {
            markOutsideCells(geometry);
            geometry.setOutsideComputed(true);
        }

        computeCompartments(geometry);

        findBreaches(level, subLevel, geometry);

        clearFloodFlags(geometry);

        ArrayDeque<BlockPos> queue = new ArrayDeque<>();

        int breachCount = 0;
        double pressureSum = 0.0;

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        Set<Integer> floodedCompartments = new HashSet<>();

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    cursor.set(x, y, z);

                    HSCell cell = geometry.get(x, y, z);

                    if (cell == null)
                        continue;

                    if (!cell.passable())
                        continue;

                    if (cell.outside())
                        continue;
                    
                    if (!cell.breach())
                        continue;

                    if (cell.compartment() != -1 && floodedCompartments.contains(cell.compartment())) 
                        continue;

                    Vector3d world = localCellCenterToWorld(subLevel, x, y, z);
                    double waterY = waterYAt(level, world.x, world.z);

                    double margin = (y == maxY) ? 1.5 : BREACH_DEPTH_MARGIN;
                    if (world.y >= waterY + margin) {
                        continue;
                    }

                    if (!cell.flooded()) {
                        cell.setFlooded(true);
                        queue.add(new BlockPos(x, y, z));
                        floodedCompartments.add(cell.compartment());
                    }

                    breachCount++;
                    pressureSum += waterY - world.y;
                }
            }
        }

        if (breachCount <= 0 || queue.isEmpty()) {
            double overtoppingPressure = computeOvertoppingPressure(level, subLevel, bounds);

            state.setScan(
                    level.getGameTime(),
                    0.0,
                    0,
                    0.0,
                    overtoppingPressure,
                    centerOf(bounds)
            );

            return;
        }

        int floodable = 0;
        Vector3d center = new Vector3d();

        while (!queue.isEmpty()) {
            BlockPos pos = queue.removeFirst();

            HSCell current = geometry.get(
                pos.getX(),
                pos.getY(),
                pos.getZ()
            );

            int compartment = current.compartment();

            if (current.outside())
                continue;

            floodable++;
            center.add(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);

            tryVisitCompartment(geometry, queue, pos.getX() + 1, pos.getY(), pos.getZ(), compartment);
            tryVisitCompartment(geometry, queue, pos.getX() - 1, pos.getY(), pos.getZ(), compartment);
            tryVisitCompartment(geometry, queue, pos.getX(), pos.getY() + 1, pos.getZ(), compartment);
            tryVisitCompartment(geometry, queue, pos.getX(), pos.getY() - 1, pos.getZ(), compartment);
            tryVisitCompartment(geometry, queue, pos.getX(), pos.getY(), pos.getZ() + 1, compartment);
            tryVisitCompartment(geometry, queue, pos.getX(), pos.getY(), pos.getZ() - 1, compartment);
        }

        if (floodable < 10) {
            double overtoppingPressure = computeOvertoppingPressure(level, subLevel, bounds);
            state.setScan(level.getGameTime(), 0, 0, 0, overtoppingPressure, centerOf(bounds));
            return;
        }

        center.div(floodable);

        double pressure = pressureSum / Math.max(1, breachCount);
        double overtoppingPressure = computeOvertoppingPressure(level, subLevel, bounds);

        state.setScan(
                level.getGameTime(),
                floodable,
                breachCount,
                pressure,
                overtoppingPressure,
                center
        );
    }

    private static void tryVisitCompartment(
        HSShipGeometry geometry,
        ArrayDeque<BlockPos> queue,
        int x,
        int y,
        int z,
        int compartment
    ) {

        HSCell cell = geometry.get(x, y, z);

        if (cell == null)
            return;

        if (!cell.passable())
            return;

        if (cell.flooded())
            return;

        if (cell.outside())
            return;

        if (cell.compartment() != compartment)
            return;

        cell.setFlooded(true);

        queue.add(new BlockPos(x, y, z));
    }

    private static void findBreaches(
        ServerLevel level,
        ServerSubLevel subLevel,
        HSShipGeometry geometry
    ) {

        int[][] dirs = {
                {1,0,0},
                {-1,0,0},
                {0,1,0},
                {0,-1,0},
                {0,0,1},
                {0,0,-1}
        };

        for (int x = geometry.minX(); x <= geometry.maxX(); x++) {
            for (int y = geometry.minY(); y <= geometry.maxY(); y++) {
                for (int z = geometry.minZ(); z <= geometry.maxZ(); z++) {

                    HSCell cell = geometry.get(x, y, z);

                    if (cell == null)
                        continue;

                    if (!cell.passable())
                        continue;

                    if (cell.outside())
                        continue;

                    for (int[] dir : dirs) {

                        HSCell outside = geometry.get(
                                x + dir[0],
                                y + dir[1],
                                z + dir[2]
                        );

                        if (outside == null)
                            continue;

                        if (!outside.outside())
                            continue;

                        Vector3d world = localCellCenterToWorld(
                                subLevel,
                                outside.x(),
                                outside.y(),
                                outside.z()
                        );

                        BlockPos pos = BlockPos.containing(
                            world.x,
                            world.y,
                            world.z
                        );

                        if (!level.isLoaded(pos))
                            continue;

                        if (!level.getFluidState(pos).is(FluidTags.WATER))
                            continue;

                        cell.setBreach(true);
                        break;
                    }
                }
            }
        }
    }

    private static boolean isFloodPassable(LevelAccelerator accelerator, ServerLevel level, BlockPos pos) {
        BlockState state = accelerator.getBlockState(pos);

        if (state.isAir()) {
            return true;
        }

        FluidState fluid = state.getFluidState();

        if (fluid.is(FluidTags.WATER)) {
            return true;
        }

        if (state.is(AeroTags.BlockTags.AIRTIGHT)) {
            return false;
        }

        return !VoxelNeighborhoodState.isSolid(accelerator, pos, state);
    }

    private static double waterYAt(ServerLevel level, double worldX, double worldZ) {
        int blockX = (int) Math.floor(worldX);
        int blockZ = (int) Math.floor(worldZ);

        for (int y = level.getMaxBuildHeight() - 1; y >= level.getMinBuildHeight(); y--) {
            BlockPos pos = new BlockPos(blockX, y, blockZ);

            if (!level.isLoaded(pos)) {
                continue;
            }

            if (level.getFluidState(pos).is(FluidTags.WATER)) {
                return y + 1;
            }
        }

        return level.getSeaLevel();
    }

    private static double computeOvertoppingPressure(
            ServerLevel level,
            ServerSubLevel subLevel,
            BoundingBox3ic bounds
    ) {
        int minX = bounds.minX();
        int minY = bounds.minY();
        int minZ = bounds.minZ();
        int maxX = bounds.maxX();
        int maxY = bounds.maxY();
        int maxZ = bounds.maxZ();

        if (maxY <= minY) {
            return 0.0;
        }

        LevelAccelerator overtoppingAccelerator = new LevelAccelerator(level);
        Set<Long> outsideReachableCheck = computeOutsideReachable(
                overtoppingAccelerator, level, minX, minY, minZ, maxX, maxY, maxZ);
        boolean hasEnclosedInterior = false;
        BlockPos.MutableBlockPos checkCursor = new BlockPos.MutableBlockPos();
        outer:
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    checkCursor.set(x, y, z);
                    if (!isFloodPassable(overtoppingAccelerator, level, checkCursor)) continue;
                    if (!outsideReachableCheck.contains(pack(x, y, z))) {
                        hasEnclosedInterior = true;
                        break outer;
                    }
                }
            }
        }
        if (!hasEnclosedInterior) {
            return 0.0;
        }

        double lowestRimY = Double.POSITIVE_INFINITY;
        boolean hasRim = false;

        for (int x = minX; x <= maxX; x++) {
            double a = rimWorldY(subLevel, x, maxY, minZ);
            double b = rimWorldY(subLevel, x, maxY, maxZ);

            if (!Double.isNaN(a)) {
                lowestRimY = Math.min(lowestRimY, a);
                hasRim = true;
            }

            if (!Double.isNaN(b)) {
                lowestRimY = Math.min(lowestRimY, b);
                hasRim = true;
            }
        }

        for (int z = minZ; z <= maxZ; z++) {
            double a = rimWorldY(subLevel, minX, maxY, z);
            double b = rimWorldY(subLevel, maxX, maxY, z);

            if (!Double.isNaN(a)) {
                lowestRimY = Math.min(lowestRimY, a);
                hasRim = true;
            }

            if (!Double.isNaN(b)) {
                lowestRimY = Math.min(lowestRimY, b);
                hasRim = true;
            }
        }

        if (!hasRim || Double.isInfinite(lowestRimY)) {
            return 0.0;
        }

        Vector3d center = localCellCenterToWorld(
                subLevel,
                (minX + maxX) / 2,
                maxY,
                (minZ + maxZ) / 2
        );

        double waterY = waterYAt(level, center.x, center.z);

        if (waterY <= lowestRimY) {
            return 0.0;
        }

        return Math.min(4.0, waterY - lowestRimY);
    }

    private static double rimWorldY(ServerSubLevel subLevel, int x, int y, int z) {
        Vector3d world = localCellCenterToWorld(subLevel, x, y, z);

        if (Double.isNaN(world.y) || Double.isInfinite(world.y)) {
            return Double.NaN;
        }

        return world.y + 0.5;
    }

    private static Vector3d localCellCenterToWorld(ServerSubLevel subLevel, int x, int y, int z) {
        Vector3d world = new Vector3d(x + 0.5, y + 0.5, z + 0.5);
        subLevel.logicalPose().transformPosition(world);
        return world;
    }

    private static Set<Long> computeOutsideReachable(
            LevelAccelerator accelerator,
            ServerLevel level,
            int minX, int minY, int minZ,
            int maxX, int maxY, int maxZ
    ) {
        int exMinX = minX - 1;
        int exMinY = minY - 1;
        int exMinZ = minZ - 1;
        int exMaxX = maxX + 1;
        int exMaxY = maxY + 1;
        int exMaxZ = maxZ + 1;

        Set<Long> outside = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int x = exMinX; x <= exMaxX; x++) {
            for (int y = exMinY; y <= exMaxY; y++) {
                for (int z = exMinZ; z <= exMaxZ; z++) {
                    boolean boundary = x == exMinX || x == exMaxX
                            || y == exMinY || y == exMaxY
                            || z == exMinZ || z == exMaxZ;
                    if (!boundary) continue;
                    if (x < minX || x > maxX || y < minY || y > maxY || z < minZ || z > maxZ) continue;
                    cursor.set(x, y, z);
                    if (!isFloodPassable(accelerator, level, cursor)) continue;
                    long key = pack(x, y, z);
                    if (outside.add(key)) queue.add(new BlockPos(x, y, z));
                }
            }
        }

        int[] dx = {1, -1, 0, 0, 0, 0};
        int[] dy = {0, 0, 1, -1, 0, 0};
        int[] dz = {0, 0, 0, 0, 1, -1};

        while (!queue.isEmpty()) {
            BlockPos pos = queue.removeFirst();
            for (int i = 0; i < 6; i++) {
                int nx = pos.getX() + dx[i];
                int ny = pos.getY() + dy[i];
                int nz = pos.getZ() + dz[i];
                if (nx < minX || nx > maxX || ny < minY || ny > maxY || nz < minZ || nz > maxZ) continue;
                long key = pack(nx, ny, nz);
                if (outside.contains(key)) continue;
                cursor.set(nx, ny, nz);
                if (!isFloodPassable(accelerator, level, cursor)) continue;
                outside.add(key);
                queue.add(new BlockPos(nx, ny, nz));
            }
        }

        return outside;
    }

    private static Vector3d centerOf(BoundingBox3ic bounds) {
        return new Vector3d(
                (bounds.minX() + bounds.maxX() + 1.0) * 0.5,
                (bounds.minY() + bounds.maxY() + 1.0) * 0.5,
                (bounds.minZ() + bounds.maxZ() + 1.0) * 0.5
        );
    }

    private static long pack(int x, int y, int z) {
        long lx = ((long) x & 0x3FFFFFFL);
        long ly = ((long) y & 0xFFFL);
        long lz = ((long) z & 0x3FFFFFFL);

        return lx | (lz << 26) | (ly << 52);
    }

    private static void clearFloodFlags(HSShipGeometry geometry) {

        for (int x = geometry.minX(); x <= geometry.maxX(); x++) {
            for (int y = geometry.minY(); y <= geometry.maxY(); y++) {
                for (int z = geometry.minZ(); z <= geometry.maxZ(); z++) {

                    HSCell cell = geometry.get(x, y, z);

                    if (cell != null) {
                        cell.setFlooded(false);
                    }
                }
            }
        }

    }

    private static void safeNormalize(Vector3d vector, double fallbackX, double fallbackY, double fallbackZ) {
        double len2 = vector.lengthSquared();

        if (len2 < 1.0E-9 || Double.isNaN(len2) || Double.isInfinite(len2)) {
            vector.set(fallbackX, fallbackY, fallbackZ);
            return;
        }

        vector.mul(1.0 / Math.sqrt(len2));
    }

    private static void clampLength(Vector3d vector, double maxLength) {
        double len2 = vector.lengthSquared();

        if (len2 <= maxLength * maxLength) {
            return;
        }

        if (len2 < 1.0E-9 || Double.isNaN(len2) || Double.isInfinite(len2)) {
            vector.zero();
            return;
        }

        vector.mul(maxLength / Math.sqrt(len2));
    }
}