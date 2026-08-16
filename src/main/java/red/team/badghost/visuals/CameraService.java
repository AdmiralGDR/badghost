// Copyright (C) 2026 RedTeam. Licensed under GNU AGPLv3.
package red.team.badghost.visuals;

import net.neoforged.bus.api.SubscribeEvent;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.CalculateDetachedCameraDistanceEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import red.team.badghost.config.BadghostConfig;

/**
 * View comfort: how far the third-person camera sits, and whether sight-robbing effects are
 * allowed to collapse the fog.
 *
 * <p>All of it is the client's own picture, so none of it is visible to the server.</p>
 */
public final class CameraService {
    private CameraService() {}

    /** Vanilla's own third-person distance, and the value the option is measured against. */
    private static final float VANILLA_DISTANCE = 4.0F;

    @SubscribeEvent
    public static void onDetachedDistance(CalculateDetachedCameraDistanceEvent event) {
        double configured = BadghostConfig.CAMERA_DISTANCE.get();
        if (configured == VANILLA_DISTANCE) {
            return;
        }
        // Scale rather than overwrite, so the shortening vanilla already applied for whatever the
        // camera would collide with is preserved and it still cannot clip through walls.
        event.setDistance(event.getDistance() * (float) (configured / VANILLA_DISTANCE));
    }

    /**
     * Pushes the fog back out when blindness or darkness has crushed it to a few blocks.
     *
     * <p>Vanilla applies those through a fog function that runs before this event, so the values
     * seen here are already the collapsed ones; restoring them to the render distance is what
     * makes the effect stop blinding the view.</p>
     */
    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        if (!NegativeEffectFilter.enabled() || !NegativeEffectFilter.hasSightRobbingEffect()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        float far = mc.gameRenderer.getRenderDistance();
        if (event.getFarPlaneDistance() >= far) {
            return;
        }
        event.setNearPlaneDistance(0.0F);
        event.setFarPlaneDistance(far);
        event.setCanceled(true);
        NegativeEffectFilter.countFog();
    }
}
