# Passkey (Fingerprint / Face ID) Frontend Implementation Guide

## Context

This is a **web application** (not a mobile app). The backend is a Spring Boot REST API deployed on Render.
WebAuthn is the browser standard that enables fingerprint and Face ID on the web — the phone's biometric
sensor is the authenticator, the browser handles everything via `navigator.credentials`.

---

## Backend Endpoints (already implemented, no changes needed)

| Method | URL | Auth Required | Purpose |
|--------|-----|---------------|---------|
| POST | `/api/auth/passkey/register/options` | No | Get registration challenge |
| POST | `/api/auth/passkey/register/verify` | No | Save enrolled credential |
| POST | `/api/auth/passkey/login/options` | No | Get login challenge (returns 400 if no passkey enrolled) |
| POST | `/api/auth/passkey/login/verify` | No | Authenticate and get JWT |
| GET | `/api/auth/passkey` | Yes (JWT) | List user's enrolled passkeys |
| DELETE | `/api/auth/passkey/{passkeyId}` | Yes (JWT) | Delete a passkey |

### Login verify success response (same shape as password login)
```json
{
  "message": "Login successful",
  "token": "<jwt>",
  "id": 1,
  "firstName": "John",
  "role": "USER"
}
```

### Registration options request body
```json
{ "email": "user@example.com" }
```

### Registration verify request body
```json
{
  "requestId": "<uuid from options response>",
  "credential": { ...credentialToJSON(credential) },
  "nickname": "My Phone"
}
```

### Login options request body
```json
{ "email": "user@example.com" }
```

### Login verify request body
```json
{
  "requestId": "<uuid from options response>",
  "credential": { ...credentialToJSON(credential) }
}
```

---

## The Two Flows

### Flow 1 — Enroll (after password login)
```
User logs in with password
  → Backend returns JWT
  → Frontend asks: "Enable fingerprint login on this device?"
  → User confirms
  → POST /api/auth/passkey/register/options  → get challenge
  → navigator.credentials.create()           → browser shows fingerprint/Face ID prompt
  → POST /api/auth/passkey/register/verify   → backend saves credential
  → Done — next login can use fingerprint
```

### Flow 2 — Login with fingerprint
```
User opens login page, types email, clicks fingerprint button
  → POST /api/auth/passkey/login/options
      → 400 = no passkey enrolled → show password form instead
      → 200 = challenge received → continue
  → navigator.credentials.get()             → browser shows fingerprint/Face ID prompt
  → POST /api/auth/passkey/login/verify      → backend returns JWT
  → Store JWT, redirect to dashboard
```

---

## File: `passkey-utils.js`

Create this file and import from it wherever needed.

```javascript
// passkey-utils.js

function bufferToBase64Url(buf) {
  const bytes = new Uint8Array(buf);
  let s = '';
  bytes.forEach(b => s += String.fromCharCode(b));
  return btoa(s).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
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

// Converts backend registration options → browser-ready format
export function prepareCreationOptions(publicKey) {
  const opts = JSON.parse(JSON.stringify(publicKey));
  opts.challenge = base64UrlToBuffer(opts.challenge);
  opts.user.id = base64UrlToBuffer(opts.user.id);
  if (opts.excludeCredentials) {
    opts.excludeCredentials = opts.excludeCredentials.map(c => ({
      ...c, id: base64UrlToBuffer(c.id)
    }));
  }
  return opts;
}

// Converts backend login options → browser-ready format
export function prepareRequestOptions(publicKey) {
  const opts = JSON.parse(JSON.stringify(publicKey));
  opts.challenge = base64UrlToBuffer(opts.challenge);
  if (opts.allowCredentials) {
    opts.allowCredentials = opts.allowCredentials.map(c => ({
      ...c, id: base64UrlToBuffer(c.id)
    }));
  }
  return opts;
}

// Converts browser credential response → JSON the backend expects
export function credentialToJSON(cred) {
  const r = cred.response;
  return {
    id: cred.id,
    rawId: bufferToBase64Url(cred.rawId),
    type: cred.type,
    response: {
      clientDataJSON: bufferToBase64Url(r.clientDataJSON),
      attestationObject: r.attestationObject ? bufferToBase64Url(r.attestationObject) : undefined,
      authenticatorData: r.authenticatorData ? bufferToBase64Url(r.authenticatorData) : undefined,
      signature: r.signature ? bufferToBase64Url(r.signature) : undefined,
      userHandle: r.userHandle ? bufferToBase64Url(r.userHandle) : undefined,
    },
    clientExtensionResults: cred.getClientExtensionResults?.() ?? {}
  };
}

// Check if the browser supports WebAuthn (passkeys)
export function isPasskeySupported() {
  return window.PublicKeyCredential !== undefined;
}
```

---

## File: `passkey-api.js`

```javascript
// passkey-api.js
import { prepareCreationOptions, prepareRequestOptions, credentialToJSON } from './passkey-utils';

const BASE_URL = '/api/auth'; // adjust if your API base URL is different

/**
 * Enroll fingerprint/Face ID for the given email.
 * Call this after a successful password login when the user opts in.
 * @param {string} email
 * @param {string} nickname - label for this passkey e.g. "My Phone"
 */
export async function enrollPasskey(email, nickname = 'My Phone') {
  // Step 1: get challenge from backend
  const optsRes = await fetch(`${BASE_URL}/passkey/register/options`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email })
  });
  if (!optsRes.ok) throw new Error(await optsRes.text());
  const { requestId, publicKey } = await optsRes.json();

  // Step 2: browser shows fingerprint / Face ID prompt
  const credential = await navigator.credentials.create({
    publicKey: prepareCreationOptions(publicKey)
  });

  // Step 3: send result to backend to save
  const verifyRes = await fetch(`${BASE_URL}/passkey/register/verify`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ requestId, credential: credentialToJSON(credential), nickname })
  });
  if (!verifyRes.ok) throw new Error(await verifyRes.text());
  return await verifyRes.json(); // { message: "Passkey registered successfully" }
}

/**
 * Login using fingerprint/Face ID.
 * Returns { status: 'no-passkey' } if none enrolled → fall back to password form.
 * Returns { status: 'cancelled' } if user dismissed the prompt.
 * Returns { token, id, firstName, role } on success.
 * @param {string} email
 */
export async function loginWithPasskey(email) {
  // Step 1: get challenge — 400 means no passkey enrolled
  const optsRes = await fetch(`${BASE_URL}/passkey/login/options`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email })
  });
  if (!optsRes.ok) return { status: 'no-passkey' };

  const { requestId, publicKey } = await optsRes.json();

  // Step 2: browser shows fingerprint / Face ID prompt
  let credential;
  try {
    credential = await navigator.credentials.get({
      publicKey: prepareRequestOptions(publicKey)
    });
  } catch (err) {
    // User cancelled or device doesn't support it
    return { status: 'cancelled' };
  }

  // Step 3: verify — backend returns same JWT payload as password login
  const verifyRes = await fetch(`${BASE_URL}/passkey/login/verify`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ requestId, credential: credentialToJSON(credential) })
  });
  if (!verifyRes.ok) return { status: 'error', message: await verifyRes.text() };

  return await verifyRes.json(); // { token, id, firstName, role }
}

/**
 * Fetch all passkeys enrolled for the logged-in user.
 * Requires Authorization header with JWT.
 * @param {string} token - JWT
 */
export async function listPasskeys(token) {
  const res = await fetch(`${BASE_URL}/passkey`, {
    headers: { Authorization: `Bearer ${token}` }
  });
  if (!res.ok) throw new Error(await res.text());
  return await res.json();
}

/**
 * Delete a passkey by ID.
 * @param {number} passkeyId
 * @param {string} token - JWT
 */
export async function deletePasskey(passkeyId, token) {
  const res = await fetch(`${BASE_URL}/passkey/${passkeyId}`, {
    method: 'DELETE',
    headers: { Authorization: `Bearer ${token}` }
  });
  if (!res.ok) throw new Error(await res.text());
  return await res.json();
}
```

---

## Wiring Into the Login Page

```javascript
import { isPasskeySupported, loginWithPasskey, enrollPasskey } from './passkey-utils';
// or from passkey-api.js depending on how you split the files

// On login page mount — only show fingerprint button if browser supports it
if (isPasskeySupported()) {
  showFingerprintButton(); // show a fingerprint icon / "Use Fingerprint" button
}

// When user clicks the fingerprint button (email already typed in the field)
async function handlePasskeyLogin(email) {
  if (!email) {
    showError('Please enter your email first');
    return;
  }

  const result = await loginWithPasskey(email);

  if (result.status === 'no-passkey') {
    // No passkey enrolled on this account — show password form
    showPasswordForm();
    showInfo('No fingerprint enrolled. Please login with your password.');
  } else if (result.status === 'cancelled') {
    // User dismissed the prompt — do nothing or show password form
    showPasswordForm();
  } else if (result.status === 'error') {
    showError('Fingerprint login failed. Please use your password.');
    showPasswordForm();
  } else if (result.token) {
    // Success
    saveToken(result.token);       // store JWT in localStorage / cookie
    saveUser({ id: result.id, firstName: result.firstName, role: result.role });
    redirectToDashboard();
  }
}

// After successful password login — offer to enable fingerprint for next time
async function afterPasswordLogin(user) {
  if (!isPasskeySupported()) return; // device/browser doesn't support it

  const wantsPasskey = confirm('Enable fingerprint / Face ID login on this device next time?');
  if (!wantsPasskey) return;

  try {
    await enrollPasskey(user.email, 'My Device');
    showSuccess('Fingerprint login enabled!');
  } catch (err) {
    // Enrollment failed or user cancelled the biometric prompt — not critical
    console.warn('Passkey enrollment failed:', err.message);
  }
}
```

---

## UX Recommendations

- Show a fingerprint icon button on the login page **only if** `isPasskeySupported()` returns true.
- The fingerprint button should be **next to or below the email field**, not replacing the password form entirely — the user needs to type their email first so the backend knows which challenge to generate.
- After the user types their email and clicks the fingerprint button, call `loginWithPasskey(email)`.
- If it returns `no-passkey` or `cancelled`, gracefully fall back to showing the password input.
- After a successful **password** login, show a one-time prompt: *"Enable fingerprint login on this device?"*
- In account settings, show a list of enrolled passkeys (from `listPasskeys()`) with a delete button for each.

---

## Important Constraints

| Constraint | Detail |
|------------|--------|
| HTTPS required | WebAuthn only works on HTTPS or `localhost`. Plain HTTP will silently fail. |
| Per device | A passkey enrolled on a phone won't work on a laptop. Each device must enroll separately. |
| rpId must match domain | The backend `webauthn.rp-id` in `application.properties` must exactly match the frontend domain (no protocol, no port). Current value: `kasikotas-frondend.onrender.com` — verify this matches your actual domain. |
| No library needed | The helpers above use the native browser `navigator.credentials` API directly. No npm package required. |
| Challenge is one-time | Each `requestId` from the options endpoint can only be used once. Never reuse it. |

---

## Backend `application.properties` WebAuthn Config (for reference)

```properties
webauthn.rp-id=${WEBAUTHN_RP_ID:kasikotas-frondend.onrender.com}
webauthn.rp-name=${WEBAUTHN_RP_NAME:KasiKotas}
webauthn.allowed-origins=${WEBAUTHN_ALLOWED_ORIGINS:https://kasikotas-frondend.onrender.com,http://localhost:5173,http://localhost:5174,http://localhost:3000}
webauthn.challenge.ttlMs=${WEBAUTHN_CHALLENGE_TTL_MS:300000}
```

The `rpId` must equal the **effective domain** of the page calling `navigator.credentials` — no `https://`, no path, no port.

---

Generated: 2025
