// Extracted out of login.html's own inline <script> (it was previously
// baked into the page's CSP via a sha256 hash — moving it to an external
// /self file sidesteps needing to keep that hash in sync every time this
// logic changes) and extended to handle the two new non-immediate login
// outcomes: a student who still needs to verify their email, and an
// instructor who needs to go through mfa.js first.
(function () {
  'use strict';
  const form = document.getElementById('authForm'), choice = document.getElementById('roleChoice'), email = document.getElementById('email'), password = document.getElementById('password'), err = document.getElementById('authError'), btn = form.querySelector('button[type="submit"]'), label = document.getElementById('selectedRoleLabel');
  let role = '';
  function choose(r) { role = r; choice.hidden = true; form.hidden = false; label.textContent = r === 'student' ? 'Student login' : 'Teacher login'; email.focus(); }
  document.querySelectorAll('[data-role]').forEach(function (b) { b.onclick = function () { choose(b.dataset.role); }; });
  document.getElementById('changeRole').onclick = function () { role = ''; form.hidden = true; choice.hidden = false; err.textContent = ''; };
  const q = new URLSearchParams(location.search);
  if (['student', 'instructor'].includes(q.get('role'))) choose(q.get('role'));

  function goHome(user) { const next = q.get('next'); location.replace(next || EduQuinnAuth.homeFor(user.role)); }

  function showVerificationNotice(emailAddr) {
    err.textContent = '';
    let notice = document.getElementById('verifyNotice');
    if (!notice) { notice = document.createElement('div'); notice.id = 'verifyNotice'; notice.className = 'auth-notice'; form.insertBefore(notice, err); }
    notice.innerHTML = 'Please verify your email address before logging in. <button type="button" id="resendVerify">Resend verification email</button>';
    document.getElementById('resendVerify').onclick = async function () {
      const b = this; b.disabled = true; b.textContent = 'Sending…';
      try { await EduQuinnAuth.api('/api/auth/resend-verification', { method: 'POST', body: JSON.stringify({ email: emailAddr }) }); } catch {}
      b.disabled = false; b.textContent = 'Email sent — check your inbox';
    };
  }

  form.addEventListener('submit', async function (e) {
    e.preventDefault(); err.textContent = ''; btn.disabled = true; btn.textContent = 'Signing in…';
    const result = await EduQuinnAuth.login(email.value, password.value, 'public', role);
    btn.disabled = false; btn.textContent = 'Log in securely';
    if (!result.ok) {
      if (result.needsVerification) { showVerificationNotice(result.email || email.value); return; }
      err.textContent = result.message; return;
    }
    if (window.EduQuinnMfa && window.EduQuinnMfa.handleMfaResponse(result, goHome)) return;
    goHome(result.user);
  });
})();
