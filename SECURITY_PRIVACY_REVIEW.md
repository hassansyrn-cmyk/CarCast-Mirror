# Security and privacy review checklist

## Verified by source/build inspection

- TLS 1.3 identity is ephemeral and SAS is confirmation, not a key.
- Browser receiver is LAN-only and requires explicit phone approval.
- MediaProjection consent is single-use and capture ownership remains with the WebRTC capturer.
- Foreground services declare `mediaProjection` and are promoted before capture.
- No root, Accessibility capture, hidden Android Auto API, DRM bypass, or cloud relay.
- Release signing secrets are environment-only.
- R8 rules protect WebRTC/JNI/reflection paths.
- Debug reports no longer persist or copy stack traces, local IPv4 addresses, or six-digit verification values.

## Must be confirmed on devices and in Play Console

- No third-party SDK performs undisclosed collection.
- Audio behavior matches Android playback-capture policy and protected-content behavior.
- Notifications and foreground service disclosure are user-visible and accurate.
- Clean uninstall removes app-private diagnostics.
- Play declarations match the signed artifact, not just source intent.
