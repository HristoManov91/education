#!/usr/bin/env bash
# JDK25/JDK27 behavior + GC flag inspection, NOT a throughput benchmark.
set -euo pipefail
if [[ "$#" -ne 2 ]]; then
  echo "Usage: compare-jdks.sh <JDK25/bin/java> <JDK27/bin/java>" >&2
  exit 2
fi

MODULE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CLASSES="$MODULE_DIR/target/classes"
REPORTS="$MODULE_DIR/target/jdk25-27-comparison"
MAIN_CLASS="bg.hristomanov.education.performance.gc.ReferenceUpdateWorkload"
mkdir -p "$REPORTS"
if [[ ! -f "$CLASSES/bg/hristomanov/education/performance/gc/ReferenceUpdateWorkload.class" ]]; then
  echo "Build Java 25 module first: mvn -pl java/jvm/performance-evolution verify" >&2
  exit 2
fi

run_one() {
  local label="$1" java_bin="$2"
  if [[ ! -x "$java_bin" ]]; then
    echo "Cannot execute $java_bin" >&2
    exit 2
  fi
  echo "=== $label ===" >&2
  "$java_bin" -version >&2

  # Без този guard подаването на същия JDK два пъти може да даде
  # фалшиво зелено "JDK25 vs JDK27" сравнение.
  local expected_major="${label#jdk}"
  local actual_major
  actual_major="$("$java_bin" -XshowSettings:properties -version 2>&1 \
    | sed -nE 's/^[[:space:]]*java\.specification\.version[[:space:]]*=[[:space:]]*([0-9]+).*$/\1/p')"
  if [[ "$actual_major" != "$expected_major" ]]; then
    echo "Expected JDK major $expected_major for $label, got ${actual_major:-unknown}: $java_bin" >&2
    exit 2
  fi

  local flags
  flags="$("$java_bin" -XX:ActiveProcessorCount=1 -XX:+PrintFlagsFinal -version 2>&1 \
    | grep -E ' (UseG1GC|UseSerialGC|UseCompactObjectHeaders|UseObjectMonitorTable) ' || true)"
  echo "$flags" | tee "$REPORTS/$label-flags.txt" >&2

  local result
  result="$("$java_bin" -XX:ActiveProcessorCount=1 -Xms128m -Xmx128m \
    "-Xlog:gc*:file=$REPORTS/$label-gc.log:uptime,level,tags" \
    -cp "$CLASSES" "$MAIN_CLASS" 2048 500000 923)"
  echo "$result" >&2
  local checksum
  checksum="$(printf '%s\n' "$result" | grep '^CHECKSUM=' || true)"
  if [[ -z "$checksum" ]]; then
    echo "Expected business CHECKSUM, did not receive one" >&2
    exit 1
  fi
  printf '%s' "$checksum"
}

FIRST="$(run_one jdk25 "$1")"
SECOND="$(run_one jdk27 "$2")"
if [[ "$FIRST" != "$SECOND" ]]; then
  echo "Business correctness mismatch: $FIRST vs $SECOND" >&2
  exit 1
fi
echo "PASS: identical $FIRST; flags and GC logs: $REPORTS"
echo "Do not treat one process invocation as a p99/throughput benchmark."
