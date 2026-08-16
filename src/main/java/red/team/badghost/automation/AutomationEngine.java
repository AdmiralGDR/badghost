// Copyright (C) 2026 RedTeam. Licensed under GNU AGPLv3.
package red.team.badghost.automation;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.jetbrains.annotations.Nullable;
import red.team.badghost.config.BadghostConfig;
import red.team.badghost.core.ClientContext;
import red.team.badghost.core.ModState;
import red.team.badghost.utils.InventoryHelper;
import red.team.badghost.utils.PlayerLookUtils;
import red.team.badghost.visuals.KeyBindings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Owns the target queue and drives every {@link MinerTask}. */
public final class AutomationEngine {
    private AutomationEngine() {}

    private static final List<MinerTask> tasks = new ArrayList<>(10);
    private static final List<MinerTask> tasksView = Collections.unmodifiableList(tasks);

    /** Reused across scans so the tick loop stays allocation free. */
    private static final BlockPos.MutableBlockPos scanCursor = new BlockPos.MutableBlockPos();
    private static final List<BlockPos> scanHits = new ArrayList<>();

    private static int scanCooldown;

    private static String cachedSupportId;
    private static Block cachedSupportBlock = Blocks.SLIME_BLOCK;

    public static int getQueueSize() {
        return tasks.size();
    }

    @Nullable
    public static MinerTask getCurrentTask() {
        return tasks.isEmpty() ? null : tasks.get(0);
    }

    public static List<MinerTask> getActiveTasks() {
        return tasksView;
    }

    /** Used by the planner so two tasks never fight over the same cell. */
    public static boolean isOccupiedByOther(MinerTask self, BlockPos pos) {
        for (int i = 0; i < tasks.size(); i++) {
            MinerTask task = tasks.get(i);
            if (task != self && task.occupies(pos)) {
                return true;
            }
        }
        return false;
    }

    /** Support block from config, resolved lazily and re-resolved when the setting changes. */
    public static Block resolveSupportBlock() {
        String id = BadghostConfig.SUPPORT_BLOCK.get();
        if (id.equals(cachedSupportId)) {
            return cachedSupportBlock;
        }
        cachedSupportId = id;
        ResourceLocation location = ResourceLocation.tryParse(id);
        Block resolved = location == null ? null : BuiltInRegistries.BLOCK.getOptional(location).orElse(null);
        cachedSupportBlock = resolved == null || resolved == Blocks.AIR ? Blocks.SLIME_BLOCK : resolved;
        return cachedSupportBlock;
    }

    // -- lifecycle --

    @SubscribeEvent
    public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        tasks.clear();
        InventoryHelper.reset();
        ModState.onJoinWorld();
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        tasks.clear();
        PlayerLookUtils.release();
        InventoryHelper.reset();
        ModState.onLeaveWorld();
    }

    // -- tick --

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (ClientContext.isInvalid()) {
            if (!tasks.isEmpty()) {
                stopAndCleanup();
            }
            return;
        }

        PlayerLookUtils.tick();
        handleToggleKey();

        if (!ModState.isAutomationEnabled()) {
            if (!tasks.isEmpty()) {
                stopAndCleanup();
            }
            return;
        }

        if (tasks.isEmpty() && BadghostConfig.AUTO_SCAN_ENABLED.get() && --scanCooldown <= 0) {
            scanCooldown = BadghostConfig.AUTO_SCAN_INTERVAL.get();
            autoScan();
        }

        if (tasks.isEmpty()) {
            return;
        }

        spawnQueueParticles();
        driveTasks();

        if (tasks.isEmpty()) {
            InventoryHelper.restoreHeld();
        }
    }

    private static void handleToggleKey() {
        boolean toggled = false;
        boolean enabled = ModState.isAutomationEnabled();
        while (KeyBindings.TOGGLE_AUTOMATION.consumeClick()) {
            enabled = ModState.toggleAutomation();
            toggled = true;
        }
        if (!toggled) {
            return;
        }
        if (!enabled) {
            message(Component.translatable("badghost.message.disabled"));
            return;
        }
        // Arming is the moment the player wants to know what the miner expects, so the whole
        // checklist goes out rather than a bare "enabled".
        message(Component.translatable("badghost.message.enabled")
                .append(Component.literal(" — "))
                .append(MinerRequirements.describeChecklist(null)));
    }

    private static void driveTasks() {
        // Indexed, because a task may only ever remove itself and the list must stay usable if
        // one of them ends the whole run.
        for (int i = 0; i < tasks.size(); i++) {
            MinerTask task = tasks.get(i);
            task.tick();

            if (!task.isComplete()) {
                continue;
            }
            if (!task.succeeded() && task.getFailure() != null) {
                message(Component.translatable(task.getFailure()));
            }
            tasks.remove(i--);
        }
    }

    private static void stopAndCleanup() {
        tasks.clear();
        PlayerLookUtils.release();
        InventoryHelper.restoreHeld();
    }

    private static void spawnQueueParticles() {
        ClientLevel level = ClientContext.getLevel();
        if (level == null || level.getGameTime() % 4 != 0) {
            return;
        }
        for (int i = 0; i < tasks.size(); i++) {
            BlockPos pos = tasks.get(i).getTarget();
            double x = pos.getX() + level.random.nextDouble();
            double y = pos.getY() + 0.6D + level.random.nextDouble() * 0.5D;
            double z = pos.getZ() + level.random.nextDouble();
            level.addParticle(ParticleTypes.ENCHANT, x, y, z, 0.0D, 0.05D, 0.0D);
        }
    }

    // -- targeting --

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        // Fires on both logical sides; in single player the integrated server would otherwise
        // run this on its own thread and race the client's task list.
        if (!event.getSide().isClient() || !ClientContext.isClientThread()) {
            return;
        }
        if (!ClientContext.isLocalPlayer(event.getEntity())) {
            return;
        }
        // The mod breaks its own scaffolding through the same vanilla call that fires this
        // event; without this it would enqueue its own demolition work.
        if (ModState.isInternalBreak()) {
            return;
        }
        if (ClientContext.isInvalid() || !ModState.isAutomationEnabled()) {
            return;
        }

        BlockPos pos = event.getPos().immutable();
        if (isProtected(pos)) {
            event.setCanceled(true);
            return;
        }
        if (!event.getLevel().getBlockState(pos).is(Blocks.BEDROCK)) {
            return;
        }

        event.setCanceled(true);
        if (enqueue(pos)) {
            LocalPlayer player = ClientContext.getPlayer();
            if (player != null) {
                player.swing(InteractionHand.MAIN_HAND);
            }
        }
    }

    /** Blocks the player from hitting a cell some task is mid-way through using. */
    private static boolean isProtected(BlockPos pos) {
        for (int i = 0; i < tasks.size(); i++) {
            if (tasks.get(i).occupies(pos)) {
                return true;
            }
        }
        return false;
    }

    /** Queues a target the same way a left click would. */
    public static boolean requestTarget(BlockPos pos) {
        return enqueue(pos.immutable());
    }

    private static boolean enqueue(BlockPos pos) {
        LocalPlayer player = ClientContext.getPlayer();
        ClientLevel level = ClientContext.getLevel();
        if (player == null || level == null) {
            return false;
        }
        if (tasks.size() >= BadghostConfig.LIMIT_MAX.get()) {
            return false;
        }
        for (int i = 0; i < tasks.size(); i++) {
            if (tasks.get(i).getTarget().equals(pos)) {
                return false;
            }
        }

        Component missing = checkRequirements(pos);
        if (missing != null) {
            message(missing);
            return false;
        }

        InventoryHelper.rememberHotbarSlot();
        tasks.add(new MinerTask(pos, level.getBlockState(pos)));
        return true;
    }

    /** Returns the reason the miner cannot run, or {@code null} when it can. */
    @Nullable
    private static Component checkRequirements(BlockPos reference) {
        return MinerRequirements.describeMissing(reference);
    }

    /**
     * Queues nearby bedrock without being asked. Off by default: standing on a bedrock floor
     * this would mine the world out from under the player and empty their inventory.
     */
    private static void autoScan() {
        LocalPlayer player = ClientContext.getPlayer();
        ClientLevel level = ClientContext.getLevel();
        if (player == null || level == null) {
            return;
        }
        int limit = BadghostConfig.LIMIT_MAX.get();
        if (tasks.size() >= limit) {
            return;
        }

        int radius = BadghostConfig.AUTO_SCAN_RADIUS.get();
        BlockPos origin = player.blockPosition();

        scanHits.clear();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    scanCursor.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    if (!level.getBlockState(scanCursor).is(Blocks.BEDROCK)) {
                        continue;
                    }
                    if (!player.canInteractWithBlock(scanCursor, 1.0D)) {
                        continue;
                    }
                    if (isQueued(scanCursor)) {
                        continue;
                    }
                    scanHits.add(scanCursor.immutable());
                }
            }
        }
        if (scanHits.isEmpty()) {
            return;
        }

        // Nearest first, so the player sees the mod work outwards from where they stand.
        var eye = player.getEyePosition();
        scanHits.sort(Comparator.comparingDouble(pos -> pos.distToCenterSqr(eye)));
        for (BlockPos pos : scanHits) {
            if (tasks.size() >= limit) {
                break;
            }
            enqueue(pos);
        }
        scanHits.clear();
    }

    private static boolean isQueued(BlockPos pos) {
        for (int i = 0; i < tasks.size(); i++) {
            if (tasks.get(i).getTarget().equals(pos)) {
                return true;
            }
        }
        return false;
    }

    private static void message(Component text) {
        LocalPlayer player = ClientContext.getPlayer();
        if (player != null) {
            player.displayClientMessage(text, true);
        }
    }

    /** Test seam: lets the scan ordering be exercised without a live level. */
    static List<BlockPos> sortByDistance(List<BlockPos> positions, net.minecraft.world.phys.Vec3 eye) {
        positions.sort(Comparator.comparingDouble(pos -> pos.distToCenterSqr(eye)));
        return positions;
    }
}
