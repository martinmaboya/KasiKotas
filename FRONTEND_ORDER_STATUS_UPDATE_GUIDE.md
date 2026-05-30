# Frontend Order Status Update Guide (React + Vite)

This guide shows how to update order status from the admin UI and keep the UX smooth.

## Endpoint

- Method: `PUT`
- Path: `/api/orders/{orderId}/status`
- Auth: Admin JWT/session required (`hasRole('ADMIN')`)

## Request Body

Send JSON with a `status` value.

```json
{
  "status": "PROCESSING"
}
```

Valid status values depend on your backend enum (examples):
- `PENDING`
- `PROCESSING`
- `DELIVERED`
- `COLLECTED`
- `CANCELLED`

## Response (Success)

The backend returns the updated order object. Use that response to update UI immediately.

## Error Handling

On failure, the backend returns an `ApiError` JSON:

```json
{
  "message": "...",
  "code": "...",
  "path": "/api/orders/123/status",
  "timestamp": "..."
}
```

Always show `message` to the user.

## React + Vite Example

```javascript
export async function updateOrderStatus(orderId, status, token) {
  const res = await fetch(`/api/orders/${orderId}/status`, {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
    },
    body: JSON.stringify({ status }),
  });

  const data = await res.json();

  if (!res.ok) {
    throw new Error(data?.message || 'Failed to update order status.');
  }

  return data; // updated order
}
```

## UI Update Pattern

1. Call `updateOrderStatus`.
2. On success, replace the order row/state with the returned order.
3. On error, display `ApiError.message` and keep the previous UI state.

## Common Pitfalls

- Sending lowercase status values (use uppercase like `PROCESSING`).
- Showing a success toast without using the response data.
- Ignoring the error body, which hides the real reason for failure.

## Quick Troubleshooting

- Confirm admin token is attached.
- Confirm `Content-Type` is `application/json`.
- Inspect network response in devtools for `ApiError.message`.

