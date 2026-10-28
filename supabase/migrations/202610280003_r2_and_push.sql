begin;
create extension if not exists pg_net with schema extensions;
create extension if not exists pg_cron;
alter table public.item_photos add column if not exists legacy_present boolean generated always as (length(photo_data)>0) stored;
alter table public.item_photos add column if not exists object_key text not null default '';
alter table public.item_photos add column if not exists content_hash text not null default '';
create or replace function public.set_r2_photo(target_list uuid,target_item uuid,object_path text,content_hash text)
returns void language plpgsql security definer set search_path='' as $$
begin
 if auth.uid() is null or coalesce(public.member_role(target_list),'') not in ('owner','editor') then raise exception 'Sem permissão'; end if;
 if not exists(select 1 from public.shopping_items where id=target_item and list_id=target_list) then raise exception 'Produto inválido'; end if;
 if not ((object_path='' and content_hash='') or (content_hash ~ '^[a-f0-9]{64}$' and object_path=target_list::text||'/'||target_item::text||'/'||content_hash||'.jpg')) then raise exception 'Foto inválida'; end if;
 insert into public.item_photos(item_id,list_id,photo_data,object_key,content_hash,updated_at)
 values(target_item,target_list,'',object_path,content_hash,now())
 on conflict(item_id) do update set photo_data='',object_key=excluded.object_key,content_hash=excluded.content_hash,updated_at=excluded.updated_at;
end $$;
revoke all on function public.set_r2_photo(uuid,uuid,text,text) from public;
grant execute on function public.set_r2_photo(uuid,uuid,text,text) to authenticated;
create table public.push_devices(installation uuid primary key,user_id uuid not null references auth.users on delete cascade,token text not null unique check(length(token) between 20 and 4096),enabled boolean not null default true,updated_at timestamptz not null default now());
alter table public.push_devices enable row level security;
revoke all on public.push_devices from anon,authenticated;
create function public.register_push(installation uuid,device_token text,notifications_enabled boolean) returns void language plpgsql security definer set search_path='' as $$
begin
 if auth.uid() is null or length(device_token) not between 20 and 4096 then raise exception 'Dispositivo inválido'; end if;
 delete from public.push_devices where token=device_token and push_devices.installation<>register_push.installation;
 insert into public.push_devices values(installation,auth.uid(),device_token,notifications_enabled,now()) on conflict on constraint push_devices_pkey do update set user_id=auth.uid(),token=excluded.token,enabled=excluded.enabled,updated_at=now();
end $$;
create function public.unregister_push(installation uuid) returns void language plpgsql security definer set search_path='' as $$
begin
 delete from public.push_devices where push_devices.installation=unregister_push.installation and user_id=auth.uid();
end $$;
revoke all on function public.register_push(uuid,text,boolean),public.unregister_push(uuid) from public;
grant execute on function public.register_push(uuid,text,boolean),public.unregister_push(uuid) to authenticated;
create table public.push_outbox(id uuid primary key default gen_random_uuid(),capability text not null default encode(extensions.gen_random_bytes(32),'hex'),list_id uuid not null references public.shopping_lists,actor uuid not null,kind text not null,created_at timestamptz not null default now(),last_attempt timestamptz,attempts integer not null default 0,complete boolean not null default false);
create table public.push_delivered(event_id uuid references public.push_outbox on delete cascade,installation uuid not null,primary key(event_id,installation));
alter table public.push_outbox enable row level security;
alter table public.push_delivered enable row level security;
revoke all on public.push_outbox,public.push_delivered from anon,authenticated;
create function public.read_push_delivery(event_id uuid,capability text) returns jsonb language plpgsql security definer set search_path='' as $$
declare e public.push_outbox;
begin
 select * into e from public.push_outbox p where p.id=event_id and p.capability=read_push_delivery.capability and p.created_at>now()-interval '1 day';
 if not found then raise exception 'Evento inválido'; end if;
 return jsonb_build_object('list_id',e.list_id,'kind',e.kind,'devices',coalesce((select jsonb_agg(jsonb_build_object('installation',d.installation,'token',d.token)) from public.push_devices d join public.list_members m on m.user_id=d.user_id and m.list_id=e.list_id where d.enabled and d.user_id<>e.actor and m.joined_at<=e.created_at and not exists(select 1 from public.push_delivered done where done.event_id=e.id and done.installation=d.installation)),'[]'::jsonb));
end $$;
create function public.ack_push_delivery(event_id uuid,capability text,installation uuid,invalid boolean default false) returns void language plpgsql security definer set search_path='' as $$
declare e public.push_outbox;
begin
 select * into e from public.push_outbox p where p.id=event_id and p.capability=ack_push_delivery.capability and p.created_at>now()-interval '1 day';
 if not found then raise exception 'Evento inválido'; end if;
 insert into public.push_delivered values(event_id,installation) on conflict do nothing;
 if invalid then update public.push_devices d set enabled=false where d.installation=ack_push_delivery.installation and d.user_id<>e.actor and exists(select 1 from public.list_members m where m.list_id=e.list_id and m.user_id=d.user_id); end if;
end $$;
revoke all on function public.read_push_delivery(uuid,text),public.ack_push_delivery(uuid,text,uuid,boolean) from public;
grant execute on function public.read_push_delivery(uuid,text),public.ack_push_delivery(uuid,text,uuid,boolean) to anon,authenticated;
create function public.queue_shared_push() returns trigger language plpgsql security definer set search_path='' as $$
declare e public.push_outbox; event_kind text;
begin
 if auth.uid() is null then return new; end if;
 if tg_table_name='shopping_items' then
  if new.data->>'deleted'='true' then return new; end if;
  if tg_op='UPDATE' and new.data=old.data then return new; end if;
  event_kind:=case when tg_op='INSERT' then 'item_added' else 'item_changed' end;
 else
  if new.created_at<(extract(epoch from now()-interval '5 minutes')*1000)::bigint then return new; end if;
  event_kind:='purchase';
 end if;
 insert into public.push_outbox(list_id,actor,kind,last_attempt,attempts) values(new.list_id,auth.uid(),event_kind,now(),1) returning * into e;
 perform net.http_post(url:='https://mercado-facil-api.brandtgustavo2001.workers.dev/dispatch',headers:='{"Content-Type":"application/json"}'::jsonb,body:=jsonb_build_object('event_id',e.id,'capability',e.capability),timeout_milliseconds:=10000);
 return new;
end $$;
revoke all on function public.queue_shared_push() from public;
create trigger item_push after insert or update on public.shopping_items for each row execute function public.queue_shared_push();
create trigger purchase_push after insert on public.shared_purchases for each row execute function public.queue_shared_push();
create function public.retry_push_events() returns void language plpgsql security definer set search_path='' as $$
declare e public.push_outbox;
begin
 for e in select * from public.push_outbox where not complete and attempts<15 and created_at>now()-interval '1 day' and last_attempt<now()-interval '1 minute' order by created_at limit 20 loop
  if not exists(select 1 from public.push_devices d join public.list_members m on m.user_id=d.user_id and m.list_id=e.list_id where d.enabled and d.user_id<>e.actor and m.joined_at<=e.created_at and not exists(select 1 from public.push_delivered done where done.event_id=e.id and done.installation=d.installation)) then
   update public.push_outbox set complete=true where id=e.id;
  else
   perform net.http_post(url:='https://mercado-facil-api.brandtgustavo2001.workers.dev/dispatch',headers:='{"Content-Type":"application/json"}'::jsonb,body:=jsonb_build_object('event_id',e.id,'capability',e.capability),timeout_milliseconds:=10000);
   update public.push_outbox set attempts=attempts+1,last_attempt=now() where id=e.id;
  end if;
 end loop;
 delete from public.push_outbox where created_at<now()-interval '2 days';
end $$;
revoke all on function public.retry_push_events() from public;
select cron.schedule('mercado-facil-push-retry','* * * * *','select public.retry_push_events()');
commit;
