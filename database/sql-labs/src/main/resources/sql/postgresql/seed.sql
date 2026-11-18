-- Small deliberate fixture: multiple PAID orders, customers without orders,
-- a customer with only PENDING orders, nullable managers and a hierarchy.
INSERT INTO customer (id, email, full_name, region, manager_id) VALUES
    (1, 'ada@example.test',   'Ada',   'BG', NULL),
    (2, 'boris@example.test', 'Boris', 'BG', 1),
    (3, 'clara@example.test', 'Clara', 'DE', NULL),
    (4, 'dima@example.test',  'Dima',  'BG', 2),
    (5, 'eva@example.test',   'Eva',   'BG', 1);

INSERT INTO purchase_order (id, customer_id, external_ref, status, total, placed_at) VALUES
    (101, 1, 'A-01', 'PAID',      100.00, TIMESTAMP '2026-01-01 10:00:00'),
    (102, 1, 'A-02', 'PAID',       50.00, TIMESTAMP '2026-01-02 10:00:00'),
    (103, 1, 'A-03', 'PENDING',    30.00, TIMESTAMP '2026-01-03 10:00:00'),
    (104, 2, 'B-01', 'PAID',      180.00, TIMESTAMP '2026-01-01 10:00:00'),
    (105, 2, 'B-02', 'CANCELLED',  40.00, TIMESTAMP '2026-01-03 10:00:00'),
    (106, 3, 'C-01', 'PAID',       90.00, TIMESTAMP '2026-01-04 10:00:00'),
    (107, 4, 'D-01', 'PENDING',    25.00, TIMESTAMP '2026-01-05 10:00:00');
