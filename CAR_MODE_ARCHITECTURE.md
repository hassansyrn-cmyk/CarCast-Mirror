# Car mode architecture decision

## Decision

CarCast must not present a generic full-screen phone-screen receiver for a factory Android Auto display. Android Auto is a phone-projection platform: apps connect through supported Android for Cars services and categories rather than receiving an arbitrary MediaProjection stream on the head unit. The official Android Auto documentation says that parked activities on the head unit are limited to supported parked categories, and the current parked-app guide lists games for Android Auto while video is an Android Automotive OS category. The Android Auto parked-app integration page also warns that builds outside the supported categories are rejected during review. [1] [2] [3]

That rules out claiming support for a factory vehicle Android Auto screen unless the vehicle or head-unit vendor provides a separate supported receiver surface. CarCast will not use undocumented Android Auto APIs, ADB or developer-mode exploits, patched Android Auto packages, Accessibility misuse, or any other bypass.

## Legitimate product modes

**CarCast Sender** runs on the phone. It obtains explicit MediaProjection consent, creates a VirtualDisplay, encodes the captured surface with MediaCodec, and sends the stream directly to an authenticated receiver.

**CarCast Receiver** runs on a second Android device, Android tablet, Android box, or an Android-powered head unit where the user can install the receiver app directly. This is the current test harness and the closest legitimate implementation of phone-to-display mirroring.

**Car mode** is an explanation and compatibility layer. It distinguishes supported Android-powered receivers from factory Android Auto projection screens. It does not label the ordinary Receiver Activity as Android Auto.

A future Android Automotive OS parked-app integration is a separate product path. Android Automotive OS is installed in the car and can support parked video apps, subject to car-app quality rules and Google Play review. The current official video guidance says the video category is for Android Automotive OS and is coming to Android Auto in beta, with early-access limitations. [4]

## Safety and Play implications

Video and parked apps must not be usable while driving. Official car quality guidance requires the UI to be hidden while driving and playback to stop when driving restrictions apply. Android Auto also automatically exits parked apps when vehicle motion is detected. [2] [5]

A generic screen mirror is not automatically a qualifying Android Auto video app. To pursue a parked video category, CarCast would need to become a compliant content/video app with an eligible distribution path, not simply stream arbitrary phone pixels. That would also require separate car-specific UX, driving-state handling, category declaration, and review.

## Android 17 local network design

Because the native transport uses raw TCP and NSD, Android 17 local-network protections apply. Android's official guidance says apps targeting SDK 37 or higher must either use a system-mediated NSD picker or declare and request `ACCESS_LOCAL_NETWORK` before LAN access. The app now explains the need for local-network access and requests it before discovery or receiver startup. [6]

## Current implementation boundary

The native project retains phone-to-phone mirroring as the real test harness. The car path is now honest: it explains why factory Android Auto is not supported and directs users to an Android-powered receiver or separate receiver hardware. The remaining engineering work before production is authenticated pairing, encrypted transport, negotiated stream metadata, rotation-safe reconfiguration, reconnect handling, diagnostics, and physical-device validation.

## References

[1]: https://developer.android.com/training/cars "Android for Cars overview"
[2]: https://developer.android.com/training/cars/platforms/android-auto "Android Auto overview"
[3]: https://developer.android.com/training/cars/parked/auto "Add support for Android Auto to your parked app"
[4]: https://developer.android.com/training/cars/parked/video "Build video apps for Android Automotive OS"
[5]: https://developer.android.com/docs/quality-guidelines/car-app-quality "Car app quality"
[6]: https://developer.android.com/privacy-and-security/local-network-permission "Local network permission"
