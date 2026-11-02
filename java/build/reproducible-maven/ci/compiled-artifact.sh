#!/usr/bin/env bash
set -euo pipefail

# Run from java/build/reproducible-maven.
# This protects compile-once artifact reuse within a trusted CI run, not
# independent third-party supply-chain attestation.
action="${1:-}"
if [[ "$action" != "record" && "$action" != "verify" ]]; then
  echo "Usage: bash ci/compiled-artifact.sh record|verify" >&2
  exit 2
fi

if [[ ! -d target/classes || ! -d target/test-classes ]]; then
  echo "Missing compiled classes/test-classes" >&2
  exit 1
fi
if ! find target/classes -name '*.class' -type f | grep -q .; then
  echo "Missing main .class files" >&2
  exit 1
fi
if ! find target/test-classes -name '*.class' -type f | grep -q .; then
  echo "Missing test .class files" >&2
  exit 1
fi

commit="$(git rev-parse HEAD)"
jdk="$(java -version 2>&1 | sed -n '1p')"
maven="$(mvn -version | sed -n '1p')"
pom_digest="$(sha256sum pom.xml | awk '{print $1}')"

# A SHA without a clean checkout is insufficient; generated target/ is ignored.
if [[ -n "$(git status --porcelain --untracked-files=normal -- pom.xml src/ ci/)" ]]; then
  echo "Uncommitted changes to module inputs are not allowed" >&2
  exit 1
fi

if [[ "$action" == "record" ]]; then
  {
    printf 'git=%s\n' "$commit"
    printf 'jdk=%s\n' "$jdk"
    printf 'maven=%s\n' "$maven"
    printf 'pom=%s\n' "$pom_digest"
  } > target/ci-build-receipt.txt

  find target/classes target/test-classes -type f -print0 |
    LC_ALL=C sort -z |
    xargs -0 sha256sum > target/ci-classes.sha256

  echo "Recorded source/toolchain identity and class checksums"
  exit 0
fi

if [[ ! -s target/ci-build-receipt.txt || ! -s target/ci-classes.sha256 ]]; then
  echo "Missing build receipt or class-file checksums" >&2
  exit 1
fi

expected="$(printf 'git=%s\njdk=%s\nmaven=%s\npom=%s\n' \
    "$commit" "$jdk" "$maven" "$pom_digest")"
actual="$(cat target/ci-build-receipt.txt)"
if [[ "$expected" != "$actual" ]]; then
  echo "Refusing stale artifact: commit, JDK, Maven or POM mismatch" >&2
  exit 1
fi
sha256sum -c --quiet target/ci-classes.sha256

# sha256sum -c validates only entries already present in the manifest.
# Reject newly added files as well: a stray .class or test resource must never
# be silently accepted when restoring target/ in another CI job.
if ! diff -u target/ci-classes.sha256 <(
    find target/classes target/test-classes -type f -print0 |
      LC_ALL=C sort -z |
      xargs -0 sha256sum
  ); then
  echo "Refusing unlisted, missing or modified files in compiled output" >&2
  exit 1
fi

echo "PASS: bytecode file set, checksums and build inputs all match"
