// Extracted out of signup.html's own inline <script> (same CSP-hash reason
// as login.js) and extended for the two new non-immediate outcomes: a new
// student account now waits on email verification instead of logging in
// straight away, and a new instructor account goes through the same
// mfa.js 2FA-enrolment step a login would.
(function () {
  'use strict';
  const form = document.getElementById('authForm');
  const nameInput = document.getElementById('name');
  const emailInput = document.getElementById('email');
  const passwordInput = document.getElementById('password');
  const roleSelect = document.getElementById('role');
  const expertiseWrap = document.getElementById('expertiseWrap');
  const expertiseSelect = document.getElementById('expertise');
  const errorBox = document.getElementById('authError');
  const btn = form && form.querySelector('button[type="submit"]');
  const termsLink = document.getElementById('termsLink');
  const termsLabel = document.getElementById('termsLabel');
  if (!form || !nameInput || !emailInput || !passwordInput || !roleSelect || !expertiseWrap || !expertiseSelect || !errorBox || !btn) { console.error('EduQuinn signup form is incomplete.'); return; }
  const q = new URLSearchParams(location.search);
  if (['student', 'instructor'].includes(q.get('role'))) roleSelect.value = q.get('role');
  function syncRole() {
    const isInstructor = roleSelect.value === 'instructor';
    expertiseWrap.hidden = !isInstructor;
    expertiseSelect.required = isInstructor;
    if (termsLink && termsLabel) {
      termsLink.href = isInstructor ? '/instructor-policy.html' : '/privacy-policy.html';
      termsLabel.textContent = isInstructor ? 'EduQuinn Instructor Terms & Privacy Policy' : 'EduQuinn Terms & Privacy Policy';
    }
  }
  roleSelect.addEventListener('change', syncRole); syncRole();

  function showCheckEmail(emailAddr) {
    form.hidden = true;
    let notice = document.getElementById('checkEmailNotice');
    if (!notice) { notice = document.createElement('div'); notice.id = 'checkEmailNotice'; notice.className = 'auth-notice'; form.parentNode.insertBefore(notice, form); }
    notice.innerHTML = 'We\'ve sent a verification link to <strong>' + emailAddr + '</strong>. Click it, then <a href="/login.html">log in</a>.<br><button type="button" id="resendVerify">Resend verification email</button>';
    document.getElementById('resendVerify').onclick = async function () {
      const b = this; b.disabled = true; b.textContent = 'Sending…';
      try { await EduQuinnAuth.api('/api/auth/resend-verification', { method: 'POST', body: JSON.stringify({ email: emailAddr }) }); } catch {}
      b.disabled = false; b.textContent = 'Email sent — check your inbox';
    };
  }

  form.addEventListener('submit', async function (e) {
    e.preventDefault(); errorBox.textContent = ''; btn.disabled = true; btn.textContent = 'Creating account…';
    const emailValue = emailInput.value;
    const result = await EduQuinnAuth.register({ name: nameInput.value, email: emailValue, password: passwordInput.value, role: roleSelect.value, expertise: roleSelect.value === 'instructor' ? expertiseSelect.value : '' });
    btn.disabled = false; btn.textContent = 'Create account';
    if (!result.ok) { errorBox.textContent = result.message; return; }
    if (result.pendingVerification) { showCheckEmail(result.email || emailValue); return; }
    if (window.EduQuinnMfa && window.EduQuinnMfa.handleMfaResponse(result, function () { location.replace('/instructor-onboarding.html'); })) return;
    if (result.user) location.replace(result.user.role === 'instructor' ? '/instructor-onboarding.html' : EduQuinnAuth.homeFor(result.user.role));
  });
})();
