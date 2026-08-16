// Copyright (C) 2026 RedTeam. Licensed under GNU AGPLv3.
package red.team.badghost.visuals;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

public final class KeyBindings {
    private KeyBindings() {}

    public static final String CATEGORY = "category.badghost.general";

    public static final KeyMapping PLACE_GHOST_BLOCK = new KeyMapping(
            "key.badghost.createghostblock",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
            CATEGORY);

    public static final KeyMapping TOGGLE_AUTOMATION = new KeyMapping(
            "key.badghost.toggleautomation",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_B,
            CATEGORY);

    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(PLACE_GHOST_BLOCK);
        event.register(TOGGLE_AUTOMATION);
    }
}
