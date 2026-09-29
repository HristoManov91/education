# Backend / Application Patterns Roadmap

Класическите GoF patterns са фундамент за object design, но backend engineer-ът ежедневно решава и по-големи проблеми:

- persistence boundaries;
- dynamic business rules;
- transactions;
- retries;
- duplicate requests;
- external service failures;
- distributed consistency;
- event delivery;
- read/write scaling.

Този roadmap е **следващото ниво след Design Patterns Atlas**.

Не всички долни понятия са GoF „design patterns“. Част от тях са enterprise/application/integration/distributed-systems patterns. Именно затова ги отделяме: проблемите и scale-ът са различни.

---

# Как ще учим и тези patterns

За всеки:

```text
реален production казус
→ naive решение
→ какво точно се чупи
→ pattern / mental model
→ Java/Spring реализация
→ failure cases
→ test / executable experiment
→ кога НЕ трябва да го използваме
→ code-review checklist
```

---

# Ниво 1 — най-полезни за ежедневен Spring backend

## 0. Specification + QueryDSL — ✅ реализиран

Executable lab:

- [Specification Pattern + QueryDSL](../persistence/specification-querydsl/README.md)

Покрива:
- dynamic optional filters;
- naive JPA Criteria baseline;
- Spring Data `Specification<T>`;
- reusable QueryDSL `BooleanExpression` predicates;
- generated Q-types;
- side-by-side integration tests;
- кога Specification е overengineering.

Следващата стъпка е да вържем това с Repository boundary и Unit of Work/JPA semantics.

---

## 1. Repository + Unit of Work / JPA — ✅ реализиран

Executable lab:

- [Repository + Unit of Work](../persistence/repository-unit-of-work/README.md)

Покрива:
- direct EntityManager baseline;
- domain-facing Repository contract;
- JPA adapter;
- persistence context;
- dirty checking;
- Identity Map semantics;
- flush vs commit;
- rollback after flush;
- detached entities;
- optimistic version field;
- кога Repository abstraction е излишна.

---

## Следващи теми в Ниво 1

Specification, Repository и Unit of Work вече са изнесени в executable labs по-горе. Следващата фундаментална тема е Dependency Injection, следвана от Anti-Corruption Layer.

## 4. Dependency Injection

Използваме го постоянно, но си струва отделен conceptual урок:

- dependency inversion;
- composition root;
- constructor injection;
- lifecycle/scope;
- why service locator is different;
- защо DI container ≠ „магия“.

Spring е excellent production context за този pattern/principle.

---

## 5. Anti-Corruption Layer

### Казус

Legacy/third-party model започва да изтича в нашия domain:

```text
external DTO
external enums
external error codes
external naming
       ↓
everywhere
```

### Решение

ACL boundary:

```text
Our domain
   ↓
Adapter / translator / ACL
   ↓
External model
```

Това надгражда Adapter от GoF на architectural level.

---

# Ниво 2 — reliability patterns

## 6. Idempotency — ✅ реализиран

Executable lab:

- [Idempotency — safe retries without duplicate side effects](../spring/reliability/idempotency/README.md)

Покрива:
- HTTP POST retries;
- idempotency key;
- request fingerprint;
- same-key/different-payload conflict;
- database unique constraint;
- check-then-act concurrency race;
- 8 concurrent duplicate requests;
- replay semantics;
- key retention/scope;
- external-side-effect limitations.

### Казус

Client timeout-ва и retry-ва:

```text
POST /payments
POST /payments   ← същата business операция
```

Без idempotency може да имаме две плащания.

### Lab

- idempotency key;
- DB uniqueness;
- processing state;
- replay на същия response;
- race между два concurrent duplicate requests.

Това е **много висок приоритет**.

---

## 7. Retry + Timeout + Circuit Breaker + Bulkhead — ✅ реализиран

Executable lab:

- [Fault Tolerance — Retry + Timeout + Circuit Breaker + Bulkhead](../spring/reliability/fault-tolerance/README.md)

Покрива:
- transient vs permanent failure classification;
- bounded retry;
- timeout/cancellation;
- Circuit Breaker CLOSED/OPEN/HALF_OPEN;
- slow-call/failure-rate semantics;
- Semaphore Bulkhead;
- Virtual Threads vs downstream capacity;
- composition order;
- per-attempt timeout vs global deadline;
- Circuit Breaker inside/outside Retry;
- fallback and observability trade-offs.

---

Подробните Retry/Timeout/Circuit Breaker/Bulkhead казуси вече са изнесени в executable lab-а по-горе.

# Ниво 3 — database + event consistency

## 11. Transactional Outbox + Inbox / Idempotent Consumer — ✅ реализирани

Executable lab:

- [Transactional Outbox + Idempotent Consumer / Inbox](../spring/messaging/outbox-inbox/README.md)

Покрива:
- direct DB + broker dual-write failure windows;
- atomic business row + outbox row transaction;
- Polling Publisher relay;
- broker publish success + missing outbox acknowledgement;
- duplicate publication with stable event ID;
- Inbox / processed-message table;
- atomic inbox claim + business effect;
- concurrent duplicate consumers;
- consumer rollback + safe redelivery;
- event ordering vs duplicate handling;
- multiple relay instances / claim strategies;
- PostgreSQL / Oracle `SKIP LOCKED` considerations;
- CDC / Debezium Outbox Event Router;
- retention, poison messages and schema evolution.

Outbox и Inbox са реализирани заедно, защото producer-side at-least-once publication логично изисква consumer-side idempotency.

---

# Ниво 4 — distributed business transactions

## 13. Saga — ✅ реализиран

Executable lab:

- [Saga Pattern — orchestration, compensation и durable workflow state](../spring/messaging/saga/README.md)

Покрива:
- отделни local transactions;
- orchestration;
- choreography comparison;
- reverse-order compensation;
- persistent saga state;
- compensation failure;
- compensation retry;
- idempotent participant operations;
- Saga + Outbox/Inbox;
- timeout/unknown outcome;
- intermediate consistency states.

---

# Ниво 5 — read/write architecture

## 14. CQRS — ✅ реализиран

Executable lab:

- [CQRS — separate write/read models, synchronous и eventual projections](../spring/architecture/cqrs/README.md)

Покрива:
- CQS vs CQRS;
- CRUD като default;
- normalized write aggregate;
- denormalized query projection;
- business-intent commands;
- query-only DTO model;
- synchronous read projection в една transaction;
- deferred projection и intentional stale-read window;
- read-your-writes проблем;
- projection source version / lag;
- one database vs separate read store;
- CQRS without messaging;
- CQRS without Event Sourcing;
- Spring Data DTO projections като по-лека алтернатива;
- Outbox/Domain Events/Saga integration.

---

## 15. Event Sourcing — ✅ реализиран

Executable lab:

- [Event Sourcing — append-only streams, replay, projections, concurrency и snapshots](../spring/architecture/event-sourcing/README.md)

Покрива:
- events като authoritative source of truth;
- event notification vs Event Sourcing;
- stream per aggregate;
- stream version vs global position;
- command -> replay -> decide -> append;
- invalid command = no event;
- historical state reconstruction;
- expected-version optimistic concurrency;
- append-only relational Event Store;
- CQRS projection + checkpoint;
- projection deletion/rebuild;
- snapshots като replay optimization;
- event schema versioning/upcasting;
- deterministic replay;
- event immutability/corrections;
- GDPR/PII trade-offs;
- Event Store vs broker/audit log/outbox;
- Kurrent/EventStoreDB expected-revision semantics.

---

# Ниво 6 — domain/application modeling

## 16. Domain Event — ✅ реализиран

Executable lab:

- [Domain Events — aggregate events, transaction phases и integration boundaries](../spring/events/domain-events/README.md)

Покрива:
- Spring Data `AbstractAggregateRoot`;
- `@DomainEvents` semantics;
- dirty checking without repository save;
- synchronous `@EventListener`;
- `@TransactionalEventListener` AFTER_COMMIT / AFTER_ROLLBACK;
- REQUIRES_NEW reaction after commit;
- Domain vs Application vs Integration Event;
- Outbox connection;
- Spring Modulith Event Publication Registry.

---

## 17. Null Object

Полезен, когато repeated null checks представят стабилно „no behavior“ състояние.

Но Java `Optional`, sealed types или explicit domain states често са по-ясни.

Ще го учим като **инструмент**, не като default заместител на null.

---

## 18. Data Mapper / DTO Mapper

Особено полезно за граници:

```text
DB entity
≠
domain model
≠
API DTO
≠
external DTO
```

Ще разгледаме кога отделянето е важно и кога mapping layer-ът става meaningless boilerplate.

---

# Ниво 7 — architecture patterns

## 19. Ports & Adapters / Hexagonal Architecture — ✅ реализиран

Executable lab:

- [Ports & Adapters / Hexagonal Architecture](../spring/architecture/hexagonal-architecture/README.md)

Покрива:
- original Cockburn inside/outside model;
- driving vs driven adapters;
- ports named by purpose, not technology;
- framework-free Java core;
- Maven-enforced dependency direction;
- REST driving adapter;
- JPA driven adapters;
- separate domain and persistence models;
- composition root with Spring `@Bean`;
- transaction decorators outside the core;
- isolated core tests with in-memory adapters;
- Spring/JPA integration test;
- layered vs hexagonal;
- Onion/Clean Architecture comparison;
- when hexagonal architecture is overengineering.

---

# Приоритет за реализация

Моят proposed order за следващите executable labs:

```text
1. Specification + QueryDSL ✅
2. Repository + Unit of Work / JPA ✅
3. Idempotency ✅
4. Retry + Timeout + Circuit Breaker + Bulkhead ✅
5. Transactional Outbox + Idempotent Consumer ✅
6. Saga ✅
7. Domain Events ✅
8. CQRS ✅
9. Ports & Adapters ✅
10. Event Sourcing ✅
```

Този ред е избран така, че всяка тема да стъпва върху предишната.

---

# Какво трябва да можеш след целия curriculum

Не просто:

> „Знам какво е Circuit Breaker.“

А:

> „Този endpoint прави неидемпотентна операция. Ако сложа Retry преди да реша duplicate semantics, мога да създам двойно плащане.“

И не:

> „CQRS е pattern с commands и queries.“

А:

> „Тук read model-ът има различни shape/performance нужди от transactional write model-а; CQRS може да оправдае разделянето, но в тази система една база и отделни models са достатъчни.“

Това е нивото, към което целим проекта.
