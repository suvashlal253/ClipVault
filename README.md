# ClipVault v1.7.0 - Privacy-First Clipboard Manager & Bilingual English + বাংলা Phonetic Keyboard (IME)

ClipVault is an offline, privacy-first Android clipboard manager and bilingual IME built with **Kotlin 2.0.0**, **Jetpack Compose (Material 3)**, **Room SQLite + FTS4**, **Dagger Hilt**, and **Android Keystore AES-256-GCM**.

## What's New in v1.7.0 (Build 30)
1. **Clean Keyboard Toolbar & Removed Duplicate Clipboard Panel:**
   - Removed the duplicate clipboard history button and panel from `ClipVaultInputMethodService` while keeping the **CV App Shortcut** and full background clipboard capture.
   - Added a visible `ⓘ` **About Developer** button in both English and বাংলা keyboard layouts.
2. **Upgraded Offline Bengali Phonetic Engine & Mixed-Language Typing:**
   - Strict 5-step matching order (Personal Dictionary -> Longest-match-first Multi-word Phrase Shortcuts -> Single-word Shortcuts -> General Token Language Classification -> Phonetic Transliteration with LRU cache).
   - Mixed-language sentence candidates (`ami tomorrow office jabo` → `আমি tomorrow office যাব`, `আমি আগামীকাল অফিস যাব`, `আমি tomorrow অফিস যাব`).
3. **Safe Keyboard-Managed Undo/Redo & Suggestion-First Auto-Correction:**
   - Bounded 30-step `KeyboardUndoRedoManager` with editor-switch clearing and password exclusion.
   - Suggestion-first auto-correction default (`keyboard_auto_apply_on_space = false`).
4. **All 20 Bilingual Keyboard Features:**
   - One-Handed Mode (`Off / Left / Right`), Key Size & Touch Target presets (`Small / Medium / Large`), Bengali Unicode Font styles, Anchored Key Pop-up Preview, Smart Language Detection prompt, TalkBack Accessibility & High-Contrast Mode, and Empty-by-Default Personal Dictionary with live search.

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
