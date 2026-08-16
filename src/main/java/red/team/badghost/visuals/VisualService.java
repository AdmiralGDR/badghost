// Copyright (C) 2026 RedTeam. Licensed under GNU AGPLv3.
package red.team.badghost.visuals;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.jetbrains.annotations.Nullable;
import red.team.badghost.config.BadghostConfig;
import red.team.badghost.core.ClientContext;
import red.team.badghost.core.ModState;

import java.util.List;

/** Client-only fake blocks, with a particle aura so they can be told apart from real ones. */
public final class VisualService {
    private VisualService() {}

    private static final double PLACE_RANGE_SQR = 36.0D;
    private static final double AURA_RANGE = 32.0D;

    /** Ticks between "ghost budget full" notices. */
    private static final int LIMIT_WARN_TICKS = 60;

    private static int limitWarnCooldown;


    private static String cachedBlockId;
    @Nullable
    private static Block cachedBlock;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (ClientContext.isInvalid()) {
            GhostBlockRegistry.clear();
            return;
        }

        Minecraft mc = ClientContext.getClient();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null) {
            return;
        }

        if (limitWarnCooldown > 0) {
            limitWarnCooldown--;
        }

        // Undo, clear and pruning work off the registry alone. They must keep working even when
        // the configured block id is unusable, or a typo would strand every ghost already placed
        // with no way to take it back.
        while (KeyBindings.UNDO_GHOST_BLOCK.consumeClick()) {
            undoLast();
        }
        while (KeyBindings.CLEAR_GHOST_BLOCKS.consumeClick()) {
            clearAll();
        }

        Block ghostBlock = resolveGhostBlock();
        if (KeyBindings.PLACE_GHOST_BLOCK.isDown()) {
            if (ghostBlock == null) {
                warnUnknownBlock(player);
            } else {
                placeGhostBlock(mc, player, level, ghostBlock);
            }
        }
        if (level.getGameTime() % 4 == 0) {
            spawnAura(player, level);
        }
    }

    /**
     * Resolves the configured block id. A typo must not take the client tick down with it, so a
     * malformed or unknown id simply disables the feature until it is corrected.
     */
    @Nullable
    private static Block resolveGhostBlock() {
        String id = BadghostConfig.GHOST_BLOCK.get();
        if (id.equals(cachedBlockId)) {
            return cachedBlock;
        }
        cachedBlockId = id;

        ResourceLocation location = ResourceLocation.tryParse(id);
        Block resolved = location == null ? null : BuiltInRegistries.BLOCK.getOptional(location).orElse(null);
        cachedBlock = resolved == Blocks.AIR ? null : resolved;
        return cachedBlock;
    }

    private static void placeGhostBlock(Minecraft mc, LocalPlayer player, ClientLevel level, Block ghostBlock) {
        BlockPos targetPos = null;
        if (mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.BLOCK) {
            BlockHitResult hit = (BlockHitResult) mc.hitResult;
            targetPos = hit.getBlockPos().relative(hit.getDirection());
        }
        if (targetPos == null || targetPos.distToCenterSqr(player.position()) > PLACE_RANGE_SQR) {
            targetPos = player.blockPosition().below();
        }

        BlockState existing = level.getBlockState(targetPos);
        if (existing.is(ghostBlock)) {
            return;
        }
        if (!existing.canBeReplaced()) {
            // Holding the key against a solid wall otherwise looks like the feature is broken.
            warnOccupied(player);
            return;
        }
        BlockState ghostState = ghostBlock.defaultBlockState();
        // Record what was covered before faking over it, so removal can put it back exactly.
        if (!GhostBlockRegistry.add(targetPos, ghostState, existing, BadghostConfig.GHOST_LIMIT.get())) {
            warnLimitReached(player);
            return;
        }
        level.setBlock(targetPos, ghostState, Block.UPDATE_ALL);
    }

    private static void spawnAura(LocalPlayer player, ClientLevel level) {
        // The registry drops whatever the server has since corrected away; this only draws.
        GhostBlockRegistry.visitLive(level::getBlockState, pos -> {
            if (!pos.closerToCenterThan(player.position(), AURA_RANGE)) {
                return;
            }
            double x = pos.getX() + level.random.nextDouble();
            double y = pos.getY() + 1.0D + level.random.nextDouble() * 0.5D;
            double z = pos.getZ() + level.random.nextDouble();
            level.addParticle(ParticleTypes.ENCHANT, x, y, z, 0.0D, 0.1D, 0.0D);
        });
    }

    /** Tells the player their configured block id is unusable, rather than failing silently. */
    private static void warnUnknownBlock(LocalPlayer player) {
        if (limitWarnCooldown > 0) {
            return;
        }
        limitWarnCooldown = LIMIT_WARN_TICKS;
        player.displayClientMessage(
                Component.translatable("badghost.message.unknown_ghost_block",
                        BadghostConfig.GHOST_BLOCK.get()), true);
    }

    /** Says once per cooldown that there is something solid in the way. */
    private static void warnOccupied(LocalPlayer player) {
        if (limitWarnCooldown > 0) {
            return;
        }
        limitWarnCooldown = LIMIT_WARN_TICKS;
        player.displayClientMessage(Component.translatable("badghost.message.spot_occupied"), true);
    }

    /** Says once per cooldown that the ghost budget is full, rather than every tick. */
    private static void warnLimitReached(LocalPlayer player) {
        if (limitWarnCooldown > 0) {
            return;
        }
        limitWarnCooldown = LIMIT_WARN_TICKS;
        player.displayClientMessage(
                Component.translatable("badghost.message.ghost_limit", GhostBlockRegistry.size()), true);
    }

    /** Removes the newest ghost block, restoring what it covered, and says what it did. */
    public static void undoLast() {
        ClientLevel level = ClientContext.getLevel();
        LocalPlayer player = ClientContext.getPlayer();
        if (level == null || player == null) {
            return;
        }
        BlockPos pos = GhostBlockRegistry.lastPlaced();
        if (pos == null) {
            // Pressing a key and getting nothing back reads as a broken key, so say so.
            player.displayClientMessage(Component.translatable("badghost.message.nothing_to_undo"), true);
            return;
        }
        BlockState original = GhostBlockRegistry.remove(pos);
        if (original != null) {
            level.setBlock(pos, original, Block.UPDATE_ALL);
        }
        player.displayClientMessage(
                Component.translatable("badghost.message.undone", GhostBlockRegistry.size()), true);
    }

    /** Removes every ghost block, restoring what each covered, and reports the count. */
    public static void clearAll() {
        ClientLevel level = ClientContext.getLevel();
        LocalPlayer player = ClientContext.getPlayer();
        if (level == null) {
            GhostBlockRegistry.clear();
            return;
        }
        int removed = 0;
        for (BlockPos pos : List.copyOf(GhostBlockRegistry.positions())) {
            BlockState original = GhostBlockRegistry.remove(pos);
            if (original != null) {
                level.setBlock(pos, original, Block.UPDATE_ALL);
            }
            removed++;
        }
        if (player != null) {
            player.displayClientMessage(Component.translatable(
                    removed == 0 ? "badghost.message.nothing_to_clear" : "badghost.message.cleared",
                    removed), true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        // Also fires on the integrated server thread; ghost blocks are client-only state.
        if (!event.getSide().isClient() || !ClientContext.isClientThread()) {
            return;
        }
        if (!ClientContext.isLocalPlayer(event.getEntity()) || ClientContext.isInvalid()) {
            return;
        }
        // The miner breaks its own scaffolding through the same vanilla call; cancelling that
        // here would strand the mechanism in the world.
        if (ModState.isInternalBreak()) {
            return;
        }

        BlockPos pos = event.getPos();
        BlockState original = GhostBlockRegistry.remove(pos);
        if (original == null) {
            return;
        }
        ClientLevel level = ClientContext.getLevel();
        if (level != null) {
            // Restore what the ghost covered rather than punching a hole in the client's world.
            level.setBlock(pos, original, Block.UPDATE_ALL);
        }
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        double offset = BadghostConfig.MODEL_OFFSET.get();
        if (offset != 0.0D) {
            event.getPoseStack().pushPose();
            event.getPoseStack().translate(0.0D, offset, 0.0D);
        }
    }

    @SubscribeEvent
    public static void onRenderPlayerPost(RenderPlayerEvent.Post event) {
        double offset = BadghostConfig.MODEL_OFFSET.get();
        if (offset != 0.0D) {
            event.getPoseStack().popPose();
        }
    }
}
