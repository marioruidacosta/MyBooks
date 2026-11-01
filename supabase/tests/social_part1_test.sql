-- pgTAP tests for the social part 1 schema. Run with `supabase test db` (CI: .github/workflows/supabase.yml).
begin;
create extension if not exists pgtap with schema extensions;
select plan(32);

-- Users: ana, bruno, carla, dora, eve (eve has no profile yet).
insert into auth.users (id, email, aud, role) values
  ('11111111-1111-1111-1111-111111111111', 'ana@test.local', 'authenticated', 'authenticated'),
  ('22222222-2222-2222-2222-222222222222', 'bruno@test.local', 'authenticated', 'authenticated'),
  ('33333333-3333-3333-3333-333333333333', 'carla@test.local', 'authenticated', 'authenticated'),
  ('44444444-4444-4444-4444-444444444444', 'dora@test.local', 'authenticated', 'authenticated'),
  ('55555555-5555-5555-5555-555555555555', 'eve@test.local', 'authenticated', 'authenticated');
insert into profiles (id, username, display_name) values
  ('11111111-1111-1111-1111-111111111111', 'ana', 'Ana'),
  ('22222222-2222-2222-2222-222222222222', 'bruno', 'Bruno'),
  ('33333333-3333-3333-3333-333333333333', 'carla', 'Carla'),
  ('44444444-4444-4444-4444-444444444444', 'dora', 'Dora');

create function pg_temp.act_as(uid uuid) returns void language sql as $$
  select set_config('request.jwt.claims', json_build_object('sub', uid::text, 'role', 'authenticated')::text, true);
$$;

-- username_format_enforced
select throws_ok($$insert into profiles (id, username, display_name) values ('55555555-5555-5555-5555-555555555555', 'Ab', 'Eve')$$,
  '23514', null, 'username_format_enforced: too short / uppercase');
select throws_ok($$insert into profiles (id, username, display_name) values ('55555555-5555-5555-5555-555555555555', 'has space', 'Eve')$$,
  '23514', null, 'username_format_enforced: space');
select lives_ok($$insert into profiles (id, username, display_name) values ('55555555-5555-5555-5555-555555555555', 'mario.c_1', 'Eve')$$,
  'username_format_enforced: valid name accepted');
-- username_unique
select throws_ok($$update profiles set username = 'ana' where id = '55555555-5555-5555-5555-555555555555'$$,
  '23505', null, 'username_unique');

-- Ana asks Bruno.
set local role authenticated;
select pg_temp.act_as('11111111-1111-1111-1111-111111111111');
select is(send_friend_request('22222222-2222-2222-2222-222222222222'), 'pending'::friendship_status, 'request is pending');
select is(send_friend_request('22222222-2222-2222-2222-222222222222'), 'pending'::friendship_status, 'repeated request is a no-op');

-- stranger_cannot_read_others_friendships
select pg_temp.act_as('33333333-3333-3333-3333-333333333333');
select is_empty($$select 1 from friendships where requester = '11111111-1111-1111-1111-111111111111'$$,
  'stranger_cannot_read_others_friendships');

-- only_addressee_can_accept
select throws_ok($$select respond_to_friend_request('11111111-1111-1111-1111-111111111111', true)$$,
  'P0002', null, 'only_addressee_can_accept: stranger');
select pg_temp.act_as('11111111-1111-1111-1111-111111111111');
select throws_ok($$select respond_to_friend_request('11111111-1111-1111-1111-111111111111', true)$$,
  'P0002', null, 'only_addressee_can_accept: requester');
select pg_temp.act_as('22222222-2222-2222-2222-222222222222');
select lives_ok($$select respond_to_friend_request('11111111-1111-1111-1111-111111111111', true)$$,
  'only_addressee_can_accept: addressee');
select is((select status from friendships where requester = '11111111-1111-1111-1111-111111111111'
  and addressee = '22222222-2222-2222-2222-222222222222'), 'accepted'::friendship_status, 'request accepted');

-- crossed_requests_become_friends
select pg_temp.act_as('11111111-1111-1111-1111-111111111111');
select is(send_friend_request('33333333-3333-3333-3333-333333333333'), 'pending'::friendship_status, 'ana asks carla');
select pg_temp.act_as('33333333-3333-3333-3333-333333333333');
select is(send_friend_request('11111111-1111-1111-1111-111111111111'), 'accepted'::friendship_status,
  'crossed_requests_become_friends');
select is((select count(*)::int from friendships where '33333333-3333-3333-3333-333333333333' in (requester, addressee)), 1,
  'crossed requests leave one row');

-- decline_deletes_request
select pg_temp.act_as('44444444-4444-4444-4444-444444444444');
select is(send_friend_request('11111111-1111-1111-1111-111111111111'), 'pending'::friendship_status, 'dora asks ana');
select pg_temp.act_as('11111111-1111-1111-1111-111111111111');
select lives_ok($$select respond_to_friend_request('44444444-4444-4444-4444-444444444444', false)$$, 'ana declines dora');
select is_empty($$select 1 from friendships where '44444444-4444-4444-4444-444444444444' in (requester, addressee)$$,
  'decline_deletes_request');

-- either_can_remove_friendship (bruno is the addressee of ana's accepted request)
select pg_temp.act_as('22222222-2222-2222-2222-222222222222');
delete from friendships where requester = '11111111-1111-1111-1111-111111111111' and addressee = '22222222-2222-2222-2222-222222222222';
select is_empty($$select 1 from friendships where '22222222-2222-2222-2222-222222222222' in (requester, addressee)$$,
  'either_can_remove_friendship');

-- block_removes_friendship_and_hides_profiles_both_ways (ana and carla are friends)
select pg_temp.act_as('11111111-1111-1111-1111-111111111111');
select lives_ok($$select block_user('33333333-3333-3333-3333-333333333333')$$, 'ana blocks carla');
select is_empty($$select 1 from friendships where '33333333-3333-3333-3333-333333333333' in (requester, addressee)$$,
  'block removes friendship');
select is_empty($$select 1 from profiles where id = '33333333-3333-3333-3333-333333333333'$$, 'blocker cannot see blocked');
select pg_temp.act_as('33333333-3333-3333-3333-333333333333');
select is_empty($$select 1 from profiles where id = '11111111-1111-1111-1111-111111111111'$$, 'blocked cannot see blocker');
-- blocked_cannot_send_request
select throws_ok($$select send_friend_request('11111111-1111-1111-1111-111111111111')$$,
  'P0002', null, 'blocked_cannot_send_request');

-- search_excludes_self_and_blocked_and_needs_2_chars
select pg_temp.act_as('11111111-1111-1111-1111-111111111111');
select is_empty($$select 1 from search_profiles('b')$$, 'search needs 2 chars');
select results_eq($$select username from search_profiles('  br ')$$, array['bruno'], 'search finds bruno, trims query');
select is_empty($$select 1 from search_profiles('an') where username = 'ana'$$, 'search excludes self');
select is_empty($$select 1 from search_profiles('carl')$$, 'search excludes blocked');

-- reports_insert_only_and_not_readable / cannot_report_self
select lives_ok($$insert into reports (reporter, reported, reason, details)
  values ('11111111-1111-1111-1111-111111111111', '44444444-4444-4444-4444-444444444444', 'spam', 'ads')$$, 'report inserted');
select is_empty($$select 1 from reports$$, 'reports_insert_only_and_not_readable');
select throws_ok($$insert into reports (reporter, reported, reason)
  values ('11111111-1111-1111-1111-111111111111', '11111111-1111-1111-1111-111111111111', 'other')$$,
  '42501', null, 'cannot_report_self');

-- delete_my_account_cascades: dora reports bruno and befriends ana, then deletes her account.
select pg_temp.act_as('44444444-4444-4444-4444-444444444444');
insert into reports (reporter, reported, reason) values ('44444444-4444-4444-4444-444444444444', '22222222-2222-2222-2222-222222222222', 'harassment');
select send_friend_request('11111111-1111-1111-1111-111111111111');
select lives_ok($$select delete_my_account()$$, 'dora deletes her account');
reset role;
select ok(
  not exists (select 1 from profiles where id = '44444444-4444-4444-4444-444444444444')
  and not exists (select 1 from friendships where '44444444-4444-4444-4444-444444444444' in (requester, addressee))
  and exists (select 1 from reports where reported = '22222222-2222-2222-2222-222222222222' and reporter is null),
  'delete_my_account_cascades');

select * from finish();
rollback;
