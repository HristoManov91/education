-- Oracle 19c manual-only example, dedicated schema.
-- Expected table VM29_NODES(ID, PARENT_ID, LABEL). Never run against production.
SELECT id, LEVEL - 1 AS depth, CONNECT_BY_ISCYCLE AS is_cycle
FROM vm29_nodes
START WITH id = 1
CONNECT BY NOCYCLE PRIOR id = parent_id
ORDER SIBLINGS BY id;

-- Oracle WITH recursive factoring omits the RECURSIVE keyword:
WITH hierarchy(id, depth) AS (
   SELECT id, 0 FROM vm29_nodes WHERE id = 1
   UNION ALL
   SELECT n.id, h.depth + 1 FROM vm29_nodes n
   JOIN hierarchy h ON n.parent_id = h.id
   WHERE h.depth < 10
)
CYCLE id SET is_cycle TO 1 DEFAULT 0
SELECT id, depth, is_cycle FROM hierarchy ORDER BY depth, id;
