// Copyright (C) 2026 RedTeam. Licensed under GNU AGPLv3.
package red.team.badghost.automation;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;
import red.team.badghost.config.BadghostConfig;
import red.team.badghost.core.ClientContext;
import red.team.badghost.utils.InventoryHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * What the miner needs before it will touch a block, and how to say it out loud.
 *
 * <p>The requirements are not obvious from the outside, so they are reported as a checklist
 * with counts rather than as a single "something is missing" line.</p>
 */
public final class MinerRequirements {
    private MinerRequirements() {}

    /** Two pistons: one to charge, one to swap in. */
    public static final int PISTONS_NEEDED = 2;
    public static final int TORCHES_NEEDED = 1;
    public static final int SUPPORT_NEEDED = 1;

    /** One line of the checklist. */
    public record Entry(Component label, int needed, int have) {
        public boolean satisfied() {
            return have >= needed;
        }
    }

    /** The item side of the checklist, always in the same order. */
    public static List<Entry> items() {
        List<Entry> entries = new ArrayList<>(3);
        entries.add(new Entry(
                Component.translatable("badghost.req.piston"),
                PISTONS_NEEDED,
                InventoryHelper.countItem(Items.PISTON) + InventoryHelper.countItem(Items.STICKY_PISTON)));
        entries.add(new Entry(
                Component.translatable("badghost.req.torch"),
                TORCHES_NEEDED,
                InventoryHelper.countItem(Items.REDSTONE_TORCH)));

        Block support = AutomationEngine.resolveSupportBlock();
        entries.add(new Entry(support.getName(), SUPPORT_NEEDED, InventoryHelper.countItem(support.asItem())));
        return entries;
    }

    public static boolean inSurvival() {
        var gameMode = ClientContext.getClient().gameMode;
        return gameMode != null && gameMode.getPlayerMode() == GameType.SURVIVAL;
    }

    public static boolean hasTool(@Nullable BlockPos reference) {
        return BadghostConfig.SKIP_INSTAMINE_CHECK.get() || InventoryHelper.hasInstaMineTool(reference);
    }

    /** Everything that is not satisfied, or {@code null} when the miner is ready to run. */
    @Nullable
    public static Component describeMissing(@Nullable BlockPos reference) {
        List<Component> missing = new ArrayList<>();

        if (!inSurvival()) {
            missing.add(Component.translatable("badghost.req.survival"));
        }
        for (Entry entry : items()) {
            if (!entry.satisfied()) {
                missing.add(Component.translatable("badghost.req.count", entry.label(), entry.needed(), entry.have()));
            }
        }
        if (!hasTool(reference)) {
            missing.add(Component.translatable("badghost.req.tool"));
        }

        if (missing.isEmpty()) {
            return null;
        }
        return Component.translatable("badghost.message.missing", join(missing)).withStyle(ChatFormatting.RED);
    }

    /** The full checklist, ticked and crossed, for the HUD and the toggle message. */
    public static Component describeChecklist(@Nullable BlockPos reference) {
        List<Component> parts = new ArrayList<>();

        parts.add(mark(inSurvival(), Component.translatable("badghost.req.survival")));
        for (Entry entry : items()) {
            parts.add(mark(entry.satisfied(),
                    Component.translatable("badghost.req.count", entry.label(), entry.needed(), entry.have())));
        }
        parts.add(mark(hasTool(reference), Component.translatable("badghost.req.tool")));

        return join(parts);
    }

    private static Component mark(boolean ok, Component text) {
        return Component.literal(ok ? "✓ " : "✗ ")
                .append(text)
                .withStyle(ok ? ChatFormatting.GREEN : ChatFormatting.RED);
    }

    private static MutableComponent join(List<Component> parts) {
        MutableComponent result = Component.empty();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                result.append(Component.literal(", ").withStyle(ChatFormatting.GRAY));
            }
            result.append(parts.get(i));
        }
        return result;
    }
}
