-- Run ONLY in an isolated lab database after schema.sql, seed.sql, performance.sql.
-- EXPLAIN ANALYZE executes SQL; these are read-only SELECT examples.

-- VM-01: JOIN cardinality vs EXISTS (4 rows vs 3 unique customers)
SELECT c.id
FROM customer c
JOIN purchase_order o ON o.customer_id = c.id
WHERE o.status = 'PAID'
ORDER BY c.id;

SELECT c.id
FROM customer c
WHERE EXISTS (
    SELECT 1 FROM purchase_order o
    WHERE o.customer_id = c.id AND o.status = 'PAID'
)
ORDER BY c.id;

-- VM-03: same CTE/window query as the Java repository.
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

-- VM-04: actual row counts are 200 and 19 800 in this controlled fixture.
SELECT status, COUNT(*) AS rows
FROM task_event GROUP BY status ORDER BY status;

EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT * FROM task_event WHERE status = 'RETRY';

EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT * FROM task_event WHERE status = 'DONE';

-- Composite index workload: tenant + status equality, bounded sorted page.
EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT id FROM task_event
WHERE tenant_id = 1 AND status = 'RETRY'
ORDER BY created_at DESC, id DESC
LIMIT 10;

-- Do not assume an index is used just because it exists.
SELECT indexname, indexdef
FROM pg_indexes WHERE schemaname = current_schema()
  AND tablename = 'task_event'
ORDER BY indexname;

-- Examine planner statistics; estimates may drift with a different data set.
SELECT attname, n_distinct, most_common_vals, most_common_freqs
FROM pg_stats
WHERE schemaname = current_schema() AND tablename = 'task_event'
  AND attname IN ('tenant_id', 'status');
