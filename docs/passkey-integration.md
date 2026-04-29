# Passkey (WebAuthn) Integration Notes

**Decision Steps**

- If the user is on the same device/profile that enrolled a passkey, call the passkey login flow directly.
- If passkey login fails (400/no credentials, user cancels, unsupported), fall back to password login and offer enroll option.
- Only show enrollment UI when backend indicates no passkey for that account or the user explicitly requests enrollment.

**Backend Notes**

- Use `POST /api/auth/passkey/login/options` with `{ email }` to probe/start login; returns 400 if none enrolled.
- For password-login-time enrollment use `POST /api/auth/login` with `enablePasskey=true` — backend returns registration options when needed.
- Keep `POST /api/auth/passkey/register/options` and `POST /api/auth/passkey/register/verify` for explicit enrollment flows.
- Backend returns the same JWT login payload as password login: `{ message, token, id, firstName, role }` on success.

**Frontend Snippets**

- Try passkey login (preferred path)

```javascript
async function attemptPasskeyLogin(email) {
  try {
    const optsResp = await fetch('/api/auth/passkey/login/options', {
      method: 'POST', headers: {'Content-Type':'application/json'}, body: JSON.stringify({email})
    });
    if (!optsResp.ok) {
      return {status:'no-passkey'};
    }
    const { requestId, publicKey } = await optsResp.json();
    const publicKeyOptions = preformatPublicKeyRequestOptions(publicKey);
    const cred = await navigator.credentials.get({ publicKey: publicKeyOptions });
    const credentialJson = credentialToJSON(cred);
    const verifyResp = await fetch('/api/auth/passkey/login/verify', {
      method: 'POST',
      headers: {'Content-Type':'application/json'},
      body: JSON.stringify({ requestId, credential: credentialJson })
    });
    if (!verifyResp.ok) throw new Error('verify failed');
    return await verifyResp.json(); // { message, token, id, firstName, role }
  } catch (err) {
    return { status:'error', error: err.message };
  }
}
```

- Registration (enroll) flow (explicit)

```javascript
async function startRegistration(email, nickname) {
  const optsResp = await fetch('/api/auth/passkey/register/options', {
    method:'POST', headers:{'Content-Type':'application/json'}, body: JSON.stringify({ email })
  });
  const { requestId, publicKey } = await optsResp.json();
  const publicKeyOptions = preformatPublicKeyCreationOptions(publicKey);
  const credential = await navigator.credentials.create({ publicKey: publicKeyOptions });
  const credentialJson = credentialToJSON(credential);
  const verifyResp = await fetch('/api/auth/passkey/register/verify', {
    method:'POST', headers:{'Content-Type':'application/json'},
    body: JSON.stringify({ requestId, credential: credentialJson, nickname })
  });
  return verifyResp.ok ? await verifyResp.json() : { error: await verifyResp.text() };
}
```

**Helper utilities (minimal)**

```javascript
function bufferToBase64Url(buf) {
  const bytes = new Uint8Array(buf);
  let s = '';
  bytes.forEach(b => s += String.fromCharCode(b));
  return btoa(s).replace(/\+/g,'-').replace(/\//g,'_').replace(/=+$/,'');
}

function base64UrlToBuffer(base64url) {
  const padding = '='.repeat((4 - base64url.length % 4) % 4);
  const base64 = (base64url + padding).replace(/-/g, '+').replace(/_/g, '/');
  const str = atob(base64);
  const buf = new ArrayBuffer(str.length);
  const view = new Uint8Array(buf);
  for (let i = 0; i < str.length; i++) view[i] = str.charCodeAt(i);
  return buf;
}

function preformatPublicKeyRequestOptions(opts) {
  const copy = JSON.parse(JSON.stringify(opts));
  copy.challenge = base64UrlToBuffer(copy.challenge);
  if (Array.isArray(copy.allowCredentials)) {
    copy.allowCredentials = copy.allowCredentials.map(c => ({
      type: c.type,
      id: base64UrlToBuffer(c.id),
      ...(c.transports ? { transports: Array.isArray(c.transports) ? c.transports : [] } : {})
    }));
  }
  return copy;
}

function preformatPublicKeyCreationOptions(opts) {
  const copy = JSON.parse(JSON.stringify(opts));
  copy.challenge = base64UrlToBuffer(copy.challenge);
  copy.user.id = base64UrlToBuffer(copy.user.id);
  if (Array.isArray(copy.excludeCredentials)) {
    copy.excludeCredentials = copy.excludeCredentials.map(c => ({
      type: c.type,
      id: base64UrlToBuffer(c.id)
    }));
  }
  return copy;
}

function credentialToJSON(cred) {
  if (!cred) return null;
  const response = cred.response;
  return {
    id: cred.id,
    rawId: bufferToBase64Url(cred.rawId),
    type: cred.type,
    response: {
      clientDataJSON: bufferToBase64Url(response.clientDataJSON),
      attestationObject: response.attestationObject ? bufferToBase64Url(response.attestationObject) : undefined,
      authenticatorData: response.authenticatorData ? bufferToBase64Url(response.authenticatorData) : undefined,
      signature: response.signature ? bufferToBase64Url(response.signature) : undefined,
      userHandle: response.userHandle ? bufferToBase64Url(response.userHandle) : undefined
    },
    clientExtensionResults: cred.getClientExtensionResults()
  };
}
```

**Integration checklist**

- On login screen show two options: "Password" and "Passkey" (only if device supports WebAuthn).
- When user chooses Passkey: call `attemptPasskeyLogin(email)`.
- If `attemptPasskeyLogin` returns `{status:'no-passkey'}` show password UI with an option to enable passkey after successful password login.
- After password login, if backend returns registration options (enablePasskey flow), call `startRegistration(...)` before navigating away.
- Remember passkeys are per device/profile; if user switches devices they must re-enroll.

**Next actions / Options**

- Add a small `/internal/webauthn/debug` endpoint that returns rp config and whether the current user has passkeys (useful for client debugging).
- I can produce a reusable React hook/component implementing these flows if you want a drop-in client.

---

Generated on: 2026-04-29


