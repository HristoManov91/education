# Default consistency lab — Java 25, JDBC/H2 and Spring Modulith 2.1.1

## Казус и доказателства

| Concept | Code | Test |
| --- | --- | --- |
| Durable receipt, immutable envelope, duplicate/conflict | [DurableInbox](src/main/java/bg/hristomanov/education/consistency/DurableInbox.java) | [DurableInboxTest](src/test/java/bg/hristomanov/education/consistency/DurableInboxTest.java) |
| Separate receipt/effect/failure transactions, gap, retry, terminal, redrive | same | 9 methods with actual file-backed H2 |
| Hybrid orchestration, terminal event, optional subscriber | [HybridOrderWorkflow](src/main/java/bg/hristomanov/education/consistency/HybridOrderWorkflow.java) | [HybridOrderWorkflowTest](src/test/java/bg/hristomanov/education/consistency/HybridOrderWorkflowTest.java), 6 methods |
| Registry after-commit listener rollback and context restart | [PublicationRegistryTest](src/test/java/bg/hristomanov/education/consistency/PublicationRegistryTest.java) (includes Boot config, source, listener) | 2 methods, actual JDBC registry and async listener |

Run from root (Java25, Maven):

    mvn -B --no-transfer-progress -pl spring/messaging/integration-patterns/consistency/lab test

Or root verify. Inventory 17 JUnit methods (not a claim of PASS until CI execution). No Docker/broker/keys in default module. H2 files isolated in JUnit temporary directories; new datasource/context checks persisted state.

## Walkthrough

1. Receiver validates stable envelope and commits receipt before returning ACK eligibility. Inject before-commit failure, check row absent; repeat duplicate after new datasource.
2. Worker applies signed numeric delta to account balance and advances per-aggregate sequence in same TX as DONE. Inject failure after SQL, assert all state rolled back, separate retry metadata committed. v2 arrives before v1 with misleading timestamp; wait for next due time. Poison or exhausted delta remains FAILED and blocks dependent delta. Redrive with reason, unchanged event identity and payload, after underlying transient cause fixed.
3. Hybrid pure model separates known rejection from unknown payment outcome. A notification projection accepts confirmed terminal fact; simulated failure cannot cancel order. Real persistence/recovery remains in existing Saga module.
4. Actual Modulith source transaction writes row + publication. Listener fails after SQL, rolls back its own transaction and registry marks failed. Close context, reopen same file-backed DB, resubmit failed publication with bounded batch, verify completed and one effect. Rollback case ensures no event publication.

## Boundaries

H2-specific schema initialization and MERGE for idempotent absolute projection. PostgreSQL migration: BIGINT/VARCHAR + constraints/indexes, INSERT ON CONFLICT with explicit conflict verification, SELECT FOR UPDATE / SKIP LOCKED with appropriate per-aggregate policy. Oracle: NUMBER(19), VARCHAR2, migration tool, unique constraint and explicit handling ORA-00001, MERGE with concurrency/unique checks. Dialect tests required; no claim that H2 proves either production engine. Single worker, no multi-instance lease or durable attempt history; crash between failure rollback and attempt metadata may repeat attempt. No actual remote broker ACK, scheduler, external call, security endpoint or full DLQ integration in this module; caller may only ACK after receive returns; bounded dueIds and gap backoff prevent immediate hot loop. Production needs admission/backlog limits, retention/partitioning, audit, tenant scopes, scheduler ownership, external idempotency. Existing [processed-ID lab](../../../outbox-inbox/README.md) and [Saga](../../../saga/README.md) remain canonical. Kafka proof is separate [opt-in module](../kafka-lab/README.md).

[Curriculum](../README.md) · [Evidence map](../SOURCES-AND-EVIDENCE.md).
