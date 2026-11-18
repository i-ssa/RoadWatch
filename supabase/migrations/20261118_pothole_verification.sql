create table public.pothole_verification_accounts (
  account_id uuid primary key references auth.users(id) on delete cascade,
  last_request timestamptz not null
);
create table public.pothole_verification_quota (
  id boolean primary key check (id),
  window_start timestamptz not null,
  requests integer not null
);
alter table public.pothole_verification_accounts enable row level security;
alter table public.pothole_verification_quota enable row level security;
revoke all on public.pothole_verification_accounts, public.pothole_verification_quota
  from public, anon, authenticated;
grant select, insert, update on public.pothole_verification_accounts, public.pothole_verification_quota
  to service_role;

create function public.take_pothole_verification_slot(account_id uuid)
returns boolean language plpgsql security invoker set search_path = '' as $$
declare
  requested_at timestamptz := statement_timestamp();
  current_window timestamptz := date_trunc('minute', requested_at);
begin
  insert into public.pothole_verification_accounts as accounts (account_id, last_request)
    values ($1, requested_at)
    on conflict on constraint pothole_verification_accounts_pkey do update
      set last_request = excluded.last_request
      where accounts.last_request <= excluded.last_request - interval '10 seconds';
  if not found then return false; end if;

  insert into public.pothole_verification_quota as quota (id, window_start, requests)
    values (true, current_window, 1)
    on conflict (id) do update set
      requests = case when quota.window_start < excluded.window_start then 1 else quota.requests + 1 end,
      window_start = excluded.window_start
    where quota.window_start < excluded.window_start
      or (quota.window_start = excluded.window_start and quota.requests < 30);
  return found;
end;
$$;
revoke execute on function public.take_pothole_verification_slot(uuid) from public, anon, authenticated;
grant execute on function public.take_pothole_verification_slot(uuid) to service_role;
