// Copyright (C) 2026 RedTeam. Licensed under GNU AGPLv3.
package red.team.badghost.automation.plan;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;

/** Hand-built world for the planner tests: a set of solid cells and a few toggles. */
final class FakeWorldView implements WorldView {

    private final Set<BlockPos> solid = new HashSet<>();
    private final Set<BlockPos> occupied = new HashSet<>();
    private final Set<BlockPos> unreachable = new HashSet<>();
    private final Set<BlockPos> invisible = new HashSet<>();
    private final Set<BlockPos> powered = new HashSet<>();
    private final Set<BlockPos> playerBox = new HashSet<>();
    private final Set<Direction> blockedPushDirs = new HashSet<>();

    private Vec3 eye = new Vec3(0.5D, 1.6D, 0.5D);
    private int visibilityChecks;

    FakeWorldView eye(double x, double y, double z) {
        this.eye = new Vec3(x, y, z);
        return this;
    }

    /** Fills every cell in the inclusive box with solid blocks. */
    FakeWorldView fill(int x0, int y0, int z0, int x1, int y1, int z1) {
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    solid.add(new BlockPos(x, y, z));
                }
            }
        }
        return this;
    }

    FakeWorldView solid(BlockPos... positions) {
        for (BlockPos pos : positions) {
            solid.add(pos);
        }
        return this;
    }

    FakeWorldView occupied(BlockPos... positions) {
        for (BlockPos pos : positions) {
            occupied.add(pos);
        }
        return this;
    }

    FakeWorldView unreachable(BlockPos... positions) {
        for (BlockPos pos : positions) {
            unreachable.add(pos);
        }
        return this;
    }

    FakeWorldView invisible(BlockPos... positions) {
        for (BlockPos pos : positions) {
            invisible.add(pos);
        }
        return this;
    }

    FakeWorldView powered(BlockPos... positions) {
        for (BlockPos pos : positions) {
            powered.add(pos);
        }
        return this;
    }

    FakeWorldView playerBox(BlockPos... positions) {
        for (BlockPos pos : positions) {
            playerBox.add(pos);
        }
        return this;
    }

    FakeWorldView blockPush(Direction dir) {
        blockedPushDirs.add(dir);
        return this;
    }

    @Override
    public boolean isReplaceable(BlockPos pos) {
        return !solid.contains(pos);
    }

    @Override
    public boolean isPushable(BlockPos pos, Direction dir) {
        return !blockedPushDirs.contains(dir);
    }

    /** A torch stands on a solid floor or hangs off a solid side. */
    @Override
    public boolean torchCanSurvive(BlockPos pos) {
        if (solid.contains(pos.below())) {
            return true;
        }
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            if (solid.contains(pos.relative(dir))) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean canPlacePiston(BlockPos pos) {
        return !solid.contains(pos);
    }

    @Override
    public boolean canPlaceSupport(BlockPos pos) {
        return !solid.contains(pos);
    }

    @Override
    public boolean isReachable(BlockPos pos) {
        return !unreachable.contains(pos);
    }

    @Override
    public boolean isVisible(BlockPos pos) {
        visibilityChecks++;
        return !invisible.contains(pos);
    }

    /** How many line-of-sight probes were asked for; a raycast each in the real world. */
    int visibilityChecks() {
        return visibilityChecks;
    }

    @Override
    public boolean hasSignal(BlockPos pos) {
        return powered.contains(pos);
    }

    @Override
    public boolean intersectsPlayer(BlockPos pos) {
        return playerBox.contains(pos);
    }

    @Override
    public double eyeDistanceSqr(BlockPos pos) {
        return eye.distanceToSqr(Vec3.atCenterOf(pos));
    }

    @Override
    public boolean isOccupied(BlockPos pos) {
        return occupied.contains(pos);
    }
}
