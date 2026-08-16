// Copyright (C) 2026 RedTeam. Licensed under GNU AGPLv3.
package red.team.badghost.automation.plan;

import net.minecraft.core.Direction;

import java.util.List;

/** Which faces of the target the piston may be planted on. */
public enum PlanMode {
    /**
     * Piston directly above or below the target. The decisive placement then points straight
     * down or up, which only needs a pitch change, so the faked angle never has to be held
     * across ticks.
     */
    VERTICAL_FAST(List.of(Direction.UP, Direction.DOWN), List.of(Direction.UP, Direction.DOWN)),

    /** All six faces. Side placements need a yaw held for a tick before the swap. */
    ALL_DIRECTION(List.of(Direction.values()), List.of(Direction.values()));

    private final List<Direction> faces;
    private final List<Direction> extendDirs;

    PlanMode(List<Direction> faces, List<Direction> extendDirs) {
        this.faces = faces;
        this.extendDirs = extendDirs;
    }

    public List<Direction> faces() {
        return faces;
    }

    public List<Direction> extendDirs() {
        return extendDirs;
    }
}
