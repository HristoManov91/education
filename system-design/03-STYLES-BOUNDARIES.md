# SD-06/07 — Software architecture styles, boundaries and ownership

> **P1 · architecture decision workshop**. Основи: [Martin Fowler — Monolith First](https://martinfowler.com/bliki/MonolithFirst.html), [Cockburn — Ports & Adapters](https://alistair.cockburn.us/hexagonal-architecture/), [Strategic DDD executable lab](../spring/architecture/strategic-ddd/README.md).

## 1. Реалният проблем

Един екип поддържа търсене и подписване на документи, отчетност и уведомления. При ново поле често се променят няколко classes и SQL projection. Архитектурен отговор „разделяме по таблици на 6 микросървиса“ увеличава latency, separate deployments и консистентността става distributed problem.

Не бъркай **бизнес граници** с:
- таблици (`USER_SERVICE`, `DOCUMENT_SERVICE` само заради entity name);
- repository package;
- екип или Kubernetes pod;
- HTTP API;
- желание всичко да е „cloud native“.

## 2. Четири варианта и честни trade-offs

| Стил | Какъв проблем решава и кога | Кога НЕ | Печалба | Цена / алтернатива |
| --- | --- | --- | --- | --- |
| Layered monolith (controller→service→repository) | умерен CRUD, малка система/екип | когато dependencies са spaghetti и multiple business capabilities co-change chaotically | simplicity, local transactions, fast debug | package coupling; package by feature and boundaries first |
| Modular monolith | бизнес capabilities имат отделна еволюция, но не изискват независими deployments | когато модулите само препращат чрез 7 абстракции | explicit boundaries, simple deployment, one DB transaction where necessary | discipline, CI ArchUnit/Spring Modulith, ограничаване на imports |
| Microservices | организационна и технологична независимост, distinct scaling/release patterns с реални boundaries | по подразбиране за малък проект; shared DB или distributed transactions като „скрита“ глобална транзакция | independent deployments/fault isolation **ако са реализирани** | network failure, ops, tracing, consistency, versioning |
| Event-driven integrations | producer/consumer temporal decoupling, many independent reactions | business invariant requires immediate acknowledgment | buffering, fault isolation if durable | duplicates, retry/DLQ, schema evolution, eventual consistency |

Две отделни bounded contexts могат да са в един deployable. Два services могат да споделят един deployment lifecycle. **Модул ≠ pod ≠ bounded context.**

## 3. Decision sequence

```text
Business capabilities / who owns decisions and data?
  → change history / frequently coordinated changes?
  → invariants requiring one local transaction?
  → team/release cadence & operational maturity?
  → load growth / independent scaling evidence?
  → choose the simplest deployable shape satisfying ALL constraints
```

Пример при reports:
- Document write operations: strict business invariants + signature status.
- Search & analysis: query optimized projections; stale reads могат да бъдат позволени само ако са изрично съгласувани.
- Exports: background worker с queue само ако синхронната заявка не може да завърши в допустимото време.
- Notifications: отделна reaction, която не трябва да определя дали core transaction е валидна.

## 4. Direction of dependencies vs topology

Разработваме long-lived domain logic:

```text
HTTP/gRPC adapter → application use case → domain
SQL adapter       ──implements──→ domain-owned repository port
```

Hexagonal е **code dependency architecture**; microservices е **deployment + communication** архитектурен избор. Можеш да имаш hexagonal monolith, layered microservice или modular monolith. Не е правилно да приемаме „hexagonal = microservices“.

Свързани executable modules:
- [Hexagonal Architecture](../spring/architecture/hexagonal-architecture/README.md)
- [DDD / Context Mapping / Spring Modulith](../spring/architecture/strategic-ddd/README.md)
- [Architecture Decision-Making / overengineering](../spring/architecture/hexagonal-architecture/P2-ARCHITECTURE-DECISIONS.md)

## 5. Decision exercise — architect's review

Система има 3 feature modules, един екип, една Oracle база и 6 deploys месечно. При peak 300 RPS CPU е нисък, но 90% от failures са DB locks. Иска се разделяне на 10 services.

**Твоят избор:** какво ще измериш първо и защо microservices НЕ решават lock contention автоматично? Напиши:
1. simple alternative (SQL lock diagnosis + bounded transactions),
2. structural alternative (feature module boundaries),
3. trigger за бъдещо split,
4. 2 metrics/tests, които ще опровергаят решението.

Не обявявай latency/throughput improvement без load-test proof.
