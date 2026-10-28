import test from 'node:test';
import assert from 'node:assert/strict';
import worker from './worker.mjs';
const list='00000000-0000-0000-0000-000000000001', item='00000000-0000-0000-0000-000000000002';
const url=`https://example.invalid/photos/${list}/${item}`;
const realFetch=globalThis.fetch;
function backend(role, photos=[]) {
 globalThis.fetch=async(input)=>{
  const path=String(input);
  if(path.endsWith('/auth/v1/user')) return Response.json({id:'user'});
  if(path.endsWith('/member_role')) return Response.json(role);
  if(path.includes('/shopping_items?')) return Response.json([{id:item}]);
  if(path.includes('/item_photos?')) return Response.json(photos);
  if(path.endsWith('/set_r2_photo')) return Response.json(null);
  throw Error('Unexpected network request');
 };
}
test('anonymous cannot read private photographs',async()=>{
 let touched=false;globalThis.fetch=async()=>{touched=true;throw Error();};
 const r=await worker.fetch(new Request(url),{});
 assert.equal(r.status,401);assert.equal(touched,false);
});
test('non-member cannot access bucket',async()=>{
 backend(null);let touched=false;
 const r=await worker.fetch(new Request(url,{headers:{Authorization:'Bearer token'}}),{PHOTOS:{get:()=>{touched=true;}}});
 assert.equal(r.status,403);assert.equal(touched,false);
});
test('viewer cannot upload',async()=>{
 backend('viewer');let touched=false;
 const r=await worker.fetch(new Request(url,{method:'PUT',headers:{Authorization:'Bearer token','Content-Type':'image/jpeg'},body:new Uint8Array([255,216,255,217])}),{PHOTOS:{put:()=>{touched=true;}}});
 assert.equal(r.status,403);assert.equal(touched,false);
});
test('editor cannot upload non-JPEG',async()=>{
 backend('editor');let touched=false;
 const r=await worker.fetch(new Request(url,{method:'PUT',headers:{Authorization:'Bearer token','Content-Type':'image/jpeg'},body:'invalid'}),{PHOTOS:{put:()=>{touched=true;}}});
 assert.equal(r.status,400);assert.equal(touched,false);
});
test('valid member downloads private JPEG and cache stays private',async()=>{
 const key=`${list}/${item}/${'a'.repeat(64)}.jpg`;backend('viewer',[{object_key:key}]);
 const r=await worker.fetch(new Request(url,{headers:{Authorization:'Bearer token'}}),{PHOTOS:{get:async(k)=>{assert.equal(k,key);return{body:new Uint8Array([255,216,255,217]),httpEtag:'"test"'};}}});
 assert.equal(r.status,200);assert.equal(r.headers.get('Cache-Control'),'private, no-store');assert.equal(r.headers.get('Content-Type'),'image/jpeg');
});
test('metadata cannot escape list namespace',async()=>{
 backend('viewer',[{object_key:'another-list/image.jpg'}]);let touched=false;
 const r=await worker.fetch(new Request(url,{headers:{Authorization:'Bearer token'}}),{PHOTOS:{get:()=>{touched=true;}}});
 assert.equal(r.status,503);assert.equal(touched,false);
});
test('push does not pretend to work without server credential',async()=>{
 const r=await worker.fetch(new Request('https://example.invalid/dispatch',{method:'POST',body:'{}'}),{});
 assert.equal(r.status,503);
});
test.after(()=>{globalThis.fetch=realFetch;});
