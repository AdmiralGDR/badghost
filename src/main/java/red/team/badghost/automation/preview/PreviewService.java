// Copyright (C) 2026 RedTeam. Licensed under GNU AGPLv3.
package red.team.badghost.automation.preview;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.Nullable;
import red.team.badghost.automation.AutomationEngine;
import red.team.badghost.automation.plan.LevelWorldView;
import red.team.badghost.automation.plan.PlanFinder;
import red.team.badghost.automation.plan.PlanResult;
import red.team.badghost.config.BadghostConfig;
import red.team.badghost.core.ClientContext;
import red.team.badghost.core.ModState;

import java.util.Collections;
import java.util.List;

/**
 * Works out what the miner <em>would</em> do to the block the player is looking at, without
 * touching anything.
 *
 * <p>The plan search casts rays, so it must not run per frame: the answer is recomputed on a tick
 * interval and only when the aimed-at block actually changes.</p>
 */
public final class PreviewService {
    private PreviewService() {}

    /** Ticks between recomputes while the player keeps looking at the same block. */
    private static final int REFRESH_TICKS = 10;

    private static List<PreviewShape> shapes = Collections.emptyList();
    @Nullable
    private static BlockPos previewedTarget;
    @Nullable
    private static Component message;
    private static long computedAt = Long.MIN_VALUE;

    /** Boxes for the current aim, empty when there is nothing to show. */
    public static List<PreviewShape> shapes() {
        return shapes;
    }

    /** One-line explanation of the current preview, or {@code null}. */
    @Nullable
    public static Component message() {
        return message;
    }

    public static void clear() {
        shapes = Collections.emptyList();
        previewedTarget = null;
        message = null;
        computedAt = Long.MIN_VALUE;
    }

    /** Called once per client tick. */
    public static void tick() {
        if (!BadghostConfig.PREVIEW_ENABLED.get() || !ModState.isAutomationEnabled()
                || ClientContext.isInvalid()) {
            if (!shapes.isEmpty()) {
                clear();
            }
            return;
        }

        BlockPos target = aimedTarget();
        if (target == null) {
            if (!shapes.isEmpty()) {
                clear();
            }
            return;
        }

        ClientLevel level = ClientContext.getLevel();
        long now = level == null ? 0L : level.getGameTime();
        boolean sameTarget = target.equals(previewedTarget);
        if (sameTarget && now - computedAt < REFRESH_TICKS && now >= computedAt) {
            return;
        }

        recompute(target, now);
    }

    private static void recompute(BlockPos target, long now) {
        ClientLevel level = ClientContext.getLevel();
        LocalPlayer player = ClientContext.getPlayer();
        if (level == null || player == null) {
            clear();
            return;
        }

        LevelWorldView view = new LevelWorldView(level, player,
                AutomationEngine.resolveSupportBlock(), AutomationEngine::isOccupiedByAny);
        PlanResult result = PlanFinder.find(view, target, BadghostConfig.PLAN_MODE.get());

        shapes = PlanPreview.shapesFor(result, target);
        previewedTarget = target;
        computedAt = now;
        message = result instanceof PlanResult.Rejected rejected
                ? Component.translatable(rejected.reason().translationKey())
                : null;
    }

    /** The block under the crosshair, if the miner would act on it. */
    @Nullable
    private static BlockPos aimedTarget() {
        Minecraft mc = ClientContext.getClient();
        if (mc.hitResult == null || mc.hitResult.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        BlockPos pos = ((BlockHitResult) mc.hitResult).getBlockPos();
        ClientLevel level = ClientContext.getLevel();
        if (level == null || !level.getBlockState(pos).is(Blocks.BEDROCK)) {
            return null;
        }
        return pos.immutable();
    }
}
