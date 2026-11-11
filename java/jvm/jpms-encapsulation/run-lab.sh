#!/usr/bin/env bash
# Java 25 JPMS positive/negative controls. No Maven, Gradle or external libs.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BUILD="$ROOT/target/jpms-lab"
mkdir -p "$BUILD"
rm -rf "$BUILD/modules" "$BUILD/negative"
mkdir -p "$BUILD/modules" "$BUILD/negative"

mapfile -t source_files < <(find "$ROOT/src" -name '*.java' -type f | sort)
javac --module-source-path "$ROOT/src" -d "$BUILD/modules" "${source_files[@]}"

java --module-path "$BUILD/modules" \
  --module org.education.jpms.app/org.education.jpms.app.AppMain deny \
  | tee "$BUILD/default-access.txt"
grep -Fxq 'PUBLIC=Hello, Education' "$BUILD/default-access.txt"
grep -Fxq 'FRAMEWORK=demo-only' "$BUILD/default-access.txt"
grep -Fxq 'APP_REFLECTION_DENIED' "$BUILD/default-access.txt"

# The *same compiled classes* demonstrate an explicit, narrowly scoped override.
java --module-path "$BUILD/modules" \
  --add-opens org.education.jpms.library/org.education.jpms.library.internal=org.education.jpms.app \
  --module org.education.jpms.app/org.education.jpms.app.AppMain allow \
  | tee "$BUILD/explicit-override.txt"
grep -Fxq 'APP_REFLECTION_ALLOWED=demo-only' "$BUILD/explicit-override.txt"

# A non-exported package must also be inaccessible to Java source code.
if javac --module-path "$BUILD/modules" --add-modules org.education.jpms.library \
    -d "$BUILD/negative" "$ROOT/negative/DeniedCompile.java" \
    > "$BUILD/negative-compile.txt" 2>&1; then
  echo 'FAIL: consumer unexpectedly compiled against an unexported package' >&2
  exit 1
fi
if ! grep -Eq 'is not visible|does not export' "$BUILD/negative-compile.txt"; then
  echo 'FAIL: compilation failed for a reason other than module encapsulation' >&2
  cat "$BUILD/negative-compile.txt" >&2
  exit 1
fi
jdeps --module-path "$BUILD/modules" --module org.education.jpms.app --list-deps \
  | tee "$BUILD/jdeps-module-deps.txt"

echo 'PASS: exports, qualified opens, access denial, explicit override, compile-time encapsulation'
