// Copyright (C) 2026 RedTeam. Licensed under GNU AGPLv3.
package red.team.badghost.visuals;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import red.team.badghost.automation.AutomationEngine;
import red.team.badghost.automation.MinerTask;
import red.team.badghost.config.BadghostConfig;
import red.team.badghost.core.ClientContext;

import java.util.List;

/** Outlines the blocks queued for mining. */
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
        if (!BadghostConfig.ESP_ENABLED.get() || ClientContext.isInvalid()) {
            return;
        }

        List<MinerTask> tasks = AutomationEngine.getActiveTasks();
        if (tasks.isEmpty()) {
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
        for (int i = 0; i < tasks.size(); i++) {
            BlockPos pos = tasks.get(i).getTarget();
            // Coordinate overload, so no AABB is allocated per block per frame.
            LevelRenderer.renderLineBox(pose, consumer,
                    pos.getX(), pos.getY(), pos.getZ(),
                    pos.getX() + 1.0D, pos.getY() + 1.0D, pos.getZ() + 1.0D,
                    cachedR, cachedG, cachedB, alpha);
        }

        pose.popPose();
        buffers.endBatch(RenderType.lines());
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
