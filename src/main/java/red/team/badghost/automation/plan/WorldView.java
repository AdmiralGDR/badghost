// Copyright (C) 2026 RedTeam. Licensed under GNU AGPLv3.
package red.team.badghost.automation.plan;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * Everything {@link PlanFinder} needs to know about the world, behind an interface so the
 * search can be exercised without a live {@code Level}.
 */
public interface WorldView {

    /** The block at {@code pos} gives way to a placement. */
    boolean isReplaceable(BlockPos pos);

    /** A piston at {@code pos} may extend towards {@code dir}. */
    boolean isPushable(BlockPos pos, Direction dir);

    /** A redstone torch placed at {@code pos} survives, standing or on a wall. */
    boolean torchCanSurvive(BlockPos pos);

    /** A piston fits at {@code pos} without clipping an entity. */
    boolean canPlacePiston(BlockPos pos);

    /** The configured support block fits at {@code pos}. */
    boolean canPlaceSupport(BlockPos pos);

    /** {@code pos} is inside the player's block interaction range. */
    boolean isReachable(BlockPos pos);

    /** At least one face of {@code pos} has line of sight from the player's eyes. */
    boolean isVisible(BlockPos pos);

    /** {@code pos} already receives a redstone signal, so a piston there would fire early. */
    boolean hasSignal(BlockPos pos);

    /** The player's bounding box overlaps {@code pos}. */
    boolean intersectsPlayer(BlockPos pos);

    /** Squared distance from the player's eyes to the centre of {@code pos}. */
    double eyeDistanceSqr(BlockPos pos);

    /** {@code pos} belongs to a mechanism another task is already building. */
    boolean isOccupied(BlockPos pos);
}
