-- Oracle 19c syntax. Requires a dedicated schema loaded via sql/oracle/schema.sql.
-- This is NOT an automated Oracle test and requires a real Oracle environment.

-- Semijoin: exactly one customer row for every parent with at least one paid order.
SELECT c.id FROM customer c
WHERE EXISTS (
    SELECT 1 FROM purchase_order o
    WHERE o.customer_id = c.id AND o.status = 'PAID'
)
ORDER BY c.id;

-- The analytic window query works in Oracle 19c.
WITH revenue AS (
    SELECT c.id AS customer_id, c.region,
           COALESCE(SUM(CASE WHEN o.status = 'PAID' THEN o.total END), 0) AS paid_total
    FROM customer c LEFT JOIN purchase_order o ON o.customer_id = c.id
    GROUP BY c.id, c.region
), ranked AS (
    SELECT region, customer_id, paid_total,
           ROW_NUMBER() OVER (
               PARTITION BY region ORDER BY paid_total DESC, customer_id
           ) AS row_position
    FROM revenue
)
SELECT region, customer_id, paid_total
FROM ranked WHERE row_position = 1
ORDER BY region;

-- Oracle recursive subquery factoring: do NOT write WITH RECURSIVE here.
WITH hierarchy(id, depth) AS (
    SELECT id, 0 FROM customer WHERE id = 1
    UNION ALL
    SELECT c.id, h.depth + 1
    FROM customer c JOIN hierarchy h ON c.manager_id = h.id
)
SELECT id, depth FROM hierarchy ORDER BY depth, id;

-- Cost estimates only; EXPLAIN PLAN does not execute the SELECT.
EXPLAIN PLAN FOR
SELECT * FROM task_event WHERE status = 'RETRY';
SELECT * FROM TABLE(DBMS_XPLAN.DISPLAY());

-- For actual row statistics, execute the query with instrumentation enabled.
-- Privileges (V$SQL etc) and session settings can restrict DISPLAY_CURSOR.
SELECT /*+ gather_plan_statistics */ *
FROM task_event WHERE status = 'RETRY';
SELECT * FROM TABLE(DBMS_XPLAN.DISPLAY_CURSOR(NULL, NULL, 'ALLSTATS LAST'));

SELECT id FROM task_event
WHERE tenant_id = 1 AND status = 'RETRY'
ORDER BY created_at DESC, id DESC
FETCH FIRST 10 ROWS ONLY;
