// Landing page for the link mailed by /api/auth/register (student branch)
// and /api/auth/resend-verification. Doesn't depend on auth.js - this page
// has to work whether or not the visitor has an active session.
(function () {
  'use strict';
  const heading = document.getElementById('verifyHeading'), message = document.getElementById('verifyMessage'), actions = document.getElementById('verifyActions');

  function showResendForm(prefillEmail) {
    actions.innerHTML =
      '<label>Email address<input id="resendEmail" type="email" required autocomplete="email" value="' + (prefillEmail || '').replace(/"/g, '&quot;') + '" placeholder="you@example.com"></label>' +
      '<button type="button" class="btn btn-primary btn-full" id="resendBtn">Send a new verification link</button>';
    document.getElementById('resendBtn').onclick = async function () {
      const emailInput = document.getElementById('resendEmail'), b = this;
      if (!emailInput.value) { emailInput.focus(); return; }
      b.disabled = true; b.textContent = 'Sending…';
      try {
        const r = await fetch('/api/auth/resend-verification', { method: 'POST', credentials: 'same-origin', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ email: emailInput.value }) });
        const x = await r.json().catch(function () { return {}; });
        message.textContent = x.message || 'If that email needs verifying, a new link is on its way.';
        actions.innerHTML = '';
      } catch { b.disabled = false; b.textContent = 'Send a new verification link'; message.textContent = 'Something went wrong. Please try again.'; }
    };
  }

  async function run() {
    const token = new URLSearchParams(location.search).get('token');
    if (!token) {
      heading.textContent = 'Missing verification link';
      message.textContent = 'This page needs the link from your verification email. If you need a new one, enter your email below.';
      showResendForm('');
      return;
    }
    try {
      const r = await fetch('/api/auth/verify-email', { method: 'POST', credentials: 'same-origin', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ token: token }) });
      const x = await r.json().catch(function () { return {}; });
      if (r.ok && x.ok) {
        heading.textContent = 'Your email is verified';
        message.textContent = 'You can now log in to your EduQuinn account.';
        actions.innerHTML = '<a class="btn btn-primary btn-full" href="/login.html">Log in</a>';
        return;
      }
      heading.textContent = 'This link has expired';
      message.textContent = x.message || 'This verification link is invalid or has expired. Enter your email below for a new one.';
      showResendForm('');
    } catch {
      heading.textContent = 'Something went wrong';
      message.textContent = 'We could not reach EduQuinn just now. Please try again in a moment.';
    }
  }
  run();
})();
