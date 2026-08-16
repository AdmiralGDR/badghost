// Copyright (C) 2026 RedTeam. Licensed under GNU AGPLv3.
package red.team.badghost.visuals;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import red.team.badghost.config.BadghostConfig;

/**
 * How ghost blocks behave underfoot, and a record of when that behaviour was applied.
 *
 * <p>Both properties are a local illusion: the server moves the player by whatever block is
 * really there, so this changes how movement feels on this client and nothing else.</p>
 *
 * <p>The counters exist so the behaviour can be proven to fire rather than assumed to —
 * {@code scripts/selftest.sh} stands the player on a ghost block and reads them.</p>
 */
public final class GhostPhysics {
    private GhostPhysics() {}

    private static int frictionApplied;
    private static int bounceApplied;

    /** Friction of ice, which is what a slippery ghost block reports. */
    public static float slipperyFriction() {
        return Blocks.ICE.getFriction();
    }

    /** True when {@code pos} is a ghost block the player asked to be slippery. */
    public static boolean isSlippery(BlockPos pos) {
        return BadghostConfig.FROZEN_SLIPPERY.get() && GhostBlockRegistry.contains(pos);
    }

    /** True when {@code pos} is a ghost block the player asked to bounce. */
    public static boolean isBouncy(BlockPos pos) {
        return BadghostConfig.BOUNCY.get() && GhostBlockRegistry.contains(pos);
    }

    public static void countFriction() {
        frictionApplied++;
    }

    public static void countBounce() {
        bounceApplied++;
    }

    public static int frictionAppliedCount() {
        return frictionApplied;
    }

    public static int bounceAppliedCount() {
        return bounceApplied;
    }

    public static void reset() {
        frictionApplied = 0;
        bounceApplied = 0;
    }
}
