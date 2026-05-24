# Frontend Order Integration Guide

This guide explains how the frontend must talk to the KasiKotas backend for ordering, EFT payment display, and inventory refresh.

## 1) What the frontend must send when creating an order

Use `POST /api/orders`.

### Request body shape

```json
{
  "userId": 7,
  "shippingAddress": "123 Main Road",
  "paymentMethod": "EFT",
  "deliveryMethod": "DELIVERY",
  "promoCode": "SAVE10",
  "scheduledDeliveryTime": "2026-05-24T18:30:00",
  "orderItems": [
    {
      "product": {
        "id": 3
      },
      "quantity": 2,
      "customizationNotes": "No onions",
      "selectedExtrasJson": "[{\"id\":8,\"quantity\":1}]",
      "selectedSaucesJson": "[{\"name\":\"Mayo\"}]"
    }
  ]
}
```

### Required fields
- `userId`
- `paymentMethod`
- `orderItems`
- `orderItems[].product.id`
- `orderItems[].quantity`

### Optional fields
- `shippingAddress`
- `deliveryMethod`
- `promoCode`
- `scheduledDeliveryTime`
- `customizationNotes`
- `selectedExtrasJson`
- `selectedSaucesJson`

## 2) What the frontend must NOT send

Do **not** send these server-owned fields:
- `status`
- `orderDate`
- `totalAmount`
- `subtotal`
- `deliveryFee`
- `discountAmount`
- `version`
- `user` object
- full `product` objects
- `eftBankDetails`
- `eftBankDetailsId`
- `eftBankName`
- `eftAccountName`
- `eftAccountNumber`
- `eftShapId`
- `eftBranchCode`

The backend now assigns EFT bank details itself and stores a snapshot safely.

## 3) How to display EFT details after success

When `paymentMethod` is `EFT`, the order response may contain `eftBankDetails`.

### Display these fields
- `bankName`
- `accountName`
- `accountNumber`
- `shapId`
- `branchCode`

### Example success handling
- Show the order number.
- Show the total amount.
- Show EFT payment details from the response.
- Show a clear message that the order is confirmed and payment is pending.

### Safe fallback
If `eftBankDetails` is missing for an older order, show:
> EFT payment details are unavailable for this order. Please contact support and include your order number.

## 4) Error handling the frontend must support

The backend returns structured errors through a global exception handler.

### Error shape
```json
{
  "message": "Only 2 kota(s) left for today. Please reduce your quantity.",
  "code": "BUSINESS_CONFLICT",
  "timestamp": "2026-05-24T15:00:00Z",
  "path": "/api/orders"
}
```

### HTTP status handling

#### `400 Bad Request`
Use for:
- missing required fields
- invalid payload structure
- invalid date format
- invalid quantities
- malformed JSON fields

UI action:
- show the error message
- keep the form open
- highlight the invalid fields if possible

#### `403 Forbidden`
Use for:
- access denied
- unauthorized admin actions
- not allowed operations

UI action:
- show the message
- redirect to login or show permission denied if needed

#### `409 Conflict`
Use for:
- sold out / order limit exceeded
- stock conflict
- high traffic / concurrency conflict

UI action:
- show the message
- let the user reduce quantity or try again later
- refresh product stock data before retrying

#### `500 Internal Server Error`
Use for:
- unexpected backend failure

UI action:
- show a generic retry message
- log the response for debugging

## 5) What the frontend should refresh after order changes

Because stock and order totals can change, the frontend should refresh data after these actions:

### After successful order creation
- refresh the cart
- refresh product availability / stock display
- refresh the user’s orders
- refresh admin counters if your dashboard shows them

### After cancelling an order
- refresh order details
- refresh product availability / stock display
- refresh order counts / daily limit data

### After deleting an order
- refresh order history
- refresh product availability / stock display
- refresh order counts / daily limit data

### Important
Do not rely on stale cached UI state after any order mutation.

## 6) Recommended frontend checkout flow

1. Validate the form locally.
2. Build the order payload.
3. Submit `POST /api/orders`.
4. If success:
   - show confirmation screen
   - show EFT details when payment method is EFT
   - clear or reset cart state
5. If error:
   - display backend `message`
   - handle `400`, `403`, `409`, `500` properly

## 7) Recommended UI behavior for stock and sold-out cases

If backend returns a conflict message such as:
- sold out for today
- only X kotas left
- insufficient stock
- high traffic right now

Then the frontend should:
- keep the cart open
- avoid auto-submitting again immediately
- let the user lower quantity or retry
- re-fetch product data before another checkout attempt

## 8) Helpful backend endpoints for the frontend

### Orders
- `POST /api/orders` — place order
- `GET /api/orders/user/{userId}` — user order history
- `GET /api/orders/{id}` — order details for admins
- `PUT /api/orders/{orderId}/status` — admin status update
- `DELETE /api/orders/{id}` — admin delete order

### Bank details
- `GET /api/bank-details/eft` — admin/customer EFT view if needed

### Limits / dashboard
- `GET /api/order-limit` — daily order limit overview
- `GET /api/order-limit/todays-kotas` — dashboard counts

## 9) Frontend implementation checklist

- [ ] Stop sending EFT bank details from the frontend
- [ ] Send only product IDs, quantities, and customer-entered notes/extras/sauces
- [ ] Render EFT snapshot from the backend response
- [ ] Handle `400`, `403`, `409`, and `500` cleanly
- [ ] Refresh product stock after create/cancel/delete
- [ ] Refresh order history after mutations
- [ ] Re-fetch daily limit data on admin dashboards after order changes
- [ ] Show a support fallback when EFT details are missing on older orders

## 10) Summary

The backend now owns:
- EFT bank snapshot assignment
- stock decrementing
- stock restoration on cancel/delete
- daily limit enforcement
- conflict detection under load

The frontend should:
- send clean order payloads
- display backend-provided EFT details
- refresh UI state after any order mutation
- show backend error messages directly to the user

