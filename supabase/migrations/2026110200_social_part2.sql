-- MyBooks social, part 2: shared book state, activity feed, likes.
-- The phone uploads each shared book's public state with publish_book(); the database decides which feed
-- entries that creates. The new tables have no table access at all: everything goes through the functions.

create type book_status as enum ('reading', 'read', 'to_read', 'to_buy');
create type activity_kind as enum ('started', 'finished', 'want_to_read', 'want_to_buy', 'rated', 'reviewed', 'progress');

-- Changed only through set_share_activity() (not in the profiles update grant), so that turning it off
-- always removes what was shared.
alter table profiles add column share_activity boolean not null default true;

create table shared_books (
  id uuid primary key,  -- the phone's permanent shared id for the book
  owner uuid not null references profiles (id) on delete cascade,
  isbn text check (char_length(isbn) <= 20),
  title text not null check (char_length(title) between 1 and 300),
  authors text not null default '' check (char_length(authors) <= 300),
  cover_url text check (char_length(cover_url) <= 500),
  status book_status not null,
  rating int check (rating between 1 and 5),
  progress int check (progress between 0 and 100),
  review text not null default '' check (char_length(review) <= 2000),
  updated_at timestamptz not null default now()
);
create index shared_books_owner on shared_books (owner);

create table activities (
  id bigint generated always as identity primary key,
  actor uuid not null references profiles (id) on delete cascade,
  book uuid not null references shared_books (id) on delete cascade,
  kind activity_kind not null,
  rating int,    -- the value when the entry was made
  progress int,
  created_at timestamptz not null default now()
);
create index activities_recent on activities (created_at desc, id desc);
create index activities_actor on activities (actor, created_at desc);
create index activities_book on activities (book);

create table likes (
  activity bigint not null references activities (id) on delete cascade,
  liker uuid not null references profiles (id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (activity, liker)
);
create index likes_liker on likes (liker);

alter table reports
  add column activity bigint references activities (id) on delete set null,
  add column reported_review text;

alter table shared_books enable row level security;
alter table activities enable row level security;
alter table likes enable row level security;
revoke all on shared_books, activities, likes from anon, authenticated;
grant insert (activity) on reports to authenticated;

-- Reports also keep the reported entry's review, so editing or deleting it afterwards doesn't erase it.
create or replace function set_reported_username() returns trigger
language plpgsql security definer set search_path = public as $$
begin
  new.reported_username := (select username from profiles where id = new.reported);
  if new.activity is not null then
    new.reported_review := (select b.review from activities a join shared_books b on b.id = a.book
                            where a.id = new.activity and a.actor = new.reported);
  end if;
  return new;
end;
$$;

-- Helpers (not callable by the app) --------------------------------------------------------

create function are_friends(a uuid, b uuid) returns boolean
language sql stable security definer set search_path = public as $$
  select exists (
    select 1 from friendships
    where status = 'accepted'
      and least(requester, addressee) = least(a, b) and greatest(requester, addressee) = greatest(a, b)
  );
$$;

-- Whether the caller may see [actor]'s activity: their own, or (no block either way and) public or a friend.
create function can_see_activity_of(actor uuid) returns boolean
language sql stable security definer set search_path = public as $$
  select actor = auth.uid() or (
    not is_blocked_with(actor)
    and (exists (select 1 from profiles where id = actor and visibility = 'public') or are_friends(auth.uid(), actor))
  );
$$;

-- Covers only from the book catalogues the app uses, so no one can make readers' phones load any image.
create function allowed_cover_url(url text) returns text
language sql immutable set search_path = public as $$
  select case
    when char_length(url) <= 500
     and url ~ '^https://(books\.google\.com|books\.googleusercontent\.com|covers\.openlibrary\.org|rnod\.bnportugal\.gov\.pt)(/|$)'
    then url
  end;
$$;

-- Adds a rated/progress entry, or updates the book's latest entry when it is the same kind and under 12 h old.
create function record_collapsible(p_actor uuid, p_book uuid, p_kind activity_kind, p_rating int, p_progress int) returns void
language plpgsql security definer set search_path = public as $$
declare
  latest activities%rowtype;
begin
  select * into latest from activities a where a.actor = p_actor and a.book = p_book
  order by a.created_at desc, a.id desc limit 1;
  if found and latest.kind = p_kind and latest.created_at > now() - interval '12 hours' then
    update activities set rating = p_rating, progress = p_progress, created_at = now() where id = latest.id;
  else
    insert into activities (actor, book, kind, rating, progress) values (p_actor, p_book, p_kind, p_rating, p_progress);
  end if;
end;
$$;

-- Every entry with its actor, book and likes, for the caller (auth.uid()). Filtered by the functions below.
create view activity_entries as
  select a.id, a.kind, a.rating, a.progress, a.created_at,
         p.id as actor_id, p.username, p.display_name, p.avatar_path, p.visibility,
         b.id as book_id, b.isbn, b.title, b.authors, b.cover_url,
         case when a.kind = 'reviewed' then b.review end as review,
         (select count(*)::int from likes l where l.activity = a.id) as like_count,
         exists (select 1 from likes l where l.activity = a.id and l.liker = auth.uid()) as liked_by_me
  from activities a
  join profiles p on p.id = a.actor
  join shared_books b on b.id = a.book;
revoke all on activity_entries from anon, authenticated;

-- Publishing -------------------------------------------------------------------------------

-- book: {id, isbn, title, authors, cover_url, status, rating, progress, review}
-- before: {status, rating, progress, review}: the book's state before its first shared change, or null for
-- a book added after joining. Used only when the server has no row for the book yet.
create function publish_book(book jsonb, before jsonb default null) returns void
language plpgsql security definer set search_path = public as $$
declare
  me uuid := require_user();
  v_id uuid;
  v_status book_status;
  v_rating int;
  v_progress int;
  v_title text := trim(coalesce(book ->> 'title', ''));
  v_authors text := coalesce(book ->> 'authors', '');
  v_isbn text := nullif(book ->> 'isbn', '');
  v_review text := coalesce(book ->> 'review', '');
  v_cover text := allowed_cover_url(book ->> 'cover_url');
  stored shared_books%rowtype;
  p_status book_status;
  p_rating int;
  p_progress int;
  p_review text := '';
  status_added boolean := false;
begin
  if (select share_activity from profiles where id = me) is not true then
    return;
  end if;

  begin
    v_id := (book ->> 'id')::uuid;
    v_status := (book ->> 'status')::book_status;
    v_rating := (book ->> 'rating')::int;
    v_progress := (book ->> 'progress')::int;
  exception when others then
    raise exception 'invalid book' using errcode = '22023';
  end;
  if v_id is null or v_status is null
     or char_length(v_title) not between 1 and 300 or char_length(v_authors) > 300 or char_length(v_isbn) > 20
     or v_rating not between 1 and 5 or v_progress not between 0 and 100 or char_length(v_review) > 2000 then
    raise exception 'invalid book' using errcode = '22023';
  end if;

  select * into stored from shared_books s where s.id = v_id for update;
  if found then
    if stored.owner <> me then
      raise exception 'book not available' using errcode = 'P0002';
    end if;
    p_status := stored.status;
    p_rating := stored.rating;
    p_progress := stored.progress;
    p_review := stored.review;
  elsif before is not null then
    begin
      p_status := (before ->> 'status')::book_status;
      p_rating := (before ->> 'rating')::int;
      p_progress := (before ->> 'progress')::int;
    exception when others then
      raise exception 'invalid before' using errcode = '22023';
    end;
    p_review := coalesce(before ->> 'review', '');
  end if;

  insert into shared_books as s (id, owner, isbn, title, authors, cover_url, status, rating, progress, review, updated_at)
  values (v_id, me, v_isbn, v_title, v_authors, v_cover, v_status, v_rating, v_progress, v_review, now())
  on conflict (id) do update set
    isbn = excluded.isbn, title = excluded.title, authors = excluded.authors, cover_url = excluded.cover_url,
    status = excluded.status, rating = excluded.rating, progress = excluded.progress, review = excluded.review,
    updated_at = now()
  where s.owner = me;

  if v_status is distinct from p_status then
    insert into activities (actor, book, kind) values (me, v_id, case v_status
      when 'reading' then 'started' when 'read' then 'finished' when 'to_read' then 'want_to_read' else 'want_to_buy'
    end::activity_kind);
    status_added := true;
  end if;
  if v_rating is not null and v_rating is distinct from p_rating then
    perform record_collapsible(me, v_id, 'rated', v_rating, null);
  end if;
  if v_progress is not null and v_progress is distinct from p_progress and v_status = 'reading' and not status_added then
    perform record_collapsible(me, v_id, 'progress', null, v_progress);
  end if;
  if p_review = '' and v_review <> '' then
    insert into activities (actor, book, kind) values (me, v_id, 'reviewed');
  elsif p_review <> '' and v_review = '' then
    delete from activities a where a.book = v_id and a.kind = 'reviewed';
  end if;
end;
$$;

create function unpublish_book(id uuid) returns void
language plpgsql security definer set search_path = public as $$
begin
  delete from shared_books s where s.id = unpublish_book.id and s.owner = require_user();
end;
$$;

create function unpublish_all() returns void
language plpgsql security definer set search_path = public as $$
begin
  delete from shared_books s where s.owner = require_user();
end;
$$;

create function set_share_activity(enabled boolean) returns void
language plpgsql security definer set search_path = public as $$
declare
  me uuid := require_user();
begin
  update profiles set share_activity = enabled where id = me;
  if not enabled then
    delete from shared_books s where s.owner = me;
  end if;
end;
$$;

-- Reading ----------------------------------------------------------------------------------

-- 'friends': the caller's and their friends' entries; 'everyone': entries of public users. Newest first,
-- paged by (created_at, id) of the last entry seen.
create function feed(scope text, before_time timestamptz default null, before_id bigint default null, max int default 20)
returns table (id bigint, kind activity_kind, rating int, progress int, created_at timestamptz,
  actor_id uuid, username text, display_name text, avatar_path text,
  book_id uuid, isbn text, title text, authors text, cover_url text, review text,
  like_count int, liked_by_me boolean)
language sql stable security definer set search_path = public as $$
  select e.id, e.kind, e.rating, e.progress, e.created_at, e.actor_id, e.username, e.display_name, e.avatar_path,
         e.book_id, e.isbn, e.title, e.authors, e.cover_url, e.review, e.like_count, e.liked_by_me
  from activity_entries e
  where auth.uid() is not null
    and case feed.scope
          when 'friends' then e.actor_id = auth.uid() or are_friends(auth.uid(), e.actor_id)
          when 'everyone' then e.visibility = 'public'
          else false
        end
    and not is_blocked_with(e.actor_id)
    and (feed.before_time is null
         or (e.created_at, e.id) < (feed.before_time, coalesce(feed.before_id, 9223372036854775807)))
  order by e.created_at desc, e.id desc
  limit least(greatest(feed.max, 1), 50);
$$;

create function person_activity(person uuid, before_time timestamptz default null, before_id bigint default null, max int default 20)
returns table (id bigint, kind activity_kind, rating int, progress int, created_at timestamptz,
  actor_id uuid, username text, display_name text, avatar_path text,
  book_id uuid, isbn text, title text, authors text, cover_url text, review text,
  like_count int, liked_by_me boolean)
language sql stable security definer set search_path = public as $$
  select e.id, e.kind, e.rating, e.progress, e.created_at, e.actor_id, e.username, e.display_name, e.avatar_path,
         e.book_id, e.isbn, e.title, e.authors, e.cover_url, e.review, e.like_count, e.liked_by_me
  from activity_entries e
  where e.actor_id = person_activity.person
    and can_see_activity_of(person_activity.person)
    and (person_activity.before_time is null
         or (e.created_at, e.id) < (person_activity.before_time, coalesce(person_activity.before_id, 9223372036854775807)))
  order by e.created_at desc, e.id desc
  limit least(greatest(person_activity.max, 1), 50);
$$;

-- Likes ------------------------------------------------------------------------------------

-- "like" is a reserved word, hence the quotes (PostgREST: /rpc/like).
create function "like"(activity bigint) returns void
language plpgsql security definer set search_path = public as $$
declare
  me uuid := require_user();
  v_actor uuid;
begin
  select a.actor into v_actor from activities a where a.id = "like".activity;
  if v_actor is null or v_actor = me or not can_see_activity_of(v_actor) then
    raise exception 'entry not available' using errcode = 'P0002';
  end if;
  insert into likes (activity, liker) values ("like".activity, me) on conflict do nothing;
end;
$$;

create function unlike(activity bigint) returns void
language plpgsql security definer set search_path = public as $$
begin
  delete from likes l where l.activity = unlike.activity and l.liker = require_user();
end;
$$;

create function likers(activity bigint, max int default 50)
returns table (id uuid, username text, display_name text, avatar_path text)
language sql stable security definer set search_path = public as $$
  select p.id, p.username, p.display_name, p.avatar_path
  from likes l
  join profiles p on p.id = l.liker
  join activities a on a.id = l.activity
  where l.activity = likers.activity
    and can_see_activity_of(a.actor)
    and not is_blocked_with(p.id)
  order by l.created_at desc, p.username
  limit least(greatest(likers.max, 1), 50);
$$;

revoke all on function are_friends(uuid, uuid), can_see_activity_of(uuid), allowed_cover_url(text),
  record_collapsible(uuid, uuid, activity_kind, int, int) from public, anon, authenticated;
revoke all on function publish_book(jsonb, jsonb), unpublish_book(uuid), unpublish_all(), set_share_activity(boolean),
  feed(text, timestamptz, bigint, int), person_activity(uuid, timestamptz, bigint, int), "like"(bigint), unlike(bigint),
  likers(bigint, int) from public, anon;
grant execute on function publish_book(jsonb, jsonb), unpublish_book(uuid), unpublish_all(), set_share_activity(boolean),
  feed(text, timestamptz, bigint, int), person_activity(uuid, timestamptz, bigint, int), "like"(bigint), unlike(bigint),
  likers(bigint, int) to authenticated;
