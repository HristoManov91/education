#!/usr/bin/env bash
# Execute from repository root. Requires docker, kind, kubectl, curl and bash.
set -euo pipefail
CLUSTER_NAME="${CLUSTER_NAME:-education-java}"
IMAGE_NAME="education-kubernetes-lab:dev"
MODULE="infrastructure/kubernetes"

# No cluster creation here: keeps ownership explicit for CI and developers.
kind get clusters | grep -Fxq "$CLUSTER_NAME" || {
  echo "Create the cluster: kind create cluster --name $CLUSTER_NAME" >&2
  exit 1
}
docker build --tag "$IMAGE_NAME" "$MODULE"
kind load docker-image "$IMAGE_NAME" --name "$CLUSTER_NAME"
for file in 00-namespace 10-configmap 20-service 30-deployment; do
  kubectl apply -f "$MODULE/manifests/$file.yaml"
done
# A locally loaded mutable :dev tag is not enough to trigger a new rollout by itself.
kubectl -n education rollout restart deployment/java-kubernetes-lab
kubectl -n education rollout status deployment/java-kubernetes-lab --timeout=240s
kubectl -n education get deployment,replicaset,pods,services

LOG=$(mktemp)
# Choose a free local port via kubectl's :0 port forward feature? 18080 is deliberately
# fixed for a simple example, and errors are surfaced in the log if it is occupied.
kubectl -n education port-forward service/java-kubernetes-lab 18080:80 >"$LOG" 2>&1 &
FORWARD_PID=$!
cleanup() { kill "$FORWARD_PID" 2>/dev/null || true; wait "$FORWARD_PID" 2>/dev/null || true; rm -f "$LOG"; }
trap cleanup EXIT
success=0
for attempt in $(seq 1 40); do
  if ! kill -0 "$FORWARD_PID" 2>/dev/null; then cat "$LOG" >&2; exit 1; fi
  if curl --fail --silent --max-time 3 http://127.0.0.1:18080/actuator/health/readiness | grep -q UP; then
    success=1
    break
  fi
  sleep 2
done
if [ "$success" -ne 1 ]; then cat "$LOG" >&2; exit 1; fi
curl --fail --silent --show-error http://127.0.0.1:18080/api/hello | tee /dev/stderr | grep -q 'Hello from a Kubernetes ConfigMap'
curl --fail --silent --show-error http://127.0.0.1:18080/api/work?delayMs=0 | grep -q 'completed'
echo 'KIND_SMOKE_PASS: deployment ready; config, HTTP and in-flight endpoint work'
