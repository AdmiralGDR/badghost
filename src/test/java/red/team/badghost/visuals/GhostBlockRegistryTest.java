// Copyright (C) 2026 RedTeam. Licensed under GNU AGPLv3.
package red.team.badghost.visuals;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GhostBlockRegistryTest {

    private static final BlockState GHOST = Blocks.BEDROCK.defaultBlockState();
    private static final BlockState GRASS = Blocks.GRASS_BLOCK.defaultBlockState();
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final int LIMIT = 3;

    @BeforeEach
    void reset() {
        GhostBlockRegistry.clear();
    }

    @Test
    @DisplayName("removal hands back exactly what the ghost covered")
    void removalRestoresTheOriginal() {
        BlockPos pos = BlockPos.ZERO;
        assertTrue(GhostBlockRegistry.add(pos, GHOST, GRASS, LIMIT));

        // The old code wrote air here, which ate the block the ghost was placed over.
        assertSame(GRASS, GhostBlockRegistry.remove(pos));
        assertFalse(GhostBlockRegistry.contains(pos));
        assertNull(GhostBlockRegistry.remove(pos), "removing twice must not invent a state");
    }

    @Test
    @DisplayName("the limit is honoured and reported")
    void limitIsEnforced() {
        for (int i = 0; i < LIMIT; i++) {
            assertTrue(GhostBlockRegistry.add(new BlockPos(i, 0, 0), GHOST, AIR, LIMIT));
        }
        assertEquals(LIMIT, GhostBlockRegistry.size());
        assertFalse(GhostBlockRegistry.add(new BlockPos(99, 0, 0), GHOST, AIR, LIMIT),
                "the budget is full, so nothing more may be recorded");
        assertEquals(LIMIT, GhostBlockRegistry.size());
    }

    @Test
    @DisplayName("re-adding a position already faked is a no-op, not a limit charge")
    void reAddingIsIdempotent() {
        BlockPos pos = BlockPos.ZERO;
        assertTrue(GhostBlockRegistry.add(pos, GHOST, GRASS, 1));
        assertTrue(GhostBlockRegistry.add(pos, GHOST, AIR, 1), "same cell, still fine");
        assertEquals(1, GhostBlockRegistry.size());
        assertSame(GRASS, GhostBlockRegistry.remove(pos), "the first original is the true one");
    }

    @Test
    @DisplayName("undo reaches the newest ghost first, then the one before it")
    void undoWalksBackwards() {
        BlockPos first = new BlockPos(1, 0, 0);
        BlockPos second = new BlockPos(2, 0, 0);
        GhostBlockRegistry.add(first, GHOST, AIR, LIMIT);
        GhostBlockRegistry.add(second, GHOST, AIR, LIMIT);

        assertEquals(second, GhostBlockRegistry.lastPlaced());
        GhostBlockRegistry.remove(second);
        assertEquals(first, GhostBlockRegistry.lastPlaced());
        GhostBlockRegistry.remove(first);
        assertNull(GhostBlockRegistry.lastPlaced());
    }

    @Test
    @DisplayName("a ghost removed by other means is skipped by undo")
    void undoSkipsAlreadyGoneEntries() {
        BlockPos first = new BlockPos(1, 0, 0);
        BlockPos second = new BlockPos(2, 0, 0);
        GhostBlockRegistry.add(first, GHOST, AIR, LIMIT);
        GhostBlockRegistry.add(second, GHOST, AIR, LIMIT);

        // The server corrected the newest one, so undo must fall through to the previous.
        GhostBlockRegistry.remove(second);
        assertEquals(first, GhostBlockRegistry.lastPlaced());
    }

    @Test
    @DisplayName("positions() reflects the registry and rejects outside writes")
    void positionsAreReadOnly() {
        GhostBlockRegistry.add(BlockPos.ZERO, GHOST, AIR, LIMIT);
        assertTrue(GhostBlockRegistry.positions().contains(BlockPos.ZERO));
        try {
            GhostBlockRegistry.positions().add(new BlockPos(5, 5, 5));
            throw new AssertionError("the view must not be writable");
        } catch (UnsupportedOperationException expected) {
            // exactly right
        }
    }
}
