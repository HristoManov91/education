# Domain Events — business facts, transaction phases and integration boundaries

Този модул учи Domain Events като design concept и после показва как Spring Data и Spring Framework ги реализират.

Основният въпрос е:

> **Как aggregate-ът да каже „нещо важно се случи“, без самият domain code да знае кой ще реагира и без да объркаме in-process event с durable integration message?**

---

# ВХОД В ТЕМАТА

## Реалният казус

Order преминава:

```text
NEW → PAID
```

След това различни части на системата може да искат:

- audit;
- projection/read model update;
- email;
- integration event към други services;
- analytics.

Naive aggregate:

```text
order.markPaid()
→ emailService.send()
→ kafkaTemplate.send()
→ analyticsService.record()
```

Така domain model-ът започва да знае infrastructure/reaction details.

---

# Domain Event mental model

Domain Event е:

> **business fact, който вече се е случил в domain-а и е значим за други части на модела/application-а.**

Затова имената често са в минало време:

```text
OrderPaid
PaymentCaptured
InventoryReserved
ProtocolConfirmed
```

а не commands като:

```text
PayOrder
CapturePayment
```

Command казва:

> направи това.

Event казва:

> това вече се случи.

---

# Aggregate-ът регистрира event, не реакциите

[`OrderAggregate.java`](./src/main/java/bg/hristomanov/education/events/domain/OrderAggregate.java) прави:

```text
markPaid()
→ enforce invariant
→ status = PAID
→ register OrderPaid
```

Aggregate-ът НЕ знае:

- кой listener съществува;
- има ли Kafka;
- има ли email;
- има ли projection table.

Това намалява coupling-а между business decision и reactions.

---

# Spring Data `AbstractAggregateRoot`

Spring Data предоставя:

```text
AbstractAggregateRoot
@DomainEvents
@AfterDomainEventPublication
```

`AbstractAggregateRoot.registerEvent(...)` пази domain events в aggregate-а.

Spring Data repository infrastructure ги публикува чрез Spring Application Events при подходящ repository method call.

Код:

- [`OrderAggregate.java`](./src/main/java/bg/hristomanov/education/events/domain/OrderAggregate.java)
- [`OrderAggregateRepository.java`](./src/main/java/bg/hristomanov/education/events/repository/OrderAggregateRepository.java)

---

# Най-важният JPA нюанс: dirty checking vs domain event publication

Предишният Unit of Work lab показа:

```text
managed entity changes
→ no explicit save needed
→ dirty checking persists changes
```

Това остава вярно.

Но Spring Data Domain Events имат **друга trigger semantics**.

Официалната Spring Data документация описва event publication при repository calls като:

```text
save(...)
saveAll(...)
delete(...)
deleteAll(...)
...
```

Следователно:

## Case A — save

```java
OrderAggregate order = repository.findById(id).orElseThrow();
order.markPaid();
repository.save(order);
```

Получаваме:

```text
JPA dirty checking
+
Spring Data domain event publication
```

## Case B — no save

```java
OrderAggregate order = repository.findById(id).orElseThrow();
order.markPaid();
// no repository.save(order)
```

Получаваме:

```text
JPA dirty checking ✅
Spring Data repository event hook ❌
```

Тестът `dirtyCheckingWithoutRepositorySavePersistsStateButDoesNotPublishSpringDataDomainEvent()` доказва това.

Това е отличен пример защо:

> `save()` може да е излишен за SQL persistence, но да има допълнителна application/infrastructure semantics.

---

# README → код

| Концепция | Код | Test |
| --- | --- | --- |
| Aggregate root | [`OrderAggregate.java`](./src/main/java/bg/hristomanov/education/events/domain/OrderAggregate.java) | [`DomainEventsTest.java`](./src/test/java/bg/hristomanov/education/events/DomainEventsTest.java) |
| Domain Event | [`OrderPaid.java`](./src/main/java/bg/hristomanov/education/events/domain/OrderPaid.java) | same |
| Repository trigger | [`OrderDomainEventService.java`](./src/main/java/bg/hristomanov/education/events/service/OrderDomainEventService.java) | same |
| Synchronous listener | [`SynchronousOrderPaidListener.java`](./src/main/java/bg/hristomanov/education/events/listener/SynchronousOrderPaidListener.java) | same |
| AFTER_COMMIT / AFTER_ROLLBACK | [`TransactionalOrderPaidListeners.java`](./src/main/java/bg/hristomanov/education/events/listener/TransactionalOrderPaidListeners.java) | same |
| New transaction after commit | [`OrderPaidProjectionWriter.java`](./src/main/java/bg/hristomanov/education/events/listener/OrderPaidProjectionWriter.java) | same |
| Integration contract mapping | [`OrderPaidIntegrationEventMapper.java`](./src/main/java/bg/hristomanov/education/events/integration/OrderPaidIntegrationEventMapper.java) | same |

---

# Plain `@EventListener`

[`SynchronousOrderPaidListener.java`](./src/main/java/bg/hristomanov/education/events/listener/SynchronousOrderPaidListener.java)

Spring Application Events са synchronous по default.

Flow:

```text
repository.save(order)
→ Spring Data publishes OrderPaid
→ @EventListener executes now
→ returns
→ transaction continues toward commit
```

Следствие:

```text
listener throws exception
→ exception propagates
→ business transaction can rollback
```

Lab test-ът доказва точно това.

---

# `@TransactionalEventListener`

Spring Framework позволява listener да бъде bound към transaction phase.

Phases:

```text
BEFORE_COMMIT
AFTER_COMMIT   ← default
AFTER_ROLLBACK
AFTER_COMPLETION
```

В lab-а имаме:

```text
OrderPaid
├→ synchronous @EventListener
├→ AFTER_COMMIT listener
└→ AFTER_ROLLBACK listener
```

---

# AFTER_COMMIT

AFTER_COMMIT е подходящ, когато reaction трябва да се случи само ако original business transaction реално е commit-нала.

Например:

```text
order PAID commit ✅
→ update derived projection
```

Ако transaction rollback-не:

```text
AFTER_COMMIT listener
→ не се изпълнява
```

Test:

`repositorySavePublishesDomainEventAndAfterCommitReaction()`

---

# Защо AFTER_COMMIT projection writer е `REQUIRES_NEW`

[`OrderPaidProjectionWriter.java`](./src/main/java/bg/hristomanov/education/events/listener/OrderPaidProjectionWriter.java) използва нова transaction.

Original business transaction вече е commit-ната.

Когато AFTER_COMMIT reaction прави нов durable DB write, трябва ясно да определим transaction boundary за този write.

В lab-а:

```text
business transaction COMMIT
→ AFTER_COMMIT listener
→ REQUIRES_NEW projection transaction
```

Това не прави двете transactions atomic.

Ако projection write fail-не след business commit, business state остава commit-нат.

Това е важен reliability distinction.

---

# AFTER_ROLLBACK

Ако synchronous listener fail-не:

```text
OrderPaid published
→ sync listener throws
→ transaction rollback
→ AFTER_ROLLBACK listener runs
```

Test:

`synchronousListenerFailureRollsBackBusinessTransactionAndTriggersAfterRollback()`

Доказва:

```text
order status = NEW after reload
AFTER_COMMIT = 0
AFTER_ROLLBACK = 1
projection rows = 0
```

---

# Transactional listener без transaction

По default `@TransactionalEventListener` не се изпълнява, ако event-ът е публикуван без active transaction.

Spring има `fallbackExecution=true`, но това променя semantics и трябва да е съзнателно решение.

Не трябва да приемаме, че transactional listener е просто fancy `@EventListener`.

---

# Domain Event vs Application Event vs Integration Event

Това разграничение е много важно.

## Domain Event

Business fact на езика на domain-а:

```text
OrderPaid
```

Фокус:

- business meaning;
- aggregate/domain lifecycle.

## Application Event

Сигнал между application components/modules.

Може да е технически Spring Application Event, но не всяко application event е domain event.

Примери:

```text
ImportJobFinished
CacheWarmupRequested
```

## Integration Event

External contract към други systems/services.

При нас:

[`OrderPaidIntegrationEvent.java`](./src/main/java/bg/hristomanov/education/events/integration/OrderPaidIntegrationEvent.java)

има:

- event ID;
- schema version;
- externalized fields.

Това са integration concerns, които domain event-ът не е длъжен да носи.

---

# Защо не публикуваме domain object директно към Kafka

Domain model се променя според internal business design.

Integration contract трябва да се променя по по-контролиран начин.

Директното:

```text
JPA entity
→ JSON
→ Kafka
```

coupling-ва:

- DB mapping;
- internal fields;
- lazy associations;
- domain refactoring;
- external consumers.

По-добър boundary:

```text
Domain Event
→ mapper
→ Integration Event v1
→ Outbox
→ Broker
```

---

# Spring Application Event НЕ е durable messaging

Plain Spring event:

```text
process memory
→ listener
```

Ако process-ът спре в неподходящ момент, няма broker/durable log по default.

Следователно:

> `ApplicationEventPublisher` не е replacement за Transactional Outbox/Kafka.

Използвай in-process events за module decoupling, когато reliability semantics го позволяват.

За external/durable delivery използвай explicit reliable publication pattern.

---

# Spring Modulith Event Publication Registry

Spring Modulith предлага интересен bridge между in-process application events и reliable publication.

Event Publication Registry:

```text
business transaction
→ application event
→ registry records publication as part of original transaction
→ transactional listener runs
→ publication marked completed
```

Ако listener fail-не, publication остава incomplete/failed и може да бъде resubmitted.

Spring Modulith 2.1.x има JPA/JDBC/Mongo implementations и APIs за completed/incomplete/failed publications.

Това е production-ready alternative/extension, когато architecture е modular monolith или event externalization pipeline.

Не го добавяме като dependency в този базов lab, за да останат core Spring semantics видими.

---

# Domain Events + Outbox

Чест production flow:

```text
aggregate method
→ Domain Event
→ map to Integration Event
→ write Outbox in same business transaction
→ relay to broker
```

Така:

- domain model говори business language;
- integration contract е отделен;
- external delivery има durability.

Предишният Outbox module показва persistence/delivery частта.

---

# Domain Events + Saga

Saga choreography използва events за coordination:

```text
OrderCreated
→ PaymentReserved
→ InventoryReserved
...
```

Но не всеки domain event трябва да бъде external integration event.

Трябва explicit decision:

```text
internal domain fact only?
application-module event?
external integration contract?
```

---

# Event payload design

Domain event трябва да съдържа достатъчно information за meaning-а му.

Но избягвай:

- entire mutable aggregate graph;
- lazy JPA entities;
- incidental infrastructure data.

Често event съдържа:

```text
aggregate ID
business identifiers
facts needed by handlers
occurredAt
```

Integration event може допълнително да има:

```text
eventId
schemaVersion
correlationId
causationId
```

---

# Event ordering

Domain Events сами по себе си не решават ordering през distributed broker.

Ако order има:

```text
OrderPaid
OrderRefunded
```

externalization може да изисква:

- aggregate sequence;
- partition key;
- version check;
- ordering guarantees.

Това е integration concern.

---

# Error handling policy

В synchronous listener:

```text
failure
→ може да rollback-не publisher transaction
```

В AFTER_COMMIT listener:

```text
business commit вече е факт
→ listener failure НЕ може да rollback-не business commit
```

Следователно failure policy е различна.

За AFTER_COMMIT critical work трябват:

- retry;
- durable publication registry;
- Outbox;
- reconciliation/alerting.

Не разчитай само на log statement.

---

# Domain Events и side effects

Не превръщай всички method calls в events.

Direct dependency е по-добър, когато:

- operation е част от същия invariant;
- caller има нужда от immediate result;
- failure трябва директно да abort-не use case-а;
- няма реална benefit от decoupling.

Event е подходящ, когато:

- вече се е случил business fact;
- reactions са отделни concerns;
- publisher не трябва да знае всички consumers;
- temporal decoupling е полезно.

---

# Какво доказват тестовете

[`DomainEventsTest.java`](./src/test/java/bg/hristomanov/education/events/DomainEventsTest.java)

## Repository save

```text
markPaid
→ repository.save
→ sync listener
→ COMMIT
→ AFTER_COMMIT
→ projection in REQUIRES_NEW
```

## Dirty checking without save

```text
markPaid
→ no repository.save
→ DB becomes PAID
→ Spring Data Domain Event NOT published
```

## Synchronous listener failure

```text
markPaid
→ save publishes event
→ sync listener throws
→ transaction rollback
→ order remains NEW
→ AFTER_ROLLBACK
→ no AFTER_COMMIT projection
```

---

# Как да стартираме

```bash
mvn -pl spring/events/domain-events -am test
```

---

# Mental model за запомняне

1. **Domain Event е business fact, не command.**
2. **Aggregate регистрира fact-а, не всички reactions.**
3. **Spring Data `@DomainEvents` publication е repository-operation semantics, не JPA dirty-checking semantics.**
4. **Plain `@EventListener` е synchronous по default.**
5. **`@TransactionalEventListener` връзва reaction към transaction phase.**
6. **AFTER_COMMIT work не може да rollback-не вече commit-натата business transaction.**
7. **Domain Event, Application Event и Integration Event не са автоматично едно и също.**
8. **Spring Application Events не са durable broker.**
9. **Outbox/Modulith publication registry решават reliability concerns.**

---

# Code-review checklist

```text
[ ] Event-ът business fact ли е и името му в минало време ли е?
[ ] Aggregate знае ли infrastructure listener-и, които не трябва да знае?
[ ] Repository save нужен ли е за DomainEvents publication?
[ ] Listener synchronous ли е или transaction-bound?
[ ] Коя transaction phase е правилна?
[ ] AFTER_COMMIT listener прави ли durable write и има ли собствена transaction?
[ ] Event listener failure трябва ли да rollback-не use case-а?
[ ] Domain event публикува ли се погрешно директно като external contract?
[ ] External event има ли ID/version/schema policy?
[ ] Нужна ли е durable publication чрез Outbox/Modulith?
[ ] Има ли ordering/idempotency requirements?
```

---

# Упражнения

1. Замени `AbstractAggregateRoot` с explicit `@DomainEvents` / `@AfterDomainEventPublication` methods.
2. Добави `OrderRefunded` domain event.
3. Добави BEFORE_COMMIT listener и сравни failure semantics.
4. Публикувай event извън transaction и виж `fallbackExecution` behavior.
5. Добави Outbox mapper от `OrderPaid` към integration event.
6. Добави correlation/causation IDs.
7. Добави aggregate event sequence.
8. Добави Spring Modulith Event Publication Registry като отделен advanced lab.
9. Направи failing AFTER_COMMIT listener и durable retry design.
10. Сравни direct method call срещу event за invariant-critical operation.

---

# Оригинални източници

- Spring Data Commons — Publishing Events from Aggregate Roots: https://docs.spring.io/spring-data/commons/reference/repositories/core-domain-events.html
- Spring Data — `AbstractAggregateRoot`: https://docs.spring.io/spring-data/commons/docs/current/api/org/springframework/data/domain/AbstractAggregateRoot.html
- Spring Framework — Transaction-bound Events: https://docs.spring.io/spring-framework/reference/data-access/transaction/event.html
- Spring Modulith — Working with Application Events: https://docs.spring.io/spring-modulith/reference/events.html
- Martin Fowler — Domain Event: https://martinfowler.com/eaaDev/DomainEvent.html
- Eric Evans — Domain-Driven Design.

---

# Изходен въпрос

Когато видиш:

```text
entity changed
→ send email
→ update report
→ publish Kafka
```

питай:

> **Кое тук е самият business fact, кои са reactions, коя от тях трябва да е в същата transaction и коя изисква durable external publication contract?**
