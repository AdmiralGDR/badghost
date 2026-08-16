// Copyright (C) 2026 RedTeam. Licensed under GNU AGPLv3.
package red.team.badghost.automation.plan;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanFinderTest {

    private static final BlockPos TARGET = BlockPos.ZERO;

    @Test
    @DisplayName("bedrock floor: piston goes on top and pushes down")
    void floorPlanGoesAbove() {
        // Solid everywhere at y <= 0, open air above; player standing a few blocks away.
        FakeWorldView view = new FakeWorldView()
                .fill(-4, -4, -4, 4, 0, 4)
                .eye(0.5D, 2.6D, 3.5D);

        MiningPlan plan = PlanFinder.find(view, TARGET, PlanMode.VERTICAL_FAST);

        assertNotNull(plan, "a floor target must be plannable");
        assertEquals(TARGET.above(), plan.pistonPos());
        assertEquals(Direction.UP, plan.extendDir());
        assertEquals(Direction.DOWN, plan.pushDir(), "the piston must end up pointing at the target");
        assertEquals(TARGET.above(2), plan.extendPos());
        assertNull(plan.supportPos(), "the torch stands on the floor, no support needed");
    }

    @Test
    @DisplayName("nether roof: piston goes underneath and a support carries the torch")
    void ceilingPlanGoesBelowWithSupport() {
        // Solid everywhere at y >= 0, open air below.
        FakeWorldView view = new FakeWorldView()
                .fill(-4, 0, -4, 4, 4, 4)
                .eye(0.5D, -2.4D, 3.5D);

        MiningPlan plan = PlanFinder.find(view, TARGET, PlanMode.VERTICAL_FAST);

        assertNotNull(plan, "a ceiling target must be plannable");
        assertEquals(TARGET.below(), plan.pistonPos());
        assertEquals(Direction.DOWN, plan.extendDir());
        assertEquals(Direction.UP, plan.pushDir());
        assertNotNull(plan.supportPos(), "nothing holds the torch in mid-air, so a support is required");
        assertEquals(plan.torchPos().below(), plan.supportPos());
    }

    @Test
    @DisplayName("fully encased target has no plan in any mode")
    void encasedTargetHasNoPlan() {
        FakeWorldView view = new FakeWorldView()
                .fill(-4, -4, -4, 4, 4, 4)
                .eye(0.5D, 1.6D, 0.5D);

        assertNull(PlanFinder.find(view, TARGET, PlanMode.VERTICAL_FAST));
        assertNull(PlanFinder.find(view, TARGET, PlanMode.ALL_DIRECTION));
    }

    @Test
    @DisplayName("only sideways room: VERTICAL_FAST gives up where ALL_DIRECTION succeeds")
    void sidewaysOnlyNeedsAllDirection() {
        // Target sandwiched vertically, open to the sides.
        FakeWorldView view = new FakeWorldView()
                .fill(-4, -4, -4, 4, -1, 4)
                .fill(-4, 1, -4, 4, 4, 4)
                .eye(3.5D, 0.6D, 0.5D);

        assertNull(PlanFinder.find(view, TARGET, PlanMode.VERTICAL_FAST),
                "both vertical faces are solid");

        MiningPlan plan = PlanFinder.find(view, TARGET, PlanMode.ALL_DIRECTION);
        assertNotNull(plan);
        assertTrue(plan.pushDir().getAxis().isHorizontal(), "a side face must have been chosen");
        assertEquals(TARGET, plan.pistonPos().relative(plan.pushDir()));
    }

    @Test
    @DisplayName("a cell another task is using is never planned over")
    void occupiedCellsAreRejected() {
        FakeWorldView open = new FakeWorldView()
                .fill(-4, -4, -4, 4, 0, 4)
                .eye(0.5D, 2.6D, 3.5D);
        assertNotNull(PlanFinder.find(open, TARGET, PlanMode.VERTICAL_FAST));

        FakeWorldView blocked = new FakeWorldView()
                .fill(-4, -4, -4, 4, 0, 4)
                .eye(0.5D, 2.6D, 3.5D)
                .occupied(TARGET.above());
        assertNull(PlanFinder.find(blocked, TARGET, PlanMode.VERTICAL_FAST),
                "the only piston position is claimed by another task");
    }

    @Test
    @DisplayName("out of reach or out of sight cells are rejected")
    void unreachableAndInvisibleAreRejected() {
        FakeWorldView unreachable = new FakeWorldView()
                .fill(-4, -4, -4, 4, 0, 4)
                .eye(0.5D, 2.6D, 3.5D)
                .unreachable(TARGET.above());
        assertNull(PlanFinder.find(unreachable, TARGET, PlanMode.VERTICAL_FAST));

        FakeWorldView invisible = new FakeWorldView()
                .fill(-4, -4, -4, 4, 0, 4)
                .eye(0.5D, 2.6D, 3.5D)
                .invisible(TARGET.above());
        assertNull(PlanFinder.find(invisible, TARGET, PlanMode.VERTICAL_FAST));
    }

    @Test
    @DisplayName("a direction the piston cannot extend into is skipped")
    void unpushableExtendDirectionIsSkipped() {
        FakeWorldView view = new FakeWorldView()
                .fill(-4, -4, -4, 4, 0, 4)
                .eye(0.5D, 2.6D, 3.5D)
                .blockPush(Direction.UP);

        // The only free piston cell is above the target and its only free extend direction is
        // up, so blocking that leaves nothing.
        assertNull(PlanFinder.find(view, TARGET, PlanMode.VERTICAL_FAST));
    }

    @Test
    @DisplayName("bare and supported variants are mutually exclusive at one torch position")
    void bareAndSupportedAreMutuallyExclusive() {
        BlockPos piston = TARGET.above();
        BlockPos torch = piston.east();
        MiningPlan bare = new MiningPlan(TARGET, piston, Direction.UP, Direction.DOWN, torch, null);
        MiningPlan supported = new MiningPlan(TARGET, piston, Direction.UP, Direction.DOWN, torch, torch.below());

        // On a floor the torch stands by itself, and the cell a support would occupy is already
        // solid ground, so only the bare variant is viable.
        FakeWorldView floor = new FakeWorldView()
                .fill(-4, -4, -4, 4, 0, 4)
                .eye(0.5D, 2.6D, 3.5D);
        assertEquals(0, PlanFinder.quality(floor, bare), "a clean plan scores best");
        assertNull(PlanFinder.quality(floor, supported), "no support fits into solid ground");

        // In open air it is the other way round: nothing holds the torch, so the support is the
        // only thing that makes the plan work, and it is ranked worse than a clean one.
        FakeWorldView air = new FakeWorldView().eye(0.5D, 2.6D, 3.5D);
        assertNull(PlanFinder.quality(air, bare), "the torch would pop off");
        assertEquals(1, PlanFinder.quality(air, supported), "viable, but costs a block");
    }

    @Test
    @DisplayName("quality penalises an already powered piston cell and the player standing in the way")
    void qualityPenalisesSignalAndPlayer() {
        BlockPos piston = TARGET.above();
        MiningPlan plan = new MiningPlan(TARGET, piston, Direction.UP, Direction.DOWN, piston.east(), null);

        FakeWorldView powered = new FakeWorldView()
                .fill(-4, -4, -4, 4, 0, 4)
                .eye(0.5D, 2.6D, 3.5D)
                .powered(piston);
        assertEquals(3, PlanFinder.quality(powered, plan));

        FakeWorldView occupiedByPlayer = new FakeWorldView()
                .fill(-4, -4, -4, 4, 0, 4)
                .eye(0.5D, 2.6D, 3.5D)
                .playerBox(piston.above());
        assertEquals(2, PlanFinder.quality(occupiedByPlayer, plan));
    }

    @Test
    @DisplayName("a torch with nothing to stand on is only allowed with a support")
    void torchNeedsSomethingToStandOn() {
        // Nothing solid anywhere: the torch cannot survive unaided.
        FakeWorldView view = new FakeWorldView().eye(0.5D, 2.6D, 3.5D);

        BlockPos piston = TARGET.above();
        MiningPlan bare = new MiningPlan(TARGET, piston, Direction.UP, Direction.DOWN, piston.east(), null);
        MiningPlan supported = new MiningPlan(TARGET, piston, Direction.UP, Direction.DOWN,
                piston.east(), piston.east().below());

        assertNull(PlanFinder.quality(view, bare));
        assertEquals(1, PlanFinder.quality(view, supported));
    }

    @Test
    @DisplayName("face ordering keeps the nearest face for last")
    void faceOrderingRotatesNearestToTheBack() {
        // Eye far to the east, so EAST is nearest and WEST is farthest.
        FakeWorldView view = new FakeWorldView().eye(20.5D, 0.5D, 0.5D);

        List<Direction> ordered = PlanFinder.orderFaces(view, TARGET, PlanMode.ALL_DIRECTION.faces());

        assertEquals(5, ordered.size(), "at most five faces are considered");
        assertSame(Direction.EAST, ordered.get(ordered.size() - 1),
                "the face the player is pressed against is tried last");
    }

    @Test
    @DisplayName("extend directions are tried farthest from the player first")
    void extendOrderingPrefersAwayFromPlayer() {
        FakeWorldView view = new FakeWorldView().eye(0.5D, 20.5D, 0.5D);

        List<Direction> ordered =
                PlanFinder.orderExtendDirs(view, TARGET, PlanMode.VERTICAL_FAST.extendDirs());

        assertSame(Direction.DOWN, ordered.get(0), "grow the head away from the player first");
    }
}
