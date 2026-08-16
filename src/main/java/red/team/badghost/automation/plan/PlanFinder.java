// Copyright (C) 2026 RedTeam. Licensed under GNU AGPLv3.
package red.team.badghost.automation.plan;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Picks where to build the mechanism around a target block.
 *
 * <p>Candidates are scored 0 (best) to 3; the search returns as soon as a perfect one is found,
 * otherwise the best of what it saw. Anything the server would reject scores {@code null} and
 * is dropped, which is the difference between a plan that works and one that silently fails.</p>
 */
public final class PlanFinder {
    private PlanFinder() {}

    /** Number of quality buckets, 0..3. */
    private static final int QUALITY_LEVELS = 4;

    /** Faces considered per search; matches the upstream mod. */
    private static final int MAX_FACES = 5;

    @Nullable
    public static MiningPlan find(WorldView view, BlockPos target, PlanMode mode) {
        // Shared across every face: a perfect plan on a far face beats a compromised one on a
        // near face, so the fallback may only be taken once the whole search is exhausted.
        MiningPlan[] byQuality = new MiningPlan[QUALITY_LEVELS];

        for (Direction face : orderFaces(view, target, mode.faces())) {
            BlockPos pistonPos = target.relative(face);
            if (!view.isReplaceable(pistonPos)) {
                continue;
            }
            MiningPlan perfect = searchFace(view, target, face, pistonPos, mode, byQuality);
            if (perfect != null) {
                return perfect;
            }
        }

        for (MiningPlan plan : byQuality) {
            if (plan != null) {
                return plan;
            }
        }
        return null;
    }

    @Nullable
    private static MiningPlan searchFace(WorldView view, BlockPos target, Direction face,
                                         BlockPos pistonPos, PlanMode mode, MiningPlan[] byQuality) {
        Direction pushDir = face.getOpposite();

        for (Direction extendDir : orderExtendDirs(view, pistonPos, mode.extendDirs())) {
            if (!view.isPushable(pistonPos, extendDir)) {
                continue;
            }
            BlockPos extendPos = pistonPos.relative(extendDir);
            if (!view.isReplaceable(extendPos)) {
                continue;
            }

            for (Direction torchDir : orderTorchDirs(view, pistonPos)) {
                BlockPos torchPos = pistonPos.relative(torchDir);
                if (torchPos.equals(extendPos) || !view.isReplaceable(torchPos)) {
                    continue;
                }

                MiningPlan bare = new MiningPlan(target, pistonPos, extendDir, pushDir, torchPos, null);
                MiningPlan supported = new MiningPlan(target, pistonPos, extendDir, pushDir, torchPos, torchPos.below());
                for (MiningPlan candidate : List.of(bare, supported)) {
                    Integer quality = quality(view, candidate);
                    if (quality == null) {
                        continue;
                    }
                    if (quality == 0) {
                        return candidate;
                    }
                    if (byQuality[quality] == null) {
                        byQuality[quality] = candidate;
                    }
                }
            }
        }
        return null;
    }

    /**
     * {@code null} means unusable. Lower is better: 3 means the piston position is already
     * powered, 2 means the player stands where the head has to go, 1 means a support block has
     * to be spent.
     */
    @Nullable
    static Integer quality(WorldView view, MiningPlan plan) {
        BlockPos extendPos = plan.extendPos();

        if (view.isOccupied(plan.pistonPos()) || view.isOccupied(plan.torchPos()) || view.isOccupied(extendPos)) {
            return null;
        }
        if (plan.supportPos() != null && view.isOccupied(plan.supportPos())) {
            return null;
        }
        if (!reachableAndVisible(view, plan.pistonPos()) || !reachableAndVisible(view, plan.torchPos())) {
            return null;
        }
        if (plan.supportPos() != null && !reachableAndVisible(view, plan.supportPos())) {
            return null;
        }
        if (!view.canPlacePiston(plan.pistonPos())) {
            return null;
        }
        if (!view.isReplaceable(extendPos) || !view.isReplaceable(plan.torchPos())) {
            return null;
        }
        if (plan.supportPos() == null) {
            if (!view.torchCanSurvive(plan.torchPos())) {
                return null;
            }
        } else if (!view.isReplaceable(plan.supportPos()) || !view.canPlaceSupport(plan.supportPos())) {
            // A support is only worth planning into empty space: anything already standing
            // there belongs to the player and must not be disturbed.
            return null;
        }

        if (view.hasSignal(plan.pistonPos())) {
            return 3;
        }
        if (view.intersectsPlayer(extendPos)) {
            return 2;
        }
        return plan.supportPos() != null ? 1 : 0;
    }

    private static boolean reachableAndVisible(WorldView view, BlockPos pos) {
        return view.isReachable(pos) && view.isVisible(pos);
    }

    /**
     * Nearest faces first, then the closest one is moved to the back: the face the player is
     * pressed against is reachable but usually the worst place to grow a mechanism.
     */
    static List<Direction> orderFaces(WorldView view, BlockPos target, List<Direction> allowed) {
        List<Direction> sorted = new ArrayList<>(allowed);
        sorted.sort(Comparator.comparingDouble(dir -> view.eyeDistanceSqr(target.relative(dir))));
        if (sorted.size() > MAX_FACES) {
            sorted = new ArrayList<>(sorted.subList(0, MAX_FACES));
        }
        if (sorted.size() > 2) {
            Direction nearest = sorted.remove(0);
            sorted.add(nearest);
        }
        return sorted;
    }

    /** Farthest first, so the piston head grows away from the player. */
    static List<Direction> orderExtendDirs(WorldView view, BlockPos pistonPos, List<Direction> allowed) {
        List<Direction> sorted = new ArrayList<>(allowed);
        sorted.sort(Comparator.comparingDouble((Direction dir) -> view.eyeDistanceSqr(pistonPos.relative(dir))).reversed());
        return sorted;
    }

    /** Nearest first; the torch is the block the player has to reach past everything else. */
    static List<Direction> orderTorchDirs(WorldView view, BlockPos pistonPos) {
        List<Direction> sorted = new ArrayList<>();
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            sorted.add(dir);
        }
        sorted.sort(Comparator.comparingDouble(dir -> view.eyeDistanceSqr(pistonPos.relative(dir))));
        return sorted;
    }
}
