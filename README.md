# MacroTrack

A privacy-first calorie & macro tracker (Cronometer-style) with a barcode
scanner, scaffolded from the `buzzr-p2p` build/architecture template.

## What carried over from buzzr-p2p, and what didn't

| buzzr-p2p                                   | MacroTrack                                         |
|----------------------------------------------|-----------------------------------------------------|
| AGP/Kotlin/KSP/Hilt version pins              | **Kept** — same toolchain                            |
| Room + KSP for local persistence              | **Kept** — `foods`, `diary_entries`, `user_goals`    |
| Hilt DI module pattern                        | **Kept** — `di/DatabaseModule.kt`, `di/NetworkModule.kt` |
| 16 KB ELF alignment task, packaging config     | **Dropped** — no native (.so) libs in this app       |
| I2P embedded router, SAM/gossip transport      | **Dropped** — no P2P networking need                 |
| Geohash, libphonenumber, device-ID-by-phone    | **Dropped** — no location or phone number needed     |
| Bugfender crash/analytics SDK                  | **Dropped** — zero telemetry, by design               |
| Ktor/WebSocket/Java-WebSocket                  | **Dropped** — replaced by a plain Retrofit client for **one** thing: Open Food Facts lookups |
| Legacy-view MainActivity (~500 lines)          | **Replaced** — thin Compose `MainActivity` + `MacroTrackNavHost` |
| — | **Added** — CameraX + ML Kit on-device barcode scanning |
| — | **Added** — Retrofit/Moshi client for `world.openfoodfacts.org` (free, keyless) |

## Privacy posture

- All diary data (what you ate, when, how much) lives **only** in the local
  Room database (`macrotrack.db`). Nothing syncs anywhere.
- The **only** network calls this app makes are barcode/name lookups against
  the free, open Open Food Facts database — no account, no API key, no
  tracking payload, just the barcode number or search text.
- No location permission, no contacts/phone permission, no analytics SDK.
- Manifest requests only `INTERNET` (for OFF lookups) and `CAMERA` (for the
  barcode scanner).

## Architecture

```
data/
  db/            Room entities + DAOs (FoodEntity is a rebuildable cache;
                 DiaryEntryEntity stores a frozen macro snapshot per logged
                 entry so later edits to a food don't retroactively change
                 old diary days — same behavior as Cronometer/MFP)
  remote/        Retrofit interface + models for Open Food Facts
  repository/    FoodRepository, DiaryRepository, GoalsRepository
di/              Hilt modules (Room, Retrofit/OkHttp/Moshi)
scanner/         BarcodeAnalyzer (CameraX ImageAnalysis.Analyzer -> ML Kit)
ui/
  diary/         Home screen: today's calories + macro bars + entries by meal
  search/        Text search (local cache instant, OFF results merged in)
  scan/          Camera preview + barcode detection -> lookup -> confirm
  addentry/      Quantity + meal picker -> writes a DiaryEntryEntity
  goals/         Edit daily calorie/protein/carbs/fat targets
  nav/           Bottom-nav Scaffold + NavHost wiring the above together
  theme/         Material3 theme (green palette, not Buzzr's alert-amber)
```

## Building

```bash
cd macrotrack
./gradlew assembleFdroidDebug     # or assemblePlaystoreDebug
```

No `local.properties` secrets are required for a debug build — the signing
block only kicks in if `RELEASE_STORE_FILE` is set, for release builds.

## What's next (not yet built)

- Weight/exercise tracking, water intake
- Custom recipes (combine multiple foods into one loggable item)
- Charts/trends screen (protein/calories over time) — `Icons.Filled.BarChart`
  is already wired to the Goals tab as a placeholder icon; a real trends
  screen would want its own tab once there's history to show
- Manual "create a food" flow (repository method `saveManualFood()` already
  exists; no screen calls it yet)
- CSV export of the diary (still 100% local — this would be an explicit,
  user-triggered export, not automatic sync)

## Gradle wrapper note

`gradlew` / `gradlew.bat` and `gradle/wrapper/gradle-wrapper.properties` are included, but
`gradle/wrapper/gradle-wrapper.jar` (a binary artifact) is **not**, since it wasn't part of the
original upload and this environment has no network path to `services.gradle.org` to fetch it.
Before building, generate it once with a local Gradle install:

```
gradle wrapper --gradle-version 8.11.1
```

or point Android Studio at the project and let it regenerate the wrapper on first sync.
