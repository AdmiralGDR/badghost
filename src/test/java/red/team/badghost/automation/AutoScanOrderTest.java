// Copyright (C) 2026 RedTeam. Licensed under GNU AGPLv3.
package red.team.badghost.automation;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The old scan walked the box in raw index order, so it queued whatever the loop reached first
 * rather than what the player was standing next to.
 */
class AutoScanOrderTest {

    @Test
    @DisplayName("scan hits come out nearest to the player first")
    void sortsNearestFirst() {
        List<BlockPos> hits = new ArrayList<>(List.of(
                new BlockPos(4, 0, 0),
                new BlockPos(0, 0, 1),
                new BlockPos(2, 0, 0),
                new BlockPos(0, 3, 0)));

        List<BlockPos> sorted = AutomationEngine.sortByDistance(hits, new Vec3(0.5D, 0.5D, 0.5D));

        assertEquals(new BlockPos(0, 0, 1), sorted.get(0));
        assertEquals(new BlockPos(2, 0, 0), sorted.get(1));
        assertEquals(new BlockPos(0, 3, 0), sorted.get(2));
        assertEquals(new BlockPos(4, 0, 0), sorted.get(3));
    }

    @Test
    @DisplayName("ordering is by true distance, not by axis walk order")
    void heightIsNotPreferredOverProximity() {
        // The old triple loop stepped dy from +radius downwards, so a block four above the
        // player beat one directly beside them.
        List<BlockPos> hits = new ArrayList<>(List.of(
                new BlockPos(0, 4, 0),
                new BlockPos(1, 0, 0)));

        List<BlockPos> sorted = AutomationEngine.sortByDistance(hits, new Vec3(0.5D, 0.5D, 0.5D));

        assertEquals(new BlockPos(1, 0, 0), sorted.get(0));
    }
}
