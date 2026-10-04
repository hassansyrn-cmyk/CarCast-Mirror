# CarCast Mirror monetization plan

## Product principle

The core local casting path remains usable without an account, cloud relay, or forced subscription. Monetization must not weaken LAN-only privacy or add latency to the media path. The beta uses Google's official Mobile Ads and UMP SDKs only for a conservative idle banner.

## Recommended staged plan

### Beta: safe banner only

The only V1 placement is a small banner below the idle nearby-device actions. It is absent during Browser Receiver, Native Receiver, MediaProjection consent, receiver approval, permission requests, connection establishment, automotive interaction, and every active media session. There are no interstitial, rewarded, app-open, or active-casting ads.

### Post-beta: one-time Pro unlock

If users ask for advanced features, prefer a one-time in-app purchase for clearly bounded additions such as saved quality presets, multi-receiver profiles, or extended diagnostics. Keep basic Browser Receiver and Native Receiver functionality available without payment.

### Optional business distribution

Offer a separately supported enterprise/OEM package with documented device management and support terms. Do not bundle this with Play consumer claims or collect device identifiers unnecessarily.

## Explicit non-goals

- No ads over screen/audio content or receiver video.
- No interstitial, rewarded, or app-open ads in the beta.
- No cloud relay or media upload to fund the product.
- No sale or sharing of screen, audio, or diagnostics.
- No dark-pattern paywall before a user can test local casting.

## Billing readiness gate

Before implementation, define the legal publisher entity, tax/billing ownership, refund/support process, entitlement persistence, offline behavior, and Play Billing policy review. None of those are configured in this beta branch.
