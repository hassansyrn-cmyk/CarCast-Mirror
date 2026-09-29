# Implemented and code verified

## FULLY IMPLEMENTED IN CODE

The project contains a pinned maintained Android WebRTC SDK, local HTTP plus WebSocket signaling, browser `RTCPeerConnection` SDP/ICE exchange, Android `PeerConnectionFactory`, `ScreenCapturerAndroid`, `VideoSource`, `VideoTrack`, browser capability detection, explicit phone approval/rejection, LAN-only ICE, temporary token authorization, and PeerConnection cleanup.

The Browser Receiver is declared as a `mediaProjection` foreground service. After phone approval and MediaProjection consent, the service calls `startForeground()` with an ongoing **CarCast Browser Mirroring** notification and Stop action before starting `ScreenCapturerAndroid`. Browser disconnect and Stop invalidate the session and stop capture.

Browser capture uses bounded source-aspect-ratio dimensions and listens for display changes to call `changeCaptureFormat()` without requiring browser reconnection. Browser and receiver state are observable StateFlows collected by Compose. The browser displays URL, QR, browser user agent, remote LAN address, approval state, connection state, and error state.

Native pairing now uses an ephemeral TLS 1.3 identity and certificate-derived six-digit SAS. The SAS is displayed for user verification and is not an encryption key. The sender compares the entered SAS to the peer certificate fingerprint before beginning negotiated screen transport. Native `TlsFramedChannel` carries message type, sequence number, payload length, and payload inside TLS and rejects sequence replay/out-of-order frames.

Native receiver capability probing uses `MediaCodecList` and `VideoCapabilities`. Negotiation now has receiver capabilities, sender proposal, receiver ACCEPT, and receiver counter-proposal paths. Encoded packets include timestamps, MediaCodec flags, codec configuration packets, keyframe information, and strict size limits. Reconnect resends configuration and requests a sync frame. Receiver codec access is serialized around Surface recreation. Native accept() uses a timeout so SAS expiry is enforced even without incoming clients.

## IMPLEMENTED — REQUIRES PHYSICAL TEST

Live native phone-to-receiver mirroring, TLS/SAS verification on two devices, browser phone-to-Nikai WebRTC video, browser capability behavior on the Nikai browser, Android MediaProjection capture, portrait/landscape changes, Wi-Fi interruption recovery, hardware decoder values, and WebRTC stats on the TV browser all require physical devices unavailable in this Sandbox.

## PARTIALLY IMPLEMENTED

Diagnostics state and UI are connected to observable session models, and browser connection/ICE state plus browser user agent are emitted. Complete measured counters for frames, bitrate, RTT, packets lost, selected candidate, and all native encoder/network statistics are not yet fully populated. WebRTC `getStats()` mapping remains the next diagnostics task.

## NOT IMPLEMENTED

Physical-device automation, full live native/browser telemetry, and a persistent production trust store are not implemented. No additional casting protocols were added in this iteration.

## NOT POSSIBLE TO VERIFY IN THIS ENVIRONMENT

The Sandbox cannot verify physical Android capture, cross-device Wi-Fi, the Nikai browser's WebRTC support, or real hardware encoder/decoder interoperability.

Browser Receiver startup now reserves a concrete WebSocket port, waits for the WebSocket `onStart()` callback with a bounded timeout, requires both HTTP and signaling sockets to be bound before showing `WAITING_FOR_BROWSER`, and preserves startup errors with a Retry action.

Browser session cleanup is centralized through `AppState.resetBrowserReceiverState()`. Explicit Stop and remote browser disconnect now clear URL, QR/session metadata, browser identity, client address, timestamps, tokens, and WebRTC diagnostics before returning to the initial STOPPED screen. Startup failures use a separate FAILED helper so error messages remain visible.

The Browser Receiver approval path now records redacted stage diagnostics locally, including projection consent, foreground-service promotion, WebRTC creation, capture, SDP, answer, ICE, and video-track stages. Recoverable approval/WebRTC errors are converted to FAILED with a safe reason. The Diagnostics screen provides the last successful stage, last error, exception, message, COPY DEBUG REPORT, and CLEAR controls. The most recent report is persisted in app-private SharedPreferences; uncaught exceptions are recorded before delegating to Android's normal crash handler.

The Browser Receiver quality milestone adds AUTO (default), LOW LATENCY, HD, and FULL HD profiles with real capture dimensions, 30 FPS limits, sender max/min bitrate parameters, MAINTAIN_FRAMERATE degradation preference, bounded AUTO adaptation, rotation-aware aspect-preserving capture, actual outbound WebRTC stats, quality limitation explanations, and explicit HD fallback reporting. The Nikai browser page uses contain-mode video and hides controls after connection.

Audio milestone status: official Android AudioPlaybackCapture PCM capture is implemented for API 29+ using the active MediaProjection, with protected/private playback restrictions respected and complete AudioRecord lifecycle cleanup. The pinned WebRTC SDK inspection showed that its public Java API has no arbitrary PCM injection path: JavaAudioDeviceModule callbacks observe stock input, while AudioTrack requires a native AudioSource. The repository now uses the focused playback-capture ADM boundary documented in AUDIO_ARCHITECTURE.md; physical validation remains required. Browser audio-track/autoplay diagnostics and explicit video-only fallback are included. Genuine completion requires the documented small native ADM/JNI bridge in AUDIO_ARCHITECTURE.md.

Latest physical-test follow-up: severe low FPS now triggers an immediate resolution step-down even when FULL HD was manually selected. Bitrate floors were lowered to protect 30 FPS and low latency. The UI now separates Selected profile from Actual stream. The new audio bridge uses Android AudioPlaybackCapture through a focused ADM boundary and adds a real WebRTC AudioTrack to the same PeerConnection; physical sound acceptance on a real phone and Nikai/PC browser remains the required validation step.

Lifecycle regression fix: Browser approval now carries a unique `browserSessionId`; the service accepts projection consent only for its current session and uses an `AtomicBoolean` claim to prevent duplicate capture starts. `ScreenCapturerAndroid` is the sole consumer of the single-use consent `Intent`; the service no longer calls `MediaProjectionManager.getMediaProjection()`. Foreground promotion occurs before capture with `FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION`. `ProjectionLifecycle` records consent, approval, WebRTC creation, capture-start, the one shared projection retrieval, the SDK-owned virtual-display milestone, and duplicate-use diagnostics.

Audio milestone implementation: the WebRTC ADM is initialized without MediaProjection, then after `ScreenCapturerAndroid.startCapture()` succeeds the session obtains `screenCapturer.getMediaProjection()` and attaches that exact object to the official `AudioPlaybackCaptureConfiguration`. The audio path creates an allowed 48 kHz stereo PCM `AudioRecord`, uses the same PeerConnection's WebRTC audio track, and leaves Opus/RTP/DTLS-SRTP/A/V timing to WebRTC. The browser validates one video and one audio track on the same media element. Audio permission denial, unavailable projection, protected playback, and adapter failure leave video running and report truthful `Audio: Unavailable — …` states. Audio-off remains the video-only regression path. Physical audio acceptance remains required.
