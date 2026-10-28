// The bucket stays private. No R2 or Supabase administrative key is needed.
const SUPABASE = 'https://igehbjnutmhkqfdlrndx.supabase.co';
const PUBLIC_KEY = 'sb_publishable_sev643ZP8Ctxocw5uL3f7A_rgGrZska';
const uuid = '[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}';
const photoRoute = new RegExp(`^/photos/(${uuid})/(${uuid})$`);
const MAX_PHOTO = 1500000;
const json = (data, status = 200) => Response.json(data, {status, headers: {'Cache-Control':'no-store'}});
async function db(path, token, body, method = 'POST') {
  const response = await fetch(`${SUPABASE}${path}`, {
    method: body === undefined ? 'GET' : method,
    headers: {apikey: PUBLIC_KEY, ...(token ? {Authorization:token} : {}), 'Content-Type':'application/json'},
    body: body === undefined ? undefined : JSON.stringify(body), signal: AbortSignal.timeout(15000)
  });
  if (!response.ok) throw new Error('database');
  return response.status === 204 ? null : response.json();
}
async function identity(request) {
  const auth = request.headers.get('Authorization') || '';
  if (!auth.startsWith('Bearer ') || auth.length > 8192) throw new Response('Entre na sua conta', {status:401});
  const user = await db('/auth/v1/user', auth);
  if (!user?.id) throw new Response('Sessão inválida', {status:401});
  return auth;
}
async function photo(request, env, match) {
  const [, listId, itemId] = match;
  const auth = await identity(request);
  const role = await db('/rest/v1/rpc/member_role', auth, {target_list:listId});
  if (!['owner','editor','viewer'].includes(role)) return new Response('Sem acesso', {status:403});
  if (!['GET','PUT','DELETE'].includes(request.method)) return new Response('Método inválido', {status:405});
  if (request.method !== 'GET' && role === 'viewer') return new Response('Sem permissão de edição', {status:403});
  const items = await db(`/rest/v1/shopping_items?id=eq.${itemId}&list_id=eq.${listId}&select=id`, auth);
  if (!items.length) return new Response('Produto não encontrado', {status:404});
  if (request.method === 'GET') {
    const rows = await db(`/rest/v1/item_photos?item_id=eq.${itemId}&list_id=eq.${listId}&select=object_key,photo_data`, auth);
    if (!rows.length) return new Response(null, {status:204});
    const row = rows[0];
    if (row.object_key) {
      if (!row.object_key.startsWith(`${listId}/${itemId}/`)) throw new Error('key');
      const object = await env.PHOTOS.get(row.object_key);
      if (!object) return new Response('Foto indisponível', {status:404});
      const headers = {'Content-Type':'image/jpeg','Cache-Control':'private, no-store',ETag:object.httpEtag};
      if (request.headers.get('If-None-Match') === object.httpEtag) return new Response(null,{status:304,headers});
      return new Response(object.body,{headers});
    }
    // Legacy photos remain readable until an editor migrates them; no data is discarded.
    if (!row.photo_data) return new Response(null,{status:204});
    const bytes = Uint8Array.from(atob(row.photo_data), c=>c.charCodeAt(0));
    return new Response(bytes,{headers:{'Content-Type':'image/jpeg','Cache-Control':'private, no-store'}});
  }
  let bytes = new Uint8Array();
  if (request.method === 'PUT') {
    if (request.headers.get('Content-Type')?.split(';')[0] !== 'image/jpeg') return new Response('Use JPEG',{status:415});
    const reader = request.body?.getReader();
    if (!reader) return new Response('Foto vazia',{status:400});
    const parts=[]; let size=0;
    while(true) {
      const part=await reader.read(); if(part.done) break;
      size+=part.value.length;
      if(size>MAX_PHOTO) { await reader.cancel(); return new Response('Foto muito grande',{status:413}); }
      parts.push(part.value);
    }
    bytes=new Uint8Array(size);let offset=0;for(const part of parts){bytes.set(part,offset);offset+=part.length;}
    if(size<4 || bytes[0]!==255 || bytes[1]!==216 || bytes[size-2]!==255 || bytes[size-1]!==217) return new Response('JPEG inválido',{status:400});
  }
  const digest = bytes.length ? [...new Uint8Array(await crypto.subtle.digest('SHA-256',bytes))].map(v=>v.toString(16).padStart(2,'0')).join('') : '';
  const key = digest ? `${listId}/${itemId}/${digest}.jpg` : '';
  if (key) await env.PHOTOS.put(key,bytes,{httpMetadata:{contentType:'image/jpeg'}});
  await db('/rest/v1/rpc/set_r2_photo',auth,{target_list:listId,target_item:itemId,object_path:key,content_hash:digest});
  return json({ok:true,hash:digest});
}
let oauthCache;
function base64url(value) {
  const bytes = typeof value === 'string' ? new TextEncoder().encode(value) : new Uint8Array(value);
  return btoa(String.fromCharCode(...bytes)).replaceAll('+','-').replaceAll('/','_').replaceAll('=','');
}
async function googleToken(env) {
  if (oauthCache && oauthCache.expires>Date.now()) return oauthCache.token;
  const account=JSON.parse(env.FIREBASE_SERVICE_ACCOUNT);
  if(account.project_id!=='mercado-facil-9abe5') throw new Error('project');
  const now=Math.floor(Date.now()/1000);
  const message=base64url(JSON.stringify({alg:'RS256',typ:'JWT'}))+'.'+base64url(JSON.stringify({iss:account.client_email,scope:'https://www.googleapis.com/auth/firebase.messaging',aud:'https://oauth2.googleapis.com/token',iat:now,exp:now+3600}));
  const pem=account.private_key.replace(/-----[^-]+-----/g,'').replace(/\s/g,'');
  const key=await crypto.subtle.importKey('pkcs8',Uint8Array.from(atob(pem),c=>c.charCodeAt(0)),{name:'RSASSA-PKCS1-v1_5',hash:'SHA-256'},false,['sign']);
  const signature=await crypto.subtle.sign('RSASSA-PKCS1-v1_5',key,new TextEncoder().encode(message));
  const response=await fetch('https://oauth2.googleapis.com/token',{method:'POST',body:new URLSearchParams({grant_type:'urn:ietf:params:oauth:grant-type:jwt-bearer',assertion:message+'.'+base64url(signature)}),signal:AbortSignal.timeout(15000)});
  if(!response.ok) throw new Error('google-auth');
  const token=await response.json();oauthCache={token:token.access_token,expires:Date.now()+3000000};return token.access_token;
}
async function dispatch(request,env) {
  if(!env.FIREBASE_SERVICE_ACCOUNT) return json({error:'Push ainda não configurado'},503);
  const text=await request.text();if(text.length>512) return json({error:'size'},413);
  const input=JSON.parse(text);
  if(!new RegExp(`^${uuid}$`).test(input.event_id)|| !/^[a-f0-9]{64}$/.test(input.capability)) return json({error:'invalid'},400);
  const event=await db('/rest/v1/rpc/read_push_delivery',null,input);
  if(!event || !event.devices?.length) return json({ok:true});
  const token=await googleToken(env);
  let failed=false;
  for(const device of event.devices.slice(0,50)) {
    const message={token:device.token,data:{event_id:input.event_id,list_id:event.list_id,kind:event.kind},notification:{title:'Mercado Fácil',body:event.kind==='purchase'?'Uma compra foi concluída na sua lista compartilhada.':'Sua lista compartilhada foi atualizada.'},android:{priority:'HIGH',ttl:'86400s',notification:{channel_id:'shared_changes',tag:input.event_id,visibility:'PRIVATE'}}};
    const response=await fetch('https://fcm.googleapis.com/v1/projects/mercado-facil-9abe5/messages:send',{method:'POST',headers:{Authorization:`Bearer ${token}`,'Content-Type':'application/json'},body:JSON.stringify({message}),signal:AbortSignal.timeout(15000)});
    if(response.ok || response.status===404) {
      await db('/rest/v1/rpc/ack_push_delivery',null,{...input,installation:device.installation,invalid:response.status===404});
    } else failed=true;
  }
  return json({ok:!failed},failed?503:200);
}
export default {async fetch(request,env) {
  try {
    const path=new URL(request.url).pathname;
    if(path==='/health' && request.method==='GET') return json({service:'mercado-facil',r2:!!env.PHOTOS,push:!!env.FIREBASE_SERVICE_ACCOUNT});
    if(path==='/dispatch' && request.method==='POST') return await dispatch(request,env);
    const match=photoRoute.exec(path);
    if(match) return await photo(request,env,match);
    return json({error:'not_found'},404);
  } catch(error) {
    if(error instanceof Response) return error;
    return json({error:'Não foi possível concluir. Tente novamente.'},503);
  }
}};
