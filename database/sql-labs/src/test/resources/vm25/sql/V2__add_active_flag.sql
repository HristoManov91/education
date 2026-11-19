-- Migration 2: additive non-null column with a migration-safe default.
ALTER TABLE vm25_account
ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;
