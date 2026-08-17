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
import red.team.badghost.visuals.template.GhostTemplate;

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

        // Drained every tick and collapsed to one action, so a burst of queued presses cannot
        // stack up several shapes in a single tick.
        boolean clicked = false;
        while (KeyBindings.PLACE_GHOST_BLOCK.consumeClick()) {
            clicked = true;
        }
        GhostTemplate shape = BadghostConfig.TEMPLATE_SHAPE.get();
        // One cell keeps painting while the key is held, which is what it has always done. A shape
        // goes down once per press: laying it again every tick would make each tick its own group
        // and leave undo taking back a single tick's worth of a shape you placed once.
        boolean triggered = shape == GhostTemplate.SINGLE
                ? KeyBindings.PLACE_GHOST_BLOCK.isDown()
                : clicked;

        if (triggered) {
            placeShape(shape);
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

    /**
     * Lays out {@code shape} where the player is looking, exactly as the key does.
     *
     * <p>Public so the self-test can exercise the real path rather than a copy of it: a harness
     * that reimplements what it checks proves only that the copy works.</p>
     */
    public static void placeShape(GhostTemplate shape) {
        Minecraft mc = ClientContext.getClient();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null) {
            return;
        }
        Block ghostBlock = resolveGhostBlock();
        if (ghostBlock == null) {
            warnUnknownBlock(player);
            return;
        }
        placeTemplate(mc, player, level, ghostBlock, shape);
    }

    /** Where a shape starts: the face being looked at, or underfoot when nothing is in range. */
    private static BlockPos originFor(Minecraft mc, LocalPlayer player) {
        BlockPos origin = null;
        if (mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.BLOCK) {
            BlockHitResult hit = (BlockHitResult) mc.hitResult;
            origin = hit.getBlockPos().relative(hit.getDirection());
        }
        if (origin == null || origin.distToCenterSqr(player.position()) > PLACE_RANGE_SQR) {
            origin = player.blockPosition().below();
        }
        return origin;
    }

    /**
     * Lays out one shape and says how much of it went down.
     *
     * <p>Every cell of the shape belongs to one group, so a single undo takes the whole thing back.
     * Cells that are in the way are stepped over rather than abandoning the shape — half a bridge
     * is more use than none — but the count that could not be placed is reported, because a shape
     * that quietly comes out smaller than asked for looks like a bug.</p>
     */
    private static void placeTemplate(Minecraft mc, LocalPlayer player, ClientLevel level,
                                      Block ghostBlock, GhostTemplate shape) {
        BlockState ghostState = ghostBlock.defaultBlockState();
        int limit = BadghostConfig.GHOST_LIMIT.get();
        int batch = GhostBlockRegistry.newBatch();

        int placed = 0;
        int blocked = 0;
        boolean limitReached = false;
        for (BlockPos pos : shape.positions(originFor(mc, player), player.getDirection(),
                BadghostConfig.TEMPLATE_SIZE.get())) {
            BlockState existing = level.getBlockState(pos);
            if (existing.is(ghostBlock)) {
                continue;
            }
            if (!existing.canBeReplaced()) {
                blocked++;
                continue;
            }
            // Record what was covered before faking over it, so removal can put it back exactly.
            if (!GhostBlockRegistry.add(pos, ghostState, existing, limit, batch)) {
                limitReached = true;
                break;
            }
            level.setBlock(pos, ghostState, Block.UPDATE_ALL);
            placed++;
        }

        if (shape == GhostTemplate.SINGLE) {
            // Painting cell by cell: the old messages are the right ones, and a count of one
            // would be noise on every tick the key is held.
            if (limitReached) {
                warnLimitReached(player);
            } else if (blocked > 0) {
                warnOccupied(player);
            }
            return;
        }
        reportShape(player, shape, placed, blocked, limitReached);
    }

    /** One line saying what a shape managed, whether or not it managed all of it. */
    private static void reportShape(LocalPlayer player, GhostTemplate shape,
                                    int placed, int blocked, boolean limitReached) {
        Component name = Component.translatable(shape.translationKey());
        if (limitReached) {
            player.displayClientMessage(Component.translatable(
                    "badghost.message.template_limited", name, placed, GhostBlockRegistry.size()), true);
        } else if (blocked > 0) {
            player.displayClientMessage(Component.translatable(
                    "badghost.message.template_partial", name, placed, blocked), true);
        } else if (placed > 0) {
            player.displayClientMessage(Component.translatable(
                    "badghost.message.template_placed", name, placed), true);
        } else {
            // Nothing at all went down; without this the key looks broken.
            player.displayClientMessage(Component.translatable(
                    "badghost.message.template_nothing", name), true);
        }
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
        // A whole shape came down as one action, so it goes back as one: undoing a wall a block at
        // a time would be forty presses to take back one.
        List<BlockPos> group = GhostBlockRegistry.lastBatch();
        if (group.isEmpty()) {
            // Pressing a key and getting nothing back reads as a broken key, so say so.
            player.displayClientMessage(Component.translatable("badghost.message.nothing_to_undo"), true);
            return;
        }
        for (BlockPos pos : group) {
            BlockState original = GhostBlockRegistry.remove(pos);
            if (original != null) {
                level.setBlock(pos, original, Block.UPDATE_ALL);
            }
        }
        player.displayClientMessage(Component.translatable(
                "badghost.message.undone_group", group.size(), GhostBlockRegistry.size()), true);
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
