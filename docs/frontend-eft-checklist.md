```markdown
# Frontend integration checklist — EFT bank details (complete)

This file explains exactly what the frontend must do after the backend changes to treat EFT bank details as admin-managed configuration and to ensure customers always see the correct payment details.

High level
- The backend now snapshots EFT bank details onto each order at creation time. The frontend must render the EFT details returned by the order response and never substitute live bank-details for past orders.
- If an order doesn't have a saved snapshot the backend will return either null fields or a clear error (see examples below). The frontend must show a safe support message in that case.

Checklist for the frontend (exact, actionable items)

1) Checkout flow
   - When the user selects EFT and submits the order, wait for the order creation response from the server.
   - The response body for an EFT order must include the EFT snapshot fields (example below). Immediately present those bank details to the user and do not make a separate call to a global `GET /api/bank-details/eft` to populate the checkout screen for this order.
   - Persist the order response in local state (memory) only for the immediate UI. Do NOT cache a single company bank account globally for the whole app; always rely on the order response for that specific order.

2) Order confirmation screen
   - Render the EFT snapshot inside the order confirmation using values from the order resource: `eft_bank_name`, `eft_account_name`, `eft_account_number`, `eft_shap_id`, `eft_branch_code`, and `eft_bank_details_id`.
   - If `eft_account_number` (or the other EFT snapshot fields) is NULL, show the safe fallback message: "EFT payment details are unavailable for this order. Please contact support and include order #<id>."

3) Order history / details
   - Always display the bank details snapshot stored on that order record.
   - Do NOT call the live bank-details endpoint and swap in new values for historical orders.

4) Admin flows in the frontend
   - Keep the create/edit UI for bank details. Only admins should see these screens.
   - Show an audit/history of changes so admins can see when and who changed bank details.
   - If admins change the active bank details, the change affects only future orders — older orders keep their snapshot.

5) Messages and copy
   - Success after placing an EFT order: "Your order has been placed successfully. Please pay <amount> using the bank details shown on this page. Use order #<id> as the payment reference."
   - When snapshot is missing: "EFT payment details are unavailable for this order. Please contact support and include order #<id>."

6) Security and UX guardrails
   - Never allow non-admin endpoints to update the `bank_details` table.
   - Do not store bank account fields in client-side persistent storage (localStorage, IndexedDB) unless absolutely required by an admin-only interface.
   - Log and surface failures: if order creation returns an error (e.g., fallback because snapshot missing) show a clear UX and prevent users from thinking EFT details were saved.


API & Response contract (backend -> frontend)
- POST /api/orders        (checkout)
  - Request: order items + payment_method: "EFT"
  - Response (partial; only EFT related fields shown):

  {
    "orderId": 282,
    "status": "CREATED",
    "amount": 50.00,
    "eft_bank_details": {
      "id": 2,
      "bank_name": "Capitec bank",
      "account_name": "Martin Maboya",
      "account_number": "1539228116",
      "shap_id": "0677439994@capitec",
      "branch_code": "470010"
    }
  }

  - If the backend cannot provide EFT snapshot for this order it returns `eft_bank_details: null` (or the fields as null) and a message string. The frontend should treat that as a soft failure and show the support message.


SQL — safe step-by-step commands for Postgres to diagnose and optionally backfill missing snapshots

IMPORTANT: Always run the `SELECT` preview queries first. Do NOT run `UPDATE` until you have a working DB snapshot and you understand which orders you will affect.

1) See what bank details exist and which are active

-- list bank details (inspect is_archived and last_verified_at)
SELECT id, bank_name, account_name, account_number, shap_id, branch_code, is_archived, last_verified_at
FROM bank_details
ORDER BY is_archived NULLS FIRST, last_verified_at DESC NULLS LAST, id DESC;

2) Preview which orders are EFT and missing saved snapshot fields

-- preview orders that look like they need an EFT snapshot
SELECT o.id AS order_id, o.payment_method, o.eft_bank_name, o.eft_account_number, o.created_at
FROM orders o
WHERE o.payment_method = 'EFT'
  AND (o.eft_bank_name IS NULL OR o.eft_account_number IS NULL)
ORDER BY o.created_at DESC
LIMIT 200;

3) (Optional) Find the single active bank-details record we will copy from (most recently verified, not archived)

SELECT id, bank_name, account_name, account_number, shap_id, branch_code, last_verified_at
FROM bank_details
WHERE COALESCE(is_archived, false) = false
ORDER BY last_verified_at DESC NULLS LAST, id DESC
LIMIT 1;

4) Backfill orders using the active bank-details record (DRY RUN first)

-- DRY RUN: show rows that would be updated and the values used
WITH active_bd AS (
  SELECT id, bank_name, account_name, account_number, shap_id, branch_code
  FROM bank_details
  WHERE COALESCE(is_archived, false) = false
  ORDER BY last_verified_at DESC NULLS LAST, id DESC
  LIMIT 1
)
SELECT o.id AS order_id, o.payment_method, o.eft_bank_name, o.eft_account_number, bd.*
FROM orders o
CROSS JOIN active_bd bd
WHERE o.payment_method = 'EFT' AND (o.eft_bank_name IS NULL OR o.eft_account_number IS NULL)
ORDER BY o.created_at DESC
LIMIT 500;

-- If the DRY RUN looks good, run the update in a transaction (backup recommended)
BEGIN;
WITH active_bd AS (
  SELECT id, bank_name, account_name, account_number, shap_id, branch_code
  FROM bank_details
  WHERE COALESCE(is_archived, false) = false
  ORDER BY last_verified_at DESC NULLS LAST, id DESC
  LIMIT 1
)
UPDATE orders o
SET eft_bank_details_id = bd.id,
    eft_bank_name = bd.bank_name,
    eft_account_name = bd.account_name,
    eft_account_number = bd.account_number,
    eft_shap_id = bd.shap_id,
    eft_branch_code = bd.branch_code
FROM active_bd bd
WHERE o.payment_method = 'EFT'
  AND (o.eft_bank_name IS NULL OR o.eft_account_number IS NULL)
  -- AND o.created_at >= '2026-05-01' -- optional safety filter
;
COMMIT;

Notes on the SQL backfill
- The queries above copy the single currently active bank details to orders that are missing snapshot data. This is a pragmatic fix for recent orders placed while the server was misconfigured.
- Do NOT run the update blindly for *all* historical orders. If you must backfill older orders, filter by a date range and run of a smaller batch.
- Always take a DB dump or run the migration on a staging DB first.


Render environment (set and redeploy)

1) Generate a secure encryption key (32 random bytes) and encode in base64. Example (use on your laptop/host):

Python one-liner (recommended):
```powershell
python - <<'PY'
import os, base64
print(base64.b64encode(os.urandom(32)).decode())
PY
```

Or using openssl (Linux/macOS):
```bash
openssl rand -base64 32
```

The value must decode to exactly 32 bytes. Base64 length will typically be 44 characters (including trailing = padding).

2) In Render dashboard -> Your service -> Environment -> add a new environment variable:
   - Key: `BANK_ENCRYPTION_KEY`
   - Value: (the base64 string you generated)

3) Make sure your `application.properties` (or `application.yml`) references the env var like:

   app.security.bank-encryption-key=${BANK_ENCRYPTION_KEY}

   (or if your code expects a default-from-property syntax, ensure it pulls `BANK_ENCRYPTION_KEY` env var; do NOT keep an inline base64 default in the repository for security reasons)

4) Redeploy the Render service. The app will now start and the `BankDetailsEncryption` component should initialize without the 32-byte error.


Other operational tips
- If bank details in the `bank_details` table were added while encryption key was wrong, those rows may not be decryptable. If that happens, the safe flow is to create a new bank-details record with the admin UI (or SQL INSERT) while the app is running with the correct key, and then use the backfill SQL above if appropriate.
- Keep the key secret. Rotate it carefully: rotate on staging first; if you rotate the key you will need to re-encrypt existing encrypted values or re-enter them.


How to test quickly (manual)
1) In a staging environment, set the env var and redeploy.
2) As an admin, create a new bank-details entry (via admin UI or SQL). Confirm the row is stored and the app can read it.
3) Place a new order with payment_method=EFT from the frontend. Confirm the order response contains the bank details and the checkout confirmation shows them.
4) Place a historical-order view: open an older order that originally had missing snapshot fields — confirm either it shows saved EFT snapshot or the fallback message.


If you want, I can create a small checklist PR to add these SQL snippets as a safe migration file and commit them. Tell me to proceed and I will create a migration file under `database_migration_*` and commit the changes.

---

End of frontend checklist

```

