#!/usr/bin/env bash
# Reproducible Java 25 upgrade diagnostics: warnings + bytecode scanning.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BUILD="$ROOT/target/upgrade-lab"
mkdir -p "$BUILD"
rm -rf "$BUILD/legacy" "$BUILD/modern"
mkdir -p "$BUILD/legacy" "$BUILD/modern"

javac -Xlint:deprecation,removal -d "$BUILD/legacy" \
  "$ROOT/src/edu/upgrade/LegacyBoxing.java" 2> "$BUILD/legacy-warnings.txt"
# This is the EXPLICIT negative fixture. Require the warning, not just exit=0.
grep -Fq 'deprecated' "$BUILD/legacy-warnings.txt"

javac -Xlint:deprecation,removal -d "$BUILD/modern" \
  "$ROOT/src/edu/upgrade/ModernBoxing.java" 2> "$BUILD/modern-warnings.txt"
test ! -s "$BUILD/modern-warnings.txt"

jdeprscan "$BUILD/legacy" | tee "$BUILD/legacy-deprecated.txt"
grep -Fq 'java/lang/Integer' "$BUILD/legacy-deprecated.txt"

jdeprscan "$BUILD/modern" | tee "$BUILD/modern-deprecated.txt"
test ! -s "$BUILD/modern-deprecated.txt"

jdeps --list-deps "$BUILD/legacy" | tee "$BUILD/jdeps.txt"
grep -Fq 'java.base' "$BUILD/jdeps.txt"

test "$(java -cp "$BUILD/legacy" edu.upgrade.LegacyBoxing)" = 'VALUE=42'
test "$(java -cp "$BUILD/modern" edu.upgrade.ModernBoxing)" = 'VALUE=42'
echo 'PASS: legacy warning + bytecode deprecation + modern replacement + JDK dependencies'
