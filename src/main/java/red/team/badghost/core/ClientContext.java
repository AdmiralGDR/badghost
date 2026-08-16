// Copyright (C) 2026 RedTeam. Licensed under GNU AGPLv3.
package red.team.badghost.core;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import org.jetbrains.annotations.Nullable;

/** Null-safe access to the client singletons, which are all absent between worlds. */
public final class ClientContext {
    private ClientContext() {}

    public static boolean isInvalid() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null || mc.player == null || mc.gameMode == null;
    }

    public static Minecraft getClient() {
        return Minecraft.getInstance();
    }

    @Nullable
    public static ClientLevel getLevel() {
        return Minecraft.getInstance().level;
    }

    @Nullable
    public static LocalPlayer getPlayer() {
        return Minecraft.getInstance().player;
    }

    @Nullable
    public static MultiPlayerGameMode getGameMode() {
        return Minecraft.getInstance().gameMode;
    }

    /**
     * Interaction events fire on both logical sides; in single player the integrated server
     * runs them on its own thread. Everything in this mod touches client-only state, so every
     * such handler has to bail out unless it is the render thread acting for the local player.
     */
    public static boolean isClientThread() {
        return Minecraft.getInstance().isSameThread();
    }

    /** True when {@code entity} is the player this client controls. */
    public static boolean isLocalPlayer(@Nullable net.minecraft.world.entity.Entity entity) {
        return entity != null && entity == Minecraft.getInstance().player;
    }
}
