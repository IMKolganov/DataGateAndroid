# OpenVPN 3 Core — Android Native Build

This module builds OpenVPN 3 Core as a native library for Android and generates a Java/JNI API via SWIG.

## 1. Generate SWIG JNI wrapper

```
cd native-openvpn3
./generate_swig.sh
```

Creates:
- `out/swig/ovpncli_wrap.cxx`
- `out/java/net/openvpn/ovpn3/*.java`

## 2. Build native libraries

```
cd ~/Android/DataGateOpenVpn3

export ANDROID_NDK_HOME="$HOME/Android/Sdk/ndk/26.1.10909125"

cmake \
-DANDROID_ABI=arm64-v8a \
-DANDROID_PLATFORM=android-24 \ 
-DCMAKE_TOOLCHAIN_FILE=$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake \ 
-B build-arm64\ 
-S native-openvpn3

cmake --build build-arm64 -j$(nproc)
```

After assembly:
`out/arm64-v8a/libovpncli.so`

## 3. Files for Android

JNI library:
```
app/src/main/jniLibs/arm64-v8a/libovpncli.so
```

Java API:
```
app/src/main/java/net/openvpn/ovpn3/*.java
```

## 4. Use in Kotlin

```
System.loadLibrary("ovpncli")

val client = OpenVPNClient()
val config = ClientAPI_Config()
config.content = ovpnProfileString

val eval = client.eval_config(config)
if (eval.error) throw RuntimeException(eval.message)

client.connect()
```

## 5. Structure

```
native-openvpn3/ 
openvpn3/ 
third_party/ 
out/ 
java/ 
swig/ 
CMakeLists.txt 
generate_swig.sh
```