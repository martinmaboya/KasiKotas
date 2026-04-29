# Frontend OTP Integration Guide

## Overview
This guide explains how to integrate the OTP-based password reset functionality into your frontend application.

---

## API Endpoints

### Base URL
```
http://localhost:8080/api/auth
```

---

## 📋 Complete Password Reset Flow

### **Step 1: Request OTP**

**Endpoint**: `POST /api/auth/forgot-password`

**What happens on backend**:
- Validates if email exists in database
- Generates random 6-digit OTP (e.g., 574829)
- Saves OTP to database with 15-minute expiry
- Sends professional HTML email with OTP to user's registered email
- Returns success response regardless of email existence (security)

**Request**:
```javascript
POST /api/auth/forgot-password
Content-Type: application/json

{
  "email": "user@example.com"
}
```

**Success Response**:
```json
{
  "success": true,
  "message": "OTP has been sent to your email"
}
```

**Error Response** (500 - Email sending failed):
```json
{
  "success": false,
  "message": "Failed to send OTP. Please try again later."
}
```

**Frontend Implementation**:
```javascript
async function requestOtp(email) {
  try {
    const response = await fetch('http://localhost:8080/api/auth/forgot-password', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ email })
    });
    
    const data = await response.json();
    
    if (data.success) {
      // Show success message
      alert('OTP has been sent to your email. Please check your inbox.');
      // Navigate to OTP verification page
      return true;
    } else {
      alert(data.message);
      return false;
    }
  } catch (error) {
    console.error('Error:', error);
    alert('Network error. Please try again.');
    return false;
  }
}
```

**UI Flow**:
1. User enters email address in "Forgot Password" form
2. User clicks "Send OTP" button
3. Show loading spinner while request is processing
4. On success: Display message "Check your email for OTP" and show OTP input form
5. On error: Display error message

---

### **Step 2: Verify OTP (Optional)**

**Endpoint**: `POST /api/auth/verify-otp`

**What happens on backend**:
- Finds most recent unused OTP for the email
- Checks if OTP has expired (15 minutes)
- Compares provided OTP with stored OTP
- Returns verification token if OTP is valid

**Request**:
```javascript
POST /api/auth/verify-otp
Content-Type: application/json

{
  "email": "user@example.com",
  "otp": "574829"
}
```

**Success Response**:
```json
{
  "success": true,
  "message": "OTP verified successfully",
  "token": "unique-reset-token-abc123"
}
```

**Error Responses**:

Invalid OTP:
```json
{
  "success": false,
  "message": "Invalid OTP"
}
```

Expired OTP:
```json
{
  "success": false,
  "message": "No valid reset request found or OTP has expired"
}
```

**Frontend Implementation**:
```javascript
async function verifyOtp(email, otp) {
  try {
    const response = await fetch('http://localhost:8080/api/auth/verify-otp', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ email, otp })
    });
    
    const data = await response.json();
    
    if (data.success) {
      // Store token for next step
      sessionStorage.setItem('resetToken', data.token);
      alert('OTP verified! Please enter your new password.');
      // Navigate to new password form
      return data.token;
    } else {
      alert(data.message);
      return null;
    }
  } catch (error) {
    console.error('Error:', error);
    alert('Network error. Please try again.');
    return null;
  }
}
```

**UI Flow**:
1. User enters 6-digit OTP received via email
2. User clicks "Verify OTP" button
3. Show loading spinner
4. On success: Show new password input form
5. On error: Display error message and allow retry

---

### **Step 3A: Reset Password with Token** (After OTP Verification)

**Endpoint**: `POST /api/auth/reset-password`

**What happens on backend**:
- Validates the reset token
- Checks if token is expired or already used
- Hashes new password with BCrypt
- Updates user's password in database
- Marks token as used (prevents reuse)

**Request**:
```javascript
POST /api/auth/reset-password
Content-Type: application/json

{
  "email": "user@example.com",
  "token": "unique-reset-token-abc123",
  "newPassword": "NewSecurePassword123!"
}
```

**Success Response**:
```json
{
  "success": true,
  "message": "Password reset successfully"
}
```

**Error Responses**:

Invalid/Expired Token:
```json
{
  "success": false,
  "message": "Invalid or expired token"
}
```

User Not Found:
```json
{
  "success": false,
  "message": "User not found"
}
```

**Frontend Implementation**:
```javascript
async function resetPassword(email, token, newPassword) {
  try {
    const response = await fetch('http://localhost:8080/api/auth/reset-password', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ email, token, newPassword })
    });
    
    const data = await response.json();
    
    if (data.success) {
      alert('Password reset successfully! You can now login with your new password.');
      // Clear stored token
      sessionStorage.removeItem('resetToken');
      // Navigate to login page
      window.location.href = '/login';
    } else {
      alert(data.message);
    }
  } catch (error) {
    console.error('Error:', error);
    alert('Network error. Please try again.');
  }
}
```

---

### **Step 3B: Reset Password with OTP Directly** (Recommended - Simpler Flow)

**Endpoint**: `POST /api/auth/reset-password-with-otp`

**What happens on backend**:
- Finds most recent unused OTP for the email
- Verifies OTP matches and hasn't expired
- Hashes new password with BCrypt
- Updates user's password in database
- Marks OTP as used (prevents reuse)

**Request**:
```javascript
POST /api/auth/reset-password-with-otp
Content-Type: application/json

{
  "email": "user@example.com",
  "otp": "574829",
  "newPassword": "NewSecurePassword123!"
}
```

**Success Response**:
```json
{
  "success": true,
  "message": "Password reset successfully"
}
```

**Error Responses**:

Invalid OTP:
```json
{
  "success": false,
  "message": "Invalid OTP"
}
```

Expired OTP:
```json
{
  "success": false,
  "message": "No valid reset request found or OTP has expired"
}
```

**Frontend Implementation**:
```javascript
async function resetPasswordWithOtp(email, otp, newPassword) {
  try {
    const response = await fetch('http://localhost:8080/api/auth/reset-password-with-otp', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ email, otp, newPassword })
    });
    
    const data = await response.json();
    
    if (data.success) {
      alert('Password reset successfully! You can now login with your new password.');
      // Navigate to login page
      window.location.href = '/login';
    } else {
      alert(data.message);
    }
  } catch (error) {
    console.error('Error:', error);
    alert('Network error. Please try again.');
  }
}
```

**UI Flow**:
1. User enters OTP and new password in the same form
2. User clicks "Reset Password" button
3. Show loading spinner
4. On success: Show success message and redirect to login
5. On error: Display error message

---

### **Bonus: Get User's First Name** (For Personalization)

**Endpoint**: `GET /api/auth/user-firstname?email={email}`

**What happens on backend**:
- Looks up user by email
- Returns first name if user exists

**Request**:
```javascript
GET /api/auth/user-firstname?email=user@example.com
```

**Success Response**:
```json
{
  "success": true,
  "firstName": "John"
}
```

**Error Response**:
```json
{
  "success": false,
  "firstName": ""
}
```

**Frontend Implementation**:
```javascript
async function getUserFirstName(email) {
  try {
    const response = await fetch(
      `http://localhost:8080/api/auth/user-firstname?email=${encodeURIComponent(email)}`
    );
    
    const data = await response.json();
    
    if (data.success) {
      return data.firstName;
    }
    return null;
  } catch (error) {
    console.error('Error:', error);
    return null;
  }
}

// Usage: Personalize the forgot password page
const firstName = await getUserFirstName('user@example.com');
if (firstName) {
  document.getElementById('greeting').textContent = `Hi ${firstName}!`;
}
```

---

## 🎨 Complete Frontend Example (React)

### Option 1: Simple Flow (Recommended)

```jsx
import React, { useState } from 'react';

function PasswordReset() {
  const [step, setStep] = useState(1); // 1: Email, 2: OTP + Password
  const [email, setEmail] = useState('');
  const [otp, setOtp] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState('');

  // Step 1: Request OTP
  const handleRequestOtp = async (e) => {
    e.preventDefault();
    setLoading(true);
    setMessage('');

    try {
      const response = await fetch('http://localhost:8080/api/auth/forgot-password', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email })
      });

      const data = await response.json();

      if (data.success) {
        setMessage('✅ OTP sent to your email. Check your inbox!');
        setStep(2); // Move to OTP + password form
      } else {
        setMessage('❌ ' + data.message);
      }
    } catch (error) {
      setMessage('❌ Network error. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  // Step 2: Reset password with OTP
  const handleResetPassword = async (e) => {
    e.preventDefault();
    setLoading(true);
    setMessage('');

    try {
      const response = await fetch('http://localhost:8080/api/auth/reset-password-with-otp', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email, otp, newPassword })
      });

      const data = await response.json();

      if (data.success) {
        setMessage('✅ Password reset successfully!');
        setTimeout(() => {
          window.location.href = '/login';
        }, 2000);
      } else {
        setMessage('❌ ' + data.message);
      }
    } catch (error) {
      setMessage('❌ Network error. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="password-reset-container">
      <h2>Reset Password</h2>

      {message && <div className="message">{message}</div>}

      {step === 1 && (
        <form onSubmit={handleRequestOtp}>
          <div>
            <label>Email Address</label>
            <input
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required
              placeholder="Enter your email"
            />
          </div>
          <button type="submit" disabled={loading}>
            {loading ? 'Sending...' : 'Send OTP'}
          </button>
        </form>
      )}

      {step === 2 && (
        <form onSubmit={handleResetPassword}>
          <div>
            <label>6-Digit OTP</label>
            <input
              type="text"
              value={otp}
              onChange={(e) => setOtp(e.target.value)}
              required
              maxLength="6"
              placeholder="Enter OTP from email"
            />
          </div>
          <div>
            <label>New Password</label>
            <input
              type="password"
              value={newPassword}
              onChange={(e) => setNewPassword(e.target.value)}
              required
              minLength="8"
              placeholder="Enter new password"
            />
          </div>
          <button type="submit" disabled={loading}>
            {loading ? 'Resetting...' : 'Reset Password'}
          </button>
          <button type="button" onClick={() => setStep(1)}>
            ← Back
          </button>
        </form>
      )}
    </div>
  );
}

export default PasswordReset;
```

---

### Option 2: Two-Step Verification Flow

```jsx
import React, { useState } from 'react';

function PasswordResetTwoStep() {
  const [step, setStep] = useState(1); // 1: Email, 2: Verify OTP, 3: New Password
  const [email, setEmail] = useState('');
  const [otp, setOtp] = useState('');
  const [resetToken, setResetToken] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState('');

  // Step 1: Request OTP
  const handleRequestOtp = async (e) => {
    e.preventDefault();
    setLoading(true);
    setMessage('');

    try {
      const response = await fetch('http://localhost:8080/api/auth/forgot-password', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email })
      });

      const data = await response.json();

      if (data.success) {
        setMessage('✅ OTP sent! Check your email.');
        setStep(2);
      } else {
        setMessage('❌ ' + data.message);
      }
    } catch (error) {
      setMessage('❌ Network error.');
    } finally {
      setLoading(false);
    }
  };

  // Step 2: Verify OTP
  const handleVerifyOtp = async (e) => {
    e.preventDefault();
    setLoading(true);
    setMessage('');

    try {
      const response = await fetch('http://localhost:8080/api/auth/verify-otp', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email, otp })
      });

      const data = await response.json();

      if (data.success) {
        setResetToken(data.token);
        setMessage('✅ OTP verified! Enter new password.');
        setStep(3);
      } else {
        setMessage('❌ ' + data.message);
      }
    } catch (error) {
      setMessage('❌ Network error.');
    } finally {
      setLoading(false);
    }
  };

  // Step 3: Reset password
  const handleResetPassword = async (e) => {
    e.preventDefault();
    setLoading(true);
    setMessage('');

    try {
      const response = await fetch('http://localhost:8080/api/auth/reset-password', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email, token: resetToken, newPassword })
      });

      const data = await response.json();

      if (data.success) {
        setMessage('✅ Password reset successfully!');
        setTimeout(() => window.location.href = '/login', 2000);
      } else {
        setMessage('❌ ' + data.message);
      }
    } catch (error) {
      setMessage('❌ Network error.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="password-reset-container">
      <h2>Reset Password</h2>
      {message && <div className="message">{message}</div>}

      {step === 1 && (
        <form onSubmit={handleRequestOtp}>
          <input
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            placeholder="Enter your email"
            required
          />
          <button type="submit" disabled={loading}>
            {loading ? 'Sending...' : 'Send OTP'}
          </button>
        </form>
      )}

      {step === 2 && (
        <form onSubmit={handleVerifyOtp}>
          <input
            type="text"
            value={otp}
            onChange={(e) => setOtp(e.target.value)}
            placeholder="Enter 6-digit OTP"
            maxLength="6"
            required
          />
          <button type="submit" disabled={loading}>
            {loading ? 'Verifying...' : 'Verify OTP'}
          </button>
        </form>
      )}

      {step === 3 && (
        <form onSubmit={handleResetPassword}>
          <input
            type="password"
            value={newPassword}
            onChange={(e) => setNewPassword(e.target.value)}
            placeholder="Enter new password"
            minLength="8"
            required
          />
          <button type="submit" disabled={loading}>
            {loading ? 'Resetting...' : 'Reset Password'}
          </button>
        </form>
      )}
    </div>
  );
}

export default PasswordResetTwoStep;
```

---

## 🎯 User Experience Best Practices

### 1. **Email Input Page**
- Clear heading: "Forgot Your Password?"
- Simple email input field
- "Send OTP" button
- Link back to login page

### 2. **OTP Input Page**
- Display message: "We've sent a 6-digit code to {email}"
- 6-digit OTP input (consider using separate input boxes for each digit)
- Timer showing OTP expiry (15 minutes countdown)
- "Resend OTP" button (if expired or not received)
- "Verify" button

### 3. **New Password Page**
- Password strength indicator
- "Show/Hide Password" toggle
- Password requirements list:
  - Minimum 8 characters
  - At least one uppercase letter
  - At least one number
  - Special characters recommended
- "Reset Password" button

### 4. **Success Page**
- Success message with checkmark icon
- Auto-redirect to login after 3 seconds
- "Go to Login" button

---

## ⚠️ Error Handling

### Common Errors to Handle:

1. **Invalid Email Format**
   - Frontend validation before API call
   - Show error: "Please enter a valid email address"

2. **Email Not Found**
   - Backend returns success anyway (security)
   - Show: "If email exists, OTP has been sent"

3. **Invalid OTP**
   - Show: "Invalid OTP. Please try again."
   - Allow user to re-enter OTP

4. **Expired OTP**
   - Show: "OTP has expired. Please request a new one."
   - Provide "Resend OTP" button

5. **Network Errors**
   - Show: "Connection error. Please check your internet."
   - Provide "Retry" button

6. **Weak Password**
   - Frontend validation
   - Show password requirements

---

## 🔒 Security Considerations

1. **Don't reveal if email exists**: Always show success message
2. **Rate limiting**: Consider adding on frontend to prevent spam
3. **HTTPS only**: Use secure connections in production
4. **Clear sensitive data**: Clear OTP and tokens after use
5. **Session timeout**: Add countdown timer for OTP expiry
6. **Input sanitization**: Validate all inputs on frontend

---

## 📱 Mobile Considerations

1. **OTP Auto-fill**: Use `autocomplete="one-time-code"` for OTP input
2. **Numeric keyboard**: Use `type="tel"` or `inputmode="numeric"` for OTP
3. **Large touch targets**: Make buttons at least 44x44px
4. **Clear error messages**: Use large, readable fonts

---

## 🧪 Testing Checklist

- [ ] User can request OTP with valid email
- [ ] OTP email is received within 30 seconds
- [ ] OTP input accepts 6 digits only
- [ ] Valid OTP proceeds to password reset
- [ ] Invalid OTP shows error message
- [ ] Expired OTP (after 15 min) shows expiry message
- [ ] New password meets requirements
- [ ] Password reset succeeds and redirects to login
- [ ] User can login with new password
- [ ] Used OTP cannot be reused

---

## 📊 Analytics Events to Track

```javascript
// Track password reset flow
analytics.track('Password Reset Started', { email });
analytics.track('OTP Requested', { email });
analytics.track('OTP Verified', { email });
analytics.track('Password Reset Completed', { email });
analytics.track('Password Reset Failed', { email, error: message });
```

---

## 🚀 Production Deployment

### Environment Variables:
```env
REACT_APP_API_URL=https://your-api-domain.com/api/auth
```

### Update API calls:
```javascript
const API_URL = process.env.REACT_APP_API_URL || 'http://localhost:8080/api/auth';

fetch(`${API_URL}/forgot-password`, { ... })
```

---

## Summary

**Recommended Flow (Simplest)**:
1. POST `/forgot-password` → Send OTP email
2. POST `/reset-password-with-otp` → Verify OTP + Reset password
3. Redirect to login

**Alternative Flow (More Secure)**:
1. POST `/forgot-password` → Send OTP email
2. POST `/verify-otp` → Verify OTP, get token
3. POST `/reset-password` → Reset password with token
4. Redirect to login

Both flows are fully implemented and ready to use!
