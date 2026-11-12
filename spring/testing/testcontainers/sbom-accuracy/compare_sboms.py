#!/usr/bin/env python3
"""Offline CycloneDX runtime-component reconciliation; evidence, not proof of completeness.

An explicit reviewed expected-runtime list is essential. Maven BOM scope cannot on its
own tell you which OS/JRE packages a deployed image contains.
"""
import argparse
import json
from pathlib import Path


def normalized_purl(value: str) -> str:
    """Discard qualifiers/subpath but preserve ecosystem, namespace, name and version."""
    return value.split("#", 1)[0].split("?", 1)[0]


def components(path: Path) -> set[str]:
    document = json.loads(path.read_text(encoding="utf-8"))
    if document.get("bomFormat") != "CycloneDX" or not isinstance(document.get("components"), list):
        raise ValueError(f"{path}: expected CycloneDX JSON components array")
    values = set()
    for component in document["components"]:
        purl = component.get("purl")
        if isinstance(purl, str) and purl.startswith("pkg:") and "@" in purl:
            values.add(normalized_purl(purl))
    return values


def expected_components(path: Path) -> set[str]:
    document = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(document, list) or not all(isinstance(v, str) and v.startswith("pkg:") and "@" in v for v in document):
        raise ValueError(f"{path}: expected JSON array of versioned PURLs")
    if len(document) != len(set(document)):
        raise ValueError(f"{path}: duplicated expected PURLs")
    return {normalized_purl(value) for value in document}


def reconcile(required: set[str], source: set[str], release: set[str]) -> dict[str, set[str]]:
    return {"missing_in_source": required - source,
            "missing_in_release": required - release,
            "unreviewed_in_release": release - required}


def main() -> int:
    parser = argparse.ArgumentParser(description="Compare reviewed runtime PURLs to source and release CycloneDX BOM")
    parser.add_argument("--source", required=True, type=Path)
    parser.add_argument("--release", required=True, type=Path)
    parser.add_argument("--expected", required=True, type=Path)
    arguments = parser.parse_args()
    try:
        result = reconcile(expected_components(arguments.expected),
                           components(arguments.source), components(arguments.release))
    except (OSError, ValueError, json.JSONDecodeError) as exc:
        parser.error(str(exc))
    for category, values in result.items():
        print(f"{category}: {len(values)}")
        for item in sorted(values):
            print(f"  {item}")
    if result["missing_in_source"] or result["missing_in_release"]:
        print("FAIL: reviewed runtime components are missing; investigate tool scopes or release contents")
        return 2
    print("PASS: the reviewed PURLs are present in both BOMs; this does NOT prove total SBOM completeness")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
