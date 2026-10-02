# Car Receiver Compatibility

## Decision

CarCast should target **legitimate Android receiver devices and browser-capable head units**, not generic factory Android Auto projection.

The existing Browser Receiver and Native CarCast Receiver remain the two supported transport paths. This milestone makes no changes to either path because the current evidence supports a compatibility/documentation milestone first, not a new receiver stack.

## Compatibility matrix

| Environment | APK install | Native Receiver | Browser Receiver | Local networking | Fullscreen video | Audio through car | Suitability | Recommended mode | Important limitations |
|---|---|---:|---:|---:|---:|---:|---|---|---|
| Generic Android aftermarket head unit | Usually, if OEM firmware permits unknown-source or Play installation | **Yes, conditionally** | **Yes, conditionally** | Usually Wi-Fi/hotspot capable; must test multicast and client isolation | Yes when the receiver activity is allowed to occupy the display; OEM system bars may remain | Usually through Android output routing; verify the unit's audio policy | **Best immediate target** | Install/open CarCast Receiver; use Native Receiver first, Browser Receiver when APK installation is unavailable | Firmware, Android version, decoder, audio routing, DPI, portrait/landscape, and background restrictions vary by manufacturer |
| Android AI Box connected to the car display | Usually, if it is a normal Android device and APK installation is allowed | **Yes, conditionally** | **Yes, conditionally** | Depends on whether phone and box share a LAN, phone hotspot, box hotspot, or isolated tethering network | Normally yes through the box's Android display output | Normally through box system audio/HDMI/USB path; verify routing | **Strong target** | Native Receiver on the box; Browser Receiver as fallback | Box may use unusual DPI, fixed landscape, limited decoder performance, aggressive power management, or hotspot multicast filtering |
| Factory Android Auto projection | Not a normal receiver APK target | **No** as a generic arbitrary-screen receiver | **No** as a generic arbitrary-screen receiver | Android Auto provides a projection/session environment, not an open peer receiver contract | Only through supported Android Auto app categories and policies | Only through supported Android Auto media/audio APIs | **Not a generic CarCast receiver** | Do not advertise as supported; use a separately supported Android Auto app category only if product requirements change | Third-party apps must use documented Android Auto categories. Arbitrary MediaProjection pixels from the phone are not a documented receiver API. |
| Android Automotive OS / Google built-in | Depends on OEM, Play availability, package/category, and car policy | **Potentially**, if the APK is installable and the receiver activity is permitted | **Potentially**, if the car has a usable browser and policy allows it | Vehicle networking varies; Wi-Fi and local peer reachability are not guaranteed | Activities can use the available display, but OEM system bars and UX restrictions apply | AAOS devices are generally fixed-volume; route and policy must be verified | **Conditional target** | Treat as an ordinary Android receiver only after device-specific installation and parked-session validation | UX restrictions can block activities while driving; video apps are a parked category and Play distribution has category and quality requirements |
| Browser-capable infotainment system | Not required | Not required | **Yes, conditionally** | Must reach the phone's local HTTP/WebSocket endpoint; captive portals and client isolation can interfere | Browser must support fullscreen or at least a usable large video element | Browser autoplay policy and device audio routing must be verified | **Good fallback target** | Browser Receiver with compatibility test page | WebRTC, WebSocket, H.264/VP8, audio playback, autoplay, and fullscreen support vary by browser firmware |
| External Android tablet/receiver by HDMI or USB | Usually, if normal Android | **Yes** | **Yes** | Same local-network requirements as any Android device; HDMI/USB only carries display/output and does not create network connectivity | Yes through the Android display | Tablet/receiver audio or HDMI/USB audio path must be verified | **Best controlled physical test target** | Native Receiver first, Browser Receiver second | This is not Android Auto. The external device must run the receiver app or browser and remain on the same reachable network |

## Factory Android Auto conclusion

**Factory Android Auto: NOT A GENERIC CARCAST RECEIVER.**

Official Android documentation describes Android Auto as a driver-optimized interface that uses declared app categories and services. Media, messaging, and templated apps use documented Android for Cars APIs. Parked apps run only in supported categories, and the current Android Auto parked-app documentation states that games are the only parked app category supported by Android Auto at this time.[1] [2]

The documented Android Auto flow does not provide a third-party API for receiving arbitrary `MediaProjection` frames from the phone and displaying them as a full-screen custom receiver. CarCast must not add `CAR_LAUNCHER`, claim generic screen mirroring, inject touch events, use accessibility, or attempt to bypass Android Auto restrictions.

The app's current product messaging is correct to distinguish a factory Android Auto display from an installable Android receiver. A user with only factory Android Auto should be offered a truthful explanation and, where available, **Use CarCast Receiver** or **Try Smart TV / Browser** rather than being shown as a compatible full-screen receiver.

## Android Automotive OS conclusion

Android Automotive OS is a different platform from Android Auto. It is Android running in the vehicle, and cars with Google built-in can include Google Play and other Google services.[3]

AAOS can be a viable CarCast receiver environment only when the vehicle/OEM permits the receiver APK or makes it available through an allowed Play track. Android Automotive has UX restrictions controlled by the vehicle manufacturer. Activities that are not distraction optimized can be blocked while driving, and OEMs may prevent immersive mode so vehicle controls remain accessible.[3]

Google's current parked-app documentation lists video as an AAOS category and requires video apps to meet car quality requirements. Video playback is generally a parked experience; audio while driving is a limited beta capability for eligible apps.[4] A future AAOS-specific CarCast receiver would need category, policy, distribution, and physical validation work. This report does not claim the current APK is AAOS-approved or Play-distributable.

## Android-powered aftermarket head unit conclusion

A normal Android aftermarket head unit is the clearest target if it permits APK installation and has a reachable local network interface. The existing Native Receiver can already serve as the receiver stack without a new protocol:

```text
Phone MediaProjection + playback audio
    -> existing Native WebRTC sender
    -> TLS 1.3/SAS approval
    -> Native CarCast Receiver APK on head unit
    -> head-unit display and audio output
```

The receiver's current WebRTC video renderer uses hardware scaling and does not mirror the receiver UI. The current implementation therefore has the right transport foundation, but a car presentation mode should be added later to provide a landscape-first, fit-by-default, black-letterboxed fullscreen display with a large Stop/Disconnect action. That is a compatibility improvement, not a second receiver architecture.

## Android AI Box conclusion

An AI box should be treated as a normal Android receiver device, not as a special casting protocol. If the box permits APK installation, the Native Receiver is the preferred path. If it exposes a Chromium-based browser but does not permit APK installation, the Browser Receiver is the fallback.

The following must be checked per box:

- Android API level and WebRTC SDK compatibility.
- Whether hardware H.264 decoding is available and stable.
- Fixed landscape orientation, unusual DPI, safe-area insets, and output resolution.
- Whether the box routes WebRTC audio to HDMI, USB, Bluetooth, or vehicle audio.
- Whether the box can remain awake during a long session.
- Whether its hotspot forwards peer traffic and multicast DNS.
- Whether client isolation prevents the phone from reaching the receiver socket.

No manufacturer-specific behavior should be hardcoded.

## Browser-capable head-unit conclusion

The existing Browser Receiver is a valid path when the head-unit browser supports the necessary runtime features. The compatibility test page should report these values without exposing them on the normal casting screen:

- WebRTC `RTCPeerConnection`.
- H.264 and/or VP8 decode capability.
- WebSocket.
- Video element playback.
- Audio track and Opus decode capability where detectable.
- Autoplay status, including whether user action is required.
- Fullscreen capability.
- Current display size and orientation.

A browser result must be treated as **compatible only after the actual CarCast WebRTC session is tested**. A browser that merely reports `RTCPeerConnection` support must not be shown as a working receiver before video, audio, A/V timing, and reconnect behavior are verified.

## Network and hotspot findings

### Same Wi-Fi

This is the existing preferred topology. Android NSD is designed to discover services on a local network using DNS-SD/mDNS.[5] It is appropriate for the Native Receiver's `_carcast._tcp.` advertisement, but access-point multicast behavior and client isolation still need physical testing.

### Phone hotspot, receiver joins phone hotspot

This can work if the hotspot gives both devices reachable addresses and allows peer-to-peer traffic. It is not safe to assume that every Android hotspot forwards multicast DNS or permits clients to reach one another. The first software fallback should be a QR or short session-code flow that transfers the receiver's temporary endpoint and session authorization without asking normal users to type an IP address.

### Receiver hotspot, phone joins receiver hotspot

This can also work if the receiver hotspot allows connected clients to reach the listening socket. Some devices isolate clients or restrict local traffic. The same QR/session-code fallback is appropriate if NSD does not appear.

### Wi-Fi Direct

Android officially provides Wi-Fi Direct service discovery, including DNS-SD records, for nearby services without a conventional local network.[6] This is a separate API path from the current NSD manager flow and requires additional permissions, device support, and user-consent handling. It should not be silently substituted into the current receiver until a real topology requires it and hardware testing proves it.

### Safe fallback design

A future fallback should use a short-lived signed pairing payload containing:

- receiver identity/friendly name;
- temporary TLS endpoint and port;
- session identifier and expiry;
- certificate fingerprint/SAS confirmation data;
- one-time authorization token.

It should be presented as a QR code or short code. It must not become a permanent unauthenticated endpoint and must not make raw IP configuration the normal user experience.

## Audio and display behavior

The current architecture already uses the existing WebRTC audio track and does not substitute microphone capture. On Android receiver hardware, audio output depends on the device's Android audio policy and physical connection: built-in speaker, HDMI, USB, Bluetooth, or vehicle integration.

The car presentation mode should be:

- landscape-first;
- fullscreen where the OEM permits it;
- fit-screen by default with black unused areas;
- optional fill-screen only when the user chooses it;
- aspect-preserving for portrait and landscape phone sources;
- free of stretched video;
- minimal during playback;
- equipped with a large Stop/Disconnect control;
- output-only, with no remote touch injection.

## Current Native Receiver changes required

**No transport rewrite is required for normal Android head units or AI boxes.** The Native Receiver already provides TLS/SAS approval, WebRTC video/audio, one-sender enforcement, and NSD advertising.

Before declaring car compatibility complete, the following isolated improvements are recommended:

1. Add the receiver-only car presentation mode described above.
2. Add receiver diagnostics for platform, Android version, display size/refresh rate, network type, discovery method, decoder, audio route/status, and WebRTC counters.
3. Add a compatibility test page to the existing Browser Receiver rather than creating a new browser stack.
4. Add a signed QR/session-code fallback when NSD is unavailable, preserving the existing TLS/SAS authorization.
5. Add physical test coverage for hotspot client reachability, multicast discovery, fixed-landscape displays, low-resolution displays, audio routing, rotation, stop, and reconnect.

No changes were made in this milestone because none can be safely validated against real car hardware in the Sandbox.

## Diagnostics requirements

Technical information belongs under Advanced Diagnostics, not the normal casting screen. The future car receiver diagnostics should include:

- receiver platform and Android version;
- display resolution, refresh rate, orientation, and display ID;
- network type and local address category;
- discovery method and connection method;
- WebRTC video codec, resolution, FPS, bitrate, RTT, and packet loss;
- decoder implementation when available;
- audio track state, codec, route, and failure reason;
- disconnect reason and last exception.

## Test matrix

Not physically testable in the current Sandbox:

1. Android phone to Android tablet receiver.
2. Android phone to Android TV/box receiver.
3. Phone hotspot to receiver.
4. Receiver hotspot to phone.
5. Fixed-landscape receiver.
6. Low-resolution receiver.
7. Video plus internal audio through built-in/HDMI/USB output.
8. Rotation and aspect preservation.
9. Stop, reconnect, and second session.
10. Browser Receiver on Chromium-based head-unit browser.
11. AAOS emulator or real vehicle policy behavior.

## Best device type for real car testing

**Best next physical test: an ordinary Android tablet or Android TV/box that permits APK installation and can join the same phone hotspot.** This gives a controlled receiver target without requiring a specific car product and tests the exact Native Receiver path proposed for aftermarket head units and AI boxes.

A specific aftermarket head unit or AI box should not be purchased yet. If an existing unit is available, test it after the tablet/box baseline. A real AAOS vehicle is a separate policy and distribution test, not a substitute for validating the core receiver transport.

## Files modified

- `CAR_RECEIVER_COMPATIBILITY.md` — this report.

No stable Browser Receiver or Native Receiver source files were modified. No Android Auto compatibility declaration, automotive manifest flag, hotspot hack, IP scanner, accessibility path, root requirement, ADB requirement, or OEM-specific code was added.

## References

[1]: https://developer.android.com/training/cars/platforms/android-auto "Android Auto overview"
[2]: https://developer.android.com/training/cars/parked/auto "Add support for Android Auto to your parked app"
[3]: https://developer.android.com/training/cars/platforms/automotive-os "Android Automotive OS overview"
[4]: https://developer.android.com/training/cars/parked/video "Build video apps for Android Automotive OS"
[5]: https://developer.android.com/develop/connectivity/wifi/use-nsd "Use network service discovery"
[6]: https://developer.android.com/develop/connectivity/wifi/nsd-wifi-direct "Use Wi-Fi Direct for service discovery"
