// Copyright (C) 2026 RedTeam. Licensed under GNU AGPLv3.
package red.team.badghost.visuals.template;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Shapes a single keypress can lay out, as a plain list of positions.
 *
 * <p>Ghost blocks were one at a time, which is fine for plugging a gap and useless for standing on:
 * a bridge or a wall meant holding the key and hoping. A shape is the same feature with the
 * arithmetic done up front.</p>
 *
 * <p>Deliberately a pure function of an origin, a facing and a size — no world, no client, no
 * config. That keeps every shape checkable in a test, and means the caller stays in charge of what
 * is actually legal to place. Order is fixed and starts at the origin, so undo takes a shape apart
 * in the reverse of how it went down.</p>
 */
public enum GhostTemplate {

    /** Just the one cell, exactly as before shapes existed. */
    SINGLE {
        @Override
        void collect(BlockPos origin, Direction facing, int size, Set<BlockPos> out) {
            out.add(origin);
        }
    },

    /** A run straight ahead: a catwalk to somewhere. */
    LINE {
        @Override
        void collect(BlockPos origin, Direction facing, int size, Set<BlockPos> out) {
            for (int ahead = 0; ahead < size; ahead++) {
                out.add(origin.relative(facing, ahead));
            }
        }
    },

    /** An upright panel across the way ahead, centred on the origin and rising from it. */
    WALL {
        @Override
        void collect(BlockPos origin, Direction facing, int size, Set<BlockPos> out) {
            Direction side = facing.getClockWise();
            int half = half(size);
            for (int up = 0; up < size; up++) {
                for (int across = 0; across < size; across++) {
                    out.add(origin.relative(side, across - half).above(up));
                }
            }
        }
    },

    /** A flat square at the origin's height, centred on it: something to stand on. */
    FLOOR {
        @Override
        void collect(BlockPos origin, Direction facing, int size, Set<BlockPos> out) {
            Direction side = facing.getClockWise();
            int half = half(size);
            for (int ahead = 0; ahead < size; ahead++) {
                for (int across = 0; across < size; across++) {
                    out.add(origin.relative(facing, ahead - half).relative(side, across - half));
                }
            }
        }
    },

    /** Like {@link #FLOOR}, but starting at the origin and running forward: a landing off a ledge. */
    PLATFORM {
        @Override
        void collect(BlockPos origin, Direction facing, int size, Set<BlockPos> out) {
            Direction side = facing.getClockWise();
            int half = half(size);
            for (int ahead = 0; ahead < size; ahead++) {
                for (int across = 0; across < size; across++) {
                    out.add(origin.relative(facing, ahead).relative(side, across - half));
                }
            }
        }
    },

    /**
     * A hollow cube standing on the origin's level, centred on it.
     *
     * <p>Hollow rather than solid so it can be sheltered in, and so the count stays a shell rather
     * than a cube's worth of the ghost budget.</p>
     */
    BOX {
        @Override
        void collect(BlockPos origin, Direction facing, int size, Set<BlockPos> out) {
            Direction side = facing.getClockWise();
            int half = half(size);
            int last = size - 1;
            for (int up = 0; up < size; up++) {
                for (int ahead = 0; ahead < size; ahead++) {
                    for (int across = 0; across < size; across++) {
                        boolean onShell = up == 0 || up == last
                                || ahead == 0 || ahead == last
                                || across == 0 || across == last;
                        if (onShell) {
                            out.add(origin.relative(facing, ahead - half)
                                    .relative(side, across - half)
                                    .above(up));
                        }
                    }
                }
            }
        }
    };

    /** Largest size worth offering: a 9-cube shell is already 386 cells. */
    public static final int MAX_SIZE = 9;

    abstract void collect(BlockPos origin, Direction facing, int size, Set<BlockPos> out);

    /** Offsets that centre a run of {@code size} on the origin. */
    private static int half(int size) {
        return (size - 1) / 2;
    }

    /**
     * Where this shape would put blocks.
     *
     * <p>Sizes below one are treated as one and sizes above {@link #MAX_SIZE} are capped, because a
     * shape is asked for by a player holding a key and refusing to draw anything would be the worse
     * answer. Duplicates cannot occur: the cells are gathered in a set that keeps its order.</p>
     */
    public List<BlockPos> positions(BlockPos origin, Direction facing, int size) {
        Direction horizontal = facing.getAxis().isHorizontal() ? facing : Direction.NORTH;
        int clamped = Math.clamp(size, 1, MAX_SIZE);
        Set<BlockPos> cells = new LinkedHashSet<>();
        collect(origin.immutable(), horizontal, clamped, cells);
        return new ArrayList<>(cells);
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String translationKey() {
        return "badghost.template." + key();
    }

    /** The shape called {@code name}, ignoring case and blanks, or null when there is no such one. */
    public static @Nullable GhostTemplate byName(String name) {
        String wanted = name.trim().toLowerCase(Locale.ROOT);
        for (GhostTemplate shape : values()) {
            if (shape.key().equals(wanted)) {
                return shape;
            }
        }
        return null;
    }

    public static List<String> names() {
        List<String> names = new ArrayList<>(values().length);
        for (GhostTemplate shape : values()) {
            names.add(shape.key());
        }
        return names;
    }
}
