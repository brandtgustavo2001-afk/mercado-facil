begin;
create table if not exists public.item_photos (
 item_id uuid primary key references public.shopping_items(id) on delete cascade,
 list_id uuid not null references public.shopping_lists(id),
 photo_data text not null check (octet_length(photo_data) <= 524288),
 updated_at timestamptz not null default now()
);
alter table public.item_photos enable row level security;
create policy photos_read on public.item_photos for select to authenticated using (public.member_role(list_id) is not null);
create policy photos_insert on public.item_photos for insert to authenticated with check (
 public.member_role(list_id) in ('owner','editor') and exists(select 1 from public.shopping_items i where i.id=item_id and i.list_id=item_photos.list_id));
create policy photos_update on public.item_photos for update to authenticated using (public.member_role(list_id) in ('owner','editor')) with check (
 public.member_role(list_id) in ('owner','editor') and exists(select 1 from public.shopping_items i where i.id=item_id and i.list_id=item_photos.list_id));
revoke all on public.item_photos from anon;
grant select,insert,update on public.item_photos to authenticated;
commit;
