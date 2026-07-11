#!/bin/bash
set -e

ABI=$1
if [ -z "$ABI" ]; then
    ABI="x86_64"
fi

NDK_PATH="/home/imkolganov/Android/Sdk/ndk/26.1.10909125"
INSTALL_DIR="$HOME/Android/deps/$ABI/lz4"

mkdir -p "$INSTALL_DIR"
cd /tmp

if [ ! -d "lz4" ]; then
    git clone --depth 1 https://github.com/lz4/lz4.git
fi

cd lz4

# Setup toolchain for NDK 26+
TOOLCHAIN="$NDK_PATH/toolchains/llvm/prebuilt/linux-x86_64"
export AR="$TOOLCHAIN/bin/llvm-ar"
if [ "$ABI" == "x86" ]; then
    export CC="$TOOLCHAIN/bin/i686-linux-android24-clang"
    export CXX="$TOOLCHAIN/bin/i686-linux-android24-clang++"
elif [ "$ABI" == "armeabi-v7a" ]; then
    export CC="$TOOLCHAIN/bin/armv7a-linux-androideabi24-clang"
    export CXX="$TOOLCHAIN/bin/armv7a-linux-androideabi24-clang++"
elif [ "$ABI" == "arm64-v8a" ]; then
    export CC="$TOOLCHAIN/bin/aarch64-linux-android24-clang"
    export CXX="$TOOLCHAIN/bin/aarch64-linux-android24-clang++"
else
    export CC="$TOOLCHAIN/bin/x86_64-linux-android24-clang"
    export CXX="$TOOLCHAIN/bin/x86_64-linux-android24-clang++"
fi
export RANLIB="$TOOLCHAIN/bin/llvm-ranlib"
# Static lib gets linked into a shared .so, so it must be position-independent
# (clang defaults to non-PIC for 32-bit x86 unlike the other ABIs).
export CFLAGS="-fPIC"

make -j$(nproc) lib
mkdir -p "$INSTALL_DIR/include"
mkdir -p "$INSTALL_DIR/lib"
cp lib/lz4.h "$INSTALL_DIR/include/"
cp lib/liblz4.a "$INSTALL_DIR/lib/"

echo "LZ4 for $ABI built and installed to $INSTALL_DIR"
