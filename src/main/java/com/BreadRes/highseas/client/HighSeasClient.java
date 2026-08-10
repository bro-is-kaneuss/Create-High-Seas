package com.BreadRes.highseas.client;

import com.BreadRes.highseas.HighSeas;
import com.BreadRes.highseas.physics.HSWaterOcclusionBridge;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.ryanhcode.sable.api.sublevel.ClientSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import org.joml.Vector3d;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = HighSeas.MOD_ID, value = Dist.CLIENT)
public final class HighSeasClient {
    private HighSeasClient() {
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY) {
            HSWaterOcclusionBridge.update(Minecraft.getInstance().level);
        }

        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            renderFloodedCells(event);
        }
    }

    @SubscribeEvent
    public static void onPlayerLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        HSWaterOcclusionBridge.invalidateAll();
        HSSubLevelBlockOcclusionCache.invalidate();
    }

    private static void renderFloodedCells(RenderLevelStageEvent event) {
        Level level = Minecraft.getInstance().level;
        if (level == null) return;

        Map<UUID, HSClientFloodStates.SyncedFloodState> states = HSClientFloodStates.all();
        if (states.isEmpty()) return;

        Map<UUID, ClientSubLevel> subLevels = getClientSubLevels(level);
        if (subLevels.isEmpty()) return;

        Vec3 cameraPos = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        double camX = cameraPos.x;
        double camY = cameraPos.y;
        double camZ = cameraPos.z;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

        float r = 0.2f, g = 0.5f, b = 0.9f, a = 0.45f;

        BufferBuilder builder = Tesselator.getInstance()
                .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        int vertexCount = 0;

        for (Map.Entry<UUID, HSClientFloodStates.SyncedFloodState> entry : states.entrySet()) {
            UUID uuid = entry.getKey();
            HSClientFloodStates.SyncedFloodState state = entry.getValue();
            ClientSubLevel subLevel = subLevels.get(uuid);
            if (subLevel == null) continue;

            for (int x = state.minX(); x <= state.maxX(); x++) {
                for (int y = state.minY(); y <= state.maxY(); y++) {
                    for (int z = state.minZ(); z <= state.maxZ(); z++) {
                        if (!state.isFlooded(x, y, z)) continue;

                        Vector3d worldPos = localToWorld(subLevel, x, y, z);
                        float bx = (float) (worldPos.x - camX);
                        float by = (float) (worldPos.y - camY);
                        float bz = (float) (worldPos.z - camZ);

                        if (shouldRenderFace(level, subLevel, state, x, y, z, 0, -1, 0)) {
                            addFace(builder, bx, by, bz, 0, r, g, b, a);
                            vertexCount += 4;
                        }
                        if (shouldRenderFace(level, subLevel, state, x, y, z, 0, 1, 0)) {
                            addFace(builder, bx, by, bz, 1, r, g, b, a);
                            vertexCount += 4;
                        }
                        if (shouldRenderFace(level, subLevel, state, x, y, z, -1, 0, 0)) {
                            addFace(builder, bx, by, bz, 2, r, g, b, a);
                            vertexCount += 4;
                        }
                        if (shouldRenderFace(level, subLevel, state, x, y, z, 1, 0, 0)) {
                            addFace(builder, bx, by, bz, 3, r, g, b, a);
                            vertexCount += 4;
                        }
                        if (shouldRenderFace(level, subLevel, state, x, y, z, 0, 0, -1)) {
                            addFace(builder, bx, by, bz, 4, r, g, b, a);
                            vertexCount += 4;
                        }
                        if (shouldRenderFace(level, subLevel, state, x, y, z, 0, 0, 1)) {
                            addFace(builder, bx, by, bz, 5, r, g, b, a);
                            vertexCount += 4;
                        }
                    }
                }
            }
        }

        if (vertexCount == 0) {
            RenderSystem.disableBlend();
            return;
        }

        BufferUploader.drawWithShader(builder.buildOrThrow());

        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    private static Map<UUID, ClientSubLevel> getClientSubLevels(Level level) {
        Map<UUID, ClientSubLevel> result = new HashMap<>();
        SubLevelContainer raw = SubLevelContainer.getContainer(level);
        if (!(raw instanceof ClientSubLevelContainer container)) return result;
        for (ClientSubLevel sl : container.getAllSubLevels()) {
            if (sl != null && !sl.isRemoved()) {
                UUID id = sl.getUniqueId();
                if (id != null) {
                    result.put(id, sl);
                }
            }
        }
        return result;
    }

    private static boolean shouldRenderFace(Level level, ClientSubLevel subLevel,
                                            HSClientFloodStates.SyncedFloodState state,
                                            int x, int y, int z, int dx, int dy, int dz) {
        int nx = x + dx;
        int ny = y + dy;
        int nz = z + dz;

        if (state.isFlooded(nx, ny, nz)) {
            return false;
        }

        Vector3d neighborWorld = localToWorld(subLevel, nx, ny, nz);
        BlockPos worldPos = BlockPos.containing(neighborWorld.x, neighborWorld.y, neighborWorld.z);
        if (level.isLoaded(worldPos)) {
            BlockState blockState = level.getBlockState(worldPos);
            if (!blockState.isAir() && blockState.getFluidState().isEmpty()) {
                return false;
            }
        }

        return true;
    }

    private static Vector3d localToWorld(ClientSubLevel subLevel, int x, int y, int z) {
        Vector3d pos = new Vector3d(x, y, z);
        subLevel.logicalPose().transformPosition(pos);
        return pos;
    }

    private static void addFace(BufferBuilder b, float x, float y, float z, int face,
                                float r, float g, float bl, float a) {
        switch (face) {
            case 0 -> {
                b.addVertex(x, y, z + 1).setColor(r, g, bl, a);
                b.addVertex(x + 1, y, z + 1).setColor(r, g, bl, a);
                b.addVertex(x + 1, y, z).setColor(r, g, bl, a);
                b.addVertex(x, y, z).setColor(r, g, bl, a);
            }
            case 1 -> {
                b.addVertex(x, y + 1, z).setColor(r, g, bl, a);
                b.addVertex(x + 1, y + 1, z).setColor(r, g, bl, a);
                b.addVertex(x + 1, y + 1, z + 1).setColor(r, g, bl, a);
                b.addVertex(x, y + 1, z + 1).setColor(r, g, bl, a);
            }
            case 2 -> {
                b.addVertex(x, y, z).setColor(r, g, bl, a);
                b.addVertex(x, y + 1, z).setColor(r, g, bl, a);
                b.addVertex(x, y + 1, z + 1).setColor(r, g, bl, a);
                b.addVertex(x, y, z + 1).setColor(r, g, bl, a);
            }
            case 3 -> {
                b.addVertex(x + 1, y, z + 1).setColor(r, g, bl, a);
                b.addVertex(x + 1, y + 1, z + 1).setColor(r, g, bl, a);
                b.addVertex(x + 1, y + 1, z).setColor(r, g, bl, a);
                b.addVertex(x + 1, y, z).setColor(r, g, bl, a);
            }
            case 4 -> {
                b.addVertex(x + 1, y, z).setColor(r, g, bl, a);
                b.addVertex(x + 1, y + 1, z).setColor(r, g, bl, a);
                b.addVertex(x, y + 1, z).setColor(r, g, bl, a);
                b.addVertex(x, y, z).setColor(r, g, bl, a);
            }
            case 5 -> {
                b.addVertex(x, y, z + 1).setColor(r, g, bl, a);
                b.addVertex(x, y + 1, z + 1).setColor(r, g, bl, a);
                b.addVertex(x + 1, y + 1, z + 1).setColor(r, g, bl, a);
                b.addVertex(x + 1, y, z + 1).setColor(r, g, bl, a);
            }
        }
    }
}
