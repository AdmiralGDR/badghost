// Copyright (C) 2026 RedTeam. Licensed under GNU AGPLv3.
package red.team.badghost.automation;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The checklist's count semantics: exactly {@code have >= needed}, boundary included. */
class MinerRequirementsTest {

    private static MinerRequirements.Entry entry(int needed, int have) {
        return new MinerRequirements.Entry(Component.literal("x"), needed, have);
    }

    @Test
    @DisplayName("having fewer than needed is not satisfied")
    void belowNeeded() {
        assertFalse(entry(2, 0).satisfied());
        assertFalse(entry(2, 1).satisfied());
    }

    @Test
    @DisplayName("having exactly the needed amount is satisfied")
    void exactlyNeeded() {
        assertTrue(entry(2, 2).satisfied());
        assertTrue(entry(1, 1).satisfied());
    }

    @Test
    @DisplayName("having more than needed is satisfied")
    void aboveNeeded() {
        assertTrue(entry(2, 64).satisfied());
    }

    @Test
    @DisplayName("the documented amounts are two pistons and one of each of the rest")
    void documentedAmounts() {
        assertTrue(MinerRequirements.PISTONS_NEEDED == 2);
        assertTrue(MinerRequirements.TORCHES_NEEDED == 1);
        assertTrue(MinerRequirements.SUPPORT_NEEDED == 1);
    }
}
