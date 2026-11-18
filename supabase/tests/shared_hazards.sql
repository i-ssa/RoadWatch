-- Run against a disposable PostgreSQL database only: creates mock Supabase roles/auth.
\set ON_ERROR_STOP on
create role anon;
create role authenticated;
create schema auth;
create table auth.users(id uuid primary key);
create table public.road_reports(id uuid primary key,user_id uuid references auth.users(id),captures jsonb not null default '[]');
\ir ../migrations/20261103_shared_hazards.sql
insert into auth.users values('00000000-0000-0000-0000-000000000001'),('00000000-0000-0000-0000-000000000002');
insert into public.road_reports values('10000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000001','[{"id":"a","latitude":-1.28,"longitude":36.82,"accuracy_m":5},{"id":"b","latitude":-1.28001,"longitude":36.82,"accuracy_m":5},{"id":"invalid","latitude":91,"longitude":0},{"id":"missing","latitude":null}]');
insert into public.road_reports values('10000000-0000-0000-0000-000000000002','00000000-0000-0000-0000-000000000002','[{"id":"c","latitude":-1.28002,"longitude":36.82,"accuracy_m":5}]');
update public.road_reports set captures=captures;
do $$ begin
 if (select count(*) from public.map_hazards)<>1 then raise exception 'Nearby reports failed to merge'; end if;
 if (select reporter_count from public.map_hazards)<>2 then raise exception 'Distinct reporter count incorrect'; end if;
 if (select count(*) from public.hazard_observations)<>3 then raise exception 'Retry changed observations'; end if;
end $$;
insert into public.road_reports values('10000000-0000-0000-0000-000000000003','00000000-0000-0000-0000-000000000002','[{"id":"distant","latitude":-1.29,"longitude":36.82,"accuracy_m":5},{"id":"inaccurate","latitude":-1.28,"longitude":36.82,"accuracy_m":90}]');
do $$ begin
 if (select count(*) from public.map_hazards)<>3 then raise exception 'Separate locations incorrectly merged'; end if;
end $$;
set role authenticated;
do $$ begin
 if (select count(*) from public.map_hazards)<>3 then raise exception 'Shared read failed'; end if;
 begin
  perform * from public.hazard_observations;
  raise exception 'Observation owners exposed';
 exception when insufficient_privilege then null; end;
 begin
  insert into public.map_hazards(latitude,longitude) values(0,0);
  raise exception 'Client write allowed';
 exception when insufficient_privilege then null; end;
end $$;
reset role;
set role anon;
do $$ begin
 begin
  perform * from public.map_hazards;
  raise exception 'Anonymous read allowed';
 exception when insufficient_privilege then null; end;
end $$;
reset role;
