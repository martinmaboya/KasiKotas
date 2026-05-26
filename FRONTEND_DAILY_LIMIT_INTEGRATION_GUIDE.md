# Frontend Daily Limit Integration Guide

This guide shows exactly how the admin frontend should update and display the order daily limit.

## Endpoint

- Base path: `/api/order-limit`
- Auth: Admin JWT/session required (`hasRole('ADMIN')`)
- Allowed methods for update: `POST`, `PUT`, `PATCH`

## Update Request

Send JSON with **one** of these keys:

- `limitValue` (preferred)
- `limit`
- `dailyLimit`
- `totalLimit`

### Example request body

```json
{
  "limitValue": 120
}
```

## Update Response (success)

The backend now returns the **confirmed saved values** from DB:

```json
{
  "id": 1,
  "limitValue": 120,
  "kotasOrderedToday": 37,
  "remainingCapacity": 83,
  "message": "Order limit updated successfully."
}
```

## Frontend Rules (important)

1. Do not show success based only on HTTP 200.
2. After update, use `response.limitValue` to refresh the input/label.
3. Show `remainingCapacity` and `kotasOrderedToday` from the same response.
4. On error, render backend message from `ApiError.message`.

## JavaScript Example (drop-in)

```javascript
export async function updateDailyLimit(limitValue, token) {
  const res = await fetch('/api/order-limit', {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
    },
    body: JSON.stringify({ limitValue }),
  });

  const data = await res.json();

  if (!res.ok) {
    // GlobalExceptionHandler returns ApiError payload with message/code/path/timestamp
    throw new Error(data?.message || 'Failed to update order limit.');
  }

  // Use returned values as source of truth
  return {
    id: data.id,
    limitValue: data.limitValue,
    kotasOrderedToday: data.kotasOrderedToday,
    remainingCapacity: data.remainingCapacity,
    message: data.message,
  };
}
```

## React Usage Example

```javascript
const [limitValue, setLimitValue] = useState(0);
const [kotasOrderedToday, setKotasOrderedToday] = useState(0);
const [remainingCapacity, setRemainingCapacity] = useState(0);
const [status, setStatus] = useState('');

async function onSave() {
  try {
    const updated = await updateDailyLimit(Number(limitValue), authToken);
    setLimitValue(updated.limitValue);
    setKotasOrderedToday(updated.kotasOrderedToday);
    setRemainingCapacity(updated.remainingCapacity);
    setStatus(updated.message || 'Limit updated successfully.');
  } catch (err) {
    setStatus(err.message);
  }
}
```

## Optional: Refresh from GET endpoint

You can still fetch current state via:

- `GET /api/order-limit`

Example response includes:

- `id`
- `limitValue`
- `kotasOrderedToday`
- `kotasOrderedAllTime`
- `remainingCapacity`

Use this on page load, and use update response immediately after save.

## Quick Troubleshooting

If UI says success but value looks unchanged:

1. Confirm request body sends numeric value (`limitValue: 120`, not empty string).
2. Confirm token belongs to an admin user.
3. Confirm UI state is set from response `data.limitValue` (not old local state).
4. Inspect network response payload in browser dev tools.
5. If response is error JSON, show `message` from backend instead of generic toast.

