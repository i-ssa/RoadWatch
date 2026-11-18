begin;
create table public.map_hazards (
 id uuid primary key default gen_random_uuid(),
 latitude double precision not null check(latitude between -90 and 90),
 longitude double precision not null check(longitude between -180 and 180),
 reporter_count integer not null default 0,
 last_seen timestamptz not null default now()
);
create table public.hazard_observations (
 report_id uuid not null references public.road_reports(id), capture_id text not null,
 user_id uuid not null references auth.users(id), hazard_id uuid not null references public.map_hazards(id),
 accuracy_m double precision,
 primary key(report_id,capture_id)
);
alter table public.map_hazards enable row level security;
alter table public.hazard_observations enable row level security;
revoke all on public.map_hazards,public.hazard_observations from anon,authenticated;
grant select on public.map_hazards to authenticated;
create policy "Read submitted hazards" on public.map_hazards for select to authenticated using(true);
create function public.publish_report_hazards() returns trigger
language plpgsql security definer set search_path='' as $$
declare c jsonb; lat double precision; lon double precision; accuracy double precision; target uuid;
begin
 -- Serialize matching/creation so concurrent submissions cannot create duplicate clusters.
 perform pg_catalog.pg_advisory_xact_lock(726394,1);
 for c in select value from pg_catalog.jsonb_array_elements(new.captures) loop
  if c->>'id' is null or exists(select 1 from public.hazard_observations where report_id=new.id and capture_id=c->>'id') then continue; end if;
  begin
   lat := (c->>'latitude')::double precision; lon := (c->>'longitude')::double precision; accuracy := (c->>'accuracy_m')::double precision;
  exception when invalid_text_representation or numeric_value_out_of_range then continue;
  end;
  if lat is null or lon is null or not(lat between -90 and 90 and lon between -180 and 180) then continue; end if;
  target := null;
  if accuracy between 0 and 20 then
   select h.id into target from public.map_hazards h
   where 6371000*2*asin(sqrt(least(1.0,power(sin(radians(h.latitude-lat)/2),2)+cos(radians(lat))*cos(radians(h.latitude))*power(sin(radians(h.longitude-lon)/2),2)))) <= 10
   and exists(select 1 from public.hazard_observations o where o.hazard_id=h.id and o.accuracy_m between 0 and 20)
   order by power(h.latitude-lat,2)+power((h.longitude-lon)*cos(radians(lat)),2),h.id limit 1;
  end if;
  if target is null then
   insert into public.map_hazards(latitude,longitude) values(lat,lon) returning id into target;
  end if;
  insert into public.hazard_observations(report_id,capture_id,user_id,hazard_id,accuracy_m) values(new.id,c->>'id',new.user_id,target,accuracy);
  update public.map_hazards set reporter_count=(select count(distinct user_id) from public.hazard_observations where hazard_id=target),last_seen=now() where id=target;
 end loop;
 return new;
end;
$$;
revoke all on function public.publish_report_hazards() from public,anon,authenticated;
create trigger publish_submitted_hazards after insert or update of captures on public.road_reports for each row execute function public.publish_report_hazards();
-- Only uploaded, explicitly submitted reports are backfilled. Drafts never reach this table.
update public.road_reports set captures=captures;
commit;
