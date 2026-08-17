// Copyright (C) 2026 RedTeam. Licensed under GNU AGPLv3.
package red.team.badghost.core;

import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.world.InteractionHand;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.SortedMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The half of the packet audit that a clean run cannot demonstrate.
 *
 * <p>In the game the tally comes out clean, which is the point — but a clean tally also passes if
 * the detector never detects anything. A broken {@code foreign:} test would make the in-game check
 * succeed vacuously forever. So the detector is shown here to actually fire on a packet class from
 * outside vanilla, using one defined in this test.</p>
 */
class PacketLogTest {

    /** A packet type from outside {@code net.minecraft.network.protocol}, which is the whole point. */
    private static final class ModPacket implements Packet<PacketListener> {
        @Override
        public PacketType<? extends Packet<PacketListener>> type() {
            return null;
        }

        @Override
        public void handle(PacketListener listener) {
            // Never dispatched; this exists only to be labelled.
        }
    }

    @AfterEach
    void stopRecording() {
        PacketLog.stop();
    }

    @Test
    @DisplayName("a packet class from outside vanilla is marked foreign, by full name")
    void foreignPacketIsMarked() {
        String label = PacketLog.label(new ModPacket());

        assertTrue(label.startsWith(PacketLog.FOREIGN_PREFIX), label);
        assertTrue(label.contains(ModPacket.class.getName()),
                "the full name is what identifies whose packet it is: " + label);
    }

    @Test
    @DisplayName("a vanilla packet keeps its class name and is not marked")
    void vanillaPacketIsPlain() {
        String label = PacketLog.label(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));

        assertEquals("ServerboundSwingPacket", label);
        assertFalse(label.startsWith(PacketLog.FOREIGN_PREFIX));
        assertFalse(label.startsWith(PacketLog.PAYLOAD_PREFIX));
    }

    @Test
    @DisplayName("nothing is counted while recording is off")
    void silentWhileOff() {
        // This is the state the mod ships in, and it sits on the path of every outgoing packet.
        PacketLog.stop();
        PacketLog.record(new ModPacket());

        assertEquals(0, PacketLog.total());
    }

    @Test
    @DisplayName("a window counts what it is given and forgets the window before")
    void windowsAreIndependent() {
        PacketLog.start();
        PacketLog.record(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));
        PacketLog.record(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));
        PacketLog.record(new ModPacket());

        assertEquals(3, PacketLog.total());
        SortedMap<String, Integer> tally = PacketLog.snapshot();
        assertEquals(2, tally.get("ServerboundSwingPacket"));
        assertEquals(1, tally.get(PacketLog.FOREIGN_PREFIX + ModPacket.class.getName()));

        PacketLog.start();
        assertEquals(0, PacketLog.total(), "a new window starts empty");
        assertTrue(PacketLog.snapshot().isEmpty());
    }

    @Test
    @DisplayName("a snapshot cannot be written back into the tally")
    void snapshotIsDetached() {
        PacketLog.start();
        PacketLog.record(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));

        SortedMap<String, Integer> tally = PacketLog.snapshot();
        tally.clear();

        assertEquals(1, PacketLog.total(), "the tally is the record; a reader must not edit it");
    }
}
