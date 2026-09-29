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

## 6. Idempotency

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

## 7. Retry

Retry е правилен само за подходящи transient failures.

Ще покрием:

- max attempts;
- exponential backoff;
- jitter;
- retryable vs non-retryable errors;
- idempotency;
- retry storm.

Важно правило:

> Retry без idempotency/failure classification може да направи проблема по-лош.

---

## 8. Timeout

Без timeout caller-ът предава собствения си resource budget на downstream service-а.

Ще разглеждаме:

- connect timeout;
- read/request timeout;
- global deadline;
- cascading latency.

Това ще се върже и с вече направения Loom/Structured Concurrency material.

---

## 9. Circuit Breaker

### Казус

Downstream service fail-ва/timeout-ва.

Без protection:

```text
request
→ wait
→ timeout
request
→ wait
→ timeout
...
```

Circuit Breaker временно спира calls към очевидно unhealthy dependency.

Ще разграничим:

- CLOSED;
- OPEN;
- HALF_OPEN;
- fallback;
- recovery probes.

И най-важното: Circuit Breaker **не поправя dependency-то**.

---

## 10. Bulkhead

Една failing dependency не трябва да изяде всички:

- threads;
- connections;
- queue capacity;
- concurrency permits.

Това ще се върже директно с bounded-concurrency lab-а, който вече имаме при Virtual Threads.

---

# Ниво 3 — database + event consistency

## 11. Transactional Outbox

Един от най-важните distributed backend patterns.

### Казус

```text
BEGIN DB
save order
COMMIT

publish Kafka event
   💥 process crashes
```

DB има order-а, Kafka няма event-а.

Обратният ред също е проблем.

### Pattern

В една DB transaction:

```text
save order
save outbox_event
COMMIT
```

После отделен publisher доставя outbox records.

### Lab

Тук ще направим реален PostgreSQL/Oracle-oriented пример и ще разгледаме:

- polling publisher;
- CDC;
- retries;
- duplicate event delivery;
- idempotent consumers.

---

## 12. Inbox / Idempotent Consumer

Outbox решава producer side.

Consumer-ът трябва да приема, че event може да пристигне повече от веднъж.

```text
eventId already processed?
→ yes: ignore/replay safe result
→ no: process + record id
```

---

# Ниво 4 — distributed business transactions

## 13. Saga

### Казус

Order flow:

```text
payment
→ inventory
→ shipping
```

Нямаме една ACID transaction през три services.

Saga моделира:

- local transactions;
- sequence;
- compensating actions.

### Ще сравним

- choreography;
- orchestration.

Тук Mediator/Observer знанията ще помогнат, но Saga има distributed failure/durability semantics и е отделен architectural pattern.

---

# Ниво 5 — read/write architecture

## 14. CQRS

### Казус

Write model и complex reporting/search model имат коренно различни нужди.

CQRS разделя:

```text
Commands / Write Model

Queries / Read Model
```

Но не означава автоматично:

- microservices;
- Kafka;
- Event Sourcing;
- две databases.

Ще започнем от най-малката разумна версия.

---

## 15. Event Sourcing

Това ще е по-късен advanced module.

State не се пази само като latest snapshot:

```text
AccountOpened
MoneyDeposited
MoneyWithdrawn
...
→ replay
→ current state
```

Важно е първо да разбираме:

- Domain Events;
- Outbox;
- Idempotency;
- CQRS.

Event Sourcing не е „по-модерна база“.

---

# Ниво 6 — domain/application modeling

## 16. Domain Event

Event за вече случил се business fact:

```text
OrderPlaced
PaymentCaptured
ProtocolConfirmed
```

Ще разграничим:

- domain event;
- application event;
- integration event.

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

## 19. Ports & Adapters / Hexagonal Architecture

Това ще обедини много от вече наученото:

```text
          REST adapter
               ↓
external → port → application/domain ← port ← persistence
               ↑
          message adapter
```

Основната идея:

> core business logic не трябва да зависи от infrastructure details.

Ще го сравним с:

- layered architecture;
- clean architecture;
- onion architecture.

Без догматично „всяка система трябва да е hexagonal“.

---

# Приоритет за реализация

Моят proposed order за следващите executable labs:

```text
1. Specification + QueryDSL ✅
2. Repository + Unit of Work / JPA ✅
3. Idempotency
4. Retry + Timeout + Circuit Breaker + Bulkhead
5. Transactional Outbox + Idempotent Consumer
6. Saga
7. Domain Events
8. CQRS
9. Ports & Adapters
10. Event Sourcing
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
