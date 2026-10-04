# CarCast Mirror milestone status

## Build validation

The project is configured for compileSdk 36, targetSdk 36, minSdk 24, JDK 17, and the checked-in Gradle wrapper. The following commands pass in the Sandbox:

```text
./gradlew clean
./gradlew testDebugUnitTest
./gradlew assembleDebug
./gradlew bundleRelease
```

## Implemented in source

BrowserReceiverService now owns a temporary HTTP page and WebSocket signaling server. Browser capture uses the pinned WebRTC Android SDK and `ScreenCapturerAndroid`. The browser page uses `RTCPeerConnection`, SDP, LAN ICE candidates, `ontrack`, `video.srcObject`, fullscreen, connection-state display, and capability detection. The phone requires browser approval and Android MediaProjection consent. BrowserReceiverService is declared as a mediaProjection foreground service, calls `startForeground()` before capture, provides an ongoing Stop notification, and stops the WebRTC session, token, sockets, and capture on Stop or browser disconnect. Default LAN mode has no external STUN dependency.

Native CarCast uses an ephemeral TLS 1.3 receiver identity created with Bouncy Castle. The displayed six-digit SAS is derived from the certificate fingerprint and is only a verification value. The sender compares it with the peer certificate before streaming. The framed inner channel authenticates message type, sequence number, payload length, and payload together and rejects replay/out-of-order frames.

The receiver uses a timed accept loop without claiming a cryptographic countdown; SAS validity is the lifetime of the ephemeral TLS session. It probes actual MediaCodec decoder capabilities and implements receiver capabilities → sender proposal → receiver ACCEPT/counter-proposal negotiation. Encoded packets carry presentation timestamps, MediaCodec flags, codec configuration, and keyframe data. Reconnect resends configuration and requests a sync frame. Orientation changes recreate the sender capture pipeline and deliver a new StreamConfig; receiver codec operations are serialized across Surface changes.

Browser and receiver UI state is StateFlow-backed and collected by Compose. Browser diagnostics consume WebRTC stats where available for codec, dimensions, frames, bytes, FPS, and bytes/sec.

## Requires physical test

Two-device TLS/SAS native mirroring, Android MediaProjection, target hardware codec limits, reconnect after Wi-Fi loss, portrait/landscape changes, Nikai browser WebRTC support, and real WebRTC statistics require physical devices unavailable in this Sandbox.

## Remaining limitations

Complete native encoder/network diagnostics are not fully mapped. Browser RTT, selected candidate type, and packet-loss values are not yet surfaced in the UI even though WebRTC stats polling is active. No additional casting protocols were added in this milestone.

Browser Receiver startup now reserves a concrete WebSocket port, waits for the WebSocket `onStart()` callback with a bounded timeout, requires both HTTP and signaling sockets to be bound before showing `WAITING_FOR_BROWSER`, and preserves startup errors with a Retry action.

Browser session cleanup is centralized through `AppState.resetBrowserReceiverState()`. Explicit Stop and remote browser disconnect now clear URL, QR/session metadata, browser identity, client address, timestamps, tokens, and WebRTC diagnostics before returning to the initial STOPPED screen. Startup failures use a separate FAILED helper so error messages remain visible.

The Browser Receiver approval path now records redacted stage diagnostics locally, including projection consent, foreground-service promotion, WebRTC creation, capture, SDP, answer, ICE, and video-track stages. Recoverable approval/WebRTC errors are converted to FAILED with a safe reason. The Diagnostics screen provides the last successful stage, last error, exception, message, COPY DEBUG REPORT, and CLEAR controls. The most recent report is persisted in app-private SharedPreferences; uncaught exceptions are recorded before delegating to Android's normal crash handler.

The Browser Receiver quality milestone adds AUTO (default), LOW LATENCY, HD, and FULL HD profiles with real capture dimensions, 30 FPS limits, sender max/min bitrate parameters, MAINTAIN_FRAMERATE degradation preference, bounded AUTO adaptation, rotation-aware aspect-preserving capture, actual outbound WebRTC stats, quality limitation explanations, and explicit HD fallback reporting. The Nikai browser page uses contain-mode video and hides controls after connection.

Audio milestone status: official Android AudioPlaybackCapture PCM capture is implemented for API 29+ using the active MediaProjection, with protected/private playback restrictions respected and complete AudioRecord lifecycle cleanup. The pinned WebRTC SDK inspection showed that its public Java API has no arbitrary PCM injection path: JavaAudioDeviceModule callbacks observe stock input, while AudioTrack requires a native AudioSource. The repository now uses the focused playback-capture ADM boundary documented in AUDIO_ARCHITECTURE.md; physical validation remains required. Browser audio-track/autoplay diagnostics and explicit video-only fallback are included. Genuine completion requires the documented small native ADM/JNI bridge in AUDIO_ARCHITECTURE.md.

Latest physical-test follow-up: severe low FPS now triggers an immediate resolution step-down even when FULL HD was manually selected. Bitrate floors were lowered to protect 30 FPS and low latency. The UI now separates Selected profile from Actual stream. The new audio bridge uses Android AudioPlaybackCapture through a focused ADM boundary and adds a real WebRTC AudioTrack to the same PeerConnection; physical sound acceptance on a real phone and Nikai/PC browser remains the required validation step.

## Beta-readiness milestone — October 2026

The `release/beta-readiness` branch builds debug and unsigned release artifacts with version `0.9.0-beta1`, SDK 36, release shrinking, conditional environment-only signing, and conservative WebRTC/Bouncy Castle R8 rules. The app now includes an in-app Help/FAQ and a prominent disclosure before screen/audio consent. Diagnostics are redacted before persistence/copy. The generated unsigned AAB is a structural artifact only; physical validation, publisher signing, Play Console declarations, privacy-policy hosting, and store review remain open gates. See `BETA_READINESS_AUDIT.md` and `RELEASE_CHECKLIST.md`.

## Play beta monetization milestone — October 2026

The isolated `release/play-beta` branch adds official Mobile Ads `25.5.0`, UMP `4.0.0`, centralized idle-banner policy, privacy options, production-ID configuration, protected signed-AAB workflow, version `0.9.1-beta` / code `3`, Play Console guide, screenshot plan, and deployable privacy-policy HTML. No new casting protocol or baseline refactor was made. Physical regression and publisher secret configuration remain required before signing or tagging the Play beta.
