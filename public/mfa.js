// Shared MFA step used by both login.html and signup.html: a password
// check that succeeded for an instructor doesn't get a session straight
// away (see /api/auth/mfa/verify on the server) - it returns here first,
// as an overlay on top of whichever form triggered it, and only calls back
// into that page's own redirect logic once a real session exists.
(function () {
  'use strict';
  function el(html) { const t = document.createElement('template'); t.innerHTML = html.trim(); return t.content.firstElementChild; }

  function modalShell(title, bodyHtml) {
    const modal = el(
      '<div class="mfa-overlay" role="dialog" aria-modal="true">' +
        '<div class="mfa-card"><h2>' + title + '</h2>' + bodyHtml + '</div>' +
      '</div>'
    );
    document.body.appendChild(modal);
    return modal;
  }

  function wireSubmit(modal, challengeId, onSession, isSetup) {
    const input = modal.querySelector('#mfaCode'), err = modal.querySelector('#mfaError'), btn = modal.querySelector('#mfaSubmit');
    if (input) input.focus();
    const resetBtn = () => { btn.disabled = false; btn.textContent = isSetup ? 'Confirm and enable' : 'Verify'; };
    const submit = async () => {
      err.textContent = ''; btn.disabled = true; btn.textContent = 'Verifying…';
      try {
        const r = await fetch('/api/auth/mfa/verify', { method: 'POST', credentials: 'same-origin', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ challengeId: challengeId, code: input.value }) });
        let x = {}; try { x = await r.json(); } catch {}
        resetBtn();
        if (!r.ok) { err.textContent = x.message || 'That code is incorrect or expired.'; return; }
        if (x.backupCodes && x.backupCodes.length) { showBackupCodes(modal, x.backupCodes, function () { modal.remove(); onSession(x.user); }); return; }
        modal.remove(); onSession(x.user);
      } catch { resetBtn(); err.textContent = 'Something went wrong. Please try again.'; }
    };
    btn.addEventListener('click', submit);
    if (input) input.addEventListener('keydown', function (e) { if (e.key === 'Enter') { e.preventDefault(); submit(); } });
  }

  function showBackupCodes(modal, codes, onContinue) {
    modal.querySelector('.mfa-card').innerHTML =
      '<h2>Save your backup codes</h2>' +
      '<p>If you ever lose access to your authenticator app, use one of these one-time codes to sign in instead. Save them somewhere safe - they will not be shown again.</p>' +
      '<ul class="mfa-backup-codes">' + codes.map(function (c) { return '<li><code>' + c + '</code></li>'; }).join('') + '</ul>' +
      '<button type="button" class="btn btn-primary btn-full" id="mfaContinue">I\'ve saved these — continue</button>';
    modal.querySelector('#mfaContinue').addEventListener('click', onContinue);
  }

  function showSetup(data, onSession) {
    const modal = modalShell('Set up two-factor authentication', '' +
      '<p>EduQuinn requires instructors to protect their account with an authenticator app (Google Authenticator, Authy, 1Password, etc).</p>' +
      (data.qrDataUrl ? '<img class="mfa-qr" src="' + data.qrDataUrl + '" alt="Scan this QR code in your authenticator app">' : '') +
      '<p>Can\'t scan it? Enter this code manually in your app:</p>' +
      '<code class="mfa-secret">' + data.secret + '</code>' +
      '<label>Enter the 6-digit code from your app<input type="text" inputmode="numeric" autocomplete="one-time-code" maxlength="6" id="mfaCode"></label>' +
      '<p class="form-error" id="mfaError" role="alert"></p>' +
      '<button type="button" class="btn btn-primary btn-full" id="mfaSubmit">Confirm and enable</button>'
    );
    wireSubmit(modal, data.challengeId, onSession, true);
  }

  function showChallenge(data, onSession) {
    const modal = modalShell('Enter your authentication code', '' +
      '<p>Enter the 6-digit code from your authenticator app, or one of your backup codes if you do not have it.</p>' +
      '<label>Authentication code<input type="text" inputmode="numeric" autocomplete="one-time-code" maxlength="10" id="mfaCode"></label>' +
      '<p class="form-error" id="mfaError" role="alert"></p>' +
      '<button type="button" class="btn btn-primary btn-full" id="mfaSubmit">Verify</button>'
    );
    wireSubmit(modal, data.challengeId, onSession, false);
  }

  // Returns true if `data` (the raw login/register API response) was an MFA
  // step and has been handled (an overlay is now showing); false means the
  // caller should carry on treating it as an ordinary response.
  function handleMfaResponse(data, onSession) {
    if (data && data.mfaSetupRequired) { showSetup(data, onSession); return true; }
    if (data && data.mfaRequired) { showChallenge(data, onSession); return true; }
    return false;
  }

  window.EduQuinnMfa = { handleMfaResponse: handleMfaResponse };
})();
