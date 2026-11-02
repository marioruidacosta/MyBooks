# New in Portugal Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A nightly job turns the National Library's new legal-deposit records into monthly JSON lists by genre, published on GitHub Pages. A new app section browses them: month, then genre, then book, with Add to my books and Where to buy.

**Architecture:**
- **Job:** a new JVM Gradle module `releases/` holds the harvester. It parses OAI-PMH pages, classifies records into books by genre and keeps its state in a data directory, all pure and unit-tested on real fixtures. It is run by `.github/workflows/releases.yml`, which commits to `releases-data` and deploys Pages.
- **App:** the shared module gets `data/releases` (a client and a file-cached repository) and `ui/releases` (three screens, three ViewModels).

**Tech Stack:** Kotlin JVM (`java.net.http`, StAX, kotlinx.serialization), GitHub Actions + Pages; app: Ktor, okio, Compose Multiplatform, existing `LibraryShelf`/`BookCover`/`LookupChain`.

**Spec:** `docs/superpowers/specs/2026-11-02-new-in-portugal-design.md`

## Global Constraints

- **Branch** `feature/new-releases`. The local gate is `bash .superpowers/sdd/2026-11-02-new-in-portugal/ut.sh`, a copy of the social-part-2 gate with `:releases:test` added. It must print `SUMMARY: exit=0 … failed=0`.
- **OAI:**
  - base `https://oai.bnportugal.gov.pt/servlet/OAIHandler`, set `bibliografianacional`, prefix `marcxchange`
  - 2 s between requests
  - User-Agent `MyBooks-releases/1.0 (+https://github.com/marioruidacosta/MyBooks)`
- **Data:** at most 7 months. Pages base URL `https://marioruidacosta.github.io/MyBooks/releases/`; files `index.json`, `<YYYY-MM>.json`, `state.json`, `seen.txt`.
- **Genre keys, labels and order:** exactly as the spec's genre table:
  - literature keys first: `crime`, `fantasy`, `scifi`, `historical`, `adventure`, `novel`, `stories`, `poetry`, `theatre`, `essays`, `biography`, `humour`, `literature`, `language`
  - then `children`
  - then `general`, `philosophy`, `religion`, `social`, `science`, `applied`, `arts`, `history`
  - then `other`, `unclassified`
- **Copy, verbatim:**
  - screen texts: "New in Portugal", "(so far)", "Books added to Portugal's National Bibliography (legal deposit) that month.", "Couldn't load the new releases", "Retry", "New releases aren't available yet.", "Showing saved list from {d MMM, HH:mm}"
  - buttons and labels: "Where to buy", "View at the National Library", "Translated by …", "Already in your library"
- **Store URLs, in this order:**
  - Bertrand `https://www.bertrand.pt/pesquisa/<isbn>`
  - Almedina `https://www.almedina.net/catalogsearch/result/?q=<isbn>`
  - Wook `https://www.wook.pt/pesquisa?keyword=<isbn>`
  - Fnac `https://www.fnac.pt/SearchResult/ResultList.aspx?Search=<isbn>`
- **No prices; no scraping of shops.** No `java.*`/`android.*` in commonMain. Commit messages end with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Review Focus

1. **A day or window with no records:** OAI answers `<error code="noRecordsMatch">`. That is an empty page, not a failed run. Pinned by Task 3 `noRecordsMatchIsEmpty`.
2. **A malformed or incomplete record** (no `210`, no `200$a`, odd `675`) is skipped and the run continues. Pinned by Task 1 `incompleteRecordIsSkipped`.
3. **A run on the 1st of a month** harvests the previous day: books land in the previous month's file, and that month becomes `complete` once the new month starts. Pinned by Task 3 `monthBoundaryAssignsByDatestamp`.
4. **Offline with the index cached but a month never downloaded:** the picker still works, and that month shows "Couldn't load the new releases" with Retry. Pinned by Task 6 `uncachedMonthOfflineShowsError`.
5. **A book with no 7XX authors and no translators:** authors come from `200$f`, and the screen shows no "Translated by". Pinned by Task 1 `authorsFallBackToStatement` and Task 6 `bookWithoutTranslatorsHasNone`.

---

### Task 1: Job module, record parsing and book extraction

**Files:**
- Create:
  - `releases/build.gradle.kts`: plugins `kotlin-jvm` and `kotlin-serialization`, `application` with `mainClass = "com.mcosta.mybooks.releases.MainKt"`; deps `kotlinx-serialization-json`, test `kotlin("test")` + `junit`
  - `releases/src/main/kotlin/com/mcosta/mybooks/releases/OaiParser.kt`
  - `releases/src/main/kotlin/com/mcosta/mybooks/releases/Isbn13.kt`
  - `releases/src/main/kotlin/com/mcosta/mybooks/releases/BookExtractor.kt`
  - `releases/src/main/kotlin/com/mcosta/mybooks/releases/Model.kt`
  - fixtures `releases/src/test/resources/oai/october.xml` (about 40 trimmed records) and `oai/no-records.xml`
- Modify: `settings.gradle.kts` (`include(":releases")`), `gradle/libs.versions.toml` (plugin `kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }`).
- Test: `releases/src/test/kotlin/com/mcosta/mybooks/releases/BookExtractorTest.kt`, `OaiParserTest.kt`, `Isbn13Test.kt`.

**Interfaces, Produces:**
```kotlin
data class DataField(val tag: String, val subfields: List<Pair<Char, String>>) { fun all(code: Char): List<String>; fun first(code: Char): String? }
data class MarcRecord(val id: String, val datestamp: String /* YYYY-MM-DD */, val leader: String,
    val controls: Map<String, String>, val fields: List<DataField>) { fun fields(tag: String): List<DataField> }
data class OaiPage(val records: List<MarcRecord>, val resumptionToken: String?)
object OaiParser { fun parse(xml: InputStream): OaiPage }   // noRecordsMatch → OaiPage(emptyList(), null); other <error> → OaiException; deleted headers skipped
class OaiException(message: String) : Exception(message)
fun isbn13(raw: String): String?                              // spec's normalisation; null if invalid
@Serializable data class ReleaseBook(val id: String, val isbn: String, val title: String, val authors: List<String>,
    val translators: List<String>, val publisher: String?, val place: String?, val year: Int, val genre: String, val link: String?)
object BookExtractor { fun extract(record: MarcRecord): ReleaseBook? }   // applies every "new book" rule except seen.txt; month = datestamp's YYYY-MM; genre via GenreClassifier (Task 2, stub returning "unclassified" here)
```

**Fixture:** build `october.xml` from the October 2026 harvest in the session scratchpad (`…/scratchpad/bnp/oct_*.xml`). Keep the OAI envelope, and pick records covering:
- each genre rule of Task 2
- the edited 2003 record `1154749`
- a record without `010`
- a periodical (`as`)
- a record without `675`
- a record whose 7XX lacks `$4`
- a translator (`$4` = `730`)

`no-records.xml` is the OAI `noRecordsMatch` error response.

- [ ] **Step 1:** Write the tests:
  - `Isbn13Test`: `"978-989-568-279-9" → "9789895682799"`, `"972-772-397-7" → "9789727723973"`, a bad check digit gives null, `"PT"` gives null
  - `OaiParserTest`:
    - `parsesRecordsAndToken`: the fixture's count, the first record's id, datestamp and leader, and a non-null token
    - `noRecordsMatchIsEmpty`
    - `otherErrorThrows` (`badArgument`)
  - `BookExtractorTest`:
    - `extractsFields`: for "A casa de cal": isbn `9789895682799`, title, authors `[Thomas Bernhard]`, translators `[Rui Miguel Mesquita]`, publisher `Documenta`, place `Lisboa`, year 2026, link from `003`
    - `titleIncludesSubtitle`
    - `authorsFallBackToStatement` (no 7XX author → `200$f`)
    - `skipsEditedOldRecord` (`1154749`, year 2003 against a 2026 datestamp)
    - `skipsWithoutIsbn`, `skipsPeriodical`
    - `incompleteRecordIsSkipped` (no `210`)
    - `yearBeforeIsAccepted` (2025 for a 2026 month)
- [ ] **Step 2:** Run `./gradlew :releases:test`. Expected: FAIL (unresolved).
- [ ] **Step 3:** Implement with StAX (`XMLInputFactory`, namespaces aware). Authors are formatted "`$b $a`" with `$b` optional.
- [ ] **Step 4:** Run `./gradlew :releases:test`. Expected: PASS. Commit `feat(releases): OAI parsing and book extraction`.

### Task 2: Genre classifier

**Files:** Create `releases/src/main/kotlin/com/mcosta/mybooks/releases/GenreClassifier.kt`. Modify `BookExtractor.kt` to call it. Test `GenreClassifierTest.kt`.

**Interfaces, Produces:** `object GenreClassifier { fun classify(udc: List<String>): String }` returning a key from Global Constraints.

- [ ] **Step 1:** Write `GenreClassifierTest`, with one assertion per row of the spec's genre table, using real codes:
  - **Literature forms:**
    - `821.134.3-312.4"20"` → crime
    - `821.111(73)-312.9"20"` → fantasy
    - `821.111(73)-311.9"19/20"` → scifi
    - `821.134.3-311.6"20"` → historical
    - `-311.3` and `-311.8` → adventure
    - `821.133.1-31"20"` → novel
    - `-32` and `-34` → stories
    - `821.134.3(81)-1"19/20"` → poetry
    - `821.134.3-2"20"` → theatre
    - `-4` → essays
    - `821.134.2(82)-94"20"` → biography
    - `-7` → humour
    - `821.134.3-92"20"`, `821.134.3Camões, Luís de.09` → literature
    - `811.134.3'36(035)` → language
  - **Children:**
    - `087.5 ; 159.942(0:053.4)` → children
    - `821.111-93"19/20"` → children
    - `childrenWinsOverNovel`: `821.111(73)-93-31` → children
  - **Non-fiction:** `0…` general, `1…` philosophy, `2…` religion, `3…` social, `5…` science, `6…` applied, `7…` arts, `9…` history
  - **Edge cases:** `4…` → other; empty list → unclassified; `usesFirst82Code` (a non-literature first code followed by an `82…` code)
- [ ] **Step 2:** Run. Expected: FAIL. Implement it. Add `genreOfFixtureBooks` to `BookExtractorTest` (three fixture books with known genres). Run. Expected: PASS. Commit `feat(releases): genre classification`.

### Task 3: Harvest run, data store and CLI

**Files:** Create `OaiSource.kt` (`interface OaiSource`, `class HttpOaiSource`), `DataStore.kt`, `ReleasesJob.kt` and `Main.kt` under `releases/src/main/kotlin/...`. Test `ReleasesJobTest.kt`, `DataStoreTest.kt`, with a `FakeOaiSource` in test.

**Interfaces, Produces:**
```kotlin
interface OaiSource { fun page(from: LocalDate, until: LocalDate, resumptionToken: String?): OaiPage }
class HttpOaiSource(baseUrl: String = OAI_BASE, userAgent: String = USER_AGENT, delayMillis: Long = 2000) : OaiSource
@Serializable data class MonthFile(val version: Int = 1, val month: String, val updated: String, val books: List<ReleaseBook>)
@Serializable data class IndexMonth(val month: String, val complete: Boolean, val total: Int, val genres: Map<String, Int>)
@Serializable data class IndexFile(val version: Int = 1, val updated: String, val months: List<IndexMonth>)
@Serializable data class JobState(val harvestedUntil: String)
class DataStore(dir: Path) { fun state(): JobState?; fun seen(): Set<String>; fun month(m: String): MonthFile?; fun months(): List<String>
    fun write(state: JobState, seen: Set<String>, months: Map<String, MonthFile>, index: IndexFile, remove: List<String>) }  // all files under dir/releases/, except state.json and seen.txt at dir root
class ReleasesJob(source: OaiSource, store: DataStore, today: LocalDate, now: Instant) { fun run() }
fun main(args: Array<String>)   // --data-dir <path> [--today YYYY-MM-DD]
```

The run logic is the spec's "Each run" and "First run":
- Collect everything in memory, then call `DataStore.write` once at the end, so an exception leaves the files untouched.
- On the first run, harvest one calendar month per series of pages, oldest first.

- [ ] **Step 1:** Write the tests, using a `FakeOaiSource` serving fixture pages by window:
  - `firstRunBackfillsSixMonthsPlusCurrent` (with `today=2026-11-03`: windows start `2026-05-01`, the last ends `2026-11-02`, and 7 months are written)
  - `nightlyRunResumesFromHarvestedUntilMinusOne`
  - `noDuplicatesAcrossRuns` (the same record twice is listed once)
  - `monthBoundaryAssignsByDatestamp`
  - `currentMonthIncompletePastComplete`
  - `indexCountsMatchMonthFiles`
  - `errorWritesNothing` (the source throws on page 2; the store's files are unchanged)
  - `oldMonthsRemoved` (an 8th month file is deleted)
  - `noRecordsMatchIsEmpty` (an empty window still advances `harvestedUntil`)
  - `DataStoreTest.roundTrip`
- [ ] **Step 2:** Run. Expected: FAIL. Implement it. `HttpOaiSource` uses `java.net.http.HttpClient` with a 60 s timeout, sleeps `delayMillis` before every request after the first, and treats non-200 as `IOException`.
- [ ] **Step 3:** Run. Expected: PASS. Write the job's test output samples `index.json` and `2026-10.json` (from `indexCountsMatchMonthFiles`) to `releases/build/samples/`. They feed Task 5. Commit `feat(releases): harvest run and data store`.

### Task 4: Workflow and Pages publishing

**Files:** Create `.github/workflows/releases.yml` and `docs/releases-setup.md` (the spec's "User setup" steps).

- [ ] **Step 1:** Write `releases.yml` as the spec describes:
  - triggers: `schedule` 03:00 UTC, `workflow_dispatch`, and `push` on paths `releases/**` and the workflow file
  - `jobs.test`: run `./gradlew :releases:test` via `annotate.sh`, Java 21
  - `jobs.publish` (needs test; only on `schedule`/`workflow_dispatch`):
    - check out `releases-data` into `data/`, or create an orphan branch with an empty commit when it's missing
    - `./gradlew :releases:run --args="--data-dir $GITHUB_WORKSPACE/data"`
    - commit and push if changed (bot identity `github-actions[bot]`)
    - `actions/configure-pages@v5`, `actions/upload-pages-artifact@v3` (path `data`), `actions/deploy-pages@v4`, with environment `github-pages`
  - permissions: `contents: write`, `pages: write`, `id-token: write`
- [ ] **Step 2:** Push. Expected: the Releases job `test` passes (badge `releases.yml`). Publishing is verified after the user's Pages setup (Task 7). Commit `ci(releases): nightly job and Pages publishing`.

### Task 5: App data — models, client, cached repository

**Files:**
- Create in `shared/src/commonMain/kotlin/com/mcosta/mybooks/data/releases/`: `ReleaseModels.kt`, `ReleasesClient.kt`, `ReleasesRepository.kt`, `StoreLinks.kt`.
- Create fixtures `shared/src/commonTest/fixtures/releases_index.json` and `releases_2026-10.json` (copied from Task 3's samples).
- Modify `AppContainer.kt`: new constructor parameters `fileSystem: FileSystem` and `releasesDir: Path`, and `val releases = ReleasesRepository(ReleasesClient(http), fileSystem, releasesDir, { Clock.System.now() })`. `AndroidAppContainer` passes `FileSystem.SYSTEM` and `context.filesDir.toOkioPath() / "releases"`; iOS `MainViewController` passes `FileSystem.SYSTEM` and its documents directory `/ "releases"`.
- Test `commonTest/.../data/releases/ReleasesRepositoryTest.kt`, `ReleasesClientTest.kt`, `StoreLinksTest.kt`.

**Interfaces, Produces:**
```kotlin
enum class ReleaseGenre(val key: String, val label: String) { CRIME("crime","Crime & mystery"), /* … spec order … */ UNCLASSIFIED("unclassified","Not yet classified"), UNKNOWN("", "Other");
    companion object { fun of(key: String): ReleaseGenre } }
data class ReleaseMonth(val month: String, val complete: Boolean, val total: Int, val genres: List<Pair<ReleaseGenre, Int>>)  // genres in enum order, counts > 0
@Serializable data class ReleaseBook(...)                               // same fields as the job's ReleaseBook
data class Cached<T>(val value: T, val savedAt: Instant, val offline: Boolean)   // offline = network failed, saved copy shown
class ReleasesUnavailable(val notPublished: Boolean, cause: Throwable? = null) : Exception(cause)
class ReleasesClient(http: HttpClient, baseUrl: String = RELEASES_BASE_URL) { suspend fun index(): String; suspend fun month(month: String): String }  // raw JSON; 404 → ReleasesUnavailable(notPublished = true)
class ReleasesRepository(client: ReleasesClient, fs: FileSystem, dir: Path, clock: () -> Instant) {
    suspend fun months(): Cached<List<ReleaseMonth>>
    suspend fun books(month: String): Cached<List<ReleaseBook>>              // sorted A–Z by searchKey(title); "complete" (refresh rule) read from the cached index
    suspend fun book(month: String, id: String): ReleaseBook? }             // from the cached file
data class StoreLink(val name: String, val url: String)
fun storeLinks(isbn: String): List<StoreLink>                              // Global Constraints order and URLs
fun monthLabel(month: String, complete: Boolean): String                    // "November 2026 (so far)", "October 2026"
```

- [ ] **Step 1:** Write the tests, using MockEngine, a fake okio `FileSystem` (`FakeFileSystem` from `okio-fakefilesystem`; add it to `commonTest` deps) and a settable clock:
  - `parsesFixtureIndexInGenreOrder`
  - `unknownGenreKeyIsOther`
  - `freshCacheSkipsNetwork`: index younger than 6 h; a past month younger than 24 h
  - `staleCacheRefetches`
  - `offlineReturnsSavedCopyFlagged`
  - `offlineWithoutCopyThrows` (`notPublished = false`)
  - `notFoundIsNotPublished`
  - `booksSortedIgnoringAccents` (`"Árvore"` before `"Barco"`)
  - `StoreLinksTest.urlsForIsbn` (all four, exact strings)
  - `monthLabelSoFar`
- [ ] **Step 2:** Run the gate. Expected: FAIL. Implement it. Run the gate. Expected: PASS. Commit `feat(releases): app data layer`.

### Task 6: ViewModels

**Files:**
- Create `shared/src/commonMain/kotlin/com/mcosta/mybooks/ui/releases/`: `ReleasesViewModel.kt`, `ReleaseGenreViewModel.kt`, `ReleaseBookViewModel.kt`.
- Modify `ui/social/FeedActions.kt`: `LibraryShelf` gains `suspend fun contains(isbn: String?, title: String, authors: String): Boolean` and `suspend fun add(book: Book): Added`. The `FeedBook` overloads delegate to them.
- Test `commonTest/.../ui/releases/ReleasesViewModelsTest.kt`.

**Interfaces, Produces:**
```kotlin
data class ReleasesState(val months: List<ReleaseMonth> = emptyList(), val selected: ReleaseMonth? = null,
    val loading: Boolean = false, val error: String? = null, val savedAt: Instant? = null, val notPublished: Boolean = false)
class ReleasesViewModel(repo: ReleasesRepository) : ViewModel() { val state: StateFlow<ReleasesState>; fun select(month: String); fun retry() }
data class GenreBooksState(val books: List<ReleaseBook> = emptyList(), val loading: Boolean = false, val error: String? = null, val savedAt: Instant? = null)
class ReleaseGenreViewModel(repo: ReleasesRepository, month: String, genre: ReleaseGenre) : ViewModel() { val state: StateFlow<GenreBooksState>; fun retry() }
data class ReleaseBookState(val book: ReleaseBook? = null, val coverUrl: String? = null, val description: String? = null,
    val inLibrary: Boolean = false, val message: String? = null, val stores: List<StoreLink> = emptyList())
class ReleaseBookViewModel(releases: ReleasesRepository, library: BookRepository, month: String, bookId: String) : ViewModel() {
    val state: StateFlow<ReleaseBookState>; fun addToLibrary(status: ReadingStatus); fun dismissMessage() }
```

- **Errors:** `error` = "Couldn't load the new releases"; `notPublished` makes the screen show "New releases aren't available yet."
- **Cover:** `coverUrl` starts as `https://covers.openlibrary.org/b/isbn/<isbn>-M.jpg?default=false` and is replaced by the lookup's `coverUrl` when the lookup finds one.
- **Adding a book:** the saved book is `Book(isbn, title, authors.joinToString(", "), publisher, publishedYear = year, coverUrl = lookup cover, status)`.

- [ ] **Step 1:** Write the tests (`MainDispatcherTest`, fake repository data from the Task 5 fixtures):
  - `loadsMonthsAndSelectsNewest`
  - `selectingMonthShowsItsGenres`
  - `offlineShowsSavedNote`
  - `notPublishedState`
  - `uncachedMonthOfflineShowsError`
  - `genreListsOnlyThatGenreSorted`
  - `bookShowsStoresAndLookupDescription`
  - `bookWithoutTranslatorsHasNone`
  - `addToLibrarySavesToBuy` (message "Added to To buy", `inLibrary` true)
  - `alreadyInLibraryDetectedByIsbn`
- [ ] **Step 2:** Run the gate. Expected: FAIL. Implement it. Run the gate. Expected: PASS. Commit `feat(releases): view models`.

### Task 7: Screens, navigation, docs

**Files:**
- Create `ui/releases/ReleasesScreen.kt`, `ReleaseGenreScreen.kt`, `ReleaseBookScreen.kt`.
- Modify:
  - `ui/MyBooksNavHost.kt`: routes `releases`, `releases/{month}/{genre}`, `releases/{month}/book/{id}`
  - `ui/library/LibraryScreen.kt`: `onOpenReleases: () -> Unit`, icon `Icons.Filled.NewReleases` "New in Portugal", first
  - `README.md` (one feature bullet), `docs/releases-setup.md`
- Test: the gate (compile and VM tests), then the phone.

- [ ] **Step 1:** Implement the three screens with the spec's layout and the copy from Global Constraints:
  - month chips via `LazyRow`/`FilterChip`
  - genre rows "{label} · {count}"
  - book rows with `BookCover`
  - Where to buy via `LocalUriHandler.current.openUri`, and View at the National Library likewise
  - the "Showing saved list from …" note formatted `d MMM, HH:mm` in the device zone, using the existing date helpers
- [ ] **Step 2:** Run the gate. Expected: PASS. Push. Expected: the Releases workflow `test` is green and Android CI is green on the PR.
- [ ] **Step 3:** Handoff:
  1. The user enables Pages (Source: GitHub Actions) and runs **Releases job** by hand.
  2. Expected: `https://marioruidacosta.github.io/MyBooks/releases/index.json` returns 7 months.
  3. Install on the phone: New in Portugal shows the months, genres and books, and "Where to buy" opens each shop.
- [ ] **Step 4:** Commit `feat(releases): New in Portugal screens`.
