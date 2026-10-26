#!/usr/bin/env bash
# Copyright (C) 2026 RedTeam. Licensed under GNU AGPLv3.
#
# Reads the in-game harness's own verdict out of a client log. Sourced, not run.
#
# Kept in one place on purpose. Both self-test scripts used to match the verdict line themselves,
# and when the harness started reporting "all N checks" instead of "all N scenarios" only one of
# them was updated — leaving the other unable to read a verdict at all. It said so rather than
# passing, but the answer to that class of bug is one reader, not two.

# read_verdict <logfile>
#   prints the verdict line as the harness wrote it
#   returns 0 on PASS, 1 on FAIL, 2 when no verdict was logged at all
read_verdict() {
    local log="$1"
    local line
    line=$(grep -aoE 'RESULT=(PASS|FAIL).*' "$log" 2>/dev/null | tail -1)
    if [ -z "$line" ]; then
        return 2
    fi
    printf '%s\n' "$line"
    case "$line" in
        RESULT=PASS*) return 0 ;;
        *) return 1 ;;
    esac
}

# True once the harness has written any verdict, for a wait loop.
verdict_present() {
    grep -aqE 'RESULT=(PASS|FAIL)' "$1" 2>/dev/null
}
