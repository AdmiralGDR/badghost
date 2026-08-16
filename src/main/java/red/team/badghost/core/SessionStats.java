// Copyright (C) 2026 RedTeam. Licensed under GNU AGPLv3.
package red.team.badghost.core;

/**
 * What the miner has actually done this session.
 *
 * <p>Counts only, held in memory, wiped when a world is left: nothing is written to disk and
 * nothing leaves the client. It exists so the player can see whether the thing is working
 * without reading a log.</p>
 */
public final class SessionStats {
    private SessionStats() {}

    private static int broken;
    private static int failed;
    private static int attempts;
    private static long totalTicksSpent;

    /** A target was removed. */
    public static void recordBroken(long ticksSpent) {
        broken++;
        totalTicksSpent += Math.max(ticksSpent, 0L);
    }

    /** A target was given up on. */
    public static void recordFailed() {
        failed++;
    }

    /** One try at a target, successful or not. */
    public static void recordAttempt() {
        attempts++;
    }

    public static int broken() {
        return broken;
    }

    public static int failed() {
        return failed;
    }

    /** Average ticks spent per removed block, or 0 when nothing has been removed yet. */
    public static long averageTicksPerBlock() {
        return broken == 0 ? 0L : totalTicksSpent / broken;
    }

    /** Attempts per success, scaled by 10 so one decimal survives integer maths. */
    public static int attemptsPerBreakTenths() {
        return broken == 0 ? 0 : (attempts * 10) / broken;
    }

    public static void reset() {
        broken = 0;
        failed = 0;
        attempts = 0;
        totalTicksSpent = 0L;
    }
}
