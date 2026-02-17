#!/usr/bin/env bash
set -e

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
O3_DIR="${ROOT_DIR}/openvpn3"
OUT_JAVA="${ROOT_DIR}/out/java/net/openvpn/ovpn3"
OUT_CPP="${ROOT_DIR}/out/swig"

mkdir -p "$OUT_JAVA" "$OUT_CPP"

swig \
  -c++ \
  -java \
  -package net.openvpn.ovpn3 \
  -outdir "$OUT_JAVA" \
  -outcurrentdir \
  -DOPENVPN_PLATFORM_ANDROID \
  -I"$O3_DIR/client" \
  -I"$O3_DIR" \
  -o "$OUT_CPP/ovpncli_wrap.cxx" \
  "$O3_DIR/client/ovpncli.i"
