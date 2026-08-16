// Copyright (C) 2026 RedTeam. Licensed under GNU AGPLv3.
package red.team.badghost.visuals;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
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

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/** Client-only fake blocks, with a particle aura so they can be told apart from real ones. */
public final class VisualService {
    private VisualService() {}

    private static final double PLACE_RANGE_SQR = 36.0D;
    private static final double AURA_RANGE = 32.0D;

    private static final Set<BlockPos> ghostBlocks = new HashSet<>();

    private static String cachedBlockId;
    @Nullable
    private static Block cachedBlock;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (ClientContext.isInvalid()) {
            ghostBlocks.clear();
            return;
        }

        Minecraft mc = ClientContext.getClient();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null) {
            return;
        }

        Block ghostBlock = resolveGhostBlock();
        if (ghostBlock == null) {
            return;
        }

        if (KeyBindings.PLACE_GHOST_BLOCK.isDown()) {
            placeGhostBlock(mc, player, level, ghostBlock);
        }
        if (level.getGameTime() % 4 == 0) {
            spawnAura(player, level, ghostBlock);
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

        if (level.getBlockState(targetPos).is(ghostBlock) || !level.getBlockState(targetPos).canBeReplaced()) {
            return;
        }
        level.setBlock(targetPos, ghostBlock.defaultBlockState(), Block.UPDATE_ALL);
        ghostBlocks.add(targetPos.immutable());
    }

    private static void spawnAura(LocalPlayer player, ClientLevel level, Block ghostBlock) {
        Iterator<BlockPos> it = ghostBlocks.iterator();
        while (it.hasNext()) {
            BlockPos pos = it.next();
            if (!level.getBlockState(pos).is(ghostBlock)) {
                it.remove();
                continue;
            }
            if (!pos.closerToCenterThan(player.position(), AURA_RANGE)) {
                continue;
            }
            double x = pos.getX() + level.random.nextDouble();
            double y = pos.getY() + 1.0D + level.random.nextDouble() * 0.5D;
            double z = pos.getZ() + level.random.nextDouble();
            level.addParticle(ParticleTypes.ENCHANT, x, y, z, 0.0D, 0.1D, 0.0D);
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
        if (!ghostBlocks.remove(pos)) {
            return;
        }
        ClientLevel level = ClientContext.getLevel();
        if (level != null) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
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
