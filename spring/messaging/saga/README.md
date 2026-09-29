# Saga Pattern — distributed business transaction without global ACID

Този модул стъпва върху:

- Idempotency;
- Retry/Timeout/Circuit Breaker/Bulkhead;
- Transactional Outbox + Inbox.

Основният въпрос е:

> Как изпълняваме business workflow през няколко отделни services/datastores, когато няма една ACID transaction, която да обхване всички стъпки?

---

# ВХОД В ТЕМАТА

## Реален казус

Order workflow:

```text
reserve payment
→ reserve inventory
→ schedule shipping
```

Ако всичко е в една база, може да използваме една transaction.

Но в microservice architecture:

```text
Payment Service     own DB
Inventory Service   own DB
Shipping Service    own DB
```

нямаме normal `@Transactional`, която да rollback-не трите databases.

---

# Naive mental model, който трябва да избегнем

```text
@Transactional
payment.reserve()
inventory.reserve()
shipping.schedule()
```

Spring transaction boundary не се превръща автоматично в distributed transaction през remote services.

Ако:

```text
payment commit ✅
inventory commit ✅
shipping fails ❌
```

не можем просто да `ROLLBACK` payment и inventory local transactions — те вече са commit-нати.

---

# Saga mental model

Saga разделя една distributed business transaction на sequence от **local transactions**.

```text
T1 reserve payment
→ T2 reserve inventory
→ T3 schedule shipping
```

Ако по-късна стъпка fail-не, Saga изпълнява **compensating transactions**:

```text
C2 release inventory
→ C1 release/refund payment
```

Compensation не е technical rollback.

Тя е нова business operation, която семантично компенсира вече commit-натия effect.

---

# Executable architecture

Нашият lab използва orchestration:

```text
OrderSagaOrchestrator
   │
   ├→ PaymentParticipantService     REQUIRES_NEW
   ├→ InventoryParticipantService   REQUIRES_NEW
   └→ ShippingParticipantService    REQUIRES_NEW
```

Persistent saga state:

- [`OrderSaga.java`](./src/main/java/bg/hristomanov/education/saga/domain/OrderSaga.java)

Orchestrator:

- [`OrderSagaOrchestrator.java`](./src/main/java/bg/hristomanov/education/saga/service/OrderSagaOrchestrator.java)

Participants:

- [`PaymentParticipantService.java`](./src/main/java/bg/hristomanov/education/saga/service/PaymentParticipantService.java)
- [`InventoryParticipantService.java`](./src/main/java/bg/hristomanov/education/saga/service/InventoryParticipantService.java)
- [`ShippingParticipantService.java`](./src/main/java/bg/hristomanov/education/saga/service/ShippingParticipantService.java)

---

# Защо `REQUIRES_NEW`?

Това е **лабораторна симулация** на отделни local service transactions.

Всички tables са в една H2 database само за deterministic CI, но всяка participant операция commit-ва в собствена transaction.

Така можем да докажем:

```text
payment transaction commit-ва
inventory transaction после fail-ва
payment не се rollback-ва автоматично
→ нужна е compensation
```

В production отделните participants могат да са различни services с различни databases.

Не трябва да тълкуваме H2 lab-а като истинска distributed topology.

---

# Happy path

```text
payment:reserve
inventory:reserve
shipping:schedule
→ saga COMPLETED
```

Test:

`happyPathCompletesAllLocalTransactions()`

State:

```text
payment   RESERVED
inventory RESERVED
shipment  SCHEDULED
saga      COMPLETED
```

---

# Inventory failure

Forward:

```text
payment reserve ✅
inventory reserve ❌
```

Compensation:

```text
payment release ✅
```

Final state:

```text
payment RELEASED
saga REJECTED
```

Това е semantic undo.

Payment reservation transaction е била реално commit-ната; compensation е втора local transaction.

---

# Shipping failure и reverse compensation order

Forward order:

```text
payment
→ inventory
→ shipping
```

Compensation обикновено върви обратно:

```text
shipping cancel
→ inventory release
→ payment release/refund
```

В конкретния test shipping operation fail-ва преди да commit-не shipment state, затова няма shipment за cancel.

Trace:

```text
payment:reserve
inventory:reserve
shipping:schedule
inventory:release
payment:release
```

---

# Compensation също може да fail-не

Това е критично.

Не мисли:

> ако Saga fail-не, просто пускаме compensations и всичко е готово.

Compensation може също да срещне:

- network failure;
- downstream outage;
- concurrency conflict;
- business restriction;
- timeout.

Lab scenario:

```text
payment reserved ✅
inventory fails ❌
payment compensation fails ❌
```

Saga state става:

```text
COMPENSATION_REQUIRED
```

а payment остава:

```text
RESERVED
```

Това е persistent operational state, който не трябва да се губи.

После:

```text
retryCompensation(sagaId)
→ payment release
→ saga REJECTED
```

Test:

`failedCompensationIsPersistentStateAndCanBeRetried()`

---

# Защо Saga state трябва да е durable

Ако orchestration state живее само в memory:

```text
payment reserved
→ process restarts
→ забравяме коя стъпка е изпълнена
```

тогава не можем надеждно да resume-ваме или компенсираме.

Production orchestrator обикновено пази:

- saga ID;
- current state;
- executed steps;
- pending command;
- failure reason;
- retry/compensation status;
- timestamps/version.

Нашият `OrderSaga` е минимална учебна версия.

---

# Participant operations трябва да са idempotent

Distributed delivery/retry означава, че:

```text
reserve payment command
```

може да пристигне повече от веднъж.

Participant service-ите в lab-а проверяват дали state вече съществува и не създават втори reservation.

Compensations също са idempotent:

```text
release already released payment
→ no-op
```

Това директно използва mental model-а от Idempotency module-а.

---

# Saga + Outbox

В реална asynchronous Saga всяка local стъпка обикновено трябва да направи атомарно:

```text
update local DB
+
publish next command/event intent
```

Точно това решава Transactional Outbox.

Пример:

```text
Payment Service transaction:

reserve payment
insert PaymentReserved outbox event
COMMIT
```

После relay публикува event-а.

Без Outbox Saga messaging itself може да има dual-write hole.

Microservices.io изрично посочва Outbox/Event Sourcing като начини local state update + message publication да бъдат reliable.

---

# Orchestration vs Choreography

## Orchestration

```text
           Orchestrator
          /     |      \
      Payment Inventory Shipping
```

Orchestrator-ът знае workflow-а и изпраща commands.

Плюсове:

- workflow е видим на едно място;
- compensation order е explicit;
- observability/state machine са по-лесни;
- сложни flows са по-лесни за reason-ване.

Минуси:

- orchestrator може да стане централен coupling point;
- трябва да се пази от God Object design;
- participant contracts трябва да останат ясни.

## Choreography

```text
OrderCreated
→ PaymentReserved
→ InventoryReserved
→ ShippingScheduled
```

Всеки participant реагира на events и публикува следващи events.

Плюсове:

- няма централен coordinator;
- services са event-driven;
- простите flows могат да са много естествени.

Минуси:

- workflow се разпределя между много handlers;
- трудно се вижда end-to-end;
- event coupling расте;
- compensation flow може да стане труден за проследяване.

---

# Saga vs distributed transaction

Saga избира:

```text
eventual consistency
+
local ACID transactions
+
compensation
```

вместо една глобална ACID transaction.

Това означава, че между стъпките системата може да бъде във visible intermediate state.

Например:

```text
payment RESERVED
inventory още не е RESERVED
saga RUNNING
```

Domain/UI трябва да знае какво означава това.

---

# Compensation не винаги може да възстанови света

Пример:

```text
email sent
physical parcel shipped
external bank transfer settled
```

някои actions нямат истински inverse.

Compensation може да бъде:

- refund вместо undo payment;
- cancellation request;
- corrective event;
- manual intervention.

Затова compensation е business semantics, не `rollback()`.

---

# Isolation anomalies

Saga няма global transaction isolation.

Друг workflow може да види intermediate state и да вземе решение върху него.

Трябва да мислим за:

- semantic locks;
- reservation states;
- optimistic concurrency;
- version checks;
- commutative operations;
- reread before critical decision.

Примерът с inventory използва `RESERVED` state именно защото reservation е по-безопасна Saga primitive от необратим final decrement без compensation policy.

---

# Timeouts

Orchestrator не трябва да чака command reply безкрайно.

Step може да стане:

```text
COMMAND_SENT
→ timeout
→ status unknown
```

Важно:

> timeout не означава, че participant operation не е изпълнена.

Оттук следват:

- idempotent command IDs;
- status query/reconciliation;
- retry policy;
- durable saga state.

Това се връзва с Fault Tolerance module-а.

---

# Saga completion и business state

Не е добра идея да маркираме Order като окончателно COMPLETED още преди Saga да е приключила.

Често имаме states като:

```text
PENDING
APPROVED
REJECTED
CANCELLATION_PENDING
```

Business state трябва честно да показва eventual-consistency lifecycle-а.

---

# Saga vs Process Manager

Термините често се припокриват.

Useful distinction:

- Saga — distributed transaction consistency pattern с local transactions + compensations;
- Process Manager — component, който управлява long-running workflow/state transitions.

Orchestration-based Saga често използва Process Manager-like orchestrator.

Не се фиксираме върху името; гледаме responsibility-то.

---

# Saga vs State pattern

GoF State:

> behavior на един object зависи от current state.

Saga:

> long-running distributed business transaction през local transactions и compensation.

Saga orchestrator често **използва state machine**, но това не прави Saga == State pattern.

---

# Saga vs Outbox

```text
Outbox
→ reliable message publication

Saga
→ distributed business workflow + compensation
```

Saga обикновено използва Outbox като infrastructure building block.

---

# Какво доказва тестът

[`OrderSagaOrchestratorTest.java`](./src/test/java/bg/hristomanov/education/saga/OrderSagaOrchestratorTest.java)

Доказва:

```text
happy path
→ COMPLETED

inventory failure
→ payment compensation

shipping failure
→ reverse compensation order

compensation failure
→ COMPENSATION_REQUIRED
→ later retry succeeds
```

---

# Как да стартираме

```bash
mvn -pl spring/messaging/saga -am test
```

---

# Mental model за запомняне

1. **Saga е sequence от local transactions, не distributed `@Transactional`.**
2. **Compensation е нова business transaction, не rollback на стар commit.**
3. **Compensation също може да fail-не.**
4. **Saga state трябва да бъде durable.**
5. **Commands и compensations трябва да са idempotent.**
6. **Outbox/Inbox правят Saga messaging reliable.**
7. **Orchestration и Choreography са coordination styles, не различни consistency goals.**
8. **Intermediate states са реални и трябва да бъдат моделирани.**

---

# Code-review checklist

```text
[ ] Кои local transactions участват?
[ ] След кои steps е нужна compensation?
[ ] Compensation order explicit ли е?
[ ] Compensation itself retry-able/idempotent ли е?
[ ] Saga state durable ли е?
[ ] Има ли unique command/message identity?
[ ] DB update + event/command publication използва ли Outbox?
[ ] Consumer commands/events idempotent ли са?
[ ] Timeout означава ли UNKNOWN вместо automatic failure?
[ ] Intermediate business states валидни ли са?
[ ] Какво става ако compensation никога не успее?
[ ] Има ли manual intervention/reconciliation path?
```

---

# Упражнения

1. Добави `PAYMENT_RESERVED`, `INVENTORY_RESERVED`, `SHIPPING_SCHEDULED` explicit saga states.
2. Добави persistent step log.
3. Добави timeout state и resume след restart.
4. Замени synchronous participant calls с commands/events.
5. Използвай Outbox module-а за всеки Saga transition.
6. Добави Inbox dedupe по `commandId`.
7. Реализирай choreography вариант и сравни observability.
8. Добави optimistic version към Saga state.
9. Добави compensation retry backoff.
10. Добави manual intervention state.

---

# Оригинални източници

- Microservices.io — Saga: https://microservices.io/patterns/data/saga.html
- Microservices.io — Saga coordination: https://microservices.io/post/sagas/2019/08/04/developing-sagas-part-2.html
- Microservices.io — Transactional Outbox: https://microservices.io/patterns/data/transactional-outbox
- Chris Richardson — Microservices Patterns (Saga / Transactional Messaging chapters).

---

# Изходен въпрос

Когато чуеш:

> ако третата стъпка fail-не, ще rollback-нем всичко

питай:

> **Предишните стъпки вече commit-нати ли са в други services? Ако да, какви compensating business operations ще изпълним, как ще ги retry-нем и къде ще пазим durable state-а на този процес?**
