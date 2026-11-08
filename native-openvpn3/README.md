# OpenVPN 3 Core — Android native build

Gradle builds `libovpncli.so` from the pinned `openvpn3` submodule and packages it in the APK. It generates the C++ JNI wrapper with SWIG as part of each ABI's CMake build; a manually copied native library is not needed.

## Prerequisites

- Initialize sources with `git submodule update --init --recursive`.
- Install SWIG on the host and Android SDK CMake 3.22.1 / NDK 29.0.14206865.
- Prepare static OpenSSL and LZ4 libraries for each selected ABI in `$HOME/Android/deps/<ABI>/{openssl,lz4}` (headers under `include`, archives under `lib`). The scripts in `scripts/` show the dependency build steps; set their NDK path for your machine.

## Build an APK

From the Android repository root:

```bash
./gradlew :app:assembleProdDebug
```

Default ABIs are `arm64-v8a` and `armeabi-v7a`. To select other ABIs:

```bash
./gradlew :app:assembleProdDebug -Pdatagate.abis=x86,x86_64
```

The native build uses the same ABI filters as the app. Gradle stages the resulting library from its CMake build directory and strips it with the configured NDK.

## Build a single native ABI manually

```bash
cmake -S native-openvpn3 -B build-arm64 \
  -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-24 \
  -DCMAKE_TOOLCHAIN_FILE="$HOME/Android/Sdk/ndk/29.0.14206865/build/cmake/android.toolchain.cmake"
cmake --build build-arm64 -j4
```

The output is `build-arm64/libovpncli.so`.

## Java bindings

Java bindings are tracked in `app/src/main/java/net/openvpn/ovpn3/`. When an upstream API change requires regenerating them, run `native-openvpn3/generate_swig.sh`, then review and copy the generated Java files from `native-openvpn3/out/java/`. Regular APK builds generate only the wrapper used by the native target; they compile the tracked Java sources.

Keep `DATAGATE_OPENVPN_VERSION` in `CMakeLists.txt` aligned with the core pin and the app's `versionName`; the value is reported to the VPN server as `IV_VER`.
