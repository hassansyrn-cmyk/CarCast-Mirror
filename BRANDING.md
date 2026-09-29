# CarCast Mirror branding

## User-facing identity

- **App name:** CarCast Mirror
- **Tagline:** SECURE LOCAL CASTING
- **Primary accent:** `#35D7C4`
- **Dark background:** `#071014`
- **Secondary surface:** `#101C22`

The app name is centralized in `app/src/main/res/values/strings.xml` and is used by the Android application label, launcher, Compose brand mark, splash transition, and foreground-service notification titles.

## Launcher icon

The original CarCast symbol combines a phone/display outline with wireless casting arcs. It contains no text and does not copy Google Cast, Chromecast, Android Auto, or vehicle branding.

- Master artwork: `design/branding/carcast-icon-master.png`
- Transparent foreground artwork: `design/branding/carcast-icon-foreground.png`
- Adaptive background: `app/src/main/res/drawable/carcast_icon_background.xml`
- Android 13+ themed layer: `app/src/main/res/drawable/carcast_icon_monochrome.xml`
- Adaptive resources: `app/src/main/res/mipmap-anydpi-v26/ic_launcher*.xml`
- Legacy density resources: `app/src/main/res/mipmap-*dpi/ic_launcher*.png`

The manifest references `@mipmap/ic_launcher` and `@mipmap/ic_launcher_round`, with the app label set to `@string/app_name`.

## Splash and in-app mark

The app uses AndroidX SplashScreen rather than a fake startup activity. The splash uses the CarCast symbol on the brand navy background and transitions to the Compose home screen. The home header uses the same reusable symbol plus the centralized app name and tagline.

## Visual review previews

The deterministic preview set is stored under `design/branding/previews/`:

1. `01-launcher-icon.png`
2. `02-adaptive-circular-icon.png`
3. `03-adaptive-rounded-square-icon.png`
4. `04-themed-monochrome-icon.png`
5. `05-splash-screen.png`
6. `06-home-screen-branding.png`

These are design previews. Final device appearance should be confirmed after installing the debug APK on a physical Android launcher, including OEM adaptive masks and Android 13 themed icons.
