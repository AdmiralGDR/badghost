// Copyright (C) 2026 RedTeam. Licensed under GNU AGPLv3.
package red.team.badghost.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import red.team.badghost.core.ClientContext;
import red.team.badghost.visuals.GhostPhysics;

/**
 * Makes ghost blocks bounce like slime when landed on.
 *
 * <p>The vanilla hook that does this carries no position, so it cannot tell a faked cell from a
 * real one. The call site does know: it is reached only when a fall actually ended, and the block
 * involved is the one under the entity's feet. Redirecting there keeps the check off the movement
 * path proper — it runs once per landing, not per tick.</p>
 *
 * <p>Client-side illusion; the server keeps its own idea of where the player is.</p>
 */
@Mixin(Entity.class)
public abstract class EntityBounceMixin {

    // getOnPosLegacy is deprecated, but it is the very method vanilla uses two lines above this
    // call site to choose the block whose fall handler runs. Asking a newer method for the
    // position would risk bouncing off a different cell than the one being handled.
    @SuppressWarnings("deprecation")
    @Redirect(
            method = "move",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/Block;updateEntityAfterFallOn(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/world/entity/Entity;)V"))
    private void badghost$bounceOnGhost(Block block, BlockGetter level, Entity entity) {
        if (ClientContext.isLocalPlayer(entity) && GhostPhysics.isBouncy(entity.getOnPosLegacy())) {
            GhostPhysics.countBounce();
            bounce(entity);
            return;
        }
        block.updateEntityAfterFallOn(level, entity);
    }

    /** The same arithmetic a slime block uses, so it feels like the real thing. */
    private static void bounce(Entity entity) {
        Vec3 motion = entity.getDeltaMovement();
        if (motion.y < 0.0D) {
            double retained = entity instanceof LivingEntity ? 1.0D : 0.8D;
            entity.setDeltaMovement(motion.x, -motion.y * retained, motion.z);
        }
    }
}
