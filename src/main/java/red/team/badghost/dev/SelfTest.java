// Copyright (C) 2026 RedTeam. Licensed under GNU AGPLv3.
package red.team.badghost.dev;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import red.team.badghost.automation.AutomationEngine;
import red.team.badghost.automation.MinerRequirements;
import red.team.badghost.automation.MinerTask;
import red.team.badghost.automation.plan.PlanMode;
import red.team.badghost.config.BadghostConfig;
import red.team.badghost.core.ClientContext;
import red.team.badghost.core.ModState;
import red.team.badghost.utils.InventoryHelper;

import java.util.List;
import java.util.function.Consumer;

/**
 * Drives the miner end to end inside a real client, so "it works" is an observation rather than
 * a claim.
 *
 * <p>Runs a sequence of scenarios, each building its own geometry, arming the miner and checking
 * the target block actually disappears. Both breaking modes are covered:
 * {@code VERTICAL_FAST} on a bedrock floor, and {@code ALL_DIRECTION} on a target whose vertical
 * faces are blocked — the sideways case that the rotation-spoofing mixin exists for. Overall it
 * reports {@code RESULT=PASS} only if every scenario passes.</p>
 *
 * <p>Enabled with {@code -Dbadghost.selftest=true}, excluded from the released jar, run by
 * {@code scripts/selftest.sh}.</p>
 */
public final class SelfTest {
    private SelfTest() {}

    public static final String PROPERTY = "badghost.selftest";
    private static final String TAG = "BADGHOST-SELFTEST";
    private static final Logger LOGGER = LoggerFactory.getLogger(TAG);

    /** Well clear of terrain, so the scenario is exactly what is built and nothing else. */
    private static final int OX = 64;
    private static final int FLOOR_Y = 200;
    private static final int OZ = 64;

    private static final int SETTLE_TICKS = 60;
    private static final int WATCH_LIMIT_TICKS = 600;

    /** One end-to-end case: a geometry, a mode and the block that must vanish. */
    private record Scenario(String name, PlanMode mode, BlockPos target, Consumer<ClientPacketListener> build) {}

    /** The ceiling target sits above head height; VERTICAL_FAST must plan the piston below it. */
    private static final BlockPos CEILING_TARGET = new BlockPos(OX, FLOOR_Y + 3, OZ);

    private static final List<Scenario> SCENARIOS = List.of(
            new Scenario("vertical-floor", PlanMode.VERTICAL_FAST, new BlockPos(OX, FLOOR_Y, OZ),
                    SelfTest::buildFloor),
            new Scenario("all-direction-sideways", PlanMode.ALL_DIRECTION, new BlockPos(OX, FLOOR_Y, OZ),
                    SelfTest::buildSideways),
            new Scenario("nether-roof-ceiling", PlanMode.VERTICAL_FAST, CEILING_TARGET,
                    SelfTest::buildCeiling));

    private enum Phase { WAIT_WORLD, EQUIP, BUILD, ARM, WATCH, NEXT, DONE }

    private static Phase phase = Phase.WAIT_WORLD;
    private static int scenarioIndex;
    private static int ticks;
    private static int watchTicks;
    private static String lastState = "";
    private static boolean offhandChecked;
    private static boolean allPassed = true;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (phase == Phase.DONE || ClientContext.isInvalid()) {
            return;
        }
        LocalPlayer player = ClientContext.getPlayer();
        ClientLevel level = ClientContext.getLevel();
        ClientPacketListener connection = player == null ? null : player.connection;
        if (player == null || level == null || connection == null) {
            return;
        }

        ticks++;
        switch (phase) {
            case WAIT_WORLD -> after(SETTLE_TICKS, () -> {
                equip(connection);
                phase = Phase.EQUIP;
            });
            case EQUIP -> after(SETTLE_TICKS, () -> {
                startScenario(connection);
                phase = Phase.BUILD;
            });
            case BUILD -> after(SETTLE_TICKS, () -> arm(level));
            case ARM -> phase = Phase.WATCH;
            case WATCH -> watch(level);
            case NEXT -> after(SETTLE_TICKS, () -> {
                scenarioIndex++;
                if (scenarioIndex >= SCENARIOS.size()) {
                    finishAll();
                } else {
                    startScenario(connection);
                    phase = Phase.BUILD;
                }
            });
            case DONE -> { }
        }
    }

    private static void after(int settle, Runnable action) {
        if (ticks > settle) {
            ticks = 0;
            action.run();
        }
    }

    // -- setup --

    /** Exactly the documented requirements, nothing more. */
    private static void equip(ClientPacketListener connection) {
        LOGGER.info("{}: equipping requirements", TAG);
        connection.sendCommand("gamemode creative");
        connection.sendCommand("clear @s");
        connection.sendCommand("give @s minecraft:netherite_pickaxe 1");
        connection.sendCommand("give @s minecraft:piston 64");
        connection.sendCommand("give @s minecraft:redstone_torch 64");
        connection.sendCommand("give @s minecraft:slime_block 64");
        // `enchant @s` only enchants the HELD item, so force the pickaxe (slot 0 after a clear)
        // into the main hand first; otherwise a stale selected slot leaves the pickaxe plain.
        InventoryHelper.setSelectedSlot(0);
        connection.sendCommand("enchant @s minecraft:efficiency 5");
        // Clear any leftover effect first: re-issuing an equal one is refused as "already have
        // something as strong". Haste III leaves margin over the speed-45 threshold.
        connection.sendCommand("effect clear @s");
        connection.sendCommand("effect give @s minecraft:haste 1000000 2 true");
        connection.sendCommand("gamemode survival");
    }

    private static void startScenario(ClientPacketListener connection) {
        Scenario s = current();
        LOGGER.info("{}: --- scenario '{}' ({}) target {} ---", TAG, s.name(), s.mode(), s.target());
        watchTicks = 0;
        lastState = "";
        connection.sendCommand("gamemode creative");
        s.build().accept(connection);

        // Once, in the first scenario: reproduce the reported bug by leaving pistons only in the
        // off hand, and assert they are still counted.
        if (!offhandChecked) {
            connection.sendCommand("clear @s minecraft:piston");
            connection.sendCommand("item replace entity @s weapon.offhand with minecraft:piston 64");
        }
        connection.sendCommand("gamemode survival");
    }

    /** Bedrock flush with a stone floor: the plain VERTICAL_FAST case. */
    private static void buildFloor(ClientPacketListener c) {
        clearAndFloor(c);
        BlockPos t = current().target();
        c.sendCommand(setblock(t, "minecraft:bedrock"));
        standWest(c);
    }

    /**
     * Bedrock with both vertical faces blocked (floor below, stone above), open on the sides.
     * VERTICAL_FAST cannot plan this; ALL_DIRECTION must place the piston sideways, which drives
     * the ROTATE state, the held faked yaw and the mixin.
     */
    private static void buildSideways(ClientPacketListener c) {
        clearAndFloor(c);
        BlockPos t = current().target();
        c.sendCommand(setblock(t, "minecraft:bedrock"));
        c.sendCommand(setblock(t.above(), "minecraft:stone"));
        standWest(c);
    }

    /**
     * The nether-roof case: bedrock overhead with solid rock above it (the roof), open air
     * below. VERTICAL_FAST must put the piston underneath and push up — the mirror of the floor
     * case, and the primary reason a bedrock miner exists.
     */
    private static void buildCeiling(ClientPacketListener c) {
        clearAndFloor(c);
        BlockPos t = current().target();
        c.sendCommand(setblock(t, "minecraft:bedrock"));
        c.sendCommand(setblock(t.above(), "minecraft:stone"));
        standWest(c);
    }

    private static void clearAndFloor(ClientPacketListener c) {
        c.sendCommand(String.format("fill %d %d %d %d %d %d minecraft:air",
                OX - 6, FLOOR_Y, OZ - 6, OX + 6, FLOOR_Y + 8, OZ + 6));
        c.sendCommand(String.format("fill %d %d %d %d %d %d minecraft:stone",
                OX - 6, FLOOR_Y - 1, OZ - 6, OX + 6, FLOOR_Y - 1, OZ + 6));
    }

    /** Feet on the floor, two blocks east of the target, looking at it. Airborne = 1/5 speed. */
    private static void standWest(ClientPacketListener c) {
        c.sendCommand(String.format("tp @s %d %d %d -90 0", OX + 2, FLOOR_Y, OZ));
    }

    private static String setblock(BlockPos p, String block) {
        return String.format("setblock %d %d %d %s", p.getX(), p.getY(), p.getZ(), block);
    }

    // -- run --

    private static void arm(ClientLevel level) {
        Scenario s = current();
        BlockPos target = s.target();

        if (!level.getBlockState(target).is(Blocks.BEDROCK)) {
            fail("scenario setup failed, target is not bedrock");
            return;
        }
        if (!offhandChecked) {
            int offhandPistons = InventoryHelper.countItem(Items.PISTON);
            LOGGER.info("{}: piston count with a full stack in the off hand = {}", TAG, offhandPistons);
            if (offhandPistons < MinerRequirements.PISTONS_NEEDED) {
                fail("off-hand pistons were not counted (got " + offhandPistons + ")");
                return;
            }
            offhandChecked = true;
        }

        BadghostConfig.PLAN_MODE.set(s.mode());

        LocalPlayer p = ClientContext.getPlayer();
        float progress = Blocks.PISTON.defaultBlockState().getDestroyProgress(p, level, target);
        LOGGER.info("{}: held={} onGround={} pistonDestroyProgress={} (need >= 1.0)",
                TAG, p.getMainHandItem().getItem(), p.onGround(), progress);

        var missing = MinerRequirements.describeMissing(target);
        if (missing != null) {
            fail("requirements not met: " + missing.getString());
            return;
        }

        ModState.setAutomationEnabled(true);
        if (!AutomationEngine.requestTarget(target)) {
            fail("target was rejected despite requirements being met");
            return;
        }
        LOGGER.info("{}: queued, watching", TAG);
        phase = Phase.ARM;
    }

    private static void watch(ClientLevel level) {
        watchTicks++;

        MinerTask task = AutomationEngine.getCurrentTask();
        if (task != null && !task.getState().name().equals(lastState)) {
            lastState = task.getState().name();
            LOGGER.info("{}: state={}", TAG, lastState);
        }

        if (!level.getBlockState(current().target()).is(Blocks.BEDROCK)) {
            LOGGER.info("{}: scenario '{}' PASS — bedrock removed", TAG, current().name());
            ModState.setAutomationEnabled(false);
            phase = Phase.NEXT;
            return;
        }
        if (AutomationEngine.getQueueSize() == 0) {
            fail("queue drained but the bedrock is still there");
            return;
        }
        if (watchTicks > WATCH_LIMIT_TICKS) {
            fail("timed out after " + watchTicks + " ticks in state " + lastState);
        }
    }

    // -- verdict --

    private static Scenario current() {
        return SCENARIOS.get(scenarioIndex);
    }

    private static void fail(String detail) {
        allPassed = false;
        ModState.setAutomationEnabled(false);
        phase = Phase.DONE;
        LOGGER.info("{}: RESULT=FAIL scenario '{}': {}", TAG, current().name(), detail);
    }

    private static void finishAll() {
        phase = Phase.DONE;
        ModState.setAutomationEnabled(false);
        LOGGER.info("{}: RESULT={} all {} scenarios",
                TAG, allPassed ? "PASS" : "FAIL", SCENARIOS.size());
    }
}
