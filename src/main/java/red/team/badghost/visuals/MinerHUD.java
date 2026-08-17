// Copyright (C) 2026 RedTeam. Licensed under GNU AGPLv3.
package red.team.badghost.visuals;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.network.chat.Component;
import red.team.badghost.automation.AutomationEngine;
import red.team.badghost.automation.MinerRequirements;
import red.team.badghost.automation.MinerTask;
import red.team.badghost.automation.preview.PreviewService;
import red.team.badghost.config.BadghostConfig;
import red.team.badghost.core.FeatureAudit;
import red.team.badghost.core.ModState;
import red.team.badghost.core.SessionStats;
import red.team.badghost.visuals.hud.HudLine;
import red.team.badghost.visuals.hud.HudModel;

import java.util.List;

/**
 * Status panel in the top left corner, in place of chat spam.
 *
 * <p>Only gathers state and paints; what the panel actually says lives in {@link HudModel}, which
 * is testable without a client.</p>
 */
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

        MinerTask current = AutomationEngine.getCurrentTask();
        List<HudLine> lines = HudModel.build(new HudModel.State(
                AutomationEngine.getQueueSize(),
                BadghostConfig.LIMIT_MAX.get(),
                current == null ? null : current.getState().name(),
                checklist(mc),
                PreviewService.message(),
                SessionStats.broken(),
                SessionStats.failed(),
                SessionStats.attemptsPerBreakTenths(),
                SessionStats.averageTicksPerBlock(),
                FeatureAudit.firstDead()));

        int y = MARGIN;
        for (int i = 0; i < lines.size(); i++) {
            HudLine line = lines.get(i);
            guiGraphics.drawString(mc.font, line.text().copy().withStyle(line.color()),
                    MARGIN, y, TEXT_COLOR, true);
            y += LINE_HEIGHT;
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
