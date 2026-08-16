// Copyright (C) 2026 RedTeam. Licensed under GNU AGPLv3.
package red.team.badghost.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import red.team.badghost.config.BadghostConfig;
import red.team.badghost.core.ClientContext;

/** Hotbar and inventory manipulation for the miner. */
public final class InventoryHelper {
    private InventoryHelper() {}

    public static final int NO_SLOT = -1;

    /** {@code Inventory} index of the off-hand stack, and the SWAP button that targets it. */
    public static final int OFFHAND_INVENTORY_INDEX = 40;

    /** Hotbar plus backpack. Above this sit the armour slots and the off hand. */
    private static final int STORAGE_SLOTS = 36;

    /**
     * A block breaks in a single tick exactly when its destroy progress reaches 1.0 in that
     * tick — this is the vanilla insta-mine boundary in {@code MultiPlayerGameMode}. A piston
     * has hardness 1.5, so progress is mining speed / 45; that makes this threshold equivalent
     * to the upstream mod's "speed above 45" gate (45/45 = 1.0), which separates a bare
     * Efficiency V pickaxe (35 → 0.78) from Efficiency V with Haste II (49 → 1.09).
     */
    private static final float INSTA_MINE_PROGRESS = 1.0F;

    private static int rememberedHotbarSlot = NO_SLOT;
    private static ItemStack rememberedOffhand = ItemStack.EMPTY;
    private static boolean offhandBorrowed;

    // -- hotbar --

    public static void setSelectedSlot(int slot) {
        LocalPlayer player = ClientContext.getPlayer();
        if (player == null || !Inventory.isHotbarSlot(slot)) {
            return;
        }
        Inventory inventory = player.getInventory();
        if (inventory.selected == slot) {
            return;
        }
        inventory.selected = slot;
        if (player.connection != null) {
            player.connection.send(new ServerboundSetCarriedItemPacket(slot));
        }
    }

    /**
     * Brings the first matching item into the main hand, pulling it out of the backpack with a
     * swap click when it is not already on the hotbar.
     */
    public static boolean switchToAny(Item... items) {
        LocalPlayer player = ClientContext.getPlayer();
        if (player == null || items == null) {
            return false;
        }
        Inventory inventory = player.getInventory();
        for (Item item : items) {
            if (inventory.getSelected().is(item)) {
                return true;
            }
        }
        for (Item item : items) {
            int slot = findSlot(inventory, item);
            if (slot != NO_SLOT) {
                return moveToMainHand(slot);
            }
        }
        return false;
    }

    private static boolean moveToMainHand(int inventorySlot) {
        LocalPlayer player = ClientContext.getPlayer();
        if (player == null) {
            return false;
        }
        if (Inventory.isHotbarSlot(inventorySlot)) {
            setSelectedSlot(inventorySlot);
            return true;
        }
        Inventory inventory = player.getInventory();
        int target = inventory.getSuitableHotbarSlot();
        if (!swap(inventorySlot, target)) {
            return false;
        }
        setSelectedSlot(target);
        return true;
    }

    // -- off hand --

    /**
     * Moves a matching item into the off hand.
     *
     * <p>The decisive tick breaks the torch and the piston and then places a piston, all in one
     * go. The pickaxe has to stay in the main hand for the breaks to be instant, so the piston
     * is placed from the off hand instead of fighting over the selected slot.</p>
     */
    public static boolean switchToOffHand(Item... items) {
        LocalPlayer player = ClientContext.getPlayer();
        if (player == null || items == null) {
            return false;
        }
        Inventory inventory = player.getInventory();
        for (Item item : items) {
            if (player.getOffhandItem().is(item)) {
                return true;
            }
        }
        for (Item item : items) {
            int slot = findSlot(inventory, item);
            if (slot != NO_SLOT) {
                rememberOffhand();
                return swap(slot, OFFHAND_INVENTORY_INDEX);
            }
        }
        return false;
    }

    /**
     * Performs a container swap between an inventory slot and either a hotbar slot or the off
     * hand. Only valid while the survival inventory menu is the open one.
     */
    private static boolean swap(int sourceInventorySlot, int targetInventorySlot) {
        Minecraft mc = ClientContext.getClient();
        LocalPlayer player = mc.player;
        MultiPlayerGameMode gameMode = mc.gameMode;
        if (player == null || gameMode == null) {
            return false;
        }
        // A swap click is addressed to the currently open menu; if a chest or crafting screen
        // is up, the slot ids mean something else entirely.
        if (mc.screen != null || player.containerMenu != player.inventoryMenu) {
            return false;
        }
        int menuSlot = toMenuSlot(sourceInventorySlot);
        if (menuSlot == NO_SLOT) {
            return false;
        }
        gameMode.handleInventoryMouseClick(
                player.inventoryMenu.containerId, menuSlot, targetInventorySlot, ClickType.SWAP, player);
        return true;
    }

    /**
     * Maps an {@code Inventory} index onto its slot id in {@code InventoryMenu}. The hotbar is
     * stored first in the inventory but sits last in the menu, so the two numbering schemes do
     * not line up.
     */
    public static int toMenuSlot(int inventorySlot) {
        if (inventorySlot >= 0 && inventorySlot < Inventory.getSelectionSize()) {
            return InventoryMenu.USE_ROW_SLOT_START + inventorySlot;
        }
        if (inventorySlot >= InventoryMenu.INV_SLOT_START && inventorySlot < InventoryMenu.INV_SLOT_END) {
            return inventorySlot;
        }
        if (inventorySlot == OFFHAND_INVENTORY_INDEX) {
            return InventoryMenu.SHIELD_SLOT;
        }
        return NO_SLOT;
    }

    // -- counting --

    private static int findSlot(Inventory inventory, Item item) {
        for (int i = 0; i < STORAGE_SLOTS; i++) {
            if (inventory.getItem(i).is(item)) {
                return i;
            }
        }
        return NO_SLOT;
    }

    /**
     * Counts an item across hotbar, backpack and the off hand.
     *
     * <p>The off hand matters: this mod places blocks from it, so after one run the pistons
     * live there. Leaving slot 40 out of the count makes the mod report that the very stack it
     * just put in the player's hand does not exist.</p>
     */
    public static int countItem(Item item) {
        LocalPlayer player = ClientContext.getPlayer();
        if (player == null) {
            return 0;
        }
        Inventory inventory = player.getInventory();
        int count = 0;
        for (int i = 0; i < STORAGE_SLOTS; i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        ItemStack offhand = inventory.getItem(OFFHAND_INVENTORY_INDEX);
        if (offhand.is(item)) {
            count += offhand.getCount();
        }
        return count;
    }

    // -- tools --

    /** Whether the item currently held can break a piston within a single tick. */
    public static boolean canInstaMinePiston(BlockPos reference) {
        LocalPlayer player = ClientContext.getPlayer();
        ClientLevel level = ClientContext.getLevel();
        if (player == null || level == null) {
            return false;
        }
        return pistonProgress(player, level, reference) >= INSTA_MINE_PROGRESS;
    }

    /** True if any slot holds a tool fast enough, whether or not it is currently held. */
    public static boolean hasInstaMineTool(BlockPos reference) {
        return findInstaMineSlot(reference) != NO_SLOT;
    }

    /** Brings the fastest usable tool into the main hand. */
    public static boolean equipInstaMineTool(BlockPos reference) {
        int slot = findInstaMineSlot(reference);
        return slot != NO_SLOT && moveToMainHand(slot);
    }

    /**
     * Finds the fastest slot that clears the threshold.
     *
     * <p>Vanilla only computes destroy progress for whatever is in the main hand, so each
     * candidate is parked in the selected slot for the duration of one measurement. Nothing is
     * sent to the server and the original stack goes back by identity, so this is invisible
     * outside this method.</p>
     */
    private static int findInstaMineSlot(BlockPos reference) {
        LocalPlayer player = ClientContext.getPlayer();
        ClientLevel level = ClientContext.getLevel();
        if (player == null || level == null) {
            return NO_SLOT;
        }

        Inventory inventory = player.getInventory();
        int probe = inventory.selected;
        if (!Inventory.isHotbarSlot(probe)) {
            return NO_SLOT;
        }
        ItemStack held = inventory.getItem(probe);

        // With the check waived, any tool will do and the fastest one is the best guess.
        float required = BadghostConfig.SKIP_INSTAMINE_CHECK.get() ? 0F : INSTA_MINE_PROGRESS;

        int best = NO_SLOT;
        float bestProgress = 0F;
        try {
            for (int i = 0; i <= STORAGE_SLOTS; i++) {
                // One past the storage range stands in for the off hand, so a tool parked
                // there is still found and can be pulled back into the main hand.
                int slot = i == STORAGE_SLOTS ? OFFHAND_INVENTORY_INDEX : i;
                // The probe slot is overwritten each iteration, so its own original stack has
                // to come from the saved copy rather than a stale re-read.
                ItemStack candidate = slot == probe ? held : inventory.getItem(slot);
                if (candidate.isEmpty()) {
                    continue;
                }
                inventory.setItem(probe, candidate);
                float progress = pistonProgress(player, level, reference);
                if (progress >= required && progress > bestProgress) {
                    bestProgress = progress;
                    best = slot;
                }
            }
        } finally {
            inventory.setItem(probe, held);
        }
        return best;
    }

    private static float pistonProgress(LocalPlayer player, ClientLevel level, BlockPos reference) {
        BlockState piston = Blocks.PISTON.defaultBlockState();
        BlockPos pos = reference != null ? reference : player.blockPosition();
        return piston.getDestroyProgress(player, level, pos);
    }

    // -- restoring what the player was holding --

    public static void rememberHotbarSlot() {
        if (rememberedHotbarSlot != NO_SLOT) {
            return;
        }
        LocalPlayer player = ClientContext.getPlayer();
        if (player != null) {
            rememberedHotbarSlot = player.getInventory().selected;
        }
    }

    private static void rememberOffhand() {
        if (offhandBorrowed) {
            return;
        }
        LocalPlayer player = ClientContext.getPlayer();
        if (player != null) {
            rememberedOffhand = player.getOffhandItem().copy();
            offhandBorrowed = true;
        }
    }

    /** Puts back the hotbar slot and the off-hand item the player had before the mod ran. */
    public static void restoreHeld() {
        if (offhandBorrowed) {
            restoreOffhand();
            offhandBorrowed = false;
            rememberedOffhand = ItemStack.EMPTY;
        }
        if (rememberedHotbarSlot != NO_SLOT) {
            setSelectedSlot(rememberedHotbarSlot);
            rememberedHotbarSlot = NO_SLOT;
        }
    }

    private static void restoreOffhand() {
        LocalPlayer player = ClientContext.getPlayer();
        if (player == null) {
            return;
        }
        if (rememberedOffhand.isEmpty()) {
            // Nothing was there before: hand whatever the mod parked back to the inventory.
            if (!player.getOffhandItem().isEmpty()) {
                swap(OFFHAND_INVENTORY_INDEX, player.getInventory().getSuitableHotbarSlot());
            }
            return;
        }
        int slot = findSlot(player.getInventory(), rememberedOffhand.getItem());
        if (slot != NO_SLOT) {
            swap(slot, OFFHAND_INVENTORY_INDEX);
        }
    }

    /** Drops all remembered state; used when the world goes away. */
    public static void reset() {
        rememberedHotbarSlot = NO_SLOT;
        rememberedOffhand = ItemStack.EMPTY;
        offhandBorrowed = false;
    }
}
