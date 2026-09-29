# Audio architecture status

## Implemented capture path

On Android 10 / API 29 and newer, `PlaybackCaptureAudioSource` uses the active MediaProjection authorization to build `AudioPlaybackCaptureConfiguration` and an `AudioRecord` configured for:

- PCM 16-bit
- 48,000 Hz
- Stereo preferred
- `USAGE_MEDIA`, `USAGE_GAME`, and `USAGE_UNKNOWN`

It does not use `MediaRecorder.AudioSource.MIC`, does not capture calls, and does not bypass protected/private playback policies. API <29 remains video-only.

## WebRTC integration

The pinned dependency is `io.github.webrtc-sdk:android:125.6422.07`. Its public Java API exposes `JavaAudioDeviceModule`, but not arbitrary PCM injection. The repository now uses the smallest adapter at that boundary:

1. Build the stock `JavaAudioDeviceModule` at 48 kHz stereo.
2. Initialize its WebRTC audio input.
3. Create the official playback-capture `AudioRecord` from the approved MediaProjection.
4. Replace the module's private input `AudioRecord` at the narrow adapter boundary using reflection because the SDK's `WebRtcAudioRecord` class is package-private.
5. Pass the existing ADM to `PeerConnectionFactory.Builder.setAudioDeviceModule`.
6. Create a WebRTC `AudioSource` and `AudioTrack`, then add it to the same PeerConnection as the screen video track.

WebRTC remains responsible for audio timing, Opus, RTP, DTLS-SRTP, jitter handling, and A/V synchronization. No custom RTP, Opus, or second streaming protocol is used.

## Physical validation status

The bridge now exists in code and the debug APK builds, but physical acceptance is still required on a real Android device and Nikai/PC browser. The test must confirm that the browser receives one video track and one audio track and that ordinary allowed playback is audible on the TV. If the device or SDK rejects the narrow adapter at runtime, the app reports video-only rather than bypassing capture policy.

## Lifecycle and fallback

The WebRTC ADM is prepared only after runtime `RECORD_AUDIO` permission, without touching MediaProjection. `ScreenCapturerAndroid.startCapture()` consumes the consent `Intent` and creates the single projection and virtual display. Only after that succeeds does the session call `screenCapturer.getMediaProjection()` and attach that exact object to `AudioPlaybackCaptureConfiguration`. The service never calls `MediaProjectionManager.getMediaProjection()` for audio and never creates a second virtual display. It is released with the WebRTC session on Stop, browser disconnect, projection stop, service destruction, or WebRTC failure. Permission denial, API <29, protected playback, null projection, and unsupported capture produce video-only status.

## Quality behavior

Video adaptation now treats severe low FPS as an immediate constraint, including manually selected FULL HD. The sender reduces capture resolution before allowing prolonged FPS collapse, uses lower bitrate floors, and reports the selected profile separately from actual transmitted resolution/FPS.

## Lifecycle regression guards

`ProjectionLifecycle` records the consent, approval, WebRTC session, capture start, projection retrieval, and SDK-owned virtual-display milestones. Both audio-off and audio-on sessions must remain at `getMediaProjection = 1` and `createVirtualDisplay = 1`; a duplicate approval is rejected before a second capture attempt. Physical audio acceptance on a phone and Nikai/PC browser remains required.
