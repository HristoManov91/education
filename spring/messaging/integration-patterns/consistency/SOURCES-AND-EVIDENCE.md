# Source map and evidence

Reviewed 2026-11-25. [Emanuel Trandafir — Consistency Patterns in Event Driven Architecture](https://www.youtube.com/watch?v=8JuQaWFKk4w), supplied English auto-generated SRT (~45 min). Captions mistranscribe dual write, idempotency, choreography, Modulith, Kafka/XA and DLQ. Source preserved through thematic paraphrase; no copied transcript. Slides mentioned, not accessed here; no original demo repo identified from transcript.

| Approx SRT range | Speaker content | Lesson |
| --- | --- | --- |
| 03–08 | Order method DB/broker/file/loyalty/email; mocks; dual write | 01/08 |
| 08–18 | Saga, commands/events, orchestration/choreography, hybrid/monolith | 02 |
| 18–24 | Slow I/O holds DB connections; GET affected; commit after publish can fail | 08 |
| 24–33 | Delivery, outbox same DB, relay duplicate, idempotency key, observedAt | 03/04/06 |
| 34–38 | Full-message inbox, uniqueness, worker, timestamp window, retry/DLQ | 03/04/05 |
| 38–41 | Three axes attributed to Software Architecture: The Hard Parts | 01 |
| 41–45 | Tables, Modulith, Kafka commits, XA/2PC question; speaker has not experimented | 06/07 |

## Additional primary sources checked 2026-11-25
- [Spring Kafka transactions](https://docs.spring.io/spring-kafka/reference/kafka/transactions.html); [Spring Kafka EOS](https://docs.spring.io/spring-kafka/reference/kafka/exactly-once.html) — sequential synchronized commits and Kafka-scoped EOS.
- [Spring Kafka non-blocking retry](https://docs.spring.io/spring-kafka/reference/retrytopic.html); [how pattern works](https://docs.spring.io/spring-kafka/reference/retrytopic/how-the-pattern-works.html) — lost ordering, batch/container transaction limitations.
- [Spring Modulith 2.1 events](https://docs.spring.io/spring-modulith/reference/2.1/events.html); [2.1.1 registry API](https://docs.spring.io/spring-modulith/docs/2.1.x/api/org/springframework/modulith/events/core/EventPublicationRegistry.html); [resubmission API](https://docs.spring.io/spring-modulith/docs/2.1.x/api/org/springframework/modulith/events/ResubmissionOptions.html) — version pin same as Education.
- [PostgreSQL 2PC](https://www.postgresql.org/docs/18/two-phase.html); [PREPARE TRANSACTION](https://www.postgresql.org/docs/18/sql-prepare-transaction.html) — prepared locks and coordinator recovery, not Saga.
- [Chris Richardson — Saga](https://microservices.io/patterns/data/saga.html); [Chris Richardson — Outbox](https://microservices.io/patterns/data/transactional-outbox.html) — definitions; [Hard Parts](https://www.oreilly.com/library/view/software-architecture-the/9781492086888/) is referenced book, not full book read here.

Corrections/extensions: observedAt heuristic not causal order; at-least-once conditional on durable retention/retry/recovery; scoped Kafka EOS exists; sequential DB+Kafka commit gap; 2PC not compensation; mock count clue not proof. Lecture latency not independently reproduced. State machines, payloads, schemas, clocks and tests are synthetic. Real H2 and Modulith JDBC recovery and targeted Kafka local broker; no claim of XA/Oracle/production benchmark. CI records actual execution; source code alone not a pass. Future lectures extend these chapters.
