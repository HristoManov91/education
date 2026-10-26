# Engineering Case Studies — реални production казуси

Това е **библиотека за възстановяване на инженерни решения**, а не колекция от интересни линкове. Целта е след месеци да можем бързо да отговорим:

- Какъв проблем са видели в production?
- Защо не е сработило очевидното решение?
- Какво точно са внедрили и при какви ограничения?
- Какви нови рискове са въвели?
- Кога същият подход е подходящ/неподходящ за наш Java/Spring backend?

Case study **не се брои за преминат урок или доказано знание**. Може да съдържа предложение за лаборатория, но не твърдим, че тестове съществуват, ако не са реализирани.

## Индекс

| ID | Казус | Симптом / проблем | Архитектурен избор | Свързани теми |
| --- | --- | --- | --- | --- |
| [001](001-decathlon-kafka-business-table-cdc.md) | Decathlon — Kafka/Order API | Dual write; consumer преди commit; PostgreSQL WAL retention | Debezium CDC върху business tables вместо директен Kafka publish | Distributed systems, Outbox, Kafka, PostgreSQL, CDC, Reliability |
| [002](002-commerzbank-exadata-performance-incident.md) | Commerzbank — Oracle Exadata 11 TB | Cold cache + скъпа read-side дедупликация след DR; HTTP 200, но клиентски timeout | Валидиран query window → write-side integrity; план за поетапна Oracle → PostgreSQL миграция | SQL/Oracle, Performance, Reliability, System Design, CDC vs dual-write |
| [003](003-skyro-temporal-saga-cancellation.md) | Skyro — Temporal Saga / payment holds | Workflow cancellation не е external hold cancellation; release-before-hold race | Compensation before activity, detached scopes, cancellation checks + reconciliation | Temporal, Saga, Idempotency, Fintech, Distributed Systems |
| [004](004-port-of-rotterdam-modular-monolith.md) | Port of Rotterdam — microservice overload | 90+ services, 5 teams, responsibilities crossing multiple deployables | EventStorming/DDD boundaries, Spring Modulith, capability-by-capability migration | Strategic DDD, EventStorming, Spring Modulith, Legacy Modernization |

## Какво изискваме от всеки казус

1. **Източник и контекст:** кой, каква система, какъв мащаб — без измислени числа.
2. **Symptom → Root Cause:** конкретна времева последователност на проблема.
3. **Failed mitigations:** какво изглежда разумно, но не решава първопричината.
4. **Alternatives & Trade-offs:** реалните избори и тяхната цена.
5. **Chosen solution:** архитектура, data flow, failure boundaries.
6. **Production traps:** какво може да се счупи след „поправката“.
7. **Recognition & runbook:** сигнали, metrics, диагностика, как да реагираме.
8. **When to reuse / not reuse:** достатъчно точни предпоставки.
9. **Reproduction ideas:** свързани налични тестове или изрично маркирани идеи.
10. **Original sources:** видео, официална документация, implementation references.

## Откриване и свързване

- **Kafka / Outbox / Idempotency / CDC:** [Case 001](001-decathlon-kafka-business-table-cdc.md) и [изпълнимата ни Outbox/Inbox лаборатория](../../spring/messaging/outbox-inbox/README.md).
- **Temporal / Saga / payment hold:** [Case 003](003-skyro-temporal-saga-cancellation.md) — cancellation pitfalls, late hold, detached compensation и reconciliation.
- **Oracle / Exadata / SQL cold cache / миграция:** [Case 002](002-commerzbank-exadata-performance-incident.md) — диагностичен runbook, read vs write deduplication, canary и ограничения на CDC.
- **Microservice overload / modular monolith:** [Case 004](004-port-of-rotterdam-modular-monolith.md) — discovery на domain boundaries, incremental migration и връзка с [Spring Modulith lab](../../spring/architecture/strategic-ddd/modulith-lab/README.md).
- **Сравни 001 ↔ 002:** при Decathlon е избран **CDC**, а при Commerzbank той не е приемлив в конкретната среда и е предложен **app-level dual-write**; всеки избор има различни consistency и operational рискове.
- За бъдещи примери използваме кратки имена, последователен номер и searchable ключови думи.
- Общият [учебен roadmap](../learning/ROADMAP.md) определя кога да задълбочаваме фундаментите. Case studies остават достъпни като справочник независимо от текущата диагностична сесия.
