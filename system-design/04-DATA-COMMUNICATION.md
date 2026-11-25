# SD-08/09 — Sync/async, data ownership, consistency and query models

> **P1 · свързващ decision guide, НЕ дубликат на готовите persistence/messaging labs**. Основи: [Microsoft — Queue-Based Load Leveling](https://learn.microsoft.com/en-us/azure/architecture/patterns/queue-based-load-leveling), [AWS REL05](https://docs.aws.amazon.com/wellarchitected/latest/framework/rel05.html), [Martin Fowler — CQRS](https://martinfowler.com/bliki/CQRS.html).

## 1. Реален сценарий

След `POST /documents/{id}/sign` потребителят очаква документът да стане подписан. После трябва да се обновят:
- transactional signature record;
- read/search projection;
- audit record;
- външно уведомление.

Наивна имплементация прави DB write, notification HTTP, cache eviction и broker send вътре в един `@Transactional`. Има възможност за DB+broker dual-write, lock hold, retry duplicates, race with cache и „success when only one side succeeded“.

## 2. Кое трябва да бъде силно консистентно?

| Операция | Нужен semantics | Candidate design | Кога НЕ |
| --- | --- | --- | --- |
| Проверка на право за подпис и state transition | DB transaction + optimistic/pessimistic concurrency per invariant | local transaction and row version | eventual read projection за authorization decision |
| Списък/аналитична справка | може да допуска ограничено staleness, **ако бизнесът го позволява** | DTO projection, MV, CQRS read model | ако този read от него взема irreversible financial/legal decision |
| Уведомление | retryable external side effect | after-commit + durable outbox if guaranteed delivery needed | unbounded HTTP retry in write transaction |
| Export | async, status + result delivery | worker queue/job status, claim/checkpoint | `202` as „download ready“ |
| Cross-service contract | explicit schema + ownership | sync REST for immediate answer, event for independent reaction | broker като заместител на ясни semantics |

## 3. Rules for communication

**Synchronous:** зависимостта трябва да е налична в request critical path. Бюджетирай end-to-end timeout, cancellation, retry budget и error propagation.

**Asynchronous:** producer не чака completion; broker/queue only moves the consistency boundary. Трябват dedup, ordering policy, backpressure, retention, DLQ/replay и monitoring на lag. „At-least-once“ не е exactly-once business effect.

**CQRS:** read model ≠ задължително друг server/DB. Първо опитай tailored DTO query в същата база, после projection при измерен read bottleneck.

**Cache:** решава performance за някои reads, не correctness; multi-instance invalidation race е реална. При sensitive signing workflow не вярвай на кеш като единствен източник на authority.

## 4. Failure windows

```text
Business DB commit successful
    ↓
Network publish attempt times out
    ├── broker did NOT accept → retry required
    └── broker DID accept → retry can duplicate

Transactional Outbox:
DB transaction: business state + outbox row
→ relay publishes (may duplicate)
→ consumer Inbox/idempotency guards business effect
```

Разликата между atomicity in local DB и eventual processing на чужда услуга е ключов architectural boundary.

## 5. Reuse — executable evidence, без ново копие

- [CQRS synchronous vs deferred projections tests](../spring/architecture/cqrs/README.md)
- [Outbox/Inbox dual-write failure tests](../spring/messaging/outbox-inbox/README.md)
- [Saga compensation, unknown outcomes](../spring/messaging/saga/README.md)
- [HTTP Idempotency](../spring/reliability/idempotency/README.md)
- [Oracle/SQL transaction labs](../database/transaction-labs/README.md)
- [Integration choice REST vs queue vs pub/sub](../spring/messaging/integration-patterns/01-INTEGRATION-DECISION-GUIDE.md)
- [Caching consistency](../spring/caching/README.md)

## Свързано P1 — Event-Driven Consistency

[EC-01–08](../spring/messaging/integration-patterns/consistency/README.md) — three axes, hybrid Saga, durable inbox, gap/replay, Kafka vs XA and Modulith registry; new tests plus existing evidence.

## 6. Decision card: да сложим ли Kafka за всяка операция?

| Измерение | Да, ако | Не, ако |
| --- | --- | --- |
| Problem | много независими consumers + durable asynchronous workflow / replay | един synchronous validation step с immediate decision |
| Benefit | отделяне във времето, batching, decoupled consumer lifecycle | няма бизнес consumer, усложняваме без value |
| Cost | брокер ops, schemas, retry, idempotency, monitoring, ordering | simple local in-process event or direct call са достатъчни |
| Alternative | outbox + worker, DB job table, in-process event | изборът е според delivery guarantee, не мода |
| Proof | failure injection, consumer dedup, checkpoint replay | „we use Kafka“ не е доказателство |

## 7. Самостоятелна задача

След sign request клиентът получава timeout, но DB commit вече е успешен. Клиентът повторно изпраща POST, а event relay publish-ва twice. Направи sequence diagram за:
- duplicate HTTP request;
- duplicate integration event;
- stale read projection;
- user-visible `WAITING / SIGNED` status.

Избери **две различни idempotency boundaries** и negative tests. Не предполагай distributed transaction.
