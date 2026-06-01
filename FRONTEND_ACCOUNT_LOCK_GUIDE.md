# Frontend Account Lock/Unlock Feature Guide

This guide explains how to implement the admin account lock/unlock feature on the frontend and how to handle locked accounts during login.

## Checklist

- [ ] Display list of all users with lock status (admin only)
- [ ] Add lock/unlock buttons in user list or admin dashboard
- [ ] Call lock/unlock endpoints from admin panel
- [ ] Handle `403 Forbidden` error during login for locked accounts
- [ ] Show "Your account is suspended" message to locked users
- [ ] Refresh user list after locking/unlocking

## 1) Admin: Fetch All Users

**Endpoint:** `GET /api/users`

Only admins can access this.

### Example Request

```ts
async function fetchAllUsers(token: string) {
  const response = await fetch('/api/users', {
    method: 'GET',
    headers: {
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json'
    }
  });

  if (!response.ok) {
    throw new Error(`Failed to fetch users: ${response.status}`);
  }

  return response.json(); // Returns User[]
}
```

### Example Response

```json
[
  {
    "id": 1,
    "email": "customer@example.com",
    "firstName": "John",
    "lastName": "Doe",
    "role": "CUSTOMER",
    "isLocked": false,
    "phoneNumber": "+123456"
  },
  {
    "id": 2,
    "email": "baduser@example.com",
    "firstName": "Jane",
    "lastName": "Smith",
    "role": "CUSTOMER",
    "isLocked": true
  }
]
```

## 2) Admin: Lock a User Account

**Endpoint:** `PUT /api/users/{userId}/lock`

Only admins can lock accounts.

### Example Request

```ts
async function lockUser(userId: number, token: string) {
  const response = await fetch(`/api/users/${userId}/lock`, {
    method: 'PUT',
    headers: {
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json'
    }
  });

  if (response.ok) {
    return { success: true, message: 'User account locked' };
  } else if (response.status === 404) {
    return { success: false, message: 'User not found' };
  } else {
    return { success: false, message: 'Failed to lock user' };
  }
}
```

### Success Response

```
200 OK
(empty body)
```

### Error Response

```
404 Not Found
(user does not exist)
```

## 3) Admin: Unlock a User Account

**Endpoint:** `PUT /api/users/{userId}/unlock`

Only admins can unlock accounts.

### Example Request

```ts
async function unlockUser(userId: number, token: string) {
  const response = await fetch(`/api/users/${userId}/unlock`, {
    method: 'PUT',
    headers: {
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json'
    }
  });

  if (response.ok) {
    return { success: true, message: 'User account unlocked' };
  } else if (response.status === 404) {
    return { success: false, message: 'User not found' };
  } else {
    return { success: false, message: 'Failed to unlock user' };
  }
}
```

### Success Response

```
200 OK
(empty body)
```

## 4) Admin UI: User List with Lock Status

### Example React Component

```tsx
import React, { useState, useEffect } from 'react';

interface User {
  id: number;
  email: string;
  firstName: string;
  lastName: string;
  role: string;
  isLocked: boolean;
}

export function AdminUserList() {
  const [users, setUsers] = useState<User[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const token = localStorage.getItem('token');

  useEffect(() => {
    loadUsers();
  }, []);

  const loadUsers = async () => {
    try {
      setLoading(true);
      const response = await fetch('/api/users', {
        headers: { 'Authorization': `Bearer ${token}` }
      });

      if (!response.ok) throw new Error('Failed to load users');

      const data = await response.json();
      setUsers(data);
      setError(null);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  const handleLock = async (userId: number) => {
    try {
      const response = await fetch(`/api/users/${userId}/lock`, {
        method: 'PUT',
        headers: { 'Authorization': `Bearer ${token}` }
      });

      if (response.ok) {
        setUsers(users.map(u => 
          u.id === userId ? { ...u, isLocked: true } : u
        ));
        alert('User account locked');
      } else {
        alert('Failed to lock user');
      }
    } catch (err) {
      alert('Error: ' + err.message);
    }
  };

  const handleUnlock = async (userId: number) => {
    try {
      const response = await fetch(`/api/users/${userId}/unlock`, {
        method: 'PUT',
        headers: { 'Authorization': `Bearer ${token}` }
      });

      if (response.ok) {
        setUsers(users.map(u => 
          u.id === userId ? { ...u, isLocked: false } : u
        ));
        alert('User account unlocked');
      } else {
        alert('Failed to unlock user');
      }
    } catch (err) {
      alert('Error: ' + err.message);
    }
  };

  if (loading) return <p>Loading users...</p>;
  if (error) return <p style={{ color: 'red' }}>Error: {error}</p>;

  return (
    <div>
      <h2>All Users</h2>
      <table border="1" cellPadding="10">
        <thead>
          <tr>
            <th>ID</th>
            <th>Email</th>
            <th>Name</th>
            <th>Role</th>
            <th>Status</th>
            <th>Actions</th>
          </tr>
        </thead>
        <tbody>
          {users.map(user => (
            <tr key={user.id}>
              <td>{user.id}</td>
              <td>{user.email}</td>
              <td>{user.firstName} {user.lastName}</td>
              <td>{user.role}</td>
              <td style={{ color: user.isLocked ? 'red' : 'green' }}>
                {user.isLocked ? '🔒 Locked' : '✓ Active'}
              </td>
              <td>
                {user.isLocked ? (
                  <button onClick={() => handleUnlock(user.id)}>
                    Unlock
                  </button>
                ) : (
                  <button onClick={() => handleLock(user.id)}>
                    Lock
                  </button>
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
```

## 5) Customer Login: Handle Locked Account

When a locked account tries to login, the backend returns **403 Forbidden** with this message:

```json
{
  "message": "Your account is suspended. Please contact Support."
}
```

### Example Login Handler

```ts
async function handleLogin(email: string, password: string) {
  try {
    const response = await fetch('/api/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        email,
        password,
        enablePasskey: false
      })
    });

    if (response.status === 403) {
      // Account is locked
      const errorData = await response.json();
      showErrorToast(errorData.message);
      // Show: "Your account is suspended. Please contact Support."
      return;
    }

    if (response.status === 401) {
      // Invalid credentials
      showErrorToast('Invalid email or password');
      return;
    }

    if (!response.ok) {
      showErrorToast('Login failed. Please try again.');
      return;
    }

    const data = await response.json();
    
    // Save token and redirect to dashboard
    localStorage.setItem('token', data.token);
    localStorage.setItem('userId', data.id);
    localStorage.setItem('userName', data.firstName);
    
    window.location.href = '/dashboard';

  } catch (error) {
    showErrorToast('An error occurred during login');
  }
}
```

## 6) HTTP Status Codes Reference

| Status | Meaning | Action |
|--------|---------|--------|
| `200 OK` | Lock/Unlock successful | Refresh user list, show success message |
| `403 Forbidden` | Account is locked (login attempt) | Show "Account suspended" message, do NOT redirect |
| `401 Unauthorized` | Invalid credentials (login) | Show "Invalid email or password" |
| `404 Not Found` | User not found | Show "User not found" |

## 7) UI/UX Best Practices

### For Admin Panel

- Show lock status visually (e.g., 🔒 icon for locked accounts)
- Use distinct colors: 
  - 🟢 Green for **Active** accounts
  - 🔴 Red for **Locked** accounts
- Confirm before locking: "Are you sure you want to lock this account?"
- Show a brief success message after locking/unlocking

### For Customer Login

- Do **NOT** suggest "Sign up again"
- Do **NOT** show a generic error message
- Show the exact message from backend: **"Your account is suspended. Please contact Support."**
- Provide a support contact link or email

### Example Error Toast

```tsx
function showLockedAccountError() {
  showToast({
    type: 'error',
    title: 'Account Suspended',
    message: 'Your account is suspended. Please contact Support.',
    duration: 5000
  });
}
```

## 8) Backend Response Handling Summary

### Login Success

```json
{
  "message": "Login successful",
  "token": "eyJ...",
  "id": 5,
  "firstName": "John",
  "role": "CUSTOMER"
}
```

### Locked Account (403)

```json
{
  "message": "Your account is suspended. Please contact Support."
}
```

### Invalid Credentials (401)

```json
{
  "message": "Invalid credentials"
}
```

## 9) Example: Complete Admin Panel Flow

```ts
import React from 'react';

export function AdminPanel() {
  const [users, setUsers] = React.useState([]);
  const token = localStorage.getItem('token');

  React.useEffect(() => {
    // Fetch users on mount
    fetch('/api/users', {
      headers: { 'Authorization': `Bearer ${token}` }
    })
      .then(r => r.json())
      .then(setUsers);
  }, []);

  const toggleLock = async (userId: number, currentlyLocked: boolean) => {
    const endpoint = currentlyLocked ? 'unlock' : 'lock';
    
    const response = await fetch(`/api/users/${userId}/${endpoint}`, {
      method: 'PUT',
      headers: { 'Authorization': `Bearer ${token}` }
    });

    if (response.ok) {
      // Update local state
      setUsers(users.map(u => 
        u.id === userId 
          ? { ...u, isLocked: !currentlyLocked }
          : u
      ));
    }
  };

  return (
    <div>
      <h1>Admin: Manage Users</h1>
      {users.map(user => (
        <div key={user.id} style={{ 
          border: '1px solid #ccc', 
          padding: '10px', 
          margin: '10px 0',
          backgroundColor: user.isLocked ? '#ffe0e0' : '#e0ffe0'
        }}>
          <h3>{user.firstName} {user.lastName}</h3>
          <p>Email: {user.email}</p>
          <p>Status: {user.isLocked ? '🔒 LOCKED' : '✓ ACTIVE'}</p>
          <button 
            onClick={() => toggleLock(user.id, user.isLocked)}
            style={{ 
              padding: '8px 16px',
              backgroundColor: user.isLocked ? '#4CAF50' : '#f44336',
              color: 'white',
              border: 'none',
              cursor: 'pointer'
            }}
          >
            {user.isLocked ? 'Unlock Account' : 'Lock Account'}
          </button>
        </div>
      ))}
    </div>
  );
}
```

## 10) Summary

### What Frontend Must Do

- ✅ Fetch user list with `GET /api/users` (admin only)
- ✅ Call `PUT /api/users/{id}/lock` to lock
- ✅ Call `PUT /api/users/{id}/unlock` to unlock
- ✅ Handle `403 Forbidden` on login and show suspension message
- ✅ Update UI to reflect lock status
- ✅ Refresh user list after lock/unlock actions

### What Frontend Must NOT Do

- ❌ Ignore the `403 Forbidden` response and suggest re-registration
- ❌ Show generic error messages instead of backend message
- ❌ Cache user list forever without refresh
- ❌ Allow customers to unlock their own accounts

---

Implement these flows and the account lock feature will be complete on both frontend and backend.

