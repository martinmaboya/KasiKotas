# KasiKotas Build & Deployment Action Steps (May 23, 2026)

## Status: Jackson Snapshot Fix Applied ✅
- [x] Fixed `pom.xml` with Jackson 2.18.0 override in `dependencyManagement`
- [x] Committed to `main` branch
- [x] Pushed to GitHub
- [ ] **NEXT: Trigger rebuild on Render**

---

## Step 1: Trigger Rebuild on Render

### Option A: Via Render Dashboard (Recommended)
1. Go to https://dashboard.render.com → Select your KasiKotas service
2. Click **Manual Deploy** → **Deploy latest commit**
3. Watch logs for completion (should take ~5-10 minutes)
4. Expected logs to see:
   ```
   BUILD SUCCESS (Maven)
   Docker image pushed
   Starting KasiKotasApplication
   ```

### Option B: Via Git Push (if you have Render webhook set up)
- The rebuild automatically triggers when you push to `main` (if auto-deploy is enabled)
- Check Render dashboard to confirm deploy is in progress

---

## Step 2: Verify Build Success (Monitor Logs)

Watch for these success indicators in Render logs:
- ✅ "Downloading from central: ... jackson-databind-2.18.0.jar"
- ✅ "BUILD SUCCESS"
- ✅ "Pushing image..."
- ✅ "Started KasiKotasApplication in ... seconds"
- ✅ No errors about `BankDetailsEncryption` or `jackson-databind`

If build fails, error logs will show the issue; let me know and I'll provide further fixes.

---

## Step 3: Run Database Migrations (Populate Checksums & Snapshots)

After build succeeds and service is running, run these SQL commands to populate missing checksums and EFT snapshot fields for existing orders.

### PowerShell Commands to Run (Copy-Paste)

```powershell
# Set database password environment variable
$env:PGPASSWORD = 'rZUcjAFMOwF4ZXAPDrhsobFvAqCdSm5E'

# Optional: Backup database first (recommended)
pg_dump -h dpg-d1p1cl3uibrs73d8s5e0-a.oregon-postgres.render.com `
  -U kasikotas_db_si2n_user `
  -d kasikotas_db_si2n `
  -Fc `
  -f .\kasikotas_backup_$(Get-Date -Format 'yyyyMMdd_HHmmss').dump

Write-Host "Backup complete. Running migrations..."

# Run the migration script that populates checksums and snapshots
$migrationSQL = @"
-- Enable pgcrypto for SHA-256 digest
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Ensure is_archived has default and fill NULLs
ALTER TABLE bank_details ALTER COLUMN is_archived SET DEFAULT false;
UPDATE bank_details SET is_archived = false WHERE is_archived IS NULL;

-- Populate missing checksums using SHA-256 (hex format, matches Java implementation)
UPDATE bank_details
SET account_number_checksum = encode(digest(convert_to(account_number, 'UTF8'), 'sha256'), 'hex'),
    last_verified_at = COALESCE(last_verified_at, now())
WHERE account_number IS NOT NULL
  AND (account_number_checksum IS NULL OR account_number_checksum = '');

UPDATE bank_details
SET account_name_checksum = encode(digest(convert_to(account_name, 'UTF8'), 'sha256'), 'hex'),
    last_verified_at = COALESCE(last_verified_at, now())
WHERE account_name IS NOT NULL
  AND (account_name_checksum IS NULL OR account_name_checksum = '');

UPDATE bank_details
SET bank_name_checksum = encode(digest(convert_to(bank_name, 'UTF8'), 'sha256'), 'hex'),
    last_verified_at = COALESCE(last_verified_at, now())
WHERE bank_name IS NOT NULL
  AND (bank_name_checksum IS NULL OR bank_name_checksum = '');

-- Populate missing EFT snapshot fields in orders from bank_details
UPDATE orders o
SET eft_bank_name = bd.bank_name,
    eft_account_name = bd.account_name,
    eft_account_number = bd.account_number,
    eft_shap_id = bd.shap_id,
    eft_branch_code = bd.branch_code
FROM bank_details bd
WHERE o.eft_bank_details_id = bd.id
  AND (o.eft_bank_name IS NULL OR o.eft_account_number IS NULL);

-- Add index for non-archived bank_details lookups
CREATE INDEX IF NOT EXISTS idx_bank_details_is_archived ON bank_details (is_archived);

-- Summary queries
SELECT count(*) as total_bank_details FROM bank_details;
SELECT count(*) as bank_details_with_checksums FROM bank_details WHERE account_number_checksum IS NOT NULL;
SELECT count(*) as orders_with_eft_snapshot FROM orders WHERE payment_method ILIKE 'eft' AND eft_account_number IS NOT NULL;
"@

# Save migration to file and run it
$migrationSQL | Out-File -FilePath .\populate_checksums_and_snapshots.sql -Encoding UTF8

psql -h dpg-d1p1cl3uibrs73d8s5e0-a.oregon-postgres.render.com `
  -U kasikotas_db_si2n_user `
  -d kasikotas_db_si2n `
  -f .\populate_checksums_and_snapshots.sql

Write-Host "Migrations complete!"

# Clean up password environment variable
Remove-Item Env:\PGPASSWORD

# Verify results
Write-Host "Checking results..."
$env:PGPASSWORD = 'rZUcjAFMOwF4ZXAPDrhsobFvAqCdSm5E'

psql -h dpg-d1p1cl3uibrs73d8s5e0-a.oregon-postgres.render.com `
  -U kasikotas_db_si2n_user `
  -d kasikotas_db_si2n `
  -c "SELECT id, bank_name, account_name, account_number_checksum, is_archived FROM bank_details LIMIT 5;"

psql -h dpg-d1p1cl3uibrs73d8s5e0-a.oregon-postgres.render.com `
  -U kasikotas_db_si2n_user `
  -d kasikotas_db_si2n `
  -c "SELECT id, payment_method, eft_bank_name, eft_account_number FROM orders WHERE lower(payment_method) = 'eft' LIMIT 10;"

Remove-Item Env:\PGPASSWORD
```

### Manual Alternative (If PowerShell Script Issues)

Save this as `populate_checksums_and_snapshots.sql`:

```sql
-- Enable pgcrypto for SHA-256 digest
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Ensure is_archived has default and fill NULLs
ALTER TABLE bank_details ALTER COLUMN is_archived SET DEFAULT false;
UPDATE bank_details SET is_archived = false WHERE is_archived IS NULL;

-- Populate missing checksums using SHA-256 (hex format)
UPDATE bank_details
SET account_number_checksum = encode(digest(convert_to(account_number, 'UTF8'), 'sha256'), 'hex'),
    last_verified_at = COALESCE(last_verified_at, now())
WHERE account_number IS NOT NULL
  AND (account_number_checksum IS NULL OR account_number_checksum = '');

UPDATE bank_details
SET account_name_checksum = encode(digest(convert_to(account_name, 'UTF8'), 'sha256'), 'hex'),
    last_verified_at = COALESCE(last_verified_at, now())
WHERE account_name IS NOT NULL
  AND (account_name_checksum IS NULL OR account_name_checksum = '');

UPDATE bank_details
SET bank_name_checksum = encode(digest(convert_to(bank_name, 'UTF8'), 'sha256'), 'hex'),
    last_verified_at = COALESCE(last_verified_at, now())
WHERE bank_name IS NOT NULL
  AND (bank_name_checksum IS NULL OR bank_name_checksum = '');

-- Populate missing EFT snapshot fields
UPDATE orders o
SET eft_bank_name = bd.bank_name,
    eft_account_name = bd.account_name,
    eft_account_number = bd.account_number,
    eft_shap_id = bd.shap_id,
    eft_branch_code = bd.branch_code
FROM bank_details bd
WHERE o.eft_bank_details_id = bd.id
  AND (o.eft_bank_name IS NULL OR o.eft_account_number IS NULL);

CREATE INDEX IF NOT EXISTS idx_bank_details_is_archived ON bank_details (is_archived);
```

Then run:
```powershell
$env:PGPASSWORD = 'rZUcjAFMOwF4ZXAPDrhsobFvAqCdSm5E'
psql -h dpg-d1p1cl3uibrs73d8s5e0-a.oregon-postgres.render.com -U kasikotas_db_si2n_user -d kasikotas_db_si2n -f .\populate_checksums_and_snapshots.sql
Remove-Item Env:\PGPASSWORD
```

---

## Step 4: Verify Everything Works

### Test 1: Place a New EFT Order
1. Go to your frontend app
2. Add items to cart
3. Checkout → Select **EFT** payment method
4. Place order
5. **Expected result**: Order confirmation page shows:
   - Bank name: Capitec bank
   - Account name: Martin Maboya
   - Account number: 1539228116
   - ShapID: 0677439994@capitec
   - Branch code: 470010

### Test 2: Check Order API Response
```bash
# Replace 999 with a real order ID from your last test order
curl -X GET "https://your-kasikotas-render-domain.com/api/orders/999" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"
```

Response should include:
```json
{
  "id": 999,
  "paymentMethod": "EFT",
  "eftBankName": "Capitec bank",
  "eftAccountName": "Martin Maboya",
  "eftAccountNumber": "1539228116",
  "eftShapId": "0677439994@capitec",
  "eftBranchCode": "470010"
}
```

### Test 3: Verify Checksums in Database
```powershell
$env:PGPASSWORD = 'rZUcjAFMOwF4ZXAPDrhsobFvAqCdSm5E'
psql -h dpg-d1p1cl3uibrs73d8s5e0-a.oregon-postgres.render.com -U kasikotas_db_si2n_user -d kasikotas_db_si2n -c "SELECT id, bank_name, account_number_checksum, bank_name_checksum FROM bank_details WHERE id = 2;"
Remove-Item Env:\PGPASSWORD
```

Expected output: checksums should be 64-character hex strings (SHA-256)

---

## Step 5: Frontend Implementation (See Next Section)

See `FRONTEND_EFT_IMPLEMENTATION_CODE.md` for exact React/Vue/Angular code to display EFT details and handle errors.

---

## Troubleshooting

### Build Still Fails on Render
1. Check Render logs for specific error
2. Run locally: `mvn clean package -DskipTests`
3. If local build fails, let me know the error

### Migration Errors
- If SQL migration fails, check that columns exist: `SELECT column_name FROM information_schema.columns WHERE table_name='bank_details';`
- If checksums don't populate, ensure pgcrypto extension is available

### EFT Details Still Missing in Orders
- Verify `bank_details` has at least one non-archived row with checksums
- Check Render app logs for `SecurityException` or checksum failures
- If app throws "checksum verification FAILED", re-save bank details via admin UI to regenerate checksums using the app code

---

## Next: Frontend Implementation

Once tests pass, implement the exact frontend code in the next document.

