# CarCast Mirror — Native Android

This project contains the Kotlin + Jetpack Compose native sender/receiver and the local browser receiver path.

## Build

Requirements: JDK 17, Android SDK 36, and the checked-in Gradle wrapper. The app uses compileSdk 36, targetSdk 36, and minSdk 24.

```bash
./gradlew clean
./gradlew testDebugUnitTest
./gradlew assembleDebug
./gradlew bundleRelease
```

Pinned networking/security dependencies include `io.github.webrtc-sdk:android:125.6422.07`, `org.java-websocket:Java-WebSocket:1.5.7`, and Bouncy Castle `1.78.1` for ephemeral TLS identity generation.

## Browser Receiver

Browser mode starts a temporary local HTTP page and WebSocket signaling server. The page performs `RTCPeerConnection` capability detection, exchanges SDP and LAN ICE candidates, and renders the Android `ScreenCapturerAndroid` video track. The default ICE configuration is empty for same-LAN operation; no external Google STUN server is contacted. The phone shows browser user agent and LAN address, then offers explicit Approve and Reject controls.

After approval and Android MediaProjection consent, BrowserReceiverService is promoted to a mediaProjection foreground service and starts an ongoing notification with a Stop action before screen capture begins. Browser disconnect and Stop terminate PeerConnection, capture, signaling, token, and server state.

## Native Receiver

The receiver creates an ephemeral TLS 1.3 identity and displays a six-digit SAS derived from its certificate fingerprint. The sender compares the entered SAS with the peer certificate before stream negotiation. The SAS is not the encryption key. The receiver probes actual MediaCodec capabilities, negotiates sender proposal/receiver acceptance or counter-proposal, carries authenticated timestamps and MediaCodec flags, and uses a timed accept loop while the SAS remains valid for the ephemeral TLS session.

## Current validation boundary

The required clean, unit-test, debug, and release Gradle tasks pass in the Sandbox. Physical phone-to-phone and Nikai-browser validation is unavailable here. Diagnostics state is observable and browser/native connection states are surfaced, but complete WebRTC `getStats()` and native throughput counter mapping remains incomplete. See `IMPLEMENTED_AND_CODE_VERIFIED.md` for the exact status.

Browser Receiver startup now reserves a concrete WebSocket port, waits for the WebSocket `onStart()` callback with a bounded timeout, requires both HTTP and signaling sockets to be bound before showing `WAITING_FOR_BROWSER`, and preserves startup errors with a Retry action.

Browser session cleanup is centralized through `AppState.resetBrowserReceiverState()`. Explicit Stop and remote browser disconnect now clear URL, QR/session metadata, browser identity, client address, timestamps, tokens, and WebRTC diagnostics before returning to the initial STOPPED screen. Startup failures use a separate FAILED helper so error messages remain visible.

The Browser Receiver approval path now records redacted stage diagnostics locally, including projection consent, foreground-service promotion, WebRTC creation, capture, SDP, answer, ICE, and video-track stages. Recoverable approval/WebRTC errors are converted to FAILED with a safe reason. The Diagnostics screen provides the last successful stage, last error, exception, message, COPY DEBUG REPORT, and CLEAR controls. The most recent report is persisted in app-private SharedPreferences; uncaught exceptions are recorded before delegating to Android's normal crash handler.

The Browser Receiver quality milestone adds AUTO (default), LOW LATENCY, HD, and FULL HD profiles with real capture dimensions, 30 FPS limits, sender max/min bitrate parameters, MAINTAIN_FRAMERATE degradation preference, bounded AUTO adaptation, rotation-aware aspect-preserving capture, actual outbound WebRTC stats, quality limitation explanations, and explicit HD fallback reporting. The Nikai browser page uses contain-mode video and hides controls after connection.

Audio milestone status: official Android AudioPlaybackCapture PCM capture is implemented for API 29+ using the active MediaProjection, with protected/private playback restrictions respected and complete AudioRecord lifecycle cleanup. The pinned WebRTC SDK inspection showed that its public Java API has no arbitrary PCM injection path: JavaAudioDeviceModule callbacks observe stock input, while AudioTrack requires a native AudioSource. The repository now uses the focused playback-capture ADM boundary documented in AUDIO_ARCHITECTURE.md; physical validation remains required. Browser audio-track/autoplay diagnostics and explicit video-only fallback are included. Genuine completion requires the documented small native ADM/JNI bridge in AUDIO_ARCHITECTURE.md.

Latest physical-test follow-up: severe low FPS now triggers an immediate resolution step-down even when FULL HD was manually selected. Bitrate floors were lowered to protect 30 FPS and low latency. The UI now separates Selected profile from Actual stream. The new audio bridge uses Android AudioPlaybackCapture through a focused ADM boundary and adds a real WebRTC AudioTrack to the same PeerConnection; physical sound acceptance on a real phone and Nikai/PC browser remains the required validation step.

The Browser approval lifecycle now carries a unique `browserSessionId` and has a single atomic capture-start claim. `ScreenCapturerAndroid` alone consumes the single-use MediaProjection consent `Intent`; after video starts, the session obtains `screenCapturer.getMediaProjection()` and uses that same object for Android playback capture. The service does not retrieve a second projection or create a second virtual display. The audio toggle defaults on for Android 10+, requests `RECORD_AUDIO` only when enabled, and falls back to video-only without failing the receiver when permission or playback capture is unavailable. The browser validates one video and one audio track on the same media element; Smart TV autoplay shows a Play prompt when needed.

## Branding

The Android application is branded consistently as **CarCast Mirror**. The launcher uses an original adaptive phone-and-casting symbol with standard and round masks plus an Android 13 monochrome layer. The modern AndroidX SplashScreen uses the same symbol, and the Compose home header reuses the centralized brand mark, name, tagline, and navy/cyan color tokens. See [BRANDING.md](BRANDING.md) and the visual review previews in `design/branding/previews/`.

## Premium UI stability milestone

The premium visual refresh is preserved as the base of the current development branch. Browser disconnects and Wi-Fi changes now surface a clear reconnect action, stale receiver URLs are invalidated on failure, diagnostics remain available after a failure, and each new session starts with clean diagnostics. WebRTC transient disconnects receive an eight-second recovery window before the app reports failure. The normal quality menu is simplified to Auto, Smooth, and High Quality while still prioritizing 30 FPS internally.
