# ClipVault v1.7.4 - Privacy-First Clipboard Manager & Bilingual English + বাংলা Phonetic Keyboard (IME)

ClipVault is an offline, privacy-first Android clipboard manager and bilingual IME built with **Kotlin 2.0.0**, **Jetpack Compose (Material 3)**, **Room SQLite + FTS4**, **Dagger Hilt**, and **Android Keystore AES-256-GCM**.

## What's New in v1.7.4 (Build 34) — Final Database Safety, Performance & Production Release Fixes
1. **Strict Production Release Signing & Tag-Only Release Pipeline (`.github/workflows/build-and-release-apk.yml` & `app/build.gradle.kts`):**
   - Removed all hardcoded keystore passwords and ephemeral fallback keystore generation.
   - Separated CI into `verify-and-debug` (runs all 31 JUnit 4 tests and `assembleDebug` on every push/PR without needing production signing secrets) and `release` (triggers strictly on `refs/tags/v*.*.*`, validates Git tag == Gradle `versionName` & `versionCode >= 34`, requires the permanent production keystore from GitHub Secrets, and fails safely if secrets are absent).
2. **Metadata-Safe Clipboard Database Migration (`AppDatabase.kt` `MIGRATION_1_2` + `ClipboardMigrationHelper`):**
   - Merges distinct tags case-insensitively across equivalent duplicates, preserves pinned status if any duplicate was pinned, selects the most informative `sourceApp`, preserves the oldest original `createdAt` timestamp, leaves Vault encrypted records untouched with `content_hash = ''`, persists merged metadata onto the keeper row before deleting duplicate rows, and rebuilds `clips_fts` atomically.
3. **Consistent Clipboard `content_hash` Maintenance (`ClipDao.kt`, `ClipboardRepository.kt`, `ClipEntity.kt`):**
   - `updateClipContent`, `updateEncryptedClipContent`, and `updateVaultStatus` now consistently maintain `content_hash` (recomputing SHA-256 over normalized plaintext on edits/unvaulting and clearing `content_hash = ''` when encrypted in the Vault).
4. **O(1) Indexed Clipboard Duplicate Lookup Without Full-History Scans (`ClipboardServiceHelper.kt` & `ClipDao.kt`):**
   - Uses indexed `findClipsByContentHash(contentHash)` with exact normalized content verification, and restricts any fallback strictly to unindexed legacy rows (`WHERE content_hash = '' AND is_vaulted = 0`) with lazy `updateClipContentHash` backfill—never loading the full clipboard history on a hash miss.
5. **Bengali Dictionary Lexicon Audit & Cleanup (`BengaliWordDictionary.kt`):**
   - Fixed malformed leading-space Bengali key `" অপেক্ষা"` → `"apekkha"` (alongside `"opekkha"`), non-ASCII key `"দ্ধ"` → `"boddho"`, and duplicate mixed-case key `"antoriK"` → `"antorikota"`, audited all 462 lexicon entries, and removed unverified Avro-equivalence claims.

## Build & Test Instructions
```bash
# Run all 31 JUnit 4 unit tests
./gradlew testDebugUnitTest

# Build debug APK (for testing — no production keystore required)
./gradlew assembleDebug

# Build signed production release APK & AAB (requires permanent production keystore env vars)
./gradlew assembleRelease bundleRelease
```

## Developer
- **Suvash Purkaystha** (AI Developer & Vibe Coder)
- **Repository:** https://github.com/suvashlal253/ClipVault
