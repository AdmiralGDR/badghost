#!/usr/bin/env bash
# Copyright (C) 2026 RedTeam. Licensed under GNU AGPLv3.
#
# End-to-end proof over a REAL network path: a dedicated NeoForge server (which does not load
# this client-only mod at all) plus a separate client that connects to it over a socket and runs
# the bedrock-miner scenarios. This exercises the block-prediction / sequence-id path across a
# genuine client<->server boundary, not the shortcut of an integrated singleplayer server.
#
# Usage: scripts/selftest-server.sh [client-seconds]
set -uo pipefail

cd "$(dirname "$0")/.."
# shellcheck source=scripts/verdict.sh
. "$(dirname "$0")/verdict.sh"

CLIENT_SECONDS="${1:-260}"
SERVER_LOG="build/selftest-server-dedicated.log"
CLIENT_LOG="build/selftest-server-client.log"
SERVER_READY_TIMEOUT=180

server_pid=""
client_pid=""
cleanup() {
    [ -n "${client_pid}" ] && kill "${client_pid}" 2>/dev/null
    [ -n "${server_pid}" ] && kill "${server_pid}" 2>/dev/null
    pkill -f "forgeserverdev" 2>/dev/null
    pkill -f "forgeclientdev" 2>/dev/null
    pkill -f "net.neoforged.devlaunch.Main" 2>/dev/null
}
trap cleanup EXIT

mkdir -p build run-server run-mp

# The dedicated server must accept an offline dev login and have "Dev" pre-opped so the harness
# can build its scenario with commands.
printf 'eula=true\n' > run-server/eula.txt
cat > run-server/ops.json <<'EOF'
[{"uuid":"380df991-f603-344c-a090-369bad2a924a","name":"Dev","level":4,"bypassesPlayerLimit":true}]
EOF
if [ ! -f run-server/server.properties ]; then
    cat > run-server/server.properties <<'EOF'
online-mode=false
gamemode=survival
difficulty=peaceful
spawn-protection=0
allow-flight=true
level-name=world
max-players=4
view-distance=8
server-port=25565
motd=badghost-selftest
allow-nether=false
spawn-monsters=false
EOF
fi

echo "### compiling and regenerating the server launch script ###"
# Only the SERVER runs as a plain-Java launch script (headless, no Gradle lock). The CLIENT is
# started through Gradle below: the generated client launch script hangs during resource load
# in this dev/offscreen setup, while the Gradle run configures the client exactly like the
# working singleplayer run. The two never contend — the server is not holding the daemon.
./gradlew classes createSelfTestServerLaunchScript --console=plain -q || {
    echo "gradle prepare failed" >&2; exit 1; }

runner=()
if [ -z "${DISPLAY:-}" ] && command -v xvfb-run >/dev/null 2>&1; then
    runner=(xvfb-run -a)
fi

echo "### starting dedicated server ###"
bash build/moddev/runSelfTestServer.sh > "${SERVER_LOG}" 2>&1 &
server_pid=$!

echo "### waiting for server to be ready (up to ${SERVER_READY_TIMEOUT}s) ###"
for _ in $(seq "${SERVER_READY_TIMEOUT}"); do
    if grep -qF 'Done (' "${SERVER_LOG}" 2>/dev/null; then break; fi
    if ! kill -0 "${server_pid}" 2>/dev/null; then
        echo "server exited before becoming ready:" >&2; tail -20 "${SERVER_LOG}" >&2; exit 1
    fi
    sleep 1
done
if ! grep -qF 'Done (' "${SERVER_LOG}"; then
    echo "server did not report ready in time" >&2; tail -20 "${SERVER_LOG}" >&2; exit 1
fi
echo "  server up. badghost on the server side: $(grep -c 'badghost' "${SERVER_LOG}" | head -1) log lines (client-only mod must not load)."

echo "### starting client via Gradle (connects to 127.0.0.1:25565) ###"
timeout --signal=INT "${CLIENT_SECONDS}" "${runner[@]}" ./gradlew runSelfTestMp --console=plain > "${CLIENT_LOG}" 2>&1 &
client_pid=$!

echo "### waiting for the client's verdict ###"
for _ in $(seq "${CLIENT_SECONDS}"); do
    if verdict_present "${CLIENT_LOG}"; then break; fi
    if ! kill -0 "${client_pid}" 2>/dev/null; then break; fi
    sleep 1
done

echo
echo "--- self-test trace (over the network) ---"
grep -aF 'BADGHOST-SELFTEST' "${CLIENT_LOG}" 2>/dev/null | sed 's/^.*BADGHOST-SELFTEST[]:]*//' || true
echo "-------------------------------------------"

verdict=$(read_verdict "${CLIENT_LOG}")
case "$?" in
    0)  echo "SERVER SELF-TEST PASSED over a real network path: ${verdict#RESULT=PASS }"
        exit 0 ;;
    1)  echo "SERVER SELF-TEST FAILED: ${verdict#RESULT=FAIL }" >&2
        echo "see ${CLIENT_LOG}" >&2; exit 1 ;;
    *)  # No verdict means the harness never finished; that is a failure, not a pass.
        echo "SERVER SELF-TEST INCONCLUSIVE: no verdict was logged, see ${CLIENT_LOG}" >&2
        exit 1 ;;
esac
