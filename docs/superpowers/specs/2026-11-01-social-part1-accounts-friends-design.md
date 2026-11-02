# MyBooks Social, Part 1: Accounts, Profiles, Friends and Privacy

**Date:** 2026-11-01
**Status:** Approved design, pending spec review
**Builds on:** `feature/ios-kmp` (Kotlin Multiplatform shared module; Android + iOS)
**Followed by:** Part 2: activity publishing, friends' feed, reviews (separate spec)

## Purpose

Turn MyBooks into a public social network where anyone who installs the app can create an account,
find other readers, become friends, and (in Part 2) see each other's reading activity. Part 1 delivers
the foundation: accounts, profiles, the friend graph, privacy settings and the safety features app
stores require (block, report, in-app account deletion).

### Decisions taken with the user

- Audience: **anyone who installs the app** (public network, not invite-only).
- Friendship is **mutual** (request → accept).
- Activity visibility is **chosen per user**: `friends` (default) or `public`. Enforced from Part 2.
- Only **shared activity** goes to the server (Part 2); the personal library stays local on the phone.
- Backend: **Supabase** (hosted Postgres + Auth + Storage), rules as SQL in this repo.
- Sign-in in Part 1: **email one-time code** only. Google and Sign in with Apple are a later add-on.

### Success criteria

1. Without an account, the app behaves exactly as today (no prompts, no network calls to Supabase).
2. A user can sign up / sign in with an email code, choose a unique username and display name, and
   optionally upload an avatar.
3. A signed-in user can search people by username or display name, send a friend request, and the
   other user can accept or decline it; either can later remove the friendship.
4. A user can block another user (removes any friendship and pending requests, hides each from the
   other's search and profile views, prevents new requests) and unblock them.
5. A user can report another user with a reason; reports are visible only to the project owner
   (Supabase dashboard).
6. A user can set their visibility (friends / public) and delete their account from the app; deletion
   removes their profile, friendships, blocks they made, and avatar; the local library is untouched.
7. All of the above is enforced by Postgres row-level security and database functions, verified by
   automated SQL tests in CI, and works identically on Android and iPhone.

### Out of scope (Part 1)

Activity feed, reviews, publishing events (Part 2); Google / Apple sign-in; push notifications;
moderation tooling beyond the Supabase dashboard; library sync; profile pages showing books.

## Architecture

```
shared/commonMain
  social/
    SocialConfig.kt          url + anon key (empty → social features hidden)
    SocialClient.kt          creates the SupabaseClient (Auth, Postgrest, Storage)
    AuthService.kt           interface: session state, sendCode(email), verifyCode(email, code), signOut()
    ProfileService.kt        interface: myProfile, createProfile, updateProfile, uploadAvatar, deleteAccount
    FriendsService.kt        interface: friends, incoming/outgoing requests, search, request/accept/decline/cancel/remove
    SafetyService.kt         interface: block, unblock, blockedUsers, report
    supabase/                Supabase implementations of the four interfaces
    model/                   Profile, Visibility, FriendRequest, PersonSummary, ReportReason
  ui/social/                 FriendsScreen (3 tabs), ProfileScreen, AccountScreen, SignInScreen
                             and one ViewModel per screen
supabase/
  config.toml                Supabase CLI project config (local dev/test)
  migrations/2026110100_social_part1.sql
  tests/social_part1_test.sql   pgTAP tests
```

- `AppContainer` gains an optional `social: SocialServices?`, null when the URL/key are not configured
  (tests, forks, missing secrets). The UI hides the Friends icon and the Account section in that case.
- The four service interfaces are what the ViewModels depend on; commonTest uses in-memory fakes.
- Library: `io.github.jan-tennert.supabase` BOM **3.8.0** (`auth-kt`, `postgrest-kt`, `storage-kt`),
  Ktor engines already present (OkHttp on Android, Darwin on iOS). Session persistence uses the
  library's default multiplatform settings storage.

## Configuration

| Name | Android | iOS / CI |
|---|---|---|
| `SUPABASE_URL` | `local.properties` → BuildConfig | GitHub secret → Secrets.xcconfig → Info.plist `SupabaseUrl` |
| `SUPABASE_ANON_KEY` | `local.properties` → BuildConfig | GitHub secret → Secrets.xcconfig → Info.plist `SupabaseAnonKey` |

The anon key is public by design; row-level security protects data. The service-role key is never
used by the app or committed.

Email: Supabase Auth "Email OTP" with a 6-digit code (magic-link template changed to show
`{{ .Token }}`), so no deep links are needed in Part 1.

## Database (migration `2026110100_social_part1.sql`)

```sql
create type visibility as enum ('friends', 'public');
create type friendship_status as enum ('pending', 'accepted');
create type report_reason as enum ('spam', 'harassment', 'inappropriate', 'impersonation', 'other');

create table profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  username text not null unique check (username ~ '^[a-z0-9_.]{3,20}$'),
  display_name text not null check (char_length(display_name) between 1 and 50),
  avatar_path text,                       -- object path in storage bucket 'avatars'
  visibility visibility not null default 'friends',
  created_at timestamptz not null default now()
);

create table friendships (
  requester uuid not null references profiles(id) on delete cascade,
  addressee uuid not null references profiles(id) on delete cascade,
  status friendship_status not null default 'pending',
  created_at timestamptz not null default now(),
  responded_at timestamptz,
  primary key (requester, addressee),
  check (requester <> addressee)
);
create unique index friendships_pair on friendships (least(requester, addressee), greatest(requester, addressee));

create table blocks (
  blocker uuid not null references profiles(id) on delete cascade,
  blocked uuid not null references profiles(id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (blocker, blocked),
  check (blocker <> blocked)
);

create table reports (
  id bigint generated always as identity primary key,
  reporter uuid references profiles(id) on delete set null,
  reported uuid not null references profiles(id) on delete cascade,
  reason report_reason not null,
  details text check (char_length(details) <= 1000),
  created_at timestamptz not null default now()
);
```

Helper: `is_blocked_between(a, b)`: true if either blocked the other (`security definer`, stable).

### Row-level security (all four tables have RLS enabled)

| Table | select | insert | update | delete |
|---|---|---|---|---|
| profiles | authenticated, and not `is_blocked_between(auth.uid(), id)` | `id = auth.uid()` | own row (`id = auth.uid()`), `id` immutable | none (use `delete_my_account`) |
| friendships | `auth.uid() in (requester, addressee)` | none (use `send_friend_request`) | none (use `respond_to_friend_request`) | `auth.uid() in (requester, addressee)` |
| blocks | `blocker = auth.uid()` | none (use `block_user`) | none | `blocker = auth.uid()` |
| reports | none | `reporter = auth.uid()` and `reported <> auth.uid()` | none | none |

### Database functions (`security definer`, `set search_path = public`, granted to `authenticated`)

- `send_friend_request(target uuid)`: rejects self, blocked pairs, unknown users. If the target already
  sent me a pending request, it is accepted instead (both asked = friends). If a pending or accepted
  row already exists, it's a no-op returning the current status.
- `respond_to_friend_request(requester uuid, accept boolean)`: only the addressee of a pending
  request; accept → `accepted` + `responded_at`, decline → row deleted.
- `block_user(target uuid)`: inserts the block and deletes any friendship row between the pair
  (pending or accepted).
- `search_profiles(query text, max int default 20)`: case-insensitive prefix/contains match on
  username and display name, excluding self and blocked pairs; trims the query, requires ≥ 2 chars.
- `delete_my_account()`: deletes `auth.users` row for `auth.uid()` (cascades to profile, friendships,
  blocks; reports by the user keep `reporter = null`), and the user's avatar objects.

Storage: bucket `avatars` (public read), objects at `<user id>/avatar.jpg`; policies allow insert /
update / delete only under the caller's own `<user id>/` prefix. Avatars are shrunk with the existing
`PhotoShrinker` to 512 px before upload.

## App behaviour

### Sign-in flow (Settings → Account → "Join the MyBooks community")
1. Enter email → `sendCode`. 2. Enter the 6-digit code → `verifyCode`. 3. If no profile exists yet:
choose username (live availability check; lowercase enforced) and display name → `createProfile`.
Errors are shown inline: invalid/expired code, username taken, invalid username format, network.

### Friends screen (top bar "Friends" icon; only when social is configured)
- Signed out: an explanation and a "Sign in" button leading to the sign-in flow.
- **Friends** tab: accepted friends (avatar, display name, @username) → Profile screen.
- **Requests** tab: incoming (Accept / Decline) and sent (Cancel). A badge on the tab shows the
  incoming count.
- **Find people** tab: search field (≥ 2 chars, 300 ms debounce) → results with the relationship
  shown (Friends / Request sent / Respond / Add friend).

### Profile screen (another user)
Avatar, display name, @username, and the action matching the relationship: Add friend / Cancel
request / Accept + Decline / Remove friend (with confirmation). Overflow: Block (confirmation; leaves
the screen) and Report (reason picker + optional details, confirmation message).

### Account section (Settings)
Signed in: avatar (tap → camera, via the existing `CoverCamera` service; shrunk and uploaded), display
name, visibility (Friends only / Public, with a one-line explanation that it applies to activity from
Part 2), Blocked people (list with Unblock), Sign out, Delete account (confirmation dialog stating
that the community account is deleted and the library on this phone is kept).

### Errors and offline
Every network call reports a user-facing message through the screen's snackbar ("You're offline",
"That username is taken", "Something went wrong, please try again"). Nothing in the library depends
on the network; social screens show their last loaded lists while offline and a retry action.

## Testing

- **SQL (pgTAP) in CI**: `supabase start` + `supabase test db` on the ubuntu runner (Docker). Cases:
  stranger cannot read/modify others' friendships or blocks; only addressee can accept; mutual requests
  become accepted; blocking deletes friendship and hides profiles both ways; blocked users can't send
  requests; search excludes self and blocked; reports are insert-only and not readable; username
  format and uniqueness; `delete_my_account` removes the user and cascades.
- **commonTest**: ViewModels for sign-in (code flow, username validation and errors), friends tabs
  (state after accept/decline/cancel/remove), search (debounce, min length, relationship labels),
  profile actions, account (visibility change, delete confirmation) against in-memory fakes of the
  four services; mapping between Supabase rows and models.
- **Manual on devices**: real email code sign-in on Android and iPhone, request/accept between two
  accounts, block/report, delete account.

## User setup (documented in `docs/social-setup.md`)

1. Create a free Supabase project (region EU, e.g. Frankfurt).
2. Auth → Email: enable Email OTP; set the email template to show the code.
3. Apply the migration: `npx supabase link --project-ref <ref>` then `npx supabase db push`.
4. Put `SUPABASE_URL` / `SUPABASE_ANON_KEY` in `local.properties` and as GitHub secrets.
5. Before opening the app to the public: configure a custom SMTP sender (Supabase's built-in email is
   limited to a few messages per hour) and publish a short privacy policy URL (required by the stores).
