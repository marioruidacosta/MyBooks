# MyBooks Social, Part 2: Activity Feed, Reviews and Likes

**Date:** 2026-11-02
**Status:** Approved design, pending spec review
**Builds on:** Part 1 (`feature/social-accounts`, spec `2026-11-01-social-part1-accounts-friends-design.md`)
**Branch:** `feature/social-feed` (from `feature/social-accounts`)

## Purpose

Let readers see what their friends, and public readers, are doing with their books. When a signed-in user
starts, finishes, rates, reviews or makes progress on a book, it appears in an activity feed. Others can
like it, see who liked it, open the reviewer's profile and add the book to their own library.

### Decisions taken with the user

- Activity types: status changes, ratings, reviews, reading progress (from the Part 1 brainstorm).
- Visibility uses the Part 1 per-user setting: `friends` (default) or `public`.
- Only shared activity goes to the server; the library itself stays on the phone.
- Sharing is **automatic with opt-out**: an account switch "Share my reading activity" (on by default)
  and a per-book "Don't share this book" switch.
- **Only new changes** are shared. Nothing that happened before joining (or before turning sharing on)
  is published.
- Interaction: **likes**, plus **seeing who liked**. No comments. Any entry's book can be added to your
  own library.
- The feed has its **own top-bar icon** in the library screen, with two tabs: **Friends** (friends and
  you) and **Everyone** (all public users).
- Publishing approach: **shared book state plus server-generated activity** (the phone uploads each
  shared book's current public state; a database function decides which feed entries that creates).

### Success criteria

1. Without an account, or with sharing switched off, the app makes no activity calls and behaves as
   in Part 1.
2. With sharing on, changing a book's status, rating, progress or review creates the matching feed
   entry, at most a few seconds later, or on the next app start if the phone was offline.
3. A friend (or anyone, for a public user) sees the entry in the right tab. A blocked user, in
   either direction, or a non-friend of a friends-only user, never sees it.
4. Re-sending a state never duplicates entries. Progress updates collapse. Rating an old finished book
   posts "rated", not "finished".
5. Users can like and unlike entries they can see, see who liked them, report entries, and add an
   entry's book to their library as To read or To buy.
6. "Don't share this book", deleting a book, turning sharing off and deleting the account remove the
   corresponding data from the server.
7. Rules are enforced by Postgres functions, verified by pgTAP tests in CI, and the app behaves the
   same on Android and iPhone.

### Out of scope (Part 2)

Comments; push notifications; sharing cover photos taken in the app; posting past history; searching
books across users; editing or deleting a single feed entry by hand (it follows the book).

## Architecture

```text
shared/commonMain
  social/
    ActivityService.kt        interface: publish, unpublish, unpublishAll, feed, personActivity,
                              like, unlike, likers
    model/Activity.kt         ActivityKind, FeedScope, FeedEntry, FeedPage, PublicBookState
    supabase/SupabaseSocial.kt  + SupabaseActivityService (RPC calls only)
  share/
    ShareTracker.kt           told about every library change; marks books waiting to upload
    Publisher.kt              uploads waiting books (2 s after the last change, at app start, after sign-in)
  data/
    BookRepository.kt         notifies ShareTracker from save/status/rating/progress/review/cover/delete
    db/ShareStateEntity.kt    + ShareStateDao; Room migration 2 → 3
  ui/social/
    ActivityScreen + ActivityViewModel   (Friends / Everyone tabs)
    FeedEntryCard, LikersSheet, FeedBookSheet
    ProfileScreen/AccountScreen          extended
  ui/book/                    Review field and "Don't share this book" switch
supabase/
  migrations/2026110200_social_part2.sql
  tests/social_part2_test.sql
```

`SocialServices` gains `val activity: ActivityService`. `AppContainer` builds `ShareTracker` and
`Publisher` only when social is configured; `BookRepository` takes an optional `ShareTracker`
(null in tests and when social is not configured).

## Database (migration `2026110200_social_part2.sql`)

### Types and tables

- `book_status` enum: `reading`, `read`, `to_read`, `to_buy`.
- `activity_kind` enum: `started`, `finished`, `want_to_read`, `want_to_buy`, `rated`, `reviewed`,
  `progress`.
- `profiles.share_activity boolean not null default true`. The account switch is stored on the server,
  so all of a user's phones follow it.
- `shared_books`
  - `id uuid primary key`: the phone's permanent shared ID for the book
  - `owner uuid not null references profiles(id) on delete cascade`
  - `isbn text` (≤ 20), `title text not null` (1–300), `authors text not null default ''` (≤ 300)
  - `cover_url text`: null unless it passes `allowed_cover_url` (below)
  - `status book_status not null`, `rating int` (1–5 or null), `progress int` (0–100 or null)
  - `review text not null default ''` (≤ 2000)
  - `updated_at timestamptz not null default now()`
- `activities`
  - `id bigint generated always as identity primary key`
  - `actor uuid not null references profiles(id) on delete cascade`
  - `book uuid not null references shared_books(id) on delete cascade`
  - `kind activity_kind not null`
  - `rating int`, `progress int`: the value at the time of the entry
  - `created_at timestamptz not null default now()`
  - index on `(created_at desc, id desc)` and on `(actor, created_at desc)`
- `likes`
  - `activity bigint references activities(id) on delete cascade`
  - `liker uuid references profiles(id) on delete cascade`
  - `created_at timestamptz not null default now()`, primary key `(activity, liker)`
- `reports` (from Part 1) gains `activity bigint references activities(id) on delete set null` and
  `reported_review text`. The existing `set_reported_username` trigger also copies the review of the
  reported activity's book, so the evidence survives a later edit or delete.

### Access

RLS is enabled on `shared_books`, `activities` and `likes`, with **no policies**, and all table
privileges are revoked from `anon` and `authenticated`. Every read and write goes through the
functions below (`security definer`, `set search_path = public`, granted to `authenticated` only).
`authenticated` gets `insert (activity)` on `reports` in addition to the Part 1 columns.
`share_activity` is **not** directly updatable: it changes only through `set_share_activity`, so that
turning it off always unpublishes. Its value is readable with the rest of the caller's own profile.

### Helper rules

- `can_see_activity_of(actor uuid) returns boolean`: true when `actor = auth.uid()`, or when neither
  has blocked the other (`is_blocked_with`) **and** (the actor's visibility is `public` **or** the two
  are accepted friends).
- `allowed_cover_url(url text) returns text`: returns the URL if it starts with `https://` and its host
  is one of `books.google.com`, `books.googleusercontent.com`, `covers.openlibrary.org`,
  `rnod.bnportugal.gov.pt`, and it is at most 500 characters; otherwise null.

### Functions

**`publish_book(book jsonb, before jsonb default null) returns void`**

`book`: `{id, isbn, title, authors, cover_url, status, rating, progress, review}`.
`before`: `{status, rating, progress, review}`. It is the book's state before its first shared change,
or null for a book added after joining.

1. If the caller's `share_activity` is false, do nothing.
2. If `book.id` exists and belongs to someone else, raise `P0002` (not available).
3. `prev` = the stored row if it exists; otherwise `before`; otherwise "nothing" (all fields null,
   review empty).
4. Upsert the row with the validated values. A disallowed cover URL becomes null. Invalid values
   (title length, rating, progress, review length, status) raise `22023`.
5. Create entries by comparing `prev` with the new state:
   - **Status changed:** add an entry: `reading` → `started`, `read` → `finished`, `to_read` →
     `want_to_read`, `to_buy` → `want_to_buy`.
   - **Rating changed to a non-null value:** `rated`, with the rating.
   - **Progress changed to a non-null value, status is `reading`, and no status entry was just
     added:** `progress`, with the value.
   - **Review went from empty to non-empty:** `reviewed`. Non-empty to a different non-empty value:
     no new entry (the card shows the current review). Non-empty to empty: delete the book's
     `reviewed` entries.
   - **Collapse:** for `rated` and `progress`, if the actor's latest entry for this book has the same
     kind and is less than 12 hours old, update its value and set `created_at = now()` instead of
     inserting.
   - A change only to title, authors or cover updates the row and adds no entry.

Calling it twice with the same `book` creates nothing the second time, because `prev` equals the
stored row.

**`unpublish_book(id uuid) returns void`**: deletes the caller's row (entries and likes cascade).
An unknown ID is not an error.

**`unpublish_all() returns void`**: deletes all of the caller's `shared_books`.

**`set_share_activity(enabled boolean) returns void`**: updates the flag. When `false`, it also runs
`unpublish_all()` in the same transaction.

**`feed(scope text, before_time timestamptz default null, before_id bigint default null, max int default 20)`**

- `scope = 'friends'`: entries whose actor is the caller or an accepted friend.
- `scope = 'everyone'`: entries whose actor has `public` visibility.
- Both exclude blocked users in either direction, and use keyset paging on `(created_at, id) <
  (before_time, before_id)`. `max` is capped at 50.
- Returns:
  - `id`, `kind`, `rating`, `progress`, `created_at`
  - the actor's `actor_id`, `username`, `display_name`, `avatar_path`
  - the book's `book_id`, `isbn`, `title`, `authors`, `cover_url`
  - `review` (the book's current review, only on `reviewed` entries; otherwise null)
  - `like_count`, `liked_by_me`

**`person_activity(person uuid, before_time, before_id, max)`**: the same columns, for one person.
Returns no rows if `can_see_activity_of(person)` is false.

**`like(activity bigint)`**: inserts `(activity, auth.uid())`, ignoring repeats. Raises `P0002` if the
caller can't see the activity, or if it is the caller's own.

**`unlike(activity bigint)`**: deletes the caller's like.

**`likers(activity bigint, max int default 50)`**: returns `id`, `username`, `display_name` and
`avatar_path` of the people who liked it, newest first, excluding anyone blocked in either direction.
Returns nothing if the caller can't see the activity.

Account deletion (Part 1 `delete_my_account`) removes everything through the `profiles` cascades.

## Phone side

### Local data (Room migration 2 → 3)

- `books.review TEXT NOT NULL DEFAULT ''`, added to `Book`, with a 2000-character limit enforced in
  `BookRepository` validation (new `SaveResult.InvalidReview`).
- `share_state` table (`ShareStateEntity`):
  - `bookId INTEGER PRIMARY KEY`: no foreign key, so a deleted book can still be unpublished
  - `shareId TEXT NOT NULL`: a random UUID, created on first need
  - `owner TEXT NOT NULL`: the user ID it belongs to
  - `beforeStatus TEXT`, `beforeRating INTEGER`, `beforeProgress INTEGER`, `beforeReview TEXT`
  - `hasBefore INTEGER NOT NULL`: whether "before" is set (a new book has no "before")
  - `pending INTEGER NOT NULL`
  - `excluded INTEGER NOT NULL`
  - `deleted INTEGER NOT NULL`
- Backups include each book's `review`, `shareId` and `excluded`. Restoring creates `share_state` rows
  with `pending = false` and no "before", so nothing is posted. Their owner is the signed-in user, or
  `""` when nobody is signed in; the first signed-in user adopts `""` rows. A later change to the book
  updates the existing server row.

### Model and API changes

- `Profile.shareActivity: Boolean` (and `ProfileRow.share_activity`), and
  `ProfileService.setShareActivity(enabled: Boolean): Profile`.
- `SafetyService.report(userId, reason, details, activityId: Long? = null)`.
- `ActivityService`:
  - `publish(book: PublicBookState, before: PublicBookState?)`
  - `unpublish(shareId: String)`, `unpublishAll()`
  - `feed(scope: FeedScope, cursor: FeedCursor?): FeedPage`
  - `personActivity(userId: String, cursor: FeedCursor?): FeedPage`
  - `like(activityId: Long)`, `unlike(activityId: Long)`, `likers(activityId: Long): List<Person>`

  `FeedPage` has `entries` and `next: FeedCursor?` (null at the end). `FeedCursor` is
  `(createdAt, id)` of the last entry.

### ShareTracker

Called by `BookRepository` after each change: `changed(old: Book?, new: Book)` and `deleted(book:
Book)`. `importBackup` and `importJson` do not call it.

It acts only if social is configured, the session is signed in with a profile, and the profile's
`shareActivity` is true. The tracker caches the profile and refreshes it on session change and when
the account switch changes.

- **Changed:** if any public field differs (status, rating, progress, review, title, authors, cover),
  and the book isn't excluded:
  - no row, or a row owned by someone else: create it with "before" = the old book's public state
    (no "before" if `old` is null); replace any other owner's row
  - set `pending = true`, then ask the Publisher to run
- **Deleted:** if a row exists, set `deleted = pending = true`.
- **Excluded (switch on):** create the row if needed, then set `excluded = pending = true`.
- **Excluded (switch off):**
  - if the row is still pending (its unpublish hasn't reached the server yet), set `excluded = false`
    and `pending = false`; the server row was never removed and stays valid
  - otherwise delete the row, so the next change creates a fresh one, with "before" = the book's state
    at that moment

### Publisher

- `requestSync()` debounces for 2 seconds, then uploads every pending row of the current owner, one
  at a time:
  - deleted or excluded: `unpublish(shareId)`
  - otherwise: `publish(state, before)`
- After success: clear "before" and set `pending = false`. Deleted rows are removed. Excluded rows
  are kept, not pending, so the switch remembers its state.
- On `NOT_AVAILABLE` (the shared ID belongs to another account, e.g. a backup restored by someone
  else): give the row a new `shareId`, set "before" = the book's current public state (so nothing is
  posted for it), keep it pending, and continue with the next row.
- On any other failure: log through `logSocialError`, keep the row pending and stop this run.
- Also runs when the app starts and right after sign-in.
- Uses an application-wide scope, not a screen's, so leaving a screen doesn't cancel an upload.
- The account switch calls `set_share_activity`. When it is turned off, local `share_state` rows of
  that owner are deleted after the server call succeeds.

The cover URL is sent as stored. Cover photos taken in the app (local file references) are sent as
null; the server also nulls disallowed URLs.

## Screens

### Activity screen (new top-bar icon in the library, shown when social is configured)

- Tabs **Friends** and **Everyone**, each a list of `FeedEntryCard`, with pull to refresh and the next
  20 loaded at the end of the list.
- `FeedEntryCard` shows:
  - the avatar and display name (tap → profile), with the action text: "started reading", "finished",
    "rated ★★★★☆", "wants to read", "wants to buy", "reviewed", "is 60% through" with a small
    progress bar
  - a relative time: "just now", "5 min ago", "2 h ago", "3 d ago", then the date
  - the cover thumbnail, title and authors (tap → `FeedBookSheet`)
  - the review, cut to 4 lines; tap expands it
  - a heart with the like count. Tapping the heart toggles it at once and rolls back with a message
    if the call fails. Tapping the count opens `LikersSheet` (avatar and name; tap → profile).
  - an overflow menu with **Report** on others' entries, which reuses the Part 1 report dialog and
    passes the activity ID
- `FeedBookSheet`: cover, title, authors, ISBN, and **Add to my books** with **To read** and **To buy**.
  It finds an existing book by ISBN (`BookRepository.findByIsbn`), or by title and authors when there
  is no ISBN. If found, it shows "Already in your library". Otherwise it saves a new book from the
  entry's fields and confirms with a message.
- Empty and error states:
  - not signed in: "Join the MyBooks community" → account route
  - Friends tab with no friends: "Find people" → Friends screen, Find people tab
  - no entries: "No activity yet"
  - error: the existing `socialMessage` text with **Retry**

### Profile screen (another person)

New **Recent activity** section with the same cards (from `person_activity`, paged). If nothing is
visible because the person is friends-only and not a friend, it shows "Only {display name}'s friends
can see their activity".

### Account screen

New switch **Share my reading activity**. Turning it off asks "Stop sharing? This removes everything
you've shared from the community." (Cancel / Stop sharing).

### Book screen and edit form

- A **Review** field labelled "Review (shared with friends)", shown only while signed in with a
  profile. **Notes** is relabelled "Notes (private)".
- A **Don't share this book** switch, shown while sharing is on.

## Errors and offline

Publishing never blocks or interrupts editing; failures only leave books pending. Feed, like and
likers errors use the existing `SocialException` kinds and messages. A like failure rolls back the
heart.

## Testing

- **pgTAP** (`supabase/tests/social_part2_test.sql`, CI workflow `supabase.yml`):
  - visibility for self, friend, stranger, public, friends-only, and blocked either way
  - each status mapping, and "before" (no false `finished`)
  - idempotent re-publish
  - collapse of `rated` and `progress` within 12 hours and not after
  - review add, edit and clear
  - no progress entry when finishing
  - `share_activity = false` is a no-op, and `set_share_activity(false)` deletes everything
  - another owner's ID is refused
  - validation errors and the cover allow-list
  - like: not own, not invisible, repeats ignored; `likers` hides blocked people
  - unpublish, unpublish_all, and account deletion cascade
  - the report snapshot of the review
  - no direct table access for `authenticated`
- **Kotlin** (commonTest; Robolectric where Room is needed):
  - ShareTracker: each public field, notes-only, excluded, deleted, another owner, sharing off, not
    signed in
  - Publisher with a fake `ActivityService` and a test dispatcher: debounce, retry after failure,
    the stop-on-failure order, clearing "before"
  - imports post nothing
  - `SupabaseActivityService` request shapes and error mapping with MockEngine
  - ActivityViewModel: paging, empty states, the like rollback
  - FeedBookSheet duplicate detection
  - the Room 2 → 3 migration and the backup round trip with the new fields
- The existing Android, iOS and Supabase CI workflows must pass.

## User setup

`docs/social-setup.md` gains a step: run `npx supabase db push` again from the MyBooks folder (with
`NODE_USE_SYSTEM_CA=1`) to apply `2026110200_social_part2.sql`.
