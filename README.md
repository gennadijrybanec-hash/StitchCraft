# StitchCraft 1.0 RC7 — Store & Release Prep

Stable commercial release candidate based on RC4.1 Commercial Clean.

## Already implemented
- Photo → cross-stitch pattern
- DMC palette and symbols
- Aida count and finished-size calculation
- Saved projects and progress
- Project import/export
- PDF / CSV / PNG export
- Free / Pro limits
- Google Play Billing 9.1.0
- Lifetime Pro product ID: `stitchcraft_pro_lifetime`
- Stable debug signing key for test APK updates

## RC5 release preparation
- compileSdk / targetSdk: 36
- Android Gradle Plugin: 8.11.1
- Gradle in GitHub Actions: 8.13
- versionCode: 105
- versionName: 1.0.0-rc5
- GitHub Actions builds a Debug APK and unsigned Release AAB
- When Google Play upload-key secrets are configured, Actions also builds a signed Google Play AAB
- Production keystore/passwords are never stored in the repository

## Google Play signing secrets
Add these GitHub repository secrets only when the Google Play upload key is created:
- `STITCHCRAFT_UPLOAD_KEYSTORE_B64`
- `STITCHCRAFT_KEYSTORE_PASSWORD`
- `STITCHCRAFT_KEY_ALIAS`
- `STITCHCRAFT_KEY_PASSWORD`

The application ID remains `com.stitchcraft.app` so future Google Play updates can continue using the same store listing and user data.


## RC7
- Materials search from the pattern screen (Aida + DMC)
- Support contact and Privacy Policy link in Pro/About
- versionCode 108 / versionName 1.0.0-rc7-store-prep


## RC7 FIX1
- Restored vertical scrolling of the whole pattern screen
- Fixed canvas to a stable 360dp editing viewport so page content can scroll
- Full palette now scrolls with the page instead of being trapped in a small nested list
- Materials button moved out of the crowded export row
- versionCode 109 / versionName 1.0.0-rc7-fix1-scroll


## RC7 FIX2 Gesture fix
- One-finger vertical swipes over the pattern scroll the whole page.
- Two-finger gestures pan and zoom the pattern.
- Tap remains available for stitch editing.


## RC7 FIX3 — Anchored chart gestures
- One finger scrolls the whole pattern page.
- Tap still marks/edits a stitch.
- Two fingers zoom the chart.
- Internal chart pan is disabled so the chart cannot drift out of its viewport.
- Materials/store search and all RC7 features are preserved.
