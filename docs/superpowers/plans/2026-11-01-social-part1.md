# MyBooks Social Part 1 (Accounts, Profiles, Friends, Privacy) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Optional community accounts on Supabase: email-code sign-in, profiles with visibility, mutual friends, search, block, report and in-app account deletion, on Android and iPhone.

**Architecture:** All rules live in one SQL migration (tables, RLS, security-definer functions) tested with pgTAP in GitHub Actions. The shared module gets a `social` package: models, four service interfaces, their Supabase implementations, and Compose screens with ViewModels tested against in-memory fakes. `AppContainer.social` is null when Supabase isn't configured, which hides every social entry point.

**Tech Stack:** Supabase (Postgres 15+, Auth email OTP, Storage), Supabase CLI via `npx supabase`, pgTAP; supabase-kt BOM 3.8.0 (`auth-kt`, `postgrest-kt`, `storage-kt`) on the existing Ktor engines; Compose Multiplatform.

**Spec:** `docs/superpowers/specs/2026-11-01-social-part1-accounts-friends-design.md`

## Global Constraints

- Branch `feature/social-accounts` (based on `feature/ios-kmp`). Local gate: `.superpowers/sdd/<plan>/ut.sh` equivalent: `./gradlew :shared:compileCommonMainKotlinMetadata :shared:testAndroidHostTest :androidApp:testDebugUnitTest :androidApp:assembleDebug`.
- Username format `^[a-z0-9_.]{3,20}$`; display name 1–50 chars; report details ≤ 1000 chars; search needs ≥ 2 chars after trim, 300 ms debounce, max 20 results.
- Visibility values `friends` (default) / `public`; report reasons `spam, harassment, inappropriate, impersonation, other`.
- Avatars: bucket `avatars`, object `<user id>/avatar.jpg`, shrunk to 512 px with the existing `PhotoShrinker`.
- Config keys: `SUPABASE_URL`, `SUPABASE_ANON_KEY` (local.properties → BuildConfig; GitHub secrets → Secrets.xcconfig → Info.plist `SupabaseUrl` / `SupabaseAnonKey`). Empty → social hidden, no Supabase calls.
- Never use or commit the service-role key. No new code in commonMain may use `java.*`/`android.*` (metadata compile is part of the gate).
- Commit messages end with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Review Focus

1. Usernames typed with capitals or spaces ("Mario Costa ") → trimmed and lower-cased; anything still invalid shows the format rule before any network call → Task 4 `usernameIsNormalizedAndValidatedLocally`.
2. Wrong or expired email code → inline "That code is wrong or has expired", code field kept, "Send a new code" available → Task 4 `wrongCodeShowsMessageAndAllowsResend`.
3. Both users send each other a request at the same time → they end up friends, not two pending rows → Task 1 SQL test `crossed_requests_become_friends`.
4. Opening the profile of someone who has since blocked you → "This profile isn't available", no crash, no actions → Task 7 `blockedProfileShowsNotAvailable`.
5. App killed after the code was verified but before choosing a username → next time the Account screen goes straight to the username step → Task 4 `signedInWithoutProfileGoesToUsernameStep`.

---

### Task 1: Database migration, pgTAP tests and CI

**Files:**
- Create: `supabase/config.toml` (from `npx supabase init`, project_id `mybooks`), `supabase/migrations/2026110100_social_part1.sql`, `supabase/tests/social_part1_test.sql`, `.github/workflows/supabase.yml`

**Interfaces — Produces** (callable via PostgREST `/rest/v1/rpc/<name>`):
- `send_friend_request(target uuid) returns friendship_status`
- `respond_to_friend_request(requester uuid, accept boolean) returns void`
- `block_user(target uuid) returns void`
- `search_profiles(query text, max int default 20) returns setof profiles`
- `delete_my_account() returns void`
- tables/columns exactly as in the spec's Database section; `is_blocked_between(a uuid, b uuid) returns boolean`.

- [ ] Step 1: Write `social_part1_test.sql` (pgTAP, `begin; select plan(n); … select * from finish(); rollback;`) creating users with `tests.create_supabase_user` style inserts into `auth.users` and switching identity via `set local role authenticated; set local request.jwt.claims = '{"sub":"<uuid>"}'`. Tests: `username_format_enforced` (`'Ab'`, `'has space'` rejected; `'mario.c_1'` ok), `username_unique`, `stranger_cannot_read_others_friendships`, `only_addressee_can_accept`, `crossed_requests_become_friends`, `decline_deletes_request`, `either_can_remove_friendship`, `block_removes_friendship_and_hides_profiles_both_ways`, `blocked_cannot_send_request`, `search_excludes_self_and_blocked_and_needs_2_chars`, `reports_insert_only_and_not_readable`, `cannot_report_self`, `delete_my_account_cascades` (profile, friendships, blocks gone; report keeps row with `reporter is null`).
- [ ] Step 2: Write `supabase.yml`: on push/PR touching `supabase/**`; ubuntu; `npx supabase start -x studio,imgproxy,inbucket,edge-runtime,logflare,vector,supavisor` then `npx supabase test db`, both via `.github/scripts/annotate.sh`. Push → Expected: FAIL (migration missing).
- [ ] Step 3: Write the migration per the spec (types, tables, index, RLS policies table, functions with `security definer set search_path = public`, `revoke all … from anon`, `grant execute … to authenticated`; storage bucket `avatars` public + policies on `storage.objects` using `(storage.foldername(name))[1] = auth.uid()::text`; `delete_my_account` also deletes `storage.objects` where `bucket_id='avatars' and owner = auth.uid()`).
- [ ] Step 4: Push → Expected: supabase workflow green, all tests `ok`. Commit `feat(db): social part 1 schema, RLS and functions`.

### Task 2: Social models, service interfaces and fakes

**Files:** Create `shared/src/commonMain/kotlin/com/mcosta/mybooks/social/model/Models.kt`, `social/Services.kt`, `social/Usernames.kt`; Test `shared/src/commonTest/kotlin/com/mcosta/mybooks/social/FakeSocial.kt`, `social/UsernamesTest.kt`.

**Interfaces — Produces:**
```kotlin
enum class Visibility { FRIENDS, PUBLIC }
enum class Relationship { NONE, FRIENDS, REQUEST_SENT, REQUEST_RECEIVED }
enum class ReportReason { SPAM, HARASSMENT, INAPPROPRIATE, IMPERSONATION, OTHER }
data class Profile(val id: String, val username: String, val displayName: String, val avatarUrl: String?, val visibility: Visibility)
data class Person(val id: String, val username: String, val displayName: String, val avatarUrl: String?)
data class SearchResult(val person: Person, val relationship: Relationship)
sealed interface SessionState { data object Loading; data object SignedOut; data class SignedIn(val userId: String, val email: String?) }
class SocialException(val kind: Kind, message: String, cause: Throwable? = null) : Exception(message, cause) {
    enum class Kind { OFFLINE, INVALID_CODE, USERNAME_TAKEN, NOT_AVAILABLE, OTHER }
}
interface AuthService { val session: StateFlow<SessionState>; suspend fun sendCode(email: String); suspend fun verifyCode(email: String, code: String); suspend fun signOut() }
interface ProfileService {
    suspend fun myProfile(): Profile?; suspend fun isUsernameAvailable(username: String): Boolean
    suspend fun createProfile(username: String, displayName: String): Profile
    suspend fun updateProfile(displayName: String, visibility: Visibility): Profile
    suspend fun uploadAvatar(jpeg: ByteArray): Profile; suspend fun person(userId: String): Person?; suspend fun deleteAccount()
}
interface FriendsService {
    suspend fun friends(): List<Person>; suspend fun incomingRequests(): List<Person>; suspend fun outgoingRequests(): List<Person>
    suspend fun search(query: String): List<SearchResult>; suspend fun relationship(userId: String): Relationship
    suspend fun sendRequest(userId: String): Relationship; suspend fun respond(requesterId: String, accept: Boolean)
    suspend fun cancelRequest(userId: String); suspend fun removeFriend(userId: String)
}
interface SafetyService { suspend fun block(userId: String); suspend fun unblock(userId: String); suspend fun blockedPeople(): List<Person>; suspend fun report(userId: String, reason: ReportReason, details: String?) }
class SocialServices(val auth: AuthService, val profiles: ProfileService, val friends: FriendsService, val safety: SafetyService)
fun normalizeUsername(raw: String): String          // trim + lowercase
fun usernameProblem(username: String): String?      // null if valid; else "3–20 characters: a–z, 0–9, _ and ."
fun socialMessage(e: Throwable): String              // OFFLINE→"You're offline", INVALID_CODE→"That code is wrong or has expired", USERNAME_TAKEN→"That username is taken", NOT_AVAILABLE→"This profile isn't available", else "Something went wrong, please try again"
```
`FakeSocial` (commonTest): one in-memory world with several users, implementing all four interfaces with the same rules as the SQL (mutual accept on crossed requests, blocks hide both ways), plus `var offline = false` that makes every call throw `SocialException(OFFLINE)`.

- [ ] Step 1: `UsernamesTest`: `normalizeUsername(" Mario.C ") == "mario.c"`; `usernameProblem("ab")`, `("has space")`, `("x".repeat(21))` non-null; `usernameProblem("mario_c.1") == null`; `socialMessage` mapping for each kind. Run → FAIL. Step 2: implement. Step 3: gate → PASS. Commit.

### Task 3: Supabase implementations and configuration

**Files:** Create `social/supabase/SupabaseSocial.kt` (client + the four implementations), `social/supabase/Rows.kt` (`@Serializable` row DTOs + mappers), `social/SocialConfig.kt`; Modify `gradle/libs.versions.toml`, `shared/build.gradle.kts` (commonMain: supabase BOM 3.8.0 `auth-kt`, `postgrest-kt`, `storage-kt`), `AppContainer.kt` (`val social: SocialServices?`), `AndroidAppContainer.kt`, `androidApp/build.gradle.kts` (BuildConfig `SUPABASE_URL`, `SUPABASE_ANON_KEY`), `MainViewController.kt` (Info.plist keys), `iosApp/project.yml` (Info.plist `SupabaseUrl: $(SUPABASE_URL)`, `SupabaseAnonKey: $(SUPABASE_ANON_KEY)`), `.github/workflows/ios.yml` (two more secrets into Secrets.xcconfig). Test `commonTest/social/supabase/SupabaseSocialTest.kt`.

**Interfaces — Consumes:** Task 1 RPC names; Task 2 interfaces. **Produces:** `data class SocialConfig(val url: String, val anonKey: String) { val enabled: Boolean }`; `fun createSocialServices(config: SocialConfig, engine: HttpClientEngine): SocialServices` (builds `createSupabaseClient` with `httpEngine = engine`, installs Auth (OTP via `signInWith(OTP)` / `verifyEmailOtp(type = OtpType.Email.EMAIL)`), Postgrest, Storage). `AppContainer(…, socialConfig: SocialConfig)` → `social = if (config.enabled) createSocialServices(config, httpEngine) else null`.

Error mapping (one function `mapSocialError(e: Throwable): SocialException`): Ktor/IO failures → OFFLINE; Auth "otp_expired"/"invalid" → INVALID_CODE; Postgrest code `23505` on `profiles_username_key` → USERNAME_TAKEN; empty result for a person/relationship lookup → NOT_AVAILABLE.

- [ ] Step 1: `SupabaseSocialTest` with Ktor `MockEngine` (base `https://test.supabase.co`): `sendRequestCallsRpc` (POST `/rest/v1/rpc/send_friend_request`, JSON body `{"target":"<id>"}`, returns `"accepted"` → `Relationship.FRIENDS`); `searchMapsRows` (rpc `search_profiles` body `{"query":"ma","max":20}`; rows → `SearchResult` with relationship from a second `friendships` select); `usernameTakenMapsTo409` (409 with code `23505` → `USERNAME_TAKEN`); `networkFailureIsOffline`; `avatarUrlBuiltFromPath` (`<url>/storage/v1/object/public/avatars/<id>/avatar.jpg`); `disabledConfigMeansNoSocial` (`SocialConfig("", "").enabled == false`). Run → FAIL.
- [ ] Step 2: Implement; wire config on both platforms. Step 3: gate → PASS (+ push: iOS workflow green). Commit.

### Task 4: Sign-in flow (email → code → username)

**Files:** Create `ui/social/SignInViewModel.kt`, `ui/social/SignInScreen.kt`; Test `commonTest/ui/social/SignInViewModelTest.kt`.

**Interfaces — Produces:** `SignInViewModel(auth: AuthService, profiles: ProfileService)`, `state: StateFlow<SignInState>` with `data class SignInState(val step: Step, val email: String = "", val code: String = "", val username: String = "", val displayName: String = "", val busy: Boolean = false, val error: String? = null, val usernameAvailable: Boolean? = null)`, `enum class Step { EMAIL, CODE, USERNAME, DONE }`; actions `setEmail`, `sendCode()`, `setCode`, `verify()`, `resend()`, `setUsername` (normalizes, debounced 300 ms availability check), `setDisplayName`, `createProfile()`. Initial step: `SignedIn` without profile → `USERNAME`; `SignedIn` with profile → `DONE`; otherwise `EMAIL`.

- [ ] Step 1: Tests (FakeSocial + `MainDispatcherTest`, virtual time): `emailThenCodeThenUsernameCreatesProfile`, `invalidEmailRejectedLocally` ("name@" → "Enter a valid email"), `wrongCodeShowsMessageAndAllowsResend`, `usernameIsNormalizedAndValidatedLocally`, `takenUsernameShowsMessage`, `signedInWithoutProfileGoesToUsernameStep`, `offlineShowsOfflineMessage`. Run → FAIL. Step 2: implement VM and screen (email keyboard, 6-digit numeric code field, username field showing availability). Step 3: gate → PASS. Commit.

### Task 5: Account section (profile, avatar, visibility, blocked, sign out, delete)

**Files:** Create `ui/social/AccountViewModel.kt`, `ui/social/AccountScreen.kt`; Modify `ui/settings/SettingsScreen.kt` (an "Account" row: "Join the MyBooks community" when signed out, else "@username"); Test `commonTest/ui/social/AccountViewModelTest.kt`.

**Interfaces — Produces:** `AccountViewModel(services: SocialServices, shrinker: PhotoShrinker)`, `state: StateFlow<AccountState>` (`profile: Profile?`, `blocked: List<Person>`, `busy`, `message: String?`, `deleted: Boolean`); actions `saveDisplayName(String)`, `setVisibility(Visibility)`, `setAvatar(photo: ByteArray)` (shrinks to 512 px on `Dispatchers.Default`, then `uploadAvatar`), `unblock(userId)`, `signOut()`, `deleteAccount()`.

- [ ] Step 1: Tests: `loadsProfileAndBlocked`, `visibilityChangeIsSaved`, `blankDisplayNameRejected` ("Enter a name"), `avatarIsShrunkTo512BeforeUpload` (fake shrinker records `maxSide == 512`), `unblockRemovesFromList`, `deleteAccountSignsOutAndKeepsLibrary` (repository book count unchanged; session `SignedOut`). Run → FAIL. Step 2: implement VM and screen (delete confirmation text: "Delete your MyBooks community account? Your profile, friends and requests are removed for good. The books on this phone stay."). Step 3: gate → PASS. Commit.

### Task 6: Friends screen (Friends / Requests / Find people)

**Files:** Create `ui/social/FriendsViewModel.kt`, `ui/social/FriendsScreen.kt`, `ui/social/PersonRow.kt` (avatar via `BookCover`-style Coil image with person icon placeholder); Test `commonTest/ui/social/FriendsViewModelTest.kt`.

**Interfaces — Produces:** `FriendsViewModel(services: SocialServices)`, `state: StateFlow<FriendsState>` (`signedIn`, `friends`, `incoming`, `outgoing`, `query`, `results: List<SearchResult>`, `loading`, `message`); actions `refresh()`, `setQuery(String)` (debounce 300 ms, ≥ 2 chars), `accept(id)`, `decline(id)`, `cancel(id)`, `add(id)`.

- [ ] Step 1: Tests: `signedOutShowsSignInPrompt`, `acceptMovesPersonToFriends`, `declineRemovesRequest`, `cancelRemovesOutgoing`, `searchWaitsForTwoCharsAndDebounces` (one call after typing "m","ma","mar" within 300 ms), `searchResultLabelsFollowRelationship`, `offlineKeepsLastListsAndShowsRetry`. Run → FAIL. Step 2: implement VM and screen (tab badge = incoming count). Step 3: gate → PASS. Commit.

### Task 7: Profile screen (actions, block, report)

**Files:** Create `ui/social/ProfileViewModel.kt`, `ui/social/ProfileScreen.kt`, `ui/social/ReportDialog.kt`; Test `commonTest/ui/social/ProfileViewModelTest.kt`.

**Interfaces — Produces:** `ProfileViewModel(services: SocialServices, userId: String)`, `state` (`person: Person?`, `relationship`, `notAvailable: Boolean`, `busy`, `message`, `left: Boolean`); actions `add()`, `cancel()`, `accept()`, `decline()`, `removeFriend()`, `block()` (sets `left = true`), `report(reason, details)` (details trimmed, ≤ 1000 chars; message "Thanks, we'll look into it").

- [ ] Step 1: Tests: `actionsFollowRelationship`, `blockLeavesScreen`, `reportSendsReasonAndTrimmedDetails`, `reportDetailsLimitedTo1000`, `blockedProfileShowsNotAvailable`. Run → FAIL. Step 2: implement VM, screen and dialog. Step 3: gate → PASS. Commit.

### Task 8: Navigation, entry points and setup docs

**Files:** Modify `ui/MyBooksNavHost.kt` (routes `friends`, `profile/{id}`, `account`, `signin`), `ui/library/LibraryScreen.kt` (Friends icon `Icons.Filled.Group` in the top bar, only when `container.social != null`), `README.md`; Create `docs/social-setup.md` (spec's User setup steps, plus GitHub secrets `SUPABASE_URL`/`SUPABASE_ANON_KEY` and the email template change); Test `commonTest/ui/social/SocialEntryPointsTest.kt`.

- [ ] Step 1: Test `socialHiddenWhenNotConfigured` (an `AppContainer` built with `SocialConfig("", "")` has `social == null`) and `socialShownWhenConfigured`. Run → FAIL if wiring missing. Step 2: implement routes/entry points; Settings "Account" row hidden when social is null. Step 3: gate → PASS; push → Android + iOS + Supabase workflows green. Commit `feat: social entry points and setup guide`.
