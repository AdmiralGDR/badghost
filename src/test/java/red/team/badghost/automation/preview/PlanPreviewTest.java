// Copyright (C) 2026 RedTeam. Licensed under GNU AGPLv3.
package red.team.badghost.automation.preview;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import red.team.badghost.automation.plan.MiningPlan;
import red.team.badghost.automation.plan.PlanResult;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanPreviewTest {

    private static final BlockPos TARGET = BlockPos.ZERO;
    private static final BlockPos PISTON = TARGET.above();
    private static final BlockPos TORCH = PISTON.east();

    private static Set<PreviewShape.Role> rolesOf(List<PreviewShape> shapes) {
        Set<PreviewShape.Role> roles = EnumSet.noneOf(PreviewShape.Role.class);
        shapes.forEach(shape -> roles.add(shape.role()));
        return roles;
    }

    private static PreviewShape shapeOf(List<PreviewShape> shapes, PreviewShape.Role role) {
        return shapes.stream().filter(s -> s.role() == role).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("a plain plan previews target, piston, torch and head")
    void barePlanShapes() {
        MiningPlan plan = new MiningPlan(TARGET, PISTON, Direction.UP, Direction.DOWN, TORCH, null);
        List<PreviewShape> shapes = PlanPreview.shapesFor(new PlanResult.Ok(plan, 0), TARGET);

        assertEquals(EnumSet.of(PreviewShape.Role.TARGET, PreviewShape.Role.PISTON,
                PreviewShape.Role.TORCH, PreviewShape.Role.HEAD), rolesOf(shapes));
        assertEquals(TARGET, shapeOf(shapes, PreviewShape.Role.TARGET).pos());
        assertEquals(PISTON, shapeOf(shapes, PreviewShape.Role.PISTON).pos());
        assertEquals(TORCH, shapeOf(shapes, PreviewShape.Role.TORCH).pos());
        assertEquals(plan.extendPos(), shapeOf(shapes, PreviewShape.Role.HEAD).pos());
    }

    @Test
    @DisplayName("the piston box carries the direction it will end up facing")
    void pistonCarriesFacing() {
        MiningPlan plan = new MiningPlan(TARGET, PISTON, Direction.UP, Direction.DOWN, TORCH, null);
        List<PreviewShape> shapes = PlanPreview.shapesFor(new PlanResult.Ok(plan, 0), TARGET);

        // The push direction is the one a player cannot infer from a box alone.
        assertSame(Direction.DOWN, shapeOf(shapes, PreviewShape.Role.PISTON).facing());
        assertSame(Direction.UP, shapeOf(shapes, PreviewShape.Role.HEAD).facing());
    }

    @Test
    @DisplayName("a supported plan also previews the block that will be spent")
    void supportedPlanShowsSupport() {
        MiningPlan plan = new MiningPlan(TARGET, PISTON, Direction.UP, Direction.DOWN, TORCH, TORCH.below());
        List<PreviewShape> shapes = PlanPreview.shapesFor(new PlanResult.Ok(plan, 1), TARGET);

        assertTrue(rolesOf(shapes).contains(PreviewShape.Role.SUPPORT));
        assertEquals(TORCH.below(), shapeOf(shapes, PreviewShape.Role.SUPPORT).pos());
    }

    @Test
    @DisplayName("a rejection points at the cell that is in the way")
    void rejectionPointsAtTheBlocker() {
        List<PreviewShape> shapes = PlanPreview.shapesFor(
                new PlanResult.Rejected(PlanResult.Reason.CELL_BLOCKED, PISTON), TARGET);

        assertEquals(EnumSet.of(PreviewShape.Role.TARGET, PreviewShape.Role.PROBLEM), rolesOf(shapes));
        assertEquals(PISTON, shapeOf(shapes, PreviewShape.Role.PROBLEM).pos());
        assertFalse(rolesOf(shapes).contains(PreviewShape.Role.PISTON),
                "a rejected plan must not draw a mechanism that will never be built");
    }

    @Test
    @DisplayName("a rejection on the target itself does not draw a duplicate box")
    void rejectionOnTargetIsNotDuplicated() {
        List<PreviewShape> shapes = PlanPreview.shapesFor(
                new PlanResult.Rejected(PlanResult.Reason.NO_FREE_FACE, TARGET), TARGET);

        assertEquals(1, shapes.size());
        assertSame(PreviewShape.Role.TARGET, shapes.get(0).role());
    }

    @Test
    @DisplayName("the target is always the first box, whatever the outcome")
    void targetComesFirst() {
        MiningPlan plan = new MiningPlan(TARGET, PISTON, Direction.UP, Direction.DOWN, TORCH, null);
        assertSame(PreviewShape.Role.TARGET,
                PlanPreview.shapesFor(new PlanResult.Ok(plan, 0), TARGET).get(0).role());
        assertSame(PreviewShape.Role.TARGET,
                PlanPreview.shapesFor(new PlanResult.Rejected(PlanResult.Reason.OUT_OF_REACH, PISTON), TARGET)
                        .get(0).role());
    }
}
