# MyBooks — Personal Book Library for Android

**Date:** 2026-10-30
**Status:** Approved design, pending spec review

## Purpose

A personal Android app for managing the user's own book collection. The user scans a
book's barcode to add it, rates it, keeps notes, and tracks each book as read,
owned-and-to-read, or wanted (to purchase). Single user, data private to the phone.

### Success criteria

- Scanning the ISBN barcode on a physical book adds it with title, author and cover
  filled in automatically (when online and the book is in Google Books or Open Library).
- Each book can be given a 1–5 star score and free-text notes.
- Books can be filtered by status: Read / To read / Wishlist (and All), and searched by
  title or author.
- The library can be exported to and restored from a JSON file.
- The app builds on the user's Windows machine without Android Studio or admin rights,
  and installs as an APK on the user's Android phone.

### Out of scope

Backend server, cloud sync, login, multiple users, statistics, iOS.

## Tooling

- Location: `C:\Users\mcosta\GIT\MyBooks` (standalone git repo).
- Kotlin, Jetpack Compose with Material 3, single Activity, Navigation Compose.
- minSdk 26 (Android 8.0), targetSdk/compileSdk = latest stable installed.
- Gradle wrapper; command-line build (`gradlew assembleDebug`).
- Android SDK command-line tools installed portably in `C:\Users\mcosta\devtools\android-sdk`
  (no admin install). The JDK is the machine's Java 25; Gradle and AGP versions are chosen
  to support it (or a portable JDK 21 is added to devtools if they do not).
- Delivery: debug APK installed via `adb install` (USB debugging) or copied to the phone.

## Architecture

Layered, each unit testable on its own:

| Unit | Responsibility | Depends on |
|------|----------------|------------|
| `data.db` (Room) | `BookEntity`, `BookDao`, `AppDatabase` | Room |
| `data.lookup` | `BookLookupService` interface; `GoogleBooksClient`, `OpenLibraryClient`; `FallbackLookupService` (Google first, then Open Library) | OkHttp, kotlinx.serialization |
| `data.backup` | `BackupSerializer` — books ⇄ JSON | kotlinx.serialization |
| `data.BookRepository` | Single entry point for the UI: observe/search/save/delete books, lookup by ISBN, duplicate detection, status rules | the three above |
| `ui.*` | Compose screens + one ViewModel per screen | repository |
| `scan` | Thin wrapper over Google code scanner returning an ISBN or cancel | play-services-code-scanner |

Dependencies are wired manually through a small `AppContainer` held by the `Application`
(no DI framework).

## Data model

`Book`:

| Field | Type | Notes |
|-------|------|-------|
| id | Long | auto PK |
| isbn | String? | unique when present; normalized to ISBN-13 digits |
| title | String | required, non-blank |
| authors | String | comma-separated display string, may be empty |
| publisher | String? | |
| publishedYear | Int? | |
| pageCount | Int? | |
| coverUrl | String? | https |
| description | String? | |
| status | enum `READ`, `TO_READ`, `WISHLIST` | exactly one |
| rating | Int? | 1..5, null = unrated |
| notes | String | may be empty |
| dateAdded | Long (epoch ms) | set on insert |
| dateRead | Long? | set automatically when status changes to READ and it is null; cleared when status leaves READ |

Rules:
- Rating outside 1..5 is rejected (set to null to clear).
- ISBN-10 input is converted to ISBN-13; only digits (and a final X for ISBN-10) are accepted.
- Saving a book whose ISBN already exists (different id) fails with a duplicate error.

## Barcode scanning and lookup

- Scanner: `GmsBarcodeScanning` (Google code scanner), formats EAN-13 and EAN-8/UPC-A
  allowed; no CAMERA permission needed. Requires Google Play services on the phone.
- Scanned value is validated as an ISBN (EAN-13 starting 978/979). Non-ISBN barcodes show
  an error message.
- Flow after a valid scan:
  1. If the ISBN already exists in the library → open that book's detail screen.
  2. Otherwise lookup: Google Books `https://www.googleapis.com/books/v1/volumes?q=isbn:{isbn}`,
     and if no result, Open Library `https://openlibrary.org/isbn/{isbn}.json` (authors resolved
     via `/authors/{key}.json`; cover `https://covers.openlibrary.org/b/isbn/{isbn}-L.jpg`).
  3. Found → "Add book" screen pre-filled, user chooses status (default TO_READ), saves.
  4. Not found / network error → same "Add book" screen with only the ISBN filled, plus a
     message; user enters details manually.
- Cover images loaded with Coil; `http://` cover URLs are rewritten to `https://`.

## Screens

1. **Library** (start): tabs All / Read / To read / Wishlist; search field filtering by title
   or author (case-insensitive); list rows show cover thumbnail, title, authors, stars.
   FAB opens a choice: Scan barcode / Enter manually. Overflow menu → Settings.
2. **Add / Edit book**: form for all metadata fields, status selector, rating, notes.
   Used for new books (from scan or manual) and editing existing ones. Validation: title
   required.
3. **Book detail**: cover, metadata, status selector (segmented buttons), tap-to-rate stars
   (tap the current rating again to clear), notes editor (saved on leaving the field/screen),
   date read, Edit and Delete (with confirmation) actions.
4. **Settings**: Export library (SAF "create document", `mybooks-backup-YYYY-MM-DD.json`) and
   Import library (SAF "open document"). Import merges: books with a matching ISBN are
   updated, others inserted; a summary (added / updated / failed) is shown.

## Backup format

```json
{ "version": 1, "exportedAt": 1761811200000, "books": [ { "isbn": "...", "title": "...", ... } ] }
```
All `Book` fields except `id`. Unknown fields ignored on import; unsupported `version`
rejected with an error.

## Error handling

- Network/lookup failure → manual entry fallback, never a crash.
- Scanner unavailable (no Play services) or cancelled → snackbar, stay on Library.
- Duplicate ISBN on save → inline error on the form.
- Invalid/corrupt import file → error dialog, no partial writes (import runs in one transaction).

## Testing

JVM unit tests (no device):
- ISBN normalization/validation (ISBN-10→13, check digits, rejects non-ISBN EAN).
- Google Books and Open Library JSON parsing from fixture files.
- `FallbackLookupService`: Google hit, Google miss → Open Library, both miss, network error.
- Repository rules: dateRead auto-set/clear, rating bounds, duplicate ISBN (using an
  in-memory fake DAO).
- Backup serializer round-trip and version rejection.

Room DAO test using Robolectric with an in-memory database (search and status filtering).

Manual verification on the phone: scanning a real book, cover display, export/import.
