#!/usr/bin/env bash
set -euo pipefail

# JDK 28 EA ONLY. The existing valhalla-jdk28-ea.yml provisions this JDK.
# No timing, heap-size assertions or nonportable HotSpot layout flags.
LAB="$(cd "$(dirname "$0")" && pwd)"
VERSION="$(java -version 2>&1 | head -1)"
echo "JDK: $VERSION"
if ! echo "$VERSION" | grep -Eq 'version "28'; then
  echo "ERROR: this opt-in lab requires JDK 28 EA" >&2
  exit 2
fi

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
mkdir -p "$WORK/old" "$WORK/new"

# One identical pre-Valhalla-compatible Java source, two compilation modes.
javac --release 25 -d "$WORK/old" "$LAB/LoadableDescriptorsProbe.java"
javac --release 28 --enable-preview -d "$WORK/new" \
  "$LAB/LoadableDescriptorsProbe.java" "$LAB/NullableFlatLayoutPreview.java"

java --enable-preview -cp "$WORK/old" LoadableDescriptorsProbe
java --enable-preview -cp "$WORK/new" LoadableDescriptorsProbe
java --enable-preview -cp "$WORK/new" NullableFlatLayoutPreview

# Compare ACTUAL class-file metadata. The attribute permits early loading,
# but the JVM is free NOT to flatten anything in the heap.
javap -verbose -private -classpath "$WORK/old" 'LoadableDescriptorsProbe$Schedule' \
  > "$WORK/old.javap"
javap -verbose -private -classpath "$WORK/new" 'LoadableDescriptorsProbe$Schedule' \
  > "$WORK/new.javap"
javap -verbose -private -classpath "$WORK/new" 'NullableFlatLayoutPreview$PlaylistSlot' \
  > "$WORK/slot.javap"

if grep -Eq '^[[:space:]]*LoadableDescriptors:' "$WORK/old.javap"; then
  echo "ERROR: old Java 25 class unexpectedly emitted LoadableDescriptors" >&2
  exit 1
fi
grep -Eq '^[[:space:]]*LoadableDescriptors:' "$WORK/new.javap"
grep -Fq 'Ljava/time/LocalDate;' "$WORK/new.javap"
grep -Eq '^[[:space:]]*LoadableDescriptors:' "$WORK/slot.javap"

echo "PASS: preview class files carry LoadableDescriptors metadata"
echo "PASS: old/preview class files preserve logical business behavior"
echo "NOTE: metadata presence is NOT evidence of physical flattening or speedup"
