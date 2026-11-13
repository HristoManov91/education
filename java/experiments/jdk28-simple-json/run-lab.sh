#!/usr/bin/env bash
# JEP 540 - Java 28 EA ONLY. Never compile incubator sources in Java 25 reactor.
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUTPUT="$HERE/target/jdk28-json"
mkdir -p "$OUTPUT"

JAVA_MAJOR="$(java -XshowSettings:properties -version 2>&1 | sed -n 's/^[[:space:]]*java.specification.version = //p' | head -1)"
if [[ "$JAVA_MAJOR" != "28" ]]; then
  echo "Expected OpenJDK 28 Early Access, detected java.specification.version=$JAVA_MAJOR" >&2
  exit 2
fi

if ! java --list-modules | grep -q '^jdk.incubator.json@'; then
  echo "Missing real jdk.incubator.json module; this is not a JEP 540-capable JDK" >&2
  exit 2
fi

echo "Compiling genuine JEP 540 source under $(java --version | head -n 1)"
javac --add-modules jdk.incubator.json -d "$OUTPUT" "$HERE"/src/*.java

echo "Running strict JSON, numeric and immutability tests"
java --add-modules jdk.incubator.json -cp "$OUTPUT" Jdk28SimpleJsonLab

echo "Generating real JSON thread dump and parsing it"
java --add-modules jdk.incubator.json -cp "$OUTPUT" Jdk28JsonThreadDumpLab
