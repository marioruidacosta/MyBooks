# MyBooks

A personal book library for Android. Scan a book's barcode to add it, give it a 1–5 star score,
keep notes, and track every book as **Read**, **To read** (you own it) or **Wishlist** (to buy).
All data stays on the phone; you can export/import it as a JSON backup from Settings.

## Features

- **Scan barcode** (Add button → Scan barcode): reads the ISBN on the back cover and fills in title,
  author, cover, publisher, year and pages from Google Books, falling back to Open Library.
  Scanning a book you already have opens it. Not found / offline → enter the details by hand.
- **Library** tabs: All / Read / To read / Wishlist, plus search by title or author.
- **Book page**: change status, tap stars to rate (tap the same star again to clear), write notes
  (saved automatically). Marking a book Read records the date.
- **Settings**: export the library to a file / import it back (merges by ISBN, never deletes).

Scanning uses Google's code scanner from Google Play services, so the phone needs Play services
(almost all Android phones have them). No camera permission is requested.

## Build

Requirements: JDK 17+ (built with Java 25) and the Android SDK (platform `android-37.0`).
Point the build at the SDK in `local.properties` (not committed):

```properties
sdk.dir=C\:\\Users\\<you>\\devtools\\android-sdk
```

### Google Books API key (recommended)

Without a key, Google Books requests share Google's anonymous daily quota, which is usually used up
(HTTP 429). The app then falls back to Open Library, which knows far fewer books. A free key gives
you your own quota (1,000 requests/day by default):

1. In [Google Cloud Console](https://console.cloud.google.com/) create a project (no billing needed).
2. *APIs & Services → Library* → enable **Books API**.
3. *APIs & Services → Credentials → Create credentials → API key*.
4. Optional but sensible: edit the key and under *API restrictions* allow only **Books API**.
   Leave *Application restrictions* at **None** — the app calls the API directly, so an
   "Android apps" restriction makes every request fail with "API key rejected".
5. Add it to `local.properties` and rebuild:

   ```properties
   GOOGLE_BOOKS_API_KEY=AIza...
   ```

The key is compiled into the APK, so don't share the APK publicly.

Then:

```sh
./gradlew testDebugUnitTest   # unit tests
./gradlew assembleDebug       # → app/build/outputs/apk/debug/app-debug.apk
```

## Install on your phone

**Option A — USB cable (adb)**

1. On the phone: Settings → About phone → tap *Build number* 7 times to enable Developer options.
2. Settings → System → Developer options → enable *USB debugging*.
3. Connect the phone, accept the "Allow USB debugging?" prompt, then run:

   ```sh
   <sdk>/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

**Option B — copy the APK**

Copy `app-debug.apk` to the phone (cable, Google Drive, email…), open it from the Files app and
allow *Install unknown apps* for that app when asked.

Reinstalling a newer build with `adb install -r` (or over the top) keeps your library.
Uninstalling the app deletes it — export a backup first.
