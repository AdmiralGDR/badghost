// Copyright (C) 2026 RedTeam. Licensed under GNU AGPLv3.
package red.team.badghost.visuals;

import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.network.chat.Component;
import red.team.badghost.automation.AutomationEngine;
import red.team.badghost.automation.MinerRequirements;
import red.team.badghost.automation.MinerTask;
import red.team.badghost.config.BadghostConfig;
import red.team.badghost.core.ModState;

/** Compact status panel in the top left corner, in place of chat spam. */
public final class MinerHUD implements LayeredDraw.Layer {

    private static final int MARGIN = 10;
    private static final int LINE_HEIGHT = 12;
    private static final int TEXT_COLOR = 0xFFFFFF;

    @Override
    public void render(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        if (!ModState.isAutomationEnabled()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.font == null || mc.options.hideGui) {
            return;
        }

        int y = MARGIN;
        guiGraphics.drawString(mc.font,
                Component.translatable("badghost.hud.title").withStyle(ChatFormatting.AQUA),
                MARGIN, y, TEXT_COLOR, true);
        y += LINE_HEIGHT;

        int queueSize = AutomationEngine.getQueueSize();
        boolean full = queueSize >= BadghostConfig.LIMIT_MAX.get();
        guiGraphics.drawString(mc.font,
                Component.translatable(full ? "badghost.hud.queue_full" : "badghost.hud.queue", queueSize)
                        .withStyle(full ? ChatFormatting.RED : ChatFormatting.WHITE),
                MARGIN, y, TEXT_COLOR, true);
        y += LINE_HEIGHT;

        MinerTask current = AutomationEngine.getCurrentTask();
        Component status = current == null
                ? Component.translatable("badghost.hud.idle").withStyle(ChatFormatting.DARK_GRAY)
                : Component.translatable("badghost.hud.status", current.getState().name()).withStyle(ChatFormatting.YELLOW);
        guiGraphics.drawString(mc.font, status, MARGIN, y, TEXT_COLOR, true);
        y += LINE_HEIGHT;

        // While idle, the checklist is the only way to see why a click does nothing.
        if (current == null) {
            guiGraphics.drawString(mc.font, MinerRequirements.describeChecklist(null), MARGIN, y, TEXT_COLOR, true);
        }
    }
}
