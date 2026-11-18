-- VM-04: skewed data, 1% RETRY and 99% DONE. PostgreSQL-only fixture.
CREATE TABLE task_event (
    id BIGINT PRIMARY KEY,
    tenant_id INTEGER NOT NULL,
    status VARCHAR(10) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    payload VARCHAR(120) NOT NULL
);

INSERT INTO task_event (id, tenant_id, status, created_at, payload)
SELECT n, (n % 10) + 1,
       CASE WHEN n % 100 = 0 THEN 'RETRY' ELSE 'DONE' END,
       TIMESTAMP '2026-01-01 00:00:00' + n * INTERVAL '1 second',
       repeat('x', 100)
FROM generate_series(1, 20000) AS n;

CREATE INDEX idx_task_event_status ON task_event(status);
CREATE INDEX idx_task_event_tenant_status_created
    ON task_event(tenant_id, status, created_at DESC, id DESC);
ANALYZE task_event;
