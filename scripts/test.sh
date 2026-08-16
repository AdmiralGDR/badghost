#!/usr/bin/env bash
# Copyright (C) 2026 RedTeam. Licensed under GNU AGPLv3.
#
# Compiles under the strict warning gate and runs the unit tests.
set -euo pipefail

cd "$(dirname "$0")/.."

./gradlew test --console=plain "$@"

echo "report: build/reports/tests/test/index.html"
