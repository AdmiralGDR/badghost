// Copyright (C) 2026 RedTeam. Licensed under GNU AGPLv3.
package red.team.badghost.visuals;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

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

    /**
     * What the mod put down, what it covered up, and which placement it belonged to.
     *
     * <p>{@code batch} groups cells laid down by one action. A shape puts down dozens at once, and
     * undoing them one keypress at a time would be a chore rather than an undo; grouping makes one
     * press take back one thing the player did.</p>
     */
    public record Entry(BlockState ghost, BlockState original, int batch) {}

    /** Insertion-ordered so the newest entries are the ones undo reaches first. */
    private static final Map<BlockPos, Entry> entries = new LinkedHashMap<>();
    private static final Deque<BlockPos> placementOrder = new ArrayDeque<>();
    private static final Set<BlockPos> positionsView = Collections.unmodifiableSet(entries.keySet());

    /** Never reused within a session, so a stale id cannot collide with a live group. */
    private static int nextBatch = 1;

    /** Positions currently faked. Read-only; safe to consult from the render and physics paths. */
    public static Set<BlockPos> positions() {
        return positionsView;
    }

    /**
     * Drops entries whose ghost is no longer standing, then hands each survivor to {@code visitor}.
     *
     * <p>Removal lives here rather than at the call site because {@link #positions()} is an
     * unmodifiable view: iterating it and calling {@code remove} throws, which would take the
     * client tick down the first time a ghost went away.</p>
     *
     * <p>Staleness is judged against the state that was recorded for that cell, not against
     * whatever block the config names now — otherwise changing the setting would orphan every
     * existing ghost, leaving fake blocks in the world that nothing could undo.</p>
     */
    public static void visitLive(Function<BlockPos, BlockState> actualState, Consumer<BlockPos> visitor) {
        Iterator<Map.Entry<BlockPos, Entry>> it = entries.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPos, Entry> entry = it.next();
            BlockPos pos = entry.getKey();
            if (!actualState.apply(pos).is(entry.getValue().ghost().getBlock())) {
                it.remove();
                placementOrder.remove(pos);
                continue;
            }
            visitor.accept(pos);
        }
    }

    public static boolean contains(BlockPos pos) {
        return entries.containsKey(pos);
    }

    public static int size() {
        return entries.size();
    }

    /** A fresh group id, for a set of cells that should be taken back together. */
    public static int newBatch() {
        return nextBatch++;
    }

    /**
     * Records a ghost block as its own group.
     *
     * @return {@code false} when {@code limit} is already reached and nothing was recorded.
     */
    public static boolean add(BlockPos pos, BlockState ghost, BlockState original, int limit) {
        return add(pos, ghost, original, limit, newBatch());
    }

    /**
     * Records a ghost block as part of {@code batch}.
     *
     * @return {@code false} when {@code limit} is already reached and nothing was recorded.
     */
    public static boolean add(BlockPos pos, BlockState ghost, BlockState original, int limit, int batch) {
        BlockPos key = pos.immutable();
        if (entries.containsKey(key)) {
            return true;
        }
        if (entries.size() >= limit) {
            return false;
        }
        entries.put(key, new Entry(ghost, original, batch));
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

    /**
     * The newest group still standing, newest cell first, or empty when nothing is left.
     *
     * <p>Only cells that are still recorded are returned: whatever the server has since corrected
     * away is not the player's to take back.</p>
     */
    public static List<BlockPos> lastBatch() {
        BlockPos newest = lastPlaced();
        if (newest == null) {
            return List.of();
        }
        int batch = entries.get(newest).batch();
        List<BlockPos> group = new ArrayList<>();
        for (Map.Entry<BlockPos, Entry> entry : entries.entrySet()) {
            if (entry.getValue().batch() == batch) {
                group.add(entry.getKey());
            }
        }
        // Newest first, so removal runs backwards through how it was laid down.
        Collections.reverse(group);
        return group;
    }

    public static void clear() {
        entries.clear();
        placementOrder.clear();
    }
}
