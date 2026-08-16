// Copyright (C) 2026 RedTeam. Licensed under GNU AGPLv3.
package red.team.badghost.automation.plan;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedstoneWallTorchBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.util.function.Predicate;

/** {@link WorldView} backed by the live client level. */
public final class LevelWorldView implements WorldView {

    /** How far into a face the line-of-sight probe aims, to stay off the exact boundary. */
    private static final double FACE_PROBE = 0.49D;

    /** Extra slack added to the vanilla interaction range, matching the server's own tolerance. */
    private static final double REACH_SLACK = 1.0D;

    private final Level level;
    private final Player player;
    private final Block supportBlock;
    private final Predicate<BlockPos> occupied;
    private final Vec3 eyePos;

    public LevelWorldView(Level level, Player player, Block supportBlock, Predicate<BlockPos> occupied) {
        this.level = level;
        this.player = player;
        this.supportBlock = supportBlock;
        this.occupied = occupied;
        this.eyePos = player.getEyePosition();
    }

    @Override
    public boolean isReplaceable(BlockPos pos) {
        return level.getBlockState(pos).canBeReplaced();
    }

    @Override
    public boolean isPushable(BlockPos pos, Direction dir) {
        return PistonBaseBlock.isPushable(Blocks.STONE.defaultBlockState(), level, pos, dir, true, dir);
    }

    @Override
    public boolean torchCanSurvive(BlockPos pos) {
        if (Blocks.REDSTONE_TORCH.defaultBlockState().canSurvive(level, pos)) {
            return true;
        }
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            BlockState wall = Blocks.REDSTONE_WALL_TORCH.defaultBlockState()
                    .setValue(RedstoneWallTorchBlock.FACING, facing);
            if (wall.canSurvive(level, pos)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean canPlacePiston(BlockPos pos) {
        return canPlace(Blocks.PISTON.defaultBlockState(), pos);
    }

    @Override
    public boolean canPlaceSupport(BlockPos pos) {
        return canPlace(supportBlock.defaultBlockState(), pos);
    }

    /** Equivalent of the protected {@code BlockItem#canPlace}, built from public API only. */
    private boolean canPlace(BlockState state, BlockPos pos) {
        return state.canSurvive(level, pos)
                && level.isUnobstructed(state, pos, CollisionContext.of(player));
    }

    @Override
    public boolean isReachable(BlockPos pos) {
        return player.canInteractWithBlock(pos, REACH_SLACK);
    }

    @Override
    public boolean isVisible(BlockPos pos) {
        for (Direction face : Direction.values()) {
            Vec3 faceCentre = Vec3.atCenterOf(pos)
                    .add(face.getStepX() * FACE_PROBE, face.getStepY() * FACE_PROBE, face.getStepZ() * FACE_PROBE);
            HitResult hit = level.clip(new ClipContext(
                    eyePos, faceCentre, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            if (hit.getType() == HitResult.Type.MISS) {
                return true;
            }
            if (hit instanceof net.minecraft.world.phys.BlockHitResult blockHit && blockHit.getBlockPos().equals(pos)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean hasSignal(BlockPos pos) {
        return level.hasNeighborSignal(pos);
    }

    @Override
    public boolean intersectsPlayer(BlockPos pos) {
        return player.getBoundingBox().intersects(new AABB(pos));
    }

    @Override
    public double eyeDistanceSqr(BlockPos pos) {
        return eyePos.distanceToSqr(Vec3.atCenterOf(pos));
    }

    @Override
    public boolean isOccupied(BlockPos pos) {
        return occupied.test(pos);
    }
}
