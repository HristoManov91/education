#!/usr/bin/env bash
# Изпълнява истински OTel auto-instrumentation smoke test с външен агент.
# Не изтегля "latest" автоматично и не изисква collector/Redis.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MODULE_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
APP_JAR="$MODULE_DIR/target/otel-agent-demo-1.0-SNAPSHOT.jar"
PORT="${LAB_HTTP_PORT:-18086}"

if [[ -z "${OTEL_AGENT_JAR:-}" || ! -f "$OTEL_AGENT_JAR" ]]; then
  echo "Set OTEL_AGENT_JAR to a downloaded, pinned OpenTelemetry javaagent jar." >&2
  exit 2
fi
if [[ ! -f "$APP_JAR" ]]; then
  echo "Build first: mvn -pl spring/observability/otel-agent-demo package" >&2
  exit 2
fi
if ! command -v curl >/dev/null 2>&1; then
  echo "curl is required for this smoke test." >&2
  exit 2
fi

LOG_FILE="$(mktemp)"
APP_PID=""
cleanup() {
  if [[ -n "$APP_PID" ]]; then
    kill "$APP_PID" 2>/dev/null || true
    wait "$APP_PID" 2>/dev/null || true
  fi
  echo "Agent logs: $LOG_FILE"
}
trap cleanup EXIT

# Въвеждаме само промените за trace export; metrics/log exporters са спрени
# за минимален и проверим output, не като production default.
java -javaagent:"$OTEL_AGENT_JAR" \
  -Dotel.service.name=education-agent-smoke \
  -Dotel.traces.exporter=console \
  -Dotel.metrics.exporter=none \
  -Dotel.logs.exporter=none \
  -Dotel.bsp.schedule.delay=500 \
  -jar "$APP_JAR" \
  --server.port="$PORT" \
  --lab.price-base-url="http://127.0.0.1:$PORT" \
  --lab.redis.enabled=false >"$LOG_FILE" 2>&1 &
APP_PID=$!

READY=false
for attempt in $(seq 1 90); do
  if curl --fail --silent --output /dev/null "http://127.0.0.1:$PORT/internal/prices/widget"; then
    READY=true
    break
  fi
  if ! kill -0 "$APP_PID" 2>/dev/null; then
    echo "App terminated before readiness" >&2
    tail -n 80 "$LOG_FILE" >&2
    exit 1
  fi
  sleep 1
done
if [[ "$READY" != "true" ]]; then
  echo "App did not become ready" >&2
  tail -n 80 "$LOG_FILE" >&2
  exit 1
fi

RESULT="$(curl --fail --silent "http://127.0.0.1:$PORT/api/quotes/widget")"
echo "$RESULT"
if [[ "$RESULT" != *'"totalCents":2500'* || "$RESULT" != *'"cached":false'* ]]; then
  echo "Wrong business response" >&2
  exit 1
fi

sleep 3
SPANS="$(grep -c 'LoggingSpanExporter' "$LOG_FILE" || true)"
if (( SPANS < 2 )); then
  echo "Expected at least two exported HTTP spans; found $SPANS" >&2
  tail -n 100 "$LOG_FILE" >&2
  exit 1
fi
echo "OTel smoke OK: $SPANS exported spans in logs."
