# Google Cast / Chromecast feasibility report

## Decision

**Do not implement Google Cast support for the current CarCast WebRTC architecture yet.** The official Cast documentation supports launching a registered Custom Web Receiver and exchanging messages with it, but it describes Cast media delivery around supported Cast media formats and streaming protocols. It explicitly warns that an app requiring an unsupported media format cannot integrate with Cast.[1] [2]

CarCast's current Browser and Native Receiver paths use a live WebRTC PeerConnection carrying `ScreenCapturerAndroid` video and an Opus device-playback track. The official Cast documentation does not define WebRTC `RTCPeerConnection` as a supported Cast media delivery mechanism, and it does not provide a Cast API that directly forwards Android `MediaProjection` frames. A Cast receiver is an HTML5 app, but that fact alone is not enough to claim that WebRTC, WebSocket signaling, autoplay, or arbitrary local peer media is supported across Chromecast generations and Google TV devices.

Following the milestone instruction, no Cast device picker, fake discovery result, placeholder App ID, reverse-engineered protocol, or unsupported WebRTC Custom Receiver has been added.

## Answers to the required questions

### 1. Can an Android app send arbitrary live MediaProjection capture directly through the official Cast SDK?

**No direct API was found.** The Android Cast Sender SDK manages Cast discovery, launches a Web Receiver application, creates a sender/receiver communication channel, and controls supported media playback. Its documented flow is not a MediaProjection screen-capture transport.[1]

Android's system screen casting and Google Cast application sessions are separate mechanisms. CarCast should not claim that the official Cast SDK can take the existing MediaProjection surface and transmit it directly.

### 2. Can a Custom Web Receiver be used as the CarCast receiver endpoint?

**Yes, in principle.** Google documents a Custom Web Receiver as a self-hosted HTML5 application implemented with the Web Receiver API. It can contain custom UI, authorization, and custom JavaScript logic.[3] A registered application ID is used by the sender to launch it.[4]

That answers the receiver-launch question, not the media-transport question.

### 3. Can the existing WebRTC screen and audio stream run inside a Cast Custom Web Receiver?

**Not as an officially supported Cast media path based on the current documentation.** The Cast overview says integrations need a Cast-supported media format, such as HLS or DASH, and warns that unsupported media formats cannot be integrated.[2] The documented Web Receiver streaming protocols are DASH, HLS, and Smooth Streaming.[5]

A Custom Web Receiver can run custom JavaScript and receive Cast custom messages, so a developer might experiment with browser APIs. That would be an unvalidated runtime dependency, not a supported CarCast feature. It would also require a carefully authenticated signaling path and device-by-device validation. I am not treating that experiment as a deliverable.

### 4. Does Google Cast Web Receiver support the required browser APIs?

The official docs establish that the receiver is an HTML5 application and expose the Cast Web Receiver API, `CastReceiverContext`, `PlayerManager`, and custom message channels.[3] They do **not** establish a cross-device support contract for:

- `RTCPeerConnection` as a CarCast media input;
- WebSocket signaling to a phone;
- WebRTC H.264/VP8/VP9 negotiation inside a Cast application;
- WebRTC Opus reception and synchronized playback;
- autoplay behavior for a custom WebRTC media element.

The Cast media documentation does list H.264 and several other codecs for particular Cast devices, but in the context of Cast-supported media containers and playback. It separately lists Opus for some audio devices; that is not evidence that a WebRTC Opus track is supported as an arbitrary Cast receiver input.[6]

Therefore the honest result is: **some underlying Chromium/browser APIs may exist on particular receiver firmware, but Google does not document them as the supported Cast transport contract required by CarCast.**

### 5. What Google registration and App ID are required?

A Styled Media Receiver or Custom Receiver must be registered in the Google Cast SDK Developer Console to obtain an application ID.[4] The Android sender's `OptionsProvider` supplies that ID to `CastOptions`, and the manifest declares the provider class.[1]

A Custom Receiver is the required type for CarCast's custom UI, authorization, and signaling logic. A real App ID must be supplied; no fake ID should be committed.

### 6. What parts require a hosted HTTPS receiver page?

A Custom Web Receiver is a hosted HTML5 application. Google states that development may use HTTP, including an internal/NAT-registered address, but a published receiver must use HTTPS and cannot use localhost.[4] The Cast SDK JavaScript itself must be loaded from Google's `gstatic.com` URL and should not be self-hosted.[3]

The receiver page therefore needs a reachable URL. The current CarCast LAN-only requirement does not remove this requirement for launching a Cast Custom Receiver. The page hosting can be static and inexpensive, but this milestone must not silently introduce paid hosting.

### 7. What can be implemented locally now?

Safe local work:

- Keep the current Browser Receiver and Native Receiver unchanged.
- Keep a Cast integration boundary documented with an App ID configuration value that remains unset.
- Prepare an Android `OptionsProvider` and Cast custom namespace only after choosing to proceed with the officially supported media architecture.
- Build and validate a Custom Web Receiver shell that only proves Cast application launch and custom messaging, without claiming CarCast screen mirroring.

Not safe to claim locally:

- Google Cast discovery as a working CarCast receiver before using the official Cast SDK with a real App ID.
- WebRTC screen/audio delivery through Chromecast.
- Chromecast support merely because a Cast device appears in a picker.

## Recommended architecture if Google Cast support remains a product requirement

There are two distinct options:

1. **Official Cast media architecture:** adapt a future CarCast live stream to a Cast-supported delivery format and receiver-compatible packaging. This would require a separate media packaging/serving design and careful latency evaluation. It must not replace or refactor the proven WebRTC Browser or Native Receiver pipelines.
2. **Officially registered Custom Receiver experiment:** first prove only receiver launch and Cast custom messaging with a real registered App ID. Then run a device-matrix experiment to determine whether the target firmware supports the required browser APIs. This must remain explicitly experimental until Google documentation and physical tests establish a reliable contract.

The first option is the only one that aligns directly with the documented Cast media model. It may conflict with CarCast's low-latency, local-only, no-cloud goals because Cast-supported streaming generally expects receiver-reachable HTTP(S) media resources and documented streaming protocols.[5]

## Manual action required from the user

No Google Cast account data or App ID is needed for this feasibility stop. If you want to authorize the next official Cast experiment, you must:

1. Open the [Google Cast SDK Developer Console](https://cast.google.com/publish).
2. Register a **Custom Receiver** application.
3. Provide a receiver URL. Development can use an HTTP URL reachable by the Cast device; a published application must use HTTPS and cannot use localhost.[4]
4. Register the Chromecast/Google TV device for development testing.
5. Send Manus the resulting **Cast Application ID** and the receiver URL. Do not send Google account passwords or private credentials.

## Files changed in this feasibility milestone

- `GOOGLE_CAST_FEASIBILITY.md` — this report.

No Android source, Browser Receiver source, Native Receiver source, Cast dependency, or fake App ID was added.

## Validation

The repository was inspected from the current `feature/native-receiver` baseline. No implementation build was run because the correct outcome of this milestone is to stop before unsupported Cast code is introduced. The existing Native Receiver debug build and unit-test status remain unchanged from commit `5a9742a`.

## References

[1]: https://developers.google.com/cast/docs/android_sender/integrate "Integrate Cast Into Your Android App"
[2]: https://developers.google.com/cast/docs/overview "Google Cast Overview"
[3]: https://developers.google.com/cast/docs/caf_receiver/basic "Create a Basic Web Receiver App"
[4]: https://developers.google.com/cast/docs/registration "Google Cast Application Registration"
[5]: https://developers.google.com/cast/docs/media/streaming_protocols "Web Receiver Player Streaming Protocols"
[6]: https://developers.google.com/cast/docs/media "Supported Media for Google Cast"
