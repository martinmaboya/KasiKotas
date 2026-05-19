# Site Security Next Steps for EFT Bank Details

## What must happen on the site now

### 1) Treat bank details as admin-managed configuration only
- Only trusted admin users should be able to create, edit, or delete bank details.
- Never let the public site edit bank details.
- Keep every change in an audit trail.

### 2) Freeze EFT bank details at order creation time
- When a customer places an EFT order, save the bank details snapshot onto that order.
- Do not rely on the live bank-details table for old orders.
- Past orders must always show the exact bank details that were used when the order was created.

### 3) Protect order data from tampering
- Use optimistic locking / version checks on order updates.
- Block order processing if critical data integrity checks fail.
- Log suspicious changes to bank details immediately.

### 4) Show safe fallback behavior
- If an old order does not have a saved EFT snapshot, do not guess a replacement account.
- Show a support message instead.

---

## What the frontend must do

### Checkout screen
- Send the order normally.
- After EFT order creation, display the bank details returned in the order response.
- Do not cache a single EFT account in local storage or app state.
- Do not hardcode bank details in the UI.

### Order history and order details
- Always render the bank details snapshot stored on the order.
- Never swap in the current live bank details for an older order.
- If the snapshot is missing, show a safe support message.

### Admin bank-details screen
- Keep the create/edit form.
- Refresh the list after updates.
- Show audit/history records so admins can see who changed bank details and when.
- Handle validation and conflict errors cleanly.

### Frontend behavior to remove
- Do not treat `GET /api/bank-details/eft` as the source of truth for past orders.
- Do not reload live bank details when viewing an existing order.
- Do not store one EFT account globally for all customers.

---

## Recommended user messages

### If everything is normal
- `Payment details saved with your order.`
- `Please use the bank details shown on your order confirmation.`

### If an old order has no snapshot
- `EFT payment details are unavailable for this old order. Please contact support.`

### If the backend detects a conflict
- `Your data changed on the server. Please refresh and try again.`

---

## Short checklist

- [ ] Bank details editable only by admins
- [ ] EFT order stores snapshot data
- [ ] Order history uses snapshot data only
- [ ] Frontend stops caching a single EFT account
- [ ] Admin audit/history page is visible
- [ ] Safe fallback shown for old orders without snapshots
- [ ] Conflict and integrity failures are handled clearly

---

## Bottom line

The site must trust the **saved order snapshot**, not the live bank-details table.
The frontend must display whatever the order response gives it and must never replace old EFT details with current bank settings.

