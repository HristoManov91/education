-- VM-01 / VM-02: logical business constraints belong to the database, not only Java.
CREATE TABLE customer (
    id BIGINT PRIMARY KEY,
    email VARCHAR(180) NOT NULL,
    full_name VARCHAR(140) NOT NULL,
    region VARCHAR(10) NOT NULL,
    manager_id BIGINT,
    CONSTRAINT uq_customer_email UNIQUE (email),
    CONSTRAINT fk_customer_manager FOREIGN KEY (manager_id) REFERENCES customer(id)
);

CREATE TABLE purchase_order (
    id BIGINT PRIMARY KEY,
    customer_id BIGINT NOT NULL,
    external_ref VARCHAR(32) NOT NULL,
    status VARCHAR(12) NOT NULL,
    total NUMERIC(12, 2) NOT NULL,
    placed_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_purchase_order_customer FOREIGN KEY (customer_id) REFERENCES customer(id),
    CONSTRAINT uq_order_customer_reference UNIQUE (customer_id, external_ref),
    CONSTRAINT ck_order_status CHECK (status IN ('PAID', 'PENDING', 'CANCELLED')),
    CONSTRAINT ck_order_total CHECK (total >= 0)
);

-- A foreign key on the referencing side does NOT automatically imply an index.
-- Here, indexing is justified by our actual customer + status workload.
CREATE INDEX idx_order_customer_status ON purchase_order(customer_id, status);
