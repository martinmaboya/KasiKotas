# Frontend Guide: EFT Random Bank Account Handling

This guide explains how the frontend should work now that the backend supports **2 EFT accounts** and randomly assigns one when an EFT order is placed.

## Goal

When a customer chooses `EFT`:
- The frontend sends the order normally.
- The backend randomly selects one configured bank account.
- The selected account is saved on that order (`eftBankDetails`).
- The frontend must display the bank details from the **created order response** (and later from order fetch APIs), not from a separate static source.

---

## Backend Endpoints You Can Use

### Customer/Admin
- `POST /api/orders` - create order
- `GET /api/orders/user/{userId}` - fetch user order history
- `GET /api/orders/{id}` - fetch a specific order (admin endpoint)

### Bank details
- `GET /api/bank-details` - legacy single-item endpoint (kept for compatibility)
- `GET /api/bank-details/eft` - returns a random EFT account (optional for preview/testing)
- `GET /api/bank-details/all` - admin-only list of all configured accounts
- `POST /api/bank-details` - admin create/update account

> Important: for payment instructions after checkout, prefer `order.eftBankDetails` from the order response.

---

## Required Frontend Behavior

## 1) Checkout UI

When user selects payment method:
- If `COD`: proceed as before.
- If `EFT`: show a note such as:
  - "Bank details will be assigned once you place the order."

Do **not** permanently cache one EFT account in frontend storage for all orders.

## 2) Place Order Request

Submit the same order payload with `paymentMethod: "EFT"` (or `"eft"`, backend handles case-insensitive check).

Example request body (shortened):

```json
{
  "userId": 15,
  "paymentMethod": "EFT",
  "deliveryMethod": "DELIVERY",
  "shippingAddress": "123 Main Road",
  "subtotal": 120.0,
  "deliveryFee": 20.0,
  "discountAmount": 0.0,
  "totalAmount": 140.0,
  "orderItems": [
    {
      "product": { "id": 3 },
      "quantity": 2
    }
  ]
}
```

## 3) Handle Create Order Response

If payment is EFT, read and display:
- `response.eftBankDetails.bankName`
- `response.eftBankDetails.accountName`
- `response.eftBankDetails.accountNumber`
- `response.eftBankDetails.branchCode`
- `response.eftBankDetails.shapId`

Minimal EFT success screen content:
- Order number
- Amount due
- Assigned bank account details
- Reference/instruction text (for example: "Use order # as payment reference")

## 4) Order History / Order Details Screens

For each EFT order, always display `order.eftBankDetails` attached to that order.

This ensures the user sees the exact account assigned at checkout even if admin later edits bank configuration.

## 5) Error Handling

If backend returns error during EFT order creation:
- Example message: `No EFT bank details configured yet.`
- Show clear UI message: "EFT is temporarily unavailable. Please choose another payment method or try later."

Also handle missing `eftBankDetails` defensively:
- If payment method is EFT but bank details missing, show a support fallback message.

---

## Recommended Frontend Data Model

Add optional EFT bank details to your `Order` type/interface:

```ts
type BankDetails = {
  id: number;
  bankName: string;
  accountName: string;
  accountNumber: string;
  branchCode: string;
  shapId?: string;
};

type Order = {
  id: number;
  paymentMethod: string;
  totalAmount: number;
  // ...existing fields
  eftBankDetails?: BankDetails | null;
};
```

---

## Suggested UX Flow (EFT)

1. User chooses `EFT` at checkout.
2. User clicks place order.
3. Frontend calls `POST /api/orders`.
4. Backend creates order + randomly assigns EFT account.
5. Frontend navigates to confirmation page.
6. Confirmation page displays `order.eftBankDetails` from response.
7. Same details appear in order history/details using saved order data.

---

## Admin-Side Expectations

Admin should maintain up to 2 EFT accounts using existing admin UI via `POST /api/bank-details` and verify with `GET /api/bank-details/all`.

Frontend admin checks to add:
- Warn if trying to create more than 2 accounts.
- Allow editing existing accounts.
- Show clear validation errors from API.

---

## Quick Implementation Checklist

- [ ] Ensure checkout sends `paymentMethod` as EFT when selected.
- [ ] On order success, if EFT, render `order.eftBankDetails`.
- [ ] Update order history/detail components to show per-order EFT details.
- [ ] Add user-friendly fallback when EFT account is unavailable.
- [ ] Remove any old logic that assumes one global static EFT account.

