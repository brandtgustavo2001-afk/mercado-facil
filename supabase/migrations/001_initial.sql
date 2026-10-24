begin;
create extension if not exists pgcrypto with schema extensions;

create table public.profiles (
  id uuid primary key references auth.users on delete cascade,
  display_name text not null default '', avatar_url text,
  created_at timestamptz not null default now()
);
create table public.shopping_lists (
  id uuid primary key, owner_id uuid not null references auth.users,
  data jsonb not null, version bigint not null default 1,
  created_at timestamptz not null default now(), updated_at timestamptz not null default now()
);
create table public.list_members (
  id uuid primary key default gen_random_uuid(),
  list_id uuid not null references public.shopping_lists on delete cascade,
  user_id uuid not null references auth.users on delete cascade,
  role text not null check (role in ('owner','editor','viewer')),
  joined_at timestamptz not null default now(), unique(list_id,user_id)
);
create index on public.list_members(user_id);
create table public.shopping_items (
  id uuid primary key, list_id uuid not null references public.shopping_lists,
  data jsonb not null, version bigint not null default 1,
  updated_by uuid not null references auth.users,
  created_at timestamptz not null default now(), updated_at timestamptz not null default now()
);
create index on public.shopping_items(list_id);
create table public.categories (
  id uuid primary key default gen_random_uuid(), owner_id uuid references auth.users,
  name text not null, icon text, color text
);
create table public.list_invitations (
  id uuid primary key default gen_random_uuid(), list_id uuid not null references public.shopping_lists,
  token_hash text not null unique, role text not null check (role in ('editor','viewer')),
  expires_at timestamptz not null, max_uses integer not null default 1,
  uses_count integer not null default 0, revoked_at timestamptz,
  created_by uuid not null references auth.users
);
create table public.shopping_activity (
  id uuid primary key default gen_random_uuid(), list_id uuid not null references public.shopping_lists,
  item_id uuid, actor_id uuid not null references auth.users,
  action text not null, details jsonb not null default '{}', created_at timestamptz not null default now()
);
create index on public.shopping_activity(list_id,created_at desc);
create table public.applied_operations (
  operation_id uuid primary key, actor_id uuid not null references auth.users,
  list_id uuid not null references public.shopping_lists,
  result jsonb not null, created_at timestamptz not null default now()
);

create function public.member_role(target_list uuid) returns text
language sql stable security definer set search_path = '' as $$
  select role from public.list_members where list_id=target_list and user_id=auth.uid()
$$;
revoke all on function public.member_role(uuid) from public;
grant execute on function public.member_role(uuid) to authenticated;

alter table public.profiles enable row level security;
alter table public.shopping_lists enable row level security;
alter table public.list_members enable row level security;
alter table public.shopping_items enable row level security;
alter table public.categories enable row level security;
alter table public.list_invitations enable row level security;
alter table public.shopping_activity enable row level security;
alter table public.applied_operations enable row level security;

create policy profile_self on public.profiles for select to authenticated using (id=auth.uid());
create policy lists_read on public.shopping_lists for select to authenticated using (public.member_role(id) is not null);
create policy items_read on public.shopping_items for select to authenticated using (public.member_role(list_id) is not null);
create policy members_read on public.list_members for select to authenticated using (public.member_role(list_id) is not null);
create policy activity_read on public.shopping_activity for select to authenticated using (public.member_role(list_id) is not null);
create policy categories_read on public.categories for select to authenticated using (owner_id is null or owner_id=auth.uid());
-- All writes use checked RPCs. Invitations and idempotency records have no direct API policy.
revoke all on all tables in schema public from anon;
revoke insert, update, delete, truncate, references, trigger on all tables in schema public from authenticated;
grant select on public.profiles,public.shopping_lists,public.shopping_items,public.list_members,public.shopping_activity,public.categories to authenticated;

create function public.apply_operation(op_id uuid, entity text, entity_id uuid, target_list uuid, patch jsonb, base jsonb)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare
  actor uuid := auth.uid(); current_data jsonb; merged jsonb; field text;
  previous jsonb; current_version bigint; allowed text[]; current_role text;
begin
  if actor is null then raise exception 'Autenticação necessária'; end if;
  if entity not in ('list','item') or jsonb_typeof(patch) <> 'object' or jsonb_typeof(base) <> 'object' or octet_length(patch::text)>16000 then raise exception 'Operação inválida'; end if;
  -- One transaction per list serializes permission, idempotency and field comparisons.
  perform pg_advisory_xact_lock(hashtextextended(target_list::text,0));
  current_role := public.member_role(target_list);
  if exists(select 1 from public.shopping_lists where id=target_list) and coalesce(current_role,'') not in ('owner','editor') then raise exception 'Sem permissão de edição'; end if;
  select result into previous from public.applied_operations where operation_id=op_id and actor_id=actor and list_id=target_list;
  if found then return previous; end if;
  if exists(select 1 from public.applied_operations where operation_id=op_id) then raise exception 'UUID de operação reutilizado'; end if;
  if entity='list' then
    if entity_id<>target_list then raise exception 'Lista inválida'; end if;
    allowed := array['id','name','description','market','budget_cents','status','deleted'];
    select data,version into current_data,current_version from public.shopping_lists where id=entity_id for update;
  else
    if current_role is null then raise exception 'Sem acesso à lista'; end if;
    if exists(select 1 from public.shopping_lists where id=target_list and data->>'deleted'='true') then raise exception 'Lista excluída'; end if;
    allowed := array['id','list_id','name','quantity','unit','estimated_cents','actual_cents','category','brand','notes','purchased','deleted'];
    select data,version into current_data,current_version from public.shopping_items where id=entity_id and list_id=target_list for update;
    if current_data is null and exists(select 1 from public.shopping_items where id=entity_id) then raise exception 'Produto de outra lista'; end if;
  end if;
  for field in select jsonb_object_keys(patch) loop
    if not field=any(allowed) then raise exception 'Campo inválido: %',field; end if;
    if current_data is not null and current_data->field is distinct from base->field and current_data->field is distinct from patch->field then
      return jsonb_build_object('conflict',true,'current',current_data,'field',field,'version',current_version);
    end if;
  end loop;
  if current_data->>'deleted'='true' and coalesce(patch->>'deleted','true')<>'true' then raise exception 'Registro excluído não pode reaparecer'; end if;
  merged := coalesce(current_data,'{}'::jsonb)||patch;
  if merged->>'id'<>entity_id::text or merged->>'id' is null or coalesce(length(trim(merged->>'name')),0) not between 1 and 200 then raise exception 'Identidade/nome inválido'; end if;
  if entity='list' then
    if (merged->>'budget_cents')::numeric not between 0 and 900000000000 or (merged->>'budget_cents')::numeric<>trunc((merged->>'budget_cents')::numeric) or merged->>'status' not in ('active','completed','archived') then raise exception 'Valores da lista inválidos'; end if;
    if current_data is null then
      insert into public.shopping_lists(id,owner_id,data) values(entity_id,actor,merged);
      insert into public.list_members(list_id,user_id,role) values(entity_id,actor,'owner');
    else
      update public.shopping_lists set data=merged,version=version+1,updated_at=now() where id=entity_id;
    end if;
  else
    if merged->>'list_id' is distinct from target_list::text or (merged->>'quantity')::numeric not between 0.001 and 1000000
      or (merged->>'estimated_cents')::numeric not between 0 and 900000000000
      or (merged->>'estimated_cents')::numeric<>trunc((merged->>'estimated_cents')::numeric)
      or (merged->>'actual_cents' is not null and ((merged->>'actual_cents')::numeric not between 0 and 900000000000 or (merged->>'actual_cents')::numeric<>trunc((merged->>'actual_cents')::numeric))) then raise exception 'Valores do produto inválidos'; end if;
    if current_data is null then insert into public.shopping_items(id,list_id,data,updated_by) values(entity_id,target_list,merged,actor);
    else update public.shopping_items set data=merged,version=version+1,updated_by=actor,updated_at=now() where id=entity_id; end if;
  end if;
  insert into public.shopping_activity(list_id,item_id,actor_id,action,details) values(target_list,case when entity='item' then entity_id end,actor,entity||'_updated',jsonb_build_object('fields',patch));
  previous := jsonb_build_object('ok',true);
  insert into public.applied_operations(operation_id,actor_id,list_id,result) values(op_id,actor,target_list,previous);
  return previous;
end $$;

create function public.create_invitation(target_list uuid, member_role text default 'editor') returns jsonb
language plpgsql security definer set search_path = '' as $$
declare secret text;
begin
  if public.member_role(target_list) is distinct from 'owner' then raise exception 'Somente o proprietário'; end if;
  if member_role not in ('editor','viewer') then raise exception 'Função inválida'; end if;
  secret := encode(extensions.gen_random_bytes(32),'hex');
  insert into public.list_invitations(list_id,token_hash,role,expires_at,created_by) values(target_list,encode(extensions.digest(secret,'sha256'),'hex'),member_role,now()+interval '24 hours',auth.uid());
  return jsonb_build_object('token',secret);
end $$;

create function public.accept_invitation(invitation_token text) returns jsonb
language plpgsql security definer set search_path = '' as $$
declare invitation public.list_invitations;
begin
  if auth.uid() is null or length(invitation_token)<>64 then raise exception 'Convite inválido'; end if;
  select * into invitation from public.list_invitations where token_hash=encode(extensions.digest(invitation_token,'sha256'),'hex') for update;
  if not found or invitation.revoked_at is not null or invitation.expires_at<=now() or invitation.uses_count>=invitation.max_uses then raise exception 'Convite expirado, revogado ou utilizado'; end if;
  if exists(select 1 from public.shopping_lists where id=invitation.list_id and data->>'deleted'='true') then raise exception 'Lista excluída'; end if;
  insert into public.list_members(list_id,user_id,role) values(invitation.list_id,auth.uid(),invitation.role) on conflict(list_id,user_id) do nothing;
  update public.list_invitations set uses_count=uses_count+1 where id=invitation.id;
  insert into public.shopping_activity(list_id,actor_id,action) values(invitation.list_id,auth.uid(),'member_joined');
  return jsonb_build_object('list_id',invitation.list_id);
end $$;

create function public.remove_member(target_list uuid,target_user uuid) returns void
language plpgsql security definer set search_path = '' as $$
begin
  perform pg_advisory_xact_lock(hashtextextended(target_list::text,0));
  if public.member_role(target_list) is distinct from 'owner' or target_user=auth.uid() then raise exception 'Sem permissão'; end if;
  delete from public.list_members where list_id=target_list and user_id=target_user and role<>'owner';
  insert into public.shopping_activity(list_id,actor_id,action,details) values(target_list,auth.uid(),'member_removed',jsonb_build_object('user_id',target_user));
end $$;
create function public.revoke_invitations(target_list uuid) returns void
language plpgsql security definer set search_path = '' as $$
begin
  if public.member_role(target_list) is distinct from 'owner' then raise exception 'Somente o proprietário'; end if;
  update public.list_invitations set revoked_at=now() where list_id=target_list and revoked_at is null;
end $$;

revoke all on function public.apply_operation(uuid,text,uuid,uuid,jsonb,jsonb) from public;
revoke all on function public.create_invitation(uuid,text) from public;
revoke all on function public.accept_invitation(text) from public;
revoke all on function public.remove_member(uuid,uuid) from public;
revoke all on function public.revoke_invitations(uuid) from public;
grant execute on function public.apply_operation(uuid,text,uuid,uuid,jsonb,jsonb), public.create_invitation(uuid,text), public.accept_invitation(text), public.remove_member(uuid,uuid), public.revoke_invitations(uuid) to authenticated;

alter publication supabase_realtime add table public.shopping_lists,public.shopping_items,public.list_members;
commit;
