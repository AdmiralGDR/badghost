// Copyright (C) 2026 RedTeam. Licensed under GNU AGPLv3.
package red.team.badghost.automation.plan;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;
import red.team.badghost.automation.plan.PlanResult.Ok;
import red.team.badghost.automation.plan.PlanResult.Reason;
import red.team.badghost.automation.plan.PlanResult.Rejected;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Picks where to build the mechanism around a target block.
 *
 * <p>Candidates are scored 0 (best) to 3; the search returns as soon as a perfect one is found,
 * otherwise the best of what it saw. A candidate the server would reject is dropped along with
 * the reason, and when nothing fits at all the most informative reason is reported back.</p>
 */
public final class PlanFinder {
    private PlanFinder() {}

    /** Number of quality buckets, 0..3. */
    private static final int QUALITY_LEVELS = 4;

    /** Faces considered per search; matches the upstream mod. */
    private static final int MAX_FACES = 5;

    /** Accumulates the runner-up plans and the most telling rejection across the whole search. */
    private static final class Search {
        private final MiningPlan[] byQuality = new MiningPlan[QUALITY_LEVELS];
        private Reason reason = Reason.NO_FREE_FACE;
        private BlockPos where;

        void note(Rejected rejected) {
            if (where == null || rejected.reason().isMoreInformativeThan(reason)) {
                reason = rejected.reason();
                where = rejected.where();
            }
        }

        void keep(MiningPlan plan, int quality) {
            if (byQuality[quality] == null) {
                byQuality[quality] = plan;
            }
        }

        PlanResult finish(BlockPos target) {
            for (int quality = 0; quality < byQuality.length; quality++) {
                if (byQuality[quality] != null) {
                    return new Ok(byQuality[quality], quality);
                }
            }
            return new Rejected(reason, where == null ? target : where);
        }
    }

    /** Finds the best mechanism, or explains why there is none. */
    public static PlanResult find(WorldView view, BlockPos target, PlanMode mode) {
        Search search = new Search();

        for (Direction face : orderFaces(view, target, mode.faces())) {
            BlockPos pistonPos = target.relative(face);
            if (!view.isReplaceable(pistonPos)) {
                search.note(new Rejected(Reason.CELL_BLOCKED, pistonPos));
                continue;
            }
            // A perfect plan on a far face beats a compromised one on a near face, so the
            // runner-ups are only consulted once the whole search is exhausted.
            MiningPlan perfect = searchFace(view, target, face, pistonPos, mode, search);
            if (perfect != null) {
                return new Ok(perfect, 0);
            }
        }
        return search.finish(target);
    }

    @Nullable
    private static MiningPlan searchFace(WorldView view, BlockPos target, Direction face,
                                         BlockPos pistonPos, PlanMode mode, Search search) {
        Direction pushDir = face.getOpposite();

        for (Direction extendDir : orderExtendDirs(view, pistonPos, mode.extendDirs())) {
            if (!view.isPushable(pistonPos, extendDir)) {
                search.note(new Rejected(Reason.PISTON_WONT_FIT, pistonPos));
                continue;
            }
            BlockPos extendPos = pistonPos.relative(extendDir);
            if (!view.isReplaceable(extendPos)) {
                search.note(new Rejected(Reason.CELL_BLOCKED, extendPos));
                continue;
            }

            for (Direction torchDir : orderTorchDirs(view, pistonPos)) {
                BlockPos torchPos = pistonPos.relative(torchDir);
                if (torchPos.equals(extendPos)) {
                    continue;
                }
                if (!view.isReplaceable(torchPos)) {
                    search.note(new Rejected(Reason.CELL_BLOCKED, torchPos));
                    continue;
                }

                // Evaluated one at a time rather than through a list: this runs for every
                // torch direction of every extend direction of every face, and the supported
                // variant is never even built when the plain one is already perfect.
                MiningPlan perfect = consider(view,
                        new MiningPlan(target, pistonPos, extendDir, pushDir, torchPos, null), search);
                if (perfect != null) {
                    return perfect;
                }
                perfect = consider(view,
                        new MiningPlan(target, pistonPos, extendDir, pushDir, torchPos, torchPos.below()), search);
                if (perfect != null) {
                    return perfect;
                }
            }
        }
        return null;
    }

    /**
     * Scores one candidate and files it under its quality.
     *
     * @return the candidate when it is perfect and the search can stop, otherwise {@code null}.
     */
    @Nullable
    private static MiningPlan consider(WorldView view, MiningPlan candidate, Search search) {
        PlanResult result = evaluate(view, candidate);
        if (result instanceof Rejected rejected) {
            search.note(rejected);
            return null;
        }
        Ok ok = (Ok) result;
        if (ok.quality() == 0) {
            return candidate;
        }
        search.keep(candidate, ok.quality());
        return null;
    }

    /**
     * Judges one candidate. Lower quality is better: 3 means the piston position is already
     * powered, 2 means the player stands where the head has to go, 1 means a support block has
     * to be spent.
     */
    static PlanResult evaluate(WorldView view, MiningPlan plan) {
        BlockPos extendPos = plan.extendPos();
        BlockPos support = plan.supportPos();

        // Every guard below must pass, so their order does not change the verdict — only how
        // much work a doomed candidate costs. Line of sight is by far the dearest (a raycast per
        // face of a position) and the search can evaluate hundreds of candidates in one tick, so
        // it runs last, after the set lookups and the block-state and geometry checks.
        if (view.isOccupied(plan.pistonPos())) {
            return new Rejected(Reason.OCCUPIED_BY_TASK, plan.pistonPos());
        }
        if (view.isOccupied(plan.torchPos())) {
            return new Rejected(Reason.OCCUPIED_BY_TASK, plan.torchPos());
        }
        if (view.isOccupied(extendPos)) {
            return new Rejected(Reason.OCCUPIED_BY_TASK, extendPos);
        }
        if (support != null && view.isOccupied(support)) {
            return new Rejected(Reason.OCCUPIED_BY_TASK, support);
        }
        if (!view.isReplaceable(extendPos)) {
            return new Rejected(Reason.CELL_BLOCKED, extendPos);
        }
        if (!view.isReplaceable(plan.torchPos())) {
            return new Rejected(Reason.CELL_BLOCKED, plan.torchPos());
        }
        if (!view.isReachable(plan.pistonPos())) {
            return new Rejected(Reason.OUT_OF_REACH, plan.pistonPos());
        }
        if (!view.isReachable(plan.torchPos())) {
            return new Rejected(Reason.OUT_OF_REACH, plan.torchPos());
        }
        if (support != null && !view.isReachable(support)) {
            return new Rejected(Reason.OUT_OF_REACH, support);
        }
        if (!view.canPlacePiston(plan.pistonPos())) {
            return new Rejected(Reason.PISTON_WONT_FIT, plan.pistonPos());
        }
        if (support == null) {
            if (!view.torchCanSurvive(plan.torchPos())) {
                return new Rejected(Reason.TORCH_WONT_SURVIVE, plan.torchPos());
            }
        } else {
            // A support is only worth planning into empty space: anything already standing
            // there belongs to the player and must not be disturbed.
            if (!view.isReplaceable(support) || !view.canPlaceSupport(support)) {
                return new Rejected(Reason.SUPPORT_WONT_FIT, support);
            }
        }
        if (!view.isVisible(plan.pistonPos())) {
            return new Rejected(Reason.NO_LINE_OF_SIGHT, plan.pistonPos());
        }
        if (!view.isVisible(plan.torchPos())) {
            return new Rejected(Reason.NO_LINE_OF_SIGHT, plan.torchPos());
        }
        if (support != null && !view.isVisible(support)) {
            return new Rejected(Reason.NO_LINE_OF_SIGHT, support);
        }

        if (view.hasSignal(plan.pistonPos())) {
            return new Ok(plan, 3);
        }
        if (view.intersectsPlayer(extendPos)) {
            return new Ok(plan, 2);
        }
        return new Ok(plan, support != null ? 1 : 0);
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
