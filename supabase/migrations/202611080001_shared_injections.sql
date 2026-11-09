begin;

-- Health details stay out of shopping_activity and are visible only to members
-- of the specific list explicitly selected for each injection routine.
create table public.injection_schedules (
  id uuid primary key,
  list_id uuid not null references public.shopping_lists on delete cascade,
  data jsonb not null,
  version bigint not null default 1 check (version > 0),
  updated_by uuid not null references auth.users,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique(id,list_id)
);
create index injection_schedules_list_id_idx on public.injection_schedules(list_id);

create table public.injection_records (
  id uuid primary key,
  list_id uuid not null,
  schedule_id uuid not null,
  data jsonb not null,
  version bigint not null default 1 check (version > 0),
  updated_by uuid not null references auth.users,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  foreign key(schedule_id,list_id) references public.injection_schedules(id,list_id) on delete cascade
);
create index injection_records_list_id_idx on public.injection_records(list_id);
create index injection_records_schedule_id_idx on public.injection_records(schedule_id);

alter table public.injection_schedules enable row level security;
alter table public.injection_records enable row level security;
create policy injection_schedules_read on public.injection_schedules
  for select to authenticated using (
    public.member_role(public.injection_schedules.list_id) is not null and exists(
      select 1 from public.shopping_lists l where l.id=public.injection_schedules.list_id and l.data->>'deleted' is distinct from 'true'
    )
  );
create policy injection_records_read on public.injection_records
  for select to authenticated using (
    public.member_role(public.injection_records.list_id) is not null and exists(
      select 1 from public.shopping_lists l where l.id=public.injection_records.list_id and l.data->>'deleted' is distinct from 'true'
    )
  );
revoke all on public.injection_schedules,public.injection_records from anon,authenticated;
grant select on public.injection_schedules,public.injection_records to authenticated;

create function public.apply_injection_operation(
  op_id uuid, entity text, entity_id uuid, target_list uuid, patch jsonb, base jsonb
) returns jsonb language plpgsql security definer set search_path = '' as $$
declare
  actor uuid := auth.uid();
  access_role text;
  current_data jsonb;
  merged jsonb;
  previous jsonb;
  current_version bigint;
  field text;
  allowed text[];
begin
  if actor is null then raise exception 'Autenticação necessária'; end if;
  if op_id is null or entity_id is null or target_list is null or entity is null or entity not in ('schedule','record')
    or jsonb_typeof(patch) is distinct from 'object' or jsonb_typeof(base) is distinct from 'object'
    or octet_length(patch::text)>24000 or octet_length(base::text)>24000 then raise exception 'Operação inválida'; end if;

  perform pg_advisory_xact_lock(hashtextextended(target_list::text,0));
  access_role := public.member_role(target_list);
  if coalesce(access_role,'') not in ('owner','editor') then raise exception 'Sem permissão de edição'; end if;
  if exists(select 1 from public.shopping_lists where id=target_list and data->>'deleted'='true')
    or not exists(select 1 from public.shopping_lists where id=target_list) then raise exception 'Lista indisponível'; end if;

  select result into previous from public.applied_operations
    where operation_id=op_id and actor_id=actor and list_id=target_list;
  if found then return previous; end if;
  if exists(select 1 from public.applied_operations where operation_id=op_id) then raise exception 'UUID de operação reutilizado'; end if;

  if entity='schedule' then
    allowed := array['id','name','person_name','medication','dose','route','interval_value','interval_unit',
      'next_due_at','responsible','notes','active','deleted'];
    select data,version into current_data,current_version from public.injection_schedules
      where id=entity_id and list_id=target_list for update;
    if current_data is null and exists(select 1 from public.injection_schedules where id=entity_id) then
      raise exception 'Rotina vinculada a outra lista';
    end if;
  else
    allowed := array['id','schedule_id','scheduled_for_at','administered_at','dose','application_site','notes','deleted'];
    select data,version into current_data,current_version from public.injection_records
      where id=entity_id and list_id=target_list for update;
    if current_data is null and exists(select 1 from public.injection_records where id=entity_id) then
      raise exception 'Registro vinculado a outra lista';
    end if;
  end if;

  for field in select jsonb_object_keys(patch) loop
    if not field=any(allowed) then raise exception 'Campo inválido'; end if;
    if current_data is not null and current_data->field is distinct from base->field
      and current_data->field is distinct from patch->field then
      return jsonb_build_object('conflict',true,'current',current_data,'field',field,'version',current_version);
    end if;
  end loop;
  if current_data->>'deleted'='true' and coalesce(patch->>'deleted','true')<>'true' then
    raise exception 'Registro excluído não pode reaparecer';
  end if;

  merged := coalesce(current_data,'{}'::jsonb)||patch;
  if jsonb_typeof(merged->'id') is distinct from 'string'
    or merged->>'id' is distinct from entity_id::text
    or jsonb_typeof(merged->'deleted') is distinct from 'boolean' then raise exception 'Identidade inválida'; end if;

  if entity='schedule' then
    if jsonb_typeof(merged->'name') is distinct from 'string'
      or coalesce(length(trim(merged->>'name')),0) not between 1 and 120
      or jsonb_typeof(merged->'person_name') is distinct from 'string'
      or coalesce(length(trim(merged->>'person_name')),0) not between 1 and 120
      or jsonb_typeof(merged->'medication') is distinct from 'string' or length(merged->>'medication')>120
      or jsonb_typeof(merged->'dose') is distinct from 'string' or length(merged->>'dose')>80
      or jsonb_typeof(merged->'route') is distinct from 'string' or length(merged->>'route')>120
      or jsonb_typeof(merged->'responsible') is distinct from 'string' or length(merged->>'responsible')>120
      or jsonb_typeof(merged->'notes') is distinct from 'string' or length(merged->>'notes')>2000
      or jsonb_typeof(merged->'interval_value') is distinct from 'number'
      or jsonb_typeof(merged->'interval_unit') is distinct from 'string'
      or jsonb_typeof(merged->'next_due_at') is distinct from 'number'
      or jsonb_typeof(merged->'active') is distinct from 'boolean' then raise exception 'Campos da rotina inválidos'; end if;
    if (merged->>'interval_value')::numeric not between 1 and 8760
      or (merged->>'interval_value')::numeric<>trunc((merged->>'interval_value')::numeric)
      or merged->>'interval_unit' not in ('HOURS','DAYS','WEEKS','MONTHS')
      or (merged->>'next_due_at')::numeric not between 0 and 253402300799999
      or (merged->>'next_due_at')::numeric<>trunc((merged->>'next_due_at')::numeric) then raise exception 'Intervalo ou data inválida'; end if;

    if current_data is null then
      insert into public.injection_schedules(id,list_id,data,updated_by) values(entity_id,target_list,merged,actor);
    else
      update public.injection_schedules set data=merged,version=version+1,updated_by=actor,updated_at=now()
        where id=entity_id and list_id=target_list;
    end if;
    if merged->>'deleted'='true' then
      update public.injection_records r set data=jsonb_set(r.data,'{deleted}','true'::jsonb,true),
        version=r.version+1,updated_by=actor,updated_at=now()
        where r.schedule_id=entity_id and r.list_id=target_list and r.data->>'deleted' is distinct from 'true';
    end if;
  else
    if jsonb_typeof(merged->'schedule_id') is distinct from 'string'
      or jsonb_typeof(merged->'scheduled_for_at') is distinct from 'number'
      or jsonb_typeof(merged->'administered_at') is distinct from 'number'
      or jsonb_typeof(merged->'dose') is distinct from 'string' or length(merged->>'dose')>80
      or jsonb_typeof(merged->'application_site') is distinct from 'string' or length(merged->>'application_site')>120
      or jsonb_typeof(merged->'notes') is distinct from 'string' or length(merged->>'notes')>2000 then raise exception 'Campos do registro inválidos'; end if;
    if (merged->>'scheduled_for_at')::numeric not between 0 and 253402300799999
      or (merged->>'administered_at')::numeric not between 0 and (extract(epoch from now())*1000)::bigint
      or (merged->>'scheduled_for_at')::numeric<>trunc((merged->>'scheduled_for_at')::numeric)
      or (merged->>'administered_at')::numeric<>trunc((merged->>'administered_at')::numeric)
      or not exists(select 1 from public.injection_schedules s where s.id=(merged->>'schedule_id')::uuid
        and s.list_id=target_list and s.data->>'deleted'='false') then raise exception 'Rotina ou data do registro inválida'; end if;
    if current_data is null then
      insert into public.injection_records(id,list_id,schedule_id,data,updated_by)
        values(entity_id,target_list,(merged->>'schedule_id')::uuid,merged,actor);
    else
      if (merged->>'schedule_id')::uuid is distinct from (current_data->>'schedule_id')::uuid then
        raise exception 'O histórico não pode mudar de rotina';
      end if;
      update public.injection_records set data=merged,version=version+1,updated_by=actor,updated_at=now()
        where id=entity_id and list_id=target_list;
    end if;
  end if;

  -- Do not write health values to shopping_activity or push notification logs.
  previous := jsonb_build_object('ok',true);
  insert into public.applied_operations(operation_id,actor_id,list_id,result)
    values(op_id,actor,target_list,previous);
  return previous;
end $$;

create function public.injection_sync_ready() returns boolean
language sql stable security invoker set search_path = '' as $$
  select auth.uid() is not null
$$;
revoke all on function public.apply_injection_operation(uuid,text,uuid,uuid,jsonb,jsonb) from public;
revoke all on function public.injection_sync_ready() from public;
grant execute on function public.apply_injection_operation(uuid,text,uuid,uuid,jsonb,jsonb),
  public.injection_sync_ready() to authenticated;
alter publication supabase_realtime add table public.injection_schedules,public.injection_records;

commit;
