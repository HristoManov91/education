-- VM-20 PostgreSQL 17 manual comparator; run in isolated schema AFTER schema.sql/seed.sql/performance.sql.
-- PostgreSQL keeps '' distinct from NULL.
SELECT length(''::text) AS empty_length, ''::text IS NULL AS empty_is_null,
       NULLIF(''::text, '') IS NULL AS explicitly_normalized;

-- Explicit ordering; both PostgreSQL and Oracle support NULLS FIRST/LAST.
WITH samples(id, metric) AS (
    VALUES (1, CAST(NULL AS INT)), (2, 10), (3, 5)
)
SELECT id FROM samples ORDER BY metric ASC NULLS FIRST, id; -- 1,3,2
WITH samples(id, metric) AS (
    VALUES (1, CAST(NULL AS INT)), (2, 10), (3, 5)
)
SELECT id FROM samples ORDER BY metric ASC NULLS LAST, id; -- 3,2,1

-- SQL standard FETCH FIRST works in both databases if ORDER BY is deterministic.
SELECT id FROM customer ORDER BY id FETCH FIRST 2 ROWS ONLY; -- 1,2

-- PostgreSQL JSONB-specific operator; Oracle 19c uses JSON_VALUE instead.
SELECT '{"order":{"status":"PAID"}}'::jsonb #>> '{order,status}' AS status;

-- PostgreSQL values from a sequence are NOT rolled back with the transaction.
CREATE TEMP SEQUENCE vm20_unique_seq START WITH 10;
BEGIN;
SELECT nextval('vm20_unique_seq') AS first_value;
ROLLBACK;
SELECT nextval('vm20_unique_seq') AS later_value; -- strictly greater

-- PostgreSQL EXPLAIN ANALYZE executes the query; here deliberately SELECT only.
EXPLAIN (ANALYZE, BUFFERS)
SELECT * FROM task_event WHERE status = 'RETRY';

-- PostgreSQL locking demonstration exists in PostgresTransactionLabIT, not duplicated.
