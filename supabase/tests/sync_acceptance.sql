-- Execute AFTER 001_initial.sql, in a disposable Supabase project, as postgres.
-- Uses the real RPCs and auth.uid(); every test record is rolled back.
begin;
insert into auth.users(id,email) values
 ('10000000-0000-0000-0000-000000000001','mf-owner@example.invalid'),
 ('10000000-0000-0000-0000-000000000002','mf-editor@example.invalid'),
 ('10000000-0000-0000-0000-000000000003','mf-viewer@example.invalid');

do $$
<<sync_acceptance>>
declare
  owner_id uuid := '10000000-0000-0000-0000-000000000001';
  editor_id uuid := '10000000-0000-0000-0000-000000000002';
  viewer_id uuid := '10000000-0000-0000-0000-000000000003';
  list_id uuid := '20000000-0000-0000-0000-000000000001';
  item_id uuid := '30000000-0000-0000-0000-000000000001';
  op uuid; response jsonb; invite text; audit_count bigint; denied boolean;
begin
  perform set_config('request.jwt.claim.sub',owner_id::text,true);
  perform public.apply_operation(gen_random_uuid(),'list',list_id,list_id,
    jsonb_build_object('id',list_id,'name','Compras','budget_cents',50000,'status','active','deleted',false),'{}');
  insert into public.list_members(list_id,user_id,role) values(list_id,editor_id,'editor'),(list_id,viewer_id,'viewer');
  op := gen_random_uuid();
  perform public.apply_operation(op,'item',item_id,list_id,
    jsonb_build_object('id',item_id,'list_id',list_id,'name','Arroz','quantity','1','unit','un','estimated_cents',1000,'actual_cents',null,'purchased',false,'deleted',false),'{}');
  select count(*) into audit_count from public.shopping_activity where shopping_activity.list_id=sync_acceptance.list_id;
  -- Same operation UUID must not add another audit event.
  perform public.apply_operation(op,'item',item_id,list_id,'{}','{}');
  if (select count(*) from public.shopping_activity where shopping_activity.list_id=sync_acceptance.list_id)<>audit_count then raise exception 'Idempotência falhou'; end if;

  perform public.apply_operation(gen_random_uuid(),'item',item_id,list_id,'{"estimated_cents":1200}','{"estimated_cents":1000}');
  perform set_config('request.jwt.claim.sub',editor_id::text,true);
  response := public.apply_operation(gen_random_uuid(),'item',item_id,list_id,'{"quantity":"2"}','{"quantity":"1"}');
  if response->>'ok'<>'true' or not exists(select 1 from public.shopping_items where id=item_id and data->>'quantity'='2' and data->>'estimated_cents'='1200') then raise exception 'Merge independente falhou'; end if;
  response := public.apply_operation(gen_random_uuid(),'item',item_id,list_id,'{"estimated_cents":1500}','{"estimated_cents":1000}');
  if response->>'conflict'<>'true' then raise exception 'Conflito não detectado'; end if;
  perform public.apply_operation(gen_random_uuid(),'item',item_id,list_id,'{"purchased":true}','{"purchased":false}');
  if not exists(select 1 from public.shopping_items where id=item_id and data->>'purchased'='true') then raise exception 'Compra compartilhada falhou'; end if;

  perform set_config('request.jwt.claim.sub',viewer_id::text,true);
  denied := false;
  begin perform public.apply_operation(gen_random_uuid(),'item',item_id,list_id,'{"quantity":"9"}','{"quantity":"2"}');
  exception when others then denied := true; end;
  if not denied then raise exception 'Visualizador conseguiu editar'; end if;

  perform set_config('request.jwt.claim.sub',owner_id::text,true);
  perform public.remove_member(list_id,editor_id);
  perform set_config('request.jwt.claim.sub',editor_id::text,true);
  denied := false;
  begin perform public.apply_operation(gen_random_uuid(),'item',item_id,list_id,'{"quantity":"9"}','{"quantity":"2"}');
  exception when others then denied := true; end;
  if not denied then raise exception 'Participante removido conseguiu editar'; end if;

  perform set_config('request.jwt.claim.sub',owner_id::text,true);
  invite := public.create_invitation(list_id,'editor')->>'token';
  perform public.revoke_invitations(list_id);
  perform set_config('request.jwt.claim.sub',editor_id::text,true);
  denied := false;
  begin perform public.accept_invitation(invite); exception when others then denied := true; end;
  if not denied then raise exception 'Convite revogado aceito'; end if;
  perform set_config('request.jwt.claim.sub',owner_id::text,true);
  invite := public.create_invitation(list_id,'editor')->>'token';
  update public.list_invitations set expires_at=now()-interval '1 second' where token_hash=encode(extensions.digest(invite,'sha256'),'hex');
  perform set_config('request.jwt.claim.sub',editor_id::text,true);
  denied := false;
  begin perform public.accept_invitation(invite); exception when others then denied := true; end;
  if not denied then raise exception 'Convite expirado aceito'; end if;

  perform set_config('request.jwt.claim.sub',owner_id::text,true);
  perform public.apply_operation(gen_random_uuid(),'item',item_id,list_id,'{"deleted":true}','{"deleted":false}');
  denied := false;
  begin perform public.apply_operation(gen_random_uuid(),'item',item_id,list_id,'{"deleted":false}','{"deleted":true}'); exception when others then denied := true; end;
  if not denied then raise exception 'Tombstone ressuscitou'; end if;
  raise notice 'RPC checks passed';
end $$;

-- A removed editor must see no data through RLS, even using direct REST-style SELECT.
select set_config('request.jwt.claim.sub','10000000-0000-0000-0000-000000000002',true);
set local role authenticated;
do $$ begin
  if exists(select 1 from public.shopping_lists where id='20000000-0000-0000-0000-000000000001') then raise exception 'RLS permite leitura após remoção'; end if;
  if exists(select 1 from public.shopping_items where list_id='20000000-0000-0000-0000-000000000001') then raise exception 'RLS permite leitura de produtos após remoção'; end if;
end $$;
reset role;
rollback;
