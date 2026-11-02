# MyBooks: New in Portugal (monthly new releases by genre)

**Date:** 2026-11-02
**Status:** Approved design, pending spec review
**Branch:** `feature/new-releases` (from `main`)

## Purpose

Help the user discover books recently published in Portugal. A new section lists each month's new books
grouped by genre, for the current month and the previous six. From any book the user can add it to their
library or open it in four online bookshops.

### Decisions taken with the user

- **Source:** the Biblioteca Nacional de Portugal's National Bibliography (legal deposit), harvested through
  its OAI-PMH service. A "month" is the month a book **first appeared in the National Bibliography**. This
  lags the shops by about one to three months, because the Library records only the publication year.
- **Genres:** literature split into the Library's genre codes, children's and young adult as its own group,
  and non-fiction grouped by subject area (all books are included).
- **Architecture:** a nightly GitHub Actions job builds one JSON file per month and publishes it to GitHub
  Pages. The app downloads and caches these files. No community account is needed.
- **Where to buy:** Bertrand, Almedina, Wook and Fnac, as links that open each shop's search for the ISBN.
  **No prices** (no official source; scraping was rejected).
- **History:** the current month ("so far") plus the previous six.

### Success criteria

1. Opening **New in Portugal** shows the month picker (current month plus 6) and the chosen month's genres,
   with book counts.
2. A genre opens its books A–Z. A book opens a screen with details, a lazily loaded cover and description,
   **Add to my books** (To read / To buy) and **Where to buy** (four shops).
3. The nightly job adds the day's new books to the current month, without duplicates and without old
   records that were only edited. A failed night loses nothing and the next run catches up.
4. Months already downloaded are readable offline.
5. The National Library is asked for at most about 15 pages a night in total, not per user.

### Out of scope

Prices and stock; notifications; search across months; months older than six; shops other than the four;
translating the UI to Portuguese.

## Data rules

### Source

- OAI-PMH base: `https://oai.bnportugal.gov.pt/servlet/OAIHandler`
- Set `bibliografianacional`, metadataPrefix `marcxchange` (UNIMARC in MarcXchange XML)
- `ListRecords` with `from` / `until` (dates `YYYY-MM-DD`), followed by `resumptionToken` paging
  (about 300 records per page). Pages are requested one at a time, 2 s apart, with the User-Agent
  `MyBooks-releases/1.0 (+https://github.com/marioruidacosta/MyBooks)`.

### Which records are new books

A record is listed when **all** of the following hold:

- leader positions 6–7 are `am` (printed monograph)
- it has an ISBN in `010$a`, normalised to 13 digits (hyphens and spaces removed; ISBN-10 converted to
  ISBN-13 with a recomputed check digit; an invalid check digit is skipped). The job has its own
  normaliser, tested to agree with the app's `Isbn.normalize` on the fixture ISBNs.
- its publication year (first 4-digit number in `210$d`) is the month's year or the year before
- its record id (`001`) is not in `seen.txt` (books already listed in any month)

Its **month** is the `YYYY-MM` of the OAI `<datestamp>` at which the job first sees it. During the
first-run backfill a datestamp is the record's latest change, so a book edited after it first appeared is
listed in the later month; the nightly runs see books as they appear.

### Fields kept per book

| JSON | Source |
|------|--------|
| `id` | `001` |
| `isbn` | `010$a`, normalised |
| `title` | `200$a`, plus `: ` + `200$e` when present |
| `authors` | `700`/`701` (`$b $a`, i.e. "Forename Surname") whose `$4` is `070` or missing; otherwise `200$f` |
| `translators` | `700`/`701`/`702` with `$4` = `730` ("Forename Surname") |
| `publisher` | first `210$c` |
| `place` | first `210$a` |
| `year` | the publication year above |
| `genre` | the genre key below |
| `link` | `003` (e.g. `http://id.bnportugal.gov.pt/bib/catbnp/2293200`) |

### Genres

`udc` = every `675$a` of the record, in order. The **first rule that matches** decides the genre:

1. `children`: any code starts with `087.5`, or any `82…` code contains `-93`
2. Literature: the first code starting with `82` decides. Within it, the first literary form (`-` followed by
   digits and dots) is matched against the most specific entry here:

   | key | Label | Forms |
   |-----|-------|-------|
   | `crime` | Crime & mystery | `-312.4` |
   | `fantasy` | Fantasy | `-312.9` |
   | `scifi` | Science fiction | `-311.9` |
   | `historical` | Historical novel | `-311.6` |
   | `adventure` | Adventure & travel novels | `-311.3`, `-311.8` |
   | `novel` | Novel | any other `-31…` |
   | `stories` | Short stories & tales | `-32…`, `-34…` |
   | `poetry` | Poetry | `-1…` |
   | `theatre` | Theatre | `-2…` |
   | `essays` | Essays | `-4…` |
   | `biography` | Biography, memoirs & chronicles | `-94…` |
   | `humour` | Humour & satire | `-7…` |
   | `literature` | Other literature | `82…` with any other form, or none |
   | `language` | Language & linguistics | `81…` |

3. Non-fiction, by the first character of the first code:
   - `0` → `general`: General, information & computing
   - `1` → `philosophy`: Philosophy & psychology
   - `2` → `religion`: Religion
   - `3` → `social`: Social sciences, law & economics
   - `5` → `science`: Science & mathematics
   - `6` → `applied`: Applied sciences, health & cooking
   - `7` → `arts`: Arts, music & sport
   - `9` → `history`: History, geography & biography
4. `other`: Other (anything else)
5. `unclassified`: Not yet classified (no `675`)

The app shows genres in this order: literature keys in table order, then `children`, then non-fiction in
digit order, then `other` and `unclassified`.

## Published files

Kept on the `releases-data` branch (the job's history and state) and deployed to GitHub Pages by the
workflow (Pages source: **GitHub Actions**), served at `https://marioruidacosta.github.io/MyBooks/releases/`.

`releases/index.json`:

```json
{
  "version": 1,
  "updated": "2026-11-03T03:04:11Z",
  "months": [
    { "month": "2026-11", "complete": false, "total": 120, "genres": { "novel": 31, "children": 25 } },
    { "month": "2026-10", "complete": true, "total": 1161, "genres": { "novel": 165 } }
  ]
}
```

`months` lists the newest month first, at most 7 entries. `complete` is false only for the current month.

`releases/2026-10.json`:

```json
{
  "version": 1,
  "month": "2026-10",
  "updated": "2026-11-01T03:02:40Z",
  "books": [
    {
      "id": "2293200", "isbn": "9789895682799", "title": "A casa de cal",
      "authors": ["Thomas Bernhard"], "translators": ["Rui Miguel Mesquita"],
      "publisher": "Documenta", "place": "Lisboa", "year": 2026, "genre": "novel",
      "link": "http://id.bnportugal.gov.pt/bib/catbnp/2293200"
    }
  ]
}
```

Job state, also on `releases-data` (not read by the app):

- `state.json` `{ "harvestedUntil": "2026-11-02" }`: the last day fully harvested
- `seen.txt`: one record id per line, for every book ever listed

## The job (`releases/` Gradle module, JVM)

- Kotlin JVM application; Java's `HttpClient` and StAX XML parsing (`javax.xml.stream`).
- `Main` takes `--data-dir <path>` and optionally `--today <YYYY-MM-DD>` (for tests).
- **Each run:**
  1. `from` = `harvestedUntil` minus 1 day (the overlap is removed by `seen.txt`); `until` = yesterday.
  2. Harvest all pages and apply the data rules.
  3. Append the new books to their month files (creating them as needed).
  4. Rewrite `index.json` and `seen.txt`, and set `harvestedUntil` = `until`.
  5. Remove month files older than the newest 7.
- **First run** (no `state.json`): backfill from the first day of the month six months before the current
  month, harvesting one calendar month per request series.
- **On any HTTP or XML error:** exit non-zero without writing anything. The workflow then publishes nothing,
  and the next run starts again from the unchanged state.
- `.github/workflows/releases.yml`:
  - triggers: `schedule: cron "0 3 * * *"`, `workflow_dispatch`, and pushes touching `releases/**`
    (tests only)
  - steps: run `./gradlew :releases:test`; on schedule or dispatch, check out `releases-data` into
    `data/` (created as an orphan branch if missing), run the job, commit and push `data/` if it changed,
    then deploy `data/` with `actions/upload-pages-artifact` and `actions/deploy-pages`
    (`permissions: contents: write, pages: write, id-token: write`)

## The app

### Data

- `data/releases/ReleasesClient`: Ktor GET of `index.json` and `<month>.json` from
  `RELEASES_BASE_URL = "https://marioruidacosta.github.io/MyBooks/releases/"`, decoded with
  `kotlinx.serialization` (`ignoreUnknownKeys`).
- `data/releases/ReleasesRepository`: caches each file in `filesDir/releases/` (the existing `okio`
  `FileSystem`), recording when it was fetched. Refresh rules:
  - `index.json` and the current month: re-fetched when older than 6 hours
  - past months: re-fetched when older than 24 hours
  - on network failure the cached copy is returned, flagged with its fetch time
  - no cached copy and no network: a typed error
- Models:
  - `ReleaseMonth(month: YearMonth, complete, total, genres: Map<Genre, Int>)`
  - `ReleaseBook(id, isbn, title, authors, translators, publisher, place, year, genre, link)`
  - `Genre` enum with keys and labels exactly as in the genre table, and an `UNKNOWN` fallback for keys
    the app doesn't know (shown as "Other")

### Screens

- **Library top bar:** new icon `Icons.Filled.NewReleases`, description "New in Portugal", always shown,
  first among the icons.
- **`ReleasesScreen`:**
  - Title: "New in Portugal".
  - **Month picker:** a horizontally scrolling row of chips from `index.json`, newest first, labelled
    "November 2026 (so far)" for an incomplete month and "October 2026" otherwise.
  - **Genre list:** rows "Novel · 165", in the genre order above, only genres with books.
  - A note under the picker: "Books added to Portugal's National Bibliography (legal deposit) that month."
- **`ReleaseGenreScreen`:** the books of one genre and month, A–Z by title (case- and accent-insensitive,
  using the existing `searchKey`). Each row shows a small cover, title, authors and publisher.
  - Covers load lazily: `covers.openlibrary.org/b/isbn/<isbn>-M.jpg?default=false` first, then the
    existing lookup chain's `coverUrl` when the book screen opens.
- **`ReleaseBookScreen`:**
  - details: cover, title, authors, "Translated by …", publisher · place · year, ISBN
  - description from the existing lookup chain (`BookRepository.lookup(isbn)`) when it has one
  - **Add to my books** (To read / To buy, or "Already in your library"), reusing `LibraryShelf` from the
    feed. The saved book uses the ISBN, title, authors, publisher, year and the looked-up cover.
  - **Where to buy:** four buttons, each opened with `LocalUriHandler`:
    - Bertrand: `https://www.bertrand.pt/pesquisa/<isbn>`
    - Almedina: `https://www.almedina.net/catalogsearch/result/?q=<isbn>`
    - Wook: `https://www.wook.pt/pesquisa?keyword=<isbn>`
    - Fnac: `https://www.fnac.pt/SearchResult/ResultList.aspx?Search=<isbn>`
  - **View at the National Library:** opens `link`.
- **States:**
  - loading: a progress indicator
  - offline with a cached copy: the note "Showing saved list from 2 Nov, 10:15"
  - no copy: "Couldn't load the new releases" with **Retry**
  - `index.json` not published yet (HTTP 404): "New releases aren't available yet."

## Testing

- **Job** (`releases/src/test`, JUnit on JVM): fixtures are real October 2026 OAI pages trimmed to about
  40 records, covering every genre rule, an edited 2003 record, a record without ISBN, a periodical and a
  record without `675`.
  - **Genre rules:** each row of the genre table; `children` precedence over a `-31` form; `087.5` with
    non-literary codes
  - **Filters:** `am` only, ISBN required and normalised, year rule, `seen.txt` deduplication
  - **Field extraction:** authors with and without `$4`, translators, title with subtitle, the `003` link
  - **Run logic:** month assignment from the datestamp; resume from `harvestedUntil - 1`; nothing written on
    an HTTP error; files beyond 7 months removed; backfill range
  - **`index.json` counts** match the month files
- **App** (commonTest):
  - `ReleasesClient` with MockEngine, using a month file and index written by the job's tests (copied into
    `commonTest/fixtures`), so both sides agree on the format
  - `ReleasesRepository` cache rules: fresh, stale, offline with a copy, offline without one, 404
  - the ViewModels: month and genre selection, ordering, labels ("(so far)"), and the states
  - the four store URLs for an ISBN
  - Add to my books from a release
- **CI:** the new `releases.yml` workflow and the existing Android workflow.

## User setup (once)

1. GitHub → Settings → Pages → Build and deployment → Source: **GitHub Actions**.
2. (The `releases-data` branch is created by the first run.)
3. Run **Releases job** once by hand (Actions → Releases job → Run workflow) to backfill six months.
