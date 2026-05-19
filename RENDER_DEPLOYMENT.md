# Render.com Deployment Guide

## Database Setup ✅

Your PostgreSQL database is already configured:

- **Database Name**: `kasikotas_db_si2n`
- **Username**: `kasikotas_db_si2n_user`
- **Password**: `rZUcjAFMOwF4ZXAPDrhsobFvAqCdSm5E`
- **Internal URL**: `postgresql://kasikotas_db_si2n_user:rZUcjAFMOwF4ZXAPDrhsobFvAqCdSm5E@dpg-d1p1cl3uibrs73d8s5e0-a/kasikotas_db_si2n`
- **External URL**: `postgresql://kasikotas_db_si2n_user:rZUcjAFMOwF4ZXAPDrhsobFvAqCdSm5E@dpg-d1p1cl3uibrs73d8s5e0-a.oregon-postgres.render.com/kasikotas_db_si2n`

---

## Environment Variables to Set in Render

Go to your Render service dashboard → **Environment** tab and add these:

### Required Environment Variables:

```bash
# Database (Render auto-populates this from connected PostgreSQL)
DATABASE_URL=postgresql://kasikotas_db_si2n_user:rZUcjAFMOwF4ZXAPDrhsobFvAqCdSm5E@dpg-d1p1cl3uibrs73d8s5e0-a/kasikotas_db_si2n

# JWT Secret (for authentication)
JWT_SECRET=r1QycRsGXwPCyZxDTw9vIiCIV8ADcvQRxPXe/pe7cPcJv0oHH8iOVTHA9WjldrzKDu2x1OaCMjhQYbzdtlqsQQ==

# Email Configuration (Yahoo SMTP)
MAIL_USERNAME=m.maboya@yahoo.com
MAIL_PASSWORD=xicrwhfsdvoltvrv

# Admin Email
ADMIN_EMAIL=m.maboya@yahoo.com

# Bank Details Encryption (required for EFT bank-details protection)
# Use a Base64-encoded 32-byte key in production
BANK_ENCRYPTION_KEY=<your-generated-base64-256-bit-key>
```

### Generate a key locally

```powershell
$bytes = New-Object byte[] 32; [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes); [Convert]::ToBase64String($bytes)
```

Copy the output and paste it into Render as `BANK_ENCRYPTION_KEY`.

---

## Deployment Steps

### 1. Connect PostgreSQL Database

In Render dashboard:
1. Go to your web service
2. Click **Environment** tab
3. Scroll to **Environment Variables**
4. Render should auto-populate `DATABASE_URL` when you connect the PostgreSQL database
5. If not, manually add it using the **Internal URL** above

### 2. Set Build Command

```bash
mvn clean package -DskipTests
```

### 3. Set Start Command

```bash
java -Dserver.port=$PORT -jar target/KasiKotas-1.0-SNAPSHOT.jar
```

### 4. Database Migration

After first successful deployment, run these SQL commands using Render's PostgreSQL shell:

```sql
-- The tables will be auto-created by Hibernate, but you may need to run migrations
-- Connect to your database in Render dashboard → PostgreSQL → Shell

-- Verify tables were created
\dt

-- If you need to run specific migrations:
-- 1. Copy contents from database_migration_add_otp_column.sql
-- 2. Paste into Render's PostgreSQL shell
-- 3. Execute
```

---

## Troubleshooting

### Connection Refused Error

**Problem**: `Communications link failure` or `Connection refused`

**Solution**: 
- Ensure `DATABASE_URL` environment variable is set
- Use the **Internal URL** (without .oregon-postgres.render.com) for faster connection
- Verify PostgreSQL database is running in Render dashboard

### Port Already in Use

**Problem**: `Port 8080 already in use`

**Solution**:
- Render provides `$PORT` environment variable
- Make sure start command uses: `java -Dserver.port=$PORT -jar ...`

### OTP Email Not Sending

**Problem**: Emails not being sent from Yahoo

**Solution**:
- Verify `MAIL_USERNAME` and `MAIL_PASSWORD` are set in environment variables
- Ensure app password is correct (not regular password)
- Check Render logs for email errors

### Database Tables Not Created

**Problem**: Tables missing in PostgreSQL

**Solution**:
- `spring.jpa.hibernate.ddl-auto=update` will auto-create tables
- Check deployment logs for Hibernate SQL statements
- Manually run migrations if needed

---

## Verify Deployment

### 1. Check Health
```bash
curl https://your-app.onrender.com/
```

### 2. Test API Endpoints

```bash
# Get products
curl https://your-app.onrender.com/api/products/get-all

# Test password reset
curl -X POST https://your-app.onrender.com/api/auth/forgot-password \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com"}'
```

### 3. Check Logs

In Render dashboard:
- Go to your service
- Click **Logs** tab
- Look for:
  - `Started KasiKotasApplication` - App started successfully
  - `HikariPool-1 - Start completed` - Database connected
  - `OTP sent to {email}` - Email service working

---

## Database Differences: MySQL vs PostgreSQL

Your app now supports BOTH databases:
- **Local Development**: MySQL (localhost:3306)
- **Production (Render)**: PostgreSQL

### Key Differences Handled:

1. **Auto-increment**: 
   - MySQL: `AUTO_INCREMENT`
   - PostgreSQL: `SERIAL` or `IDENTITY`
   - ✅ Hibernate handles this automatically

2. **Boolean Type**:
   - MySQL: `TINYINT(1)`
   - PostgreSQL: `BOOLEAN`
   - ✅ Hibernate handles this automatically

3. **Datetime**:
   - MySQL: `DATETIME`
   - PostgreSQL: `TIMESTAMP`
   - ✅ Hibernate handles this automatically

4. **String Comparison**:
   - MySQL: Case-insensitive by default
   - PostgreSQL: Case-sensitive by default
   - ⚠️ All queries using `LIKE` should use `ILIKE` in PostgreSQL
   - ✅ Your app uses JPA methods which handle this

---

## Production Checklist

- [ ] PostgreSQL database created on Render
- [ ] Environment variables set in Render
- [ ] Build command configured
- [ ] Start command configured with `$PORT`
- [ ] Database connection successful (check logs)
- [ ] Tables auto-created by Hibernate
- [ ] OTP column migration run (if needed)
- [ ] Email sending tested
- [ ] API endpoints tested
- [ ] Frontend connected to new backend URL
- [ ] CORS configured for frontend domain

---

## Render Configuration Summary

```yaml
# Service Type: Web Service
# Build Command: mvn clean package -DskipTests
# Start Command: java -Dserver.port=$PORT -jar target/KasiKotas-1.0-SNAPSHOT.jar
# Environment: Docker (or Native)
# Instance Type: Free (or Starter)
# Region: Oregon (same as database)

# Environment Variables:
# DATABASE_URL: (auto-populated from PostgreSQL)
# JWT_SECRET: r1QycRsGXwPCyZxDTw9vIiCIV8ADcvQRxPXe/pe7cPcJv0oHH8iOVTHA9WjldrzKDu2x1OaCMjhQYbzdtlqsQQ==
# MAIL_USERNAME: m.maboya@yahoo.com
# MAIL_PASSWORD: xicrwhfsdvoltvrv
# ADMIN_EMAIL: m.maboya@yahoo.com
```

---

## Next Steps After Deployment

1. **Run Database Migrations**:
   - Connect to PostgreSQL shell in Render
   - Run migration scripts (scheduled delivery, promo codes, OTP)

2. **Test OTP Email Flow**:
   ```bash
   curl -X POST https://your-app.onrender.com/api/auth/forgot-password \
     -H "Content-Type: application/json" \
     -d '{"email":"your-email@example.com"}'
   ```

3. **Update Frontend**:
   - Change API base URL to Render URL
   - Update CORS allowed origins in SecurityConfig if needed

4. **Monitor Logs**:
   - Watch for any runtime errors
   - Check email sending success
   - Verify database queries

---

## Support

If deployment fails:
1. Check Render build logs
2. Check Render runtime logs
3. Verify all environment variables are set
4. Ensure DATABASE_URL format is correct
5. Test database connection using Render's shell

**Status**: Ready to deploy! 🚀
