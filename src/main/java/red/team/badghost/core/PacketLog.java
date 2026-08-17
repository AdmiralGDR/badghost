// Copyright (C) 2026 RedTeam. Licensed under GNU AGPLv3.
package red.team.badghost.core;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;

import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A tally of what the client sends, used to check that this mod adds nothing to it.
 *
 * <p>The mod's whole premise is that the server learns nothing about it, and until now that rested
 * on reading the code. This makes it an observation: with recording on, every outgoing packet is
 * counted by class, and {@code scripts/selftest.sh} mines real bedrock and then checks what went
 * out.</p>
 *
 * <h2>What a clean tally proves, and what it does not</h2>
 *
 * <p>It proves there is no packet class from outside {@code net.minecraft.network.protocol} — so no
 * packet this mod defined — and no custom-payload channel belonging to this mod, which is the one
 * way a mod would normally speak to a server. It does <em>not</em> say anything about the timing or
 * the values inside vanilla packets; those are ordinary interactions, and the tally is not evidence
 * about them either way. The check reports itself in those terms rather than claiming more.</p>
 *
 * <p>Off by default and never switched on by the mod itself. While off the cost is one read of a
 * {@code volatile boolean} — no allocation, nothing else — because this sits on the path every
 * packet takes.</p>
 */
public final class PacketLog {
    private PacketLog() {}

    /** Packets defined anywhere else than here did not come from vanilla. */
    public static final String VANILLA_PACKET_PACKAGE = "net.minecraft.network.protocol";

    /** Prefix under which a custom-payload channel is recorded instead of the packet class. */
    public static final String PAYLOAD_PREFIX = "CustomPayload:";

    private static volatile boolean recording;

    // Sends happen on the render thread and on the network thread, so the tally has to tolerate
    // both. Only reached while recording, which is the harness and never normal play.
    private static final ConcurrentMap<String, AtomicInteger> counts = new ConcurrentHashMap<>();

    /**
     * Counts one outgoing packet. Called from the client's own send path.
     *
     * <p>Named for what it observes rather than what it hooks, and does nothing to the packet: this
     * must never be able to change what the client sends, or the audit would be measuring
     * itself.</p>
     */
    public static void record(Packet<?> packet) {
        if (!recording) {
            return;
        }
        counts.computeIfAbsent(label(packet), key -> new AtomicInteger()).incrementAndGet();
    }

    /** Prefix under which a packet class from outside vanilla's protocol packages is recorded. */
    public static final String FOREIGN_PREFIX = "foreign:";

    /**
     * How a packet appears in the tally.
     *
     * <p>Three cases, because they answer different questions. A packet class from outside vanilla's
     * protocol packages is marked {@code foreign:} with its full name — that is a packet type
     * somebody added, and the whole point is that there should be none. Custom payloads are recorded
     * by channel rather than class, since they are all the same class and the channel is the part
     * that says whose it is. Everything else keeps its class name, nested types included
     * ({@code ServerboundMovePlayerPacket$PosRot}), because the inner name alone says nothing.</p>
     */
    static String label(Packet<?> packet) {
        String name = packet.getClass().getName();
        if (!name.startsWith(VANILLA_PACKET_PACKAGE)) {
            return FOREIGN_PREFIX + name;
        }
        if (packet instanceof ServerboundCustomPayloadPacket payload) {
            return PAYLOAD_PREFIX + payload.payload().type().id();
        }
        int lastDot = name.lastIndexOf('.');
        return lastDot < 0 ? name : name.substring(lastDot + 1);
    }

    /** Begins a fresh window. Anything counted before is discarded. */
    public static void start() {
        counts.clear();
        recording = true;
    }

    public static void stop() {
        recording = false;
    }

    public static boolean isRecording() {
        return recording;
    }

    /** How many packets the current window has seen, across all kinds. */
    public static int total() {
        int sum = 0;
        for (AtomicInteger count : counts.values()) {
            sum += count.get();
        }
        return sum;
    }

    /** The window so far, ordered by name so two runs can be compared by eye. */
    public static SortedMap<String, Integer> snapshot() {
        SortedMap<String, Integer> copy = new TreeMap<>();
        for (Map.Entry<String, AtomicInteger> entry : counts.entrySet()) {
            copy.put(entry.getKey(), entry.getValue().get());
        }
        return copy;
    }
}
