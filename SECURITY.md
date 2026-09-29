# Security status

The native receiver creates an ephemeral TLS 1.3 identity for the active receiver session. The sender connects through TLS and calculates a six-digit SAS from the receiver certificate SHA-256 fingerprint. The visible SAS is a verification value only; it is not used as an encryption key. The sender must match the SAS entered from the receiver UI before streaming begins. TLS carries the media transport.

The browser receiver uses a cryptographically random temporary token, LAN-only ICE, a single authorized browser, explicit phone approval, and standard WebRTC DTLS-SRTP. The browser capture path is promoted to a `mediaProjection` foreground service before `ScreenCapturerAndroid` starts. Disconnect and Stop invalidate the browser session and stop capture. Both directions of trickle ICE are queued safely around SDP setup.

`TlsFramedChannel` is application framing only. It carries sequence, message type, payload length, and payload inside the already-established TLS 1.3 stream. TLS provides confidentiality, integrity, authentication, and ordered transport protection. Monotonic application sequence numbers reject replay or out-of-order frames. No custom AES-GCM or all-zero key remains.

The current build still requires physical-device and Nikai-browser validation. TLS certificate verification uses a session-scoped fingerprint/SAS confirmation rather than a persisted trust decision; no trust is persisted after the receiver session ends. The SAS is valid for the lifetime of the ephemeral TLS receiver identity; the UI does not claim a misleading countdown.
