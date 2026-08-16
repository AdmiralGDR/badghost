// Copyright (C) 2026 RedTeam. Licensed under GNU AGPLv3.
package red.team.badghost.utils;

import net.minecraft.world.inventory.InventoryMenu;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The hotbar comes first in {@code Inventory} but last in {@code InventoryMenu}. Getting this
 * mapping wrong sends a swap click at the crafting grid instead of the hotbar.
 */
class InventoryHelperTest {

    @Test
    @DisplayName("hotbar slots map to the menu's bottom row")
    void hotbarMapsToUseRow() {
        assertEquals(InventoryMenu.USE_ROW_SLOT_START, InventoryHelper.toMenuSlot(0));
        assertEquals(InventoryMenu.USE_ROW_SLOT_START + 8, InventoryHelper.toMenuSlot(8));
    }

    @Test
    @DisplayName("backpack slots keep their index")
    void backpackMapsOneToOne() {
        assertEquals(9, InventoryHelper.toMenuSlot(9));
        assertEquals(35, InventoryHelper.toMenuSlot(35));
    }

    @Test
    @DisplayName("the off hand maps to the shield slot")
    void offHandMapsToShieldSlot() {
        assertEquals(InventoryMenu.SHIELD_SLOT,
                InventoryHelper.toMenuSlot(InventoryHelper.OFFHAND_INVENTORY_INDEX));
    }

    @Test
    @DisplayName("armour slots and nonsense indices are rejected")
    void unmappedSlotsReturnNoSlot() {
        assertEquals(InventoryHelper.NO_SLOT, InventoryHelper.toMenuSlot(36));
        assertEquals(InventoryHelper.NO_SLOT, InventoryHelper.toMenuSlot(39));
        assertEquals(InventoryHelper.NO_SLOT, InventoryHelper.toMenuSlot(-1));
        assertEquals(InventoryHelper.NO_SLOT, InventoryHelper.toMenuSlot(999));
    }

    @Test
    @DisplayName("no two storage slots collide in the menu")
    void mappingIsInjective() {
        boolean[] seen = new boolean[InventoryMenu.SHIELD_SLOT + 1];
        for (int i = 0; i < 36; i++) {
            int menuSlot = InventoryHelper.toMenuSlot(i);
            assertEquals(false, seen[menuSlot], "menu slot " + menuSlot + " claimed twice");
            seen[menuSlot] = true;
        }
    }
}
