# CarCast Mirror branding

## User-facing identity

**CarCast Mirror** uses the tagline **SECURE LOCAL CASTING**. The premium dark-utility interface keeps the familiar teal accent (`#35D7C4`) and navy background (`#071014`), with `#101C22` cards, `#17272F` raised surfaces, and `#AABCC1` secondary text. A clear section hierarchy, 4/8 dp spacing, high-contrast labels, and horizontally scrollable route navigation keep the layout composed on narrow phones.

The app name and tagline remain centralized in `app/src/main/res/values/strings.xml`.

## Launcher icon

The refreshed no-text mark pairs a phone silhouette with a separate display frame and two cast arcs. Editable vector sources are `design/branding/carcast-icon-foreground.svg` and `design/branding/carcast-icon-master.svg`; their high-resolution PNG counterparts are `design/branding/carcast-icon-foreground.png` and `design/branding/carcast-icon-master.png`.

Android uses the vector foreground at `app/src/main/res/drawable/carcast_icon_foreground.xml`, with the adaptive background in `app/src/main/res/drawable/carcast_icon_background.xml` and the themed monochrome layer in `app/src/main/res/drawable/carcast_icon_monochrome.xml`. Existing adaptive launcher references and legacy density resource names are unchanged; the same foreground vector is used by the AndroidX splash screen and in-app brand mark.

## Visual review previews

The refreshed deterministic previews are `design/branding/previews/01-launcher-icon.png`, `02-adaptive-circular-icon.png`, `03-adaptive-rounded-square-icon.png`, `04-themed-monochrome-icon.png`, `05-splash-screen.png`, and `06-home-screen-branding.png`.

These are design previews, not device screenshots. Confirm adaptive masks, themed icons, safe areas, and large text on a physical Android device before release.
