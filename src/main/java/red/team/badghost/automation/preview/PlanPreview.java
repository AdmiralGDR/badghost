// Copyright (C) 2026 RedTeam. Licensed under GNU AGPLv3.
package red.team.badghost.automation.preview;

import net.minecraft.core.BlockPos;
import red.team.badghost.automation.plan.MiningPlan;
import red.team.badghost.automation.plan.PlanResult;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns a plan — or the reason there is none — into boxes to draw.
 *
 * <p>This is what makes the mod legible: before anything is placed or broken, the player sees
 * exactly where the mechanism would go, or which cell is in the way. Pure geometry, no rendering
 * and no world access, so it is asserted on directly in tests.</p>
 */
public final class PlanPreview {
    private PlanPreview() {}

    /** Boxes describing {@code result}, in a stable order: target first, problem last. */
    public static List<PreviewShape> shapesFor(PlanResult result, BlockPos target) {
        List<PreviewShape> shapes = new ArrayList<>(5);
        shapes.add(new PreviewShape(target, PreviewShape.Role.TARGET));

        if (result instanceof PlanResult.Ok ok) {
            MiningPlan plan = ok.plan();
            // The piston carries the direction it ends up facing — the renderer draws that as an
            // arrow, which is the part a player cannot infer from a plain box.
            shapes.add(new PreviewShape(plan.pistonPos(), PreviewShape.Role.PISTON, plan.pushDir()));
            shapes.add(new PreviewShape(plan.torchPos(), PreviewShape.Role.TORCH));
            shapes.add(new PreviewShape(plan.extendPos(), PreviewShape.Role.HEAD, plan.extendDir()));
            if (plan.supportPos() != null) {
                shapes.add(new PreviewShape(plan.supportPos(), PreviewShape.Role.SUPPORT));
            }
        } else if (result instanceof PlanResult.Rejected rejected
                && !rejected.where().equals(target)) {
            // Only worth pointing at a cell that is not the target itself; otherwise the target
            // box already carries the message.
            shapes.add(new PreviewShape(rejected.where(), PreviewShape.Role.PROBLEM));
        }
        return shapes;
    }
}
