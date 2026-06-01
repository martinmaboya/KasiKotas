-- Database migration: Add user account lock feature
-- Purpose: Allow admins to lock/suspend user accounts
-- Default: is_locked = false (all existing users are unlocked)

ALTER TABLE users ADD COLUMN is_locked BOOLEAN NOT NULL DEFAULT false;

-- Add index for faster queries on locked accounts during login
CREATE INDEX idx_users_is_locked ON users(is_locked);

