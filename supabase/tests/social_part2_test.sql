-- pgTAP tests for the social part 2 schema (shared books, activity feed, likes).
begin;
create extension if not exists pgtap with schema extensions;
select * from no_plan();

-- ana and bruno are friends (friends-only); carla and eve are public; dora is friends-only.
-- eve blocked ana; dora blocked carla.
insert into auth.users (id, email, aud, role) values
  ('11111111-1111-1111-1111-111111111111', 'ana@test.local', 'authenticated', 'authenticated'),
  ('22222222-2222-2222-2222-222222222222', 'bruno@test.local', 'authenticated', 'authenticated'),
  ('33333333-3333-3333-3333-333333333333', 'carla@test.local', 'authenticated', 'authenticated'),
  ('44444444-4444-4444-4444-444444444444', 'dora@test.local', 'authenticated', 'authenticated'),
  ('55555555-5555-5555-5555-555555555555', 'eve@test.local', 'authenticated', 'authenticated');
insert into profiles (id, username, display_name, visibility) values
  ('11111111-1111-1111-1111-111111111111', 'ana', 'Ana', 'friends'),
  ('22222222-2222-2222-2222-222222222222', 'bruno', 'Bruno', 'friends'),
  ('33333333-3333-3333-3333-333333333333', 'carla', 'Carla', 'public'),
  ('44444444-4444-4444-4444-444444444444', 'dora', 'Dora', 'friends'),
  ('55555555-5555-5555-5555-555555555555', 'eve', 'Eve', 'public');
insert into friendships (requester, addressee, status) values
  ('11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222', 'accepted');
insert into blocks (blocker, blocked) values
  ('55555555-5555-5555-5555-555555555555', '11111111-1111-1111-1111-111111111111'),
  ('44444444-4444-4444-4444-444444444444', '33333333-3333-3333-3333-333333333333');

create function pg_temp.act_as(uid uuid) returns void language sql as $$
  select set_config('request.jwt.claims', json_build_object('sub', uid::text, 'role', 'authenticated')::text, true);
$$;
create function pg_temp.book(id text, status text, rating int default null, progress int default null,
  review text default '', title text default 'Book', cover text default null) returns jsonb language sql as $$
  select jsonb_build_object('id', id, 'isbn', '9789726084884', 'title', title, 'authors', 'Author',
    'cover_url', cover, 'status', status, 'rating', rating, 'progress', progress, 'review', review);
$$;
create function pg_temp.before(status text, rating int default null, progress int default null, review text default '')
  returns jsonb language sql as $$
  select jsonb_build_object('status', status, 'rating', rating, 'progress', progress, 'review', review);
$$;
-- The caller's own entries for one book, oldest first (people always see their own activity).
create function pg_temp.kinds(book uuid) returns text[] language sql as $$
  select coalesce(array_agg(kind::text order by id), '{}') from person_activity(auth.uid(), null, null, 50) where book_id = book;
$$;

set local role authenticated;

-- status_mapping / null_before_means_new_book: each first publish of a new book adds its status entry.
select pg_temp.act_as('11111111-1111-1111-1111-111111111111');
select lives_ok($$select publish_book(pg_temp.book('aaaaaaaa-0000-0000-0000-000000000001', 'to_read'))$$, 'ana publishes A1');
select is(pg_temp.kinds('aaaaaaaa-0000-0000-0000-000000000001'), array['want_to_read'], 'null_before_means_new_book / status_mapping: to_read');
select pg_temp.act_as('22222222-2222-2222-2222-222222222222');
select publish_book(pg_temp.book('bbbbbbbb-0000-0000-0000-000000000001', 'reading'));
select is(pg_temp.kinds('bbbbbbbb-0000-0000-0000-000000000001'), array['started'], 'status_mapping: reading');
select pg_temp.act_as('33333333-3333-3333-3333-333333333333');
select publish_book(pg_temp.book('cccccccc-0000-0000-0000-000000000001', 'read'));
select is(pg_temp.kinds('cccccccc-0000-0000-0000-000000000001'), array['finished'], 'status_mapping: read');
select pg_temp.act_as('44444444-4444-4444-4444-444444444444');
select publish_book(pg_temp.book('dddddddd-0000-0000-0000-000000000001', 'to_buy'));
select is(pg_temp.kinds('dddddddd-0000-0000-0000-000000000001'), array['want_to_buy'], 'status_mapping: to_buy');
select set_config('t.d1', (select id::text from person_activity(auth.uid()) limit 1), true);
select pg_temp.act_as('55555555-5555-5555-5555-555555555555');
select publish_book(pg_temp.book('eeeeeeee-0000-0000-0000-000000000001', 'to_read'));

-- Visibility, as ana.
select pg_temp.act_as('11111111-1111-1111-1111-111111111111');
select ok(exists (select 1 from feed('friends') where book_id = 'aaaaaaaa-0000-0000-0000-000000000001'), 'self_sees_own_entries');
select ok(exists (select 1 from feed('friends') where book_id = 'bbbbbbbb-0000-0000-0000-000000000001'), 'friend_sees_friends_only_user: feed');
select ok(exists (select 1 from person_activity('22222222-2222-2222-2222-222222222222')), 'friend_sees_friends_only_user: profile');
select ok(not exists (select 1 from feed('friends') where actor_id = '44444444-4444-4444-4444-444444444444')
  and not exists (select 1 from feed('everyone') where actor_id = '44444444-4444-4444-4444-444444444444'),
  'stranger_does_not_see_friends_only_user: feeds');
select is_empty($$select 1 from person_activity('44444444-4444-4444-4444-444444444444')$$, 'stranger_does_not_see_friends_only_user: profile');
select ok(exists (select 1 from feed('everyone') where book_id = 'cccccccc-0000-0000-0000-000000000001'), 'stranger_sees_public_user_in_everyone');
select ok(exists (select 1 from person_activity('33333333-3333-3333-3333-333333333333')), 'stranger_sees_public_user: profile');
select ok(not exists (select 1 from feed('friends') where book_id = 'cccccccc-0000-0000-0000-000000000001'), 'public stranger not in friends tab');
select ok(not exists (select 1 from feed('everyone') where actor_id in
  ('11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222')), 'everyone_excludes_friends_only_users');
select ok(not exists (select 1 from feed('everyone') where actor_id = '55555555-5555-5555-5555-555555555555'), 'blocked_hidden_both_ways: blocked viewer, feed');
select is_empty($$select 1 from person_activity('55555555-5555-5555-5555-555555555555')$$, 'blocked_hidden_both_ways: blocked viewer, profile');
select pg_temp.act_as('44444444-4444-4444-4444-444444444444');
select ok(not exists (select 1 from feed('everyone') where actor_id = '33333333-3333-3333-3333-333333333333'), 'blocked_hidden_both_ways: blocker viewer, feed');
select is_empty($$select 1 from person_activity('33333333-3333-3333-3333-333333333333')$$, 'blocked_hidden_both_ways: blocker viewer, profile');

-- Likes on carla's C1 entry.
select pg_temp.act_as('33333333-3333-3333-3333-333333333333');
select set_config('t.c1', (select id::text from person_activity(auth.uid()) where book_id = 'cccccccc-0000-0000-0000-000000000001'), true);
select throws_ok($$select "like"(current_setting('t.c1')::bigint)$$, 'P0002', null, 'like_own_refused');
select pg_temp.act_as('11111111-1111-1111-1111-111111111111');
select throws_ok($$select "like"(current_setting('t.d1')::bigint)$$, 'P0002', null, 'like_invisible_refused');
select lives_ok($$select "like"(current_setting('t.c1')::bigint)$$, 'ana likes C1');
select lives_ok($$select "like"(current_setting('t.c1')::bigint)$$, 'ana likes C1 again');
select is((select like_count from feed('everyone') where id = current_setting('t.c1')::bigint), 1, 'like_twice_counts_once');
select ok((select liked_by_me from feed('everyone') where id = current_setting('t.c1')::bigint), 'liked_by_me');
select pg_temp.act_as('55555555-5555-5555-5555-555555555555');
select lives_ok($$select "like"(current_setting('t.c1')::bigint)$$, 'eve likes C1');
select is((select array_agg(username order by username) from likers(current_setting('t.c1')::bigint)), array['eve'],
  'blocked_hidden_both_ways: likers hides ana from eve');
select pg_temp.act_as('11111111-1111-1111-1111-111111111111');
select is((select array_agg(username order by username) from likers(current_setting('t.c1')::bigint)), array['ana'],
  'blocked_hidden_both_ways: likers hides eve from ana');
select pg_temp.act_as('33333333-3333-3333-3333-333333333333');
select is((select array_agg(username order by username) from likers(current_setting('t.c1')::bigint)), array['ana', 'eve'], 'owner sees all likers');
select pg_temp.act_as('44444444-4444-4444-4444-444444444444');
select is_empty($$select 1 from likers(current_setting('t.c1')::bigint)$$, 'likers empty when the entry is invisible');
select pg_temp.act_as('11111111-1111-1111-1111-111111111111');
select lives_ok($$select unlike(current_setting('t.c1')::bigint)$$, 'ana unlikes C1');
select pg_temp.act_as('33333333-3333-3333-3333-333333333333');
select is((select like_count from person_activity(auth.uid()) where id = current_setting('t.c1')::bigint), 1, 'unlike_removes');

-- Entry rules, on ana's books.
select pg_temp.act_as('11111111-1111-1111-1111-111111111111');
select publish_book(pg_temp.book('aaaaaaaa-0000-0000-0000-000000000002', 'read', rating => 4), pg_temp.before('read'));
select is(pg_temp.kinds('aaaaaaaa-0000-0000-0000-000000000002'), array['rated'], 'before_prevents_false_finished');
select publish_book(pg_temp.book('aaaaaaaa-0000-0000-0000-000000000002', 'read', rating => 4), pg_temp.before('read'));
select is(pg_temp.kinds('aaaaaaaa-0000-0000-0000-000000000002'), array['rated'], 'republish_same_state_creates_nothing');
select publish_book(pg_temp.book('aaaaaaaa-0000-0000-0000-000000000002', 'read', rating => 4, title => 'New title'));
select is(pg_temp.kinds('aaaaaaaa-0000-0000-0000-000000000002'), array['rated'], 'title_change_adds_nothing');
select publish_book(pg_temp.book('aaaaaaaa-0000-0000-0000-000000000002', 'read', rating => 4, review => 'Great'));
select is(pg_temp.kinds('aaaaaaaa-0000-0000-0000-000000000002'), array['rated', 'reviewed'], 'review_add_edit_clear: add');
select publish_book(pg_temp.book('aaaaaaaa-0000-0000-0000-000000000002', 'read', rating => 4, review => 'Great!!'));
select is(pg_temp.kinds('aaaaaaaa-0000-0000-0000-000000000002'), array['rated', 'reviewed'], 'review_add_edit_clear: edit adds nothing');
select is((select review from feed('friends') where book_id = 'aaaaaaaa-0000-0000-0000-000000000002' and kind = 'reviewed'), 'Great!!',
  'review_add_edit_clear: card shows current review');
select is((select review from feed('friends') where book_id = 'aaaaaaaa-0000-0000-0000-000000000002' and kind = 'rated'), null,
  'review only on reviewed entries');
select publish_book(pg_temp.book('aaaaaaaa-0000-0000-0000-000000000002', 'read', rating => 4, review => ''));
select is(pg_temp.kinds('aaaaaaaa-0000-0000-0000-000000000002'), array['rated'], 'review_add_edit_clear: clear');

select publish_book(pg_temp.book('aaaaaaaa-0000-0000-0000-000000000003', 'reading', progress => 10), pg_temp.before('reading'));
select publish_book(pg_temp.book('aaaaaaaa-0000-0000-0000-000000000003', 'reading', progress => 20));
select is(pg_temp.kinds('aaaaaaaa-0000-0000-0000-000000000003'), array['progress'], 'progress_collapses_within_12h: one entry');
select is((select progress from feed('friends') where book_id = 'aaaaaaaa-0000-0000-0000-000000000003'), 20, 'progress_collapses_within_12h: latest value');
reset role;
update activities set created_at = now() - interval '13 hours' where book = 'aaaaaaaa-0000-0000-0000-000000000003';
set local role authenticated;
select pg_temp.act_as('11111111-1111-1111-1111-111111111111');
select publish_book(pg_temp.book('aaaaaaaa-0000-0000-0000-000000000003', 'reading', progress => 30));
select is(pg_temp.kinds('aaaaaaaa-0000-0000-0000-000000000003'), array['progress', 'progress'], 'progress_not_collapsed_after_12h');
select publish_book(pg_temp.book('aaaaaaaa-0000-0000-0000-000000000003', 'read', progress => 100));
select is(pg_temp.kinds('aaaaaaaa-0000-0000-0000-000000000003'), array['progress', 'progress', 'finished'], 'finishing_adds_no_progress_entry');

-- Validation and covers.
select throws_ok($$select publish_book(pg_temp.book('aaaaaaaa-0000-0000-0000-000000000009', 'read', title => ''))$$, '22023', null, 'validation_errors: empty title');
select throws_ok($$select publish_book(pg_temp.book('aaaaaaaa-0000-0000-0000-000000000009', 'read', title => repeat('x', 301)))$$, '22023', null, 'validation_errors: long title');
select throws_ok($$select publish_book(pg_temp.book('aaaaaaaa-0000-0000-0000-000000000009', 'read', rating => 6))$$, '22023', null, 'validation_errors: rating');
select throws_ok($$select publish_book(pg_temp.book('aaaaaaaa-0000-0000-0000-000000000009', 'reading', progress => 101))$$, '22023', null, 'validation_errors: progress');
select throws_ok($$select publish_book(pg_temp.book('aaaaaaaa-0000-0000-0000-000000000009', 'read', review => repeat('x', 2001)))$$, '22023', null, 'validation_errors: review');
select throws_ok($$select publish_book(pg_temp.book('aaaaaaaa-0000-0000-0000-000000000009', 'shelved'))$$, '22023', null, 'validation_errors: status');

create function pg_temp.cover_after(url text) returns text language sql as $$
  select publish_book(pg_temp.book('aaaaaaaa-0000-0000-0000-000000000004', 'to_read', cover => url));
  select cover_url from feed('friends') where book_id = 'aaaaaaaa-0000-0000-0000-000000000004';
$$;
select is(pg_temp.cover_after('https://books.google.com/books/content?id=x&img=1'), 'https://books.google.com/books/content?id=x&img=1', 'cover_allow_list: google');
select is(pg_temp.cover_after('https://books.googleusercontent.com/x.jpg'), 'https://books.googleusercontent.com/x.jpg', 'cover_allow_list: googleusercontent');
select is(pg_temp.cover_after('https://covers.openlibrary.org/b/isbn/1-L.jpg'), 'https://covers.openlibrary.org/b/isbn/1-L.jpg', 'cover_allow_list: open library');
select is(pg_temp.cover_after('https://rnod.bnportugal.gov.pt/ImagesBN/winlibimg.aspx?doc=1'), 'https://rnod.bnportugal.gov.pt/ImagesBN/winlibimg.aspx?doc=1', 'cover_allow_list: bnp');
select is(pg_temp.cover_after('http://books.google.com/x.jpg'), null, 'cover_allow_list: http refused');
select is(pg_temp.cover_after('https://evil.example/x.jpg'), null, 'cover_allow_list: other host refused');
select is(pg_temp.cover_after('https://books.google.com.evil.example/x.jpg'), null, 'cover_allow_list: lookalike host refused');
select is(pg_temp.cover_after('https://books.google.com/' || repeat('x', 480)), null, 'cover_allow_list: over 500 characters refused');

-- Ownership.
select pg_temp.act_as('44444444-4444-4444-4444-444444444444');
select throws_ok($$select publish_book(pg_temp.book('aaaaaaaa-0000-0000-0000-000000000001', 'read'))$$, 'P0002', null, 'others_book_id_refused');
select lives_ok($$select unpublish_book('aaaaaaaa-0000-0000-0000-000000000001')$$, 'unpublishing another person''s book is a no-op');
select pg_temp.act_as('11111111-1111-1111-1111-111111111111');
select is(pg_temp.kinds('aaaaaaaa-0000-0000-0000-000000000001'), array['want_to_read'], 'other person''s unpublish left the book');

-- Sharing switch (bruno).
reset role;
update profiles set share_activity = false where id = '22222222-2222-2222-2222-222222222222';
set local role authenticated;
select pg_temp.act_as('22222222-2222-2222-2222-222222222222');
select publish_book(pg_temp.book('bbbbbbbb-0000-0000-0000-000000000002', 'read'));
select is(pg_temp.kinds('bbbbbbbb-0000-0000-0000-000000000002'), '{}'::text[], 'share_activity_off_is_noop');
select lives_ok($$select set_share_activity(true)$$, 'bruno turns sharing on');
select publish_book(pg_temp.book('bbbbbbbb-0000-0000-0000-000000000002', 'read'));
select is(pg_temp.kinds('bbbbbbbb-0000-0000-0000-000000000002'), array['finished'], 'publishing works after turning sharing on');
select lives_ok($$select set_share_activity(false)$$, 'bruno turns sharing off');
select is_empty($$select 1 from person_activity(auth.uid())$$, 'set_share_activity_false_unpublishes_all');
select is((select share_activity from profiles where id = auth.uid()), false, 'share_activity stored');
select throws_ok($$update profiles set share_activity = true where id = auth.uid()$$, '42501', null, 'share_activity_not_directly_updatable');

-- Reports keep the review.
select pg_temp.act_as('33333333-3333-3333-3333-333333333333');
select publish_book(pg_temp.book('cccccccc-0000-0000-0000-000000000002', 'read', review => 'Spoiler'));
select set_config('t.c2', (select id::text from person_activity(auth.uid()) where book_id = 'cccccccc-0000-0000-0000-000000000002' and kind = 'reviewed'), true);
select pg_temp.act_as('11111111-1111-1111-1111-111111111111');
select lives_ok($$insert into reports (reporter, reported, reason, activity) values ('11111111-1111-1111-1111-111111111111',
  '33333333-3333-3333-3333-333333333333', 'spam', current_setting('t.c2')::bigint)$$, 'ana reports the review');
select pg_temp.act_as('33333333-3333-3333-3333-333333333333');
select publish_book(pg_temp.book('cccccccc-0000-0000-0000-000000000002', 'read', review => ''));

-- Removal.
select lives_ok($$select unpublish_book('cccccccc-0000-0000-0000-000000000001')$$, 'carla unpublishes C1');
select pg_temp.act_as('11111111-1111-1111-1111-111111111111');
select ok(not exists (select 1 from feed('everyone') where book_id = 'cccccccc-0000-0000-0000-000000000001'), 'unpublish_book_cascades: entry gone');

-- No direct table access.
select throws_ok($$select 1 from shared_books$$, '42501', null, 'no_direct_table_access: shared_books');
select throws_ok($$select 1 from activities$$, '42501', null, 'no_direct_table_access: activities');
select throws_ok($$select 1 from likes$$, '42501', null, 'no_direct_table_access: likes');
select throws_ok($$insert into activities (actor, book, kind) values ('11111111-1111-1111-1111-111111111111',
  'aaaaaaaa-0000-0000-0000-000000000001', 'finished')$$, '42501', null, 'no_direct_table_access: insert');

select pg_temp.act_as('44444444-4444-4444-4444-444444444444');
select lives_ok($$select delete_my_account()$$, 'dora deletes her account');

reset role;
select is((select count(*)::int from likes where activity = current_setting('t.c1')::bigint), 0, 'unpublish_book_cascades: likes gone');
select is((select reported_review from reports where reporter = '11111111-1111-1111-1111-111111111111'), 'Spoiler', 'report_snapshots_review');
select is((select activity from reports where reporter = '11111111-1111-1111-1111-111111111111'), null, 'report keeps row after entry deleted');
select is((select count(*)::int from shared_books where owner = '44444444-4444-4444-4444-444444444444')
  + (select count(*)::int from activities where actor = '44444444-4444-4444-4444-444444444444'), 0, 'delete_my_account_removes_shared_data');

select * from finish();
rollback;
