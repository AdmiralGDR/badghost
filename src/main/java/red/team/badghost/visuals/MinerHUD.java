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

    /**
     * Ticks between checklist rebuilds. Building it walks the whole inventory several times and
     * probes every slot for mining speed, which must not happen once per frame; half a second of
     * staleness on an informational line is imperceptible.
     */
    private static final int CHECKLIST_REFRESH_TICKS = 10;

    private static Component cachedChecklist;
    private static long checklistBuiltAt = Long.MIN_VALUE;

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
            guiGraphics.drawString(mc.font, checklist(mc), MARGIN, y, TEXT_COLOR, true);
        }
    }

    /** Rebuilds the requirement checklist at most every {@link #CHECKLIST_REFRESH_TICKS}. */
    private static Component checklist(Minecraft mc) {
        long now = mc.level == null ? 0L : mc.level.getGameTime();
        if (cachedChecklist == null || now - checklistBuiltAt >= CHECKLIST_REFRESH_TICKS || now < checklistBuiltAt) {
            cachedChecklist = MinerRequirements.describeChecklist(null);
            checklistBuiltAt = now;
        }
        return cachedChecklist;
    }
}
