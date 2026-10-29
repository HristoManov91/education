# Integration Patterns for Distributed Systems — decisions, queue fairness and AsyncAPI contracts

> **P2 · Devoxx Belgium 2026 · Java 25 · Maven · JUnit 5 · Spring Boot 4.1.1 dependency management**  
> Original talk: [Integration patterns for distributed systems — Dirk Fröhner, Sabarish Sekar](https://www.youtube.com/watch?v=_CtnBnmYQC0).
>
> This learning material is informed by the **user-supplied Gemini-generated SRT**. Its audio/text accuracy is not established word for word; AWS-specific technical behavior has been checked against AWS documentation.

## 1. Real-world entry point

Booking sends commands to Payments, publishes facts to Loyalty and Notifications, and integrates with an external provider. We need to decide **where coupling belongs**, who initiates each interaction, and what happens if a consumer is unavailable, slow or noisy.

Separately, our SaaS has 20 tenants sharing a queue. One tenant can publish a huge burst; a healthy total throughput masks the fact that quiet tenants wait for ages. We must choose between FIFO, fair scheduling, cells, shuffle sharding and dedicated capacity, without claiming that any single choice is free.

**Anti-pattern:** choose "Kafka everywhere" or create one queue per tenant without expressing the business requirement and operational trade-off.

## 2. Three complementary learning blocks

| Module | Main engineering decision | Read |
| --- | --- | --- |
| **01 — Integration Patterns Decision Guide (P2)** | REST vs queue vs topic; command vs event; location/format/temporal/domain coupling; push/pull, backpressure and delivery boundaries | [01 — Integration Decision Guide](01-INTEGRATION-DECISION-GUIDE.md) |
| **02 — Queue Fairness and Noisy Neighbor (P2)** | Measure per-tenant dwell, compare FIFO and round robin, protect independent cells, understand shuffle pair collisions and SQS Fair Queues | [02 — Runnable Fairness Lab](02-QUEUE-FAIRNESS-NOISY-NEIGHBOR.md) |
| **03 — AsyncAPI Contract-First (P2)** | Published message schema, sender/receiver operations, JSON validation, backward/forward compatibility, HTML docs and CLI validation | [03 — AsyncAPI Contract-First](03-ASYNCAPI-CONTRACT-FIRST.md) |

**We intentionally do not re-teach** Saga, Outbox, Inbox, HTTP Idempotency, Event Sourcing, RabbitMQ/Kafka or the existing resilience load-shedding lab. Instead, the chapters point directly to those executable modules.

## 3. README → code → tests

| Problem | Production-like Java model | Proof |
| --- | --- | --- |
| Fairness vs global queue | [GlobalFifoScheduler](src/main/java/bg/hristomanov/education/integrationpatterns/queue/GlobalFifoScheduler.java), [TenantRoundRobinScheduler](src/main/java/bg/hristomanov/education/integrationpatterns/queue/TenantRoundRobinScheduler.java) | [QueueFairnessSimulationTest](src/test/java/bg/hristomanov/education/integrationpatterns/queue/QueueFairnessSimulationTest.java) |
| Dwell time/p95, processing cost | [QueueSimulation](src/main/java/bg/hristomanov/education/integrationpatterns/queue/QueueSimulation.java), [QueuedJob](src/main/java/bg/hristomanov/education/integrationpatterns/queue/QueuedJob.java) | QueueFairnessSimulationTest |
| Bounded admission vs false ACK | [BoundedAdmissionQueue](src/main/java/bg/hristomanov/education/integrationpatterns/queue/BoundedAdmissionQueue.java) | QueueFairnessSimulationTest |
| Independent cell queues | [CellRouter](src/main/java/bg/hristomanov/education/integrationpatterns/queue/CellRouter.java) | [CellIsolationTest](src/test/java/bg/hristomanov/education/integrationpatterns/queue/CellIsolationTest.java) |
| Shuffle pair assignment and collision math | [ShufflePairAssigner](src/main/java/bg/hristomanov/education/integrationpatterns/queue/ShufflePairAssigner.java) | [ShufflePairAssignerTest](src/test/java/bg/hristomanov/education/integrationpatterns/queue/ShufflePairAssignerTest.java) |
| Minimal runnable CLI | [FairnessDemo](src/main/java/bg/hristomanov/education/integrationpatterns/queue/FairnessDemo.java) | same measured result as fairness tests |
| Shared AsyncAPI 3.1 message and send/receive operations | [order-events.yaml](src/main/resources/asyncapi/order-events.yaml), [producer](src/main/resources/asyncapi/order-events-send.yaml), [consumer](src/main/resources/asyncapi/order-events-receive.yaml) | [OrderCreatedContractTest](src/test/java/bg/hristomanov/education/integrationpatterns/event/OrderCreatedContractTest.java), [AsyncAPI CLI workflow](../../../.github/workflows/asyncapi-contract.yml) |
| Real serialized Java event & compatibility | [OrderCreatedEvent](src/main/java/bg/hristomanov/education/integrationpatterns/event/OrderCreatedEvent.java), [fixtures](src/test/resources/asyncapi/order-created-valid.json) | [03 — AsyncAPI lesson](03-ASYNCAPI-CONTRACT-FIRST.md) |

## 4. Run (no AWS account, no credentials, no broker)

```bash
# From the root of education
mvn -pl spring/messaging/integration-patterns test

mvn -pl spring/messaging/integration-patterns compile
java -cp spring/messaging/integration-patterns/target/classes bg.hristomanov.education.integrationpatterns.queue.FairnessDemo

# Full repository
mvn --no-transfer-progress verify
```

**Deterministic results:** 80 one-tick jobs from `noisy` ahead of 5 one-tick jobs from `quiet`. The queue is fully populated at tick 0, with one non-preemptive worker.

| | Quiet p95 dwell | Total processing time |
| --- | ---: | ---: |
| Global FIFO | 84 ticks | 85 ticks |
| Tenant round robin | 9 ticks | 85 ticks |

The p95 uses nearest rank on only five quiet messages (effectively the max) and is not a measured production latency. More fairness does **not** mean more worker throughput.

## 5. Senior mental model

**Coupling is multi-dimensional, not boolean.** Queues reduce producer/consumer timing dependence at the cost of broker and message-delivery operations. A topic moves domain knowledge from a publisher onto consumers, but does not eliminate shared event contracts.

**Fairness and isolation are distinct:** fairness decides whose job runs next; cells limit who shares resources; shuffle sharding distributes overlapping subsets and has a non-trivial probability of partial overlap. The lab explicitly proves that `1/28` is the chance of an *identical 2-of-8 pair*, not of *any overlap*.

**The code is a model, not an SQS simulator.** No distributed durability, acknowledgements, concurrency, real arrival processes, real AWS scheduling, isolation under shared databases, queue routing/resharding or failover has been implemented.

## 6. Existing links and source material

- [Outbox/Inbox](../outbox-inbox/README.md) — delivery gaps, duplicates, Kafka ordering and poison messages.
- [Saga](../saga/README.md) — command orchestration, compensations and distributed business state.
- [Domain Events](../../events/domain-events/README.md) — application vs domain vs integration events.
- [Production API Resilience](../../reliability/fault-tolerance/PRODUCTION-API-RESILIENCE-P1.md) — backpressure, shared quotas and load shedding.
- [Strategic DDD / Context Mapping](../../architecture/strategic-ddd/02-CONTEXT-MAPPING.md) — ownership, runtime coupling and replication.
- [AWS SQS fair queues primary documentation](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-fair-queues.html).
- [AWS Builders' Library: Shuffle Sharding](https://aws.amazon.com/builders-library/workload-isolation-using-shuffle-sharding/).

## What to do next

Read **01** to make a transport decision; read **02** to predict and measure noisy-neighbor effects; read **03** to publish a verifiable message contract shared by the producer and consumer. For a real migration, define the business SLO, delivery semantics and compatibility direction separately from the spec.

**Optional AsyncAPI CLI:** In this module, use Node 24 + npm 11, `npm install`, `npm run validate` for the send/receive specs and `npm run docs` to generate ignored HTML docs in `target/asyncapi-docs/`. Maven tests require no Node or Kafka. See [03 — AsyncAPI lesson](03-ASYNCAPI-CONTRACT-FIRST.md) for exact commands and limitations.
