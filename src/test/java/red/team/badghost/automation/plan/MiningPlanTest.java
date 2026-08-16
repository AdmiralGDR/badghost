// Copyright (C) 2026 RedTeam. Licensed under GNU AGPLv3.
package red.team.badghost.automation.plan;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MiningPlanTest {

    private static final BlockPos TARGET = BlockPos.ZERO;
    private static final BlockPos PISTON = TARGET.above();

    @Test
    @DisplayName("the head cell follows the extend direction")
    void extendPosFollowsExtendDir() {
        MiningPlan plan = new MiningPlan(TARGET, PISTON, Direction.UP, Direction.DOWN, PISTON.east(), null);
        assertEquals(TARGET.above(2), plan.extendPos());
    }

    @Test
    @DisplayName("every cell the mechanism needs is reported as occupied")
    void occupiesEveryUsedCell() {
        BlockPos support = PISTON.east().below();
        MiningPlan plan = new MiningPlan(TARGET, PISTON, Direction.UP, Direction.DOWN, PISTON.east(), support);

        assertTrue(plan.occupies(TARGET));
        assertTrue(plan.occupies(PISTON));
        assertTrue(plan.occupies(PISTON.east()));
        assertTrue(plan.occupies(plan.extendPos()));
        assertTrue(plan.occupies(support));

        assertFalse(plan.occupies(TARGET.west()));
        assertFalse(plan.occupies(TARGET.below()));
    }

    @Test
    @DisplayName("a plan without a support does not claim the cell under the torch")
    void noSupportMeansNoClaim() {
        MiningPlan plan = new MiningPlan(TARGET, PISTON, Direction.UP, Direction.DOWN, PISTON.east(), null);
        assertFalse(plan.occupies(PISTON.east().below()));
    }
}
