# Event Sourcing — events as the source of truth

Това е advanced module-ът, който затваря основната последователност:

```text
Domain Events
→ CQRS
→ Event Sourcing
```

Но първото правило е:

> **Event Sourcing не е default architecture и не е "по-модерен CRUD".**

Това е фундаментална промяна в начина, по който пазим authoritative state.

---

# ВХОД В ТЕМАТА

## 1. Traditional current-state persistence

При типичен CRUD/JPA model пазим последното състояние:

```text
ACCOUNT
----------------
id       ACC-1
balance  130.00
currency EUR
```

Историята:

```text
+150 salary
-20 groceries
```

вече не е необходима, за да прочетем current balance.

Тя може да съществува в audit table, но source of truth за account state е current row.

---

# 2. Event Sourcing обръща source of truth

Event Sourcing пази:

```text
stream ACC-1

v0 AccountOpened
v1 MoneyDeposited  +150
v2 MoneyWithdrawn   -20
```

Current state:

```text
balance = 130
```

не е authoritative row.

Той е:

> **derived working state, получен чрез replay на authoritative event stream-а.**

Това е същината на pattern-а.

Fowler формулира Event Sourcing като съхраняване на всички промени в application state като sequence of events и възможност state-ът да бъде реконструиран от тях.

---

# 3. Event Notification ≠ Event Sourcing

Това е критично разграничение.

## Event Notification

```text
UPDATE order.status = PAID
→ publish OrderPaid notification
```

Source of truth:

```text
orders table
```

Event-ът уведомява.

## Event Sourcing

```text
append OrderPaid
→ derive current order state
```

Source of truth:

```text
event stream
```

Event-ът не е просто message след update-а.

Event-ът **е durable state transition**.

Martin Fowler също разделя Event Notification от Event Sourcing и подчертава, че при Event Sourcing event log-ът е principal source of truth. 

---

# 4. Domain Event ≠ автоматично Event-Sourced Event

Предишният Domain Events lab имаше:

```text
Order entity changes
→ OrderPaid domain event
```

но JPA row остава source of truth.

В този lab:

```text
AccountOpened
MoneyDeposited
MoneyWithdrawn
```

са persist-нати business facts, от които Account се reconstruct-ва.

Тоест:

```text
Domain Events
може да съществуват
без Event Sourcing.

Event Sourcing
по дефиниция изисква persisted event history
като source of truth.
```

---

# 5. Защо account е добър учебен domain

Accounting е естествен fit.

Вместо:

```text
balance = 130
```

имаме:

```text
open account
+150 salary
-20 groceries
```

Това носи:

- audit trail;
- причинно-следствена история;
- historical reconstruction;
- corrections/reversals;
- stream-level concurrency.

Fowler също посочва accounting като силен пример за Event Sourcing.

---

# README → код

| Концепция | Код | Test |
| --- | --- | --- |
| Event-sourced aggregate | [`Account.java`](./src/main/java/bg/hristomanov/education/eventsourcing/domain/Account.java) | [`EventSourcingLabTest.java`](./src/test/java/bg/hristomanov/education/eventsourcing/EventSourcingLabTest.java) |
| Event hierarchy | [`AccountEvent.java`](./src/main/java/bg/hristomanov/education/eventsourcing/domain/AccountEvent.java) | same |
| Append-only event row | [`StoredEventEntity.java`](./src/main/java/bg/hristomanov/education/eventsourcing/store/StoredEventEntity.java) | same |
| Event Store contract | [`EventStore.java`](./src/main/java/bg/hristomanov/education/eventsourcing/store/EventStore.java) | same |
| JPA Event Store | [`JpaEventStore.java`](./src/main/java/bg/hristomanov/education/eventsourcing/store/JpaEventStore.java) | same |
| Replay loader | [`AccountLoader.java`](./src/main/java/bg/hristomanov/education/eventsourcing/application/AccountLoader.java) | same |
| Command side | [`AccountCommandService.java`](./src/main/java/bg/hristomanov/education/eventsourcing/application/AccountCommandService.java) | same |
| Snapshot | [`AccountSnapshotStore.java`](./src/main/java/bg/hristomanov/education/eventsourcing/snapshot/AccountSnapshotStore.java) | same |
| Read projection | [`AccountBalanceProjection.java`](./src/main/java/bg/hristomanov/education/eventsourcing/projection/AccountBalanceProjection.java) | same |
| Projector / rebuild | [`AccountBalanceProjector.java`](./src/main/java/bg/hristomanov/education/eventsourcing/projection/AccountBalanceProjector.java) | same |
| Runnable API | [`EventSourcingController.java`](./src/main/java/bg/hristomanov/education/eventsourcing/api/EventSourcingController.java) | [`event-sourcing-demo.http`](./http/event-sourcing-demo.http) |

---

# 6. Event stream per aggregate

Нашият stream ID е:

```text
accountId
```

Пример:

```text
ACC-001
├── v0 AccountOpened
├── v1 MoneyDeposited
├── v2 MoneyWithdrawn
└── v3 MoneyDeposited
```

Stream version е local ordering за конкретния aggregate.

Current account version:

```text
last event stream version
```

Kurrent/EventStoreDB docs също описват streams като logical collections от events, често един stream per entity.

---

# 7. Stream version vs global position

Event Store row пази две ordering coordinates.

## Stream version

```text
ACC-1 v0
ACC-1 v1
ACC-1 v2
```

Използва се за:

- aggregate replay;
- optimistic concurrency;
- ordering within aggregate.

## Global position

```text
position 1  ACC-1 v0
position 2  ACC-2 v0
position 3  ACC-1 v1
```

Използва се от projection checkpoint-а:

```text
process all events after global position N
```

Global order е infrastructure ordering, не automatic global business causality.

---

# 8. Append-only event row

[`StoredEventEntity.java`](./src/main/java/bg/hristomanov/education/eventsourcing/store/StoredEventEntity.java)

пази:

```text
global_position
event_id
stream_id
stream_version
event_type
event_schema_version
payload
occurred_at
```

Constraints:

```text
UNIQUE(event_id)
UNIQUE(stream_id, stream_version)
```

В application design-а няма update/delete operation върху event history.

Event row-овете са факти.

---

# 9. Command flow

При deposit:

```text
read event stream
→ replay Account
→ validate command
→ raise MoneyDeposited
→ append new event with expectedVersion
```

Код:

[`AccountCommandService.java`](./src/main/java/bg/hristomanov/education/eventsourcing/application/AccountCommandService.java)

```text
Event Store
→ Aggregate working copy
→ business decision
→ new event
→ Event Store
```

Няма:

```text
UPDATE account SET balance = ...
```

---

# 10. Aggregate apply vs decide

Account има две различни responsibilities.

## Decide

Command method:

```java
account.withdraw(amount, reference);
```

проверява:

```text
amount > 0
balance >= amount
```

и при успех създава:

```text
MoneyWithdrawn
```

## Apply

Event handler вътре в aggregate-а:

```text
MoneyWithdrawn
→ balance -= amount
```

Apply logic не трябва да решава дали event-ът е позволен.

Той reconstruct-ва state от вече приети facts.

---

# 11. Invalid command не създава event

Test:

`rejectedCommandDoesNotCreateAnEvent()`

Flow:

```text
AccountOpened
balance = 0

withdraw 100
→ invariant fails
→ no MoneyWithdrawn event
```

Event stream остава:

```text
v0 AccountOpened
```

Това е command-side consistency boundary.

---

# 12. Replay

[`AccountLoader.java`](./src/main/java/bg/hristomanov/education/eventsourcing/application/AccountLoader.java)

чете stream-а:

```text
v0 AccountOpened
v1 MoneyDeposited +150
v2 MoneyWithdrawn -20
```

и прилага events последователно:

```text
0
→ 150
→ 130
```

Полученият `Account` е working copy.

Event Store остава authoritative.

Test:

`currentStateIsDerivedEntirelyByReplayingTheEventStream()`

---

# 13. Historical state

Понеже пазим цялата history, можем да rebuild-нем:

```text
state at v1
state at v2
current state
```

Test:

`historicalStateCanBeReconstructedAtAnEarlierStreamVersion()`

При history:

```text
v0 AccountOpened
v1 Deposit 150
v2 Withdraw 20
```

получаваме:

```text
state at v1 → 150
state at v2 → 130
```

Това е фундаментална Event Sourcing capability, не просто audit display.

---

# 14. Optimistic concurrency

Два writers могат да load-нат еднакъв stream version:

```text
T1 reads v0
T2 reads v0
```

T1 решава:

```text
append v1
```

T2 също иска:

```text
append v1
```

Event Store не трябва да позволи silent overwrite.

Нашият API:

```text
append(streamId, expectedVersion, events)
```

проверява current version.

Освен preliminary check имаме:

```text
UNIQUE(stream_id, stream_version)
```

като atomic race barrier.

Test:

`staleExpectedVersionIsRejectedInsteadOfOverwritingHistory()`

Доказва:

```text
writer 1: expected 0 → append v1 ✅
writer 2: expected 0 → conflict ❌
```

без lost update.

---

# 15. Expected revision в истински event store

Kurrent/EventStoreDB използва същата идея:

```text
NO_STREAM
STREAM_EXISTS
ANY
specific expected revision
```

Specific expected revision позволява optimistic concurrency: ако stream-ът вече е променен след read-а, append се reject-ва.

Нашият relational Event Store възпроизвежда този mental model.

---

# 16. Какво правим след concurrency conflict?

Не retry-ваме механично същия generated event.

Правилният flow обикновено е:

```text
reload latest stream
→ rehydrate latest aggregate
→ re-evaluate original command
→ maybe generate different event
→ append against new expected version
```

Защо?

Command, който е бил валиден при v5, може вече да е invalid при v6.

Пример:

```text
two withdrawals
same old balance
```

Вторият трябва да re-check-не insufficient-funds invariant.

---

# 17. Event ID vs command ID

`event_id` идентифицира persisted fact.

Но:

```text
unique event ID
≠
automatic command idempotency
```

Ако client retry-не command и application генерира нов UUID event ID, можем да append-нем semantic duplicate.

За externally retryable commands често се пази:

- command/request ID;
- idempotency key;
- causation ID;
- processed-command registry.

Това се връзва с Idempotency module-а.

---

# 18. Projection / materialized view

Event Store не е удобен за всеки query.

Не искаме при UI list:

```text
replay 2 million streams
```

Затова правим derived read models.

В lab-а:

[`AccountBalanceProjection.java`](./src/main/java/bg/hristomanov/education/eventsourcing/projection/AccountBalanceProjection.java)

пази:

```text
accountId
ownerName
currency
balance
sourceVersion
```

Това е CQRS read model.

---

# 19. Projection checkpoint

Projector-ът чете global event log.

Checkpoint:

```text
account-balance-v1
lastGlobalPosition = 12345
```

След restart:

```text
read events after 12345
```

не replay-ва всичко всеки път.

Checkpoint + projection update трябва да имат ясна atomicity/idempotency policy.

В lab-а са в една local DB transaction.

---

# 20. Eventual consistency

Command append-ва event.

Projection може да навакса по-късно:

```text
Event Store:
v2 already committed

Read projection:
still at v1
```

Това е същият consistency trade-off от CQRS module-а.

`sourceVersion` помага lag-ът да бъде видим.

---

# 21. Projection rebuild

Derived projection трябва да може да бъде disposable, ако architecture-ът наистина я счита за derived.

Test:

`readProjectionCanBeDeletedAndRebuiltFromTheEventStore()`

прави:

```text
build projection
→ delete projection
→ delete checkpoint
→ replay entire event store
→ same read model
```

Това е силно доказателство кой е source of truth.

---

# 22. New projection from old history

Event history позволява:

```text
existing events
→ build NEW projection
```

без да променяме command side.

Пример:

```text
AccountOpened
MoneyDeposited
MoneyWithdrawn
```

днес захранват:

```text
AccountBalanceProjection
```

утре могат да rebuild-нат:

```text
MonthlyCashFlowProjection
```

ако historical events съдържат необходимите facts.

---

# 23. Snapshots

Replay cost расте с stream length.

```text
1 event  → cheap
100 events → fine
1,000,000 events → maybe expensive
```

Snapshot пази:

```text
aggregate state at stream version N
```

После load:

```text
snapshot at v1000
+
events v1001..v1010
```

вместо:

```text
events v0..v1010
```

---

# 24. Snapshot НЕ е source of truth

Това е критично.

Snapshot е:

> cache/checkpoint за replay performance.

Ако snapshot бъде изгубен:

```text
event stream
→ full replay
→ same account state
```

Test:

`snapshotReducesReplayWorkWithoutBecomingSourceOfTruth()`

Доказва:

```text
full replay events = 9
snapshot-tail replay = 3
same balance
same stream version
```

После snapshot-ът се изтрива и state пак се възстановява от 9 events.

---

# 25. Snapshot invalidation/versioning

Когато aggregate apply logic или state shape се промени, стар snapshot може вече да не е compatible.

Production snapshot често има:

- snapshot schema/version;
- aggregate version;
- creation timestamp;
- rebuild policy.

Ако има съмнение:

> delete snapshot and replay source events.

Точно защото snapshot не е source of truth.

---

# 26. Event schema evolution

Event-ите живеят дълго.

След години ще имаме old payloads.

Нашият envelope има:

```text
event_type
event_schema_version
payload
```

Текущият lab поддържа schema version 1.

При evolution options включват:

- tolerant reader;
- upcaster;
- versioned event type;
- new event type;
- migration of event store — само ако е внимателно оправдана.

Пример:

```text
AccountOpened v1
{ ownerName }

AccountOpened v2
{ ownerName, currency }
```

Upcaster може runtime да преведе v1 към current in-memory event shape.

---

# 27. Защо old events не трябва да се редактират лекомислено

Event history е business record.

Ако вчера сме записали:

```text
MoneyDeposited 100
```

а днес разберем, че е грешно, типичният Event Sourcing approach е:

```text
append corrective/reversal event
```

не:

```sql
UPDATE old_event SET amount = 50
```

Иначе променяме историята, върху която могат да са били изградени:

- projections;
- audit decisions;
- external messages;
- reports.

---

# 28. Correction vs deletion

Append-only не означава:

> никога при никакви обстоятелства bytes не могат да бъдат премахвани.

Има operational/legal cases:

- corrupted test data;
- retention policy;
- GDPR/privacy requirements;
- secrets accidentally persisted.

Но това е special governance problem, не normal business update mechanism.

Design-ът трябва предварително да минимизира sensitive data в events.

---

# 29. GDPR / PII tension

Immutable historical logs и right-to-erasure могат да се сблъскат.

Възможни techniques:

- не записвай unnecessary PII в event payload;
- reference към separately erasable personal-data store;
- tokenization;
- encryption keys per subject + crypto-shredding;
- retention/pseudonymization policies.

Няма universal magic solution.

Event Sourcing трябва да бъде оценен и от privacy/compliance гледна точка.

---

# 30. Deterministic replay

Apply logic трябва да бъде replay-safe.

BAD:

```text
apply MoneyDeposited
→ call exchange-rate API
→ use current date
→ send email
```

При replay утре бихме могли да получим различен state или repeated side effects.

Добър mental model:

```text
persisted event contains the historical fact needed to derive state
```

Replay:

- no email;
- no payment API;
- no random;
- no current external price;
- no wall-clock dependency.

---

# 31. External interactions belong in command decision, not replay

Ако business decision зависи от external data:

```text
exchange rate
credit score
market price
```

command handler може да го получи преди event creation.

После event-ът трябва да capture-не достатъчно от решението/fact-а, за да бъде replay deterministic.

Пример:

```text
PaymentAccepted
amountEUR = 100
appliedExchangeRate = 1.08
```

а не replay да пита exchange-rate API какъв е курсът днес.

---

# 32. Event Store ≠ Message Broker

Event Store:

```text
authoritative domain history
per aggregate streams
replay state
optimistic concurrency
```

Broker:

```text
delivery/routing
consumer groups
retention policies
integration messaging
```

Kafka може да бъде част от Event Sourcing architecture, но:

> Kafka topic не става автоматично добре моделиран domain Event Store.

Трябват stream identity, concurrency semantics, event evolution, aggregate loading и lifecycle decisions.

---

# 33. Event Store ≠ audit log

Audit log:

> записва какво се е случило около state changes.

Event Store:

> state changes са reconstructable authoritative record.

Ако изтрием current-state database и не можем да rebuild-нем system state от audit records, това не е Event Sourcing.

---

# 34. Event Store ≠ Outbox

Outbox:

```text
business state table
+
reliable intent-to-publish
```

Event Sourcing:

```text
events are business state source of truth
```

При Event Sourcing integration publication може да бъде driven от event store subscription/log tailing.

Но externalization пак има:

- delivery;
- idempotency;
- schema contract;
- consumer concerns.

---

# 35. Event Sourcing + CQRS

Двете често се комбинират естествено.

Command side:

```text
command
→ event-sourced aggregate
→ Event Store
```

Query side:

```text
Event Store
→ projections
→ query-optimized read models
```

Но:

```text
CQRS without Event Sourcing ✅
Event Sourcing without elaborate CQRS infrastructure ✅
```

Те са отделни patterns.

---

# 36. Event Sourcing + Saga

Long-running process може да пази transitions като events.

Но Saga и Event Sourcing решават различни problems:

```text
Saga
→ distributed business transaction coordination

Event Sourcing
→ authoritative state representation as event history
```

Event-sourced Saga/process manager е възможна комбинация, не definition.

---

# 37. Event Sourcing + Hexagonal Architecture

Event Store може да бъде output port:

```text
application core
→ AccountEventStorePort
← relational/Kurrent adapter
```

Тогава aggregate/domain code не знае concrete storage.

Този lab държи focus-а върху Event Sourcing mechanics, затова не повтаря целия multi-module Hexagonal structure.

---

# 38. Dedicated Event Store

Нашият lab използва relational table, защото искаме mechanics да се виждат.

Dedicated systems като Kurrent/EventStoreDB предлагат first-class:

- streams;
- expected revisions;
- subscriptions;
- projections;
- event-oriented storage semantics.

Kurrent docs показват `expectedRevision` exactly като stream optimistic-concurrency mechanism.

Изборът relational vs dedicated store е operational/design decision.

Pattern-ът не изисква конкретен vendor.

---

# 39. Projection rebuild и side effects

Projection handler трябва да бъде:

- deterministic;
- idempotent/restartable;
- version-aware;
- free от unintended external side effects.

При rebuild не искаме:

```text
replay AccountOpened
→ send welcome email again
```

Projection и reaction са различни responsibilities.

---

# 40. Projection schema changes

Една от силите на Event Sourcing:

```text
old event history
→ new projection v2
```

Migration strategy може да бъде:

```text
build projection_v2 in parallel
→ catch up
→ switch reads
→ retire v1
```

без да rewrite-ваме source events.

Това е мощно, но изисква storage/processing capacity и tooling.

---

# 41. Snapshot vs projection

Тези две често се бъркат.

## Snapshot

```text
optimization for loading one aggregate stream
```

## Projection

```text
derived read model for queries
```

Пример:

```text
AccountSnapshot
→ helps command side rehydrate Account faster

AccountBalanceProjection
→ helps query side answer GET balance faster
```

---

# 42. Performance trade-offs

Event Sourcing може да има excellent append performance, но добавя други costs:

- replay;
- serialization;
- projection lag;
- projection storage;
- snapshots;
- event versioning;
- operational tooling.

Не приемаме:

> append-only = system automatically faster.

Measure the actual workload.

---

# 43. Debugging

Event history е много полезна при incident analysis:

```text
v31 CreditLimitChanged
v32 PaymentAuthorized
v33 PaymentCaptured
```

Можем да reconstruction-нем:

- state before incident;
- exact transition sequence;
- actor/correlation metadata, ако е записана.

Но debugging distributed projections/subscriptions може също да стане по-сложно.

Ползата не е free.

---

# 44. Metadata

Production event envelope често съдържа повече от нашия minimal lab:

- eventId;
- streamId;
- streamVersion;
- eventType;
- schemaVersion;
- occurredAt;
- correlationId;
- causationId;
- actor/tenant;
- trace context.

Тези полета трябва да имат ясна semantics.

Не добавяме metadata само защото "event systems имат много headers".

---

# 45. Rebuild deployment concerns

Full replay може да натовари:

- event store;
- projection database;
- CPU;
- network;
- downstream dependencies, ако projector е зле проектиран.

Production rebuild често изисква:

- throttling;
- parallelism by partition;
- checkpoints;
- blue/green projection;
- capacity planning.

---

# 46. Кога Event Sourcing е добър fit

Силни сигнали:

- audit/history е core business requirement;
- трябва да reconstruct-ваме historical state;
- domain естествено говори чрез meaningful events;
- corrections/reversals са важни;
- temporal queries носят business value;
- complex domain with explicit aggregate decisions;
- multiple derived projections;
- event history има standalone business value.

---

# 47. Кога НЕ

Лош fit:

- simple CRUD;
- history няма business value;
- екипът няма operational maturity за projections/versioning;
- PII/retention constraints са тежки;
- event schemas ще са неясни/нестабилни;
- един current-state row решава problem-а отлично.

Microsoft актуално предупреждава, че Event Sourcing е complex, costly to migrate to/from и за повечето systems traditional data management е достатъчно.

---

# 48. Не прилагай Event Sourcing навсякъде

Дори в една система може да имаме:

```text
Payments ledger
→ Event Sourcing

User preferences
→ CRUD

Reference data
→ CRUD/cache
```

Pattern-ът може да бъде локален за bounded context/aggregate type.

Не е enterprise-wide religion.

---

# Какво доказват тестовете

[`EventSourcingLabTest.java`](./src/test/java/bg/hristomanov/education/eventsourcing/EventSourcingLabTest.java)

## Source of truth

```text
3 events
→ replay
→ balance 130
```

без current-state Account table.

## Historical state

```text
state at v1 = 150
current at v2 = 130
```

## Invalid command

```text
withdraw > balance
→ exception
→ no new event
```

## Optimistic concurrency

```text
stale expectedVersion
→ conflict
→ no overwrite
```

## Projection rebuild

```text
delete read model
→ replay event store
→ same projection
```

## Snapshot

```text
full replay = 9 events
snapshot replay = 3 tail events
same state
```

---

# Как да стартираме

```bash
mvn -pl spring/architecture/event-sourcing -am test
```

Стартиране:

```bash
mvn -pl spring/architecture/event-sourcing spring-boot:run
```

HTTP scenarios:

- [`http/event-sourcing-demo.http`](./http/event-sourcing-demo.http)

---

# Mental model за запомняне

1. **Event stream е source of truth; aggregate state е derived working copy.**
2. **Persisted event е historical fact, не просто notification.**
3. **Command rehydrates state, enforces invariant и append-ва new event.**
4. **Expected stream version дава optimistic concurrency.**
5. **Historical state идва естествено от partial replay.**
6. **Projections са disposable derived read models.**
7. **Snapshots ускоряват aggregate replay, но не заменят event store-а.**
8. **Replay logic трябва да бъде deterministic и side-effect free.**
9. **Event schemas са long-lived contracts и evolution е first-class problem.**
10. **CQRS и Event Sourcing се комбинират добре, но не са едно и също.**
11. **Event Sourcing има висока complexity цена и трябва да решава реален problem.**

---

# Code-review checklist

```text
[ ] Event Store действително ли е source of truth?
[ ] Event-ите business facts ли са?
[ ] Stream boundary съвпада ли с aggregate consistency boundary?
[ ] Stream version monotonic ли е?
[ ] Append има ли expected-version concurrency guard?
[ ] Command conflict води ли до reload + re-decide?
[ ] Event ID и command/idempotency ID разграничени ли са?
[ ] Apply logic deterministic ли е?
[ ] Replay извиква ли external systems? (не трябва)
[ ] Projection rebuildable ли е?
[ ] Projection checkpoint semantics ясни ли са?
[ ] Snapshot optional optimization ли е?
[ ] Event schema/versioning strategy има ли?
[ ] PII/retention implications разгледани ли са?
[ ] Corrections append-ват ли нови facts вместо silent history rewrite?
[ ] Pattern-ът приложен ли е само там, където history/replay имат реална value?
```

---

# Упражнения

1. Добави `AccountClosed` event.
2. Добави `MoneyTransferRequested/Completed` и реши stream boundaries.
3. Добави `commandId` dedupe.
4. Симулирай два concurrent withdrawals и reload/re-decide след conflict.
5. Добави snapshot schema version.
6. Добави `AccountOpenedV1 -> current` upcaster.
7. Добави `MonthlyCashFlowProjection` и build-ни от existing events.
8. Направи projection v2 blue/green rebuild.
9. Добави correlationId/causationId metadata.
10. Замени relational Event Store adapter с Kurrent/EventStoreDB client, без да променяш aggregate semantics.

---

# Оригинални източници

- Martin Fowler — Event Sourcing: https://martinfowler.com/eaaDev/EventSourcing.html
- Martin Fowler — What do you mean by Event-Driven?: https://martinfowler.com/articles/201701-event-driven.html
- Microsoft Azure Architecture Center — Event Sourcing pattern: https://learn.microsoft.com/azure/architecture/patterns/event-sourcing
- Kurrent/EventStoreDB — Event streams: https://docs.kurrent.io/server/v24.10/features/streams
- Kurrent/EventStoreDB — Appending events / expected revision: https://docs.kurrent.io/clients/node/legacy/v6.2/appending-events
- Kurrent/EventStoreDB — Projections: https://docs.kurrent.io/server/v24.10/features/projections/
- Предишни education modules: Domain Events, CQRS, Idempotency, Outbox/Inbox, Saga, Hexagonal Architecture.

---

# Изходен въпрос

Когато някой каже:

> Ние вече публикуваме events, значи системата ни е event-sourced.

питай:

> **Ако изтрием current-state таблиците, можем ли authoritative business state да бъде възстановен само чрез replay на immutable ordered event streams — и готови ли сме да поемем concurrency, schema evolution, projection, replay и operational цената на това решение?**
