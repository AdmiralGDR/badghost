// Copyright (C) 2026 RedTeam. Licensed under GNU AGPLv3.
package red.team.badghost.automation;

/** Stages of breaking one target. */
public enum TaskState {
    INITIALIZE,
    FIND_PLAN,
    PLACE_SUPPORT,
    PLACE_TORCH,
    PLACE_PISTON,
    /** Waiting for the charged piston to report {@code EXTENDED}. */
    AWAIT_EXTEND,
    /** ALL_DIRECTION only: holding the faked yaw for a tick before the swap. */
    ROTATE,
    /** The one decisive tick: break torch, break piston, place piston into the target. */
    SWAP,
    /** Waiting for the piston animation to finish and the block to settle. */
    AWAIT_SETTLE,
    /** Checking whether the target actually changed. */
    VERIFY,
    /** Collecting the mechanism back, one block per tick. */
    CLEANUP,
    WAIT,
    RETRY,
    COMPLETE,
    FAIL
}
