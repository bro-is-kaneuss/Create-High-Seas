package com.BreadRes.highseas.physics;

import com.BreadRes.highseas.HighSeas;
import com.BreadRes.highseas.network.HSNetwork;
import dev.ryanhcode.sable.api.physics.force.QueuedForceGroup;
import dev.ryanhcode.sable.api.physics.mass.MassData;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.neoforge.event.ForgeSablePrePhysicsTickEvent;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.joml.Vector3d;
import org.joml.Vector3dc;

@EventBusSubscriber(modid = HighSeas.MOD_ID)
public final class HSSableBuoyancyCompat {

    private static final double GRAVITY = 9.81;
    private static final double WATER_DENSITY = 1.0;

    private static final double DAMPING_MULTIPLIER = 1.05;
    private static final double FLOW_DRAG_MULTIPLIER = 0.14;

    private static final double MAX_IMPULSE_PER_POINT = 1.0E100;
    private static final double MIN_FORCE_SQUARED = 0.0001;

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

            applyPhysics(level, subLevel, timeStep);
        }
    }

    private static void applyPhysics(ServerLevel level, ServerSubLevel subLevel, double timeStep) {
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
                floodState,
                timeStep
        );

        HSFloodingSystem.applyFloodWeight(subLevel, floodState, timeStep);
    }

    private static void applyArchimedes(
            ServerLevel level,
            ServerSubLevel subLevel,
            MassData massTracker,
            BoundingBox3ic localBounds,
            HSFloodState floodState,
            double timeStep
    ) {
        HSShipGeometry geo = HSFloodingSystem.getGeometry(subLevel);

        if (geo == null) {
            return;
        }

        double mass = massTracker.getMass();

        Vector3d worldCenter = new Vector3d(
                (localBounds.minX() + localBounds.maxX() + 1.0) * 0.5,
                (localBounds.minY() + localBounds.maxY() + 1.0) * 0.5,
                (localBounds.minZ() + localBounds.maxZ() + 1.0) * 0.5
        );
        subLevel.logicalPose().transformPosition(worldCenter);
        double waterY = findWaterSurface(level, worldCenter.x, worldCenter.z);

        double displacedVolume = 0.0;
        Vector3d buoyancyCenter = new Vector3d();

        for (int x = geo.minX(); x <= geo.maxX(); x++) {
            for (int y = geo.minY(); y <= geo.maxY(); y++) {
                for (int z = geo.minZ(); z <= geo.maxZ(); z++) {
                    HSCell cell = geo.get(x, y, z);

                    if (cell == null || cell.outside()) {
                        continue;
                    }

                    double wy = cellWorldY(subLevel, x, y, z);

                    if (wy < waterY) {
                        displacedVolume += 1.0;
                        buoyancyCenter.add(x + 0.5, y + 0.5, z + 0.5);
                    }
                }
            }
        }

        if (displacedVolume <= 0.0) {
            return;
        }

        buoyancyCenter.div(displacedVolume);

        Vector3d localNormal = new Vector3d(0.0, 1.0, 0.0);
        subLevel.logicalPose().transformNormalInverse(localNormal);
        safeNormalize(localNormal, 0.0, 1.0, 0.0);

        double buoyancyImpulse = WATER_DENSITY * GRAVITY * displacedVolume * timeStep;

        Vector3d pointVel = pointVelocityLocal(subLevel, massTracker, buoyancyCenter);
        double normalVel = pointVel.dot(localNormal);

        double effectiveVolume = Math.max(displacedVolume, 1.0);

        double dampingImpulse = -normalVel * DAMPING_MULTIPLIER * mass * timeStep / effectiveVolume;

        Vector3d horizVel = new Vector3d(pointVel.x, 0.0, pointVel.z);
        Vector3d flowDrag = horizVel.mul(-FLOW_DRAG_MULTIPLIER * mass * timeStep / effectiveVolume);

        Vector3d impulse = new Vector3d(localNormal)
                .mul(buoyancyImpulse + dampingImpulse)
                .add(flowDrag);

        clampLength(impulse, MAX_IMPULSE_PER_POINT);

        if (impulse.lengthSquared() < MIN_FORCE_SQUARED) {
            return;
        }

        QueuedForceGroup forceGroup = subLevel.getOrCreateQueuedForceGroup(HSForceGroups.archimedes());
        forceGroup.applyAndRecordPointForce(buoyancyCenter, impulse);
    }

    private static double cellWorldY(ServerSubLevel subLevel, int x, int y, int z) {
        Vector3d world = new Vector3d(x + 0.5, y + 0.5, z + 0.5);
        subLevel.logicalPose().transformPosition(world);
        return world.y;
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
