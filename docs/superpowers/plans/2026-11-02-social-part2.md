# MyBooks Social Part 2 (Activity Feed, Reviews, Likes) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Signed-in users automatically share status changes, ratings, reviews and reading progress. Friends and public readers see these in an Activity feed, where they can like entries, see who liked them, report entries and add the book to their own library.

**Architecture:**
- **Server:** one SQL migration adds `shared_books`, `activities` and `likes`. They have no table access; everything goes through security-definer functions. `publish_book(book, before)` turns a book's public state into feed entries; `feed`, `person_activity` and `likers` read them. pgTAP tests run in CI.
- **Phone:** a local `share_state` table, plus a `ShareTracker` that `BookRepository` notifies on each change. A debounced `Publisher` uploads pending books, and `SharingStatus` knows whether sharing is active.
- **Screens:** a new Activity screen (Friends and Everyone tabs), with additions to the Profile, Account and Book screens.

**Tech Stack:**
- Supabase Postgres with pgTAP, run through `npx supabase`
- supabase-kt 3.8.0 (`postgrest.rpc`)
- Room KMP 2.8.5, schema v3
- Compose Multiplatform, Coil 3
- kotlinx-datetime, `kotlin.time.Instant`, `kotlin.uuid.Uuid`

**Spec:** `docs/superpowers/specs/2026-11-02-social-part2-activity-feed-design.md`

## Global Constraints

- **Branch** `feature/social-feed`. The local gate is `bash .superpowers/sdd/2026-11-02-social-part2/ut.sh`, a copy of the Part 1 `ut.sh`. It runs `:shared:compileCommonMainKotlinMetadata :shared:testAndroidHostTest :androidApp:testDebugUnitTest :androidApp:assembleDebug` and prints `SUMMARY: exit=0 … failed=0` on success.
- **SQL tests:** the `supabase` workflow on push. Use workflow badge SVGs, not the GitHub API, to check results (the API rate limit is 60/hour).
- **Server limits:**
  - review ≤ 2000 characters; title 1–300; authors ≤ 300; ISBN ≤ 20
  - rating 1–5 or null; progress 0–100 or null
  - cover URL https, ≤ 500 characters, host in {`books.google.com`, `books.googleusercontent.com`, `covers.openlibrary.org`, `rnod.bnportugal.gov.pt`}
- **Status mapping**, phone → server: `READING→reading`, `READ→read`, `TO_READ→to_read`, `WISHLIST→to_buy`. Entry kinds: `started, finished, want_to_read, want_to_buy, rated, reviewed, progress`.
- **Timing:** feed pages hold 20 entries (`max` capped at 50 on the server). Progress and rating entries collapse within **12 hours**. The Publisher debounce is **2 seconds**.
- **No direct writes** to the new tables from the app. `profiles.share_activity` changes only through `set_share_activity`.
- **Copy, verbatim from the spec:**
  - "Review (shared with friends)", "Notes (private)", "Don't share this book", "Share my reading activity"
  - "Stop sharing? This removes everything you've shared from the community." with the buttons "Cancel" / "Stop sharing"
  - "Join the MyBooks community", "Find people", "No activity yet", "Already in your library"
  - "Only {display name}'s friends can see their activity"
- **Code rules:**
  - No `java.*` or `android.*` in commonMain.
  - Never use the service-role key.
  - Commit messages end with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Review Focus

1. **Editing a book while offline (or on the Zscaler network), then reopening the app later:** the entry appears once, with no duplicates and no lost change. Pinned by Task 6 `failedUploadStaysPendingAndRetriesOnNextSync` and Task 1 `republish_same_state_creates_nothing`.
2. **Dragging the progress slider through many values in a few seconds:** one upload and one feed entry. Pinned by Task 6 `rapidChangesUploadOnce` and Task 1 `progress_collapses_within_12h`.
3. **Rating an old finished book right after joining:** only "rated" is posted, never "finished". Pinned by Task 5 `firstChangeRecordsBeforeState` and Task 1 `before_prevents_false_finished`.
4. **Liking while offline:** the heart flips back and a message is shown, so the count isn't wrong. Pinned by Task 7 `likeRollsBackOnFailure`.
5. **Restoring a backup:** nothing is posted. A later change updates the same server row. Pinned by Task 4 `backupRoundTripKeepsReviewAndShareInfo` and Task 5 `restoredBooksAreNotPending`.

---

### Task 1: Database migration and pgTAP tests

**Files:** Create `supabase/migrations/2026110200_social_part2.sql`, `supabase/tests/social_part2_test.sql`.

**Interfaces, Produces** (PostgREST `/rest/v1/rpc/<name>`; signatures exactly as in the spec's Functions section):
- `publish_book(book jsonb, before jsonb default null)`
- `unpublish_book(id uuid)`, `unpublish_all()`, `set_share_activity(enabled boolean)`
- `feed(scope text, before_time timestamptz default null, before_id bigint default null, max int default 20)`
- `person_activity(person uuid, before_time timestamptz default null, before_id bigint default null, max int default 20)`
- `like(activity bigint)`, `unlike(activity bigint)`, `likers(activity bigint, max int default 50)`
- the column `profiles.share_activity`, and `reports.activity` / `reports.reported_review`

The JSON keys of `book` are `id, isbn, title, authors, cover_url, status, rating, progress, review`; `before` has `status, rating, progress, review`. Feed rows have the columns `id, kind, rating, progress, created_at, actor_id, username, display_name, avatar_path, book_id, isbn, title, authors, cover_url, review, like_count, liked_by_me`.

- [ ] **Step 1:** Write `social_part2_test.sql` in the same style as `social_part1_test.sql`: users inserted into `auth.users` plus profiles, and an identity switch through `set local role authenticated; set local request.jwt.claims`. Tests:
  - **Visibility:** `self_sees_own_entries`, `friend_sees_friends_only_user`, `stranger_does_not_see_friends_only_user`, `stranger_sees_public_user_in_everyone`, `everyone_excludes_friends_only_users`, `blocked_hidden_both_ways` (in `feed`, `person_activity` and `likers`)
  - **Entries:** `status_mapping` (each of the 4 statuses gives its kind), `before_prevents_false_finished` (new row with `before.status='read'`, `book.status='read'`, rating null→4 gives only `rated`), `null_before_means_new_book` (gives a status entry), `republish_same_state_creates_nothing`, `progress_collapses_within_12h` (2 publishes give 1 entry with the latest value), `progress_not_collapsed_after_12h` (back-date `created_at` by 13 h, giving 2 entries), `finishing_adds_no_progress_entry`, `review_add_edit_clear` (1 entry, still 1 after an edit, 0 after clearing), `title_change_adds_nothing`
  - **Sharing and ownership:** `share_activity_off_is_noop`, `set_share_activity_false_unpublishes_all`, `others_book_id_refused` (`P0002`)
  - **Validation:** `validation_errors` (`22023` for title '' or 301 characters, rating 6, progress 101, review 2001 characters), `cover_allow_list` (each allowed host kept; `http://books.google.com/…`, `https://evil.example/x.jpg` and a 501-character URL become null)
  - **Likes:** `like_own_refused`, `like_invisible_refused`, `like_twice_counts_once`, `unlike_removes`
  - **Removal:** `unpublish_book_cascades` (entries and likes gone), `delete_my_account_removes_shared_data`
  - **Reports and access:** `report_snapshots_review` (survives clearing the review), `no_direct_table_access` (`select` / `insert` on `shared_books`, `activities` and `likes` as authenticated raise `42501`), `share_activity_not_directly_updatable`
- [ ] **Step 2:** Push. Expected: the supabase workflow is red, because the migration is missing.
- [ ] **Step 3:** Write the migration as the spec describes. Reuse the Part 1 `is_blocked_with`, `require_user` and friendship rows. Revoke all privileges on the new tables from `anon` and `authenticated`, then `grant execute` on the new functions to `authenticated`. Extend the trigger function `set_reported_username` to also set `reported_review` from the activity's book.
- [ ] **Step 4:** Push. Expected: the supabase workflow is green. Commit `feat(db): social part 2 schema and functions`.

### Task 2: Activity models, service interfaces and fakes

**Files:** Create `shared/src/commonMain/kotlin/com/mcosta/mybooks/social/model/Activity.kt`. Modify `social/model/Models.kt`, `social/Services.kt`, and `commonTest/…/social/FakeSocial.kt`. Test `commonTest/…/social/FakeActivityTest.kt`.

**Interfaces, Produces:**
```kotlin
enum class ActivityKind { STARTED, FINISHED, WANT_TO_READ, WANT_TO_BUY, RATED, REVIEWED, PROGRESS }
enum class FeedScope { FRIENDS, EVERYONE }
data class PublicBookState(val shareId: String, val isbn: String?, val title: String, val authors: String,
    val coverUrl: String?, val status: ReadingStatus, val rating: Int?, val progress: Int?, val review: String)
data class FeedBook(val shareId: String, val isbn: String?, val title: String, val authors: String, val coverUrl: String?)
data class FeedEntry(val id: Long, val actor: Person, val kind: ActivityKind, val rating: Int?, val progress: Int?,
    val createdAt: Instant, val book: FeedBook, val review: String?, val likeCount: Int, val likedByMe: Boolean)
data class FeedCursor(val createdAt: Instant, val id: Long)
data class FeedPage(val entries: List<FeedEntry>, val next: FeedCursor?)   // next == null when fewer than 20 came back
interface ActivityService {
    suspend fun publish(book: PublicBookState, before: PublicBookState?)
    suspend fun unpublish(shareId: String)
    suspend fun unpublishAll()
    suspend fun feed(scope: FeedScope, cursor: FeedCursor? = null): FeedPage
    suspend fun personActivity(userId: String, cursor: FeedCursor? = null): FeedPage
    suspend fun like(activityId: Long)
    suspend fun unlike(activityId: Long)
    suspend fun likers(activityId: Long): List<Person>
}
```

Changes to existing types:
- `Profile` gains `val shareActivity: Boolean = true`.
- `Person` gains `val visibility: Visibility = Visibility.FRIENDS`.
- `ProfileService` gains `suspend fun setShareActivity(enabled: Boolean): Profile`.
- `SafetyService.report` gains `activityId: Long? = null`.
- `SocialServices` gains `val activity: ActivityService`.

`FakeSocial` implements `ActivityService`:
- It records `published: MutableList<Pair<PublicBookState, PublicBookState?>>` and `unpublished: MutableList<String>`, and keeps `var failPublish: SocialException? = null`.
- `fun addEntry(actorId: String, kind: ActivityKind, title: String = "Book", isbn: String? = null): Long` seeds entries. `feed` and `personActivity` filter them with the SQL visibility rules (friends, public, blocks), newest first, 20 per page.
- `like` follows the same rules (not own, not invisible). Likes are tracked per entry.
- `setShareActivity(false)` clears that user's entries.
- `setVisibility(userId, Visibility)` is a test helper.
- `offline` applies to every call, as before.

- [ ] **Step 1:** In `FakeActivityTest`, write `friendsFeedHasFriendsAndMe`, `everyoneHasOnlyPublicUsers`, `blockedHiddenBothWays`, `cannotLikeOwnEntry`, `pagingReturnsNextCursorOnlyWhenFull` (25 entries give 20 + a cursor, then 5 + null) and `reportCarriesActivityId`. Run: FAIL (types missing).
- [ ] **Step 2:** Implement the models, interfaces and fake. Update the existing callers so the gate compiles: `SupabaseSocial` gets stubs that throw `NotImplementedError` (until Task 3), and existing fakes are updated.
- [ ] **Step 3:** Run the gate. Expected: PASS. Commit `feat(social): activity models and service interfaces`.

### Task 3: Supabase activity service

**Files:** Modify `social/supabase/SupabaseSocial.kt` (add `SupabaseActivityService`, `setShareActivity`, report `activity`, person `visibility`) and `social/supabase/Rows.kt` (`FeedRow`, `PublishBookJson`, `BeforeJson`, `share_activity` on `ProfileRow`). Test `commonTest/…/social/supabase/SupabaseSocialTest.kt`.

**Interfaces:**
- Consumes: the Task 1 RPC names and JSON keys, and the Task 2 interfaces.
- Mapping rules:
  - status: as in Global Constraints
  - kinds: the snake_case name maps to the `ActivityKind` with the same name in upper case
  - `avatar_path`: through the existing `avatarUrl`
  - `created_at`: parsed as `Instant`
  - a page with 20 rows gives `next = FeedCursor(last.createdAt, last.id)`
- All calls go through the existing `call {}`, so errors map to `SocialException`.

- [ ] **Step 1:** Write the tests, using `MockEngine` on `https://test.supabase.co`:
  - `publishSendsBookAndBefore`: POST `/rest/v1/rpc/publish_book`, body `{"book":{"id":"s1","isbn":"978…","title":"T","authors":"A","cover_url":null,"status":"to_buy","rating":null,"progress":null,"review":""},"before":{"status":"read","rating":3,"progress":null,"review":""}}`
  - `publishWithoutBeforeSendsNull`
  - `unpublishSendsId`
  - `feedSendsScopeAndCursor`: body `{"scope":"everyone","before_time":"2026-11-02T10:00:00Z","before_id":7,"max":20}`
  - `feedRowsMapToEntries`: covers kind, actor and avatar URL, review null on a non-review entry, like count, and the next cursor when there are 20 rows
  - `likeOwnMapsToNotAvailable`: 400 with code `P0002` gives `NOT_AVAILABLE`
  - `likersMapToPeople`
  - `setShareActivityCallsRpcAndReturnsProfile`
  - `reportSendsActivity`: the insert body contains `"activity":12`
- [ ] **Step 2:** Run. Expected: FAIL. Implement it. Run the gate. Expected: PASS. Commit `feat(social): Supabase activity service`.

### Task 4: Local data — review, share_state, migration 2→3, backups

**Files:**
- Create: `data/db/ShareStateEntity.kt` (with `ShareStateDao`), `shared/schemas/…/3.json` (generated by the build), `commonTest/…/data/FakeShareStateDao.kt`.
- Modify: `domain/Book.kt` (`val review: String = ""`), `data/db/BookEntity.kt` (+`review`, mappers), `data/db/AppDatabase.kt` (version 3, entities + `ShareStateEntity`, `abstract fun shareStateDao()`, `MIGRATION_2_3`, added to `createDatabase`), `data/backup/BackupSerializer.kt`, `data/BookRepository.kt` (validation, `setReview`).
- Tests: `androidHostTest/…/data/db/MigrationTest.kt`, `commonTest/…/data/backup/BackupSerializerTest.kt`, `commonTest/…/data/BookRepositoryTest.kt`.

**Interfaces, Produces:**
```kotlin
@Entity(tableName = "share_state") data class ShareStateEntity(
    @PrimaryKey val bookId: Long, val shareId: String, val owner: String,
    val hasBefore: Boolean, val beforeStatus: ReadingStatus?, val beforeRating: Int?, val beforeProgress: Int?, val beforeReview: String?,
    val pending: Boolean, val excluded: Boolean, val deleted: Boolean)
@Dao interface ShareStateDao {
    suspend fun get(bookId: Long): ShareStateEntity?
    fun observe(bookId: Long): Flow<ShareStateEntity?>
    @Upsert suspend fun upsert(row: ShareStateEntity)
    suspend fun delete(bookId: Long)
    suspend fun pendingFor(owner: String): List<ShareStateEntity>
    suspend fun all(): List<ShareStateEntity>
    suspend fun adoptOrphans(owner: String)   // UPDATE share_state SET owner = :owner WHERE owner = ''
    suspend fun deleteForOwner(owner: String)
}
data class BackupShare(val shareId: String, val excluded: Boolean)
// BackupSerializer:
fun encode(books: List<Book>, exportedAt: Long, shares: Map<Long, BackupShare> = emptyMap()): String   // keyed by Book.id; fields "review", "shareId", "shareExcluded"
fun decodeWithShares(text: String): List<Pair<Book, BackupShare?>>       // decode(text) = decodeWithShares(text).map { it.first }
// BookRepository:
suspend fun setReview(id: Long, review: String)    // require(review.length <= 2000)
// SaveResult gains: data object InvalidReview  (save() rejects review > 2000)
```
`MIGRATION_2_3` adds the column with `ALTER TABLE books ADD COLUMN review TEXT NOT NULL DEFAULT ''` and creates `share_state` exactly as Room's v3 schema expects. Copy the SQL from the generated `3.json`. The backup file format stays at `version` 1, because the new fields are optional.

- [ ] **Step 1:** Write the tests:
  - `MigrationTest`: `upgradeFrom2KeepsBooksAndAddsReview` (a v2 database with one book opens at v3, the book is intact with `review == ""`, and `share_state` is empty and writable), and the existing 1→2 test extended to open at v3
  - `BackupSerializerTest`: `backupRoundTripKeepsReviewAndShareInfo` and `oldBackupWithoutNewFieldsStillDecodes`
  - `BookRepositoryTest`: `reviewOver2000Rejected` (save gives `InvalidReview`; `setReview` throws) and `setReviewStoresText`
- [ ] **Step 2:** Run. Expected: FAIL. Implement it, building once to generate `3.json`. Run the gate. Expected: PASS. Commit `feat(data): review field, share_state table, schema v3`.

### Task 5: ShareTracker, SharingStatus and repository hooks

**Files:** Create `share/PublicState.kt`, `share/ShareTracker.kt`, `share/SharingStatus.kt`. Modify `data/BookRepository.kt`. Test `commonTest/…/share/ShareTrackerTest.kt`, `commonTest/…/share/SharingStatusTest.kt`, `commonTest/…/data/BookRepositoryBackupTest.kt`.

**Interfaces, Produces:**
```kotlin
fun Book.publicState(shareId: String): PublicBookState            // coverUrl kept only if it starts with "https://"; progress = progressPercent
fun publicFieldsDiffer(a: Book, b: Book): Boolean                  // status, rating, progressPercent, review, title, authors, coverUrl

class ShareTracker(
    private val dao: ShareStateDao,
    private val activeOwner: () -> String?,          // non-null only when signed in + profile + shareActivity
    private val onPending: () -> Unit,               // Publisher.requestSync
    private val newShareId: () -> String = { Uuid.random().toString() },
) {
    suspend fun changed(old: Book?, new: Book)
    suspend fun deleted(book: Book)
    suspend fun setExcluded(book: Book, excluded: Boolean)
    fun observeExcluded(bookId: Long): Flow<Boolean>
    suspend fun exportShares(): Map<Long, BackupShare>
    suspend fun restored(bookId: Long, share: BackupShare)    // owner = activeOwner() ?: "", pending = false, hasBefore = false
    suspend fun forgetOwner(owner: String)                    // dao.deleteForOwner
}

data class SharingState(val userId: String? = null, val hasProfile: Boolean = false, val shareActivity: Boolean = false) {
    val activeOwner: String? get() = userId?.takeIf { hasProfile && shareActivity }
}
class SharingStatus(
    private val social: SocialServices, scope: CoroutineScope,
    private val onActivated: () -> Unit,                       // Publisher.requestSync, when activeOwner becomes non-null
    private val onDisabled: suspend (owner: String) -> Unit,   // ShareTracker.forgetOwner
) {
    val state: StateFlow<SharingState>                         // follows auth.session; on SignedIn loads myProfile()
    suspend fun refresh()                                      // reload profile (after createProfile, etc.)
    suspend fun setShareActivity(enabled: Boolean): Profile    // profiles.setShareActivity, then onDisabled(owner) when false, then update state
}
// BookRepository gains constructor param `shareTracker: ShareTracker? = null` and:
fun observeShareExcluded(id: Long): Flow<Boolean>          // flowOf(false) without a tracker
suspend fun setShareExcluded(id: Long, excluded: Boolean)
```

The rules are the spec's ShareTracker section, applied exactly:
- **changed:** acts only when `activeOwner()` is non-null, `publicFieldsDiffer` is true (or `old` is null), and the book isn't excluded.
  - No row, or a row owned by someone else: write a new row with a new `shareId`. "Before" is `old`'s status, rating, progress and review, with `hasBefore = old != null`.
  - Then set `pending = true` and call `onPending()`.
- **deleted:** sets `deleted = pending = true` if a row exists.
- **setExcluded(true):** creates the row if needed (with "before" = the current state), then sets `excluded = pending = true`.
- **setExcluded(false):** if the row is pending, sets `excluded = false, pending = false`; otherwise deletes the row.

The repository calls:
- `changed(null, saved)` after an insert, and `changed(stored, merged)` after an update in `save`
- `changed(old, new)` in `modify`, which covers status, rating, notes, progress, start date, cover and review
- `deleted(book)` in `delete`

`importJson` and `importBackup` call only `restored(...)`, for entries that have a `BackupShare`. `exportBackup` passes `exportShares()` to `encode`.

- [ ] **Step 1:** Write the tests, with `FakeBookDao`, `FakeShareStateDao`, `FakeSocial` and a fixed `newShareId`:
  - `ShareTrackerTest`:
    - `firstChangeRecordsBeforeState`: rating null→4 on a READ book gives a row with `hasBefore`, `beforeStatus = READ`, `beforeRating = null`, and pending
    - `newBookHasNoBefore`
    - `notesOnlyChangeIgnored`
    - `nothingWhenNoActiveOwner`
    - `excludedBookIgnored`
    - `deleteMarksDeletedPending`
    - `rowOfOtherOwnerReplaced`
    - `unexcludeWhilePendingKeepsRow` / `unexcludeAfterUploadDeletesRow`
    - `onPendingCalledOnce` (once per change)
  - `SharingStatusTest`:
    - `activeOnlyWithProfileAndShareActivity`
    - `disablingCallsServerThenForgetsLocalRows`
    - `activationTriggersSync`
  - `BookRepositoryBackupTest`:
    - `restoredBooksAreNotPending`: a backup containing a share gives a row with the same `shareId`, `pending = false` and owner `""` when signed out
    - `importDoesNotTrackChanges`
- [ ] **Step 2:** Run. Expected: FAIL. Implement it. Run the gate. Expected: PASS. Commit `feat(share): track shareable changes`.

### Task 6: Publisher and app wiring

**Files:** Create `share/Publisher.kt`. Modify `AppContainer.kt` (build `ShareTracker`, `SharingStatus` and `Publisher` when `social != null`, and pass the tracker to `BookRepository`; expose `val sharing: SharingStatus?`). Test `commonTest/…/share/PublisherTest.kt`, and `commonTest/…/ui/social/SocialEntryPointsTest.kt` (extended).

**Interfaces, Produces:**
```kotlin
class Publisher(
    private val dao: ShareStateDao,
    private val loadBook: suspend (Long) -> Book?,
    private val activity: ActivityService,
    private val activeOwner: () -> String?,
    private val scope: CoroutineScope,
    private val debounce: Duration = 2.seconds,
    private val newShareId: () -> String = { Uuid.random().toString() },
) {
    fun requestSync()          // restarts a 2 s timer, then syncNow() in scope
    suspend fun syncNow()      // adoptOrphans(owner), then each pendingFor(owner) in bookId order
}
```

Each pending row is handled as follows:
- **deleted, or the book is gone:** call `unpublish(shareId)`, then delete the row.
- **excluded:** call `unpublish(shareId)`, then save the row with `pending = false`.
- **otherwise:** call `publish(book.publicState(shareId), before)`. `before` is a `PublicBookState` built from the before fields when `hasBefore`, else null. Then clear "before" and set `pending = false`.

Failures:
- `SocialException(NOT_AVAILABLE)`: assign a new `shareId`, set "before" = the current public state with `hasBefore = true`, keep the row pending and continue.
- Any other exception: `logSocialError`, then stop the run. `CancellationException` is rethrown.

Wiring in `AppContainer`:
- `SharingStatus(social, applicationScope, onActivated = publisher::requestSync, onDisabled = tracker::forgetOwner)`
- the tracker gets `onPending = publisher::requestSync`
- both get `activeOwner = { sharing.state.value.activeOwner }`

Use `lateinit` or lambdas to break the construction cycle.

- [ ] **Step 1:** Write the tests, using `StandardTestDispatcher` with virtual time:
  - `rapidChangesUploadOnce`: 5 `requestSync()` calls within 1 s, then advance 2 s, give exactly 1 publish
  - `failedUploadStaysPendingAndRetriesOnNextSync`: with `failPublish = OFFLINE`, the row stays pending and `before` is kept; after clearing it and calling `syncNow()`, it's published with the same `before`
  - `stopsAtFirstFailure`
  - `deletedRowUnpublishedAndRemoved`
  - `excludedRowUnpublishedAndKept`
  - `notAvailableGetsNewShareIdAndContinues`
  - `orphanRowsAdopted`
  - `nothingWithoutActiveOwner`
  - `SocialEntryPointsTest`: `sharingNullWhenSocialNotConfigured`
- [ ] **Step 2:** Run. Expected: FAIL. Implement it. Run the gate. Expected: PASS. Commit `feat(share): publisher and wiring`.

### Task 7: Activity screen

**Files:** Create `ui/social/ActivityViewModel.kt`, `ui/social/ActivityScreen.kt`, `ui/social/FeedEntryCard.kt`, `ui/social/LikersSheet.kt`, `ui/social/FeedBookSheet.kt`, `ui/social/FeedText.kt`. Modify `ui/MyBooksNavHost.kt` (route `Routes.ACTIVITY = "activity"`) and `ui/library/LibraryScreen.kt` (`onOpenActivity: (() -> Unit)?`; icon `Icons.Filled.DynamicFeed`, description "Activity", placed before Friends). Test `commonTest/…/ui/social/ActivityViewModelTest.kt`, `commonTest/…/ui/social/FeedTextTest.kt`.

**Interfaces, Produces:**
```kotlin
// FeedText.kt
fun actionText(entry: FeedEntry): String      // "started reading", "finished", "rated ★★★★☆", "wants to read", "wants to buy", "reviewed", "is 60% through"
fun relativeTime(then: Instant, now: Instant): String   // <1 min "just now"; <60 min "N min ago"; <24 h "N h ago"; <7 d "N d ago"; else formatDate(then.toEpochMilliseconds())

data class ActivityState(
    val signedIn: Boolean = false, val scope: FeedScope = FeedScope.FRIENDS,
    val entries: List<FeedEntry> = emptyList(), val next: FeedCursor? = null,
    val loading: Boolean = false, val loadingMore: Boolean = false, val hasFriends: Boolean = true,
    val error: String? = null, val message: String? = null,
    val likers: List<Person>? = null,                 // non-null = LikersSheet open
    val openBook: FeedBook? = null, val openBookInLibrary: Boolean = false,
)
class ActivityViewModel(private val services: SocialServices, private val repository: BookRepository) : ViewModel() {
    val state: StateFlow<ActivityState>
    fun selectScope(scope: FeedScope); fun refresh(); fun loadMore()
    fun toggleLike(entryId: Long)                   // optimistic; on failure revert + message = socialMessage(e)
    fun showLikers(entryId: Long); fun dismissLikers()
    fun report(entry: FeedEntry, reason: ReportReason, details: String)   // message "Thanks, we'll look into it"
    fun openBook(book: FeedBook); fun closeBook()
    fun addToLibrary(status: ReadingStatus)         // TO_READ or WISHLIST; message "Added to To read" / "Added to To buy"
    fun dismissMessage()
}
```

Behaviour:
- **Library match:** `openBook` looks the book up with `repository.findByIsbn`, or, when there is no ISBN, `findWithoutIsbn(title, authors)` through a new `BookRepository.findByTitleAndAuthors(title, authors): Book?`.
- **Adding a book:** `addToLibrary` saves `Book(isbn, title, authors, coverUrl, status)`. If it's already present it does nothing, and the sheet shows "Already in your library".
- **Friends tab without friends:** `hasFriends` comes from `friends.friends().isNotEmpty()`, loaded on the Friends tab only. When it's false, the screen shows a "Find people" button that opens the Friends screen.
- **Navigation:** taps on people go to `Routes.profile(id)`.

- [ ] **Step 1:** Write the tests:
  - `FeedTextTest`: each kind's text, 4 → "rated ★★★★☆", 60 → "is 60% through", and every `relativeTime` boundary (59 s, 61 s, 59 min, 23 h, 6 d, 8 d → the date)
  - `ActivityViewModelTest`, using `FakeSocial` and `MainDispatcherTest`:
    - `signedOutShowsJoinState`
    - `friendsTabLoadsFirstPage`
    - `switchingScopeReloads`
    - `loadMoreAppendsAndStopsAtEnd` (25 entries)
    - `noFriendsFlagged`
    - `likeIsOptimistic`
    - `likeRollsBackOnFailure` (offline: `likedByMe` and the count revert, and the message is "You're offline")
    - `likersShown`
    - `reportPassesActivityId`
    - `openBookDetectsExistingByIsbn`
    - `addToLibrarySavesToBuy`
    - `errorShowsRetryMessage`
- [ ] **Step 2:** Run. Expected: FAIL. Implement the VM, screen, card, sheets, route and icon as the spec's Screens section describes:
  - tabs, pull to refresh with `PullToRefreshBox`, and loading more when the last item is shown
  - the review cut to 4 lines and expanded on tap
  - the count opens the likers list; the overflow menu offers Report, reusing `ReportDialog`
- [ ] **Step 3:** Run the gate. Expected: PASS. Commit `feat(ui): activity feed`.

### Task 8: Profile, Account and Book screens, docs, CI

**Files:**
- Modify:
  - `ui/social/ProfileViewModel.kt` and `ProfileScreen.kt`
  - `ui/social/AccountViewModel.kt` and `AccountScreen.kt`
  - `ui/social/AccountRoute.kt` (passes `sharing`)
  - `ui/social/SignInViewModel.kt`: new constructor parameter `onProfileCreated: suspend () -> Unit = {}`, called after a successful `createProfile`. `AccountRoute` passes `{ sharing?.refresh() }`.
  - `ui/detail/BookDetailViewModel.kt` and `BookDetailScreen.kt`
  - `ui/edit/EditBookViewModel.kt` and `EditBookScreen.kt`
  - `ui/MyBooksNavHost.kt`
  - `docs/social-setup.md`, `README.md`
- Tests: `ProfileViewModelTest`, `AccountViewModelTest`, `BookDetailViewModelTest`, `EditBookViewModelTest`.

**Interfaces, Produces:**
- `ProfileState` gains `activity: List<FeedEntry>`, `activityNext: FeedCursor?` and `activityHidden: Boolean`. Hidden means `person.visibility == FRIENDS && relationship != FRIENDS`; in that case no call is made. It also gains `loadMoreActivity()` and `toggleLike(entryId)`, with the same optimistic rule as Task 7.
- `AccountViewModel(services, shrinker, sharing: SharingStatus?)` gains `fun setShareActivity(enabled: Boolean)`, which calls `sharing.setShareActivity` and updates `profile`. The screen shows the confirmation dialog with the exact copy before turning it off.
- `BookDetailViewModel` gains:
  - `reviewDraft: StateFlow<String>`, `editReview(text)` (capped at 2000) and `saveReview()`, mirroring the existing notes draft (saved on focus loss, on stop and on leaving)
  - `shareExcluded: StateFlow<Boolean>` and `setShareExcluded(Boolean)`
- `BookDetailScreen` gets a `sharing: SharingState` parameter, from `container.sharing?.state` or `SharingState()` otherwise:
  - the Review field is shown when `sharing.hasProfile`
  - the "Don't share this book" switch is shown when `sharing.activeOwner != null`
  - Notes is relabelled "Notes (private)"
- `EditBookViewModel`'s form gains `review`, shown under the same condition, with the label "Review (shared with friends)".

- [ ] **Step 1:** Write the tests:
  - `ProfileViewModelTest`: `publicStrangerShowsActivity`, `friendsOnlyStrangerShowsHidden` (no `personActivity` call), `friendShowsActivity`
  - `AccountViewModelTest`: `turningSharingOffCallsServerAndUpdatesProfile`, `turningSharingOnUpdatesProfile`
  - `BookDetailViewModelTest`: `reviewDraftSavedOnLeave`, `reviewCappedAt2000`, `excludeTogglesTracker`
  - `EditBookViewModelTest`: `reviewSavedWithBook`
- [ ] **Step 2:** Run. Expected: FAIL. Implement it, with the copy exactly as in Global Constraints. Add step 3b to `docs/social-setup.md`: run `npx supabase db push` again from `C:\Users\mcosta\GIT\MyBooks` with `$env:NODE_USE_SYSTEM_CA = "1"` to apply `2026110200_social_part2.sql`. Add a line to the README's features section.
- [ ] **Step 3:** Run the gate. Expected: PASS. Push; the iOS and Supabase workflow badges for `feature/social-feed` should be green. `android.yml` runs only on `main` and on pull requests, so for Android the local gate is the check until a PR is opened. Commit `feat(ui): reviews, sharing switches and profile activity`.
