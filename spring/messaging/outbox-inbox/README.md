# Transactional Outbox + Idempotent Consumer / Inbox

Този модул решава един от най-важните distributed backend problems:

> **Как да запазим business state в database и надеждно да публикуваме event към message broker, без distributed transaction между DB и broker?**

Втората половина на проблема е:

> **Ако event може да бъде доставен повече от веднъж, как consumer-ът да приложи business effect-а само веднъж?**

Затова Outbox и Inbox са в един executable lab.

---

# ВХОД В ТЕМАТА

## 1. Dual-write problem

Имаме use case:

```text
Create Order
→ INSERT order в database
→ publish OrderCreated event
```

Това са две различни systems:

```text
Database
Message Broker
```

Една normal local DB transaction не може атомарно да commit-не и двете.

---

# 2. BAD вариант A — DB commit, после publish

[`NaiveOrderService.java`](./src/main/java/bg/hristomanov/education/outbox/service/NaiveOrderService.java) показва:

```text
BEGIN DB
INSERT order
COMMIT

process execution stops

publish event   ← никога не се случва
```

Резултат:

```text
DB: order exists
Broker: no OrderCreated
```

Тестът `naiveCommitThenPublishCanLoseTheEvent()` доказва точно тази inconsistency.

---

# 3. BAD вариант B — publish преди DB commit

Ако обърнем реда:

```text
publish OrderCreated
→ broker accepts message

DB transaction rolls back
```

получаваме обратната inconsistency:

```text
Broker: OrderCreated exists
DB: order does not exist
```

Следователно само разместването на реда не решава проблема.

---

# 4. Защо не просто distributed 2PC?

Transactional Outbox се използва, когато не искаме или не можем да имаме една distributed transaction, която включва database + broker.

2PC може да е:

- неподдържан от някоя система;
- operationally тежък;
- силно coupling-ващ;
- неподходящ за desired availability model.

Outbox заменя проблема с:

```text
ONE local DB transaction
+
separate reliable relay
```

---

# 5. Transactional Outbox solution

Business state и event record се записват в една и съща DB transaction:

```text
BEGIN

INSERT orders (...)
INSERT outbox_events (...)

COMMIT
```

или:

```text
ROLLBACK both
```

Код:

- [`TransactionalOutboxOrderService.java`](./src/main/java/bg/hristomanov/education/outbox/service/TransactionalOutboxOrderService.java)
- [`OutboxEvent.java`](./src/main/java/bg/hristomanov/education/outbox/outbox/OutboxEvent.java)

Test:

```text
failure before commit
→ orders = 0
→ outbox_events = 0

normal commit
→ orders = 1
→ outbox_events = 1
→ broker messages = 0
```

Последният ред е важен: business transaction НЕ публикува директно към broker.

---

# README → код

| Концепция | Код | Test |
| --- | --- | --- |
| Unsafe dual write | [`NaiveOrderService.java`](./src/main/java/bg/hristomanov/education/outbox/service/NaiveOrderService.java) | [`TransactionalOutboxProducerTest.java`](./src/test/java/bg/hristomanov/education/outbox/TransactionalOutboxProducerTest.java) |
| Atomic order + event | [`TransactionalOutboxOrderService.java`](./src/main/java/bg/hristomanov/education/outbox/service/TransactionalOutboxOrderService.java) | same |
| Outbox row | [`OutboxEvent.java`](./src/main/java/bg/hristomanov/education/outbox/outbox/OutboxEvent.java) | same |
| Polling relay | [`OutboxRelayService.java`](./src/main/java/bg/hristomanov/education/outbox/service/OutboxRelayService.java) | same |
| Broker fixture | [`InMemoryMessageBroker.java`](./src/main/java/bg/hristomanov/education/outbox/broker/InMemoryMessageBroker.java) | same |
| Inbox claim | [`ProcessedMessage.java`](./src/main/java/bg/hristomanov/education/outbox/inbox/ProcessedMessage.java) | [`IdempotentConsumerTest.java`](./src/test/java/bg/hristomanov/education/outbox/IdempotentConsumerTest.java) |
| Atomic consumer effect | [`InboxBusinessProcessor.java`](./src/main/java/bg/hristomanov/education/outbox/service/InboxBusinessProcessor.java) | same |
| Duplicate orchestration | [`IdempotentOrderCreatedConsumer.java`](./src/main/java/bg/hristomanov/education/outbox/service/IdempotentOrderCreatedConsumer.java) | same |
| Runnable scenarios | [`outbox-inbox-demo.http`](./http/outbox-inbox-demo.http) | manual |

---

# 6. Outbox row anatomy

Нашият polling-oriented outbox има:

```text
id
aggregate_type
aggregate_id
event_type
payload
created_at
published_at
```

## event id

Unique identity на logical event.

Този ID трябва да преживее retries/redelivery и е ключов за consumer deduplication.

## aggregate id

Identity на aggregate-а, породил event-а.

Това е полезно за routing/partition key и ordering.

## event type

Например:

```text
OrderCreated
PaymentCaptured
ProtocolConfirmed
```

## payload

Serialized event contract.

Не е добра идея consumer-ът да получава JPA entity serialization като integration contract.

---

# 7. Message Relay

Outbox row сам по себе си не публикува нищо.

Трябва relay:

```text
DB outbox
→ Message Relay
→ Broker
```

Два основни implementation styles:

1. Polling Publisher;
2. Transaction Log Tailing / CDC.

Нашият executable lab използва Polling Publisher.

---

# 8. Polling Publisher

[`OutboxRelayService.java`](./src/main/java/bg/hristomanov/education/outbox/service/OutboxRelayService.java) прави:

```text
find oldest unpublished event
→ publish to broker
→ mark published_at
```

Предимства:

- работи с обикновена SQL database;
- лесен mental model;
- лесен старт без CDC infrastructure.

Цена:

- polling latency;
- cleanup;
- concurrency coordination между relay instances;
- ordering трябва да е съзнателно проектиран.

---

# 9. Вторият критичен failure window

Transactional Outbox решава business DB → outbox atomicity.

Но relay има друг unavoidable window:

```text
broker.publish(event)  → SUCCESS

execution stops

mark outbox published  → НЕ се случва
```

След restart:

```text
outbox row still unpublished
→ publish SAME event again
```

Тестът `relayCanPublishTheSameOutboxEventMoreThanOnce()` доказва:

```text
broker messages = 2
eventId #1 == eventId #2
```

Това не е bug в Outbox pattern-а.

Това е нормалната причина producer side да има **at-least-once publishing semantics**.

---

# 10. Защо не можем просто publish + mark да са atomic?

Защото отново имаме две systems:

```text
broker
database
```

Ако нямаме distributed transaction между тях, винаги има boundary между:

```text
broker acknowledged message
и
DB remembered acknowledgement
```

Затова design-ът приема възможни duplicates и прави consumer-а idempotent.

---

# 11. Idempotent Consumer / Inbox

Broker-и с at-least-once delivery могат да доставят едно logical message повече от веднъж.

Naive consumer:

```text
OrderCreated
→ add 10 loyalty points

same OrderCreated redelivered
→ add another 10 points  ← BUG
```

Резултатът става 20 вместо 10.

---

# 12. Processed Messages / Inbox table

Нашият consumer пази:

```text
consumer_name
event_id
processed_at
```

с:

```text
UNIQUE(consumer_name, event_id)
```

Защо consumer name е част от key-а?

Един event може легитимно да бъде обработен от:

```text
loyalty consumer
email consumer
analytics consumer
```

Всеки има собствен processing identity.

---

# 13. Check-then-act отново не е достатъчен

Naive duplicate check:

```text
if event not processed:
    apply effect
    record processed
```

При concurrency:

```text
T1: exists? → false
T2: exists? → false

T1: +10 points
T2: +10 points
```

Затова първият `exists()` в [`IdempotentOrderCreatedConsumer.java`](./src/main/java/bg/hristomanov/education/outbox/service/IdempotentOrderCreatedConsumer.java) е само fast path.

Correctness идва от database UNIQUE constraint-а.

---

# 14. Inbox claim + business effect трябва да са една transaction

[`InboxBusinessProcessor.java`](./src/main/java/bg/hristomanov/education/outbox/service/InboxBusinessProcessor.java) прави:

```text
BEGIN

INSERT processed_messages(eventId)
→ UNIQUE claim

apply business effect
→ loyalty +10

COMMIT
```

Ако processing fail-не:

```text
ROLLBACK processed_messages
ROLLBACK loyalty change
```

Така broker redelivery може безопасно да опита отново.

Тестът `failedBusinessProcessingRollsBackInboxClaimAndEffect()` доказва точно това.

---

# 15. Concurrent duplicate messages

Integration test-ът стартира 8 virtual-thread consumers с един и същ event ID.

Очакване:

```text
1 → PROCESSED
7 → DUPLICATE

processed_messages rows = 1
loyalty points = 10
```

Тоест:

```text
many delivery attempts
→ one logical business effect
```

Това е по-полезният mental model от blanket claim за exactly-once execution.

---

# 16. Inbox table vs message IDs в business entity

Има поне два common designs.

## Separate processed_messages table

```text
consumer_name + message_id
```

Предимства:

- generic;
- лесно audit-ване;
- business model не се замърсява.

Цена:

- допълнителна table/index;
- cleanup;
- още write.

## Message identity в business entity

Пример:

```text
lastProcessedEventVersion
processedMessageIds
sourceEventId
```

Може да е по-естествено, ако business row вече има подходящ atomic conditional update.

Pattern-ът не изисква задължително отделна INBOX таблица.

---

# 17. Idempotency не решава ordering

Тези са различни проблеми:

```text
duplicate event
vs
out-of-order event
```

Пример:

```text
OrderCreated v1
OrderCancelled v2
```

Ако consumer получи v2 преди v1, duplicate detection не помага.

Ordering може да изисква:

- aggregate sequence/version;
- partitioning по aggregate ID;
- buffering/rejection на gaps;
- monotonic version checks.

---

# 18. Aggregate ID като broker key

При Kafka common approach е events от един aggregate да използват `aggregateId` като message key.

Така те попадат в една partition и могат да запазят partition ordering.

Debezium Outbox Event Router също използва `aggregateid` като event key по подразбиране именно с оглед правилния partition/order behavior.

Но:

> Kafka ordering е partition-local, не global ordering на всички events.

---

# 19. Polling с повече от един relay instance

Нашият lab нарочно има един relay, за да изолира fundamental semantics.

Production system често има N instances.

Naive:

```text
relay A reads row X
relay B reads row X
both publish X
```

Duplicates пак са допустими, но може да получим ненужно amplification и ordering проблеми.

Common approaches:

- row claim/status + lease;
- `SELECT ... FOR UPDATE SKIP LOCKED`;
- database advisory locks;
- partitioned ownership;
- CDC вместо application polling.

PostgreSQL и Oracle поддържат `SKIP LOCKED`, което е подходящо за queue-like access patterns.

Важно:

> Не дръж database transaction/row lock отворен през бавен broker network call без да си анализирал lock duration и failure behavior.

Често се прави кратък atomic claim, после publish извън claim transaction-а.

---

# 20. Claim / lease state machine

По-развит polling publisher може да има:

```text
NEW
→ CLAIMED(owner, leaseUntil)
→ PUBLISHED
```

Ако relay instance умре:

```text
lease expires
→ друг relay може да reclaim-не
```

Това намалява конкурентното дублиране, но не премахва broker-publish/ack failure window-а.

Consumer idempotency остава необходима.

---

# 21. CDC / Transaction Log Tailing

Вместо application poller:

```text
business transaction
→ INSERT outbox row

database WAL/binlog/redolog
→ CDC connector
→ broker
```

Debezium е популярна реализация.

Предимства:

- няма application polling loop;
- ниска latency;
- natural integration с transaction log;
- business service само insert-ва outbox row.

Цена:

- CDC infrastructure;
- connector operations;
- schema/configuration governance;
- database-specific connector behavior.

---

# 22. Нашият polling schema vs Debezium outbox schema

Нашият lab използва:

```text
published_at
```

за polling acknowledgement.

Debezium Outbox Event Router обикновено очаква **insert-only outbox**:

```text
id
aggregatetype
aggregateid
type
payload
```

и transaction-log capture публикува INSERT-а.

Тоест не трябва механично да копираме `published_at` design при CDC implementation.

Relay strategy влияе на schema/lifecycle.

---

# 23. Cleanup и retention

Outbox и processed-message tables растат.

Трябва explicit policy:

```text
published outbox retention
processed-message dedupe retention
archive/delete schedule
```

Но cleanup на Inbox има semantic цена:

```text
delete old processed event ID
→ много късна redelivery
→ consumer може да го обработи отново
```

Retention трябва да е съобразен с broker redelivery/replay window и business requirements.

---

# 24. Poison messages

Idempotency не решава message, който винаги fail-ва.

```text
bad schema
invalid business data
unsupported event version
```

Нужни са policy решения:

- bounded retries;
- DLQ / parking lot;
- alerting;
- manual repair/replay;
- schema compatibility.

Не трябва един poison event безкрайно да блокира partition/queue processing.

---

# 25. Event schema evolution

Outbox payload е integration contract.

Трябва да мислим за:

- event type;
- schema/version;
- backward compatibility;
- additive vs breaking changes;
- tolerant readers;
- serializer format;
- contract tests.

JPA entity JSON не е добра integration schema по подразбиране.

---

# 26. Domain Event vs Integration Event

Следващите модули ще разграничат това подробно.

Useful mental model:

```text
Domain Event
→ факт вътре в domain model-а

Integration Event
→ serialized external contract за други services
```

Понякога един domain event се map-ва към integration event.

Не е задължително вътрешният domain object да се публикува директно.

---

# 27. Spring Kafka Exactly Once Semantics

Spring Kafka поддържа Kafka transactions и exactly-once semantics за Kafka read-process-write flow.

Но това не трябва да се превежда като:

> arbitrary database + Kafka business workflow вече е magical exactly-once

DB state и Kafka transaction имат отделни boundaries/coordination semantics.

Outbox остава много полезен, когато source of truth е relational database и искаме DB commit да бъде atomic с durable intent-to-publish.

---

# 28. Outbox не е event sourcing

Outbox:

```text
current business state се пази нормално
+
event/message row за integration
```

Event Sourcing:

```text
events са primary source of truth
→ current state се rebuild-ва от event history
```

Това са фундаментално различни patterns.

---

# 29. Outbox не е Saga

Outbox решава:

> reliable DB → message publication.

Saga решава:

> distributed business transaction чрез local transactions + coordination/compensation.

Saga често **използва** Transactional Outbox за reliable messaging.

Следователно Outbox е building block за следващия Saga module.

---

# 30. Observability

Producer metrics:

- unpublished outbox count;
- oldest unpublished age;
- publish attempts/failures;
- relay throughput;
- claim lease expirations.

Consumer metrics:

- processed count;
- duplicate count;
- processing failures;
- oldest retry age;
- DLQ count;
- processing latency.

Critical alert:

```text
oldest unpublished outbox age increasing
```

може да означава, че relay pipeline е счупен, дори application writes да изглеждат healthy.

---

# 31. Database differences: PostgreSQL и Oracle

Pattern-ът е database-agnostic, но polling implementation не е напълно.

## PostgreSQL

`FOR UPDATE SKIP LOCKED` е удобен за multiple workers върху queue-like table.

## Oracle

`SELECT FOR UPDATE` има `NOWAIT`, `WAIT` и `SKIP LOCKED` semantics; locks живеят до commit/rollback.

Production lab при конкретна база трябва да тества:

- lock behavior;
- ordering;
- batch size;
- index usage;
- transaction isolation;
- cleanup plan;
- LOB/JSON payload choices.

H2 в този module е само deterministic CI database.

---

# Какво доказват тестовете

## Producer side

```text
BAD DB-then-publish:
DB order = 1
broker events = 0

Transactional Outbox rollback:
orders = 0
outbox = 0

Transactional Outbox commit:
orders = 1
outbox = 1
broker = 0 before relay
```

## Relay side

```text
publish succeeds
ack state not persisted
→ retry publishes same event ID again
```

## Consumer side

```text
same event twice
→ business effect once

8 concurrent duplicate deliveries
→ 1 PROCESSED
→ 7 DUPLICATE
→ loyalty +10 once

consumer failure before commit
→ inbox claim rollback
→ business effect rollback
→ next delivery can succeed
```

---

# Как да стартираме

От root:

```bash
mvn -pl spring/messaging/outbox-inbox -am test
```

Стартиране:

```bash
mvn -pl spring/messaging/outbox-inbox spring-boot:run
```

IntelliJ HTTP Client:

- [`http/outbox-inbox-demo.http`](./http/outbox-inbox-demo.http)

---

# Mental model за запомняне

1. **DB + broker direct dual write има unavoidable consistency window без distributed transaction.**
2. **Outbox atomically записва business state + intent-to-publish в една DB transaction.**
3. **Relay publication е at-least-once: duplicate publish е възможен.**
4. **Event ID трябва да е стабилен през retries.**
5. **Consumer dedupe check сам по себе си не е concurrency-safe; нужна е atomic DB constraint/conditional write.**
6. **Inbox claim + business effect трябва да commit/rollback-нат заедно.**
7. **Idempotency решава duplicates, не ordering.**
8. **Polling Publisher и CDC са различни relay strategies.**
9. **Outbox не е Saga и не е Event Sourcing.**
10. **Целта е reliable at-least-once delivery + idempotent effect, не магическо exactly-once execution навсякъде.**

---

# Как да разпозная казуса

```text
[ ] Update-ваме DB и после publish-ваме Kafka/Rabbit event?
[ ] Какво става ако process-ът спре между двете?
[ ] Publish-ваме ли message преди transaction commit?
[ ] Има ли stable event/message ID?
[ ] Relay може ли да publish-не duplicate?
[ ] Consumer side effect naturally idempotent ли е?
[ ] Duplicate check concurrency-safe ли е?
[ ] Inbox record и business effect в една transaction ли са?
[ ] Трябва ли per-aggregate ordering?
[ ] Multiple relay instances как claim-ват work?
[ ] Как чистим outbox/inbox history?
[ ] Какво правим с poison events?
```

---

# Code-review checklist

```text
[ ] Business row + outbox row atomic ли са?
[ ] Event payload е integration contract, а не serialized entity?
[ ] Event ID unique и stable ли е?
[ ] aggregateId използва ли се за ordering/routing, ако е нужно?
[ ] Relay semantics polling ли са или CDC?
[ ] Multiple relay instances имат ли claim/locking strategy?
[ ] Broker publish success преди DB ack води ли до safe duplicate?
[ ] Consumer има ли database-backed dedupe guard?
[ ] Dedupe guard + effect atomic ли са?
[ ] Duplicate retention window дефиниран ли е?
[ ] Poison-message policy има ли?
[ ] Outbox lag/age observable ли е?
[ ] Schema evolution policy има ли?
```

---

# Упражнения

1. Добави `event_version` към outbox payload envelope.
2. Добави aggregate sequence и reject-ни out-of-order events.
3. Направи relay batch processing.
4. Добави claim/lease state machine.
5. Реализирай PostgreSQL `FOR UPDATE SKIP LOCKED` publisher.
6. Реализирай Oracle equivalent и сравни locking semantics.
7. Добави cleanup job с configurable retention.
8. Добави poison-event retry + DLQ simulation.
9. Замени polling relay с Debezium design/config documentation.
10. Свържи module-а със следващия Saga lab.

---

# Оригинални източници

- Microservices.io — Transactional Outbox: https://microservices.io/patterns/data/transactional-outbox
- Microservices.io — Polling Publisher: https://microservices.io/patterns/data/polling-publisher.html
- Microservices.io — Transaction Log Tailing: https://microservices.io/patterns/data/transaction-log-tailing.html
- Microservices.io — Idempotent Consumer: https://microservices.io/patterns/communication-style/idempotent-consumer.html
- Debezium — Outbox Event Router: https://debezium.io/documentation/reference/stable/transformations/outbox-event-router.html
- Spring Kafka — Transactions: https://docs.spring.io/spring-kafka/reference/kafka/transactions.html
- Spring Kafka — Exactly Once Semantics: https://docs.spring.io/spring-kafka/reference/kafka/exactly-once.html
- PostgreSQL — SELECT / SKIP LOCKED: https://www.postgresql.org/docs/current/sql-select.html
- Oracle Database 19c — SQL Processing / SELECT FOR UPDATE: https://docs.oracle.com/en/database/oracle/oracle-database/19/adfns/sql-processing-for-application-developers.html

---

# Изходен въпрос

Когато видиш:

```text
save business data
then
publish event
```

питай:

> **Какво става, ако първата system boundary успее, а execution спре преди втората — и ако retry после произведе duplicate, кой гарантира, че consumer side effect-ът ще остане един?**
