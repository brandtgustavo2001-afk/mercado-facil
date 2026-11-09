-- Run after the shared-injections migration in a disposable Supabase project.
-- All temporary rows are rolled back.
begin;
insert into auth.users(id,email) values
 ('11000000-0000-0000-0000-000000000001','mf-injection-owner@example.invalid'),
 ('11000000-0000-0000-0000-000000000002','mf-injection-editor@example.invalid'),
 ('11000000-0000-0000-0000-000000000003','mf-injection-viewer@example.invalid');

do $$
<<injection_acceptance>>
declare
  owner_id uuid := '11000000-0000-0000-0000-000000000001';
  editor_id uuid := '11000000-0000-0000-0000-000000000002';
  viewer_id uuid := '11000000-0000-0000-0000-000000000003';
  list_id uuid := '22000000-0000-0000-0000-000000000001';
  schedule_id uuid := '33000000-0000-0000-0000-000000000001';
  record_id uuid := '44000000-0000-0000-0000-000000000001';
  schedule_data jsonb;
  record_data jsonb;
  op uuid;
  response jsonb;
  activity_before bigint;
  denied boolean;
begin
  perform set_config('request.jwt.claim.sub',owner_id::text,true);
  perform public.apply_operation(gen_random_uuid(),'list',list_id,list_id,
    jsonb_build_object('id',list_id,'name','Casa','budget_cents',0,'status','active','deleted',false),'{}');
  insert into public.list_members(list_id,user_id,role) values
    (list_id,editor_id,'editor'),(list_id,viewer_id,'viewer');
  select count(*) into activity_before from public.shopping_activity where shopping_activity.list_id=injection_acceptance.list_id;

  schedule_data := jsonb_build_object('id',schedule_id,'name','Rotina Ana','person_name','Ana',
    'medication','Medicamento teste','dose','1 ml','route','Subcutânea','interval_value',14,
    'interval_unit','DAYS','next_due_at',(extract(epoch from now()+interval '14 days')*1000)::bigint,
    'responsible','','notes','Observação reservada','active',true,'deleted',false);
  op := gen_random_uuid();
  perform public.apply_injection_operation(op,'schedule',schedule_id,list_id,schedule_data,'{}');
  -- Replaying an operation UUID must not duplicate or change a sensitive row.
  response := public.apply_injection_operation(op,'schedule',schedule_id,list_id,'{}','{}');
  if response->>'ok'<>'true' then raise exception 'Idempotência da rotina falhou'; end if;

  record_data := jsonb_build_object('id',record_id,'schedule_id',schedule_id,'scheduled_for_at',
    (schedule_data->>'next_due_at')::bigint,'administered_at',(extract(epoch from now())*1000)::bigint-60000,
    'dose','1 ml','application_site','Braço','notes','Registro reservado','deleted',false);
  perform public.apply_injection_operation(gen_random_uuid(),'record',record_id,list_id,record_data,'{}');

  perform set_config('request.jwt.claim.sub',editor_id::text,true);
  response := public.apply_injection_operation(gen_random_uuid(),'schedule',schedule_id,list_id,
    '{"notes":"Atualizada pelo editor"}','{"notes":"Observação reservada"}');
  if response->>'ok'<>'true' then raise exception 'Editor não conseguiu atualizar rotina'; end if;
  response := public.apply_injection_operation(gen_random_uuid(),'schedule',schedule_id,list_id,
    '{"dose":"2 ml"}','{"dose":"1 ml"}');
  if response->>'ok'<>'true' or not exists(select 1 from public.injection_schedules
      where id=schedule_id and data->>'notes'='Atualizada pelo editor' and data->>'dose'='2 ml') then
    raise exception 'Alterações independentes da rotina não foram mescladas';
  end if;
  response := public.apply_injection_operation(gen_random_uuid(),'schedule',schedule_id,list_id,
    '{"notes":"Outra alteração"}','{"notes":"Observação reservada"}');
  if response->>'conflict'<>'true' then raise exception 'Conflito de campo de saúde não detectado'; end if;

  perform set_config('request.jwt.claim.sub',viewer_id::text,true);
  denied := false;
  begin
    perform public.apply_injection_operation(gen_random_uuid(),'record',record_id,list_id,
      '{"notes":"alteração não autorizada"}','{"notes":"Registro reservado"}');
  exception when others then denied := true; end;
  if not denied then raise exception 'Leitor conseguiu alterar registro de saúde'; end if;

  perform set_config('request.jwt.claim.sub',owner_id::text,true);
  if exists(select 1 from public.shopping_activity where shopping_activity.list_id=injection_acceptance.list_id
      and details::text ilike '%Medicamento teste%') then raise exception 'Dados de saúde vazaram para a atividade de compras'; end if;
  if (select count(*) from public.shopping_activity where shopping_activity.list_id=injection_acceptance.list_id)<>activity_before then
    raise exception 'Uma rotina de saúde criou atividade na lista de compras';
  end if;
end $$;

select set_config('request.jwt.claim.sub','11000000-0000-0000-0000-000000000003',true);
set local role authenticated;
do $$
begin
  if not exists(select 1 from public.injection_schedules where id='33000000-0000-0000-0000-000000000001') then
    raise exception 'Membro leitor não conseguiu consultar rotina compartilhada';
  end if;
  if not exists(select 1 from public.injection_records where id='44000000-0000-0000-0000-000000000001') then
    raise exception 'Membro leitor não conseguiu consultar histórico compartilhado';
  end if;
end $$;
reset role;

select set_config('request.jwt.claim.sub','11000000-0000-0000-0000-000000000001',true);
select public.remove_member('22000000-0000-0000-0000-000000000001','11000000-0000-0000-0000-000000000003');
select set_config('request.jwt.claim.sub','11000000-0000-0000-0000-000000000003',true);
set local role authenticated;
do $$
begin
  if exists(select 1 from public.injection_schedules where id='33000000-0000-0000-0000-000000000001') then
    raise exception 'Participante removido ainda consegue ler rotina';
  end if;
end $$;
reset role;

select set_config('request.jwt.claim.sub','11000000-0000-0000-0000-000000000001',true);
select public.apply_injection_operation(gen_random_uuid(),'schedule','33000000-0000-0000-0000-000000000001',
  '22000000-0000-0000-0000-000000000001','{"deleted":true,"active":false}','{"deleted":false,"active":true}');
do $$
begin
  if not exists(select 1 from public.injection_records where id='44000000-0000-0000-0000-000000000001' and data->>'deleted'='true') then
    raise exception 'Excluir rotina não marcou o histórico como removido';
  end if;
end $$;
rollback;
