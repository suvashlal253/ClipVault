# ClipVault v1.3.0 - Privacy-First Clipboard Manager & Optional Keyboard (IME) for Android

ClipVault is an offline, privacy-first Android clipboard manager built with **Kotlin 2.0.0**, **Jetpack Compose (Material 3)**, **Room SQLite + FTS4**, **Dagger Hilt**, and **Android Keystore AES-256-GCM**.

## What's New in v1.3.0 (Build 26)
1. **Optional ClipVault Keyboard (IME) (`ClipVaultInputMethodService.kt` + `res/xml/method.xml`):**
   - Clean, responsive Dark/Light English QWERTY keyboard with Shift, Backspace, Space, Enter, symbol toggle, and a 1-tap **Switch Keyboard** button.
   - 100% optional: users can continue using Gboard or any preferred keyboard, or switch to ClipVault Keyboard when they want active IME clipboard capture.
   - Lifecycle-safe `ClipboardManager.OnPrimaryClipChangedListener` that excludes password/sensitive fields, respects `ClipDescription.EXTRA_IS_SENSITIVE`, and never logs clipboard contents.
2. **Centralized Sequential Queue (`ClipboardServiceHelper.kt`):**
   - All capture sources (Foreground, Accessibility Service, Optional ClipVault IME, Quick Tile) submit synchronously via `clipQueue.trySend(...)` into a single `Channel<QueuedClipItem>(Channel.UNLIMITED)` guarded by a `Mutex`.
   - Rapid `A -> B -> C` copies are saved in exact arrival order (`C` newest first, then `B`, then `A`) with strictly increasing monotonic `createdAt` timestamps.
   - Deduplicates only consecutive identical copies (`A -> A`) while preserving non-consecutive re-copies (`A -> B -> A`).
   - Updates `lastProcessedText` **only after** `clipDao.insertClip(entity)` succeeds so failed insertions can be retried.
3. **Clipboard Automation Settings & Honest Android 10+ Privacy Disclosures (`SettingsDialog.kt`):**
   - Enable/disable automatic capture, inspect Accessibility Service status, enable or switch to the Optional ClipVault Keyboard, and view live Capture Status and Android 10+ OS privacy limitations.
4. **Unit Test Suite (`ClipboardArchitectureTest.kt`):**
   - 10 JUnit 4 unit tests covering sequential insertion, rapid `A -> B -> C` ordering, consecutive deduplication, `A -> B -> A` re-copying, DB failure retry, multi-source routing, IME listener lifecycle, password field exclusion, existing DB preservation, and Settings status calculation.

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
