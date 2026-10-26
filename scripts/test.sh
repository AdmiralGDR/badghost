#!/usr/bin/env bash
# Copyright (C) 2026 RedTeam. Licensed under GNU AGPLv3.
#
# Compiles under the strict warning gate, runs the unit tests, and checks that every
# translation key the code asks for actually exists in every language.
set -euo pipefail

cd "$(dirname "$0")/.."

./gradlew test --console=plain "$@"

echo "### translation coverage ###"
# A missing key shows the player a raw identifier instead of a sentence, and nothing in the
# compiler or the tests would notice. Derived keys (PlanResult reasons) are covered by unit
# tests; this catches the literals.
missing=0
# The tail may itself contain dots (badghost.command.help.title) but must not end in one:
# ending in a dot means the literal is a prefix a call site concatenates onto, and the key it
# builds cannot be known from here. Those are covered by unit tests instead.
keys=$(grep -rhoE '"badghost\.(message|reason|hud|req|configuration|command|feature|profile)\.[a-zA-Z_.]*[a-zA-Z_]"' src/main/java --include=*.java \
        | tr -d '"' | sort -u)
for key in ${keys}; do
    for lang in en_us ru_ru; do
        file="src/main/resources/assets/badghost/lang/${lang}.json"
        if ! grep -q "\"${key}\"" "${file}"; then
            echo "  MISSING ${key} in ${lang}" >&2
            missing=1
        fi
    done
done
if [ "${missing}" -ne 0 ]; then
    echo "### translation keys are missing — fix before release ###" >&2
    exit 1
fi
echo "  every referenced key is translated in en_us and ru_ru ($(echo "${keys}" | wc -l) keys)"

echo "report: build/reports/tests/test/index.html"
