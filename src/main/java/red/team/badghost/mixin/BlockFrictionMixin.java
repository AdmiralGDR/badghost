// Copyright (C) 2026 RedTeam. Licensed under GNU AGPLv3.
package red.team.badghost.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.extensions.IBlockExtension;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import red.team.badghost.visuals.GhostPhysics;

/**
 * Makes ghost blocks as slippery as ice underfoot.
 *
 * <p>Hooks the position-aware friction NeoForge adds, which is what {@code LivingEntity#travel}
 * consults, so only the faked cells are affected and every real block keeps its own friction.</p>
 *
 * <p>Client-side illusion: the server moves the player by the block that is really there, so
 * this changes how movement feels locally, not what the server accepts.</p>
 */
@Mixin(IBlockExtension.class)
public interface BlockFrictionMixin {

    @Inject(method = "getFriction", at = @At("HEAD"), cancellable = true, remap = false)
    private void badghost$ghostFriction(BlockState state, LevelReader level, BlockPos pos,
                                        @Nullable Entity entity, CallbackInfoReturnable<Float> cir) {
        // This runs for every block an entity stands on, every tick, so the config flag is
        // checked before the registry lookup.
        if (pos == null || !GhostPhysics.isSlippery(pos)) {
            return;
        }
        GhostPhysics.countFriction();
        cir.setReturnValue(GhostPhysics.slipperyFriction());
    }
}
