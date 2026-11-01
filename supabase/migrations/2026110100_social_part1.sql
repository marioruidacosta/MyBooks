-- MyBooks social, part 1: profiles, friendships, blocks, reports, avatars.
-- Every rule is enforced here (row-level security + security-definer functions), not in the app.

create type visibility as enum ('friends', 'public');
create type friendship_status as enum ('pending', 'accepted');
create type report_reason as enum ('spam', 'harassment', 'inappropriate', 'impersonation', 'other');

create table profiles (
  id uuid primary key references auth.users (id) on delete cascade,
  username text not null unique check (username ~ '^[a-z0-9_.]{3,20}$'),
  display_name text not null check (char_length(display_name) between 1 and 50),
  avatar_path text check (avatar_path is null or avatar_path like id::text || '/%'),
  visibility visibility not null default 'friends',
  created_at timestamptz not null default now()
);

create table friendships (
  requester uuid not null references profiles (id) on delete cascade,
  addressee uuid not null references profiles (id) on delete cascade,
  status friendship_status not null default 'pending',
  created_at timestamptz not null default now(),
  responded_at timestamptz,
  primary key (requester, addressee),
  check (requester <> addressee)
);
-- One row per pair, whichever direction it was asked in.
create unique index friendships_pair on friendships (least(requester, addressee), greatest(requester, addressee));
create index friendships_addressee on friendships (addressee);

create table blocks (
  blocker uuid not null references profiles (id) on delete cascade,
  blocked uuid not null references profiles (id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (blocker, blocked),
  check (blocker <> blocked)
);
create index blocks_blocked on blocks (blocked);

create table reports (
  id bigint generated always as identity primary key,
  reporter uuid references profiles (id) on delete set null,
  -- Kept (with the username) when the reported account is deleted, so reports can't be erased that way.
  reported uuid references profiles (id) on delete set null,
  reported_username text,
  reason report_reason not null,
  details text check (char_length(details) <= 1000),
  created_at timestamptz not null default now()
);

-- Helpers ----------------------------------------------------------------------------------

-- Whether the caller and [other] are blocked either way. Scoped to the caller, so nobody can ask about
-- other people's blocks.
create function is_blocked_with(other uuid) returns boolean
language sql stable security definer set search_path = public as $$
  select exists (
    select 1 from blocks
    where (blocker = auth.uid() and blocked = other) or (blocker = other and blocked = auth.uid())
  );
$$;

create function require_user() returns uuid
language plpgsql stable set search_path = public as $$
declare
  me uuid := auth.uid();
begin
  if me is null then
    raise exception 'not signed in' using errcode = '28000';
  end if;
  return me;
end;
$$;

-- Row-level security -----------------------------------------------------------------------

alter table profiles enable row level security;
alter table friendships enable row level security;
alter table blocks enable row level security;
alter table reports enable row level security;

revoke all on profiles, friendships, blocks, reports from anon;

-- Signed-in users write only through the policies below and the functions, and only these columns.
revoke insert, update, delete, truncate, references, trigger on profiles, friendships, blocks, reports from authenticated;
grant insert (id, username, display_name) on profiles to authenticated;
grant update (username, display_name, visibility, avatar_path) on profiles to authenticated;
grant delete on friendships, blocks to authenticated;
grant insert (reporter, reported, reason, details) on reports to authenticated;

create policy profiles_select on profiles for select to authenticated
  using (not is_blocked_with(id));
create policy profiles_insert on profiles for insert to authenticated
  with check (id = auth.uid());
create policy profiles_update on profiles for update to authenticated
  using (id = auth.uid()) with check (id = auth.uid());

create policy friendships_select on friendships for select to authenticated
  using (auth.uid() in (requester, addressee));
create policy friendships_delete on friendships for delete to authenticated
  using (auth.uid() in (requester, addressee));

create policy blocks_select on blocks for select to authenticated
  using (blocker = auth.uid());
create policy blocks_delete on blocks for delete to authenticated
  using (blocker = auth.uid());

create policy reports_insert on reports for insert to authenticated
  with check (reporter = auth.uid() and reported <> auth.uid());

create function set_reported_username() returns trigger
language plpgsql security definer set search_path = public as $$
begin
  new.reported_username := (select username from profiles where id = new.reported);
  return new;
end;
$$;
create trigger reports_reported_username before insert on reports
  for each row execute function set_reported_username();

-- Functions --------------------------------------------------------------------------------

-- Sends a request, or accepts the other person's pending request to me (both asked = friends).
create function send_friend_request(target uuid) returns friendship_status
language plpgsql security definer set search_path = public as $$
declare
  me uuid := require_user();
  existing friendships%rowtype;
begin
  if target = me then
    raise exception 'cannot befriend yourself' using errcode = '22023';
  end if;
  if not exists (select 1 from profiles where id = target) or is_blocked_with(target) then
    raise exception 'profile not available' using errcode = 'P0002';
  end if;

  select * into existing from friendships
  where least(requester, addressee) = least(me, target) and greatest(requester, addressee) = greatest(me, target)
  for update;

  if found then
    if existing.status = 'pending' and existing.requester = target then
      update friendships set status = 'accepted', responded_at = now()
      where requester = target and addressee = me;
      return 'accepted';
    end if;
    return existing.status;
  end if;

  begin
    insert into friendships (requester, addressee) values (me, target);
  exception when unique_violation then
    -- The other person asked at the same moment: accept theirs.
    update friendships set status = 'accepted', responded_at = now()
    where requester = target and addressee = me and status = 'pending';
    return 'accepted';
  end;
  return 'pending';
end;
$$;

create function respond_to_friend_request(requester uuid, accept boolean) returns void
language plpgsql security definer set search_path = public as $$
declare
  me uuid := require_user();
begin
  if accept then
    update friendships f set status = 'accepted', responded_at = now()
    where f.requester = respond_to_friend_request.requester and f.addressee = me and f.status = 'pending';
  else
    delete from friendships f
    where f.requester = respond_to_friend_request.requester and f.addressee = me and f.status = 'pending';
  end if;
  if not found then
    raise exception 'no such request' using errcode = 'P0002';
  end if;
end;
$$;

create function block_user(target uuid) returns void
language plpgsql security definer set search_path = public as $$
declare
  me uuid := require_user();
begin
  if target = me then
    raise exception 'cannot block yourself' using errcode = '22023';
  end if;
  insert into blocks (blocker, blocked) values (me, target) on conflict do nothing;
  delete from friendships
  where least(requester, addressee) = least(me, target) and greatest(requester, addressee) = greatest(me, target);
end;
$$;

create function search_profiles(query text, max int default 20) returns setof profiles
language sql stable security definer set search_path = public as $$
  with q as (
    select trim(search_profiles.query) as text,
           '%' || replace(replace(replace(trim(search_profiles.query), '\', '\\'), '%', '\%'), '_', '\_') || '%' as pattern
  )
  select p.* from profiles p, q
  where char_length(q.text) >= 2
    and p.id <> auth.uid()
    and not is_blocked_with(p.id)
    and (p.username ilike q.pattern or p.display_name ilike q.pattern)
  order by (p.username ilike q.text || '%') desc, p.username
  limit least(greatest(search_profiles.max, 1), 50);
$$;

-- The people the caller blocked (their profiles are otherwise hidden from the caller by RLS).
create function my_blocked_profiles() returns setof profiles
language sql stable security definer set search_path = public as $$
  select p.* from profiles p join blocks b on b.blocked = p.id
  where b.blocker = auth.uid()
  order by p.username;
$$;

-- Deletes the caller's auth user; profile, friendships and blocks cascade, reports keep reporter = null.
-- The app removes the avatar through the Storage API first (direct storage.objects deletes are blocked).
create function delete_my_account() returns void
language plpgsql security definer set search_path = public as $$
declare
  me uuid := require_user();
begin
  delete from auth.users where id = me;
end;
$$;

revoke all on function is_blocked_with(uuid), send_friend_request(uuid), respond_to_friend_request(uuid, boolean),
  block_user(uuid), search_profiles(text, int), my_blocked_profiles(), delete_my_account(), require_user() from public, anon;
grant execute on function is_blocked_with(uuid), send_friend_request(uuid), respond_to_friend_request(uuid, boolean),
  block_user(uuid), search_profiles(text, int), my_blocked_profiles(), delete_my_account(), require_user() to authenticated;

-- Avatars ----------------------------------------------------------------------------------

-- Public read; at most 1 MB, JPEG only (the app uploads 512 px JPEGs).
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('avatars', 'avatars', true, 1048576, array['image/jpeg'])
on conflict (id) do update set public = true, file_size_limit = excluded.file_size_limit,
  allowed_mime_types = excluded.allowed_mime_types;

-- Owners must see their own objects: the Storage API's upload and delete return the affected rows.
create policy avatars_select_own on storage.objects for select to authenticated
  using (bucket_id = 'avatars' and (storage.foldername(name))[1] = auth.uid()::text);

create policy avatars_insert_own on storage.objects for insert to authenticated
  with check (bucket_id = 'avatars' and (storage.foldername(name))[1] = auth.uid()::text);
create policy avatars_update_own on storage.objects for update to authenticated
  using (bucket_id = 'avatars' and (storage.foldername(name))[1] = auth.uid()::text)
  with check (bucket_id = 'avatars' and (storage.foldername(name))[1] = auth.uid()::text);
create policy avatars_delete_own on storage.objects for delete to authenticated
  using (bucket_id = 'avatars' and (storage.foldername(name))[1] = auth.uid()::text);
