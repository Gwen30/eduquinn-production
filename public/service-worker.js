const CACHE='eduquinn-shell-v15';
const SHELL=['/','/index.html','/styles.css','/icons.js','/auth.js','/app.js','/offline.html','/manifest.webmanifest','/app-icon.svg'];
self.addEventListener('install',e=>e.waitUntil(caches.open(CACHE).then(c=>c.addAll(SHELL)).then(()=>self.skipWaiting())));
self.addEventListener('activate',e=>e.waitUntil(caches.keys().then(keys=>Promise.all(keys.filter(k=>k!==CACHE).map(k=>caches.delete(k)))).then(()=>self.clients.claim())));
self.addEventListener('fetch',e=>{
  const r=e.request,u=new URL(r.url);
  if(r.method!=='GET'||u.origin!==location.origin||u.pathname.startsWith('/api/')||u.pathname.startsWith('/api/media/')) return;
  if(r.mode==='navigate') return e.respondWith(fetch(r).then(resp=>{const copy=resp.clone();caches.open(CACHE).then(c=>c.put(r,copy));return resp}).catch(()=>caches.match(r).then(x=>x||caches.match('/offline.html'))));
  e.respondWith(caches.match(r).then(cached=>cached||fetch(r).then(resp=>{if(resp.ok)caches.open(CACHE).then(c=>c.put(r,resp.clone()));return resp})));
});
