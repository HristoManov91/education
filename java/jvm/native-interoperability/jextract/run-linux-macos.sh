#!/usr/bin/env bash
set -euo pipefail

# Optional: requires a Java 25-compatible jextract tool and an installed
# C compiler. This script is NOT called by the default Maven/CI reactor.
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
mkdir -p "$root/target/native" "$root/target/jextract-generated" "$root/target/jextract-classes"

for executable in cc jextract java javac; do
  if ! command -v "$executable" >/dev/null 2>&1; then
    echo "Missing tool: $executable. See JEXTRACT-LAB.md" >&2
    exit 2
  fi
done

if ! javac -version 2>&1 | grep -q '^javac 25'; then
  echo "Use Java 25 for this example; choose matching jextract EA" >&2
  exit 2
fi

case "$(uname -s)" in
  Linux)
    library="$root/target/native/libeducation_math.so"
    cc -Wall -Wextra -Werror -fPIC -shared -o "$library" "$root/jextract/native_math.c"
    ;;
  Darwin)
    library="$root/target/native/libeducation_math.dylib"
    cc -Wall -Wextra -Werror -dynamiclib -o "$library" "$root/jextract/native_math.c"
    ;;
  *)
    echo "This optional script supports Linux/macOS; see the lab for Windows guidance." >&2
    exit 2
    ;;
esac

# Do not put generated bindings or native binaries in src/main or Git.
# The ':' prefix is part of jextract's explicit absolute-library-path syntax.
jextract \
  --target-package bg.hristomanov.education.ffi.generated \
  --output "$root/target/jextract-generated" \
  --library ":$library" \
  "$root/jextract/native_math.h"

mapfile_supported=true
sources=()
while IFS= read -r -d '' source_file; do
  sources+=("$source_file")
done < <(find "$root/target/jextract-generated" -name '*.java' -type f -print0)

if (( ${#sources[@]} == 0 )); then
  echo "jextract produced no Java bindings" >&2
  exit 1
fi

javac --release 25 \
  -d "$root/target/jextract-classes" \
  "${sources[@]}" \
  "$root/jextract/GeneratedBindingSmoke.java"

java --enable-native-access=ALL-UNNAMED \
  -cp "$root/target/jextract-classes" \
  bg.hristomanov.education.ffi.manual.GeneratedBindingSmoke
