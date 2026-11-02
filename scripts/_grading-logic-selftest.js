// Standalone self-test of the quiz-grading algorithm used in server.js'
// POST /api/learning/assessments/:id/submit handler. This mirrors that logic
// exactly (copy kept in sync by hand) so it can be exercised with mock
// question rows, without needing a live Postgres connection or the `pg`
// driver installed. Run: node scripts/_grading-logic-selftest.js
'use strict';

function grade(questionRows, answers) {
  let earned = 0, possible = 0;
  const results = questionRows.map((v, i) => {
    const marks = Math.max(1, Number(v.marks || 1)); possible += marks;
    const given = answers[i]; const type = v.question_type || 'single'; let ok = false;
    if (type === 'multiple') {
      const correctIdx = (v.correct_answers || []).map(opt => Number(opt.index)).sort((p, n) => p - n);
      const givenIdx = Array.isArray(given) ? [...new Set(given.map(Number))].filter(n => Number.isInteger(n)).sort((p, n) => p - n) : [];
      ok = correctIdx.length > 0 && correctIdx.length === givenIdx.length && correctIdx.every((val, idx) => val === givenIdx[idx]);
    } else if (type === 'short_answer') {
      const accepted = (v.correct_answers || []).map(opt => String(opt.text || '').trim().toLowerCase()).filter(Boolean);
      ok = accepted.includes(String(given || '').trim().toLowerCase());
    } else {
      ok = v.correct_index !== null && v.correct_index !== undefined && Number(given) === Number(v.correct_index);
    }
    if (ok) earned += marks;
    return ok;
  });
  const score = possible ? Math.round(earned * 100 / possible) : 0;
  return { score, results };
}

function assert(cond, msg) { if (!cond) { console.error('FAIL:', msg); process.exitCode = 1; } else { console.log('PASS:', msg); } }

// Single-choice: correct_index = 1
{
  const rows = [{ question_type: 'single', correct_index: 1, marks: 1 }];
  const { score } = grade(rows, [1]);
  assert(score === 100, 'single-choice correct answer scores 100');
  const { score: s2 } = grade(rows, [0]);
  assert(s2 === 0, 'single-choice wrong answer scores 0');
}

// True/false behaves like single
{
  const rows = [{ question_type: 'true_false', correct_index: 0, marks: 1 }];
  assert(grade(rows, [0]).score === 100, 'true_false correct scores 100');
  assert(grade(rows, [1]).score === 0, 'true_false wrong scores 0');
}

// Multiple-answer: must select exactly the correct set, order independent
{
  const rows = [{ question_type: 'multiple', correct_answers: [{ index: 0 }, { index: 2 }], marks: 2 }];
  assert(grade(rows, [[2, 0]]).score === 100, 'multiple-answer correct set (any order) scores 100');
  assert(grade(rows, [[0]]).score === 0, 'multiple-answer partial selection scores 0');
  assert(grade(rows, [[0, 1, 2]]).score === 0, 'multiple-answer superset selection scores 0');
}

// Short answer: case-insensitive, trimmed match against accepted text
{
  const rows = [{ question_type: 'short_answer', correct_answers: [{ text: 'Harare' }], marks: 1 }];
  assert(grade(rows, ['harare']).score === 100, 'short_answer matches case-insensitively');
  assert(grade(rows, ['  Harare  ']).score === 100, 'short_answer matches with surrounding whitespace');
  assert(grade(rows, ['Bulawayo']).score === 0, 'short_answer wrong text scores 0');
}

// Mixed quiz with weighted marks
{
  const rows = [
    { question_type: 'single', correct_index: 0, marks: 1 },
    { question_type: 'multiple', correct_answers: [{ index: 1 }], marks: 3 }
  ];
  // Gets the 1-mark question right, the 3-mark question wrong -> 1/4 = 25%
  assert(grade(rows, [0, [0]]).score === 25, 'weighted marks: partial credit computed correctly (25%)');
}

if (process.exitCode) {
  console.error('\nSome grading self-tests FAILED.');
} else {
  console.log('\nAll grading self-tests passed.');
}
