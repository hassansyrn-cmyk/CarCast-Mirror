# CarCast Mirror beta-readiness audit

**Branch:** `release/beta-readiness`  
**Baseline:** `v0.2-browser-av-ui-stable` and native receiver compatibility work  
**Target:** Android 11+ runtime support, compile/target SDK 36, internal beta distribution

## Executive status

CarCast is suitable for a controlled internal beta after a fresh physical regression pass. It is **not yet ready for unrestricted Google Play production publication** because signing ownership, Play Console setup, privacy-policy hosting, Data Safety submission, and broader device validation remain release-owner tasks.

## Completed in this milestone

- Release version is `0.9.0-beta1` / version code `2`.
- Release build keeps minification and resource shrinking enabled.
- Release signing is environment-driven; no keystore or secret is checked into Git.
- WebRTC, Bouncy Castle, and the reflective audio bridge have conservative R8 keep rules.
- Removed the obsolete `ACCESS_LOCAL_NETWORK` declaration for target SDK 36; Android 17/SDK 37 permission work is explicitly deferred.
- Added `android:resizeableActivity="true"` for projection rotation and multi-window compatibility.
- Added an in-app Help/FAQ covering same-network discovery, DRM audio limits, recovery, and local-only storage.
- Added prominent in-app disclosure before screen/audio consent actions.
- Diagnostic persistence and copied reports now omit stack traces and redact local IPv4 addresses and six-digit verification values.
- Native libraries were inspected in the debug APK: `arm64-v8a`, `armeabi-v7a`, `x86`, and `x86_64` WebRTC libraries are present.

## Remaining release blockers

1. Create and protect the release keystore; configure the CI secret values listed in `RELEASE_SIGNING.md`.
2. Host and link the final privacy policy on the store listing and from an accessible in-app surface.
3. Complete Play Console Data Safety, app access, content rating, target audience, ads, and foreground-service declarations.
4. Run the physical matrix in `BETA_TEST_MATRIX.md`, including Nikai browser, Android 14/15/16 behavior, rotation, Wi-Fi loss, audio/DRM, and clean reinstall.
5. Test the signed AAB on a 64-bit-only device and verify Play Console native-library analysis.
6. Decide whether the native Android receiver remains beta-only or receives a separate TV/AAOS distribution strategy.

## Honest validation boundary

Sandbox validation can prove compilation, unit tests, manifest merge, R8 configuration, artifact generation, and static security checks. It cannot prove MediaProjection behavior, hardware encoder stability, WebRTC interoperability, audio policy behavior, or Play review acceptance. Those remain physical-device and Play Console gates.
