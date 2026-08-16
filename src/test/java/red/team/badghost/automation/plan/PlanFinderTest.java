// Copyright (C) 2026 RedTeam. Licensed under GNU AGPLv3.
package red.team.badghost.automation.plan;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanFinderTest {

    private static final BlockPos TARGET = BlockPos.ZERO;

    /** The plan, or {@code null} when the search was rejected. */
    private static MiningPlan findPlan(WorldView view, BlockPos target, PlanMode mode) {
        return PlanFinder.find(view, target, mode) instanceof PlanResult.Ok ok ? ok.plan() : null;
    }

    /** The quality score, or {@code null} when the candidate is unusable. */
    private static Integer qualityOf(WorldView view, MiningPlan plan) {
        return PlanFinder.evaluate(view, plan) instanceof PlanResult.Ok ok ? ok.quality() : null;
    }

    /** The reason a search failed; fails the test if it actually succeeded. */
    private static PlanResult.Reason reasonFor(WorldView view, BlockPos target, PlanMode mode) {
        PlanResult result = PlanFinder.find(view, target, mode);
        assertInstanceOf(PlanResult.Rejected.class, result, "expected no plan");
        return ((PlanResult.Rejected) result).reason();
    }

    /** The reason one candidate was rejected; fails the test if it was accepted. */
    private static PlanResult.Reason reasonFor(WorldView view, MiningPlan plan) {
        PlanResult result = PlanFinder.evaluate(view, plan);
        assertInstanceOf(PlanResult.Rejected.class, result, "expected a rejection");
        return ((PlanResult.Rejected) result).reason();
    }

    @Test
    @DisplayName("bedrock floor: piston goes on top and pushes down")
    void floorPlanGoesAbove() {
        // Solid everywhere at y <= 0, open air above; player standing a few blocks away.
        FakeWorldView view = new FakeWorldView()
                .fill(-4, -4, -4, 4, 0, 4)
                .eye(0.5D, 2.6D, 3.5D);

        MiningPlan plan = findPlan(view, TARGET, PlanMode.VERTICAL_FAST);

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

        MiningPlan plan = findPlan(view, TARGET, PlanMode.VERTICAL_FAST);

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

        assertNull(findPlan(view, TARGET, PlanMode.VERTICAL_FAST));
        assertNull(findPlan(view, TARGET, PlanMode.ALL_DIRECTION));
    }

    @Test
    @DisplayName("only sideways room: VERTICAL_FAST gives up where ALL_DIRECTION succeeds")
    void sidewaysOnlyNeedsAllDirection() {
        // Target sandwiched vertically, open to the sides.
        FakeWorldView view = new FakeWorldView()
                .fill(-4, -4, -4, 4, -1, 4)
                .fill(-4, 1, -4, 4, 4, 4)
                .eye(3.5D, 0.6D, 0.5D);

        assertNull(findPlan(view, TARGET, PlanMode.VERTICAL_FAST),
                "both vertical faces are solid");

        MiningPlan plan = findPlan(view, TARGET, PlanMode.ALL_DIRECTION);
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
        assertNotNull(findPlan(open, TARGET, PlanMode.VERTICAL_FAST));

        FakeWorldView blocked = new FakeWorldView()
                .fill(-4, -4, -4, 4, 0, 4)
                .eye(0.5D, 2.6D, 3.5D)
                .occupied(TARGET.above());
        assertNull(findPlan(blocked, TARGET, PlanMode.VERTICAL_FAST),
                "the only piston position is claimed by another task");
    }

    @Test
    @DisplayName("out of reach or out of sight cells are rejected")
    void unreachableAndInvisibleAreRejected() {
        FakeWorldView unreachable = new FakeWorldView()
                .fill(-4, -4, -4, 4, 0, 4)
                .eye(0.5D, 2.6D, 3.5D)
                .unreachable(TARGET.above());
        assertNull(findPlan(unreachable, TARGET, PlanMode.VERTICAL_FAST));

        FakeWorldView invisible = new FakeWorldView()
                .fill(-4, -4, -4, 4, 0, 4)
                .eye(0.5D, 2.6D, 3.5D)
                .invisible(TARGET.above());
        assertNull(findPlan(invisible, TARGET, PlanMode.VERTICAL_FAST));
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
        assertNull(findPlan(view, TARGET, PlanMode.VERTICAL_FAST));
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
        assertEquals(0, qualityOf(floor, bare), "a clean plan scores best");
        assertNull(qualityOf(floor, supported), "no support fits into solid ground");

        // In open air it is the other way round: nothing holds the torch, so the support is the
        // only thing that makes the plan work, and it is ranked worse than a clean one.
        FakeWorldView air = new FakeWorldView().eye(0.5D, 2.6D, 3.5D);
        assertNull(qualityOf(air, bare), "the torch would pop off");
        assertEquals(1, qualityOf(air, supported), "viable, but costs a block");
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
        assertEquals(3, qualityOf(powered, plan));

        FakeWorldView occupiedByPlayer = new FakeWorldView()
                .fill(-4, -4, -4, 4, 0, 4)
                .eye(0.5D, 2.6D, 3.5D)
                .playerBox(piston.above());
        assertEquals(2, qualityOf(occupiedByPlayer, plan));
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

        assertNull(qualityOf(view, bare));
        assertEquals(1, qualityOf(view, supported));
    }

    @Test
    @DisplayName("a candidate rejected on a cheap check never costs a line-of-sight probe")
    void cheapRejectionSkipsVisibility() {
        BlockPos piston = TARGET.above();
        BlockPos torch = piston.east();
        // The torch cell is solid, so the candidate dies on a block-state lookup. Visibility is
        // a raycast per face in the real world and the search runs hundreds of candidates per
        // click, so it must never be reached for a candidate already known to be unusable.
        FakeWorldView view = new FakeWorldView()
                .fill(-4, -4, -4, 4, 0, 4)
                .eye(0.5D, 2.6D, 3.5D)
                .solid(torch);

        MiningPlan plan = new MiningPlan(TARGET, piston, Direction.UP, Direction.DOWN, torch, null);

        assertNull(qualityOf(view, plan));
        assertEquals(0, view.visibilityChecks(), "line of sight was probed for a doomed candidate");
    }

    @Test
    @DisplayName("a usable candidate still gets its line of sight checked")
    void viableCandidateIsStillVerified() {
        FakeWorldView view = new FakeWorldView()
                .fill(-4, -4, -4, 4, 0, 4)
                .eye(0.5D, 2.6D, 3.5D);

        BlockPos piston = TARGET.above();
        MiningPlan plan = new MiningPlan(TARGET, piston, Direction.UP, Direction.DOWN, piston.east(), null);

        assertEquals(0, qualityOf(view, plan));
        assertTrue(view.visibilityChecks() > 0, "a surviving candidate must be sight-checked");
    }

    @Test
    @DisplayName("each rejection names its own cause, not a blanket failure")
    void rejectionReasonsAreSpecific() {
        BlockPos piston = TARGET.above();
        BlockPos torch = piston.east();
        MiningPlan bare = new MiningPlan(TARGET, piston, Direction.UP, Direction.DOWN, torch, null);
        MiningPlan supported = new MiningPlan(TARGET, piston, Direction.UP, Direction.DOWN, torch, torch.below());

        // A fresh floor world per case, so one case's tweak cannot leak into the next.
        java.util.function.Supplier<FakeWorldView> floor =
                () -> new FakeWorldView().fill(-4, -4, -4, 4, 0, 4).eye(0.5D, 2.6D, 3.5D);

        assertEquals(PlanResult.Reason.OCCUPIED_BY_TASK,
                reasonFor(floor.get().occupied(piston), bare));
        assertEquals(PlanResult.Reason.CELL_BLOCKED,
                reasonFor(floor.get().solid(torch), bare));
        assertEquals(PlanResult.Reason.OUT_OF_REACH,
                reasonFor(floor.get().unreachable(piston), bare));
        assertEquals(PlanResult.Reason.NO_LINE_OF_SIGHT,
                reasonFor(floor.get().invisible(piston), bare));
        // Nothing solid anywhere: the torch has no floor and no wall to cling to.
        assertEquals(PlanResult.Reason.TORCH_WONT_SURVIVE,
                reasonFor(new FakeWorldView().eye(0.5D, 2.6D, 3.5D), bare));
        // On solid ground the support cell is already filled, so the support cannot go in.
        assertEquals(PlanResult.Reason.SUPPORT_WONT_FIT,
                reasonFor(floor.get(), supported));
    }

    @Test
    @DisplayName("a failed search reports the furthest reason it reached")
    void searchReportsMostInformativeReason() {
        // Encased on every side: the search never gets past the very first check.
        FakeWorldView encased = new FakeWorldView()
                .fill(-4, -4, -4, 4, 4, 4)
                .eye(0.5D, 1.6D, 0.5D);
        assertEquals(PlanResult.Reason.CELL_BLOCKED,
                reasonFor(encased, TARGET, PlanMode.VERTICAL_FAST));

        // Open floor, but the player cannot reach the only usable piston cell: that is a more
        // specific answer than "blocked", and it is what the player is told.
        FakeWorldView unreachable = new FakeWorldView()
                .fill(-4, -4, -4, 4, 0, 4)
                .eye(0.5D, 2.6D, 3.5D)
                .unreachable(TARGET.above());
        assertEquals(PlanResult.Reason.OUT_OF_REACH,
                reasonFor(unreachable, TARGET, PlanMode.VERTICAL_FAST));
    }

    @Test
    @DisplayName("reason ordering ranks later checks as more informative")
    void reasonOrdering() {
        assertTrue(PlanResult.Reason.NO_LINE_OF_SIGHT
                .isMoreInformativeThan(PlanResult.Reason.CELL_BLOCKED));
        assertTrue(PlanResult.Reason.TORCH_WONT_SURVIVE
                .isMoreInformativeThan(PlanResult.Reason.OUT_OF_REACH));
        assertTrue(!PlanResult.Reason.NO_FREE_FACE
                .isMoreInformativeThan(PlanResult.Reason.CELL_BLOCKED));
    }

    @Test
    @DisplayName("every reason has a distinct translation key")
    void reasonsHaveTranslationKeys() {
        java.util.Set<String> keys = new java.util.HashSet<>();
        for (PlanResult.Reason reason : PlanResult.Reason.values()) {
            String key = reason.translationKey();
            assertTrue(key.startsWith("badghost.reason."), key);
            assertTrue(keys.add(key), "duplicate key " + key);
        }
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
