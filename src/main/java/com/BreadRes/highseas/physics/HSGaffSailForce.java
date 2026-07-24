package com.BreadRes.highseas.physics;

import dev.ryanhcode.sable.api.physics.force.QueuedForceGroup;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.joml.Vector3d;

import java.util.HashSet;
import java.util.Set;

public final class HSGaffSailForce {
    private static final double BASE_FORCE_PER_BLOCK = 4.0;
    private static final double MAX_SAIL_FORCE = 800.0;
    private static final double MIN_WIND_DOT = 0.05;

    private HSGaffSailForce() {
    }

    public static void apply(
            ServerLevel level,
            ServerSubLevel subLevel,
            BoundingBox3ic localBounds,
            double timeStep
    ) {
        Set<BlockPos> processed = new HashSet<>();

        for (int x = localBounds.minX(); x <= localBounds.maxX(); x++) {
            for (int y = localBounds.minY(); y <= localBounds.maxY(); y++) {
                for (int z = localBounds.minZ(); z <= localBounds.maxZ(); z++) {
                    BlockPos pos = new BlockPos(x, y, z);

                    if (processed.contains(pos)) {
                        continue;
                    }

                    BlockState state = level.getBlockState(pos);

                    if (!isSailBlock(state)) {
                        continue;
                    }

                    Direction facing = getSailFacing(state);

                    if (facing == null) {
                        continue;
                    }

                    Set<BlockPos> cluster = collectCluster(level, pos, facing);
                    processed.addAll(cluster);

                    applyClusterForce(subLevel, cluster, facing, timeStep);
                }
            }
        }
    }

    private static boolean isSailBlock(BlockState state) {
        return state.getBlock().getDescriptionId().contains("create") &&
                state.getBlock().getDescriptionId().contains("sail");
    }

    private static Direction getSailFacing(BlockState state) {
        try {
            return state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        } catch (Exception e) {
            return null;
        }
    }

    private static Set<BlockPos> collectCluster(ServerLevel level, BlockPos origin, Direction facing) {
        Set<BlockPos> visited = new HashSet<>();
        java.util.ArrayDeque<BlockPos> queue = new java.util.ArrayDeque<>();
        queue.add(origin);
        visited.add(origin);

        Direction[] dirs = {Direction.UP, Direction.DOWN, facing.getClockWise(), facing.getCounterClockWise()};

        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();
            for (Direction dir : dirs) {
                BlockPos neighbor = pos.relative(dir);
                if (visited.contains(neighbor)) continue;
                BlockState neighborState = level.getBlockState(neighbor);
                if (isSailBlock(neighborState) && getSailFacing(neighborState) == facing) {
                    visited.add(neighbor);
                    queue.add(neighbor);
                }
            }
        }
        return visited;
    }

    private static void applyClusterForce(
            ServerSubLevel subLevel,
            Set<BlockPos> cluster,
            Direction facing,
            double timeStep
    ) {
        int area = cluster.size();
        if (area <= 0) return;

        double nx = facing.getStepX();
        double nz = facing.getStepZ();

        double windX = 1.0;
        double windZ = 0.0;
        double windStrength = 1.0;

        double dot = Math.abs(nx * windX + nz * windZ);

        if (dot < MIN_WIND_DOT) return;

        double forceMag = BASE_FORCE_PER_BLOCK * area * dot * windStrength * timeStep;
        forceMag = Math.min(forceMag, MAX_SAIL_FORCE);

        if (forceMag <= 0.001) return;

        double forceWorldX = windX * forceMag;
        double forceWorldZ = windZ * forceMag;

        double cx = 0, cy = 0, cz = 0;
        for (BlockPos pos : cluster) {
            cx += pos.getX() + 0.5;
            cy += pos.getY() + 0.5;
            cz += pos.getZ() + 0.5;
        }
        cx /= area;
        cy /= area;
        cz /= area;

        Vector3d forceWorld = new Vector3d(forceWorldX, 0.0, forceWorldZ);
        subLevel.logicalPose().transformNormalInverse(forceWorld);
        forceWorld.y = 0.0;

        Vector3d localCenter = new Vector3d(cx, cy, cz);

        QueuedForceGroup forceGroup = subLevel.getOrCreateQueuedForceGroup(HSForceGroups.wind());
        forceGroup.applyAndRecordPointForce(localCenter, forceWorld);
    }
}