-- Run once in the project's Supabase SQL editor before enabling report sync.
create table if not exists public.road_reports (
  id uuid primary key,
  user_id uuid not null references auth.users(id),
  started_at_ms bigint not null,
  captures jsonb not null default '[]',
  model_version text not null,
  created_at timestamptz not null default now()
);
alter table public.road_reports enable row level security;
create policy "Read own reports" on public.road_reports for select to authenticated using (auth.uid() = user_id);
create policy "Insert own reports" on public.road_reports for insert to authenticated with check (auth.uid() = user_id);
create policy "Update own reports" on public.road_reports for update to authenticated using (auth.uid() = user_id) with check (auth.uid() = user_id);
insert into storage.buckets(id, name, public) values ('roadwatch-captures', 'roadwatch-captures', false) on conflict(id) do nothing;
create policy "Read own captures" on storage.objects for select to authenticated using (bucket_id='roadwatch-captures' and (storage.foldername(name))[1]=auth.uid()::text);
create policy "Insert own captures" on storage.objects for insert to authenticated with check (bucket_id='roadwatch-captures' and (storage.foldername(name))[1]=auth.uid()::text);
create policy "Update own captures" on storage.objects for update to authenticated using (bucket_id='roadwatch-captures' and (storage.foldername(name))[1]=auth.uid()::text) with check (bucket_id='roadwatch-captures' and (storage.foldername(name))[1]=auth.uid()::text);
