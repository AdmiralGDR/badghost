#!/usr/bin/env bash
# Copyright (C) 2026 RedTeam. Licensed under GNU AGPLv3.
#
# End-to-end proof that the bedrock miner works. Launches a real client into a real world,
# builds a scenario, runs the glitch and reports the verdict the game itself logged.
#
# Needs a singleplayer world with cheats in run/saves. Name it with -Pselftest_world=...
#
# Usage: scripts/selftest.sh [seconds] [world name]
set -uo pipefail

cd "$(dirname "$0")/.."

RUN_SECONDS="${1:-240}"
WORLD="${2:-New World}"
LOG="build/selftest.log"

if [ ! -d "run/saves/${WORLD}" ]; then
    echo "no world at run/saves/${WORLD}" >&2
    echo "create a singleplayer world with cheats once via ./gradlew runClient, then re-run" >&2
    exit 2
fi

runner=()
if [ -z "${DISPLAY:-}" ] && command -v xvfb-run >/dev/null 2>&1; then
    runner=(xvfb-run -a)
fi

mkdir -p build
echo "running self-test in '${WORLD}' for up to ${RUN_SECONDS}s -> ${LOG}"
timeout --signal=INT "${RUN_SECONDS}" \
    "${runner[@]}" ./gradlew runSelfTest --console=plain -Pselftest_world="${WORLD}" >"${LOG}" 2>&1

if [ ! -s "${LOG}" ]; then
    echo "no log produced; the client did not start" >&2
    exit 1
fi

echo
echo "--- self-test trace ---"
grep -F "BADGHOST-SELFTEST" "${LOG}" | sed 's/^.*BADGHOST-SELFTEST[]:]*//' || true
echo "-----------------------"

verdict=$(grep -oE "RESULT=(PASS|FAIL)" "${LOG}" | tail -1)
case "${verdict}" in
    RESULT=PASS)
        echo "SELF-TEST PASSED: the miner removed the bedrock"
        exit 0
        ;;
    RESULT=FAIL)
        echo "SELF-TEST FAILED, see ${LOG}" >&2
        exit 1
        ;;
    *)
        # No verdict at all means the harness never ran; that is a failure, not a pass.
        echo "SELF-TEST INCONCLUSIVE: no verdict was logged, see ${LOG}" >&2
        exit 1
        ;;
esac
