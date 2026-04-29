-- Add OTP column to password_reset_tokens table (PostgreSQL)
-- Run this migration to enable OTP-based password reset

-- Add OTP column
ALTER TABLE password_reset_tokens 
ADD COLUMN IF NOT EXISTS otp VARCHAR(6);

-- Optional: Add index for faster OTP lookups
CREATE INDEX IF NOT EXISTS idx_password_reset_otp 
ON password_reset_tokens(email, otp, used, expires_at);

-- Display the updated table structure
\d password_reset_tokens
