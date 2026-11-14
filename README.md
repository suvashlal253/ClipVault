# ClipVault v1.6.0 - Privacy-First Clipboard Manager & Bilingual English + বাংলা Phonetic Keyboard (IME)

ClipVault is an offline, privacy-first Android clipboard manager and bilingual IME built with **Kotlin 2.0.0**, **Jetpack Compose (Material 3)**, **Room SQLite + FTS4**, **Dagger Hilt**, and **Android Keystore AES-256-GCM**.

## What's New in v1.6.0 (Build 29)
1. **Bilingual English + বাংলা (Bangla Phonetic) Keyboard (`com.clipvault.android.keyboard.transliteration`):**
   - **Real-Time Bengali Transliteration:** Type Banglish using English letters (`ami → আমি`, `tumi → তুমি`, `amar nam → আমার নাম`, `kemon acho → কেমন আছো`, `bhalo → ভালো`, `bangladesh → বাংলাদেশ`, `bhalobashi → ভালোবাসি`, `apni kemon achen → আপনি কেমন আছেন`, `suvash → সুভাষ`).
   - **3–5 Ranked Suggestions & Contextual Next-Word Prediction:** Fixed-height suggestion bar above the keyboard for both Bangla Phonetic and English QWERTY modes, plus one-tap `+ Save` Personal Dictionary learning and Custom Text Shortcuts (`brb → be right back`, `amar nam → আমার নাম সুভাষ`).
   - **Dual Language Switching:** Swipe left/right on the spacebar or tap the dedicated `🌐 EN / 🌐 বাং` Globe key to switch instantaneously between English and বাংলা.
2. **Shared Premium Features in Both Languages:**
   - 8-Icon Top Toolbar (`CV` shortcut first, 8-category Emoji panel, Keyboard Settings, Clipboard Quick Access, Text Editing Toolbar with Undo/Redo, `?123`, Theme cycler, More Options), 4 keyboard themes, 3 height presets, 4-level haptic feedback, optional dedicated Number Row (`1–0 / ১–০`), and one-time Background Auto-Capture onboarding.

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
