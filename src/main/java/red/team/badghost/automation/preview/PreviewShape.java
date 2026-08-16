// Copyright (C) 2026 RedTeam. Licensed under GNU AGPLv3.
package red.team.badghost.automation.preview;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

/**
 * One box the preview wants drawn, and what it means.
 *
 * <p>Deliberately holds no colour or render state: the preview is computed as plain geometry so
 * it can be asserted on in tests without a client, and the renderer is the only thing that knows
 * how a role should look.</p>
 */
public record PreviewShape(BlockPos pos, Role role, @Nullable Direction facing) {

    public PreviewShape(BlockPos pos, Role role) {
        this(pos, role, null);
    }

    /** What a box stands for. The renderer maps this to a colour. */
    public enum Role {
        /** The block that would be broken. */
        TARGET,
        /** Where the piston is planted. Carries the direction it will face. */
        PISTON,
        /** Where the redstone torch goes. */
        TORCH,
        /** Where a support block has to be spent, if any. */
        SUPPORT,
        /** The cell the piston head sweeps into; must stay clear. */
        HEAD,
        /** The cell that made the plan impossible. */
        PROBLEM
    }
}
