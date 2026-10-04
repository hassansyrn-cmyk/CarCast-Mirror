
## Native CarCast Receiver milestone

The isolated `feature/native-receiver` branch adds a native Android/Android TV receiver path without changing the stable Browser Receiver. It reuses the existing TLS 1.3/SAS primitives, `_carcast._tcp.` discovery, and the proven sender-side `ScreenCapturerAndroid` plus playback-audio WebRTC session. Native signaling carries SDP/ICE inside TLS; media uses WebRTC DTLS-SRTP with native video rendering and Android audio output. The receiver advertises only while intentionally enabled, shows a friendly name and explicit CONNECT/DECLINE approval, enforces one active sender, and hides raw endpoint details from normal users.

The first native slice compiles and passes unit tests, but physical phone-to-phone/Android TV, device-audio, rotation, D-pad, reconnect, and hardware interoperability tests remain required. See [NATIVE_RECEIVER_ARCHITECTURE.md](NATIVE_RECEIVER_ARCHITECTURE.md) for the reuse audit, security model, lifecycle, and known limitations.

## Beta-readiness milestone

The `release/beta-readiness` branch is the controlled beta preparation line. It keeps the stable Browser Receiver capture ownership unchanged while adding explicit `0.9.0-beta1` release versioning, conditional publisher-controlled signing, conservative R8 rules for WebRTC/Bouncy Castle/reflection, an in-app Help/FAQ, prominent screen/audio disclosure, and redacted diagnostics. The current target is SDK 36; Android 17 local-network permission work is intentionally deferred until the target SDK changes. See [BETA_READINESS_AUDIT.md](BETA_READINESS_AUDIT.md), [BETA_TEST_MATRIX.md](BETA_TEST_MATRIX.md), and [RELEASE_CHECKLIST.md](RELEASE_CHECKLIST.md).

Privacy and Play preparation drafts are in [PRIVACY_POLICY_DRAFT.md](PRIVACY_POLICY_DRAFT.md), [PLAY_DATA_SAFETY_DRAFT.md](PLAY_DATA_SAFETY_DRAFT.md), and [PLAY_STORE_LISTING_DRAFT.md](PLAY_STORE_LISTING_DRAFT.md). No keystore or monetization code is included; see [RELEASE_SIGNING.md](RELEASE_SIGNING.md) and [MONETIZATION_PLAN.md](MONETIZATION_PLAN.md).

## Play beta monetization milestone

The `release/play-beta` branch is additive from `v0.9.0-beta1`. It integrates official Google Mobile Ads SDK `25.5.0` and UMP `4.0.0` behind `MonetizationManager`. Only a small idle nearby-device banner is allowed; ads are disabled during Browser Receiver, Native Receiver, MediaProjection consent, receiver approval, connection setup, permission flows, automotive interaction, and active casting. No screen, audio, WebRTC, receiver, or diagnostics data is passed to advertising SDKs.

Debug uses Google's official test App ID and banner unit ID. Release ads require publisher-provided `CARCAST_ADMOB_APP_ID` and `CARCAST_ADMOB_BANNER_AD_UNIT_ID`; missing release values disable ads safely. See [ADMOB_INTEGRATION.md](ADMOB_INTEGRATION.md), [PLAY_CONSOLE_BETA_GUIDE.md](PLAY_CONSOLE_BETA_GUIDE.md), [VERSIONING.md](VERSIONING.md), and [privacy-policy.html](privacy-policy.html).
