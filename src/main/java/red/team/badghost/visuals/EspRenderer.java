// Copyright (C) 2026 RedTeam. Licensed under GNU AGPLv3.
package red.team.badghost.visuals;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import red.team.badghost.automation.AutomationEngine;
import red.team.badghost.automation.MinerTask;
import red.team.badghost.automation.preview.PreviewService;
import red.team.badghost.automation.preview.PreviewShape;
import red.team.badghost.config.BadghostConfig;
import red.team.badghost.core.ClientContext;

import java.util.List;

/** Outlines the blocks queued for mining, and the mechanism the miner would build. */
public final class EspRenderer {
    private EspRenderer() {}

    private static final float FALLBACK_R = 1.0F;
    private static final float FALLBACK_G = 0.66F;
    private static final float FALLBACK_B = 0.0F;

    private static String cachedColorString;
    private static float cachedR = FALLBACK_R;
    private static float cachedG = FALLBACK_G;
    private static float cachedB = FALLBACK_B;

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        if (ClientContext.isInvalid()) {
            return;
        }

        boolean espOn = BadghostConfig.ESP_ENABLED.get();
        List<MinerTask> tasks = AutomationEngine.getActiveTasks();
        List<PreviewShape> preview = PreviewService.shapes();
        if ((!espOn || tasks.isEmpty()) && preview.isEmpty()) {
            return;
        }

        refreshColor();
        float alpha = BadghostConfig.ESP_ALPHA.get().floatValue();

        Minecraft mc = ClientContext.getClient();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        Vec3 cam = event.getCamera().getPosition();

        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);

        VertexConsumer consumer = buffers.getBuffer(RenderType.lines());
        if (espOn) {
            for (int i = 0; i < tasks.size(); i++) {
                BlockPos pos = tasks.get(i).getTarget();
                box(pose, consumer, pos, 0.0D, cachedR, cachedG, cachedB, alpha);
            }
        }
        for (int i = 0; i < preview.size(); i++) {
            PreviewShape shape = preview.get(i);
            int rgb = colorFor(shape.role());
            // Inset the mechanism boxes slightly so they read as intent rather than as real
            // blocks, and never coincide exactly with a queued target's outline.
            float r = ((rgb >> 16) & 0xFF) / 255.0F;
            float g = ((rgb >> 8) & 0xFF) / 255.0F;
            float b = (rgb & 0xFF) / 255.0F;
            box(pose, consumer, shape.pos(), shape.role() == PreviewShape.Role.TARGET ? 0.0D : 0.06D,
                    r, g, b, alpha);
            if (shape.facing() != null) {
                // Which way the piston ends up pointing is the one thing a box cannot say.
                arrow(pose, consumer, shape.pos(), shape.facing(), r, g, b, alpha);
            }
        }

        pose.popPose();
        buffers.endBatch(RenderType.lines());
    }

    /** Coordinate overload, so no AABB is allocated per box per frame. */
    private static void box(PoseStack pose, VertexConsumer consumer, BlockPos pos, double inset,
                            float r, float g, float b, float a) {
        LevelRenderer.renderLineBox(pose, consumer,
                pos.getX() + inset, pos.getY() + inset, pos.getZ() + inset,
                pos.getX() + 1.0D - inset, pos.getY() + 1.0D - inset, pos.getZ() + 1.0D - inset,
                r, g, b, a);
    }

    /** A spike from the centre of {@code pos} towards {@code dir}, showing which way it points. */
    private static void arrow(PoseStack pose, VertexConsumer consumer, BlockPos pos, Direction dir,
                              float r, float g, float b, float a) {
        double cx = pos.getX() + 0.5D;
        double cy = pos.getY() + 0.5D;
        double cz = pos.getZ() + 0.5D;
        // Stops just short of the face, so it reads as an indicator rather than a wall.
        double reach = 0.45D;
        Matrix4f matrix = pose.last().pose();
        consumer.addVertex(matrix, (float) cx, (float) cy, (float) cz)
                .setColor(r, g, b, a)
                .setNormal(pose.last(), dir.getStepX(), dir.getStepY(), dir.getStepZ());
        consumer.addVertex(matrix,
                        (float) (cx + dir.getStepX() * reach),
                        (float) (cy + dir.getStepY() * reach),
                        (float) (cz + dir.getStepZ() * reach))
                .setColor(r, g, b, a)
                .setNormal(pose.last(), dir.getStepX(), dir.getStepY(), dir.getStepZ());
    }

    /** Colour per preview role: green is good, amber costs something, red is the blocker. */
    static int colorFor(PreviewShape.Role role) {
        return switch (role) {
            case TARGET -> 0x66CCFF;
            case PISTON -> 0x55FF55;
            case TORCH -> 0xFF5555;
            case SUPPORT -> 0xFFAA00;
            case HEAD -> 0x44AAFF;
            case PROBLEM -> 0xFF2222;
        };
    }

    /** Re-parses the configured hex colour only when the setting actually changed. */
    private static void refreshColor() {
        String colorStr = BadghostConfig.ESP_COLOR.get();
        if (colorStr.equals(cachedColorString)) {
            return;
        }
        cachedColorString = colorStr;

        int rgb = parseHex(colorStr);
        if (rgb < 0) {
            cachedR = FALLBACK_R;
            cachedG = FALLBACK_G;
            cachedB = FALLBACK_B;
            return;
        }
        cachedR = ((rgb >> 16) & 0xFF) / 255.0F;
        cachedG = ((rgb >> 8) & 0xFF) / 255.0F;
        cachedB = (rgb & 0xFF) / 255.0F;
    }

    /** Returns {@code -1} for anything that is not a usable RRGGBB value. */
    static int parseHex(String value) {
        if (value == null) {
            return -1;
        }
        String cleaned = value.trim();
        if (cleaned.startsWith("#")) {
            cleaned = cleaned.substring(1);
        }
        if (cleaned.length() != 6) {
            return -1;
        }
        try {
            return Integer.parseInt(cleaned, 16);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
