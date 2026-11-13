"""Offline CycloneDX 1.6 VEX/backport *evidence consistency* gate.

This educational checker is deliberately NOT a CycloneDX schema validator,
a signature/provenance verifier, a vulnerability scanner, or proof that a
fix works. A successful review means "READY_FOR_HUMAN_REVIEW", not "SAFE".

All CVE-2099-... identifiers in the fixtures are intentionally fictional.
No third-party packages required. Cross-BOM references use BOM-Link:
urn:cdx:<SBOM UUID>/<SBOM version>#<component bom-ref>.
"""
from __future__ import annotations

import argparse
import base64
import binascii
from dataclasses import dataclass
from datetime import date
import hashlib
import json
from pathlib import Path
import re
from typing import Mapping

MAX_JSON_BYTES = 512 * 1024
MAX_BLOB_BYTES = 2 * 1024 * 1024
SHA256 = re.compile(r"[0-9a-f]{64}\Z")
CVE = re.compile(r"CVE-[0-9]{4}-[0-9]{4,}\Z")
SBOM_ID = re.compile(r"urn:uuid:[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\Z")


class EvidenceError(ValueError):
    """The submitted review contradicts its declared release or lacks evidence."""


@dataclass(frozen=True)
class GateResult:
    artifact_sha256: str
    reviewed_findings: int
    states: tuple[str, ...]


def demand(value: bool, message: str) -> None:
    if not value:
        raise EvidenceError(message)


def is_hash(value: object) -> bool:
    return isinstance(value, str) and SHA256.fullmatch(value) is not None


def digest(blob: bytes) -> str:
    return hashlib.sha256(blob).hexdigest()


def unique_index(items: object, key: str, subject: str) -> dict[str, dict]:
    demand(isinstance(items, list) and len(items) > 0, f"{subject} must contain entries")
    entries: dict[str, dict] = {}
    for obj in items:
        demand(isinstance(obj, dict), f"{subject} entry must be an object")
        ident = obj.get(key)
        demand(isinstance(ident, str) and bool(ident.strip()), f"{subject} {key} is required")
        demand(ident not in entries, f"Duplicate {subject} {key}: {ident}")
        entries[ident] = obj
    return entries


def load_json(path: Path) -> dict:
    demand(path.is_file() and not path.is_symlink(), f"Missing JSON file: {path.name}")
    demand(0 < path.stat().st_size <= MAX_JSON_BYTES, f"JSON file too large or empty: {path.name}")
    try:
        obj = json.loads(path.read_text(encoding="utf-8"))
    except (UnicodeDecodeError, ValueError) as exc:
        raise EvidenceError(f"Invalid JSON: {path.name}") from exc
    demand(isinstance(obj, dict), f"Expected JSON object: {path.name}")
    return obj


def load_blob(path: Path) -> bytes:
    demand(path.is_file() and not path.is_symlink(), f"Missing evidence blob: {path.name}")
    demand(0 < path.stat().st_size <= MAX_BLOB_BYTES, f"Evidence blob too large/empty: {path.name}")
    return path.read_bytes()


def declared_sha256(component: object, label: str) -> str:
    demand(isinstance(component, dict), f"Missing {label} metadata.component")
    hashes = component.get("hashes")
    demand(isinstance(hashes, list), f"{label} lacks SHA-256 digest")
    values = [h.get("content") for h in hashes
              if isinstance(h, dict) and h.get("alg") == "SHA-256"]
    demand(len(values) == 1 and is_hash(values[0]),
           f"{label} must declare exactly one SHA-256 digest")
    return values[0]


def check_bom_header(bom: dict, label: str) -> None:
    demand(bom.get("bomFormat") == "CycloneDX" and bom.get("specVersion") == "1.6",
           f"{label} must be CycloneDX 1.6")
    demand(type(bom.get("version")) is int and bom["version"] >= 1,
           f"{label} BOM version must be a positive integer")


def check_review(
    sbom: dict, vex: dict, evidence: dict, blobs: Mapping[str, bytes], *,
    as_of: date,
) -> GateResult:
    """Validate reviewed claims bound to the exact release and SBOM component.

    The caller supplies four independent local evidence bytes, *not* paths
    obtained from untrusted VEX JSON.
    """
    check_bom_header(sbom, "SBOM")
    check_bom_header(vex, "VEX")
    serial = sbom.get("serialNumber")
    demand(isinstance(serial, str) and SBOM_ID.fullmatch(serial) is not None,
           "SBOM needs a valid serialNumber for BOM-Link")
    demand(evidence.get("sbom") == {"serialNumber": serial, "version": sbom["version"]},
           "Review refers to another SBOM identity or version")

    expected_blob_names = {"artifact", "patch", "regression", "reachability"}
    demand(set(blobs) == expected_blob_names, "Review requires exactly four supplied evidence blobs")
    for name, value in blobs.items():
        demand(isinstance(value, bytes) and 0 < len(value) <= MAX_BLOB_BYTES,
               f"Invalid evidence bytes: {name}")

    release = evidence.get("release")
    demand(isinstance(release, dict), "Missing reviewed release")
    release_name, release_version = release.get("name"), release.get("version")
    actual_artifact_sha = digest(blobs["artifact"])
    demand(is_hash(release.get("sha256")) and release["sha256"] == actual_artifact_sha,
           "Reviewed release digest does not match artifact bytes")

    sbom_product = sbom.get("metadata", {}).get("component")
    vex_product = vex.get("metadata", {}).get("component")
    for label, product in (("SBOM", sbom_product), ("VEX", vex_product)):
        demand(isinstance(product, dict) and product.get("type") == "application",
               f"{label} metadata.component must identify an application")
        demand(product.get("name") == release_name
               and product.get("version") == release_version,
               f"{label} product identity does not match reviewed release")
        demand(declared_sha256(product, label) == actual_artifact_sha,
               f"{label} product digest does not match exact release artifact")

    index = unique_index(sbom.get("components"), "bom-ref", "SBOM components")
    purls = [c.get("purl") for c in index.values()]
    demand(all(isinstance(p, str) and p for p in purls)
           and len(set(purls)) == len(purls),
           "Missing or duplicate PURL in reviewed SBOM")
    reviewed_purl = evidence.get("component_purl")
    matches = [c for c in index.values() if c.get("purl") == reviewed_purl]
    demand(isinstance(reviewed_purl, str) and reviewed_purl.startswith("pkg:maven/")
           and len(matches) == 1,
           "Reviewed component PURL must resolve to exactly one SBOM component")
    component = matches[0]
    ref = component["bom-ref"]
    expected_link = f"urn:cdx:{serial.removeprefix('urn:uuid:')}/{sbom['version']}#{ref}"

    vex_findings = unique_index(vex.get("vulnerabilities"), "id", "VEX findings")
    decision_list = evidence.get("decisions")
    decisions = unique_index(decision_list, "id", "review decisions")
    demand(set(decisions) == set(vex_findings),
           "Every VEX finding needs exactly one reviewed decision; no extras allowed")
    demand(1 <= len(decisions) <= 8, "Too many findings for the education lab")

    for vid, claim in vex_findings.items():
        demand(CVE.fullmatch(vid) is not None, f"Invalid CVE identifier: {vid}")
        affects = claim.get("affects")
        demand(isinstance(affects, list) and len(affects) == 1
               and isinstance(affects[0], dict) and affects[0].get("ref") == expected_link,
               f"{vid} does not target the exact SBOM component via BOM-Link")
        analysis = claim.get("analysis")
        demand(isinstance(analysis, dict), f"{vid}: missing VEX analysis")
        state = analysis.get("state")
        demand(state in {"resolved_with_pedigree", "not_affected"},
               f"{vid}: in_triage/exploitable/unknown is NOT cleared")
        demand(isinstance(analysis.get("detail"), str)
               and len(analysis["detail"].strip()) >= 40,
               f"{vid}: vague or absent VEX analysis detail")
        decision = decisions[vid]
        demand(decision.get("state") == state,
               f"{vid}: VEX state and human decision disagree")
        demand(isinstance(decision.get("owner"), str)
               and len(decision["owner"].strip()) >= 4,
               f"{vid}: missing accountable owner")
        try:
            reviewed_at = date.fromisoformat(decision["reviewed_at"])
            expires_at = date.fromisoformat(decision["expires_at"])
        except (KeyError, TypeError, ValueError) as exc:
            raise EvidenceError(f"{vid}: invalid review/expiration date") from exc
        demand(reviewed_at <= as_of <= expires_at,
               f"{vid}: review not yet issued or expired; re-triage required")

        if state == "not_affected":
            demand(analysis.get("justification") == "code_not_reachable",
                   f"{vid}: not_affected must explain unreachable code for this lab")
            demand(decision.get("reachability_sha256") == digest(blobs["reachability"]),
                   f"{vid}: reachability report bytes disagree with reviewed evidence")
            report = blobs["reachability"].decode("utf-8", errors="replace")
            demand(f"component={reviewed_purl}" in report
                   and f"vulnerability={vid}" in report,
                   f"{vid}: reachability report not scoped to reviewed finding")
        else:
            demand(decision.get("patch_sha256") == digest(blobs["patch"]),
                   f"{vid}: backport patch bytes do not match reviewed hash")
            demand(decision.get("regression_sha256") == digest(blobs["regression"]),
                   f"{vid}: test report bytes do not match reviewed hash")
            patch_candidates = component.get("pedigree", {}).get("patches", [])
            demand(isinstance(patch_candidates, list), f"{vid}: missing patch pedigree")
            ok = False
            for patch in patch_candidates:
                if not isinstance(patch, dict) or patch.get("type") != "backport":
                    continue
                issues = patch.get("resolves", [])
                if not isinstance(issues, list) or not any(
                    isinstance(issue, dict) and issue.get("type") == "security"
                    and issue.get("id") == vid for issue in issues
                ):
                    continue
                body = patch.get("diff", {}).get("text", {})
                if not isinstance(body, dict) or body.get("encoding") != "base64":
                    continue
                try:
                    unwrapped = base64.b64decode(body["content"], validate=True)
                except (KeyError, TypeError, binascii.Error):
                    continue
                if unwrapped == blobs["patch"]:
                    ok = True
                    break
            demand(ok, f"{vid}: backport pedigree must embed the matching patch and CVE")
    return GateResult(actual_artifact_sha, len(decisions),
                      tuple(sorted(d["state"] for d in decisions.values())))


def main() -> int:
    parser = argparse.ArgumentParser(description="CycloneDX VEX evidence consistency review gate")
    parser.add_argument("--fixtures", type=Path,
                        default=Path(__file__).with_name("fixtures"))
    parser.add_argument("--as-of", default=date.today().isoformat(),
                        help="ISO date for deterministic expiry evaluation")
    args = parser.parse_args()
    try:
        as_of = date.fromisoformat(args.as_of)
        root = args.fixtures
        result = check_review(
            load_json(root / "release.cdx.json"), load_json(root / "vex.cdx.json"),
            load_json(root / "review.json"),
            {key: load_blob(root / name) for key, name in {
                "artifact": "release-artifact.txt",
                "patch": "backport.diff",
                "regression": "regression-report.txt",
                "reachability": "reachability-report.txt",
            }.items()}, as_of=as_of)
    except (EvidenceError, ValueError, OSError) as exc:
        print(f"REVIEW_BLOCKED: {exc}")
        return 2
    print(f"READY_FOR_HUMAN_REVIEW: {result.reviewed_findings} reviewed findings; "
          f"release_sha256={result.artifact_sha256}")
    print("NOT a vulnerability fix proof, full schema validation, signature check, or SLSA attestation.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
