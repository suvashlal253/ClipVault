# ClipVault v1.4.0 - Privacy-First Clipboard Manager & Premium Keyboard (IME) for Android

ClipVault is an offline, privacy-first Android clipboard manager built with **Kotlin 2.0.0**, **Jetpack Compose (Material 3)**, **Room SQLite + FTS4**, **Dagger Hilt**, and **Android Keystore AES-256-GCM**.

## What's New in v1.4.0 (Build 27)
1. **Cross-Keyboard Clipboard Capture Reliability (Gboard, Samsung Keyboard & ClipVault IME):**
   - Restored legitimate foreground clipboard sync (`MainActivity.onWindowFocusChanged(true)` & foreground `OnPrimaryClipChangedListener`) so opening ClipVault while using Gboard or Samsung Keyboard immediately captures the latest permitted clip into `ClipboardServiceHelper`.
   - Added 1-tap **Import Clip** button in `ClipboardScreen.kt` and `ClipMonitorService.kt` ongoing notification.
   - Enhanced `ClipAccessibilityService.kt` to inspect focused input/accessibility nodes when `event.source` is null while another keyboard is active.
   - Accurate status detection (`ClipVault Keyboard Active`, `Clipboard Capture Active`, `Background Capture Restricted`, `Waiting for Clipboard Access`, `Capture Permission Required`).
2. **Premium ClipVault Keyboard UI Redesign (`ClipVaultInputMethodService.kt`):**
   - **Bigger Letters & Clear Hierarchy:** **24–26sp** alphabet keys, **21–24sp** numbers, **20–22sp** function keys, and **13–14sp** compact header.
   - **Keyboard Height Presets:** User-selectable **Compact**, **Normal**, and **Tall** modes in Settings.
   - **Rich Typing Interactions:** Single-tap Shift, double-tap **Caps Lock (`⇪`)**, press-and-hold repeating Backspace, `123` and `#+=` symbol pages, tactile key-press animation, and optional **Key Vibration** & **Key Sound**.

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
