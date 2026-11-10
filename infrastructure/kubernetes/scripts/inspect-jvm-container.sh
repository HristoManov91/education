#!/usr/bin/env bash
# Requires a built local education-kubernetes-lab:dev image and Docker Engine.
set -euo pipefail
image="${IMAGE_NAME:-education-kubernetes-lab:dev}"
for percentage in 25.0 60.0; do
  echo "=== JVM under 512Mi cgroup with MaxRAMPercentage=$percentage ==="
  docker run --rm --memory=512m --cpus=1 \
    --entrypoint java "$image" \
    "-XX:MaxRAMPercentage=$percentage" -XX:+PrintFlagsFinal -version 2>&1 \
    | grep -E 'MaxRAMPercentage|MaxHeapSize|UseSerialGC|UseG1GC' || true
done
# Do not assert speedups or compare OS RSS based on PrintFlagsFinal alone.
