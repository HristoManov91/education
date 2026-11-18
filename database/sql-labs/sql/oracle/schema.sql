-- Oracle 19c manual, lab-only schema. Run in a dedicated clean schema.
-- Names align with PostgreSQL fixtures; Oracle NUMBER/VARCHAR2 types are intentional.
CREATE TABLE customer (
    id NUMBER(19) PRIMARY KEY,
    email VARCHAR2(180 CHAR) NOT NULL,
    full_name VARCHAR2(140 CHAR) NOT NULL,
    region VARCHAR2(10 CHAR) NOT NULL,
    manager_id NUMBER(19),
    CONSTRAINT uq_customer_email UNIQUE (email),
    CONSTRAINT fk_customer_manager FOREIGN KEY (manager_id) REFERENCES customer(id)
);

CREATE TABLE purchase_order (
    id NUMBER(19) PRIMARY KEY,
    customer_id NUMBER(19) NOT NULL,
    external_ref VARCHAR2(32 CHAR) NOT NULL,
    status VARCHAR2(12 CHAR) NOT NULL,
    total NUMBER(12, 2) NOT NULL,
    placed_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_purchase_order_customer FOREIGN KEY (customer_id) REFERENCES customer(id),
    CONSTRAINT uq_order_customer_reference UNIQUE (customer_id, external_ref),
    CONSTRAINT ck_order_status CHECK (status IN ('PAID', 'PENDING', 'CANCELLED')),
    CONSTRAINT ck_order_total CHECK (total >= 0)
);
CREATE INDEX idx_order_customer_status ON purchase_order(customer_id, status);

INSERT INTO customer(id, email, full_name, region, manager_id)
    VALUES(1, 'ada@example.test', 'Ada', 'BG', NULL);
INSERT INTO customer(id, email, full_name, region, manager_id)
    VALUES(2, 'boris@example.test', 'Boris', 'BG', 1);
INSERT INTO customer(id, email, full_name, region, manager_id)
    VALUES(3, 'clara@example.test', 'Clara', 'DE', NULL);
INSERT INTO customer(id, email, full_name, region, manager_id)
    VALUES(4, 'dima@example.test', 'Dima', 'BG', 2);
INSERT INTO customer(id, email, full_name, region, manager_id)
    VALUES(5, 'eva@example.test', 'Eva', 'BG', 1);

INSERT INTO purchase_order VALUES(101,1,'A-01','PAID',100,TIMESTAMP '2026-01-01 10:00:00');
INSERT INTO purchase_order VALUES(102,1,'A-02','PAID',50,TIMESTAMP '2026-01-02 10:00:00');
INSERT INTO purchase_order VALUES(103,1,'A-03','PENDING',30,TIMESTAMP '2026-01-03 10:00:00');
INSERT INTO purchase_order VALUES(104,2,'B-01','PAID',180,TIMESTAMP '2026-01-01 10:00:00');
INSERT INTO purchase_order VALUES(105,2,'B-02','CANCELLED',40,TIMESTAMP '2026-01-03 10:00:00');
INSERT INTO purchase_order VALUES(106,3,'C-01','PAID',90,TIMESTAMP '2026-01-04 10:00:00');
INSERT INTO purchase_order VALUES(107,4,'D-01','PENDING',25,TIMESTAMP '2026-01-05 10:00:00');
COMMIT;

-- VM-04: optional performance fixture with 20 000 records (Oracle dialect).
CREATE TABLE task_event(
    id NUMBER(19) PRIMARY KEY,
    tenant_id NUMBER(10) NOT NULL,
    status VARCHAR2(10 CHAR) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    payload VARCHAR2(120 CHAR) NOT NULL
);
INSERT INTO task_event(id, tenant_id, status, created_at, payload)
SELECT LEVEL,
       MOD(LEVEL, 10) + 1,
       CASE WHEN MOD(LEVEL, 100) = 0 THEN 'RETRY' ELSE 'DONE' END,
       TIMESTAMP '2026-01-01 00:00:00' + NUMTODSINTERVAL(LEVEL, 'SECOND'),
       RPAD('x', 100, 'x')
FROM dual CONNECT BY LEVEL <= 20000;
COMMIT;

CREATE INDEX idx_task_event_status ON task_event(status);
CREATE INDEX idx_task_event_tenant_status_created
    ON task_event(tenant_id, status, created_at DESC, id DESC);

-- You need EXECUTE privilege on DBMS_STATS and appropriate schema privileges.
BEGIN
    DBMS_STATS.GATHER_TABLE_STATS(USER, 'TASK_EVENT');
END;
/
