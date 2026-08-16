// Copyright (C) 2026 RedTeam. Licensed under GNU AGPLv3.
package red.team.badghost.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import red.team.badghost.visuals.NegativeEffectFilter;

/**
 * Stops nausea from swirling the view.
 *
 * <p>Vanilla draws it two different ways and only one of them runs at a time, which is why both
 * are hooked here. With "Distortion Effects" below 100% the leftover is painted as an overlay;
 * at the default 100% there is no overlay at all and the warp comes from rotating and squashing
 * the projection matrix by the player's spinning intensity. Hooking only the overlay — the
 * obvious-looking target — suppresses nothing for a player on default settings.</p>
 *
 * <p>Purely cosmetic and purely local: the effect keeps its full duration and every other
 * consequence; only the picture stops moving.</p>
 */
@Mixin(GameRenderer.class)
public class ConfusionOverlayMixin {

    /** The overlay path, used when the distortion slider is turned down. */
    @Inject(method = "renderConfusionOverlay", at = @At("HEAD"), cancellable = true)
    private void badghost$skipConfusionOverlay(GuiGraphics guiGraphics, float scalar, CallbackInfo ci) {
        if (NegativeEffectFilter.enabled()) {
            NegativeEffectFilter.countNausea();
            ci.cancel();
        }
    }

    /**
     * The projection-warp path, used at default settings. Reporting no spin leaves the rest of
     * the maths intact and simply skips the rotation, exactly as it is skipped when nausea is
     * not active at all.
     */
    @Redirect(
            method = "renderLevel",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;lerp(FFF)F", ordinal = 0))
    private float badghost$flattenSpin(float delta, float from, float to) {
        if (NegativeEffectFilter.enabled() && NegativeEffectFilter.hasNausea()) {
            NegativeEffectFilter.countNausea();
            return 0.0F;
        }
        return Mth.lerp(delta, from, to);
    }
}
