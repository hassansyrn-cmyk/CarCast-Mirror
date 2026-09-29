# Physical test plan

Install the debug APK on two Android devices connected to the same Wi-Fi. On Device B, choose **Receive**, attach the visible Surface, and start the receiver. Confirm that a temporary `_carcast._tcp` service appears. On Phone A, choose **Cast screen**, allow local-network access when requested, and scan for displays.

Select Device B. Enter the six-digit code shown by the receiver. Verify that an incorrect code does not start the stream. With the correct code, approve the official Android screen-sharing dialog. Verify that the sender notification says **Screen mirroring active** and that Device B displays live pixels.

Open another app on Phone A, rotate the phone in both directions, lock and unlock the phone, temporarily disable Wi-Fi, restart the receiver, and stop the sender from the foreground notification. Check that the receiver disconnects cleanly, the sender does not leave a foreground service behind, and a later session can pair again.

For manual fallback, use **Enter IP, port, and pairing code**. Use `adb logcat | grep -E 'CarCast|MediaProjection|MediaCodec'` while testing. Capture the actual resolution, codec, FPS, bitrate, and reconnect behavior from logs; do not treat the diagnostics placeholders as measurements until a session reports values.
