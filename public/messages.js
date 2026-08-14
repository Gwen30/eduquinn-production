(function(){
'use strict';
const user=EduQuinnAuth.session();
const instructorMode=document.body.dataset.messageRole==='instructor';
let threads=[],current=null;
const esc=s=>String(s??'').replace(/[&<>"']/g,m=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#039;'}[m]));
const initials=n=>String(n||'?').split(/\s+/).map(x=>x[0]).join('').slice(0,2).toUpperCase();
const counterpart=t=>instructorMode?{name:t.student_name,email:t.student_email}:{name:t.instructor_name,email:t.instructor_email};
const time=v=>v?new Date(v).toLocaleString([], {month:'short',day:'numeric',hour:'2-digit',minute:'2-digit'}):'';
async function loadThreads(){
  const x=await EduQuinnAuth.api('/api/conversations');threads=x.conversations||[];if(!current&&threads[0])current=threads[0].id;renderThreads(document.querySelector('#messageSearch')?.value||'');await renderChat();
}
function renderThreads(q=''){
 const root=document.querySelector('#threadItems');if(!root)return;const list=threads.filter(t=>`${counterpart(t).name} ${t.course_title||''}`.toLowerCase().includes(q.toLowerCase()));
 root.innerHTML=list.map(t=>{const c=counterpart(t);return `<button class="thread-item ${t.id===current?'active':''}" data-thread="${t.id}"><span class="thread-avatar">${initials(c.name)}</span><span class="thread-copy"><b>${esc(c.name)}</b><small>${esc(t.last_message||t.course_title||'Course conversation')}</small></span><span class="thread-time">${time(t.updated_at)}</span></button>`}).join('')||'<div class="empty">No conversations yet.</div>';
 root.querySelectorAll('[data-thread]').forEach(b=>b.onclick=async()=>{current=b.dataset.thread;renderThreads(document.querySelector('#messageSearch').value);await renderChat()});
}
async function renderChat(){
 const head=document.querySelector('#chatHead'),root=document.querySelector('#chatMessages');if(!head||!root)return;
 const t=threads.find(x=>x.id===current);if(!t){head.innerHTML='<div><b>No conversation selected</b><small>Select a conversation to begin.</small></div>';root.innerHTML='';return}
 const c=counterpart(t);head.innerHTML=`<span class="thread-avatar">${initials(c.name)}</span><div><b>${esc(c.name)}</b><small>${esc(t.course_title||'EduQuinn course')}</small></div>`;
 try{const x=await EduQuinnAuth.api(`/api/conversations/${current}/messages`);root.innerHTML=(x.messages||[]).map(m=>`<div class="bubble ${m.sender_id===user.id?'mine':''}">${esc(m.body)}<small>${time(m.created_at)}</small></div>`).join('')||'<div class="empty">No messages yet. Start the conversation below.</div>';root.scrollTop=root.scrollHeight}catch(e){root.innerHTML=`<div class="empty">${esc(e.message)}</div>`}
}
async function sendMessage(e){e.preventDefault();const input=document.querySelector('#messageText'),body=input.value.trim();if(!body||!current)return;const btn=e.submitter||e.target.querySelector('button[type=submit]');if(btn)btn.disabled=true;try{await EduQuinnAuth.api(`/api/conversations/${current}/messages`,{method:'POST',body:JSON.stringify({body})});input.value='';await loadThreads()}catch(err){alert(err.message)}finally{if(btn)btn.disabled=false}}
async function newConversation(){
 if(instructorMode)return;
 try{const x=await EduQuinnAuth.api('/api/enrolments'),items=x.enrolments||[];if(!items.length)return alert('Enroll in a course before messaging an instructor.');
   const options=items.map((e,i)=>`${i+1}. ${e.title}`).join('\n');const picked=prompt(`Choose the course instructor to message:\n\n${options}`,'1');const i=Number(picked)-1;if(!Number.isInteger(i)||!items[i])return;
   const r=await EduQuinnAuth.api('/api/conversations',{method:'POST',body:JSON.stringify({courseId:items[i].course_id})});current=r.conversationId;await loadThreads();document.querySelector('#messageText')?.focus();
 }catch(e){alert(e.message)}
}
document.querySelector('#messageSearch')?.addEventListener('input',e=>renderThreads(e.target.value));
document.querySelector('#messageForm')?.addEventListener('submit',sendMessage);
const newBtn=document.querySelector('#newConversation');if(newBtn)newBtn.onclick=newConversation;
loadThreads().catch(e=>{const r=document.querySelector('#threadItems');if(r)r.innerHTML=`<div class="empty">${esc(e.message)}</div>`});
})();
