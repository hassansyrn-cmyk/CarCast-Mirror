# CarCast Mirror architecture

CarCast is split into a phone Sender, a native Android Receiver, discovery providers, and a browser receiver. The Sender owns MediaProjection and capture. The native Receiver owns TLS/SAS verification, decoding, and the rendering Surface. Browser mode owns a temporary local HTTP page and WebSocket signaling.

The native receiver creates an ephemeral TLS 1.3 identity for the active receiver session and displays a six-digit SAS derived from its certificate fingerprint. The sender establishes TLS, verifies the SAS against the receiver UI, and only then begins stream negotiation. The SAS is not an encryption key and no trust decision persists beyond the session. Inside TLS, `TlsFramedChannel` provides bounded application framing with monotonic sequence, message type, payload length, and payload. TLS supplies confidentiality, integrity, authentication, and ordered transport protection.

The native flow is receiver capabilities → sender proposal → receiver accept or counter-proposal → codec configuration → encoded frames. Browser mode uses a separately free-bound WebSocket port, a reachable active-LAN address, bidirectional queued trickle ICE, Android WebRTC screen capture, browser `RTCPeerConnection`, and explicit phone approval/rejection.

The Android Auto boundary remains explicit. Factory Android Auto is not treated as a generic receiver. No additional casting protocol was added in this correctness iteration.
