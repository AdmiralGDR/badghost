#!/usr/bin/env bash
# Copyright (C) 2026 RedTeam. Licensed under GNU AGPLv3.
#
# Launches the development client, lets it reach the main menu, kills it and checks the log
# for load failures. Verifies that the mod registers, the mixin applies and nothing throws
# during startup. It cannot verify the glitch itself; that needs a real world.
#
# Usage: scripts/smoke-client.sh [seconds]
set -uo pipefail

cd "$(dirname "$0")/.."

RUN_SECONDS="${1:-120}"
LOG="build/smoke-client.log"

runner=()
if [ -z "${DISPLAY:-}" ] && command -v xvfb-run >/dev/null 2>&1; then
    runner=(xvfb-run -a)
fi

mkdir -p build
echo "running client for ${RUN_SECONDS}s -> ${LOG}"
timeout --signal=INT "${RUN_SECONDS}" \
    "${runner[@]}" ./gradlew runClient --console=plain >"${LOG}" 2>&1
status=$?

# 124 is the timeout we asked for, 130 is the interrupt reaching gradle: both are expected.
if [ "${status}" -ne 0 ] && [ "${status}" -ne 124 ] && [ "${status}" -ne 130 ]; then
    echo "client exited unexpectedly (status ${status})" >&2
fi

# Without a log every grep below trivially "passes", which would turn a client that never
# started into a green run. Refuse to report anything in that case.
if [ ! -s "${LOG}" ]; then
    echo "no log produced at ${LOG}; the client did not start" >&2
    exit 1
fi

fail=0

require() {
    if grep -qF "$1" "${LOG}"; then
        echo "ok      $2"
    else
        echo "MISSING $2" >&2
        fail=1
    fi
}

forbid() {
    if grep -qE "$1" "${LOG}"; then
        echo "FOUND   $2" >&2
        grep -nE "$1" "${LOG}" | head -5 >&2
        fail=1
    else
        echo "ok      no $2"
    fi
}

require "Selecting config badghost.mixins.json" "mixin config selected"
# Any of ours applying proves the config is live. Naming one specifically is wrong here: the
# movement-packet mixin only applies once a world is joined, which a main-menu run never does,
# so requiring it made the result depend on how far the client happened to get.
if grep -qE "Mixing [A-Za-z]+ from badghost\.mixins\.json into" "${LOG}"; then
    echo "ok      mixin applied ($(grep -cE 'Mixing [A-Za-z]+ from badghost\.mixins\.json into' "${LOG}") target(s))"
else
    echo "MISSING mixin applied to any target" >&2
    fail=1
fi

# Written by the mod constructor, so its presence proves the entry point ran.
if [ -f run/config/badghost-client.toml ]; then
    echo "ok      client config registered"
else
    echo "MISSING client config registered" >&2
    fail=1
fi

forbid "Mixin apply failed|InvalidInjectionException|InvalidMixinException|MixinApplyError" "mixin failure"
forbid "Failed to load mod|ModLoadingException|Cannot load mod" "mod load failure"
forbid "java\.lang\.NoSuchMethodError|java\.lang\.NoSuchFieldError|java\.lang\.NoClassDefFoundError" "linkage error"
forbid "Exception in thread \"Render thread\"" "render thread crash"

if [ "${fail}" -ne 0 ]; then
    echo "smoke test failed, see ${LOG}" >&2
    exit 1
fi
echo "smoke test passed"
