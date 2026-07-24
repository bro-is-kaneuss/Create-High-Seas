package com.BreadRes.highseas.physics;

import com.BreadRes.highseas.HighSeas;
import com.BreadRes.highseas.network.HSNetwork;
import dev.ryanhcode.sable.api.physics.force.QueuedForceGroup;
import dev.ryanhcode.sable.api.physics.mass.MassData;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.neoforge.event.ForgeSablePrePhysicsTickEvent;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.joml.Vector3d;
import org.joml.Vector3dc;

@EventBusSubscriber(modid = HighSeas.MOD_ID)
public final class HSSableBuoyancyCompat {

    private static final double GRAVITY = 9.81;
    private static final double DAMPING_MULTIPLIER = 1.05;
    private static final double FLOW_DRAG_MULTIPLIER = 0.14;

    private static final double WIND_FORCE_MULTIPLIER = 0.055;
    private static final double WIND_MIN_EXPOSED_FACTOR = 0.08;
    private static final double WIND_MAX_IMPULSE = 1200.0;
    private static final double WATER_DENSITY = 1.0;
    private static final double DISPLACEMENT_MULTIPLIER = 1.35;
    private static final double FLOODED_DISPLACEMENT_LOSS = 1.0;
    private static final double MIN_DISPLACEMENT_FACTOR = 0.0;

    private static final double SAMPLE_SIDE_INSET = 0.08;
    private static final double SAMPLE_BOTTOM_OFFSET = 0.15;
    private static final double MAX_SUBMERGENCE = 1.15;
    private static final double MAX_IMPULSE_PER_POINT = 3000.0;
    private static final double MIN_FORCE_SQUARED = 0.0001;

    private static final int SAMPLE_GRID = 5;
    private static final int SAMPLE_COUNT = SAMPLE_GRID * SAMPLE_GRID;

    private HSSableBuoyancyCompat() {
    }

    @SubscribeEvent
    public static void onSablePrePhysicsTick(ForgeSablePrePhysicsTickEvent event) {
        ServerLevel level = event.getPhysicsSystem().getLevel();
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);

        if (container == null) {
            return;
        }

        double timeStep = event.getTimeStep();

        if (timeStep <= 0.0 || Double.isNaN(timeStep) || Double.isInfinite(timeStep)) {
            return;
        }

        for (ServerSubLevel subLevel : container.getAllSubLevels()) {
            if (subLevel == null || subLevel.isRemoved()) {
                continue;
            }

            applyArchimedesAndWind(level, subLevel, timeStep);
        }
    }

    private static void applyArchimedesAndWind(ServerLevel level, ServerSubLevel subLevel, double timeStep) {
        MassData massTracker = subLevel.getMassTracker();

        if (massTracker == null || massTracker.isInvalid()) {
            return;
        }

        Vector3dc centerOfMass = massTracker.getCenterOfMass();

        if (centerOfMass == null) {
            return;
        }

        double mass = massTracker.getMass();

        if (mass <= 0.0 || Double.isNaN(mass) || Double.isInfinite(mass)) {
            return;
        }

        BoundingBox3ic localBounds = subLevel.getPlot().getBoundingBox();

        if (localBounds == null) {
            return;
        }

        BoundingBox3dc globalBounds = subLevel.boundingBox();

        if (globalBounds == null) {
            return;
        }

        HSFloodState floodState = HSFloodingSystem.update(
                level,
                subLevel,
                timeStep,
                localBounds
        );

        HSNetwork.syncFlood(level, subLevel, floodState, localBounds);

        applyArchimedes(
                level,
                subLevel,
                massTracker,
                localBounds,
                globalBounds,
                floodState,
                timeStep
        );

        applyWindForce(
                level,
                subLevel,
                massTracker,
                localBounds,
                globalBounds,
                timeStep
        );

        HSFloodingSystem.applyFloodWeight(subLevel, floodState, timeStep);
        HSGaffSailForce.apply(level, subLevel, localBounds, timeStep);
    }

    private static void applyArchimedes(
            ServerLevel level,
            ServerSubLevel subLevel,
            MassData massTracker,
            BoundingBox3ic localBounds,
            BoundingBox3dc globalBounds,
            HSFloodState floodState,
            double timeStep
    ) {
        double mass = massTracker.getMass();

        QueuedForceGroup forceGroup = subLevel.getOrCreateQueuedForceGroup(HSForceGroups.archimedes());

        double minX = localBounds.minX();
        double maxX = localBounds.maxX() + 1.0;
        double minY = localBounds.minY();
        double minZ = localBounds.minZ();
        double maxZ = localBounds.maxZ() + 1.0;

        double sizeX = Math.max(1.0, maxX - minX);
        double sizeZ = Math.max(1.0, maxZ - minZ);

        double sampleMinX = minX + sizeX * SAMPLE_SIDE_INSET;
        double sampleMaxX = maxX - sizeX * SAMPLE_SIDE_INSET;
        double sampleMinZ = minZ + sizeZ * SAMPLE_SIDE_INSET;
        double sampleMaxZ = maxZ - sizeZ * SAMPLE_SIDE_INSET;

        if (sampleMaxX < sampleMinX) {
            double mid = (minX + maxX) * 0.5;
            sampleMinX = mid;
            sampleMaxX = mid;
        }

        if (sampleMaxZ < sampleMinZ) {
            double mid = (minZ + maxZ) * 0.5;
            sampleMinZ = mid;
            sampleMaxZ = mid;
        }

        double sampleY = minY + SAMPLE_BOTTOM_OFFSET;

        double footprintArea = Math.max(1.0, sizeX * sizeZ);
        double sampleArea = footprintArea / SAMPLE_COUNT;

        double floodFill = floodState == null ? 0.0 : floodState.fill();
        double displacementFactor = 1.0 - floodFill * FLOODED_DISPLACEMENT_LOSS;
        displacementFactor = clamp(displacementFactor, MIN_DISPLACEMENT_FACTOR, 1.0);

        for (int ix = 0; ix < SAMPLE_GRID; ix++) {
            double tx = SAMPLE_GRID == 1 ? 0.5 : ix / (double) (SAMPLE_GRID - 1);
            double localX = lerp(sampleMinX, sampleMaxX, tx);

            for (int iz = 0; iz < SAMPLE_GRID; iz++) {
                double tz = SAMPLE_GRID == 1 ? 0.5 : iz / (double) (SAMPLE_GRID - 1);
                double localZ = lerp(sampleMinZ, sampleMaxZ, tz);

                Vector3d localPoint = new Vector3d(localX, sampleY, localZ);
                Vector3d worldPoint = new Vector3d(localPoint);
                subLevel.logicalPose().transformPosition(worldPoint);

                if (!hasWaterNear(level, worldPoint.x, worldPoint.z)) {
                    continue;
                }

                final double waterY = findWaterSurface(level, worldPoint.x, worldPoint.z);
                double submerged = waterY - worldPoint.y;

                if (submerged <= 0.0) {
                    continue;
                }

                double clampedSubmerged = Math.min(submerged, MAX_SUBMERGENCE);
                double submergence = clamp(submerged / MAX_SUBMERGENCE, 0.0, 1.0);

                if (submergence <= 0.0) {
                    continue;
                }

                Vector3d localNormal = new Vector3d(0.0, 1.0, 0.0);
                subLevel.logicalPose().transformNormalInverse(localNormal);
                safeNormalize(localNormal, 0.0, 1.0, 0.0);

                Vector3d localFlow = new Vector3d(0.0, 0.0, 0.0);
                subLevel.logicalPose().transformNormalInverse(localFlow);
                localFlow.y = 0.0;

                Vector3d pointVelocity = pointVelocityLocal(subLevel, massTracker, localPoint);
                double normalVelocity = pointVelocity.dot(localNormal);

                double displacedVolume = sampleArea * clampedSubmerged;

                double archimedesImpulse = WATER_DENSITY
                        * GRAVITY
                        * displacedVolume
                        * DISPLACEMENT_MULTIPLIER
                        * displacementFactor
                        * timeStep;

                double dampingImpulse = -normalVelocity
                        * DAMPING_MULTIPLIER
                        * mass
                        * timeStep
                        / SAMPLE_COUNT
                        * submergence;

                Vector3d horizontalVelocity = new Vector3d(pointVelocity.x, 0.0, pointVelocity.z);
                Vector3d flowDrag = localFlow.sub(horizontalVelocity, new Vector3d())
                        .mul(FLOW_DRAG_MULTIPLIER * mass * timeStep / SAMPLE_COUNT * submergence);

                Vector3d impulse = new Vector3d(localNormal)
                        .mul(archimedesImpulse + dampingImpulse)
                        .add(flowDrag);

                clampLength(impulse, MAX_IMPULSE_PER_POINT);

                if (impulse.lengthSquared() < MIN_FORCE_SQUARED) {
                    continue;
                }

                forceGroup.applyAndRecordPointForce(new Vector3d(localPoint), impulse);
            }
        }
    }

    private static void applyWindForce(
            ServerLevel level,
            ServerSubLevel subLevel,
            MassData massTracker,
            BoundingBox3ic localBounds,
            BoundingBox3dc globalBounds,
            double timeStep
    ) {
        double centerX = (globalBounds.minX() + globalBounds.maxX()) * 0.5;
        double centerZ = (globalBounds.minZ() + globalBounds.maxZ()) * 0.5;

        double waterY = findWaterSurface(level, centerX, centerZ);

        if (globalBounds.maxY() <= waterY) {
            return;
        }

        if (!hasWaterNear(level, centerX, centerZ)) {
            return;
        }

        double mass = massTracker.getMass();

        if (mass <= 0.0 || Double.isNaN(mass) || Double.isInfinite(mass)) {
            return;
        }

        double height = Math.max(1.0, globalBounds.maxY() - globalBounds.minY());
        double exposedHeight = Math.max(0.0, globalBounds.maxY() - waterY);
        double exposedFactor = clamp(exposedHeight / height, 0.0, 1.0);

        if (exposedFactor < WIND_MIN_EXPOSED_FACTOR) {
            return;
        }

        double localMinX = localBounds.minX();
        double localMaxX = localBounds.maxX() + 1.0;
        double localMinY = localBounds.minY();
        double localMaxY = localBounds.maxY() + 1.0;
        double localMinZ = localBounds.minZ();
        double localMaxZ = localBounds.maxZ() + 1.0;

        double localCenterX = (localMinX + localMaxX) * 0.5;
        double localCenterZ = (localMinZ + localMaxZ) * 0.5;
        double localWindY = localMinY + (localMaxY - localMinY) * 0.68;

        Vector3d localWindDirection = new Vector3d(1.0, 0.0, 0.0);
        subLevel.logicalPose().transformNormalInverse(localWindDirection);
        localWindDirection.y = 0.0;
        safeNormalize(localWindDirection, 1.0, 0.0, 0.0);

        double impulseStrength = mass
                * WIND_FORCE_MULTIPLIER
                * exposedFactor
                * timeStep;

        if (impulseStrength <= 0.0 || Double.isNaN(impulseStrength) || Double.isInfinite(impulseStrength)) {
            return;
        }

        Vector3d impulse = new Vector3d(localWindDirection).mul(impulseStrength);
        clampLength(impulse, WIND_MAX_IMPULSE);

        if (impulse.lengthSquared() < MIN_FORCE_SQUARED) {
            return;
        }

        QueuedForceGroup forceGroup = subLevel.getOrCreateQueuedForceGroup(HSForceGroups.wind());

        Vector3d centerPoint = new Vector3d(localCenterX, localWindY, localCenterZ);
        forceGroup.applyAndRecordPointForce(centerPoint, impulse);

        double widthX = Math.max(1.0, localMaxX - localMinX);
        double widthZ = Math.max(1.0, localMaxZ - localMinZ);

        if (Math.max(widthX, widthZ) >= 3.0) {
            double sideOffsetX = widthX * 0.28;
            double sideOffsetZ = widthZ * 0.28;

            Vector3d sideA = new Vector3d(
                    localCenterX - sideOffsetZ * localWindDirection.z,
                    localWindY,
                    localCenterZ + sideOffsetX * localWindDirection.x
            );

            Vector3d sideB = new Vector3d(
                    localCenterX + sideOffsetZ * localWindDirection.z,
                    localWindY,
                    localCenterZ - sideOffsetX * localWindDirection.x
            );

            Vector3d sideImpulse = new Vector3d(impulse).mul(0.35);

            forceGroup.applyAndRecordPointForce(sideA, new Vector3d(sideImpulse));
            forceGroup.applyAndRecordPointForce(sideB, new Vector3d(sideImpulse));
        }
    }

    private static Vector3d pointVelocityLocal(ServerSubLevel subLevel, MassData massTracker, Vector3d localPoint) {
        Vector3d linear = new Vector3d(subLevel.latestLinearVelocity);
        Vector3d angular = new Vector3d(subLevel.latestAngularVelocity);

        Vector3dc centerOfMass = massTracker.getCenterOfMass();

        if (centerOfMass == null) {
            return linear;
        }

        Vector3d radius = localPoint.sub(centerOfMass, new Vector3d());
        Vector3d rotational = angular.cross(radius, new Vector3d());

        return linear.add(rotational);
    }

    private static boolean hasWaterNear(ServerLevel level, double x, double z) {
        int blockX = (int) Math.floor(x);
        int blockZ = (int) Math.floor(z);

        for (int y = level.getMaxBuildHeight() - 1; y >= level.getMinBuildHeight(); y--) {
            BlockPos pos = new BlockPos(blockX, y, blockZ);

            if (!level.isLoaded(pos)) {
                return false;
            }

            FluidState fluid = level.getFluidState(pos);

            if (fluid.is(FluidTags.WATER)) {
                return true;
            }
        }

        return false;
    }

    private static double findWaterSurface(ServerLevel level, double x, double z) {
        int blockX = (int) Math.floor(x);
        int blockZ = (int) Math.floor(z);

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

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static double clamp(double value, double min, double max) {
        if (value < min) {
            return min;
        }

        if (value > max) {
            return max;
        }

        return value;
    }
}