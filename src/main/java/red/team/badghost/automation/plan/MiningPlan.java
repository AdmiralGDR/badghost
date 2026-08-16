// Copyright (C) 2026 RedTeam. Licensed under GNU AGPLv3.
package red.team.badghost.automation.plan;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

/**
 * One fully resolved mechanism around a target block.
 *
 * <p>{@code extendDir} is where the piston points while it is being charged; {@code pushDir}
 * is where it points after the swap, i.e. straight into {@code target}.</p>
 */
public record MiningPlan(
        BlockPos target,
        BlockPos pistonPos,
        Direction extendDir,
        Direction pushDir,
        BlockPos torchPos,
        @Nullable BlockPos supportPos) {

    /** Cell the piston head occupies while charged; must stay clear. */
    public BlockPos extendPos() {
        return pistonPos.relative(extendDir);
    }

    /** True if this plan needs {@code pos}, so no other task may touch it. */
    public boolean occupies(BlockPos pos) {
        return pos.equals(pistonPos)
                || pos.equals(torchPos)
                || pos.equals(target)
                || pos.equals(extendPos())
                || pos.equals(supportPos);
    }
}
