# Frontend Order Refresh & Security Guide

This guide explains what the frontend must do after the backend security and cache updates.

## Checklist

- [ ] Do **not** send `userId` when creating orders
- [ ] Use the logged-in session/user from the backend
- [ ] Refetch orders after create, update, delete, or status change
- [ ] Make the refresh button call the API again
- [ ] Invalidate frontend query/state cache after mutations
- [ ] Display backend error messages as returned

## 1) Creating Orders

### What changed
The backend now resolves the order owner from the authenticated session.

### What the frontend must do
Do **not** include `userId` in the order request body.

### Send only:
- `shippingAddress`
- `paymentMethod`
- `deliveryMethod`
- `promoCode` (optional)
- `scheduledDeliveryTime` (optional)
- `orderItems`

### Example
```json
{
  "shippingAddress": "Room 12, Block A",
  "paymentMethod": "eft",
  "deliveryMethod": "DELIVERY",
  "promoCode": "",
  "scheduledDeliveryTime": "2026-06-01T19:30:00",
  "orderItems": [
    {
      "product": { "id": 5 },
      "quantity": 2,
      "customizationNotes": "No onions",
      "selectedExtrasJson": "[]",
      "selectedSaucesJson": "[]"
    }
  ]
}
```

## 2) Refreshing Order Status on Customer Site

If the admin changes an order status, the customer page must fetch fresh data from the backend.

### The refresh button should:
- call `GET /api/orders/user/{userId}`
- replace the current UI state with the returned response
- not rely only on old in-memory state

### Important
If the page only updates after a full browser refresh, the problem is usually frontend state handling, not backend logic.

## 3) Cache Invalidation

The backend now evicts order-list cache entries after:
- order creation
- order status update
- order deletion

That helps, but the frontend should still refresh its own local/query cache after any mutation.

### If you use React Query
Invalidate the user orders query after mutations.

Example:
```ts
queryClient.invalidateQueries({ queryKey: ['userOrders', userId] })
```

### If you use SWR
Call `mutate()` after mutations.

### If you use plain React state
Call the API again and `setState(response.data)`.

## 4) Error Handling

The frontend should display backend error messages directly.

### Examples
- `ORDER_LIMIT_EXCEEDED` -> show sold-out message
- `CONCURRENCY_CONFLICT` -> show high-traffic / retry message
- `UNAUTHORIZED` -> redirect to login or show session-expired message

### Recommendation
Do not hardcode success/error text only in the frontend. Prefer backend message + code.

## 5) Order List / Order Detail UI

Your customer order page should:
- load data on mount
- refresh from backend when the user clicks refresh
- update immediately after a successful mutation

### Good pattern
1. User clicks refresh
2. Frontend calls the API
3. Backend returns latest orders
4. Frontend updates state

## 6) Security Notes

### Already fixed in backend
- Customers cannot view other customers' orders
- Order creation no longer trusts client-supplied `userId`
- Admin-only order management remains protected

### What the frontend must avoid
- Sending user IDs for ownership
- Caching sensitive order data too aggressively without invalidation
- Assuming status changes will appear without refetching

## 7) Summary

### Frontend must change
- Remove `userId` from create-order payloads
- Make refresh buttons actually call the API
- Invalidate/refetch order data after create/update/delete

### Frontend does **not** need to change
- Security rules for ownership enforcement
- Backend authorization logic
- Error message definitions, unless you want prettier UI handling

---

If you want, you can keep this guide next to the other frontend docs and use it as the implementation checklist for the UI.

