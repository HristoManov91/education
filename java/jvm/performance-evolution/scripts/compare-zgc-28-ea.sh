#!/usr/bin/env bash
# Manual JDK 28 EARLY ACCESS experiment. No installation or production changes.
set -euo pipefail
if [[ "$#" -ne 1 ]]; then
  echo "Usage: compare-zgc-28-ea.sh <JDK28_HOME/bin/java>" >&2
  exit 2
fi
JAVA="$1"
if [[ ! -x "$JAVA" ]]; then
  echo "Executable Java 28 binary not found: $JAVA" >&2
  exit 2
fi

MAJOR="$("$JAVA" -XshowSettings:properties -version 2>&1 \
  | sed -nE 's/^[[:space:]]*java\.specification\.version[[:space:]]*=[[:space:]]*([0-9]+).*$/\1/p')"
if [[ "$MAJOR" != "28" ]]; then
  echo "This manual experiment requires a JDK 28 EA binary; got: ${MAJOR:-unknown}" >&2
  exit 2
fi

MODULE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CLASS_DIR="$MODULE_DIR/target/classes"
MAIN="bg.hristomanov.education.performance.gc.ReferenceUpdateWorkload"
if [[ ! -f "$CLASS_DIR/bg/hristomanov/education/performance/gc/ReferenceUpdateWorkload.class" ]]; then
  echo "First build: mvn -pl java/jvm/performance-evolution -am verify (Java 25)" >&2
  exit 2
fi
OUT="$MODULE_DIR/target/zgc-28-ea"
mkdir -p "$OUT"
"$JAVA" -version >&2
"$JAVA" -XX:+UseZGC -XX:+ZAdaptiveHeapSizing -XX:+PrintFlagsFinal -version \
  > "$OUT/available-flags.txt" 2>&1 || {
    echo "JDK 28 EA build does not support this ZAdaptiveHeapSizing experiment" >&2
    cat "$OUT/available-flags.txt" >&2
    exit 2
  }
grep -Eq 'ZAdaptiveHeapSizing[[:space:]]*=[[:space:]]*true' "$OUT/available-flags.txt" || {
  echo "Adaptive ZGC flag is not actually enabled" >&2
  exit 2
}

run_case() {
  local label="$1" flag="$2"
  "$JAVA" -XX:+UseZGC "$flag" -XX:ActiveProcessorCount=2 \
    -Xms64m -Xmx256m \
    "-Xlog:gc*:file=$OUT/$label-gc.log:uptime,level,tags" \
    -cp "$CLASS_DIR" "$MAIN" 2048 500000 923 \
    | tee "$OUT/$label-output.txt"
  grep -E '^CHECKSUM=' "$OUT/$label-output.txt" | tail -1
}
BASELINE="$(run_case default-zgc -XX:-ZAdaptiveHeapSizing)"
ADAPTIVE="$(run_case adaptive-zgc -XX:+ZAdaptiveHeapSizing)"
if [[ -z "$BASELINE" || "$BASELINE" != "$ADAPTIVE" ]]; then
  echo "Correctness mismatch: $BASELINE vs $ADAPTIVE" >&2
  exit 1
fi
echo "PASS: matching $BASELINE; logs at $OUT"
echo 'Not a proof of p99, throughput, memory savings or beneficial adaptive behavior.'
