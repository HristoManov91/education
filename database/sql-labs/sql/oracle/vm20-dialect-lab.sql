-- VM-20 Oracle Database 19c manual comparator.
-- RUN ONLY in a dedicated learning schema, AFTER sql/oracle/schema.sql.
-- Oracle has NOT been run in the automatic CI: verify outputs manually.

-- Oracle VARCHAR2 currently treats empty character strings as NULL.
SELECT CASE WHEN CAST('' AS VARCHAR2(10)) IS NULL THEN 1 ELSE 0 END
       AS empty_is_null FROM dual; -- expect 1 (not PostgreSQL false)

-- Explicit null placement; do not confuse this with the default null order.
WITH samples(id, metric) AS (
    SELECT 1, CAST(NULL AS NUMBER) FROM dual UNION ALL
    SELECT 2, 10 FROM dual UNION ALL
    SELECT 3, 5 FROM dual
)
SELECT id FROM samples ORDER BY metric ASC NULLS FIRST, id; -- 1,3,2

WITH samples(id, metric) AS (
    SELECT 1, CAST(NULL AS NUMBER) FROM dual UNION ALL
    SELECT 2, 10 FROM dual UNION ALL
    SELECT 3, 5 FROM dual
)
SELECT id FROM samples ORDER BY metric ASC NULLS LAST, id; -- 3,2,1

SELECT id FROM customer ORDER BY id FETCH FIRST 2 ROWS ONLY; -- 1,2

-- Oracle 19c JSON is commonly stored in VARCHAR2/CLOB/BLOB, with SQL/JSON functions.
SELECT JSON_VALUE('{"order":{"status":"PAID"}}',
                  '$.order.status' RETURNING VARCHAR2(20) ERROR ON ERROR)
       AS status FROM dual;

-- Oracle 19c sequence syntax; execute once per dedicated learning schema.
CREATE SEQUENCE vm20_unique_seq START WITH 10 INCREMENT BY 1;
SELECT vm20_unique_seq.NEXTVAL FROM dual;
ROLLBACK;
SELECT vm20_unique_seq.NEXTVAL FROM dual; -- value advances regardless of rollback

-- Oracle recursive factoring omits the RECURSIVE keyword.
WITH hierarchy(id, depth) AS (
    SELECT id, 0 FROM customer WHERE id = 1
    UNION ALL
    SELECT c.id, h.depth + 1 FROM customer c JOIN hierarchy h ON c.manager_id = h.id
)
SELECT id, depth FROM hierarchy ORDER BY depth, id;

-- Estimates only; actual stats require privileges / controlled execution.
EXPLAIN PLAN FOR SELECT * FROM task_event WHERE status = 'RETRY';
SELECT * FROM TABLE(DBMS_XPLAN.DISPLAY());
-- With a suitable statement run and privileges:
-- SELECT * FROM TABLE(DBMS_XPLAN.DISPLAY_CURSOR(NULL, NULL, 'ALLSTATS LAST'));

-- Oracle supports FOR UPDATE SKIP LOCKED, but combined ordering/row limiting
-- restrictions differ from PostgreSQL. Do not copy the PG LIMIT/FOR UPDATE query.
-- Run locks in TWO real sessions, not inside this full-file setup script.
