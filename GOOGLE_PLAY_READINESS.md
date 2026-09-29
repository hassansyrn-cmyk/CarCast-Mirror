# Google Play readiness

The project now uses a checked-in Gradle wrapper, JDK 17, compileSdk 36, targetSdk 36, minSdk 24, R8 release shrinking, a ProGuard file, and a MediaProjection foreground-service declaration. The app requests local-network access only when discovery or receiver operation needs it. It does not use root, Accessibility capture, hidden Android Auto APIs, DRM bypass, or background boot-started MediaProjection.

`assembleDebug` and `bundleRelease` are build-verified in this environment. The release AAB is not release-ready for publishing: it is unsigned for Play App Signing, has not passed physical-device validation, and still has incomplete browser/WebRTC, reconnect, rotation, and multi-provider adapters. Play review for car categories is separate and must not be implied by a successful Gradle build.
