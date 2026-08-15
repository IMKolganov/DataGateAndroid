#!/usr/bin/env bash
# Build or fetch libXray Android AAR into app/libs/libxray.aar
#
# Upstream submodule: native-libxray (https://github.com/XTLS/libXray)
# Prefer: python3 build/main.py android inside the submodule (requires Go + Android NDK)
# Fallback: download matching GitHub release zip (no Go required)
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
SUBMODULE="${REPO_ROOT}/native-libxray"
OUT_AAR="${REPO_ROOT}/app/libs/libxray.aar"
RELEASE_TAG="${LIBXRAY_RELEASE_TAG:-v26.7.28}"

mkdir -p "${REPO_ROOT}/app/libs"

if [[ "${1:-}" == "download" ]] || ! command -v go >/dev/null 2>&1; then
  echo "Downloading libXray Android AAR (${RELEASE_TAG})..."
  TMP="$(mktemp -d)"
  trap 'rm -rf "$TMP"' EXIT
  ZIP_URL="https://github.com/XTLS/libXray/releases/download/${RELEASE_TAG}/libxray-android.zip"
  curl -fsSL "$ZIP_URL" -o "$TMP/libxray-android.zip"
  unzip -qo "$TMP/libxray-android.zip" -d "$TMP/out"
  AAR="$(find "$TMP/out" -name '*.aar' | head -n1)"
  if [[ -z "$AAR" ]]; then
    echo "No .aar found in release zip" >&2
    exit 1
  fi
  cp -f "$AAR" "$OUT_AAR"
  echo "Wrote $OUT_AAR"
  exit 0
fi

if [[ ! -d "$SUBMODULE" ]]; then
  echo "Missing submodule at $SUBMODULE (git submodule update --init native-libxray)" >&2
  exit 1
fi

echo "Building libXray from source (Go $(go version))..."
cd "$SUBMODULE"
python3 build/main.py android
BUILT="$(find "$SUBMODULE" -name 'libXray.aar' -o -name 'libxray.aar' | head -n1)"
if [[ -z "$BUILT" ]]; then
  echo "Build finished but AAR not found; try: $0 download" >&2
  exit 1
fi
cp -f "$BUILT" "$OUT_AAR"
echo "Wrote $OUT_AAR"
