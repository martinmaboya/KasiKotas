# Frontend End-to-End EFT Snapshot Checklist

## Purpose
This document tells the frontend exactly what must happen so EFT bank details work safely and consistently from checkout to order history, including admin bank-details management and audit visibility.

The frontend must trust the **saved order snapshot**, not the live bank-details table, for any order that already exists.

---

## Core rule

### Frontend trust model
- **Use `order.eftBankDetails` for any created EFT order.**
- **Do not send `eftBankDetails` in the checkout payload.**
- **Never re-fetch live bank details to render an old order.**
- **Never cache one EFT account for all customers.**
- **Never hardcode bank details in the UI.**
- **If the snapshot is missing, fail safely and tell the customer to contact support.**

---

## 1) EFT checkout flow

### What must happen
When the customer chooses `EFT` and places the order:

1. Send the order normally to `POST /api/orders`.
2. Do **not** include `eftBankDetails` in the request body.
2. Wait for the order creation response.
3. The backend will choose the EFT bank details and save them on the order.
4. If the response includes `order.eftBankDetails`, display those values immediately as the payment instructions.
5. Show the instructions exactly as returned for that order.
6. Persist only the order id or order reference in the UI state, not a shared bank account.

### Do not do
- Do not use `GET /api/bank-details/eft` as the checkout result.
- Do not build or send a bank-details object from the frontend for checkout.
- Do not store one EFT account in localStorage, sessionStorage, or global app state.
- Do not assume the live bank details table matches the order that was just created.

### Display these fields from the order snapshot
- `bankName`
- `accountName`
- `accountNumber`
- `branchCode`
- `shapId`

---

## 2) Order confirmation screen

### What must happen
After successful checkout:
- Show a clear success message.
- Display the EFT payment instructions from `order.eftBankDetails`.
- Show the order number and total amount.
- Make it obvious that the displayed bank details belong to **that specific order**.

### Recommended UI copy
- `Your order has been placed successfully.`
- `Please pay using the bank details below for this order only.`

### Do not do
- Do not refresh live bank details after the order is created.
- Do not replace the snapshot with the current admin bank configuration.

---

## 3) Order history and order details

### What must happen
For every previously created order:
- Render the saved `order.eftBankDetails` if the order used EFT.
- Use the exact snapshot returned by the backend.
- If the order is old and has no EFT snapshot, show a safe fallback message instead of guessing another account.

### Safe fallback message
Use a message like:
- `EFT payment details are unavailable for this old order. Please contact support.`

### Do not do
- Do not fetch live bank details for historical orders.
- Do not infer bank details from the current admin bank config.
- Do not hide the order as broken just because the snapshot is missing; show a clear support message.

---

## 4) Customer order detail page behavior

### What must happen
On the order detail page:
- If `paymentMethod === 'EFT'`, render the snapshot details.
- If `order.eftBankDetails` is present, show it in a clearly labeled payment section.
- If the snapshot is missing, show the support fallback.
- If the order uses another payment method, do not show EFT instructions.

### Recommended layout
- Order summary
- Payment method
- EFT payment instructions section
- Support fallback section for legacy orders

---

## 5) Admin bank-details management screen

### What must happen
Admins must still be able to manage the live bank-details configuration.

Frontend must:
- keep the create/edit bank-details form
- call `POST /api/bank-details`
- refresh the list with `GET /api/bank-details/all`
- handle validation errors clearly
- handle conflict or version errors by telling the admin to refresh and retry

### Do not do
- Do not expose bank-details editing to public users.
- Do not use the admin config screen as the source of truth for old customer orders.

### Admin list should show
- bank name
- account name
- account number
- branch code
- SHAP ID
- archived/active state if available
- last verified time if available

---

## 6) Audit/history view for admins

### What must happen
The frontend should add or keep an audit/history section that reads from:
- `GET /api/bank-details/audit`

Show the following fields if returned:
- `action` (`CREATE`, `UPDATE`, `DELETE`)
- `actorUsername`
- `changedAt`
- `beforeSnapshotJson`
- `afterSnapshotJson`

### Purpose
This helps admins see who changed EFT details and when, and spot accidental edits or tampering.

### Recommended UI behavior
- sort newest first
- show a compact diff-style view if possible
- allow opening full JSON snapshots for support/debugging

---

## 7) Error handling rules

### When checkout fails
If the backend refuses checkout or order creation:
- show a clear error
- keep the cart state intact if possible
- allow retry

### When data integrity fails
If the backend reports tampering, checksum failure, or blocked order processing:
- show a serious but non-technical message
- do not continue to payment instructions
- do not attempt to fetch another live bank account

### When a conflict happens
If the backend returns an optimistic-lock or conflict error:
- tell the user/admin to refresh and retry

### Suggested messages
- `Your data changed on the server. Please refresh and try again.`
- `Payment details are temporarily unavailable. Please contact support.`

---

## 8) State management rules

### What must happen
- Store the created order response in state.
- Use the order snapshot returned from the API as the source of truth.
- Reuse the order snapshot when rendering confirmation, history, or details.

### Do not do
- Do not keep one shared EFT account in a global store.
- Do not rehydrate old orders from live bank configuration.
- Do not depend on a separate bank-details fetch for past order rendering.

---

## 9) API contract the frontend should expect

### Orders
- `POST /api/orders`
- `GET /api/orders/user/{userId}`
- `GET /api/orders/{id}`

### Bank details
- `GET /api/bank-details`
- `GET /api/bank-details/eft`
- `GET /api/bank-details/all`
- `POST /api/bank-details`
- `GET /api/bank-details/audit`

### Important contract rule
For EFT orders, the frontend should trust the returned order snapshot, especially `order.eftBankDetails`.

---

## 10) Recommended frontend types

### `BankDetails`
```ts
export type BankDetails = {
  id?: number;
  bankName: string;
  accountName: string;
  accountNumber: string;
  branchCode: string;
  shapId?: string;
};
```

### `Order`
```ts
export type Order = {
  id: number;
  paymentMethod: string;
  totalAmount: number;
  eftBankDetails?: BankDetails | null;
  // other existing fields
};
```

### `BankDetailsAudit`
```ts
export type BankDetailsAudit = {
  id: number;
  bankDetailsId?: number | null;
  action: 'CREATE' | 'UPDATE' | 'DELETE';
  actorUsername: string;
  changedAt: string;
  beforeSnapshotJson?: string | null;
  afterSnapshotJson?: string | null;
};
```

---

## 11) What the frontend must remove

Remove any logic that:
- stores a single EFT account for all orders
- treats `GET /api/bank-details/eft` as the source of truth for old orders
- reloads live bank details when rendering a previously created EFT order
- assumes bank details never change
- silently swaps one bank account for another

---

## 12) Acceptance checklist

Use this to confirm the frontend is ready:

- [ ] EFT checkout shows the bank details returned with the created order
- [ ] Order confirmation displays the saved EFT snapshot
- [ ] Order history reads `order.eftBankDetails`
- [ ] Order detail pages render the saved snapshot
- [ ] Old orders without snapshots show a safe support message
- [ ] Admin bank-details create/edit still works
- [ ] Admin audit/history page displays change records
- [ ] Conflict and validation errors are handled cleanly
- [ ] No EFT account is cached globally for all orders
- [ ] No live bank details are used to rewrite past orders

---

## 13) Final rule of thumb
If the order already exists, the frontend must trust the **order snapshot**.
If the order does not have a snapshot, the frontend must **fail safely** and direct the customer to support.

