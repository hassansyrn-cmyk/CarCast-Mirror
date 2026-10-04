# CarCast Mirror Google Play beta guide

This guide assumes the publisher has a Google Play Developer account and owns the `com.carcast.mirror` application identity. The current branch is `release/play-beta`; the rollback tag `v0.9.0-beta1` remains unchanged.

## 1. Create or select the application

In Play Console, choose **All apps → Create app** if CarCast Mirror does not already exist. Use the exact app name **CarCast Mirror**, default language, **App** (not Game), and the free pricing choice unless the publisher has a different commercial plan. Confirm that the package name is exactly `com.carcast.mirror`; it must match the signed AAB.

If an application already exists, open it and verify the package name under **App integrity** or the application details before uploading anything.

## 2. Configure Play App Signing

Open **Test and release → App integrity**. Use **Play App Signing**. Google manages the app signing key. The publisher retains the upload key used to sign the AAB submitted to Play Console. Create the upload key locally using the commands in `RELEASE_SIGNING.md`; never send its password or keystore to Manus or commit it to Git.

For the first upload, Play Console may offer to enroll the app in Play App Signing and may provide an upload certificate flow. Follow the displayed enrollment steps and preserve the upload-key backup securely.

## 3. Configure the store listing

Under **Grow users → Store presence → Main store listing**, enter the consumer-facing text from `PLAY_STORE_LISTING_DRAFT.md`. Replace the privacy-policy placeholder with the final HTTPS URL. Upload truthful phone screenshots from the screenshot plan. Do not claim Chromecast mirroring, universal Android Auto mirroring, every Smart TV, or every vehicle.

Because this build includes optional AdMob banner ads, set the Play Console app declaration **Contains ads** to **Yes**. Keep the store listing and the actual binary consistent.

## 4. Complete App content

Open **Policy and programs → App content** and complete the displayed declarations. Provide the privacy-policy URL. Complete **Data safety** from `PLAY_DATA_SAFETY_DRAFT.md`, but verify every answer against the signed artifact, Google Mobile Ads SDK, UMP, WebRTC, Bouncy Castle, ZXing, and Java-WebSocket behavior. Complete **Target audience and content**, **Content rating**, and any **App access** questionnaire. CarCast has no account; provide the local-network test steps if Play asks for access instructions.

Review the **Ads** declaration and make sure it says the app contains ads. If Play requests a **Foreground service** declaration, explain that the service is user-initiated for MediaProjection screen mirroring and active local casting, not background surveillance or boot-started capture. If Play presents a permissions declaration, describe `RECORD_AUDIO` as optional Android playback capture and MediaProjection as user-approved screen capture.

## 5. Upload the beta build

Build a signed AAB using the protected release workflow. Confirm the version name is `0.9.1-beta` and version code is `3`; every later upload must use a larger version code. In **Test and release → Testing → Internal testing**, create or open the internal testing track and upload the signed AAB. Review the generated release notes and Play's pre-launch warnings.

Use **Closed testing** instead when a broader invited tester group is needed. Create the closed-testing track, choose the tester management method, add tester email addresses or a Google Group, and save the tester list. Do not publish to a wider audience until physical regression and policy review are complete.

## 6. Publish to testers

Complete the track's release name and release notes. Review countries/regions, tester access, and the generated app bundle explorer results. Click **Review release**, resolve blocking errors, and start the rollout to the selected testing track. Copy the opt-in URL and send it only to the intended testers.

## 7. After rollout

Install from Google Play rather than sideloading for the final beta check. Verify app version, consent behavior, idle banner behavior, no ads during casting, offline LAN casting, Browser Receiver, Native Receiver where available, rotation, audio, stop, and second-session recovery. Record failures with the redacted in-app report. Do not merge this branch into `main` or create `v0.9.1-play-beta` until the physical checklist passes.

## Official references

[1]: https://support.google.com/googleplay/android-developer/answer/9859152 "Create and set up your app"
[2]: https://support.google.com/googleplay/android-developer/answer/9842756 "Set up an open, closed, or internal test"
[3]: https://support.google.com/googleplay/android-developer/answer/9845334 "Prepare and roll out a release"
[4]: https://support.google.com/googleplay/android-developer/answer/9859455 "App content"
[5]: https://support.google.com/googleplay/android-developer/answer/10787469 "Data safety"
[6]: https://developer.android.com/studio/publish/app-signing "Sign your app"
