(function(){
'use strict';
const app=document.querySelector('#instructorApp');
function esc(s){return String(s??'').replace(/[&<>"']/g,m=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#039;'}[m]))}
function fmt(bytes){const n=Number(bytes||0);if(n<1024)return n+' B';if(n<1048576)return (n/1024).toFixed(1)+' KB';return (n/1048576).toFixed(1)+' MB'}
async function init(){
  const root=document.querySelector('.studio-main'); if(!root)return;
  const section=document.createElement('section');section.className='studio-page media-page';section.innerHTML=`<div class="studio-pagehead"><div><p class="eyebrow">COURSE CONTENT</p><h1>Media & lesson manager</h1><p>Upload protected course media, attach files to lessons and build a course curriculum.</p></div></div><div class="media-toolbar"><label>Course <select id="mediaCourse"><option value="">Loading your courses…</option></select></label><button class="btn btn-ghost" id="createBackendCourse">＋ New course</button></div><div class="media-upload"><section class="media-drop"><h2>Upload course media</h2><p>Videos, PDFs, audio, thumbnails and supporting resources are stored outside the public web folder and served through protected URLs.</p><label>Media type<select id="mediaKind"><option value="video">Video</option><option value="document">Document / PDF</option><option value="audio">Audio</option><option value="thumbnail">Thumbnail</option><option value="other">Other resource</option></select></label><label>Select file<input id="mediaFile" type="file"></label><div class="upload-progress" aria-hidden="true"><i id="uploadBar"></i></div><button class="btn btn-primary" id="uploadMedia">Upload securely</button><p id="mediaStatus" class="form-hint"></p></section>
  <section class=\"studio-panel\">
  <h2>Add lesson</h2>

  <form id=\"lessonForm\" class=\"form-panel\">

    <label>
      Lesson title
      <input
        id=\"lessonTitle\"
        required
        placeholder=\"e.g. Introduction to mass balance\">
    </label>

    <label>
      Lesson type
      <select id=\"lessonType\">
        <option value=\"video\">Video</option>
        <option value=\"article\">Article</option>
        <option value=\"quiz\">Quiz</option>
        <option value=\"assignment\">Assignment</option>
        <option value=\"resource\">Resource</option>
      </select>
    </label>

    <div id=\"videoLessonFields\" class=\"lesson-type-fields\">
      <label>
        Attach video
        <select id=\"lessonAsset\">
          <option value=\"\">No media</option>
        </select>
      </label>
    </div>

    <div id=\"articleLessonFields\" class=\"lesson-type-fields\" hidden>
      <label>
        Article content
        <textarea
          id=\"articleContent\"
          rows=\"8\"
          placeholder=\"Write the lesson content here...\"></textarea>
      </label>
    </div>

    <div id=\"quizLessonFields\" class=\"lesson-type-fields\" hidden>

      <h3>Quiz settings</h3>

      <label>
        Instructions
        <textarea
          id=\"quizInstructions\"
          rows=\"3\"
          placeholder=\"Instructions for students\"></textarea>
      </label>

      <div class=\"quiz-settings-grid\">

        <label>
          Pass mark (%)
          <input
            id=\"quizPassMark\"
            type=\"number\"
            min=\"0\"
            max=\"100\"
            value=\"70\">
        </label>

        <label>
          Maximum attempts
          <input
            id=\"quizAttempts\"
            type=\"number\"
            min=\"1\"
            value=\"3\">
        </label>

        <label>
          Time limit (minutes)
          <input
            id=\"quizTimeLimit\"
            type=\"number\"
            min=\"0\"
            value=\"0\">
        </label>

      </div>

      <label class=\"check lesson-check\">
  <input id=\"quizShuffleQuestions\" type=\"checkbox\">
  <span>Randomise question order</span>
</label>

     <label class=\"check lesson-check\">
  <input id=\"quizShowAnswers\" type=\"checkbox\" checked>
  <span>Show answers after submission</span>
</label>

      <div class=\"quiz-builder-head\">
        <h3>Questions</h3>

        <button
          type=\"button\"
          class=\"btn btn-ghost\"
          id=\"addQuizQuestion\">
          + Add question
        </button>
      </div>

      <div id=\"quizQuestions\"></div>

    </div>

    <div id=\"assignmentLessonFields\" class=\"lesson-type-fields\" hidden>

      <label>
        Assignment instructions
        <textarea
          id=\"assignmentInstructions\"
          rows=\"5\"
          placeholder=\"Explain what students must submit\"></textarea>
      </label>

      <label>
        Due date
        <input
          id=\"assignmentDueDate\"
          type=\"datetime-local\">
      </label>

      <label>
        Maximum marks
        <input
          id=\"assignmentMarks\"
          type=\"number\"
          min=\"1\"
          value=\"100\">
      </label>

    </div>

    <div id=\"resourceLessonFields\" class=\"lesson-type-fields\" hidden>

      <label>
        Attach resource
        <select id=\"resourceAsset\">
          <option value=\"\">No resource selected</option>
        </select>
      </label>

      <label>
        Description
        <textarea
          id=\"resourceDescription\"
          rows=\"3\"
          placeholder=\"Describe this resource\"></textarea>
      </label>

    </div>

    <label class=\"check lesson-check\">
  <input id=\"lessonPreview\" type=\"checkbox\">
  <span>Allow public preview</span>
</label>

    <button class=\"btn btn-primary\" type=\"submit\">
      Add lesson
    </button>

  </form>
</section>
  </div>
  <section class="studio-panel" style="margin-top:20px">
  <div class="panel-head"><div><h2>Course assets</h2>
  <p>Files currently attached to the selected course.</p></div>
  </div><div class="asset-list" id="assetList"></div></section>
  <section class="studio-panel" style="margin-top:20px"><div class="panel-head"><div>
  <h2>Course curriculum</h2><p>Lessons are ordered and progress-ready for enrolled students.</p>
  </div></div><div class="asset-list" id="lessonList"></div></section>`;root.appendChild(section);
  const courseSelect=document.querySelector('#mediaCourse'),assetSelect=document.querySelector('#lessonAsset'),assetList=document.querySelector('#assetList'),lessonList=document.querySelector('#lessonList'),status=document.querySelector('#mediaStatus');
  let assets=[];
  const lessonTypeSelect = document.querySelector('#lessonType');

const lessonTypePanels = {
  video: document.querySelector('#videoLessonFields'),
  article: document.querySelector('#articleLessonFields'),
  quiz: document.querySelector('#quizLessonFields'),
  assignment: document.querySelector('#assignmentLessonFields'),
  resource: document.querySelector('#resourceLessonFields')
};

function updateLessonTypeFields() {
  Object.values(lessonTypePanels).forEach(panel => {
    if (panel) panel.hidden = true;
  });

  const selected = lessonTypePanels[lessonTypeSelect.value];

  if (selected) {
    selected.hidden = false;
  }
}

lessonTypeSelect.addEventListener('change', updateLessonTypeFields);

updateLessonTypeFields();

  const quizQuestionsContainer =
  document.querySelector('#quizQuestions');

const addQuizQuestionButton =
  document.querySelector('#addQuizQuestion');

let quizQuestionIndex = 0;

function addQuizAnswerOption(card, text = '') {
  const options = card.querySelector('.quiz-options');

  const questionType =
    card.querySelector('.quiz-question-type').value;

  const option = document.createElement('div');

  option.className = 'quiz-option-row';

  const inputType =
    questionType === 'multiple' ? 'checkbox' : 'radio';

  const groupName =
    'correct-' + card.dataset.questionIndex;

  option.innerHTML =
    '<input class="quiz-option-correct" type="' +
    inputType +
    '" name="' +
    groupName +
    '">' +

    '<input class="quiz-option-text" type="text" ' +
    'placeholder="Answer option" value="' +
    esc(text) +
    '">' +

    '<button type="button" class="remove-option-btn">' +
    '×</button>';

  options.appendChild(option);

  option
    .querySelector('.remove-option-btn')
    .addEventListener('click', () => option.remove());
}


function initialiseQuizQuestion(card) {
  const questionType =
    card.querySelector('.quiz-question-type');

  const addOptionButton =
    card.querySelector('.add-option-btn');

  addOptionButton.addEventListener('click', () => {
    addQuizAnswerOption(card);
  });

  card
    .querySelector('.remove-question-btn')
    .addEventListener('click', () => {
      card.remove();
    });

  questionType.addEventListener('change', () => {
    const options =
      card.querySelector('.quiz-options');

    options.innerHTML = '';

    if (questionType.value === 'true_false') {
      addQuizAnswerOption(card, 'True');
      addQuizAnswerOption(card, 'False');

    } else if (questionType.value === 'short_answer') {
      addQuizAnswerOption(card, '');

    } else {
      addQuizAnswerOption(card);
      addQuizAnswerOption(card);
      addQuizAnswerOption(card);
      addQuizAnswerOption(card);
    }
  });

  questionType.dispatchEvent(new Event('change'));
}


function createQuizQuestion() {
  quizQuestionIndex += 1;

  const card = document.createElement('div');

  card.className = 'quiz-question-card';

  card.dataset.questionIndex = quizQuestionIndex;

  card.innerHTML =
    '<div class="quiz-question-header">' +
      '<strong>Question ' + quizQuestionIndex + '</strong>' +

      '<button type="button" class="remove-question-btn">' +
        'Remove' +
      '</button>' +
    '</div>' +

    '<label>' +
      'Question' +
      '<textarea class="quiz-question-text" rows="3" ' +
      'placeholder="Enter the question"></textarea>' +
    '</label>' +

    '<label>' +
      'Question type' +
      '<select class="quiz-question-type">' +
        '<option value="single">Multiple choice — one answer</option>' +
        '<option value="multiple">Multiple choice — multiple answers</option>' +
        '<option value="true_false">True / False</option>' +
        '<option value="short_answer">Short answer</option>' +
      '</select>' +
    '</label>' +

    '<label>' +
      'Marks' +
      '<input class="quiz-question-marks" type="number" ' +
      'min="1" value="1">' +
    '</label>' +

    '<div class="quiz-options"></div>' +

    '<button type="button" class="btn btn-ghost add-option-btn">' +
      '+ Add answer' +
    '</button>' +

    '<label>' +
      'Explanation / feedback' +
      '<textarea class="quiz-question-explanation" rows="2" ' +
      'placeholder="Optional feedback shown after submission"></textarea>' +
    '</label>';

  quizQuestionsContainer.appendChild(card);

  initialiseQuizQuestion(card);
}


if (addQuizQuestionButton) {
  addQuizQuestionButton.addEventListener(
    'click',
    createQuizQuestion
  );
}

  function buildQuizPayload() {
  const cards = [
    ...document.querySelectorAll('.quiz-question-card')
  ];

  return {
    instructions:
      document.querySelector('#quizInstructions')?.value.trim() || '',

    passMark:
      Number(
        document.querySelector('#quizPassMark')?.value || 70
      ),

    maxAttempts:
      Number(
        document.querySelector('#quizAttempts')?.value || 3
      ),

    timeLimitMinutes:
      Number(
        document.querySelector('#quizTimeLimit')?.value || 0
      ),

    shuffleQuestions:
      !!document.querySelector('#quizShuffleQuestions')?.checked,

    showAnswers:
      !!document.querySelector('#quizShowAnswers')?.checked,

    questions: cards.map((card, questionIndex) => {
      const type =
        card.querySelector('.quiz-question-type').value;

      const optionRows = [
        ...card.querySelectorAll('.quiz-option-row')
      ];

      const options = optionRows.map((row, optionIndex) => ({
        text:
          row.querySelector('.quiz-option-text').value.trim(),

        correct:
          row.querySelector('.quiz-option-correct').checked,

        position: optionIndex + 1
      }));

      return {
        prompt:
          card.querySelector('.quiz-question-text').value.trim(),

        type,

        marks:
          Number(
            card.querySelector('.quiz-question-marks').value || 1
          ),

        explanation:
          card
            .querySelector('.quiz-question-explanation')
            .value.trim(),

        position: questionIndex + 1,

        options
      };
    })
  };
}

  function validateQuizPayload(quiz) {
  if (!quiz.questions.length) {
    return 'Add at least one quiz question.';
  }

  if (
    !Number.isFinite(quiz.passMark) ||
    quiz.passMark < 0 ||
    quiz.passMark > 100
  ) {
    return 'Pass mark must be between 0 and 100.';
  }

  for (let i = 0; i < quiz.questions.length; i++) {
    const q = quiz.questions[i];

    if (!q.prompt) {
      return 'Question ' + (i + 1) + ' needs question text.';
    }

    if (!q.marks || q.marks < 1) {
      return 'Question ' + (i + 1) + ' must have at least 1 mark.';
    }

    const validOptions =
      q.options.filter(option => option.text);

    if (q.type !== 'short_answer' && validOptions.length < 2) {
      return 'Question ' + (i + 1) +
        ' needs at least two answer options.';
    }

    const correct =
      validOptions.filter(option => option.correct);

    if (
      ['single', 'true_false'].includes(q.type) &&
      correct.length !== 1
    ) {
      return 'Question ' + (i + 1) +
        ' must have exactly one correct answer.';
    }

    if (
      q.type === 'multiple' &&
      correct.length < 1
    ) {
      return 'Question ' + (i + 1) +
        ' needs at least one correct answer.';
    }

    if (
      q.type === 'short_answer' &&
      !validOptions.some(option => option.correct && option.text)
    ) {
      return 'Question ' + (i + 1) +
        ' needs the accepted answer.';
    }
  }

  return '';
}
  async function loadCourses(){try{const x=await EduQuinnAuth.api('/api/instructor/courses');const c=x.courses||[];courseSelect.innerHTML=c.length?c.map(v=>`<option value="${v.id}">${esc(v.title)}</option>`).join(''):'<option value="">Create a database-backed course first</option>';if(c.length)await refresh()}catch(e){courseSelect.innerHTML='<option value="">Unable to load courses</option>';status.textContent=e.message}}
  async function refresh(){const id=courseSelect.value;if(!id){assetList.innerHTML='<p>No course selected.</p>';lessonList.innerHTML='';return}try{const [a,l]=await Promise.all([EduQuinnAuth.api(`/api/instructor/courses/${id}/assets`),EduQuinnAuth.api(`/api/instructor/courses/${id}/lessons`)]);assets=a.assets||[];assetSelect.innerHTML='<option value="">No media</option>'+assets.map(v=>`<option value="${v.id}">${esc(v.original_name)} (${v.kind})</option>`).join('');assetList.innerHTML=assets.length?assets.map(v=>`<div class="asset-row"><span>${iconSvg(v.kind==='video'?'play':v.kind==='thumbnail'?'image':'book')}</span><div><b>${esc(v.original_name)}</b><small>${esc(v.kind)} · ${fmt(v.size_bytes)}</small></div><a class="table-action" href="/api/media/${v.id}" target="_blank">Open</a></div>`).join(''):'<p>No media uploaded for this course yet.</p>';const lessons=l.lessons||[];lessonList.innerHTML=lessons.length?lessons.map((v,i)=>`<div class="asset-row"><span>${i+1}</span><div><b>${esc(v.title)}</b><small>${esc(v.lesson_type)}${v.asset_name?' · '+esc(v.asset_name):''}${v.is_preview?' · Public preview':''}</small></div><span>${v.is_preview?'Preview':'Protected'}</span></div>`).join(''):'<p>No database lessons added yet.</p>'}catch(e){status.textContent=e.message}}
  courseSelect.onchange=refresh;document.querySelector('#createBackendCourse').onclick=async()=>{const title=prompt('Course title');if(!title)return;const educationLevel=prompt('Education level: Primary, Secondary, A-Level, Tertiary or Professional','Professional')||'Professional';const category=prompt('Category or subject','Education')||'Education';const price=Number(prompt('Price in USD','0')||0);try{const x=await EduQuinnAuth.api('/api/instructor/courses',{method:'POST',body:JSON.stringify({title,educationLevel,category,price})});status.textContent='Database course created.';await loadCourses();courseSelect.value=x.id;await refresh()}catch(e){status.textContent=e.message}};
  document.querySelector('#uploadMedia').onclick=async()=>{const file=document.querySelector('#mediaFile').files[0],courseId=courseSelect.value,kind=document.querySelector('#mediaKind').value,bar=document.querySelector('#uploadBar');if(!courseId)return status.textContent='Select a course first.';if(!file)return status.textContent='Choose a file to upload.';status.textContent='Uploading…';bar.style.width='15%';try{const r=await fetch(`/api/instructor/courses/${courseId}/assets?kind=${encodeURIComponent(kind)}&name=${encodeURIComponent(file.name)}`,{method:'PUT',credentials:'same-origin',headers:{'Content-Type':file.type||'application/octet-stream'},body:file});bar.style.width='85%';const x=await r.json();if(!r.ok)throw new Error(x.message||'Upload failed');bar.style.width='100%';status.textContent='Upload complete.';document.querySelector('#mediaFile').value='';await refresh();setTimeout(()=>bar.style.width='0',700)}catch(e){bar.style.width='0';status.textContent=e.message}};
  document.querySelector('#lessonForm').onsubmit = async e => {
  e.preventDefault();

  if (!courseSelect.value) {
    status.textContent = 'Select a course first.';
    return;
  }

  const type =
    document.querySelector('#lessonType').value;

  const payload = {
    title:
      document.querySelector('#lessonTitle').value.trim(),

    lessonType: type,

    assetId: null,

    isPreview:
      document.querySelector('#lessonPreview').checked,

    position:
      Date.now() % 1000000
  };

  if (!payload.title) {
    status.textContent = 'Enter a lesson title.';
    return;
  }

  if (type === 'video') {
    payload.assetId =
      document.querySelector('#lessonAsset')?.value || null;
  }

  if (type === 'article') {
    payload.body =
      document.querySelector('#articleContent')?.value.trim() || '';

    if (!payload.body) {
      status.textContent = 'Enter the article content.';
      return;
    }
  }

  if (type === 'resource') {
    payload.assetId =
      document.querySelector('#resourceAsset')?.value || null;

    payload.body =
      document.querySelector('#resourceDescription')
        ?.value.trim() || '';

    if (!payload.assetId) {
      status.textContent = 'Select a resource.';
      return;
    }
  }

  if (type === 'assignment') {
    payload.assignment = {
      instructions:
        document.querySelector('#assignmentInstructions')
          ?.value.trim() || '',

      dueDate:
        document.querySelector('#assignmentDueDate')
          ?.value || null,

      maxMarks:
        Number(
          document.querySelector('#assignmentMarks')
            ?.value || 100
        )
    };

    if (!payload.assignment.instructions) {
      status.textContent =
        'Enter the assignment instructions.';
      return;
    }
  }

  if (type === 'quiz') {
    payload.quiz = buildQuizPayload();

    const quizError =
      validateQuizPayload(payload.quiz);

    if (quizError) {
      status.textContent = quizError;
      return;
    }
  }

  try {
    status.textContent = 'Saving lesson…';

    await EduQuinnAuth.api(
      '/api/instructor/courses/' +
      courseSelect.value +
      '/lessons',
      {
        method: 'POST',
        body: JSON.stringify(payload)
      }
    );

    e.target.reset();

    quizQuestionsContainer.innerHTML = '';
    quizQuestionIndex = 0;

    updateLessonTypeFields();

    status.textContent = 'Lesson added successfully.';

    await refresh();

  } catch (err) {
    status.textContent = err.message;
  }
};
  await loadCourses();
}
window.addEventListener('DOMContentLoaded',()=>setTimeout(init,0));
})();
