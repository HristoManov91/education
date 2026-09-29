# CQRS — Command Query Responsibility Segregation

Този модул показва CQRS в най-малката полезна форма:

> **write model и read model са различни, но могат да живеят в една Spring application и една database.**

Не започваме с microservices, Kafka, две databases или Event Sourcing.

Първо разбираме самото разделение.

---

# ВХОД В ТЕМАТА

## 1. Реалният казус

Имаме Order.

За write side ни интересуват:

- invariants;
- lifecycle;
- optimistic version;
- normalized child entities;
- transactional consistency;
- business operations като `markPaid()`.

За read side UI-то иска:

```text
order reference
customer
status
total
item count
ready-to-display label
```

Ако използваме един и същ object model за всичко, той постепенно трябва да обслужва две различни цели.

---

# 2. CRUD е правилният default

CQRS не означава:

> CRUD е лош.

За simple application:

```text
Entity
→ Repository
→ CRUD
```

може да е най-добрият design.

Martin Fowler изрично предупреждава, че CQRS добавя значителна complexity и за повечето systems не трябва да се прилага без реална причина.

Затова този lesson започва и завършва с въпроса:

> **Каква concrete asymmetry между writes и reads оправдава отделните models?**

---

# 3. CQS vs CQRS

Имената са сходни, но не са едно и също.

## Command Query Separation (CQS)

На method level:

```text
Command method
→ променя state
→ не е query

Query method
→ връща data
→ не променя observable state
```

Пример:

```java
order.markPaid();        // command
order.getStatus();       // query
```

## CQRS

Отива по-далеч:

```text
write responsibility
→ собствен model

read responsibility
→ различен model
```

Не просто различни methods върху един object.

---

# 4. Основният CQRS mental model

```text
                 Commands
                    ↓
              WRITE MODEL
         invariants / transactions
                    ↓
                database
                    ↓
               projection
                    ↓
               READ MODEL
                    ↓
                  Queries
```

В по-напреднал вариант write и read sides могат да имат различни stores.

Но това е deployment/storage optimization, не definition на CQRS.

---

# 5. Нашият write model

[`OrderWriteEntity.java`](./src/main/java/bg/hristomanov/education/cqrs/write/OrderWriteEntity.java)

Write side е optimized за consistency:

```text
OrderWriteEntity
├→ reference
├→ customerId
├→ status
├→ totalAmount
├→ @Version
└→ List<OrderLineEntity>
```

Той има behavior:

```java
order.markPaid();
```

и invariant:

```text
only NEW order can become PAID
```

`OrderLineEntity` валидира quantity и price.

Това е business model, не UI view.

Association-ът към lines е `LAZY` и `spring.jpa.open-in-view=false`. Lab test-овете не четат lazy graph-а след края на transaction-а; [`OrderWriteInspectorService.java`](./src/main/java/bg/hristomanov/education/cqrs/write/OrderWriteInspectorService.java) прави диагностичния snapshot в explicit `readOnly` transaction. Това е умишлено — не използваме EAGER/OSIV, за да маскираме persistence-boundary problem.

---

# 6. Commands са business intent

[`CreateOrderCommand.java`](./src/main/java/bg/hristomanov/education/cqrs/write/CreateOrderCommand.java)

Command-ът казва:

```text
Create this order
```

а не:

```text
set row column X to Y
```

Command side:

[`OrderCommandService.java`](./src/main/java/bg/hristomanov/education/cqrs/write/OrderCommandService.java)

```text
CreateOrderCommand
→ validate/construct aggregate
→ save write model
→ update or schedule read projection
→ return order ID
```

Command handler-ът не връща rich query DTO.

Това държи write responsibility отделна.

---

# 7. CQRS Command ≠ GoF Command pattern

Думата Command се използва в два различни контекста.

## CQRS command

Represent-ва intent за state change:

```text
CreateOrder
PayOrder
CancelOrder
```

## GoF Command

Encapsulates executable action as object, често за queue/history/undo/invoker.

Може да има overlap в implementation shape, но intent-ът е различен.

Не всяко CQRS system има generic Command Bus.

Нашият lab умишлено няма такъв.

---

# 8. Нашият read model

[`OrderSummaryProjection.java`](./src/main/java/bg/hristomanov/education/cqrs/read/OrderSummaryProjection.java)

Read side е denormalized и query-oriented:

```text
orderId
reference
customerId
status as String
totalAmount
itemCount
displayLabel
sourceVersion
projectedAt
```

Забележи, че той:

- няма `markPaid()`;
- няма order lines като child entities;
- няма business invariants;
- има предварително изчислени полета за query use case.

Това е feature, не duplication bug.

---

# 9. Query side

[`OrderQueryService.java`](./src/main/java/bg/hristomanov/education/cqrs/read/OrderQueryService.java)

```text
GET order summary
→ read projection repository
→ OrderSummaryDto
```

Query side е `readOnly=true` и няма нужда да load-ва write aggregate-а.

Read DTO:

[`OrderSummaryDto.java`](./src/main/java/bg/hristomanov/education/cqrs/read/OrderSummaryDto.java)

е shaped за consumer-а.

Spring Data JPA projections са друг възможен инструмент за query-specific shapes, без да материализираме отделна table.

---

# 10. Нива на CQRS

Полезно е да мислим за CQRS като spectrum.

## Ниво 0 — CRUD

```text
one model
read + write
```

Най-просто.

## Ниво 1 — separate interfaces/models, same database

```text
Write Model → same DB ← Read Model
```

Това е foundational CQRS.

## Ниво 2 — materialized read projection, same database

Нашият lab:

```text
cqrs_orders + cqrs_order_lines
            ↓ projection
cqrs_order_summary
```

## Ниво 3 — separate read store

```text
Write DB
→ events
→ Read DB / search engine / cache
```

Тук вече имаме eventual consistency и distributed synchronization problem.

---

# README → код

| Концепция | Код | Доказателство |
| --- | --- | --- |
| Write aggregate | [`OrderWriteEntity.java`](./src/main/java/bg/hristomanov/education/cqrs/write/OrderWriteEntity.java) | [`CqrsLabTest.java`](./src/test/java/bg/hristomanov/education/cqrs/CqrsLabTest.java) |
| Command | [`CreateOrderCommand.java`](./src/main/java/bg/hristomanov/education/cqrs/write/CreateOrderCommand.java) | same |
| Command service | [`OrderCommandService.java`](./src/main/java/bg/hristomanov/education/cqrs/write/OrderCommandService.java) | same |
| Read model | [`OrderSummaryProjection.java`](./src/main/java/bg/hristomanov/education/cqrs/read/OrderSummaryProjection.java) | same |
| Query DTO | [`OrderSummaryDto.java`](./src/main/java/bg/hristomanov/education/cqrs/read/OrderSummaryDto.java) | same |
| Query service | [`OrderQueryService.java`](./src/main/java/bg/hristomanov/education/cqrs/read/OrderQueryService.java) | same |
| Projector | [`OrderProjectionWriter.java`](./src/main/java/bg/hristomanov/education/cqrs/projection/OrderProjectionWriter.java) | same |
| Deferred projection queue | [`ProjectionRefreshRequest.java`](./src/main/java/bg/hristomanov/education/cqrs/projection/ProjectionRefreshRequest.java) | same |
| Projection worker | [`OrderProjectionWorker.java`](./src/main/java/bg/hristomanov/education/cqrs/projection/OrderProjectionWorker.java) | same |
| Interactive API | [`CqrsController.java`](./src/main/java/bg/hristomanov/education/cqrs/api/CqrsController.java) | [`cqrs-demo.http`](./http/cqrs-demo.http) |

---

# 11. Synchronous projection

Първият mode е:

```text
command transaction
→ update write model
→ update read projection
→ COMMIT together
```

При create:

```text
OrderWriteEntity
+
OrderSummaryProjection
```

се commit-ват в една DB transaction.

Така read side няма lag.

Тест:

`synchronousCqrsUsesDifferentWriteAndReadModelsWithoutConsistencyLag()`

Доказва:

```text
write model:
2 OrderLine entities
normalized business structure

read model:
itemCount = 3
displayLabel ready for UI
no child entities
```

Разделили сме models без distributed complexity.

---

# 12. Цена на synchronous projection

Този вариант е прост, но има coupling:

```text
write latency
включва
read projection write latency
```

Ако имаме 12 read projections:

```text
command
→ write aggregate
→ projection A
→ projection B
→ projection C
...
```

command transaction може да стане тежка.

Затова asynchronous projection понякога е по-подходяща.

---

# 13. Deferred projection

Lab mode:

```text
ProjectionMode.DEFERRED
```

прави:

```text
write model update
+
durable ProjectionRefreshRequest
→ COMMIT

по-късно:

Projection Worker
→ rebuild read model
```

Това е учебен local queue в същата DB.

Не го представяме като message broker.

Целта е да направим eventual consistency **видима и тестируема**.

---

# 14. Eventual consistency window

Тест:

`deferredProjectionCreatesAnIntentionalStaleWindowThenCatchesUp()`

След command:

```text
WRITE DB:
order exists ✅

READ MODEL:
projection missing ❌
```

Query в този момент не може да върне new order.

След:

```text
projectionWorker.refreshNext()
```

read side наваксва.

Това е eventual consistency.

---

# 15. Stale read, не само missing read

Още по-важният test:

`deferredUpdateCanMakeReadModelTemporarilyStale()`

Start:

```text
write = NEW version 0
read  = NEW version 0
```

Command:

```text
markPaid
projectionMode = DEFERRED
```

След write commit:

```text
write = PAID version 1
read  = NEW  version 0
```

След projector:

```text
write = PAID version 1
read  = PAID version 1
```

`sourceVersion` прави lag-а observable.

---

# 16. Read-your-writes problem

При eventual CQRS user може да направи:

```text
POST /pay
→ success

immediately GET /order
→ old status NEW
```

Това не е задължително bug.

То е consistency contract.

Възможни strategies:

- UI optimistic update;
- command response връща minimal new state/version;
- wait until read projection reaches version;
- query write model за critical immediate confirmation;
- session consistency token;
- synchronous projection за конкретни use cases.

Трябва да е explicit, а не surprise.

---

# 17. CQRS не означава separate databases

Microsoft Architecture guidance изрично описва foundational вариант:

```text
separate read/write models
+
one shared data store
```

Different data stores са по-advanced option, когато има нужда от:

- independent scaling;
- different storage technology;
- read replicas;
- materialized views;
- search engine;
- radically different read schema.

Не започваме оттам по навик.

---

# 18. Separate read store

Advanced shape:

```text
Command
→ Write Model
→ Write DB
→ Integration Event
→ Read Model Projector
→ Read DB
→ Query
```

Тук вече важат всички теми от предишните modules:

- Domain Events;
- Transactional Outbox;
- Idempotent Consumer;
- retries;
- ordering;
- schema evolution.

CQRS не ги решава автоматично.

---

# 19. CQRS + Outbox

При separate read store:

```text
write DB transaction:
update aggregate
+
insert outbox event
COMMIT
```

После:

```text
Outbox Relay
→ event
→ Read Model Consumer
→ idempotent projection update
```

Това е естественото свързване с Outbox/Inbox lab-а.

---

# 20. CQRS + Domain Events

Domain Event:

```text
OrderPaid
```

може да бъде source за:

- internal synchronous projection;
- integration event mapping;
- outbox event;
- separate read model update.

Но CQRS не изисква events.

Можем да имаме CQRS с direct synchronous projection, както първият mode в lab-а.

---

# 21. CQRS НЕ е Event Sourcing

Това е най-честото объркване.

## CQRS

```text
different model for writes
different model for reads
```

Source of truth може да е normal current-state relational tables.

## Event Sourcing

```text
events are source of truth
current state is rebuilt from event stream
```

Можем да имаме:

```text
CQRS without Event Sourcing ✅
Event Sourcing + CQRS ✅
```

Не са пакет.

Event Sourcing ще е отделен advanced module.

---

# 22. CQRS не означава messaging

Нашият synchronous mode няма broker.

```text
command service
→ write tables
→ read projection table
```

Това пак е CQRS, защото models/responsibilities са отделени.

Messaging се появява, когато separation/distribution го изисква.

---

# 23. Query model може да бъде DTO projection без отделна table

Понякога не ни трябва materialized read model.

Spring Data JPA позволява dedicated projection return types.

Можем да имаме:

```text
write entities
+
query-specific DTO projection
from same normalized tables
```

Това дава read/write model separation на code/API level, без data duplication.

Отделна materialized projection има повече смисъл, когато:

- query е скъп;
- joins са сложни;
- read volume е висок;
- latency трябва да е ниска;
- query shape е много различен.

---

# 24. Denormalization е trade-off

Read projection може да дублира:

```text
customer name
totals
counts
display labels
derived flags
```

Benefit:

```text
simple fast query
```

Cost:

```text
synchronization
staleness
rebuild
extra storage
```

Денормализацията не е free performance.

---

# 25. Rebuilding read models

Ако read model е derived state, трябва да знаем:

> От какво можем да го rebuild-нем?

При current-state write DB:

- full scan;
- batch rebuild;
- recompute projection.

При Event Sourcing:

- replay event stream.

В production е полезно read model да се третира като rebuildable projection, когато това е реално вярно.

---

# 26. Projection version

Lab read model пази:

```text
sourceVersion
```

Write model използва:

```java
@Version
```

Така можем да диагностицираме:

```text
write version = 8
read version = 6
→ projection lag = 2 versions
```

При distributed system version/sequence може също да помогне за:

- stale-event rejection;
- ordering detection;
- read-your-writes waiting.

---

# 27. Commands не трябва да са generic CRUD setters

По-смислено:

```text
PayOrder
ReserveInventory
ApproveProtocol
```

от:

```text
UpdateOrderStatus
SetProtocolField
```

когато domain operation има business semantics.

Command side е мястото за:

- validation;
- invariants;
- authorization policy;
- concurrency checks;
- transaction boundary.

---

# 28. Queries не трябва да съдържат domain mutation

Query:

```text
find customer orders
```

не трябва като side effect да:

- mark order seen;
- increment business counter;
- create missing domain state;
- change lifecycle.

Ако има state change, това conceptually е command.

Technical metrics/logging/cache може да са separate infrastructure effects, но business semantics трябва да останат ясни.

---

# 29. CQRS + security

Read/write separation може да даде отделни permission models:

```text
query permission
≠
command permission
```

Пример:

```text
many users can view order summary
only finance role can PayOrder
```

Не е главната причина за CQRS, но separation може да направи policy boundary по-ясна.

---

# 30. CQRS + scaling

Read-heavy system:

```text
reads 100x writes
```

може да scale-ва read side отделно:

- replicas;
- cache;
- search index;
- denormalized store.

Write side може да остане optimized за consistency.

Но ако system има 20 requests/minute, това вероятно не оправдава distributed CQRS infrastructure.

---

# 31. CQRS + Specification / QueryDSL

Предишният Specification lab решава:

```text
как да композирам complex dynamic predicates
```

Това може да е query-side implementation technique в CQRS.

Например:

```text
OrderQueryService
→ QueryDSL
→ read projection/search index
```

CQRS определя responsibility boundary; QueryDSL определя query construction technique.

---

# 32. CQRS + Saga

Saga command side може да използва CQRS-style commands:

```text
ReservePaymentCommand
ReserveInventoryCommand
```

Query side може отделно да предоставя:

```text
OrderProcessingStatusView
```

Това е полезно, защото long-running workflow write model и UI status view често имат много различен shape.

---

# 33. Кога CQRS е добър fit

Сигнали:

- write domain има сложни invariants;
- reads имат radically different shape;
- read/write load е силно асиметричен;
- read side иска различна storage technology;
- query performance изисква materialized views;
- task-based UI;
- отделни security/scaling requirements;
- много различни query projections върху един write model.

---

# 34. Кога CQRS е overengineering

Не го използвай автоматично за:

- simple CRUD admin screen;
- малък domain без complex business rules;
- еднакъв read/write shape;
- нисък load;
- когато екипът няма нужда от eventual consistency complexity.

Martin Fowler предупреждава, че CQRS лесно се превръща в significant drag върху productivity, ако се приложи в неподходящ domain.

Pattern-ът трябва да се използва само там, където separation-ът носи реална стойност.

---

# Какво доказват тестовете

[`CqrsLabTest.java`](./src/test/java/bg/hristomanov/education/cqrs/CqrsLabTest.java)

## Synchronous CQRS

```text
write aggregate
→ normalized lines + invariants

read projection
→ itemCount + displayLabel

same transaction
→ no lag
```

## Deferred projection

```text
write commit
→ read missing
→ worker
→ read catches up
```

## Stale update

```text
write PAID version 1
read NEW version 0
→ worker
→ read PAID version 1
```

## Invalid command

```text
invalid quantity
→ no write state
→ no read state
```

---

# Как да стартираме

```bash
mvn -pl spring/architecture/cqrs -am test
```

Стартиране:

```bash
mvn -pl spring/architecture/cqrs spring-boot:run
```

HTTP scenarios:

- [`http/cqrs-demo.http`](./http/cqrs-demo.http)

---

# Mental model за запомняне

1. **CQRS разделя models/responsibilities за commands и queries.**
2. **CQRS не изисква microservices, messaging, separate databases или Event Sourcing.**
3. **CRUD остава default за simple domains.**
4. **Write model е optimized за invariants/transactions.**
5. **Read model е optimized за query shape.**
6. **Separate read store въвежда synchronization и eventual consistency.**
7. **Read-your-writes semantics трябва да са explicit.**
8. **Denormalization купува query simplicity срещу synchronization cost.**
9. **Projection version прави lag-а observable.**
10. **CQRS се прилага локално там, където ползата оправдава complexity-то.**

---

# Code-review checklist

```text
[ ] Защо един model не е достатъчен?
[ ] Read и write shapes реално различни ли са?
[ ] Commands business intent ли изразяват?
[ ] Queries guaranteed ли са да не mutate-ват business state?
[ ] Read model materialized ли трябва да бъде или DTO projection стига?
[ ] Projection synchronous ли е или eventual?
[ ] Read-your-writes contract ясен ли е?
[ ] Как се rebuild-ва read model?
[ ] Има ли source version / ordering strategy?
[ ] Ако stores са separate, има ли Outbox + idempotent projector?
[ ] Денормализираните данни имат ли ясна refresh ownership?
[ ] CQRS приложен ли е само в подходящ bounded context?
[ ] Event Sourcing добавено ли е по реална причина, а не защото 'върви с CQRS'?
```

---

# Упражнения

1. Добави `CancelOrderCommand`.
2. Добави отделен `CustomerOrderListProjection` с още по-малко полета.
3. Замени materialized table за един query с Spring Data DTO projection.
4. Добави `projectionLag = writeVersion - sourceVersion` endpoint.
5. Добави worker batch refresh.
6. Добави duplicate refresh requests и докажи idempotent projection update.
7. Изнеси read model в отделна H2 datasource.
8. После използвай Domain Event + Outbox вместо local refresh table.
9. Добави read-your-writes wait по version token.
10. След това сравни с Event Sourcing implementation.

---

# Оригинални източници

- Martin Fowler — CQRS: https://martinfowler.com/bliki/CQRS.html
- Microsoft Azure Architecture Center — CQRS Pattern: https://learn.microsoft.com/azure/architecture/patterns/cqrs
- Spring Data JPA — Projections: https://docs.spring.io/spring-data/jpa/reference/repositories/projections.html
- Greg Young — CQRS material, посочен като origin/reference и от Fowler.
- Предишни modules: Domain Events, Transactional Outbox, Idempotent Consumer, Saga.

---

# Изходен въпрос

Когато видиш един огромен model, използван едновременно за business updates, UI lists, reports и search, не казвай автоматично:

> трябва ни CQRS.

Питай:

> **Кои write invariants и кои read shapes реално се дърпат в различни посоки, и достатъчно ли е да отделим interfaces/DTO projections, или наистина имаме нужда от материализиран/отделен read model и неговата consistency цена?**
