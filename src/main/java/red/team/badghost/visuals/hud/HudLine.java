// Copyright (C) 2026 RedTeam. Licensed under GNU AGPLv3.
package red.team.badghost.visuals.hud;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * One line of the panel, with what it means rather than how it looks.
 *
 * <p>Keeping the HUD's content separate from its painting is what lets it be asserted on in a
 * test with no client running — the same trick the plan finder uses with {@code WorldView}.</p>
 */
public record HudLine(Component text, Kind kind) {

    /** What a line is for; the painter maps this to a colour. */
    public enum Kind {
        TITLE,
        /** Neutral information. */
        INFO,
        /** Something the player has to fix before the miner will run. */
        PROBLEM,
        /** Work in progress. */
        ACTIVE,
        /** Idle or unimportant. */
        MUTED
    }

    public ChatFormatting color() {
        return switch (kind) {
            case TITLE -> ChatFormatting.AQUA;
            case INFO -> ChatFormatting.WHITE;
            case PROBLEM -> ChatFormatting.RED;
            case ACTIVE -> ChatFormatting.YELLOW;
            case MUTED -> ChatFormatting.DARK_GRAY;
        };
    }
}
