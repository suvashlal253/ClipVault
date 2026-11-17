# ClipVault v1.7.1 - Privacy-First Clipboard Manager & Bilingual English + বাংলা Phonetic Keyboard (IME)

ClipVault is an offline, privacy-first Android clipboard manager and bilingual IME built with **Kotlin 2.0.0**, **Jetpack Compose (Material 3)**, **Room SQLite + FTS4**, **Dagger Hilt**, and **Android Keystore AES-256-GCM**.

## What's New in v1.7.1 (Build 31) — Critical Bug Fix & Stability Patch
1. **Unified Settings Preference Keys:** Unified `keyboard_key_popup_preview` and `keyboard_high_contrast_accessibility` across `SettingsDialog.kt` and `ClipVaultInputMethodService.kt` with automatic legacy preference migration.
2. **Personal Dictionary Auto-Save Prevention:** Selecting a suggestion candidate now updates adaptive ranking (`recordAdaptiveCandidateSelection`) separately without polluting the user's Personal Dictionary.
3. **Mixed-Sentence Suggestions in Real Keyboard Workflow:** Connected `generateMixedSentenceCandidates()` into `BengaliSuggestionEngine.getSuggestions()` and `ClipVaultInputMethodService` so typing `ami tomorrow office jabo` surfaces `আমি tomorrow office যাব`, `আমি আগামীকাল অফিস যাব`, and `আমি tomorrow অফিস যাব`.
4. **Undo/Redo Session Isolation & Selection Safety:** `KeyboardUndoRedoManager` treats `fieldId == 0` as valid, clears history when switching between two `fieldId == 0` editors or apps, supports selection replacement undo/redo, and blocks unsafe undo/redo during active multi-character selections.
5. **Genuine Idempotent v1.6.0 Dictionary Migration:** Migrates user-saved entries and modified words from v1.6.0 preferences while filtering unmodified demo defaults and preserving legacy keys non-destructively.
6. **Bengali Auto-Correction, Font Rendering, Accessibility & Crash Isolation:** Added Bengali Unicode spelling corrections (`ভূল → ভুল`, `মানুস → মানুষ`), ambiguous alternatives (`কি ↔ কী`, `কোন ↔ কোনো`), `isElegantTextHeight` rendering for যুক্তাক্ষর/কার/ফলা, atomic TalkBack key focus, and `SafeInputConnectionExecutor` catching recoverable `Exception`s only.

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
