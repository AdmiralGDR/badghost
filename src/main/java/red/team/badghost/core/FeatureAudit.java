// Copyright (C) 2026 RedTeam. Licensed under GNU AGPLv3.
package red.team.badghost.core;

import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.extensions.IBlockExtension;
import org.jetbrains.annotations.Nullable;
import red.team.badghost.automation.plan.PlanMode;
import red.team.badghost.config.BadghostConfig;
import red.team.badghost.visuals.GhostPhysics;
import red.team.badghost.visuals.NegativeEffectFilter;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

/**
 * Whether the mod's optional capabilities are actually wired into the game.
 *
 * <p>Three of them — slippery ghost blocks, bouncy ghost blocks, suppressed nausea — and the
 * rotation the server is told about are carried by mixins. A mixin that fails to apply, because
 * the game moved a method or another mod claimed the same spot, takes its feature with it and
 * says nothing: the setting stays on, and pressing the key does nothing at all. Silence is the
 * worst possible report, so this asks each hook whether it is there and lets the player know
 * when the answer is no.</p>
 *
 * <p>Two independent facts are collected. <em>Applied</em> is structural: the mixin's handler
 * was merged into the target class, checked once by reflection. <em>Fired</em> is behavioural:
 * the handler has actually run this session, taken from counters the features already keep. A
 * capability can be applied without having fired yet — you have not walked on a ghost block —
 * but one that fired is beyond doubt.</p>
 */
public final class FeatureAudit {
    private FeatureAudit() {}

    public enum Health {
        /** Hooked up and ready. */
        LIVE,
        /** Wanted, but the hook is missing: the setting is a lie until this is fixed. */
        DEAD,
        /** Switched off, so nothing was expected of it. */
        OFF
    }

    /** Reported as the fired count for a capability that keeps no counter. */
    public static final int NOT_COUNTED = -1;

    /** One capability's verdict. {@code fired} is {@link #NOT_COUNTED} when nothing counts it. */
    public record Row(String id, Health health, int fired) {
        public Component name() {
            return Component.translatable("badghost.feature." + id);
        }
    }

    /**
     * A capability, the hook that carries it, and how to tell whether it is wanted.
     *
     * @param handlers every handler the capability needs; all must be present, since a partly
     *                 applied mixin is a dead feature with a convincing disguise.
     */
    private record Probe(String id, Class<?> target, List<String> handlers,
                         BooleanSupplier wanted, IntSupplier fired) {}

    // Plain array and index loops throughout: the HUD asks about this every frame, and iterating
    // a List allocates an iterator each time (§ no allocations on hot paths).
    private static final Probe[] PROBES = {
            new Probe("rotation", ServerboundMovePlayerPacket.class,
                    List.of("badghost$overrideRotation"),
                    // Only sideways plans hold a faked angle; vertical ones never need the hook.
                    () -> BadghostConfig.PLAN_MODE.get() == PlanMode.ALL_DIRECTION,
                    () -> NOT_COUNTED),
            new Probe("friction", IBlockExtension.class,
                    List.of("badghost$ghostFriction"),
                    BadghostConfig.FROZEN_SLIPPERY::get,
                    GhostPhysics::frictionAppliedCount),
            new Probe("bounce", Entity.class,
                    List.of("badghost$bounceOnGhost"),
                    BadghostConfig.BOUNCY::get,
                    GhostPhysics::bounceAppliedCount),
            new Probe("negatives", GameRenderer.class,
                    List.of("badghost$skipConfusionOverlay", "badghost$flattenSpin"),
                    BadghostConfig.DISABLE_NEGATIVES::get,
                    () -> NegativeEffectFilter.nauseaSuppressedCount()
                            + NegativeEffectFilter.fogSuppressedCount()),
            new Probe("packets", ClientCommonPacketListenerImpl.class,
                    List.of("badghost$countOutgoing"),
                    // Only wanted while a tally is being kept; the rest of the time an idle
                    // observer is nothing to warn about, so it reports OFF rather than a problem.
                    PacketLog::isRecording,
                    PacketLog::total)
    };

    /** Structural answers, which cannot change once the classes are loaded, so asked once. */
    private static boolean @Nullable [] applied;

    private static synchronized boolean[] applied() {
        if (applied == null) {
            boolean[] found = new boolean[PROBES.length];
            for (int i = 0; i < PROBES.length; i++) {
                found[i] = isApplied(PROBES[i]);
            }
            applied = found;
        }
        return applied;
    }

    /**
     * True when every handler the probe names is present on the target class.
     *
     * <p>Matched by suffix, not by equality, because Mixin does not merge a handler under the
     * name it was written with. Measured on this exact toolchain, the merged names are:</p>
     *
     * <pre>
     *   handler$zza000$badghost$overrideRotation          (&#64;Inject)
     *   wrapOperation$zzc000$badghost$bounceOnGhost       (&#64;WrapOperation)
     *   modifyExpressionValue$zzd000$badghost$flattenSpin (&#64;ModifyExpressionValue)
     * </pre>
     *
     * <p>The shape is {@code <injector>$<unique>$<original>}, and the unique part is not ours to
     * predict, so only the tail is dependable. Comparing for equality — the obvious reading, and
     * the one this started as — declared every capability dead while three of them were provably
     * firing; the self-test cross-checks the verdict against the counters for exactly that
     * reason.</p>
     */
    private static boolean isApplied(Probe probe) {
        List<String> wanted = probe.handlers();
        int hits = 0;
        for (Method method : probe.target().getDeclaredMethods()) {
            String name = method.getName();
            for (String handler : wanted) {
                if (name.equals(handler) || name.endsWith('$' + handler)) {
                    hits++;
                    break;
                }
            }
        }
        return hits >= wanted.size();
    }

    /**
     * Method names on a capability's target class that look like they came from this mod.
     *
     * <p>Exists so a probe reporting DEAD can say what it actually found instead of only what it
     * expected — the difference between "the mixin is missing" and "the mixin is there under
     * another name" is the difference between a real fault and a broken probe.</p>
     */
    public static List<String> foundHandlers(String id) {
        for (Probe probe : PROBES) {
            if (probe.id().equals(id)) {
                List<String> names = new ArrayList<>();
                for (Method method : probe.target().getDeclaredMethods()) {
                    if (method.getName().contains("badghost")) {
                        names.add(method.getName());
                    }
                }
                return names;
            }
        }
        return List.of();
    }

    /** Ids of every capability this audit knows about, in report order. */
    public static List<String> ids() {
        List<String> ids = new ArrayList<>(PROBES.length);
        for (Probe probe : PROBES) {
            ids.add(probe.id());
        }
        return ids;
    }

    /** Every capability with its current verdict, for the audit command and the self-test. */
    public static List<Row> report() {
        boolean[] found = applied();
        List<Row> rows = new ArrayList<>(PROBES.length);
        for (int i = 0; i < PROBES.length; i++) {
            Probe probe = PROBES[i];
            Health health = !probe.wanted().getAsBoolean() ? Health.OFF
                    : found[i] ? Health.LIVE : Health.DEAD;
            rows.add(new Row(probe.id(), health, probe.fired().getAsInt()));
        }
        return rows;
    }

    /**
     * The first capability that is switched on but not wired up, or null when all is well.
     *
     * <p>Allocates nothing, so the HUD may ask on every frame.</p>
     */
    public static @Nullable String firstDead() {
        boolean[] found = applied();
        for (int i = 0; i < PROBES.length; i++) {
            if (!found[i] && PROBES[i].wanted().getAsBoolean()) {
                return PROBES[i].id();
            }
        }
        return null;
    }

    /**
     * Tells the player about anything switched on that cannot work.
     *
     * <p>Says nothing when all is well: a warning that appears every time you join a world is a
     * warning nobody reads by the third one.</p>
     */
    @SubscribeEvent
    public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        boolean[] found = applied();
        for (int i = 0; i < PROBES.length; i++) {
            if (!found[i] && PROBES[i].wanted().getAsBoolean()) {
                event.getPlayer().displayClientMessage(
                        Component.translatable("badghost.message.feature_dead",
                                Component.translatable("badghost.feature." + PROBES[i].id())),
                        false);
            }
        }
    }

    /** True when every mixin this mod ships applied, regardless of what is switched on. */
    public static boolean allApplied() {
        boolean[] found = applied();
        for (boolean ok : found) {
            if (!ok) {
                return false;
            }
        }
        return true;
    }
}
