// Copyright (C) 2026 RedTeam. Licensed under GNU AGPLv3.
package red.team.badghost.visuals.template;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Shapes are arithmetic on an origin, so every claim about them can be checked outright. */
class GhostTemplateTest {

    private static final BlockPos ORIGIN = new BlockPos(10, 64, -20);

    private static List<BlockPos> at(GhostTemplate shape, Direction facing, int size) {
        return shape.positions(ORIGIN, facing, size);
    }

    @Test
    @DisplayName("one block is one block, whatever size is asked for")
    void singleIgnoresSize() {
        for (int size : List.of(1, 3, 9)) {
            assertEquals(List.of(ORIGIN), at(GhostTemplate.SINGLE, Direction.NORTH, size));
        }
    }

    @Test
    @DisplayName("each shape lays down the number of blocks it should")
    void countsAreRight() {
        assertEquals(5, at(GhostTemplate.LINE, Direction.NORTH, 5).size());
        assertEquals(25, at(GhostTemplate.WALL, Direction.NORTH, 5).size());
        assertEquals(25, at(GhostTemplate.FLOOR, Direction.NORTH, 5).size());
        assertEquals(25, at(GhostTemplate.PLATFORM, Direction.NORTH, 5).size());
        // A hollow cube is the cube less its interior: 5³ − 3³.
        assertEquals(125 - 27, at(GhostTemplate.BOX, Direction.NORTH, 5).size());
    }

    @Test
    @DisplayName("no shape ever repeats a cell")
    void noDuplicates() {
        for (GhostTemplate shape : GhostTemplate.values()) {
            for (int size = 1; size <= GhostTemplate.MAX_SIZE; size++) {
                List<BlockPos> cells = at(shape, Direction.EAST, size);
                assertEquals(cells.size(), new HashSet<>(cells).size(),
                        shape + " at size " + size + " repeats a cell, wasting the ghost budget");
            }
        }
    }

    @Test
    @DisplayName("a wall stands up across the way ahead")
    void wallIsUpright() {
        List<BlockPos> cells = at(GhostTemplate.WALL, Direction.NORTH, 3);

        // Facing north, the wall spreads east-west and upwards, and stays on the origin's row.
        for (BlockPos pos : cells) {
            assertEquals(ORIGIN.getZ(), pos.getZ(), "a wall must not have depth: " + pos);
        }
        assertEquals(ORIGIN.getY(), cells.get(0).getY(), "it rises from the origin, not below it");
        assertTrue(cells.stream().anyMatch(p -> p.getY() == ORIGIN.getY() + 2));
    }

    @Test
    @DisplayName("a floor is flat and a platform runs forward from the origin")
    void flatShapes() {
        for (BlockPos pos : at(GhostTemplate.FLOOR, Direction.SOUTH, 5)) {
            assertEquals(ORIGIN.getY(), pos.getY(), "a floor is one layer");
        }
        // South is +Z, so a platform must never fall behind the origin on that axis.
        for (BlockPos pos : at(GhostTemplate.PLATFORM, Direction.SOUTH, 5)) {
            assertEquals(ORIGIN.getY(), pos.getY());
            assertTrue(pos.getZ() >= ORIGIN.getZ(), "a platform extends ahead, not behind: " + pos);
        }
    }

    @Test
    @DisplayName("shapes turn with the player")
    void orientationFollowsFacing() {
        assertNotEquals(new HashSet<>(at(GhostTemplate.WALL, Direction.NORTH, 3)),
                new HashSet<>(at(GhostTemplate.WALL, Direction.EAST, 3)));
        assertEquals(new HashSet<>(at(GhostTemplate.LINE, Direction.NORTH, 4)),
                new HashSet<>(at(GhostTemplate.LINE, Direction.NORTH, 4)),
                "the same request must give the same answer");
    }

    @Test
    @DisplayName("looking straight up or down still gives a horizontal shape")
    void verticalFacingFallsBack() {
        // Direction comes from where the player looks, and a wall along the Y axis is meaningless.
        for (Direction vertical : List.of(Direction.UP, Direction.DOWN)) {
            assertEquals(at(GhostTemplate.LINE, Direction.NORTH, 4),
                    at(GhostTemplate.LINE, vertical, 4),
                    "a vertical facing must fall back rather than produce nonsense");
        }
    }

    @Test
    @DisplayName("an impossible size is brought into range instead of refused")
    void sizeIsClamped() {
        // A player holding a key deserves a shape, not a rejection.
        assertEquals(1, at(GhostTemplate.LINE, Direction.NORTH, 0).size());
        assertEquals(1, at(GhostTemplate.LINE, Direction.NORTH, -7).size());
        assertEquals(GhostTemplate.MAX_SIZE, at(GhostTemplate.LINE, Direction.NORTH, 999).size());
    }

    @Test
    @DisplayName("a name is found however it is written, and an unknown one is null")
    void nameLookup() {
        for (GhostTemplate shape : GhostTemplate.values()) {
            assertSame(shape, GhostTemplate.byName(shape.key()));
            assertSame(shape, GhostTemplate.byName(" " + shape.key().toUpperCase(java.util.Locale.ROOT) + " "));
        }
        for (String bogus : List.of("", "wal", "cube", "1")) {
            assertNull(GhostTemplate.byName(bogus));
        }
        assertEquals(GhostTemplate.values().length, GhostTemplate.names().size());
    }

    @Test
    @DisplayName("every shape is named in every language")
    void everyShapeIsNamed() {
        // Built as "badghost.template." + key, so the shell gate in scripts/test.sh cannot see it.
        for (String language : List.of("en_us", "ru_ru")) {
            String path = "/assets/badghost/lang/" + language + ".json";
            JsonObject json;
            try (InputStream in = GhostTemplateTest.class.getResourceAsStream(path)) {
                assertNotNull(in, "missing " + path);
                json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                        .getAsJsonObject();
            } catch (Exception e) {
                throw new AssertionError("could not read " + path, e);
            }
            List<String> missing = new ArrayList<>();
            for (GhostTemplate shape : GhostTemplate.values()) {
                String key = shape.translationKey();
                if (!json.has(key) || json.get(key).getAsString().isBlank()) {
                    missing.add(key);
                }
            }
            assertTrue(missing.isEmpty(), language + " is missing " + missing);
        }
    }
}
