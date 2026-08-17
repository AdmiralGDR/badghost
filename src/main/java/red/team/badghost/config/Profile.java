// Copyright (C) 2026 RedTeam. Licensed under GNU AGPLv3.
package red.team.badghost.config;

import org.jetbrains.annotations.Nullable;
import red.team.badghost.automation.plan.PlanMode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Coherent groups of settings, applied in one go.
 *
 * <p>There are twenty-odd options and they are not independent: turning on sideways plans without
 * loosening the retry budget, or raising the queue without turning off auto-scan, gives a
 * combination nobody wants. Rather than leave that knowledge in the reader's head, each profile is
 * a combination that makes sense together.</p>
 *
 * <p>Nothing here is hidden state: a profile writes the same options the settings screen shows, so
 * after applying one you can see exactly what changed and adjust it further.</p>
 */
public enum Profile {

    /** Slowest and least visible: vertical plans only, one target, auto-scan off. */
    SAFE {
        @Override
        void write() {
            BadghostConfig.PLAN_MODE.set(PlanMode.VERTICAL_FAST);
            BadghostConfig.LIMIT_MAX.set(1);
            BadghostConfig.MINER_MAX_RETRIES.set(3);
            BadghostConfig.AUTO_SCAN_ENABLED.set(false);
            BadghostConfig.PREVIEW_ENABLED.set(true);
            BadghostConfig.SKIP_INSTAMINE_CHECK.set(false);
        }
    },

    /** Every face considered, several targets at once, nearby bedrock queued without asking. */
    FAST {
        @Override
        void write() {
            BadghostConfig.PLAN_MODE.set(PlanMode.ALL_DIRECTION);
            BadghostConfig.LIMIT_MAX.set(4);
            BadghostConfig.MINER_MAX_RETRIES.set(5);
            BadghostConfig.AUTO_SCAN_ENABLED.set(true);
            BadghostConfig.PREVIEW_ENABLED.set(true);
            BadghostConfig.SKIP_INSTAMINE_CHECK.set(false);
        }
    },

    /**
     * For working out why something failed: everything drawn, nothing skipped, and the
     * insta-break requirement waived so a doomed attempt still runs and can be watched.
     */
    DEBUG {
        @Override
        void write() {
            BadghostConfig.PLAN_MODE.set(PlanMode.ALL_DIRECTION);
            BadghostConfig.LIMIT_MAX.set(1);
            BadghostConfig.MINER_MAX_RETRIES.set(1);
            BadghostConfig.AUTO_SCAN_ENABLED.set(false);
            BadghostConfig.PREVIEW_ENABLED.set(true);
            BadghostConfig.ESP_ENABLED.set(true);
            BadghostConfig.SKIP_INSTAMINE_CHECK.set(true);
        }
    };

    /** Writes this profile's values. Split out so {@link #apply()} can own saving and reporting. */
    abstract void write();

    /** Lowercase name as it is typed and translated. */
    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String translationKey() {
        return "badghost.profile." + key();
    }

    /**
     * Applies the profile and persists it.
     *
     * @return false when the config is not loaded yet, so the caller can say so rather than
     *         leaving the player believing settings changed when they did not.
     */
    public boolean apply() {
        if (!BadghostConfig.SPEC.isLoaded()) {
            return false;
        }
        write();
        BadghostConfig.SPEC.save();
        return true;
    }

    /** The profile called {@code name}, ignoring case and surrounding blanks, or null. */
    public static @Nullable Profile byName(String name) {
        String wanted = name.trim().toLowerCase(Locale.ROOT);
        for (Profile profile : values()) {
            if (profile.key().equals(wanted)) {
                return profile;
            }
        }
        return null;
    }

    /** Names in the order they are offered, for a usage line. */
    public static List<String> names() {
        List<String> names = new ArrayList<>(values().length);
        for (Profile profile : values()) {
            names.add(profile.key());
        }
        return names;
    }
}
