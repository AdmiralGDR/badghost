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

    public static final KeyMapping UNDO_GHOST_BLOCK = new KeyMapping(
            "key.badghost.undoghostblock",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_H,
            CATEGORY);

    public static final KeyMapping CLEAR_GHOST_BLOCKS = new KeyMapping(
            "key.badghost.clearghostblocks",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_J,
            CATEGORY);

    /** Hides or shows the status panel without leaving the game for the settings screen. */
    public static final KeyMapping TOGGLE_HUD = new KeyMapping(
            "key.badghost.togglehud",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_K,
            CATEGORY);

    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(PLACE_GHOST_BLOCK);
        event.register(UNDO_GHOST_BLOCK);
        event.register(CLEAR_GHOST_BLOCKS);
        event.register(TOGGLE_AUTOMATION);
        event.register(TOGGLE_HUD);
    }
}
