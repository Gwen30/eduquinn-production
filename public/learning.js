const esc=s=>String(s??'').replace(/[&<>"']/g,m=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#039;'}[m]));const id=new URLSearchParams(location.search).get('id');
function header(){return `<header class="site-header"><a class="brand" href="/"><span class="brand-mark">EQ</span><span><strong>EduQuinn</strong><small>Learn. Grow. Succeed.</small></span></a><div class="header-actions"><a class="btn btn-ghost" href="/my-learning.html">My Learning</a><button class="btn btn-primary" data-logout>Log out</button></div></header>`}
async function myLearning(){document.body.insertAdjacentHTML('afterbegin',header());document.querySelector('.page-title').innerHTML='<p class="eyebrow">MY EDUQUINN</p><h1>My Learning</h1><p>Continue your enrolled courses and track your progress.</p>';const root=document.querySelector('#pageContent');try{const d=await EduQuinnAuth.api('/api/enrolments');root.innerHTML=d.enrolments.length?`<div class="learning-grid">${d.enrolments.map(e=>`<article class="learning-card"><div><small>${esc(e.education_level||'')}</small><h3>${esc(e.title)}</h3><div class="progress"><i style="width:${Number(e.progress||0)}%"></i></div><div class="progress-label"><span>${Number(e.progress||0)}% complete</span></div><a class="btn btn-primary" href="/course-player.html?id=${encodeURIComponent(e.course_id)}">${Number(e.progress||0)?'Continue learning':'Start learning'}</a>${Number(e.progress||0)===100?` <a class="btn btn-ghost" href="/certificate.html?id=${encodeURIComponent(e.course_id)}">Certificate</a>`:''}</div></article>`).join('')}</div>`:'<div class="empty-state"><h2>No enrolled courses yet</h2><p>Your purchased courses will appear here after payment confirmation.</p><a class="btn btn-primary" href="/">Browse courses</a></div>'}catch(e){root.innerHTML=`<div class="empty-state"><h2>Learning records unavailable</h2><p>${esc(e.message)}</p></div>`}}
const lessonTypeLabel={video:'Video lesson',article:'Article',quiz:'Quiz',assignment:'Assignment',resource:'Resource'};

function renderQuizQuestion(q,qi){
  const opts=Array.isArray(q.options)?q.options:[];
  const marks=`<small class="muted">${Number(q.marks||1)} mark${Number(q.marks||1)===1?'':'s'}</small>`;
  if(q.question_type==='short_answer'){
    return `<article><p><b>${qi+1}.</b> ${esc(q.prompt)} ${marks}</p><input type="text" name="q${qi}" class="quiz-answer-input" placeholder="Type your answer" style="width:100%;padding:12px;border:1px solid var(--line);border-radius:7px"></article>`;
  }
  const inputType=q.question_type==='multiple'?'checkbox':'radio';
  return `<article><p><b>${qi+1}.</b> ${esc(q.prompt)} ${marks}</p>${opts.map((o,oi)=>`<label style="display:flex;gap:9px;align-items:center;padding:8px 0"><input type="${inputType}" name="q${qi}" value="${oi}"> ${esc(o.text)}</label>`).join('')||'<p class="muted">No answer options were set for this question.</p>'}</article>`;
}

function readQuizAnswers(a){
  return (a.questions||[]).map((q,qi)=>{
    if(q.question_type==='short_answer'){const el=document.querySelector(`[name="q${qi}"]`);return el?el.value.trim():''}
    const checked=[...document.querySelectorAll(`[name="q${qi}"]:checked`)].map(i=>Number(i.value));
    return q.question_type==='multiple'?checked:(checked.length?checked[0]:null);
  });
}

function renderLessonBody(current,assessmentsByLesson,lastResult){
  if(!current)return '<div class="empty-state"><h2>No lessons available</h2></div>';
  const completeBtn=`<div class="lesson-actions"><button class="btn btn-primary" id="completeLesson">${current.completed?'Mark incomplete':'Mark complete'}</button></div>`;
  const type=current.lesson_type;
  if(type==='video'){
    return `<section class="video-stage">${current.asset_id?`<video controls style="width:100%;max-height:60vh" src="/api/media/${current.asset_id}"></video>`:'<div class="empty-state"><p>This lesson has no media attached.</p></div>'}</section>${completeBtn}`;
  }
  if(type==='article'){
    const text=String(current.body||'').trim();
    return `<article class="panel" style="padding:28px;margin:20px 0">${text?text.split(/\n{2,}/).map(p=>`<p>${esc(p).replace(/\n/g,'<br>')}</p>`).join(''):'<p class="muted">The instructor has not added article content yet.</p>'}</article>${completeBtn}`;
  }
  if(type==='resource'){
    const text=String(current.body||'').trim();
    return `<article class="panel" style="padding:28px;margin:20px 0">${text?`<p>${esc(text)}</p>`:''}${current.asset_id?`<a class="btn btn-primary" href="/api/media/${current.asset_id}" target="_blank" rel="noopener">Open resource</a>`:'<p class="muted">No downloadable file was attached to this resource.</p>'}</article>${completeBtn}`;
  }
  if(type==='assignment'){
    let a={};try{a=JSON.parse(current.body||'{}')}catch{}
    return `<article class="panel" style="padding:28px;margin:20px 0"><p>${esc(a.instructions||'No instructions provided.')}</p><div class="lesson-details"><div><small>DUE</small><b>${a.dueDate?new Date(a.dueDate).toLocaleDateString():'No due date'}</b></div><div><small>MARKS</small><b>${Number(a.maxMarks||100)}</b></div></div><p class="muted">Submit your work to your instructor through Messages, then mark this lesson complete.</p></article><div class="lesson-actions"><a class="btn btn-ghost" href="/messages.html">Message instructor</a> <button class="btn btn-primary" id="completeLesson">${current.completed?'Mark incomplete':'Mark complete'}</button></div>`;
  }
  if(type==='quiz'){
    const a=assessmentsByLesson.get(current.id);
    if(!a)return '<div class="empty-state"><p>This quiz is not available yet.</p></div>';
    const maxAttempts=Number(a.max_attempts||3);
    if(lastResult){
      const canRetake=lastResult.attemptsUsed<lastResult.maxAttempts;
      return `<article class="panel" style="padding:28px;margin:20px 0"><h2>${esc(a.title)}</h2><p class="${lastResult.passed?'review-note success':'review-note'}">${lastResult.passed?'Passed':'Not yet passed'} — score ${lastResult.score}% (pass mark ${lastResult.passMark}%). Attempt ${lastResult.attemptsUsed} of ${lastResult.maxAttempts}.</p>${canRetake?'<button class="btn btn-ghost" id="retakeQuiz">Try again</button>':'<p class="muted">No attempts remaining.</p>'}</article>${completeBtn}`;
    }
    if(Number(a.attemptsUsed||0)>=maxAttempts){
      return `<article class="panel" style="padding:28px;margin:20px 0"><h2>${esc(a.title)}</h2><p>You have used all ${maxAttempts} attempt${maxAttempts===1?'':'s'} for this quiz.</p></article>`;
    }
    return `<form id="quizForm" class="panel" style="padding:28px;margin:20px 0"><h2>${esc(a.title)}</h2>${a.instructions?`<p class="muted">${esc(a.instructions)}</p>`:''}<div class="question-list">${(a.questions||[]).map((q,qi)=>renderQuizQuestion(q,qi)).join('')}</div><button class="btn btn-primary" type="submit">Submit answers</button><p class="muted">Attempt ${Number(a.attemptsUsed||0)+1} of ${maxAttempts}${a.time_limit_minutes?` · Time limit ${a.time_limit_minutes} min`:''}</p></form>`;
  }
  return '<div class="empty-state"><p>This lesson type is not supported yet.</p></div>';
}

async function player(){const root=document.querySelector('#playerApp');if(!id){root.innerHTML='<div class="empty-state"><h2>Course not found</h2></div>';return}try{const [course,learn,assessRes]=await Promise.all([EduQuinnAuth.api('/api/courses/'+encodeURIComponent(id)),EduQuinnAuth.api('/api/learning/courses/'+encodeURIComponent(id)),EduQuinnAuth.api('/api/learning/courses/'+encodeURIComponent(id)+'/assessments').catch(()=>({assessments:[]}))]);const assessmentsByLesson=new Map((assessRes.assessments||[]).filter(a=>a.lesson_id).map(a=>[a.lesson_id,a]));let current=learn.lessons[0]||null;let lastResult=null;const render=()=>{root.innerHTML=`<div class="learning-shell"><aside class="curriculum"><a class="brand" href="/my-learning.html"><span class="brand-mark">EQ</span><span><strong>EduQuinn</strong><small>Back to My Learning</small></span></a><h2>${esc(course.course.title)}</h2><div>${learn.lessons.length?learn.lessons.map((l,i)=>`<button class="lesson-link ${current?.id===l.id?'active':''}" data-lesson="${l.id}"><span>${l.completed?'✓':i+1}</span><div><b>${esc(l.title)}</b><small>${esc(lessonTypeLabel[l.lesson_type]||l.lesson_type)}</small></div></button>`).join(''):'<p>No lessons have been published yet.</p>'}</div></aside><main class="lesson-stage"><header><h1>${esc(current?.title||course.course.title)}</h1></header>${renderLessonBody(current,assessmentsByLesson,lastResult)}</main></div>`;root.querySelectorAll('[data-lesson]').forEach(b=>b.onclick=()=>{current=learn.lessons.find(l=>l.id===b.dataset.lesson);lastResult=null;render()});const complete=document.querySelector('#completeLesson');if(complete)complete.onclick=async()=>{const next=!current.completed;await EduQuinnAuth.api('/api/learning/lessons/'+current.id+'/progress',{method:'POST',body:JSON.stringify({completed:next,positionSeconds:0})});current.completed=next;render()};const quizForm=document.querySelector('#quizForm');if(quizForm)quizForm.onsubmit=async(e)=>{e.preventDefault();const a=assessmentsByLesson.get(current.id);const answers=readQuizAnswers(a);const btn=quizForm.querySelector('button[type=submit]');btn.disabled=true;try{const r=await EduQuinnAuth.api('/api/learning/assessments/'+a.id+'/submit',{method:'POST',body:JSON.stringify({answers})});lastResult=r;a.attemptsUsed=r.attemptsUsed;if(r.passed){await EduQuinnAuth.api('/api/learning/lessons/'+current.id+'/progress',{method:'POST',body:JSON.stringify({completed:true,positionSeconds:0})});current.completed=true}render()}catch(err){alert(err.message);btn.disabled=false}};const retake=document.querySelector('#retakeQuiz');if(retake)retake.onclick=()=>{lastResult=null;render()}};render()}catch(e){root.innerHTML=`<div class="empty-state"><h2>Course unavailable</h2><p>${esc(e.message)}</p></div>`}}
async function certificate(){const root=document.querySelector('#certificateApp');if(!id){root.innerHTML='<div class="empty-state"><h2>Certificate unavailable</h2></div>';return}try{const d=await EduQuinnAuth.api('/api/certificates/issue',{method:'POST',body:JSON.stringify({courseId:id})}),c=await EduQuinnAuth.api('/api/courses/'+encodeURIComponent(id)),me=await EduQuinnAuth.api('/api/auth/session'),cert=d.certificate;root.innerHTML=`<div class="certificate-toolbar"><a href="/my-learning.html">← Back to My Learning</a><button class="btn btn-primary" onclick="print()">Print / Save PDF</button></div><div class="certificate-sheet"><div class="certificate-border"><div class="certificate-logo"><span class="brand-mark">EQ</span><strong>EduQuinn</strong></div><p class="certificate-kicker">CERTIFICATE OF COMPLETION</p><h1>This certifies that</h1><h2>${esc(me.user?.name||'Learner')}</h2><p>has successfully completed</p><h3>${esc(c.course.title)}</h3><p>Instructor: <b>${esc(c.course.instructor_name||'EduQuinn Instructor')}</b></p><div class="certificate-meta"><div><small>DATE ISSUED</small><b>${new Date(cert.issued_at||cert.created_at||Date.now()).toLocaleDateString()}</b></div><div><small>CERTIFICATE ID</small><b>${esc(cert.certificate_code)}</b></div></div></div></div>`}catch(e){root.innerHTML=`<div class="empty-state"><h2>Certificate not available yet</h2><p>${esc(e.message)}</p></div>`}}
const p=document.body.dataset.page;if(p==='learning')myLearning();if(p==='player')player();if(p==='certificate')certificate();
