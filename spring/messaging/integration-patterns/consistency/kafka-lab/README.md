# Real Kafka + DB commit-order proof — targeted module

## Problem
Starting two local transactions is not one atomic distributed transaction. This lab uses actual embedded Kafka KRaft (Spring Kafka test tooling, managed by Boot 4.1.1) and file-backed H2, not MockProducer or an in-memory map.

[KafkaCommitWindowTest](src/test/java/bg/hristomanov/education/consistency/kafka/KafkaCommitWindowTest.java) uses separate topics, transactional IDs, bounded calls and actual Kafka consumers.

| Experiment | Failure | Expected independent evidence |
| --- | --- | --- |
| DB first | Replacement producer fences original after DB commit, old Kafka commit fails | DB row remains; read_committed sees only committed marker; read_uncommitted sees aborted order + marker |
| Kafka first | Commit Kafka, then close uncommitted H2 connection and attempt commit | SQLException, DB row absent, read_committed sees order event |

The marker is a positive control, not an empty poll interpreted as absence. Fencing is deliberate; H2 connection close is a controlled rollback, not a model of uncertain distributed commit outcome. The experiments isolate the two real resource commits, not Spring's transaction synchronization internals, Kafka read-process-write EOS integration or XA/JTA implementation. No real production broker, replication guarantee, throughput benchmark or external database. A one-node broker is only for tests.

## Run
Java 25, Maven, local ephemeral ports, dependencies on first run; no Docker, no credentials:

    mvn -B --no-transfer-progress -f spring/messaging/integration-patterns/consistency/kafka-lab/pom.xml test

Or from root:

    mvn -B --no-transfer-progress -Pconsistency-kafka -pl spring/messaging/integration-patterns/consistency/kafka-lab test

Two JUnit methods; separate path-filtered CI executes this module on changes to its files/workflow. Default root reactor does not start a Kafka broker. Verify actual CI results before calling the proof passed.

## Exit
A failed second commit does not reverse the first. For DB as source of truth, [Outbox](../../../outbox-inbox/README.md) makes state + intent atomic, then accepts redelivery and consumer idempotency. Kafka EOS is scoped to Kafka. XA/2PC requires real enlisted resources, recovery coordinator and a justified invariant. [EC-06](../06-KAFKA-XA-2PC.md) · [Evidence map](../SOURCES-AND-EVIDENCE.md).

## Оригинални източници
[Spring Kafka test tooling](https://docs.spring.io/spring-kafka/reference/testing.html) · [Transactions](https://docs.spring.io/spring-kafka/reference/kafka/transactions.html) · [Producer fencing](https://kafka.apache.org/41/javadoc/org/apache/kafka/common/errors/ProducerFencedException.html).
