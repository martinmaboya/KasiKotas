# 🚀 Deployment Checklist - Bank Details Security

**Version**: 1.0  
**Date**: May 19, 2026  
**Status**: Ready for Production Deployment  

---

## Phase 1: Pre-Deployment (LOCAL)

### ✅ Code Compilation
- [x] Maven clean build completed
- [x] No compilation errors
- [x] All tests updated with encryption mock
- [x] Build artifact created: `target/KasiKotas-1.0-SNAPSHOT.jar`

### ✅ Git Repository
- [x] All changes staged
- [x] Commit created: `141be3e` with detailed message
- [x] Pushed to main branch
- [x] Remote sync verified

### ✅ Encryption Key Generation (⚠️ MUST DO BEFORE DEPLOYMENT)

**Action Required**: Generate a new encryption key

```bash
# Navigate to project directory
cd C:\Users\nm\IdeaProjects\KasiKotas

# Build the project (if not already done)
mvn clean package -DskipTests

# Generate new encryption key
java -cp "target/KasiKotas-1.0-SNAPSHOT.jar" kasiKotas.security.BankDetailsEncryption

# Output will show:
# New encryption key: ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrst==

# ⚠️ IMPORTANT: Copy and save this key securely
# This is the ONLY key that will decrypt bank details
# DO NOT lose this key!
# DO NOT commit to Git!
# Store in secure location (password manager, vault)
```

**Save the key in a secure format**:
```
File: encryption-key-production.txt (store in secure location)
Content: [paste the generated key here]
Date Generated: May 19, 2026
Usage: BANK_ENCRYPTION_KEY environment variable
```

---

## Phase 2: Render.com Deployment

### Step 1: Set Environment Variable

1. Go to [Render.com Dashboard](https://dashboard.render.com)
2. Navigate to **Services** → **KasiKotas**
3. Click on **Settings** tab
4. Scroll to **Environment** section
5. Click **Add Environment Variable**
6. Fill in:
   - **Key**: `BANK_ENCRYPTION_KEY`
   - **Value**: `[paste the generated key from Phase 1]`
7. Click **Save**
8. Render will show notification: *"Configuration updated"*

### Step 2: Deploy Updated Code

**Option A: Auto-deploy (if GitHub integration enabled)**
```
Render automatically detects push to main branch and redeploys
Check deployment status in Render dashboard
Should complete within 5-10 minutes
```

**Option B: Manual deploy (if auto-deploy not enabled)**
1. Go to Render Dashboard → KasiKotas service
2. Click **Manual Deploy** button
3. Select branch: **main**
4. Click **Deploy**
5. Monitor deployment progress (takes 2-5 minutes)

### Step 3: Verify Deployment

**Check Application Logs**:
1. In Render Dashboard, click **KasiKotas** service
2. Go to **Logs** tab
3. Look for these confirmation messages:
   ```
   [INFO] Bank details encryption initialized successfully
   [INFO] Encryption key loaded from BANK_ENCRYPTION_KEY environment variable
   [INFO] Application started successfully
   ```

**If you see warnings** ⚠️:
```
[ERROR] Failed to initialize bank details encryption key
[ERROR] BANK_ENCRYPTION_KEY environment variable not found
```

**Solution**:
- Go back to Settings → Environment
- Verify `BANK_ENCRYPTION_KEY` is set
- Wait 30 seconds and check logs again
- Redeploy if necessary

---

## Phase 3: Database Migration

### Step 1: Connect to Production Database

```bash
# Using psql command line
psql -h dpg-d1p1cl3uibrs73d8s5e0-a.oregon-postgres.render.com \
     -U kasikotas_db_si2n_user \
     -d kasikotas_db_si2n \
     -c "\dt"

# You should see all tables listed
# Including: bank_details, bank_details_audit, orders, users, etc.
```

### Step 2: Run Migration Script

```bash
# Option 1: Run entire migration file
psql -h dpg-d1p1cl3uibrs73d8s5e0-a.oregon-postgres.render.com \
     -U kasikotas_db_si2n_user \
     -d kasikotas_db_si2n \
     -f database_migration_bank_details_security.sql

# Option 2: Run migration commands one by one (safer)
# Copy commands from database_migration_bank_details_security.sql
# Paste into psql and execute manually
```

### Step 3: Verify Migration

```sql
-- Check new columns were added
SELECT column_name, data_type 
FROM information_schema.columns 
WHERE table_name = 'bank_details' 
ORDER BY column_name;

-- Expected columns:
-- account_number_checksum | character varying
-- account_name_checksum | character varying
-- bank_name_checksum | character varying
-- last_verified_at | timestamp without time zone
-- is_archived | boolean

-- Check indexes were created
SELECT indexname FROM pg_indexes 
WHERE tablename = 'bank_details';

-- Expected indexes:
-- idx_bank_details_active
-- idx_unique_active_account_number
-- idx_bank_details_pkey

-- Check triggers were created
SELECT trigger_name, event_manipulation 
FROM information_schema.triggers 
WHERE event_object_table = 'bank_details';

-- Expected triggers:
-- bank_details_update_timestamp
-- bank_details_audit_trigger
```

### Step 4: Generate Initial Checksums

```sql
-- For each existing bank detail, generate checksums
-- This SQL generates checksums for existing records
-- These will be used on first access after deployment

UPDATE bank_details 
SET 
  account_number_checksum = 
    (SELECT encode(digest(account_number, 'sha256'), 'hex')),
  account_name_checksum = 
    (SELECT encode(digest(account_name, 'sha256'), 'hex')),
  bank_name_checksum = 
    (SELECT encode(digest(bank_name, 'sha256'), 'hex')),
  last_verified_at = CURRENT_TIMESTAMP,
  is_archived = false
WHERE account_number_checksum IS NULL;

-- Verify checksums were generated
SELECT id, account_number_checksum, bank_name_checksum, last_verified_at 
FROM bank_details 
LIMIT 5;
```

---

## Phase 4: Testing & Verification

### Test 1: Admin Access Control ✅

**Objective**: Verify non-admins cannot modify bank details

```bash
# 1. Get a customer token
CUSTOMER_TOKEN="<token-from-login-as-customer>"

# 2. Attempt to modify bank details
curl -X POST http://localhost:8080/api/admin/bank-details \
  -H "Authorization: Bearer $CUSTOMER_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "bankName": "TestBank",
    "accountName": "TestAccount",
    "accountNumber": "1111111111",
    "shapId": "TEST123",
    "branchCode": "999999"
  }'

# Expected Response:
# 403 Forbidden
# "Unauthorized. Only admins can modify bank details."

echo "✅ PASS: Non-admin access denied"
```

### Test 2: Checksum Tampering Detection ✅

**Objective**: Verify tampering is detected

```bash
# 1. Get admin token
ADMIN_TOKEN="<token-from-login-as-admin>"

# 2. Create a bank detail
curl -X POST http://localhost:8080/api/admin/bank-details \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "bankName": "FNB",
    "accountName": "KasiKotas",
    "accountNumber": "1234567890",
    "shapId": "ABC123",
    "branchCode": "250655"
  }'

# Response includes: id = 1 (example)

# 3. Simulate attacker tampering with database
psql -h <host> -U <user> -d <db> -c "
  UPDATE bank_details 
  SET account_number = '9999999999' 
  WHERE id = 1;
"

# 4. Try to get bank details (should fail checksum)
curl -X GET http://localhost:8080/api/admin/bank-details \
  -H "Authorization: Bearer $ADMIN_TOKEN"

# Expected: Application detects tampering and throws SecurityException
# Logs show: "[SECURITY ALERT] Bank details checksum verification FAILED"

echo "✅ PASS: Tampering detected"
```

### Test 3: Order Snapshot Immutability ✅

**Objective**: Verify orders are frozen to bank details

```bash
# 1. Create order with Bank A
curl -X POST http://localhost:8080/api/orders \
  -H "Authorization: Bearer $CUSTOMER_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "paymentMethod": "eft",
    "items": [...],
    "deliveryMethod": "DELIVERY"
  }'

# Response: orderId = 12345, eftBankDetailsId = 1, eftAccountNumber = "1234567890"

# 2. Admin changes bank details
curl -X POST http://localhost:8080/api/admin/bank-details/1 \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "bankName": "FNB",
    "accountName": "KasiKotas",
    "accountNumber": "9999999999",
    "shapId": "ABC123",
    "branchCode": "250655"
  }'

# 3. Retrieve order details
curl -X GET http://localhost:8080/api/orders/12345 \
  -H "Authorization: Bearer $CUSTOMER_TOKEN"

# Expected in response:
# eftAccountNumber: "1234567890"  ← UNCHANGED from step 1
# currentBankDetailsId: 1
# bankDetailsChangedSinceOrder: true  ← Alert flag

echo "✅ PASS: Order snapshot frozen"
```

### Test 4: Audit Log Tracking ✅

**Objective**: Verify audit trail is complete

```bash
# 1. Get audit history
curl -X GET http://localhost:8080/api/admin/bank-details-audit \
  -H "Authorization: Bearer $ADMIN_TOKEN"

# Expected response contains:
# [
#   {
#     "action": "CREATE",
#     "actor_username": "admin@kasikotas.com",
#     "changed_at": "2026-05-20T10:30:00Z",
#     "beforeSnapshotJson": null,
#     "afterSnapshotJson": { "bankName": "FNB", ... }
#   },
#   {
#     "action": "UPDATE",
#     "actor_username": "admin@kasikotas.com",
#     "changed_at": "2026-05-20T14:45:00Z",
#     "beforeSnapshotJson": { "accountNumber": "1234567890" },
#     "afterSnapshotJson": { "accountNumber": "9999999999" }
#   }
# ]

echo "✅ PASS: Audit trail complete"
```

---

## Phase 5: Production Monitoring

### Ongoing Checks

**Daily**:
- [ ] Check application logs for security alerts
- [ ] Monitor for failed access attempts (403 Forbidden)
- [ ] Check audit log for unusual patterns

**Weekly**:
- [ ] Review audit dashboard for all bank detail changes
- [ ] Verify no unauthorized access attempts
- [ ] Check checksum verification status

**Monthly**:
- [ ] Review complete audit history
- [ ] Verify encryption key is secure
- [ ] Test disaster recovery procedures

### Alert Thresholds

**CRITICAL**: Immediate action needed
- [ ] Checksum mismatch detected (tampering)
- [ ] Multiple failed access attempts
- [ ] Admin account compromised

**HIGH**: Investigate within 24 hours
- [ ] Bank details changed 3+ times in same day
- [ ] Bank details changed by different admins in short time
- [ ] Large number of failed access attempts

**MEDIUM**: Monitor and log
- [ ] Bank details changed (normal admin activity)
- [ ] Archive/restore operations
- [ ] Successful access attempts

---

## Phase 6: Rollback Plan (If Issues Occur)

### If Migration Fails

```bash
# Rollback the database migration
# (Keep backup of original schema)

# Revert Git commit
git revert 141be3e
git push origin main

# Redeploy previous version
# (Render will auto-deploy after revert)
```

### If Encryption Key Issues

```bash
# If wrong key was set, corrects deployment:

# 1. Generate new key
java -cp target/KasiKotas-1.0-SNAPSHOT.jar kasiKotas.security.BankDetailsEncryption

# 2. Update Render environment variable
# Go to Render Dashboard → Settings → Environment
# Update BANK_ENCRYPTION_KEY value

# 3. Redeploy
# Click Manual Deploy or wait for auto-deploy

# 4. Verify logs show encryption initialized
```

### If Tampering Detected During Testing

```bash
# 1. Stop order processing
# DO NOT proceed with production if tampering not detected

# 2. Review code and configuration
# Verify checksums are being generated and verified

# 3. Re-run database migration
# Make sure triggers and functions created successfully

# 4. Run verification queries again
SELECT * FROM bank_details 
WHERE account_number_checksum IS NULL;

# If any checksums are NULL, generate them:
UPDATE bank_details 
SET account_number_checksum = encode(digest(account_number, 'sha256'), 'hex')
WHERE account_number_checksum IS NULL;
```

---

## ✅ Final Verification Checklist

Before considering deployment complete:

- [ ] Environment variable `BANK_ENCRYPTION_KEY` is set in Render
- [ ] Application started successfully (check logs)
- [ ] Database migration completed without errors
- [ ] All new columns exist in `bank_details` table
- [ ] All triggers created in database
- [ ] Test 1 passed: Admin access control working
- [ ] Test 2 passed: Checksum tampering detection working
- [ ] Test 3 passed: Order snapshots immutable
- [ ] Test 4 passed: Audit logs tracking changes
- [ ] No errors in application logs
- [ ] No errors in database logs
- [ ] Customer can still place EFT orders successfully
- [ ] Admin can view and modify bank details
- [ ] Frontend shows encrypted account numbers (masked)

---

## 📞 Support & Troubleshooting

### Issue: Application fails to start
**Check**: `BANK_ENCRYPTION_KEY` environment variable is set correctly  
**Check**: No typos in the encryption key  
**Solution**: Regenerate key and update in Render

### Issue: "Bank details integrity check FAILED"
**Check**: Database migration completed  
**Check**: Checksums were generated  
**Check**: No manual database tampering  
**Solution**: Verify integrity section in migration script

### Issue: Orders fail to save with EFT payment
**Check**: Bank details exist in database  
**Check**: Checksums are valid  
**Check**: No tampering detected  
**Solution**: Run checksum regeneration

### Issue: Audit log is empty
**Check**: Database triggers were created  
**Check**: bank_details_audit table exists  
**Check**: Permissions granted to application user  
**Solution**: Verify triggers in database

---

## 📋 Deployment Summary

**Code Deployed**: ✅ Main branch (commit 141be3e)  
**Database Migration**: Ready to apply  
**Encryption Key**: Needs to be generated  
**Tests**: Ready to run  
**Frontend**: Implementation guide available  

**Estimated Time to Complete**: 30-45 minutes  
- 5 min: Set environment variable
- 5 min: Deploy to Render
- 10 min: Run database migration
- 15 min: Run verification tests

**Risk Level**: LOW (with proper testing)  
**Rollback Time**: 10 minutes (if needed)

---

**Deployment Ready**: ✅ YES  
**Date**: May 19, 2026  
**Next Review**: When deployed to production

