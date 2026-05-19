# Render.com Deployment Guide for KasiKotas

## 🚨 Database Connection Error Fix

Your deployment is failing because the application can't connect to a MySQL database. Here's how to fix it:

---

## Option 1: Use External MySQL (Recommended - Free Tier Available)

### Step 1: Create MySQL Database on Railway/Aiven/PlanetScale

**Railway.app (Recommended)**:
1. Go to [railway.app](https://railway.app)
2. Sign up/login with GitHub
3. Click "New Project" → "Provision MySQL"
4. Copy the connection details

**Aiven (Free $300 credit)**:
1. Go to [aiven.io](https://aiven.io)
2. Create free account
3. Create MySQL service
4. Get connection string

**PlanetScale (Free tier)**:
1. Go to [planetscale.com](https://planetscale.com)
2. Create database
3. Get connection string

---

### Step 2: Configure Environment Variables on Render

1. Go to your Render dashboard
2. Click on your web service
3. Go to **Environment** tab
4. Add these environment variables:

```
DATABASE_URL = jdbc:mysql://your-mysql-host:3306/kasikotas_db?useSSL=true&serverTimezone=UTC
DB_USERNAME = your_mysql_username
DB_PASSWORD = your_mysql_password
BANK_ENCRYPTION_KEY = your_base64_32_byte_key
```

**Example with Railway**:
```
DATABASE_URL = jdbc:mysql://containers-us-west-123.railway.app:6789/railway?useSSL=true&serverTimezone=UTC
DB_USERNAME = root
DB_PASSWORD = AbCdEfGhIjKlMnOp
```

**Example with Aiven**:
```
DATABASE_URL = jdbc:mysql://mysql-kasikotas-user.aivencloud.com:12345/defaultdb?useSSL=true&serverTimezone=UTC
DB_USERNAME = avnadmin
DB_PASSWORD = your_aiven_password
```

---

### Step 3: Import Your Database

#### Option A: Export from Local MySQL
```bash
# On your local machine
mysqldump -u root kasikotas_db > kasikotas_backup.sql

# Import to Railway/Aiven (they provide instructions)
```

#### Option B: Use MySQL Workbench
1. Connect to your external database
2. File → Run SQL Script
3. Select your local database dump

---

## Option 2: Use Render PostgreSQL (Switch from MySQL)

Render offers free PostgreSQL, but you'd need to convert from MySQL to PostgreSQL.

**Not recommended** - stick with MySQL using external provider.

---

## Option 3: Use JawsDB MySQL on Render

JawsDB is a MySQL add-on, but it may not be available on Render's free tier.

---

## Updated application.properties (Already Done)

Your `application.properties` now supports environment variables:

```properties
spring.datasource.url=${DATABASE_URL:jdbc:mysql://localhost:3306/kasikotas_db?useSSL=false&serverTimezone=UTC&createDatabaseIfNotExist=true}
spring.datasource.username=${DB_USERNAME:root}
spring.datasource.password=${DB_PASSWORD:}
```

This means:
- **Production (Render)**: Uses `DATABASE_URL`, `DB_USERNAME`, `DB_PASSWORD` environment variables
- **Local Development**: Falls back to `localhost:3306` with `root` and empty password

---

## Quick Fix Steps (Railway + Render)

### 1️⃣ Create MySQL on Railway (5 minutes)

```bash
# Visit https://railway.app
# Click "New Project" → "Provision MySQL"
# Click on MySQL service → "Variables" tab
# Copy these values:
MYSQLHOST = containers-us-west-xxx.railway.app
MYSQLPORT = 6543
MYSQLDATABASE = railway
MYSQLUSER = root
MYSQLPASSWORD = xxxxx
```

### 2️⃣ Set Environment Variables on Render

Go to Render dashboard → Your service → Environment → Add:

```
DATABASE_URL = jdbc:mysql://containers-us-west-xxx.railway.app:6543/railway?useSSL=true&serverTimezone=UTC
DB_USERNAME = root
DB_PASSWORD = xxxxx
```

### 3️⃣ Redeploy on Render

Click **"Manual Deploy"** → **"Clear build cache & deploy"**

---

## After Database is Connected

### Import Your Data

**Option A: Use MySQL Workbench**
1. Download MySQL Workbench
2. Connect to Railway database using the credentials
3. Go to Server → Data Import
4. Import from your local dump

**Option B: Use Railway CLI**
```bash
# Install Railway CLI
npm i -g @railway/cli

# Login
railway login

# Link to project
railway link

# Connect to MySQL
railway run mysql -h $MYSQLHOST -P $MYSQLPORT -u $MYSQLUSER -p$MYSQLPASSWORD $MYSQLDATABASE

# Import SQL file
railway run mysql -h $MYSQLHOST -P $MYSQLPORT -u $MYSQLUSER -p$MYSQLPASSWORD $MYSQLDATABASE < kasikotas_backup.sql
```

---

## Verify Connection

Once environment variables are set, your logs should show:

```
✅ HikariPool-1 - Starting...
✅ HikariPool-1 - Start completed.
✅ Started KasiKotasApplication in X.XXX seconds
```

Instead of:
```
❌ Communications link failure
❌ Connection refused
```

---

## Environment Variables Summary

Required on Render:

| Variable | Example | Description |
|----------|---------|-------------|
| `DATABASE_URL` | `jdbc:mysql://mysql-host:3306/dbname?useSSL=true&serverTimezone=UTC` | Full JDBC connection URL |
| `DB_USERNAME` | `root` or `admin` | MySQL username |
| `DB_PASSWORD` | `your-secure-password` | MySQL password |

Optional (already configured):
| Variable | Value | Description |
|----------|-------|-------------|
| `spring.mail.username` | `m.maboya@yahoo.com` | Email sender |
| `spring.mail.password` | `xicrwhfsdvoltvrv` | Yahoo app password |
| `jwt.secret` | (your secret) | JWT signing key |

---

## Cost Comparison

| Provider | Free Tier | Storage | Notes |
|----------|-----------|---------|-------|
| **Railway** | $5 credit (500 hours) | 1GB | Easiest setup |
| **Aiven** | $300 credit | 5GB | 30 days trial |
| **PlanetScale** | Free forever | 5GB | Serverless MySQL |
| **Render PostgreSQL** | Free | 1GB | Would require migration |

**Recommendation**: Use **Railway** for quick setup or **PlanetScale** for long-term free tier.

---

## Troubleshooting

### Error: "Access denied for user"
- Check `DB_USERNAME` and `DB_PASSWORD` are correct
- Verify user has permissions on the database

### Error: "Unknown database"
- Create database on your MySQL server
- Or add `createDatabaseIfNotExist=true` to `DATABASE_URL`

### Error: "SSL connection required"
- Ensure `useSSL=true` in `DATABASE_URL`
- Add `&requireSSL=true` if needed

### Error: "Connection timeout"
- Check MySQL host and port are correct
- Verify firewall allows connections from Render IPs
- Railway/Aiven usually allow all connections by default

---

## Next Steps After Database is Connected

1. ✅ Verify application starts successfully
2. Run database migrations:
   ```sql
   source database_migration_add_otp_column.sql
   ```
3. Create admin user
4. Test OTP email functionality
5. Configure CORS for your frontend domain

---

## Support

If you still have issues:
1. Check Render logs: Dashboard → Logs tab
2. Look for "HikariPool" in logs
3. Verify environment variables: Dashboard → Environment tab
4. Test database connection separately

**The application code is ready** - you just need to configure the database connection! 🚀
