// Copyright (C) 2026 RedTeam. Licensed under GNU AGPLv3.
package red.team.badghost.visuals;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import red.team.badghost.config.BadghostConfig;

/**
 * Decides whether a status effect's visual nuisance should be hidden, and counts what it hid.
 *
 * <p>Only the picture changes. The effects themselves belong to the server: they keep their full
 * duration, and everything they do to the player still happens. Nausea stops swirling the screen
 * and blindness stops collapsing the fog, so the world stays legible.</p>
 *
 * <p>The counters exist so the suppression can be proven to fire rather than assumed to —
 * {@code scripts/selftest.sh} reads them after applying the effects for real.</p>
 */
public final class NegativeEffectFilter {
    private NegativeEffectFilter() {}

    private static int nauseaSuppressed;
    private static int fogSuppressed;

    /** Whether the player asked for the visual nuisances to be hidden. */
    public static boolean enabled() {
        return BadghostConfig.DISABLE_NEGATIVES.get();
    }

    /** True while the local player is under nausea. */
    public static boolean hasNausea() {
        return has(MobEffects.CONFUSION);
    }

    /** True while the local player has an effect that collapses the fog. */
    public static boolean hasSightRobbingEffect() {
        return has(MobEffects.BLINDNESS) || has(MobEffects.DARKNESS);
    }

    public static boolean has(Holder<MobEffect> effect) {
        LivingEntity player = Minecraft.getInstance().player;
        return player != null && player.hasEffect(effect);
    }

    /** Called when the nausea overlay was skipped. */
    public static void countNausea() {
        nauseaSuppressed++;
    }

    /** Called when collapsed fog was pushed back out. */
    public static void countFog() {
        fogSuppressed++;
    }

    public static int nauseaSuppressedCount() {
        return nauseaSuppressed;
    }

    public static int fogSuppressedCount() {
        return fogSuppressed;
    }

    public static void reset() {
        nauseaSuppressed = 0;
        fogSuppressed = 0;
    }
}
