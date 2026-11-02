(function(){
'use strict';
const esc=s=>String(s??'').replace(/[&<>"']/g,m=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#039;'}[m]));
async function init(){
 if(!EduQuinnAuth.guard(['instructor','admin']))return;
 const host=document.querySelector('#instructorApp');
 const [courses,existing]=await Promise.all([EduQuinnAuth.api('/api/instructor/courses'),EduQuinnAuth.api('/api/announcements')]);
 const rows=courses.courses||[];
 const content=`<section class="studio-page"><div class="studio-pagehead"><div><p class="eyebrow">COMMUNICATION</p><h1>Course announcements</h1><p>Send important updates to enrolled learners. Announcements appear in EduQuinn and can also be emailed.</p></div></div><div class="studio-two"><form class="studio-panel form-panel" id="announcementForm"><h2>New announcement</h2><label>Course<select name="courseId" required><option value="">Select course</option>${rows.map(c=>`<option value="${c.id}">${esc(c.title)}</option>`).join('')}</select></label><label>Title<input name="title" maxlength="180" required placeholder="e.g. Assignment deadline updated"></label><label>Message<textarea name="body" rows="8" maxlength="5000" required placeholder="Write the update your students need to know..."></textarea></label><label class="consent"><input type="checkbox" name="emailStudents" checked> Also email enrolled students who have email announcements enabled</label><button class="btn btn-primary" type="submit">Publish announcement</button><p class="form-note" id="announcementStatus"></p></form><section class="studio-panel"><div class="panel-head"><div><h2>Recent announcements</h2><p>Your latest course communications.</p></div></div><div id="announcementHistory">${render(existing.announcements||[])}</div></section></div></section>`;
 if(typeof layout==='function')layout(content,'announcements');else host.innerHTML=content;
 const form=document.querySelector('#announcementForm');form.onsubmit=async e=>{e.preventDefault();const d=Object.fromEntries(new FormData(form));d.emailStudents=form.elements.emailStudents.checked;const btn=e.submitter;btn.disabled=true;try{const r=await EduQuinnAuth.api('/api/announcements',{method:'POST',body:JSON.stringify(d)});document.querySelector('#announcementStatus').textContent=`Published to ${r.recipients} enrolled learner${r.recipients===1?'':'s'}.`;form.reset();form.elements.emailStudents.checked=true;const x=await EduQuinnAuth.api('/api/announcements');document.querySelector('#announcementHistory').innerHTML=render(x.announcements||[])}catch(err){document.querySelector('#announcementStatus').textContent=err.message}finally{btn.disabled=false}};
}
function render(a){return a.map(x=>`<article class="announcement-item"><div><b>${esc(x.title)}</b><small>${esc(x.course_title||'Course')} · ${new Date(x.created_at).toLocaleString()}</small></div><p>${esc(x.body)}</p></article>`).join('')||'<div class="empty">No announcements published yet.</div>'}
window.EduQuinnAnnouncements={init};
})();
