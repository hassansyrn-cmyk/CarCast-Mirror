# CarCast Mirror beta test matrix

## Devices and network

| Area | Test | Expected result | Evidence |
|---|---|---|---|
| Android 11–13 | Browser receiver video | 30 FPS priority, encrypted LAN stream, stop/new session works | device + receiver model |
| Android 14–16 | MediaProjection consent | one consent per session; no duplicate-token SecurityException | diagnostics report |
| 64-bit-only | Signed APK/AAB install | launches and streams with WebRTC native libraries | install log |
| Nikai TV browser | QR/local URL, approve, video/audio | WebRTC connects; video remains usable if audio is unsupported | TV model/firmware |
| Desktop Chrome/Edge/Safari | Browser receiver | connection, rotation, reconnect, fullscreen | browser/version |
| Native receiver | phone-to-phone/Android TV | TLS/SAS approval and WebRTC media | receiver model |

## Lifecycle and recovery

- Start receiver, connect, stop, start a new session three times.
- Lock/unlock the phone while casting.
- Rotate portrait → landscape → portrait.
- Kill/reopen the TV browser tab.
- Disable Wi-Fi, wait for failure, restore Wi-Fi, use Reconnect.
- Reject approval, then approve a new browser.
- Cancel MediaProjection consent and verify a safe recoverable error.
- Start a second browser and verify one-client enforcement.

## Audio and privacy

- Enable supported media playback and verify audio track telemetry.
- Test protected/DRM content and verify video-only fallback without claiming audio success.
- Deny microphone permission and verify video-only behavior.
- Confirm no screen/audio data is written to app storage or sent off-LAN.
- Verify diagnostics contain no IP address, SAS, screen pixels, audio samples, or stack trace.
- Confirm Help/FAQ disclosure appears before consent.

## Performance acceptance

- Auto/Smooth modes should prefer approximately 30 FPS over resolution.
- Under constrained bandwidth, resolution may step down before FPS collapses.
- Record actual resolution, FPS, bitrate, RTT, packet loss, and device temperature where available.
- A failed case must include the redacted in-app debug report and exact device/browser versions.

## Exit criteria

No P0 lifecycle/security issue; no duplicate MediaProjection retrieval; no crash on stop/reconnect/rotation; no cloud dependency; and all privacy declarations match observed behavior.
