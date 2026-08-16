// Copyright (C) 2026 RedTeam. Licensed under GNU AGPLv3.
package red.team.badghost.visuals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** A bad colour in the config must fall back, never throw out of the render loop. */
class EspColorTest {

    @Test
    @DisplayName("plain and hash-prefixed hex both parse")
    void parsesHex() {
        assertEquals(0xFFAA00, EspRenderer.parseHex("FFAA00"));
        assertEquals(0xFFAA00, EspRenderer.parseHex("#FFAA00"));
        assertEquals(0xFFAA00, EspRenderer.parseHex("  ffaa00  "));
        assertEquals(0x000000, EspRenderer.parseHex("000000"));
    }

    @Test
    @DisplayName("malformed values report failure instead of throwing")
    void rejectsGarbage() {
        assertEquals(-1, EspRenderer.parseHex(null));
        assertEquals(-1, EspRenderer.parseHex(""));
        assertEquals(-1, EspRenderer.parseHex("FFAA"));
        assertEquals(-1, EspRenderer.parseHex("FFAA000"));
        assertEquals(-1, EspRenderer.parseHex("ZZZZZZ"));
        assertEquals(-1, EspRenderer.parseHex("orange"));
    }
}
