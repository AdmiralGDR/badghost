// Copyright (C) 2026 RedTeam. Licensed under GNU AGPLv3.
package red.team.badghost.visuals.hud;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The panel's content is a pure function, so it can be checked with no client running. */
class HudModelTest {

    private static final Component CHECKLIST = Component.literal("checklist");

    private static HudModel.State idle() {
        return new HudModel.State(0, 1, null, CHECKLIST, null, 0, 0, 0, 0L, null);
    }

    private static boolean hasKind(List<HudLine> lines, HudLine.Kind kind) {
        return lines.stream().anyMatch(l -> l.kind() == kind);
    }

    @Test
    @DisplayName("an untouched panel is title, queue, idle and the checklist")
    void idlePanel() {
        List<HudLine> lines = HudModel.build(idle());

        assertEquals(4, lines.size());
        assertSame(HudLine.Kind.TITLE, lines.get(0).kind());
        assertTrue(hasKind(lines, HudLine.Kind.MUTED), "idle status is muted");
        assertTrue(lines.stream().anyMatch(l -> l.text() == CHECKLIST),
                "the checklist answers 'why does my click do nothing'");
    }

    @Test
    @DisplayName("while a task runs the checklist gives way to its state")
    void activePanelDropsChecklist() {
        List<HudLine> lines = HudModel.build(
                new HudModel.State(1, 1, "SWAP", CHECKLIST, null, 0, 0, 0, 0L, null));

        assertTrue(hasKind(lines, HudLine.Kind.ACTIVE));
        assertFalse(lines.stream().anyMatch(l -> l.text() == CHECKLIST),
                "the checklist is noise once work has started");
    }

    @Test
    @DisplayName("a full queue is flagged as a problem, a partial one is not")
    void fullQueueIsFlagged() {
        assertTrue(hasKind(HudModel.build(new HudModel.State(1, 1, "SWAP", CHECKLIST, null, 0, 0, 0, 0L, null)),
                HudLine.Kind.PROBLEM));
        assertFalse(hasKind(HudModel.build(new HudModel.State(1, 4, "SWAP", CHECKLIST, null, 0, 0, 0, 0L, null)),
                HudLine.Kind.PROBLEM));
    }

    @Test
    @DisplayName("a blocker from the preview is shown as a problem")
    void blockerIsShown() {
        Component blocker = Component.literal("out of reach");
        List<HudLine> lines = HudModel.build(
                new HudModel.State(0, 1, null, CHECKLIST, blocker, 0, 0, 0, 0L, null));

        assertTrue(lines.stream().anyMatch(l -> l.text() == blocker && l.kind() == HudLine.Kind.PROBLEM));
    }

    @Test
    @DisplayName("statistics stay hidden until there is something to report")
    void statsAppearOnlyWhenEarned() {
        int before = HudModel.build(idle()).size();
        int after = HudModel.build(new HudModel.State(0, 1, null, CHECKLIST, null, 3, 1, 15, 60L, null)).size();

        assertEquals(before + 1, after, "one extra line once blocks have been broken");
    }

    @Test
    @DisplayName("a setting that cannot work is called out, right under the title")
    void deadFeatureIsFlagged() {
        List<HudLine> lines = HudModel.build(
                new HudModel.State(0, 1, null, CHECKLIST, null, 0, 0, 0, 0L, "bounce"));

        assertSame(HudLine.Kind.PROBLEM, lines.get(1).kind(),
                "it outranks everything below it, which may be describing a feature that is off");
        assertEquals(HudModel.build(idle()).size() + 1, lines.size());
    }

    @Test
    @DisplayName("a healthy panel says nothing about liveness")
    void healthyPanelIsQuiet() {
        // A warning shown every session is a warning nobody reads by the third one.
        assertFalse(HudModel.build(idle()).stream()
                .anyMatch(l -> l.kind() == HudLine.Kind.PROBLEM));
    }

    @Test
    @DisplayName("tenths render as a one-decimal number")
    void tenthsFormatting() {
        assertEquals("1.5", HudModel.formatTenths(15));
        assertEquals("2.0", HudModel.formatTenths(20));
        assertEquals("0.0", HudModel.formatTenths(0));
        assertEquals("10.3", HudModel.formatTenths(103));
    }

    @Test
    @DisplayName("ticks are reported as seconds")
    void secondsFormatting() {
        assertEquals("3.0", HudModel.formatSeconds(60L), "60 ticks is three seconds");
        assertEquals("0.5", HudModel.formatSeconds(10L));
        assertEquals("0.0", HudModel.formatSeconds(0L));
    }

    @Test
    @DisplayName("every kind maps to a colour")
    void everyKindHasAColor() {
        for (HudLine.Kind kind : HudLine.Kind.values()) {
            assertTrue(new HudLine(Component.empty(), kind).color() != null, kind.name());
        }
    }
}
