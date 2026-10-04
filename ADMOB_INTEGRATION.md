# CarCast Mirror AdMob and consent integration

## SDK decision

This branch uses Google's official Android Mobile Ads SDK `25.5.0` and User Messaging Platform SDK `4.0.0`. Google's current documentation lists Mobile Ads `25.5.0` as the latest release covered by the official release notes and recommends the Next-Gen migration path for future work. The current implementation uses the stable banner APIs because the milestone explicitly asks for conservative banner monetization.

## Central policy

`MonetizationManager` is the only class that initializes Mobile Ads, owns UMP consent state, exposes privacy options, and decides whether an ad surface may load. `SafeIdleBanner` is the only Compose banner view. The policy refuses banners whenever Browser Receiver or Native Receiver activity is active. It also exposes ads only on the idle Cast/nearby-devices surface, below the device controls.

There are no interstitial, rewarded, app-open, native-overlay, receiver, automotive, permission-flow, approval-flow, or active-casting ads. The banner is never inserted between a discovered receiver and its connect action. It is destroyed when the Compose surface leaves the hierarchy.

## Consent flow

At activity launch, UMP requests fresh consent information. UMP displays a form only when its official consent state says one is required. Ads are initialized and loaded only after `canRequestAds()` is true and the release configuration contains production IDs. Settings → Privacy exposes Google's privacy-options form only when UMP reports that an entry point is required. If the network, consent endpoint, or ad request fails, casting remains available and the banner is absent.

## Configuration

Debug builds use Google's official test App ID `ca-app-pub-3940256099942544~3347511713` and banner unit ID `ca-app-pub-3940256099942544/9214589741`. Release builds require these environment variables for ads to be enabled:

```text
CARCAST_ADMOB_APP_ID
CARCAST_ADMOB_BANNER_AD_UNIT_ID
```

If release IDs are absent, the release bundle still builds for structural validation but `ADS_CONFIGURED` is false and no ad request is made. The test IDs are not used as release fallback values.

## Data boundary

No screen frame, audio PCM, WebRTC track, receiver endpoint, SAS value, or debug report is passed to AdMob or UMP. AdMob receives only the data processed by Google's SDK for the ad request and consent operation. The local casting transport remains direct and encrypted between user-approved devices.

## Official references

[1]: https://developers.google.com/admob/android/quick-start "Set up Google Mobile Ads SDK for Android"
[2]: https://developers.google.com/ad-manager/mobile-ads-sdk/android/privacy "Set up User Messaging Platform SDK for Android"
[3]: https://developers.google.com/admob/android/banner "Set up banner ads for Android"
[4]: https://developers.google.com/admob/android/rel-notes "Google Mobile Ads SDK Android release notes"
