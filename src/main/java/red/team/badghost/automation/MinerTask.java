// Copyright (C) 2026 RedTeam. Licensed under GNU AGPLv3.
package red.team.badghost.automation;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import red.team.badghost.automation.plan.LevelWorldView;
import red.team.badghost.automation.plan.MiningPlan;
import red.team.badghost.automation.plan.PlanFinder;
import red.team.badghost.automation.plan.PlanMode;
import red.team.badghost.automation.plan.PlanResult;
import red.team.badghost.config.BadghostConfig;
import red.team.badghost.core.ClientContext;
import red.team.badghost.core.SessionStats;
import red.team.badghost.utils.InteractionHelper;
import red.team.badghost.utils.InventoryHelper;
import red.team.badghost.utils.PlayerLookUtils;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Breaks one block with the vanilla piston glitch.
 *
 * <p>A piston is charged next to the target and allowed to extend away from it. Then, within a
 * single tick, the torch and the piston base are broken and a new piston is placed in the base's
 * cell pointing at the target. The server resolves that as the extending head arriving where the
 * target used to be, and the target is replaced.</p>
 *
 * <p>The tool never leaves the main hand: it has to break the piston instantly, and a slot
 * switch in the middle of the decisive tick loses that. The replacement piston therefore comes
 * out of the off hand.</p>
 */
public final class MinerTask {

    /** Ticks allowed for a placement to register before the step is retried. */
    private static final int PLACE_SETTLE_TICKS = 2;

    /**
     * Minimum wait after the swap before the result is judged. The verdict is read from the
     * client's copy of the world, which only learns about the swap once the server's block
     * update comes back, so checking immediately would report every success as a failure.
     */
    private static final int MIN_SETTLE_TICKS = 6;

    /** Hard ceiling on a task's lifetime, so a stuck target cannot wedge the queue. */
    private static final int MAX_TOTAL_TICKS = 400;

    /** Extra ticks granted after a timeout purely to collect what was already placed. */
    private static final int CLEANUP_GRACE_TICKS = 60;

    private final BlockPos target;
    private final BlockState originalState;
    private final Deque<BlockPos> recycleQueue = new ArrayDeque<>(4);

    private TaskState state = TaskState.INITIALIZE;
    private TaskState afterWait = TaskState.INITIALIZE;
    private int waitTicks;
    private int stepTicks;
    private int totalTicks;
    private int attempts;
    private boolean rotationHeld;
    private boolean chargeRotated;
    private boolean toolSettled;
    private boolean succeeded;
    private boolean aborted;

    @Nullable
    private MiningPlan plan;

    @Nullable
    private String failure;

    public MinerTask(BlockPos target, BlockState originalState) {
        this.target = target;
        this.originalState = originalState;
    }

    public TaskState getState() {
        return state;
    }

    public BlockPos getTarget() {
        return target;
    }

    @Nullable
    public MiningPlan getPlan() {
        return plan;
    }

    /** Ticks this task has lived, for the session average. */
    public int ticksSpent() {
        return totalTicks;
    }

    public boolean isComplete() {
        return state == TaskState.COMPLETE || state == TaskState.FAIL;
    }

    public boolean succeeded() {
        return succeeded;
    }

    @Nullable
    public String getFailure() {
        return failure;
    }

    /**
     * Stops as soon as it safely can, collecting whatever it has already placed.
     *
     * <p>Abandoning the mechanism would leave a piston, a lit torch and a support block sitting
     * next to the target — untidy, and evidence. Idempotent, and a no-op once finished.</p>
     */
    public void abort() {
        abort(null);
    }

    /** @param reason message key to report, or {@code null} when the player asked for the stop. */
    private void abort(@Nullable String reason) {
        if (aborted || isComplete()) {
            return;
        }
        aborted = true;
        succeeded = false;
        failure = reason;
        releaseRotation();
        state = recycleQueue.isEmpty() ? TaskState.COMPLETE : TaskState.CLEANUP;
    }

    /** Positions this task needs untouched by other tasks. */
    public boolean occupies(BlockPos pos) {
        return plan != null ? plan.occupies(pos) : pos.equals(target);
    }

    public void tick() {
        if (ClientContext.isInvalid()) {
            abandon(null);
            return;
        }

        totalTicks++;
        if (!isComplete()) {
            if (aborted) {
                // Winding up already; allow a bounded grace period to finish collecting before
                // giving up, so a stuck cleanup cannot wedge the queue forever.
                if (totalTicks > MAX_TOTAL_TICKS + CLEANUP_GRACE_TICKS) {
                    state = TaskState.COMPLETE;
                    return;
                }
            } else if (totalTicks > MAX_TOTAL_TICKS) {
                // Out of time, but the mechanism is real and in the world: collect it rather
                // than walking away and leaving a piston and a lit torch next to the target.
                abort("badghost.message.timeout");
                return;
            }
        }

        switch (state) {
            case INITIALIZE -> {
                stepTicks = 0;
                chargeRotated = false;
                toolSettled = false;
                SessionStats.recordAttempt();
                state = TaskState.FIND_PLAN;
            }
            case FIND_PLAN -> findPlan();
            case PLACE_SUPPORT -> placeSupport();
            case PLACE_TORCH -> placeTorch();
            case PLACE_PISTON -> placePiston();
            case AWAIT_EXTEND -> awaitExtend();
            case ROTATE -> rotate();
            case SWAP -> swap();
            case AWAIT_SETTLE -> awaitSettle();
            case VERIFY -> verify();
            case CLEANUP -> cleanup();
            case RETRY -> retry();
            case WAIT -> {
                if (--waitTicks <= 0) {
                    state = afterWait;
                }
            }
            case COMPLETE, FAIL -> { }
        }
    }

    // -- stages --

    private void findPlan() {
        ClientLevel level = ClientContext.getLevel();
        LocalPlayer player = ClientContext.getPlayer();
        if (level == null || player == null) {
            abandon(null);
            return;
        }

        PlanMode mode = BadghostConfig.PLAN_MODE.get();
        LevelWorldView view = new LevelWorldView(
                level, player, supportBlock(), pos -> AutomationEngine.isOccupiedByOther(this, pos));

        PlanResult result = PlanFinder.find(view, target, mode);
        if (result instanceof PlanResult.Rejected rejected) {
            // Report what actually stood in the way rather than a blanket "no room".
            abandon(rejected.reason().translationKey());
            return;
        }

        plan = ((PlanResult.Ok) result).plan();
        state = plan.supportPos() != null ? TaskState.PLACE_SUPPORT : TaskState.PLACE_TORCH;
    }

    private void placeSupport() {
        MiningPlan current = plan;
        if (current == null || current.supportPos() == null) {
            state = TaskState.PLACE_TORCH;
            return;
        }
        if (!InteractionHelper.isReplaceable(current.supportPos())) {
            // Something is already there. It is not ours, so it must not end up in the
            // recycle queue, or cleanup would mine a block belonging to the player.
            state = TaskState.PLACE_TORCH;
            return;
        }
        if (!InventoryHelper.canMoveItems()) {
            fail("badghost.message.screen_open");
            return;
        }
        if (!InventoryHelper.switchToOffHand(supportBlock().asItem())) {
            fail("badghost.message.need_support");
            return;
        }
        if (!InteractionHelper.place(current.supportPos(), Direction.UP, InteractionHand.OFF_HAND)) {
            fail("badghost.message.place_failed");
            return;
        }
        addRecycle(current.supportPos());
        wait(TaskState.PLACE_TORCH, PLACE_SETTLE_TICKS);
    }

    private void placeTorch() {
        MiningPlan current = plan;
        if (current == null) {
            abandon(null);
            return;
        }
        // The torch goes down before the piston: the piston then arrives into an already
        // powered cell and starts extending on the very next tick.
        if (!InventoryHelper.canMoveItems()) {
            fail("badghost.message.screen_open");
            return;
        }
        if (!InventoryHelper.switchToOffHand(Items.REDSTONE_TORCH)) {
            fail("badghost.message.need_torch");
            return;
        }
        if (!InteractionHelper.place(current.torchPos(), Direction.UP, InteractionHand.OFF_HAND)) {
            fail("badghost.message.place_failed");
            return;
        }
        addRecycle(current.torchPos());
        wait(TaskState.PLACE_PISTON, PLACE_SETTLE_TICKS);
    }

    private void placePiston() {
        MiningPlan current = plan;
        if (current == null) {
            abandon(null);
            return;
        }
        // A sideways charge placement has the same problem as the sideways swap: the server
        // has to have applied the yaw before the placement reaches it.
        if (needsHeldRotation(current.extendDir()) && !chargeRotated) {
            PlayerLookUtils.hold(current.extendDir());
            rotationHeld = true;
            chargeRotated = true;
            wait(TaskState.PLACE_PISTON, BadghostConfig.ROTATE_SETTLE_TICKS.get());
            return;
        }

        if (!InventoryHelper.canMoveItems()) {
            fail("badghost.message.screen_open");
            return;
        }
        if (!InventoryHelper.switchToOffHand(Items.PISTON, Items.STICKY_PISTON)) {
            fail("badghost.message.need_pistons");
            return;
        }
        if (!InteractionHelper.placeDirectional(current.pistonPos(), current.extendDir(), InteractionHand.OFF_HAND)) {
            fail("badghost.message.place_failed");
            return;
        }
        addRecycle(current.pistonPos());
        releaseRotation();
        // Everything is placed from the off hand, so the main hand can hold the pickaxe from
        // here on. Doing it now means the server has the whole charge-up to register it, and
        // the decisive tick spends no packets on switching.
        InventoryHelper.equipInstaMineTool(current.pistonPos());
        stepTicks = 0;
        wait(TaskState.AWAIT_EXTEND, PLACE_SETTLE_TICKS);
    }

    /** Sideways placements need the faked angle to survive across a tick boundary. */
    private static boolean needsHeldRotation(Direction facing) {
        return BadghostConfig.PLAN_MODE.get() == PlanMode.ALL_DIRECTION && facing.getAxis().isHorizontal();
    }

    private void awaitExtend() {
        MiningPlan current = plan;
        ClientLevel level = ClientContext.getLevel();
        if (current == null || level == null) {
            abandon(null);
            return;
        }

        BlockState pistonState = level.getBlockState(current.pistonPos());
        if (pistonState.getBlock() instanceof PistonBaseBlock && pistonState.getValue(PistonBaseBlock.EXTENDED)) {
            stepTicks = 0;
            state = needsHeldRotation(current.pushDir()) ? TaskState.ROTATE : TaskState.SWAP;
            return;
        }

        if (++stepTicks > BadghostConfig.WAIT_TICKS.get()) {
            retryOrFail("badghost.message.piston_stuck");
        }
    }

    private void rotate() {
        MiningPlan current = plan;
        if (current == null) {
            abandon(null);
            return;
        }
        PlayerLookUtils.hold(current.pushDir());
        rotationHeld = true;
        wait(TaskState.SWAP, BadghostConfig.ROTATE_SETTLE_TICKS.get());
    }

    /**
     * The decisive tick. Packet order matters and must not be split across ticks: the torch and
     * the piston have to die and the replacement piston has to be requested before the server
     * ticks the piston again.
     */
    private void swap() {
        MiningPlan current = plan;
        if (current == null) {
            abandon(null);
            return;
        }

        if (!toolSettled && !InventoryHelper.canInstaMinePiston(current.pistonPos())) {
            if (!InventoryHelper.equipInstaMineTool(current.pistonPos())) {
                fail("badghost.message.need_efficiency");
                return;
            }
            // The switch has to be acknowledged before the breaks, or the server resolves them
            // against whatever was held a moment ago and the piston survives. The piston stays
            // powered and extended meanwhile, so losing a tick here costs nothing. Only one
            // such tick is ever spent, so this cannot spin.
            toolSettled = true;
            wait(TaskState.SWAP, 1);
            return;
        }

        InteractionHelper.breakBlock(current.torchPos());
        InteractionHelper.breakBlock(current.pistonPos());

        if (!InventoryHelper.switchToOffHand(Items.PISTON, Items.STICKY_PISTON)) {
            fail("badghost.message.need_pistons");
            return;
        }
        InteractionHelper.placeDirectional(current.pistonPos(), current.pushDir(), InteractionHand.OFF_HAND);
        addRecycle(current.pistonPos());

        releaseRotation();
        stepTicks = 0;
        state = TaskState.AWAIT_SETTLE;
    }

    private void awaitSettle() {
        MiningPlan current = plan;
        ClientLevel level = ClientContext.getLevel();
        if (current == null || level == null) {
            abandon(null);
            return;
        }

        stepTicks++;

        BlockState pistonState = level.getBlockState(current.pistonPos());
        boolean settled = !pistonState.is(Blocks.MOVING_PISTON)
                && (!(pistonState.getBlock() instanceof PistonBaseBlock)
                        || !pistonState.getValue(PistonBaseBlock.EXTENDED));

        if (settled && stepTicks >= MIN_SETTLE_TICKS || stepTicks > BadghostConfig.WAIT_TICKS.get()) {
            state = TaskState.VERIFY;
        }
    }

    /** Success is decided by looking at the target, not by a timer. */
    private void verify() {
        ClientLevel level = ClientContext.getLevel();
        if (level == null) {
            abandon(null);
            return;
        }
        succeeded = !level.getBlockState(target).equals(originalState);
        state = TaskState.CLEANUP;
    }

    /** Runs on success and on failure alike: nothing the mod placed may be left behind. */
    private void cleanup() {
        if (drainRecycleStep()) {
            releaseRotation();
            // An aborted task never retries: the player asked it to stop, and it only stayed
            // alive long enough to pick its own mechanism back up.
            state = succeeded || aborted ? TaskState.COMPLETE : TaskState.RETRY;
        } else {
            wait(TaskState.CLEANUP, 1);
        }
    }

    private void retry() {
        if (++attempts >= BadghostConfig.MINER_MAX_RETRIES.get()) {
            failure = "badghost.message.breaking_failed";
            state = TaskState.FAIL;
            return;
        }
        plan = null;
        stepTicks = 0;
        state = TaskState.INITIALIZE;
    }

    // -- helpers --

    private Block supportBlock() {
        return AutomationEngine.resolveSupportBlock();
    }

    /** Removes one placed block; returns true once the mechanism is fully collected. */
    private boolean drainRecycleStep() {
        BlockPos pos = recycleQueue.peek();
        if (pos == null) {
            return true;
        }
        if (InteractionHelper.isReplaceable(pos)) {
            recycleQueue.poll();
            return recycleQueue.isEmpty();
        }
        if (!InventoryHelper.canInstaMinePiston(pos)) {
            InventoryHelper.equipInstaMineTool(pos);
        }
        InteractionHelper.breakBlock(pos);
        return false;
    }

    private void addRecycle(BlockPos pos) {
        if (pos != null && !recycleQueue.contains(pos)) {
            recycleQueue.add(pos);
        }
    }

    private void wait(TaskState next, int ticks) {
        afterWait = next;
        waitTicks = Math.max(ticks, 1);
        state = TaskState.WAIT;
    }

    /** Recoverable problem: tear the mechanism down and try again from scratch. */
    private void retryOrFail(String messageKey) {
        failure = messageKey;
        succeeded = false;
        releaseRotation();
        state = TaskState.CLEANUP;
    }

    /** Unrecoverable for this target; still cleans up what was placed. */
    private void fail(String messageKey) {
        failure = messageKey;
        succeeded = false;
        attempts = BadghostConfig.MINER_MAX_RETRIES.get();
        releaseRotation();
        state = recycleQueue.isEmpty() ? TaskState.FAIL : TaskState.CLEANUP;
    }

    /**
     * The world is gone, so nothing placed still exists and there is nothing to collect. Used
     * only for that case; running out of time goes through {@link #abort(String)} instead, which
     * still tidies up.
     */
    private void abandon(@Nullable String messageKey) {
        failure = messageKey;
        succeeded = false;
        releaseRotation();
        state = TaskState.FAIL;
    }

    private void releaseRotation() {
        if (rotationHeld) {
            PlayerLookUtils.release();
            rotationHeld = false;
        }
    }
}
