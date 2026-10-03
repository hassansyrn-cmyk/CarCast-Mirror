# CarCast Mirror monetization plan

## Product principle

The core local casting path should remain usable without an account, cloud relay, advertising SDK, or forced subscription. Monetization must not weaken LAN-only privacy or add latency to the media path.

## Recommended staged plan

### Beta: no monetization

Use internal/closed testing to validate reliability, hardware compatibility, audio expectations, and support cost. Do not add billing code while the receiver and privacy model are still being validated.

### Post-beta: one-time Pro unlock

If users ask for advanced features, prefer a one-time in-app purchase for clearly bounded additions such as saved quality presets, multi-receiver profiles, or extended diagnostics. Keep basic Browser Receiver and Native Receiver functionality available without payment.

### Optional business distribution

Offer a separately supported enterprise/OEM package with documented device management and support terms. Do not bundle this with Play consumer claims or collect device identifiers unnecessarily.

## Explicit non-goals

- No ads over screen/audio content.
- No cloud relay or media upload to fund the product.
- No sale or sharing of screen, audio, or diagnostics.
- No dark-pattern paywall before a user can test local casting.

## Billing readiness gate

Before implementation, define the legal publisher entity, tax/billing ownership, refund/support process, entitlement persistence, offline behavior, and Play Billing policy review. None of those are configured in this beta branch.
