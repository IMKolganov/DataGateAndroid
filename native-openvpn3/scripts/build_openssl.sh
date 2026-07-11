#!/bin/bash
set -e

ABI=$1
if [ -z "$ABI" ]; then
    ABI="x86_64"
fi

NDK_PATH="/home/imkolganov/Android/Sdk/ndk/26.1.10909125"
INSTALL_DIR="$HOME/Android/deps/$ABI/openssl"
OPENSSL_VER="3.0.15"

mkdir -p "$INSTALL_DIR"
cd /tmp

if [ ! -d "openssl-$OPENSSL_VER" ]; then
    wget https://github.com/openssl/openssl/releases/download/openssl-$OPENSSL_VER/openssl-$OPENSSL_VER.tar.gz
    tar -xf openssl-$OPENSSL_VER.tar.gz
fi

cd openssl-$OPENSSL_VER

# Source dir is reused across ABIs; without cleaning, object files from a
# previous ABI's build linger and get linked into the new target (e.g.
# "incompatible with armelf_linux_eabi"), so always start from a clean tree.
if [ -f Makefile ]; then
    make distclean
fi

TOOLCHAIN="$NDK_PATH/toolchains/llvm/prebuilt/linux-x86_64"
export PATH="$TOOLCHAIN/bin:$PATH"

# OpenSSL uses ANDROID_NDK_ROOT
export ANDROID_NDK_ROOT="$NDK_PATH"

if [ "$ABI" == "x86" ]; then
    TARGET="android-x86"
elif [ "$ABI" == "armeabi-v7a" ]; then
    TARGET="android-arm"
elif [ "$ABI" == "arm64-v8a" ]; then
    TARGET="android-arm64"
else
    TARGET="android-x86_64"
fi

./Configure "$TARGET" \
    -D__ANDROID_API__=24 \
    no-shared \
    no-tests \
    --prefix="$INSTALL_DIR" \
    --openssldir="$INSTALL_DIR"

make -j$(nproc)
make install_sw

echo "OpenSSL for $ABI built and installed to $INSTALL_DIR"
