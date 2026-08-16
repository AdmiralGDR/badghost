// Copyright (C) 2026 RedTeam. Licensed under GNU AGPLv3.
package red.team.badghost.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import red.team.badghost.utils.PlayerLookUtils;

/**
 * Rewrites the rotation carried by outgoing movement packets while an override is held.
 *
 * <p>The player's real view is untouched, so the camera never jerks; only the angle the server
 * reads changes. Every subclass ({@code Pos}, {@code Rot}, {@code PosRot}, {@code StatusOnly})
 * routes through this one constructor, so a single hook covers all of them.</p>
 */
@Mixin(ServerboundMovePlayerPacket.class)
public abstract class ServerboundMovePlayerPacketMixin {

    @Shadow
    @Final
    private boolean hasRot;

    @Shadow
    @Final
    @Mutable
    private float yRot;

    @Shadow
    @Final
    @Mutable
    private float xRot;

    @Inject(method = "<init>(DDDFFZZZ)V", at = @At("RETURN"))
    private void badghost$overrideRotation(double x, double y, double z, float packetYRot, float packetXRot,
                                           boolean onGround, boolean hasPos, boolean packetHasRot, CallbackInfo ci) {
        if (!this.hasRot || !PlayerLookUtils.isActive()) {
            return;
        }
        // Packets are also constructed off-thread during deserialisation on an integrated
        // server; only the client's own outgoing packets may be touched.
        if (!Minecraft.getInstance().isSameThread()) {
            return;
        }
        this.yRot = PlayerLookUtils.getYaw(this.yRot);
        this.xRot = PlayerLookUtils.getPitch(this.xRot);
    }
}
