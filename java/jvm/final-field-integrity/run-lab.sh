#!/usr/bin/env bash
# P1 JEP 500, verified separately with Java 25 and Java 26.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUT="$ROOT/target/final-field-integrity"
mkdir -p "$OUT"
javac -d "$OUT" "$ROOT/FinalFieldMutationDemo.java"

VERSION="$(java -version 2>&1 | head -n 1)"
MAJOR="$(echo "$VERSION" | sed -n 's/.*version "\([0-9]*\).*/\1/p')"
if [[ "$MAJOR" != "25" && "$MAJOR" != "26" ]]; then
  echo "Expected Java 25 or 26, got: $VERSION" >&2
  exit 2
fi

java -cp "$OUT" FinalFieldMutationDemo > "$OUT/default.out" 2> "$OUT/default.err"
grep -Fq "MUTATION_ALLOWED ageLimit=17" "$OUT/default.out"

if [[ "$MAJOR" == "25" ]]; then
  if grep -Fq "WARNING: Final field" "$OUT/default.err"; then
    echo "Unexpected JEP 500 warning on JDK25" >&2
    exit 1
  fi
  echo "PASS Java 25: reflective final-field mutation permitted in this demo"
  exit 0
fi

grep -Fq "WARNING: Final field" "$OUT/default.err"
echo "PASS Java 26: default warn, mutation still occurs"

java --illegal-final-field-mutation=debug \
  -cp "$OUT" FinalFieldMutationDemo > "$OUT/debug.out" 2> "$OUT/debug.err"
grep -Fq "MUTATION_ALLOWED ageLimit=17" "$OUT/debug.out"
grep -Fq "WARNING: Final field" "$OUT/debug.err"
grep -Fq "FinalFieldMutationDemo.main" "$OUT/debug.err"
echo "PASS Java 26: debug warning + stack trace"

java --illegal-final-field-mutation=deny \
  -cp "$OUT" FinalFieldMutationDemo > "$OUT/deny.out" 2> "$OUT/deny.err"
grep -Fq "MUTATION_DENIED IllegalAccessException ageLimit=21" "$OUT/deny.out"
echo "PASS Java 26: deny blocks mutation"

# Temporary compatibility: opt in for the module doing the reflective write.
java --enable-final-field-mutation=ALL-UNNAMED \
  --illegal-final-field-mutation=deny \
  -cp "$OUT" FinalFieldMutationDemo > "$OUT/explicit-optin.out" 2> "$OUT/explicit-optin.err"
grep -Fq "MUTATION_ALLOWED ageLimit=17" "$OUT/explicit-optin.out"
echo "PASS Java 26: explicit module opt-in allows mutation"
