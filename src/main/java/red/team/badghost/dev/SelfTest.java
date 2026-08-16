// Copyright (C) 2026 RedTeam. Licensed under GNU AGPLv3.
package red.team.badghost.dev;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
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
import red.team.badghost.visuals.GhostBlockRegistry;
import red.team.badghost.visuals.GhostPhysics;
import red.team.badghost.visuals.NegativeEffectFilter;

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

    /**
     * One end-to-end case: a geometry, a mode and the block that must vanish. When
     * {@code abortMidway} is set the miner is disarmed while it is working instead, and the case
     * passes only if it collected everything it had placed.
     */
    private record Scenario(String name, PlanMode mode, BlockPos target,
                            Consumer<ClientPacketListener> build, boolean abortMidway) {
        Scenario(String name, PlanMode mode, BlockPos target, Consumer<ClientPacketListener> build) {
            this(name, mode, target, build, false);
        }
    }

    /** Blocks the mod places; none may survive an abort. */
    private static final List<net.minecraft.world.level.block.Block> MECHANISM = List.of(
            Blocks.PISTON, Blocks.STICKY_PISTON, Blocks.MOVING_PISTON,
            Blocks.REDSTONE_TORCH, Blocks.REDSTONE_WALL_TORCH, Blocks.SLIME_BLOCK);

    /** Where the physics check fakes its ghost block: on the floor, clear of the mining area. */
    private static final BlockPos GHOST_POS = new BlockPos(OX + 4, FLOOR_Y, OZ + 4);

    /** The ceiling target sits above head height; VERTICAL_FAST must plan the piston below it. */
    private static final BlockPos CEILING_TARGET = new BlockPos(OX, FLOOR_Y + 3, OZ);

    private static final List<Scenario> SCENARIOS = List.of(
            new Scenario("vertical-floor", PlanMode.VERTICAL_FAST, new BlockPos(OX, FLOOR_Y, OZ),
                    SelfTest::buildFloor),
            new Scenario("all-direction-sideways", PlanMode.ALL_DIRECTION, new BlockPos(OX, FLOOR_Y, OZ),
                    SelfTest::buildSideways),
            new Scenario("nether-roof-ceiling", PlanMode.VERTICAL_FAST, CEILING_TARGET,
                    SelfTest::buildCeiling),
            new Scenario("abort-leaves-nothing", PlanMode.VERTICAL_FAST, new BlockPos(OX, FLOOR_Y, OZ),
                    SelfTest::buildFloor, true));

    private enum Phase { WAIT_WORLD, EQUIP, BUILD, ARM, WATCH, NEXT, EFFECTS, EFFECTS_CHECK, PHYSICS, PHYSICS_FRICTION, PHYSICS_BOUNCE, DONE }

    private static Phase phase = Phase.WAIT_WORLD;
    private static int scenarioIndex;
    private static int ticks;
    private static int watchTicks;
    private static String lastState = "";
    private static boolean offhandChecked;
    private static boolean allPassed = true;
    private static boolean abortIssued;

    /** Every check that actually ran and passed, so the verdict can name them. */
    private static final List<String> passed = new java.util.ArrayList<>();

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
                    startEffectCheck(connection);
                    phase = Phase.EFFECTS;
                } else {
                    startScenario(connection);
                    phase = Phase.BUILD;
                }
            });
            case EFFECTS -> after(SETTLE_TICKS, () -> phase = Phase.EFFECTS_CHECK);
            case EFFECTS_CHECK -> checkEffectsSuppressed();
            case PHYSICS -> after(SETTLE_TICKS, () -> phase = Phase.PHYSICS_FRICTION);
            case PHYSICS_FRICTION -> checkFriction(connection);
            case PHYSICS_BOUNCE -> after(SETTLE_TICKS, SelfTest::checkBounce);
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
        abortIssued = false;
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

        if (current().abortMidway()) {
            watchAbort(level, task);
            return;
        }

        if (!level.getBlockState(current().target()).is(Blocks.BEDROCK)) {
            LOGGER.info("{}: scenario '{}' PASS — bedrock removed", TAG, current().name());
            passed.add(current().name());
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

    /**
     * Disarms the miner once it has actually built something, then requires the world to be
     * clean: an abort must roll the mechanism back, not abandon it next to the target.
     */
    private static void watchAbort(ClientLevel level, MinerTask task) {
        if (!abortIssued) {
            // Wait until hardware is really in the world before pulling the plug.
            if (task == null || countMechanismBlocks(level) == 0) {
                if (watchTicks > WATCH_LIMIT_TICKS) {
                    fail("nothing was ever placed, cannot test the abort path");
                }
                return;
            }
            LOGGER.info("{}: {} mechanism block(s) placed, disarming mid-operation",
                    TAG, countMechanismBlocks(level));
            abortIssued = true;
            ModState.setAutomationEnabled(false);
            return;
        }

        if (AutomationEngine.getQueueSize() > 0) {
            if (watchTicks > WATCH_LIMIT_TICKS) {
                fail("queue did not drain after the abort");
            }
            return;
        }

        int leftovers = countMechanismBlocks(level);
        if (leftovers > 0) {
            fail(leftovers + " mechanism block(s) left in the world after the abort");
            return;
        }
        LOGGER.info("{}: scenario '{}' PASS — abort left nothing behind", TAG, current().name());
        passed.add(current().name());
        phase = Phase.NEXT;
    }

    /** Counts the mod's own hardware around the target. */
    private static int countMechanismBlocks(ClientLevel level) {
        BlockPos target = current().target();
        int found = 0;
        for (BlockPos pos : BlockPos.betweenClosed(target.offset(-3, -3, -3), target.offset(3, 3, 3))) {
            if (MECHANISM.contains(level.getBlockState(pos).getBlock())) {
                found++;
            }
        }
        return found;
    }

    /**
     * Applies real nausea and blindness with the suppression on, so the next phase can check the
     * suppression actually ran rather than assuming it would.
     */
    private static void startEffectCheck(ClientPacketListener connection) {
        LOGGER.info("{}: --- checking negative-effect suppression ---", TAG);
        NegativeEffectFilter.reset();
        BadghostConfig.DISABLE_NEGATIVES.set(true);
        connection.sendCommand("effect clear @s");
        connection.sendCommand("effect give @s minecraft:nausea 30 0 true");
        connection.sendCommand("effect give @s minecraft:blindness 30 0 true");
    }

    private static void checkEffectsSuppressed() {
        boolean nausea = NegativeEffectFilter.has(MobEffects.CONFUSION);
        boolean blind = NegativeEffectFilter.has(MobEffects.BLINDNESS);
        int nauseaHits = NegativeEffectFilter.nauseaSuppressedCount();
        int fogHits = NegativeEffectFilter.fogSuppressedCount();
        LOGGER.info("{}: effects active nausea={} blindness={}; suppressed nausea={} fog={}",
                TAG, nausea, blind, nauseaHits, fogHits);

        if (!nausea || !blind) {
            fail("the effects were not applied, so suppression could not be judged");
            return;
        }
        if (nauseaHits == 0) {
            fail("nausea is active but its overlay was never suppressed");
            return;
        }
        if (fogHits == 0) {
            fail("blindness is active but the fog was never pushed back");
            return;
        }
        LOGGER.info("{}: negative-effect suppression PASS — nausea overlay and blindness fog both suppressed", TAG);
        passed.add("negative-effects");
        BadghostConfig.DISABLE_NEGATIVES.set(false);
        startPhysicsCheck();
        phase = Phase.PHYSICS;
    }

    /** Fakes a ghost block on the floor and stands the player on it, both properties enabled. */
    private static void startPhysicsCheck() {
        LOGGER.info("{}: --- checking ghost-block ice and slime ---", TAG);
        GhostPhysics.reset();
        BadghostConfig.FROZEN_SLIPPERY.set(true);
        BadghostConfig.BOUNCY.set(true);

        ClientLevel level = ClientContext.getLevel();
        LocalPlayer player = ClientContext.getPlayer();
        if (level == null || player == null) {
            fail("no world for the physics check");
            return;
        }
        // Registered exactly the way the ghost-block key does it, so the mixins see what they
        // would see in normal play.
        BlockState ghost = Blocks.BEDROCK.defaultBlockState();
        BlockState covered = level.getBlockState(GHOST_POS);
        GhostBlockRegistry.add(GHOST_POS, ghost, covered, BadghostConfig.GHOST_LIMIT.get());
        level.setBlock(GHOST_POS, ghost, net.minecraft.world.level.block.Block.UPDATE_ALL);

        player.connection.sendCommand(String.format("tp @s %d %d %d -90 0",
                GHOST_POS.getX(), GHOST_POS.getY() + 1, GHOST_POS.getZ()));
    }

    /** Standing on the ghost must report ice friction; then drop the player to test the bounce. */
    private static void checkFriction(ClientPacketListener connection) {
        int hits = GhostPhysics.frictionAppliedCount();
        LOGGER.info("{}: ghost friction applied {} time(s)", TAG, hits);
        if (hits == 0) {
            fail("standing on a slippery ghost block never reported ice friction");
            return;
        }
        LOGGER.info("{}: ice PASS — ghost block reports friction {}", TAG, GhostPhysics.slipperyFriction());
        passed.add("ghost-ice");
        // Drop from a height so the landing goes through the fall handler the bounce redirects.
        connection.sendCommand(String.format("tp @s %d %d %d -90 0",
                GHOST_POS.getX(), GHOST_POS.getY() + 6, GHOST_POS.getZ()));
        phase = Phase.PHYSICS_BOUNCE;
    }

    private static void checkBounce() {
        int hits = GhostPhysics.bounceAppliedCount();
        LOGGER.info("{}: ghost bounce applied {} time(s)", TAG, hits);
        if (hits == 0) {
            fail("landing on a bouncy ghost block never bounced");
            return;
        }
        LOGGER.info("{}: slime PASS — landing on a ghost block bounced", TAG);
        passed.add("ghost-slime");
        BadghostConfig.FROZEN_SLIPPERY.set(false);
        BadghostConfig.BOUNCY.set(false);
        finishAll();
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

    /** Everything this harness claims to verify. A run that skips any of it is not a pass. */
    private static final List<String> EXPECTED_CHECKS = List.of(
            "vertical-floor", "all-direction-sideways", "nether-roof-ceiling",
            "abort-leaves-nothing", "negative-effects", "ghost-ice", "ghost-slime");

    private static void finishAll() {
        phase = Phase.DONE;
        ModState.setAutomationEnabled(false);

        List<String> skipped = new java.util.ArrayList<>(EXPECTED_CHECKS);
        skipped.removeAll(passed);
        if (!skipped.isEmpty()) {
            // A check that never ran must not hide behind the ones that did.
            allPassed = false;
            LOGGER.info("{}: RESULT=FAIL {} of {} checks never ran: {}",
                    TAG, skipped.size(), EXPECTED_CHECKS.size(), skipped);
            return;
        }
        LOGGER.info("{}: RESULT={} all {} checks: {}",
                TAG, allPassed ? "PASS" : "FAIL", passed.size(), passed);
    }
}
