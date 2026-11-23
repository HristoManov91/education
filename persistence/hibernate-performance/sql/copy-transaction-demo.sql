-- PostgreSQL/psql-only manual lab: COPY FROM is transactional.
-- Do NOT execute against a production database. Uses TEMP TABLE (session-local).
-- Run from education/ root:
-- psql -X -v ON_ERROR_STOP=1 -d postgres -f persistence/hibernate-performance/sql/copy-transaction-demo.sql
-- Expected: inside_transaction=2, after_rollback=0.
-- Source: https://www.postgresql.org/docs/17/sql-copy.html

CREATE TEMP TABLE education_copy_tx_demo (
    id BIGINT PRIMARY KEY,
    label TEXT NOT NULL
) ON COMMIT PRESERVE ROWS;

BEGIN;

COPY education_copy_tx_demo (id, label) FROM STDIN WITH (FORMAT csv);
1,alpha
2,beta
\.

SELECT COUNT(*) AS inside_transaction FROM education_copy_tx_demo;

ROLLBACK;

SELECT COUNT(*) AS after_rollback FROM education_copy_tx_demo;

DROP TABLE education_copy_tx_demo;
