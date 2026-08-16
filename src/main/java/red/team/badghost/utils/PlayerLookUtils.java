// Copyright (C) 2026 RedTeam. Licensed under GNU AGPLv3.
package red.team.badghost.utils;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import org.jetbrains.annotations.Nullable;
import red.team.badghost.core.ClientContext;

/**
 * Rotation the server is told about, which is not the rotation the player sees.
 *
 * <p>A held override is only needed for {@code ALL_DIRECTION} plans: those place the decisive
 * piston sideways, and the faked yaw has to survive whatever movement packets the client emits
 * between the rotation and the placement. {@code ServerboundMovePlayerPacketMixin} rewrites
 * those packets while an override is set. Vertical plans do not need this at all — the
 * rotation and the placement go out in the same tick.</p>
 */
public final class PlayerLookUtils {
    private PlayerLookUtils() {}

    /** An override never outlives this many ticks, however the caller exits. */
    private static final int AUTO_RESET_TICKS = 20;

    private static boolean active;
    private static float yaw;
    private static float pitch;
    private static int ticks;

    /**
     * Angle that makes a placed directional block point at {@code facing}. Blocks take the
     * opposite of the placement look direction, so the table is inverted relative to the naive
     * reading: {@code NORTH} is yaw 0, which is looking south.
     */
    public static float yawFor(Direction facing) {
        return switch (facing) {
            case SOUTH -> 180F;
            case EAST -> 90F;
            case NORTH -> 0F;
            case WEST -> -90F;
            default -> currentYaw();
        };
    }

    public static float pitchFor(Direction facing) {
        return switch (facing) {
            case UP -> 90F;
            case DOWN -> -90F;
            default -> 0F;
        };
    }

    /** Called from the mixin. Returns the faked yaw while an override is held. */
    public static float getYaw(float original) {
        return active ? yaw : original;
    }

    /** Called from the mixin. Returns the faked pitch while an override is held. */
    public static float getPitch(float original) {
        return active ? pitch : original;
    }

    public static boolean isActive() {
        return active;
    }

    /** Holds a faked angle and tells the server about it immediately. */
    public static void hold(Direction facing) {
        yaw = yawFor(facing);
        pitch = pitchFor(facing);
        active = true;
        ticks = 0;
        sendRotation(yaw, pitch);
    }

    /** Drops the override and restores the player's real angle for the server. */
    public static void release() {
        if (!active) {
            return;
        }
        active = false;
        ticks = 0;
        LocalPlayer player = ClientContext.getPlayer();
        if (player != null) {
            sendRotation(player.getYRot(), player.getXRot());
        }
    }

    /** Safety net against an override outliving the task that set it. */
    public static void tick() {
        if (active && ++ticks > AUTO_RESET_TICKS) {
            release();
        }
    }

    /**
     * Sends one rotation packet. The mixin substitutes the held angle when an override is
     * active, so the arguments are what the server sees only when it is not.
     */
    public static void sendRotation(float sendYaw, float sendPitch) {
        LocalPlayer player = ClientContext.getPlayer();
        if (player == null) {
            return;
        }
        NetworkUtils.send(new ServerboundMovePlayerPacket.Rot(sendYaw, sendPitch, player.onGround()));
    }

    private static float currentYaw() {
        @Nullable LocalPlayer player = ClientContext.getPlayer();
        return player == null ? 0F : player.getYRot();
    }
}
