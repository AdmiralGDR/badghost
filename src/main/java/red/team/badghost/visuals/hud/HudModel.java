// Copyright (C) 2026 RedTeam. Licensed under GNU AGPLv3.
package red.team.badghost.visuals.hud;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the panel's content from plain values.
 *
 * <p>Takes no client, reads no globals and draws nothing, so what the HUD says under any given
 * state can be checked directly in a test.</p>
 */
public final class HudModel {
    private HudModel() {}

    /** Everything the panel needs to know, gathered by the caller. */
    public record State(
            int queueSize,
            int queueLimit,
            @Nullable String currentTaskState,
            Component checklist,
            @Nullable Component blocker,
            int broken,
            int failed,
            int attemptsPerBreakTenths,
            long averageTicksPerBlock,
            /** Id of a setting that is on but whose hook never applied, or null when all is well. */
            @Nullable String deadFeature) {}

    public static List<HudLine> build(State state) {
        List<HudLine> lines = new ArrayList<>(7);
        lines.add(new HudLine(Component.translatable("badghost.hud.title"), HudLine.Kind.TITLE));

        // Straight under the title, because a setting that cannot work outranks anything else the
        // panel has to say: everything below it may be describing a feature that is not running.
        if (state.deadFeature() != null) {
            lines.add(new HudLine(
                    Component.translatable("badghost.hud.feature_dead",
                            Component.translatable("badghost.feature." + state.deadFeature())),
                    HudLine.Kind.PROBLEM));
        }

        boolean full = state.queueSize() >= state.queueLimit();
        lines.add(new HudLine(
                Component.translatable(full ? "badghost.hud.queue_full" : "badghost.hud.queue", state.queueSize()),
                full ? HudLine.Kind.PROBLEM : HudLine.Kind.INFO));

        if (state.currentTaskState() == null) {
            lines.add(new HudLine(Component.translatable("badghost.hud.idle"), HudLine.Kind.MUTED));
            // The checklist only matters while nothing is running: it answers "why is my click
            // doing nothing", which is not a question you have once work has started.
            lines.add(new HudLine(state.checklist(), HudLine.Kind.INFO));
        } else {
            lines.add(new HudLine(
                    Component.translatable("badghost.hud.status", state.currentTaskState()),
                    HudLine.Kind.ACTIVE));
        }

        if (state.blocker() != null) {
            lines.add(new HudLine(state.blocker(), HudLine.Kind.PROBLEM));
        }

        // Silent until there is something to report, so an untouched HUD stays three lines.
        if (state.broken() > 0 || state.failed() > 0) {
            lines.add(new HudLine(
                    Component.translatable("badghost.hud.stats",
                            state.broken(),
                            state.failed(),
                            formatTenths(state.attemptsPerBreakTenths()),
                            formatSeconds(state.averageTicksPerBlock())),
                    HudLine.Kind.MUTED));
        }
        return lines;
    }

    /** Renders tenths as a one-decimal number without dragging in locale-dependent formatting. */
    static String formatTenths(int tenths) {
        return (tenths / 10) + "." + Math.abs(tenths % 10);
    }

    /** Ticks as seconds with one decimal; 20 ticks make a second. */
    static String formatSeconds(long ticks) {
        return formatTenths((int) (ticks / 2));
    }
}
