(function(){
'use strict';
const SESSION_KEY='eduquinnSessionV10';
const OLD_KEYS=['eduquinnSessionV6','eduquinnUsersV6'];
for(const k of OLD_KEYS)localStorage.removeItem(k);
const parse=(v,f=null)=>{try{return JSON.parse(v)||f}catch{return f}};
function session(){return parse(localStorage.getItem(SESSION_KEY),null)}
function cache(user){if(user)localStorage.setItem(SESSION_KEY,JSON.stringify(user));else localStorage.removeItem(SESSION_KEY)}
async function api(url,options={}){const r=await fetch(url,{credentials:'same-origin',headers:{'Content-Type':'application/json',...(options.headers||{})},...options});let x={};try{x=await r.json()}catch{}if(!r.ok)throw Object.assign(new Error(x.message||'Request failed'),{status:r.status,data:x});return x}
async function refresh(){try{const x=await api('/api/auth/session');cache(x.user||null);return x.user||null}catch{cache(null);return null}}
async function login(email,password,portal='public',expectedRole=''){try{const x=await api('/api/auth/login',{method:'POST',body:JSON.stringify({email,password,portal,expectedRole})});cache(x.user);return {ok:true,user:x.user}}catch(e){return {ok:false,message:e.message}}}
async function register(data){try{const x=await api('/api/auth/register',{method:'POST',body:JSON.stringify(data)});cache(x.user);return {ok:true,user:x.user}}catch(e){return {ok:false,message:e.message}}}
async function logout(){cache(null);try{await api('/api/auth/logout',{method:'POST',body:'{}'})}catch{}location.replace('/login.html')}
function homeFor(role){return role==='admin'?'/admin.html':role==='instructor'?'/instructor.html':'/my-learning.html'}
function can(roles){const s=session();return !!s&&roles.includes(s.role)}
function guard(roles){const s=session();if(!s){const next=encodeURIComponent(location.pathname+location.search);location.replace('/login.html?next='+next);return false}if(!roles.includes(s.role)){sessionStorage.setItem('eduquinnAccessMessage',`Your ${s.role} account cannot open that page.`);location.replace(homeFor(s.role));return false}return true}
function accessNotice(){const msg=sessionStorage.getItem('eduquinnAccessMessage');if(!msg)return;sessionStorage.removeItem('eduquinnAccessMessage');window.addEventListener('DOMContentLoaded',()=>{const n=document.createElement('div');n.className='access-notice';n.innerHTML=`<strong>Access restricted</strong><span>${msg}</span><button aria-label="Dismiss">×</button>`;document.body.appendChild(n);n.querySelector('button').onclick=()=>n.remove();setTimeout(()=>n.remove(),6000)})}
const path=location.pathname;const admin=path.startsWith('/admin')&&path!=='/admin-access.html';const instructor=['/instructor.html','/instructor-courses.html','/instructor-analytics.html','/instructor-onboarding.html','/course-builder.html','/instructor-live.html','/instructor-messages.html','/instructor-payouts.html','/media-library.html','/instructor-announcements.html'].includes(path);const student=['/my-learning.html','/purchases.html','/checkout.html','/order-success.html','/course-player.html','/certificate.html','/live-class-room.html','/messages.html','/wishlist.html'].includes(path);if(admin)guard(['admin']);else if(instructor)guard(['instructor','admin']);else if(student)guard(['student']);accessNotice();
window.EduQuinnAuth={session,login,register,logout,homeFor,can,guard,refresh,api};
window.addEventListener('DOMContentLoaded',()=>{document.body.classList.add('role-'+(session()?.role||'guest'));document.addEventListener('click',e=>{const b=e.target.closest('[data-logout]');if(b){e.preventDefault();logout()}});refresh().then(u=>{if(u)document.body.dataset.authReady='true'}).catch(()=>{})});
})();
