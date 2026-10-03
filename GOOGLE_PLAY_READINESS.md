# Google Play readiness

The project now uses a checked-in Gradle wrapper, JDK 17, compileSdk 36, targetSdk 36, minSdk 24, R8 release shrinking, a ProGuard file, and a MediaProjection foreground-service declaration. The app requests local-network access only when discovery or receiver operation needs it. It does not use root, Accessibility capture, hidden Android Auto APIs, DRM bypass, or background boot-started MediaProjection.

`assembleDebug` and `bundleRelease` are build-verified in this environment. The release AAB is not release-ready for publishing: it is unsigned for Play App Signing, has not passed physical-device validation, and still has incomplete browser/WebRTC, reconnect, rotation, and multi-provider adapters. Play review for car categories is separate and must not be implied by a successful Gradle build.

## Current beta audit update — October 2026

Google's current target API guidance requires new apps and updates to target Android 16/API 36 from August 31, 2026, with separate lower thresholds for Android TV, Android Automotive OS, and Wear OS. CarCast targets API 36 and is aligned for a phone Play submission. Google Play Data Safety still requires a completed declaration and privacy-policy link for published testing tracks; this repository contains drafts, not a submitted form.

The release branch adds environment-only signing configuration, version `0.9.0-beta1`, explicit release shrinking, and R8 rules for WebRTC/JNI/reflection. The generated release bundle remains unsigned unless the publisher supplies the protected variables documented in `RELEASE_SIGNING.md`. This is intentional: signing ownership cannot be safely invented in the sandbox.

The app's local transport and media path are direct and encrypted. MediaProjection and optional playback audio are user-initiated and disclosed in-app. Diagnostics are app-private and redacted. Physical device coverage, signed 64-bit verification, Play Console declarations, privacy-policy hosting, and final policy review remain required before public production release. See `BETA_READINESS_AUDIT.md` for the release gates.
