# ClipVault v1.7.2 - Privacy-First Clipboard Manager & Bilingual English + বাংলা Phonetic Keyboard (IME)

ClipVault is an offline, privacy-first Android clipboard manager and bilingual IME built with **Kotlin 2.0.0**, **Jetpack Compose (Material 3)**, **Room SQLite + FTS4**, **Dagger Hilt**, and **Android Keystore AES-256-GCM**.

## What's New in v1.7.2 (Build 32) — Critical Bug Fixes, Stability & Bengali Keyboard Enhancement
1. **Duplicate Clipboard Prevention (`ClipboardContentNormalizer` + Room `MIGRATION_1_2`):** Prevents duplicate clips when copying from ClipVault or external apps via Unicode NFC normalization, line-ending normalization, indexed SHA-256 `content_hash` lookup with exact content verification, concurrency-safe mutex serialization, and short-lived app-originated copy fingerprinting while preserving existing clip IDs, timestamps, pins, and tags.
2. **Bengali Numeric Row (`১ ২ ৩ ৪ ৫ ৬ ৭ ৮ ৯ ০`):** Automatically displays and commits Bengali numerals (`১ ২ ৩ ৪ ৫ ৬ ৭ ৮ ৯ ০`) in বাংলা mode and English numerals (`1 2 3 4 5 6 7 8 9 0`) in English mode across the number row, top-row long-press hints, and symbol layer.
3. **Avro-Grade Bengali Phonetic Transliteration Engine:** Expanded phonetic rule engine and 450+ word lexicon supporting all 18 complex conjuncts (`ক্ত, ক্ষ, জ্ঞ, ত্র, দ্ধ, দ্য, ন্দ, ন্ধ, ম্প, ষ্ট, স্থ, স্ক, স্ত, ন্ত, ত্ত, দ্ম, হ্ন, ল্প`) and natural Bengali words (`ব্যক্তিগত`, `বিদ্যুৎ`, `কৃষি`, `চাঁদ`, `উৎসব`, `হ্যাঁ`, `ভালো`, `করছি`, `ফেসবুক`, `বিকাশ`, `অঙ্ক`, `বাক্য`, `পুরকায়স্থ`, `ধর্ম`).
4. **WhatsApp, Messenger & Gemini Multiline Enter Key Fix:** Properly distinguishes multiline fields (`INSERT_NEWLINE` without closing the keyboard) from single-line Search/Go/Next/Done actions (`PERFORM_EDITOR_ACTION`).
5. **Clipboard Toolbar Icon, Duplicate About Removal & Keyboard Picker Name:** Replaced `CV` text with `ic_content_copy` vector icon, removed the duplicate `ⓘ` button from the top toolbar (kept in `⋯` More Options), and set the system keyboard picker name to **ClipVault Keyboard** (**English (US)** / **বাংলা**).
6. **Mixed-Language Sentence Punctuation & Undo/Redo Restoration:** Preserves `, . ? ! ।` across mixed sentences and restores cursor/selection state on Undo/Redo.

## Build & Test Instructions
```bash
# Run all 27 JUnit 4 unit tests
./gradlew testDebugUnitTest

# Build debug APK (for testing)
./gradlew assembleDebug

# Build signed production release APK & AAB
./gradlew assembleRelease bundleRelease
```

## Developer
- **Suvash Purkaystha** (AI Developer & Vibe Coder)
- **Repository:** https://github.com/suvashlal253/ClipVault
