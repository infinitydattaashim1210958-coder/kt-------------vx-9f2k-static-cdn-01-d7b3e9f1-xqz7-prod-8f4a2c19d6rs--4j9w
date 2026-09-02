# স্বাধ্যায় (Swadhyay) — Native Kotlin

Minimal Android Gradle project for the native migration.

## Database Gate (M3)

Verified production assets:

| Database            | Table   | Expected Count |
|---------------------|---------|----------------|
| core.db             | vedas   | 4              |
| core.db             | mantras | 20,380         |
| ramayana_core.db    | kandas  | 6              |
| ramayana_core.db    | shlokas | 17,802         |

## Build (GitHub Actions)

Push to `main` → Actions → **Android Build + Database Gate**.

Success criteria printed by CI:

```
BUILD SUCCESSFUL
DATABASE VERIFICATION PASSED
vedas=4
mantras=20380
kandas=6
shlokas=17802
APK BUILD PASSED
```

## Local structure

- `app/src/main/assets/databases/` — production SQLite files (do not modify)
- `app/src/main/java/com/kyronix/swadhyaa/data/local/` — Room entities, DAOs, databases
- `app/src/test/.../DatabaseVerificationTest.kt` — real count assertions (JVM)

## Phone-only workflow

1. Upload this entire folder to a GitHub repository.
2. Commit to `main`.
3. Open **Actions** tab and wait for green.
4. Download the APK artifact.
