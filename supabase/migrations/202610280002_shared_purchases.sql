begin;
alter table public.item_photos drop constraint item_photos_photo_data_check;
alter table public.item_photos add constraint item_photos_photo_data_check check(octet_length(photo_data)<=2000000);
create table public.shared_purchases(id uuid primary key,list_id uuid not null references public.shopping_lists(id),list_name text not null,created_at bigint not null,items jsonb not null,total_cents bigint not null,item_count integer not null);
create table public.purchase_receipts(id uuid primary key,list_id uuid not null references public.shopping_lists(id));
alter table public.shared_purchases enable row level security;
alter table public.purchase_receipts enable row level security;
create policy purchases_read on public.shared_purchases for select to authenticated using(public.member_role(list_id) is not null);
grant select on public.shared_purchases to authenticated;
revoke all on public.purchase_receipts from anon,authenticated;
create function public.save_purchase(purchase jsonb) returns jsonb language plpgsql security definer set search_path='' as $$
declare lid uuid := (purchase->>'list_id')::uuid; pid uuid := (purchase->>'id')::uuid;
begin
 if auth.uid() is null or coalesce(public.member_role(lid),'') not in ('owner','editor') then raise exception 'Sem permissão'; end if;
 if jsonb_typeof(purchase->'items') is distinct from 'array' or octet_length(purchase::text)>1000000 then raise exception 'Compra inválida'; end if;
 perform pg_advisory_xact_lock(hashtextextended(lid::text,0));
 if exists(select 1 from public.purchase_receipts where id=pid and list_id=lid) then return '{"ok":true}'::jsonb; end if;
 insert into public.purchase_receipts values(pid,lid);
 insert into public.shared_purchases values(pid,lid,purchase->>'list_name',(purchase->>'created_at')::bigint,purchase->'items',(purchase->>'total_cents')::bigint,(purchase->>'item_count')::integer);
 delete from public.shared_purchases where list_id=lid and id not in(select id from public.shared_purchases where list_id=lid order by created_at desc,id desc limit 10);
 return '{"ok":true}'::jsonb;
end $$;
revoke all on function public.save_purchase(jsonb) from public;
grant execute on function public.save_purchase(jsonb) to authenticated;
commit;
