// Copyright (C) 2026 RedTeam. Licensed under GNU AGPLv3.
package red.team.badghost.automation.plan;

import net.minecraft.core.BlockPos;

/**
 * Outcome of looking for a mechanism: either a plan, or the reason there is none.
 *
 * <p>The search used to answer with {@code null}, which threw away everything it had learned —
 * the player was told "no room" whether the torch had nothing to stand on, the spot was out of
 * reach, or another task already owned the cell. Carrying the reason back is what lets the mod
 * explain itself.</p>
 */
public sealed interface PlanResult {

    /** A usable mechanism. Lower {@code quality} is better; 0 is unconditionally good. */
    record Ok(MiningPlan plan, int quality) implements PlanResult {}

    /** No mechanism fits. {@code where} is the cell that made it impossible. */
    record Rejected(Reason reason, BlockPos where) implements PlanResult {}

    /**
     * Why a candidate was thrown out.
     *
     * <p>Declared in the order the checks run, cheapest first. A candidate that got further
     * through the checks tells the player something more specific, so when the whole search
     * fails the search reports the furthest reason it reached — see {@link #isMoreInformativeThan}.</p>
     */
    enum Reason {
        /** No face of the target is even free; nothing could be tried. */
        NO_FREE_FACE,
        /** Another queued task is already using one of the cells. */
        OCCUPIED_BY_TASK,
        /** A cell the mechanism needs is not empty. */
        CELL_BLOCKED,
        /** Outside the player's block interaction range. */
        OUT_OF_REACH,
        /** The piston itself would not fit or survive there. */
        PISTON_WONT_FIT,
        /** A standing or wall torch cannot survive at the torch cell. */
        TORCH_WONT_SURVIVE,
        /** The support block cannot be placed under the torch. */
        SUPPORT_WONT_FIT,
        /** No face of the cell can be seen from the player's eyes. */
        NO_LINE_OF_SIGHT;

        /** True when {@code this} was reached later in the checks, so it explains more. */
        public boolean isMoreInformativeThan(Reason other) {
            return ordinal() > other.ordinal();
        }

        /** Translation key for the player-facing message. */
        public String translationKey() {
            return "badghost.reason." + name().toLowerCase(java.util.Locale.ROOT);
        }
    }
}
