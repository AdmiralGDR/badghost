// Copyright (C) 2026 RedTeam. Licensed under GNU AGPLv3.
package red.team.badghost.visuals;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * What the mod has faked onto the client's copy of the world, and what was there before.
 *
 * <p>Remembering only the position was not enough: removing a ghost block wrote air, so a ghost
 * placed over grass ate the grass on the client until the server corrected it. Keeping the
 * original state makes removal exact and gives undo something to undo.</p>
 *
 * <p>Client-only and non-authoritative — the server never hears about any of this.</p>
 */
public final class GhostBlockRegistry {
    private GhostBlockRegistry() {}

    /** What the mod put down, and what it covered up. */
    public record Entry(BlockState ghost, BlockState original) {}

    /** Insertion-ordered so the newest entries are the ones undo reaches first. */
    private static final Map<BlockPos, Entry> entries = new LinkedHashMap<>();
    private static final Deque<BlockPos> placementOrder = new ArrayDeque<>();
    private static final Set<BlockPos> positionsView = Collections.unmodifiableSet(entries.keySet());

    /** Positions currently faked. Read-only; safe to consult from the render and physics paths. */
    public static Set<BlockPos> positions() {
        return positionsView;
    }

    public static boolean contains(BlockPos pos) {
        return entries.containsKey(pos);
    }

    public static int size() {
        return entries.size();
    }

    /**
     * Records a ghost block.
     *
     * @return {@code false} when {@code limit} is already reached and nothing was recorded.
     */
    public static boolean add(BlockPos pos, BlockState ghost, BlockState original, int limit) {
        BlockPos key = pos.immutable();
        if (entries.containsKey(key)) {
            return true;
        }
        if (entries.size() >= limit) {
            return false;
        }
        entries.put(key, new Entry(ghost, original));
        placementOrder.addLast(key);
        return true;
    }

    /** Forgets {@code pos}. @return what was there before the ghost, or {@code null}. */
    @Nullable
    public static BlockState remove(BlockPos pos) {
        Entry entry = entries.remove(pos);
        if (entry == null) {
            return null;
        }
        placementOrder.remove(pos);
        return entry.original();
    }

    /** The most recently placed ghost still standing, or {@code null}. */
    @Nullable
    public static BlockPos lastPlaced() {
        while (!placementOrder.isEmpty()) {
            BlockPos pos = placementOrder.peekLast();
            if (entries.containsKey(pos)) {
                return pos;
            }
            placementOrder.removeLast();
        }
        return null;
    }

    public static void clear() {
        entries.clear();
        placementOrder.clear();
    }
}
