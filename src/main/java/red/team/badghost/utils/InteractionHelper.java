// Copyright (C) 2026 RedTeam. Licensed under GNU AGPLv3.
package red.team.badghost.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import red.team.badghost.core.ClientContext;
import red.team.badghost.core.ModState;

/** Block breaking and placement through the vanilla client controller. */
public final class InteractionHelper {
    private InteractionHelper() {}

    /**
     * Breaks {@code pos} using the vanilla path, which opens the block-prediction scope, hands
     * out a valid sequence id and instantly destroys the block when the held tool is fast
     * enough. Rolling this by hand desynchronises the prediction handler and leaves ghost
     * blocks, so it must go through {@code startDestroyBlock}.
     *
     * <p>That method fires {@code PlayerInteractEvent.LeftClickBlock}, which this mod also
     * listens to, hence the re-entrancy flag.</p>
     */
    public static void breakBlock(BlockPos pos) {
        if (pos == null || ClientContext.isInvalid()) {
            return;
        }
        ClientLevel level = ClientContext.getLevel();
        MultiPlayerGameMode gameMode = ClientContext.getGameMode();
        if (level == null || gameMode == null || level.getBlockState(pos).isAir()) {
            return;
        }

        ModState.setInternalBreak(true);
        try {
            gameMode.startDestroyBlock(pos, Direction.UP);
        } finally {
            ModState.setInternalBreak(false);
        }
    }

    /**
     * Places the held item at {@code pos} without a real supporting block: the hit result is
     * synthesised against the neighbouring cell. The hit vector stays half a block from the
     * centre of {@code pos}, inside the server's tolerance of one block.
     */
    public static boolean place(BlockPos pos, Direction facing, InteractionHand hand) {
        if (pos == null || facing == null || ClientContext.isInvalid()) {
            return false;
        }
        Minecraft mc = ClientContext.getClient();
        LocalPlayer player = mc.player;
        MultiPlayerGameMode gameMode = mc.gameMode;
        ClientLevel level = mc.level;
        if (player == null || gameMode == null || level == null) {
            return false;
        }
        if (!level.getBlockState(pos).canBeReplaced()) {
            return false;
        }

        gameMode.useItemOn(player, hand, hitResultFor(pos, facing));
        return true;
    }

    /**
     * Places a directional block so that it ends up facing {@code facing}.
     *
     * <p>The server derives the facing from the player's rotation, so the rotation is pushed
     * first and the local rotation fields are set for the duration of the call: that is what
     * the client's own placement prediction reads. Setting them and restoring them inside one
     * tick never reaches a frame, so the camera does not move.</p>
     */
    public static boolean placeDirectional(BlockPos pos, Direction facing, InteractionHand hand) {
        if (pos == null || facing == null || ClientContext.isInvalid()) {
            return false;
        }
        Minecraft mc = ClientContext.getClient();
        LocalPlayer player = mc.player;
        MultiPlayerGameMode gameMode = mc.gameMode;
        ClientLevel level = mc.level;
        if (player == null || gameMode == null || level == null) {
            return false;
        }
        if (!level.getBlockState(pos).canBeReplaced()) {
            return false;
        }

        float yaw = PlayerLookUtils.yawFor(facing);
        float pitch = PlayerLookUtils.pitchFor(facing);

        float oldYRot = player.getYRot();
        float oldXRot = player.getXRot();
        float oldHeadRot = player.yHeadRot;
        try {
            player.setYRot(yaw);
            player.setXRot(pitch);
            player.yHeadRot = yaw;
            PlayerLookUtils.sendRotation(yaw, pitch);
            gameMode.useItemOn(player, hand, hitResultFor(pos, facing));
        } finally {
            player.setYRot(oldYRot);
            player.setXRot(oldXRot);
            player.yHeadRot = oldHeadRot;
        }
        return true;
    }

    private static BlockHitResult hitResultFor(BlockPos pos, Direction facing) {
        BlockPos hitPos = pos.relative(facing.getOpposite());
        Vec3 hitVec = Vec3.atCenterOf(hitPos).relative(facing, 0.5D);
        return new BlockHitResult(hitVec, facing, pos, false);
    }

    public static boolean isReplaceable(BlockPos pos) {
        if (pos == null) {
            return false;
        }
        ClientLevel level = ClientContext.getLevel();
        return level != null && level.getBlockState(pos).canBeReplaced();
    }
}
