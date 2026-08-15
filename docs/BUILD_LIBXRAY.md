# libXray (Android)

Official Xray-core mobile wrapper used by DataGate Android.

- Upstream: https://github.com/XTLS/libXray
- Core: https://github.com/XTLS/Xray-core
- Submodule: [`native-libxray`](../native-libxray) (same pattern as `native-openvpn3/openvpn3`)

Pinned release for the shipped AAR: **v26.7.28** (`LIBXRAY_RELEASE_TAG`), matching submodule tag **v1.260728.0**.

## Produce `app/libs/libxray.aar`

```bash
# Preferred when Go + NDK are installed (builds from the submodule checkout):
./scripts/libxray/build-android.sh

# Or download the official GitHub release AAR (no Go required):
./scripts/libxray/build-android.sh download
```

Gradle requires `app/libs/libxray.aar` (fail-fast if missing).

## Notes

- Do not load another independently built Go/gomobile library in the same process (OpenVPN3 is C++ — OK).
- API used by the app: `libXray.LibXray.invoke(json)` with methods `convertShareLinksToXrayJson`, `runXrayFromJson`, `stopXray` (apiVersion 0/1 on this release).
