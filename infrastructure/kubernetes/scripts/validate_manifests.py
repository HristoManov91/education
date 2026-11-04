"""Offline structural assertions for the teaching Kubernetes manifests (not a substitute for API admission)."""
from pathlib import Path
import yaml

MANIFESTS = Path(__file__).resolve().parents[1] / "manifests"


def inspect(manifests=MANIFESTS):
    issues = []
    docs = {}
    for path in sorted(manifests.glob("*.yaml")):
        with path.open(encoding="utf-8") as stream:
            for item in yaml.safe_load_all(stream):
                if item:
                    docs[item["kind"]] = item
    for kind in ("Namespace", "ConfigMap", "Service", "Deployment"):
        if kind not in docs:
            issues.append(f"missing {kind}")
    if issues:
        return issues
    deployment = docs["Deployment"]
    spec = deployment["spec"]
    pod = spec["template"]
    pod_spec = pod["spec"]
    container, = pod_spec["containers"]
    if spec.get("replicas", 0) < 2:
        issues.append("need two replicas for rolling update lab")
    if spec["selector"]["matchLabels"] != pod["metadata"]["labels"]:
        issues.append("deployment selector and pod labels differ")
    if docs["Service"]["spec"]["selector"] != pod["metadata"]["labels"]:
        issues.append("service selector must match pod labels")
    if docs["Namespace"]["metadata"]["labels"].get("pod-security.kubernetes.io/enforce") != "restricted":
        issues.append("Pod Security restricted namespace missing")
    if not pod_spec.get("securityContext", {}).get("runAsNonRoot"):
        issues.append("runAsNonRoot missing")
    if pod_spec.get("automountServiceAccountToken") is not False:
        issues.append("service account token must not auto-mount")
    sec = container.get("securityContext", {})
    if sec.get("allowPrivilegeEscalation") is not False or sec.get("readOnlyRootFilesystem") is not True:
        issues.append("container privilege/escalation/file-system hardening missing")
    if "ALL" not in sec.get("capabilities", {}).get("drop", []):
        issues.append("all capabilities should be dropped")
    if pod_spec.get("securityContext", {}).get("seccompProfile", {}).get("type") != "RuntimeDefault":
        issues.append("seccomp must be RuntimeDefault")
    for name in ("livenessProbe", "readinessProbe", "startupProbe"):
        if name not in container:
            issues.append(f"missing {name}")
    if "resources" not in container or "requests" not in container["resources"] or "limits" not in container["resources"]:
        issues.append("requests and limits required")
    if not pod_spec.get("terminationGracePeriodSeconds"):
        issues.append("termination grace missing")
    if container["image"] != "education-kubernetes-lab:dev":
        issues.append("kind smoke expects an explicitly local loaded image")
    for variable in container.get("env", []):
        if variable["name"] == "APP_GREETING" and "configMapKeyRef" not in variable.get("valueFrom", {}):
            issues.append("APP_GREETING must reference ConfigMap")
    if "JDK_JAVA_OPTIONS" not in docs["ConfigMap"]["data"]:
        issues.append("runtime JDK_JAVA_OPTIONS missing")
    return issues


if __name__ == "__main__":
    errors = inspect()
    if errors:
        raise SystemExit("MANIFEST FAIL:\n" + "\n".join(errors))
    print("MANIFEST PASS: selectors, probes, resources, security and config")
