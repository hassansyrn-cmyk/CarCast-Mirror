# CarCast Mirror release checklist

## Engineering

- [ ] `./gradlew clean testDebugUnitTest assembleDebug bundleRelease`
- [ ] Signed AAB built with publisher-controlled upload key
- [ ] `apksigner verify --verbose` passes for signed APKs
- [ ] R8 release smoke test passes on a physical 64-bit device
- [ ] No secrets or keystores in Git
- [ ] BETA_TEST_MATRIX.md exit criteria complete

## Privacy and policy

- [ ] Final privacy policy hosted over HTTPS and linked from listing and app
- [ ] Play Data Safety form completed from the signed artifact and SDK review
- [ ] Prominent disclosures match screen/audio behavior
- [ ] Foreground-service declarations and video/audio use are accurately described
- [ ] Content rating, target audience, app access, and ads declarations complete

## Store and operations

- [ ] Store screenshots and feature graphic created
- [ ] Support contact and issue intake ready
- [ ] Internal/closed test track created
- [ ] Rollback version and staged rollout plan prepared
- [ ] Release notes state beta limitations honestly
