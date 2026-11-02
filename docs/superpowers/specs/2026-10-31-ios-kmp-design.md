# MyBooks on iPhone — Kotlin Multiplatform design

**Date:** 2026-10-31
**Status:** Approved design, pending spec review

## Purpose

Run MyBooks on the user's iPhone while keeping the Android app, from a single Kotlin codebase.
The user develops on Windows with no Mac and has (or will get) a paid Apple Developer Program
membership. iPhone builds are produced on GitHub's macOS runners and installed through TestFlight.

### Success criteria

- The iPhone app offers the same features as the Android app: library tabs (All / Reading / To read /
  Read / To buy) and search, book page (status, rating, notes, start date, progress), add/edit,
  barcode scan with BNP → Google Books → Open Library lookup, cover photo, `.zip` backup export/import.
- A `.zip` backup exported on Android imports on iPhone (books and cover photos), and vice versa.
- The Android app keeps working identically; Android data (Room schema v2) is untouched.
- Android builds and all common + Android tests run on the Windows machine
  (`./gradlew :shared:testDebugUnitTest :androidApp:testDebugUnitTest :androidApp:assembleDebug`).
- A manually triggered (or tag-triggered) GitHub Actions workflow builds, signs and uploads the iPhone
  app to TestFlight without any Mac owned by the user.

### Out of scope

Sync between devices, iPad-specific layouts, App Store public release, widgets, iCloud backup.

## Constraints

- No local Mac: nothing iOS-specific can be compiled or run on the dev machine. iOS code is
  compiled and tested only in CI (macOS runner). Keep iOS-only code small and behind interfaces.
- iOS deployment target **16.0** (VisionKit `DataScannerViewController`; user's iPhone is XS/XR+ on iOS 16+).
- Repo becomes **public** (unlimited Actions minutes). Secrets only in GitHub Actions secrets and the
  untracked `local.properties`. A history scan on 2026-10-31 found no keys committed.
- Bundle ID / applicationId stays `com.mcosta.mybooks`.

## Module layout

```
MyBooks/
  shared/        Kotlin Multiplatform library: androidTarget, iosArm64, iosSimulatorArm64
    commonMain   domain, data (Room KMP, lookups, backup, covers), ViewModels, Compose Multiplatform UI
    androidMain  Android implementations of platform services
    iosMain      iOS implementations (Kotlin calling UIKit/VisionKit/Foundation via interop)
    commonTest   tests runnable on JVM/Android here and on iOS in CI
    androidUnitTest  Robolectric tests that need Android (migration, photo shrinking)
  androidApp/    Android application: MainActivity, MyBooksApp, manifest, resources, FileProvider
  iosApp/        project.yml (XcodeGen), Swift entry point hosting the shared ComposeUIViewController,
                 Info.plist, asset catalog (app icon)
  .github/workflows/  android.yml (tests on push), ios.yml (build + TestFlight, manual/tag)
```

The shared module exposes an `iOS` framework named `Shared` (static) consumed by the Xcode project.

## Library replacements

| Current (Android-only) | Shared replacement |
|---|---|
| OkHttp + MockWebServer | Ktor client (OkHttp engine on Android, Darwin engine on iOS); `MockEngine` in tests |
| `javax.xml` DOM (BNP) | `io.github.pdvrieze.xmlutil:core` streaming reader |
| `java.time` | `kotlinx-datetime` |
| `java.text.Normalizer` (search key) | `expect fun foldAccents(s)`: Android `Normalizer`, iOS `NSString.decomposedStringWithCanonicalMapping` + combining-mark strip |
| `java.util.UUID` | `kotlin.uuid.Uuid` |
| `java.util.zip` (backups) | Common ZIP reader/writer (local headers + central directory, CRC-32 in common code); raw DEFLATE via `expect` codec: Android `java.util.zip.Deflater/Inflater`, iOS `platform.zlib` |
| `java.io.File` (cover store) | `okio` `FileSystem` + `Path` |
| Room (Android) | Room KMP + `BundledSQLiteDriver`; same entity, DAO and migration 1→2 |
| AndroidX ViewModel/Navigation/Lifecycle | their KMP artifacts (`org.jetbrains.androidx.*`) |
| Coil 3 + OkHttp network | Coil 3 + Ktor network (`coil-network-ktor3`) |
| `BuildConfig.GOOGLE_BOOKS_API_KEY` | `expect val googleBooksApiKey`: Android BuildConfig; iOS `Info.plist` key `GoogleBooksApiKey` filled from an xcconfig generated in CI from a secret |

Library versions are chosen in the plan (latest stable compatible with Kotlin 2.4 / Compose Multiplatform).

## Platform services (interfaces in commonMain)

```kotlin
interface IsbnScanner { suspend fun scan(): ScanOutcome }                  // Android: GmsBarcodeScanning; iOS: DataScannerViewController (EAN-13/EAN-8/UPC-E)
interface CoverCamera { suspend fun takePhoto(): ByteArray? }             // raw photo bytes or null if cancelled; Android: TakePicture+FileProvider; iOS: UIImagePickerController(.camera)
interface PhotoShrinker { fun shrink(photo: ByteArray, maxSide: Int = 1000): ByteArray } // Android: BitmapFactory+EXIF; iOS: UIImage (orientation-aware) + UIGraphicsImageRenderer → JPEG 0.85
interface DocumentPicker {                                                  // Android: SAF CreateDocument/OpenDocument; iOS: UIDocumentPickerViewController (export via temp file + forExporting)
    suspend fun save(suggestedName: String, mime: String, bytes: ByteArray): Boolean
    suspend fun open(mimeTypes: List<String>): ByteArray?
}
```

`ScanOutcome` keeps its current meaning (Isbn / NotAnIsbn / Cancelled / Failed). Implementations are
provided to the shared UI through a `PlatformServices` holder created by each app shell, alongside the
`AppContainer` (which moves to commonMain and receives platform pieces: database builder, HTTP engine,
cover directory, API key).

Backups switch from streaming to `ByteArray` in common code (libraries are small: hundreds of books plus
~150 KB per photo).

## UI

The existing Compose screens move to commonMain largely unchanged. Android-only APIs used today are
replaced: `LocalContext`/`Toast` → snackbar messages; `rememberLauncherForActivityResult` → platform
services; `BackHandler`/system back → navigation's multiplatform back handling; `LifecycleEventEffect`
stays (KMP lifecycle). iOS gets standard insets (safe area) via Compose's window insets.

## iOS build pipeline (`.github/workflows/ios.yml`)

Triggers: `workflow_dispatch` and tags `v*`. Runner: latest `macos` image.

1. Checkout; set up JDK; cache Gradle and Kotlin/Native.
2. `./gradlew :shared:iosSimulatorArm64Test` (iOS tests) then build the framework via Xcode build phase.
3. Generate `iosApp/Secrets.xcconfig` from secret `GOOGLE_BOOKS_API_KEY`.
4. `brew install xcodegen` → `xcodegen generate` in `iosApp/`.
5. `xcodebuild archive` with automatic signing using the App Store Connect API key
   (`-allowProvisioningUpdates -authenticationKeyPath … -authenticationKeyID … -authenticationKeyIssuerID …`),
   build number = `GITHUB_RUN_NUMBER`.
6. `xcodebuild -exportArchive` with `method = app-store-connect`, `destination = upload` → TestFlight.

Secrets: `ASC_KEY_ID`, `ASC_ISSUER_ID`, `ASC_KEY_P8` (base64 of the .p8), `APPLE_TEAM_ID`,
`GOOGLE_BOOKS_API_KEY`.

`.github/workflows/android.yml`: on push/PR, ubuntu runner, `./gradlew testDebugUnitTest :androidApp:assembleDebug`.

The user's one-time Apple setup (enrol, create app record `com.mcosta.mybooks`, create API key with
App Manager role, add secrets, install TestFlight) is documented in `docs/ios-setup.md`.

## Data

iPhone and Android keep separate local databases. Moving the library: export `.zip` on Android, open it
on iPhone via Settings → Import (Files app / AirDrop / iCloud Drive). iOS starts at schema v2 directly.

## Testing

- **commonTest** (run here on the JVM/Android host and in CI on iOS): ISBN, search key, lookup clients
  (Ktor `MockEngine` with the existing fixtures), lookup chain, repository rules (fake DAO), backup
  serializer, ZIP reader/writer round-trip (including reading a `java.util.zip`-made archive fixture
  with DEFLATE entries), ViewModels.
- **androidUnitTest** (Robolectric, here): Room DAO, migration 1→2, photo shrinker, manifest checks.
- **iosTest** (CI only): ZIP DEFLATE codec via zlib, accent folding, Room DAO on the bundled driver.
- **Manual on iPhone** (via TestFlight): scanner, camera, document picker, import of an Android backup.

## Stages (each delivered and verified before the next)

1. **Shared module, Android unchanged.** Restructure into `shared/` + `androidApp/`, swap libraries per
   the table, move tests. Gate: all tests pass here; the Android APK behaves as before and upgrades over
   the installed app with data intact (same applicationId, same database).
2. **iPhone app core + pipeline.** `iosApp` shell, iOS actuals for everything except scanner/camera/
   document picker (those show "coming soon" messages), CI workflows, setup doc. Gate: TestFlight build
   installs and runs; library, book page, Reading, manual add, ISBN lookup work on the iPhone.
3. **iPhone platform features.** VisionKit scanner, camera cover photo, document picker export/import.
   Gate: scanning, cover photo and Android→iPhone backup import verified on the iPhone.
