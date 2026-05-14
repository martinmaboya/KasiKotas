# Frontend EFT Snapshot and Bank Audit Checklist

## Scope
This document explains what the frontend must do now that EFT bank details are:

- **snapshot-backed on each order**
- **no longer read from live bank details for past orders**
- **audited on the admin side**

---

## 1) Checkout flow for EFT

When the customer selects `EFT`:

- send the order normally to `POST /api/orders`
- do **not** cache a single EFT account in local storage or global app state
- do **not** hardcode bank details in the frontend
- do **not** call `GET /api/bank-details/eft` as the source of truth for the checkout result

### After order creation
If the response contains `order.eftBankDetails`, show:

- `bankName`
- `accountName`
- `accountNumber`
- `branchCode`
- `shapId`

Use these values as the payment instructions shown to the customer.

---

## 2) Order history and order details

For every EFT order shown later:

- always render `order.eftBankDetails`
- never replace it with the current live `bank_details` record
- never assume the current admin bank config is correct for old orders

### Why
The backend now stores the assigned EFT account as a snapshot on the order, so past orders must keep the original instructions.

### Legacy fallback
If an old order has no EFT snapshot data:

- show a safe fallback message like:
  - `EFT payment details are unavailable for this old order. Please contact support.`
- do **not** guess or fetch a different live account

---

## 3) Admin bank-details screen

Admin users can still create and update bank details normally.

Frontend should:

- keep the current create/edit form
- continue to call `POST /api/bank-details`
- refresh the current list with `GET /api/bank-details/all`
- handle validation and conflict errors cleanly

### New audit/history view
The backend now exposes audit/history data, so the admin frontend can add a history panel or page that shows:

- action: `CREATE`, `UPDATE`, `DELETE`
- actor username
- change timestamp
- before snapshot
- after snapshot

This is useful for spotting accidental edits or tampering.

---

## 4) What the frontend must stop doing

Remove any logic that:

- stores one EFT bank account for all orders
- treats `GET /api/bank-details/eft` as the source of truth for past orders
- reloads live bank details when rendering an already-created EFT order
- assumes bank details never change

---

## 5) Recommended frontend types

### `Order`
```ts
export type Order = {
  id: number;
  paymentMethod: string;
  totalAmount: number;
  // existing fields...
  eftBankDetails?: BankDetails | null;
};
```

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

## 6) Relevant API endpoints

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

---

## 7) Short implementation checklist

- [ ] Checkout displays `order.eftBankDetails` after EFT order creation
- [ ] Order history/detail pages read the saved order snapshot
- [ ] Admin edit form still saves bank details normally
- [ ] Admin audit/history page shows change records
- [ ] Remove any static EFT account caching from the frontend
- [ ] Add a safe fallback for older EFT orders without snapshot data

---

## Notes

- The frontend should trust the **order snapshot**, not the live bank-details table.
- The audit endpoint is for admin visibility only.
- If backend returns a conflict due to optimistic locking, ask the user to refresh and retry.

