// Copyright (C) 2026 RedTeam. Licensed under GNU AGPLv3.
package red.team.badghost.core;

import red.team.badghost.config.BadghostConfig;

/**
 * Runtime state. Deliberately separate from {@link BadghostConfig}: writing a toggle through
 * {@code ModConfigSpec.ConfigValue#set} needs a loaded config, hits the config file on every
 * keypress, and makes the miner re-arm itself on the next login. The config only supplies the
 * initial value when a world is entered.
 */
public final class ModState {
    private ModState() {}

    private static boolean automationEnabled;

    /**
     * Set while the mod breaks a block through {@code MultiPlayerGameMode#startDestroyBlock},
     * which fires {@code PlayerInteractEvent.LeftClickBlock} from the inside. Without this the
     * engine would re-enqueue its own demolition work.
     */
    private static boolean internalBreak;

    public static boolean isAutomationEnabled() {
        return automationEnabled;
    }

    public static void setAutomationEnabled(boolean enabled) {
        automationEnabled = enabled;
    }

    public static boolean toggleAutomation() {
        automationEnabled = !automationEnabled;
        return automationEnabled;
    }

    public static boolean isInternalBreak() {
        return internalBreak;
    }

    public static void setInternalBreak(boolean value) {
        internalBreak = value;
    }

    /** Called when a world is joined; restores the configured default. */
    public static void onJoinWorld() {
        automationEnabled = BadghostConfig.AUTOMATION_ENABLED.get();
        internalBreak = false;
    }

    /** Called when a world is left; nothing may survive into the next session. */
    public static void onLeaveWorld() {
        automationEnabled = false;
        internalBreak = false;
    }
}
