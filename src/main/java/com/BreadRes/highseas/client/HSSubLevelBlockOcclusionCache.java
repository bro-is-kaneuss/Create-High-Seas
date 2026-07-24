package com.BreadRes.highseas.client;

import com.BreadRes.highseas.HighSeas;
import dev.ryanhcode.sable.api.sublevel.ClientSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;

@EventBusSubscriber(modid = HighSeas.MOD_ID, value = Dist.CLIENT)
public final class HSSubLevelBlockOcclusionCache {

    public record WorldAABB(
            double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ
    ) {
        public boolean contains(double x, double y, double z) {
            return x >= minX && x <= maxX
                    && y >= minY && y <= maxY
                    && z >= minZ && z <= maxZ;
        }
    }

    private static volatile List<WorldAABB> CACHE = List.of();

    private HSSubLevelBlockOcclusionCache() {
    }

    @SubscribeEvent
    public static void onBeforeRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) {
            return;
        }

        Level level = Minecraft.getInstance().level;

        if (level == null) {
            CACHE = List.of();
            return;
        }

        SubLevelContainer raw = SubLevelContainer.getContainer(level);

        if (!(raw instanceof ClientSubLevelContainer container)) {
            CACHE = List.of();
            return;
        }

        List<WorldAABB> list = new ArrayList<>();

        for (ClientSubLevel subLevel : container.getAllSubLevels()) {
            if (subLevel == null || subLevel.isRemoved() || !subLevel.isFinalized()) {
                continue;
            }

            var plot = subLevel.getPlot();

            if (plot == null) {
                continue;
            }

            var bounds = plot.getBoundingBox();

            if (bounds == null) {
                continue;
            }

            double minWX = Double.MAX_VALUE, minWY = Double.MAX_VALUE, minWZ = Double.MAX_VALUE;
            double maxWX = -Double.MAX_VALUE, maxWY = -Double.MAX_VALUE, maxWZ = -Double.MAX_VALUE;

            int[] xs = {bounds.minX(), bounds.maxX() + 1};
            int[] ys = {bounds.minY(), bounds.maxY() + 1};
            int[] zs = {bounds.minZ(), bounds.maxZ() + 1};

            for (int lx : xs) {
                for (int ly : ys) {
                    for (int lz : zs) {
                        Vector3d w = new Vector3d(lx, ly, lz);
                        subLevel.logicalPose().transformPosition(w);

                        minWX = Math.min(minWX, w.x);
                        minWY = Math.min(minWY, w.y);
                        minWZ = Math.min(minWZ, w.z);
                        maxWX = Math.max(maxWX, w.x);
                        maxWY = Math.max(maxWY, w.y);
                        maxWZ = Math.max(maxWZ, w.z);
                    }
                }
            }

            double shrink = 0.05;
            list.add(new WorldAABB(
                    minWX + shrink, minWY + shrink, minWZ + shrink,
                    maxWX - shrink, maxWY - shrink, maxWZ - shrink
            ));
        }

        CACHE = List.copyOf(list);
    }

    public static boolean isInsideAnySubLevel(int blockX, int blockY, int blockZ) {
        List<WorldAABB> snapshot = CACHE;

        if (snapshot.isEmpty()) {
            return false;
        }

        double cx = blockX + 0.5;
        double cy = blockY + 0.5;
        double cz = blockZ + 0.5;

        for (WorldAABB aabb : snapshot) {
            if (aabb.contains(cx, cy, cz)) {
                return true;
            }
        }

        return false;
    }

    public static void invalidate() {
        CACHE = List.of();
    }
}