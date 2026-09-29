# CarCast Mirror branding

## User-facing identity

**CarCast Mirror** uses the tagline **SECURE LOCAL CASTING**. The premium dark-utility interface keeps the teal accent (`#35D7C4`) and navy UI background (`#071014`), with `#101C22` cards, `#17272F` raised surfaces, and `#AABCC1` secondary text. A clear section hierarchy, 4/8 dp spacing, high-contrast labels, and horizontally scrollable route navigation keep the layout composed on narrow phones.

The app name and tagline remain centralized in `app/src/main/res/values/strings.xml`.

## Launcher icon

The supplied phone-to-display artwork is the canonical icon at `design/branding/carcast-icon-master.png`; `design/branding/carcast-icon-master.svg` is an SVG wrapper that references that raster master. `carcast-icon-foreground.png` is the same artwork with its navy base removed for layered use, and `carcast-icon-monochrome.png` is a white silhouette derived from that artwork.

Android uses the full transparent foreground for the in-app brand mark and splash screen. Adaptive launcher icons use a separate, safely inset 108 dp foreground and matching monochrome layer under `app/src/main/res/drawable-xxxhdpi/`; the adaptive background is `#010D23`, sampled from the supplied image. The legacy `ic_launcher` density assets use the supplied full-color artwork at each Android density, with circularly masked `ic_launcher_round` variants. The launcher manifest references and resource IDs remain unchanged.

## Visual review previews

The regenerated design previews are `design/branding/previews/01-launcher-icon.png`, `02-adaptive-circular-icon.png`, `03-adaptive-rounded-square-icon.png`, `04-themed-monochrome-icon.png`, `05-splash-screen.png`, and `06-home-screen-branding.png`. They use the supplied icon; these are design previews, not device screenshots.

Confirm adaptive masks, themed icons, safe areas, and large text on a physical Android device before release.
