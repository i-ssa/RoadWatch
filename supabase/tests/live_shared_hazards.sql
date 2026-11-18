-- Run with the SQL editor's postgres role. All synthetic data rolls back.
begin;
insert into auth.users(id) values('00000000-0000-4000-8000-000000009a01'),('00000000-0000-4000-8000-000000009a02');
set local role authenticated;
select set_config('request.jwt.claims','{"sub":"00000000-0000-4000-8000-000000009a01","role":"authenticated"}',true);
insert into public.road_reports(id,user_id,started_at_ms,captures,model_version) values(
 '10000000-0000-4000-8000-000000009a01','00000000-0000-4000-8000-000000009a01',0,
 '[{"id":"acceptance-a","latitude":-70.0,"longitude":0.0,"accuracy_m":5},{"id":"acceptance-a-repeat","latitude":-70.00001,"longitude":0.0,"accuracy_m":5}]','transactional-acceptance');
insert into storage.objects(bucket_id,name) values('roadwatch-captures','00000000-0000-4000-8000-000000009a01/acceptance/private.jpg');
do $$ begin
 if not exists(select 1 from storage.objects where bucket_id='roadwatch-captures' and name='00000000-0000-4000-8000-000000009a01/acceptance/private.jpg') then raise exception 'Owner cannot read photo metadata'; end if;
end $$;
select set_config('request.jwt.claims','{"sub":"00000000-0000-4000-8000-000000009a02","role":"authenticated"}',true);
do $$ begin
 if exists(select 1 from public.road_reports where id='10000000-0000-4000-8000-000000009a01') then raise exception 'Other account can read private report'; end if;
 if not exists(select 1 from public.map_hazards where latitude=-70.0 and longitude=0 and reporter_count=1) then raise exception 'Other account cannot see submitted marker'; end if;
end $$;
insert into public.road_reports(id,user_id,started_at_ms,captures,model_version) values(
 '10000000-0000-4000-8000-000000009a02','00000000-0000-4000-8000-000000009a02',0,
 '[{"id":"acceptance-b","latitude":-70.00002,"longitude":0.0,"accuracy_m":5}]','transactional-acceptance');
update public.road_reports set captures=captures where id='10000000-0000-4000-8000-000000009a02';
do $$ begin
 if (select count(*) from public.map_hazards where latitude between -70.001 and -69.999 and longitude=0)<>1 then raise exception 'Duplicate markers'; end if;
 if not exists(select 1 from public.map_hazards where latitude=-70.0 and longitude=0 and reporter_count=2) then raise exception 'Wrong distinct-user count'; end if;
 begin
  perform * from public.hazard_observations;
  raise exception 'Reporter identities exposed';
 exception when insufficient_privilege then null; end;
 begin
  insert into public.map_hazards(latitude,longitude) values(0,0);
  raise exception 'Direct marker insert allowed';
 exception when insufficient_privilege then null; end;
 if exists(select 1 from storage.objects where bucket_id='roadwatch-captures' and (storage.foldername(name))[1]='00000000-0000-4000-8000-000000009a01') then raise exception 'Other account can read photos'; end if;
end $$;
reset role;
do $$ begin
 if (select count(*) from public.hazard_observations where report_id in ('10000000-0000-4000-8000-000000009a01','10000000-0000-4000-8000-000000009a02'))<>3 then raise exception 'Retries duplicated observations'; end if;
end $$;
set local role anon;
do $$ begin
 begin
  perform * from public.map_hazards;
  raise exception 'Anonymous read allowed';
 exception when insufficient_privilege then null; end;
end $$;
reset role;
rollback;
do $$ begin
 if exists(select 1 from auth.users where id in ('00000000-0000-4000-8000-000000009a01','00000000-0000-4000-8000-000000009a02')) then raise exception 'Synthetic users were not rolled back'; end if;
 if exists(select 1 from public.road_reports where model_version='transactional-acceptance') then raise exception 'Synthetic reports were not rolled back'; end if;
end $$;
select * from (values
 ('Cross-account shared locations','PASS'),
 ('Private reports and photos','PASS'),
 ('Nearby sightings grouped','PASS'),
 ('Distinct reporting accounts','PASS'),
 ('Retries remain idempotent','PASS'),
 ('Direct marker writes denied','PASS'),
 ('Anonymous reads denied','PASS'),
 ('Synthetic test data rolled back','PASS')
) as checks(check_name,result);
