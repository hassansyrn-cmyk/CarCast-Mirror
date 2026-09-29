# Nikai Smart TV WebRTC acceptance test

1. Install the debug APK on the Android phone.
2. Open **CarCast Mirror → Browser**.
3. Tap **Start Browser Receiver**.
4. Note the displayed local HTTP URL and QR code.
5. Open the Nikai built-in browser and enter the displayed URL.
6. The page checks `window.RTCPeerConnection`. If unavailable, it must show **This browser does not support WebRTC** and must not claim mirroring.
7. If supported, the phone shows the browser user agent, remote LAN address, session age, and **APPROVE / REJECT** controls.
8. Tap **APPROVE** on the phone.
9. Approve Android MediaProjection consent.
10. Confirm the phone promotes the browser capture path to the foreground service and shows **CarCast Browser Mirroring — Sharing screen with TV browser** with a Stop action.
11. Confirm the browser receives the SDP offer, answers it, exchanges LAN ICE candidates, and changes to **Connected — live phone screen**.
12. Confirm live phone pixels appear in the browser video element and use fullscreen.
13. Rotate the phone portrait → landscape → portrait and verify the browser video resizes without reconnecting.
14. Open the phone home screen, YouTube, and another normal app to verify that live pixels change.
15. Close the TV browser. Confirm the phone stops WebRTC capture and invalidates the browser session rather than continuing to capture indefinitely.
16. Start another session and use the foreground notification **Stop** action. Confirm capture, WebSocket clients, token, and local server stop cleanly.

If the Nikai browser reports no `RTCPeerConnection`, record its user agent and capability result. Do not claim WebRTC mirroring for that browser. The architecture leaves room for a later explicitly labeled higher-latency fallback, but no HLS or additional protocol is part of this milestone.

Browser Receiver startup now reserves a concrete WebSocket port, waits for the WebSocket `onStart()` callback with a bounded timeout, requires both HTTP and signaling sockets to be bound before showing `WAITING_FOR_BROWSER`, and preserves startup errors with a Retry action.

Browser session cleanup is centralized through `AppState.resetBrowserReceiverState()`. Explicit Stop and remote browser disconnect now clear URL, QR/session metadata, browser identity, client address, timestamps, tokens, and WebRTC diagnostics before returning to the initial STOPPED screen. Startup failures use a separate FAILED helper so error messages remain visible.

The Browser Receiver approval path now records redacted stage diagnostics locally, including projection consent, foreground-service promotion, WebRTC creation, capture, SDP, answer, ICE, and video-track stages. Recoverable approval/WebRTC errors are converted to FAILED with a safe reason. The Diagnostics screen provides the last successful stage, last error, exception, message, COPY DEBUG REPORT, and CLEAR controls. The most recent report is persisted in app-private SharedPreferences; uncaught exceptions are recorded before delegating to Android's normal crash handler.

The Browser Receiver quality milestone adds AUTO (default), LOW LATENCY, HD, and FULL HD profiles with real capture dimensions, 30 FPS limits, sender max/min bitrate parameters, MAINTAIN_FRAMERATE degradation preference, bounded AUTO adaptation, rotation-aware aspect-preserving capture, actual outbound WebRTC stats, quality limitation explanations, and explicit HD fallback reporting. The Nikai browser page uses contain-mode video and hides controls after connection.

Audio milestone status: official Android AudioPlaybackCapture PCM capture is implemented for API 29+ using the active MediaProjection, with protected/private playback restrictions respected and complete AudioRecord lifecycle cleanup. The pinned WebRTC SDK inspection showed that its public Java API has no arbitrary PCM injection path: JavaAudioDeviceModule callbacks observe stock input, while AudioTrack requires a native AudioSource. The repository now uses the focused playback-capture ADM boundary documented in AUDIO_ARCHITECTURE.md; physical validation remains required. Browser audio-track/autoplay diagnostics and explicit video-only fallback are included. Genuine completion requires the documented small native ADM/JNI bridge in AUDIO_ARCHITECTURE.md.

Latest physical-test follow-up: severe low FPS now triggers an immediate resolution step-down even when FULL HD was manually selected. Bitrate floors were lowered to protect 30 FPS and low latency. The UI now separates Selected profile from Actual stream. The new audio bridge uses Android AudioPlaybackCapture through a focused ADM boundary and adds a real WebRTC AudioTrack to the same PeerConnection; physical sound acceptance on a real phone and Nikai/PC browser remains the required validation step.
