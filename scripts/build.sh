#!/usr/bin/env bash
# Copyright (C) 2026 RedTeam. Licensed under GNU AGPLv3.
#
# Builds the mod and hashes the artifact. Archives are configured for reproducible
# output, so a rebuild from the same sources must produce the same checksum.
set -euo pipefail

cd "$(dirname "$0")/.."

./gradlew build --console=plain "$@"

shopt -s nullglob
artifacts=(build/libs/*.jar)
if [ ${#artifacts[@]} -eq 0 ]; then
    echo "no artifact produced" >&2
    exit 1
fi

for jar in "${artifacts[@]}"; do
    sha256sum "$jar" | tee "$jar.sha256"
done
