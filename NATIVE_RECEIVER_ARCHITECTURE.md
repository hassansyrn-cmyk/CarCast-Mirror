# Native CarCast Receiver

## Scope

The Native Receiver milestone is isolated on `feature/native-receiver` and does not replace or refactor the physically verified Browser / Smart TV receiver path. The stable baseline remains `v0.2-browser-av-ui-stable`.

## Existing repository audit

- **Existing native receiver:** The repository already contained `ReceiverService`, NSD advertising, TLS/SAS setup, capability negotiation, and a custom MediaCodec framed-video decoder.
- **Reused components:** `TlsIdentity` and `TlsFramedChannel` remain the local signaling security primitives; `_carcast._tcp.` remains the discovery type; `BrowserWebRtcSession` supplies the existing MediaProjection, `ScreenCapturerAndroid`, playback-capture audio bridge, WebRTC encoder, adaptation policy, and stats machinery on the sender.
- **Isolated/retired from the user path:** The old `ReceiverService` custom encoded-frame path remains in the source for comparison and rollback, but the Receive UI and discovery filter now use the new `native-webrtc-v1` service and do not start or advertise the old implementation.

## Native architecture

```text
Sender Android
  MediaProjection -> existing BrowserWebRtcSession capture/audio
  WebRTC PeerConnection (video + optional device playback audio)
  DTLS-SRTP / Opus / video
        |
        | TLS 1.3 signaling + SAS approval over same LAN
        v
Receiver Android / Android TV
  NativeReceiverWebRtcService
  PeerConnection -> SurfaceViewRenderer + WebRTC audio output
```

The signaling channel carries JSON SDP offers/answers and ICE candidates inside `TlsFramedChannel`. Media is not sent through the custom frame transport: the native receiver uses WebRTC's DTLS-SRTP media path and default Android audio output.

## Discovery

The receiver advertises only while `NativeReceiverWebRtcService` is intentionally running. NSD uses `_carcast._tcp.` with `protocol=native-webrtc-v1` and capabilities metadata. The sender filters for that protocol, shows only genuinely resolved receivers, and hides raw IP/port from normal UI. Technical endpoint data remains available to diagnostics/state for development.

## Approval and security

1. Receiver creates an ephemeral TLS 1.3 identity.
2. Sender verifies the receiver certificate fingerprint-derived six-digit SAS.
3. Receiver displays the same SAS and a large approval request.
4. Receiver must select **CONNECT** or **DECLINE**; approval times out after 60 seconds.
5. One active sender is allowed. Additional connections are rejected while pending or connected.
6. WebRTC media remains protected by DTLS-SRTP. No raw unauthenticated media socket is introduced.

This milestone does not yet persist a trusted-device store; repeat connections require the normal secure approval flow.

## Receiver UX

The existing premium Compose design is reused. The Receive screen now presents a friendly receiver name, ready state, same-Wi-Fi guidance, TV launcher metadata, an approval card with D-pad-safe large buttons, and a hardware-accelerated `SurfaceViewRenderer` with aspect-preserving rendering. The current identity store persists a friendly name locally; a dedicated rename settings control remains a follow-up.

## Lifecycle

Advertising stops when the native receiver service stops. Approval state, active sender state, PeerConnection, renderer, NSD registration, TLS socket, and executor are released on teardown. Receiver service shutdown does not invoke MediaProjection. Sender MediaProjection remains owned by the existing `ScreenCapturerAndroid` path.

## Validation status

- `./gradlew testDebugUnitTest assembleDebug`: passing in Sandbox.
- Browser Receiver source files remain unchanged on this branch.
- Physical phone-to-phone, Android TV, portrait/landscape, device-audio, network-interruption, and D-pad acceptance are still required before calling this milestone complete.
- Native WebRTC receiver stats mapping and trusted-device persistence are not complete.
