# ClipVault v1.5.0 - Privacy-First Clipboard Manager & Premium Keyboard (IME) for Android

ClipVault is an offline, privacy-first Android clipboard manager built with **Kotlin 2.0.0**, **Jetpack Compose (Material 3)**, **Room SQLite + FTS4**, **Dagger Hilt**, and **Android Keystore AES-256-GCM**.

## What's New in v1.5.0 (Build 28)
1. **Samsung/Gboard-Style Dark Keyboard Redesign (`ClipVaultInputMethodService.kt`):**
   - **8-Icon Top Toolbar:** **ClipVault App Shortcut (`CV`)** as first icon (tap launches ClipVault app, long-press launches ClipVault Settings), **Emoji Panel** (8 categories), **Keyboard Settings**, **Clipboard History Quick Access** (3–5 recent clips), **Text Editing Tools** (*Select All, Copy, Cut, Paste, Cursor Left/Right*), **`?123` Symbol Toggle**, **Theme Cycler**, and **More Options**.
   - **Dual-Label Keycaps & Long-Press Secondary Input:** `Q=1..P=0` and `A=@..L=)` secondary labels with long-press insertion and key preview popup.
   - **4 Keyboard Themes & 4-Level Haptic Feedback:** *Dark Charcoal*, *Pure Black AMOLED*, *Light*, *Follow System*, and *Off / Light / Medium / Strong* vibration intensity.
2. **One-Time First-Launch Background Auto-Capture Onboarding:**
   - `BackgroundCaptureOnboardingDialog` is shown only once after installation (`background_capture_onboarding_shown = true`) and never nags repeatedly; accessible anytime from **Settings → Clipboard Automation → About Background Auto-Capture**.

## Build & Test Instructions
```bash
# Run unit tests
./gradlew testDebugUnitTest

# Build debug APK
./gradlew assembleDebug
```

## Developer
- **Suvash Purkaystha** (AI Developer & Vibe Coder)
- **Repository:** https://github.com/suvashlal253/ClipVault
