
## Native CarCast Receiver milestone

The isolated `feature/native-receiver` branch adds a native Android/Android TV receiver path without changing the stable Browser Receiver. It reuses the existing TLS 1.3/SAS primitives, `_carcast._tcp.` discovery, and the proven sender-side `ScreenCapturerAndroid` plus playback-audio WebRTC session. Native signaling carries SDP/ICE inside TLS; media uses WebRTC DTLS-SRTP with native video rendering and Android audio output. The receiver advertises only while intentionally enabled, shows a friendly name and explicit CONNECT/DECLINE approval, enforces one active sender, and hides raw endpoint details from normal users.

The first native slice compiles and passes unit tests, but physical phone-to-phone/Android TV, device-audio, rotation, D-pad, reconnect, and hardware interoperability tests remain required. See [NATIVE_RECEIVER_ARCHITECTURE.md](NATIVE_RECEIVER_ARCHITECTURE.md) for the reuse audit, security model, lifecycle, and known limitations.
