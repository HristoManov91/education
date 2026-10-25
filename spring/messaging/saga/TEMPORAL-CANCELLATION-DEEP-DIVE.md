# Temporal Saga Deep Dive — cancellation, unknown outcomes и reconciliation

> **Приоритет:** Tech Radar → **P1 / Deep Technical Lecture** — System Design, Distributed Systems, Reliability.
>
> **Ниво:** Mid+ → Early Senior. **Фокус:** конкретни production failure modes, не въведение в Saga.
>
> **Основа:** [Saga Pattern — Fundamentals](./README.md) и [Idempotency](../../reliability/idempotency/README.md).
>
> **Статус на доказателствата:** тук имаме **два отделни слоя**: (1) независими детерминистични Java/JUnit state-machine тестове и (2) **реален Temporal Java SDK 1.40.0** Workflow/Worker със самостоятелен **TestWorkflowEnvironment** (in-memory Temporal service) и интеграционни JUnit тестове. Последните упражняват реални workflow/activity/cancellation primitives, но **не** представляват тест срещу production Temporal Cluster, реален HTTP provider, persistence или WAN. GitHub Actions/CI потвърждава execution резултатите, когато проверките са завършили.

## 1. Реалният казус — клиентът чака на терминала

Във fintech система едно плащане изисква блокиране на средства в **две независими системи**: credit и debit. Клиентът чака синхронен отговор, а backend-ът оркестрира два remote HTTP calls през Temporal.

~~~text
Customer / POS
    │ POST /hold  (кратък HTTP deadline)
    ▼
Operations API ───── Temporal Workflow
                         │
                         ├── hold credit ──► Credit Service / DB
                         └── hold debit  ──► Debit Service / DB
~~~

Business invariant: операцията е успешно потвърдена, само ако всички задължителни hold операции са потвърдени. При отказ искаме **нито една остатъчна блокировка**, или ясно видимо, активно състояние за възстановяване.

**Проблемът:** за разлика от една DB transaction, remote call може да е променил истинското състояние, въпреки че получаваме exception, timeout или cancellation. Нямаме глобален atomic commit.

Лекцията на Andrei Sulyz (Skyro) показва няколко последователни „поправки“, всяка от които изглежда правилна, но открива нов failure mode. Това е ценният инженерeн урок.

**Преди Deep Dive:** прочети [основния Saga lesson](./README.md) — local transactions, compensation ≠ rollback, durable state, idempotency, Outbox.

## 2. Първият капан: response lost ≠ операцията не е изпълнена

**Видеото:** [07:04 — compensation registration](https://www.youtube.com/watch?v=c45C2wNSZFc&t=424s).

~~~text
T0   Orchestrator sends HOLD(op-7)
T1   Credit Service commits HELD(op-7)
T2   HTTP response is lost
T3   Orchestrator receives timeout/exception
T4   Saga tries to compensate
~~~

**BAD — регистрацията е след remote call:**

~~~java
activities.holdCredit(operationId);
saga.addCompensation(activities::releaseCredit, operationId);
~~~

При exception между T1 и T3 вторият ред никога не е достигнат. Hold остава, но няма регистриран начин за release.

**GOOD — регистрацията е преди remote call:**

~~~java
saga.addCompensation(activities::releaseCredit, operationId);
activities.holdCredit(operationId);
~~~

Тук компенсацията може да се стартира дори ако Activity не е върнала успех.

**Нова отговорност:** компенсацията трябва да е идемпотентна и безопасна, когато hold изобщо **не е започнал**. „Release unknown operation → no-op“ обаче **не е достатъчно**, ако закъснял hold може да се изпълни след release. Този втори проблем ще докажем в лабораторията.

**Mental model:** network error означава **UNKNOWN outcome**, не автоматично **FAILED side effect**.

## 3. Вторият капан: cancelled Workflow и detached cancellation scope

**Видеото:** [08:43–13:12 — защо обичайният catch не стига](https://www.youtube.com/watch?v=c45C2wNSZFc&t=523s).

Temporal изпълнява Workflow кода в root cancellation scope. При Workflow cancellation той и обичайните му child scopes получават заявка за отмяна. Нова Activity, извикана директно от вече cancelled scope, може да бъде отменена и компенсацията да не успее.

Това не е обикновен Java `Thread.interrupt()`. Temporal има собствена cooperative cancellation семантика, а Activity code и Workflow code са с различен runtime model.

**Правилен механизъм за cleanup в Java SDK:**

~~~java
try {
    // forward steps
} catch (RuntimeException originalFailure) {
    CancellationScope cleanup =
            Workflow.newDetachedCancellationScope(saga::compensate);
    cleanup.run();
    throw originalFailure;
}
~~~

- `Workflow.newDetachedCancellationScope(...)` не наследява cancellation на parent scope.
- **Задължително** извикваме `run()` — само създаване на scope не изпълнява cleanup.
- След успешни compensations трябва да запазим коректния terminal outcome, а не да маркираме failed/cancelled операция като успешна.
- **Важно:** `saga.compensate()` също може да fail-не. Продукционната версия трябва да съхрани оригиналния failure, да регистрира незавършеното възстановяване и да предвиди retry/manual intervention. Горният фрагмент е **минималният API механизъм**, не пълна error-handling policy.

**Отделен anti-pattern:** `terminate()` не е еквивалент на graceful `cancel()` — не разчитаме, че при terminate ще се изпълни Workflow cleanup.

## 4. Третият капан: cancellation race и „release before hold“

**Видеото:** [13:45–17:46 — live демонстрацията](https://www.youtube.com/watch?v=c45C2wNSZFc&t=825s).

Да приемем, че hold HTTP call има 8 секунди latency. Докато Activity чака, потребителят или caller-ът иска cancellation.

~~~text
Worker                 Orchestrator              Credit Service
  │ HOLD ──────────────────────────────────────────► [in flight]
  │                      │ CANCEL requested
  │                      │ Saga releaseCredit ───► no reservation yet → no-op
  │                      │ workflow ends
  │                                              └─► HOLD commits later!
                                                        ORPHANED HOLD
~~~

**Причината:** Temporal cancellation на Activity **не отменя магически външния HTTP request** и не доказва, че downstream side effect е прекратен.

Дори да сме регистрирали компенсацията преди извикването и да я изпълним в detached scope, пак може да загубим състезанието между `hold` и `release`.

Това е различен проблем от липсващата компенсация: **има компенсация, но тя пристига твърде рано**.

## 5. ActivityCancellationType — три различни договора

**Видеото:** [14:37–20:22 — cancellation type и финалната стъпка](https://www.youtube.com/watch?v=c45C2wNSZFc&t=877s).

| Тип | Поведение при отмяна | Капан |
| --- | --- | --- |
| `TRY_CANCEL` | Изпраща заявка за отмяна и бързо уведомява Workflow | Activity/външният HTTP side effect може да продължи |
| `WAIT_CANCELLATION_COMPLETED` | Чака завършване или потвърждение за отмяна на Activity | Може да чака дълго, ако Activity не прави heartbeat; не е гаранция за глобална консистентност |
| `ABANDON` | Не изпраща Activity cancellation request; Workflow продължава с логиката по отмяна | Страничният ефект може да продължи без контрол |

В SDK конфигурацията изглежда така (фрагмент, не цял компилируем клас):

~~~java
ActivityOptions options = ActivityOptions.newBuilder()
        .setStartToCloseTimeout(Duration.ofSeconds(30))
        .setCancellationType(ActivityCancellationType.WAIT_CANCELLATION_COMPLETED)
        .build();
~~~

**Защо WAIT не е окончателното решение?** В показаната реализация Activity може да приключи нормално след заявена cancellation. Ако това е **последната** Activity и кодът разчита единствено на exception за задействане на компенсация, Workflow може да пропусне cleanup.

Точката за проверка е **след всяка потенциално последна side-effecting стъпка**, включително след последната Activity, а не само преди следващата:

~~~java
saga.addCompensation(activities::releaseCredit, operationId);
activities.holdCredit(operationId);
CancellationScope.throwCanceled();  // check current scope

saga.addCompensation(activities::releaseDebit, operationId);
activities.holdDebit(operationId);
CancellationScope.throwCanceled();  // CRITICAL after the last activity
~~~

`CancellationScope.throwCanceled()` проверява дали текущият scope е отменен и тогава хвърля `CanceledFailure`. Това помага на Workflow control flow, но **не доказва**, че закъснял външен side effect не може да се появи по-късно.

### Activity heartbeat ≠ външно HTTP cancellation

Temporal доставя cancellation към дълго работещи remote Activities чрез heartbeat механизма. Heartbeat timeout и подходящ heartbeat cadence са важни за cooperative cancellation. Но heartbeat/interrupt на Worker **не означава**, че кредитната система е отменила вече приетата операция.

При голи HTTP calls трябва отделно да проектираме:

- per-request timeout и cancel contract на downstream service;
- endpoint за query на състоянието по operation ID;
- idempotent `hold` / `release`;
- атомарна проверка на cancellation intent в authoritative participant;
- reconciliation, ако definitive outcome все още не е известен.

Local Activities имат различен cancellation/heartbeat модел; не пренасяй същите настройки механично към тях.

## 6. Синхронният caller: четири различни срока

**Видеото:** [23:48–26:21 — връзката HTTP ↔ Temporal](https://www.youtube.com/watch?v=c45C2wNSZFc&t=1428s).

Разграничи:

1. **HTTP request deadline:** колко дълго клиентът ще чака.
2. **Workflow execution lifetime:** колко дълго бизнес операцията може да продължи/се възстановява.
3. **Activity attempt timeout:** ограничение за единичен attempt.
4. **Business reservation deadline:** максимално допустимата възраст на блокировката.

**Не са едно и също.** Timeout на `workflowStub.getResult(...)` казва само, че caller-ът не е получил резултат в своя прозорец.

~~~text
Client POST /hold(op-19)
    → start/attach Workflow ID = "hold:" + tenant + ":" + op-19
    → wait up to HTTP response budget
       ├─ completed: return definitive outcome
       └─ timed out:
          ├─ request cancellation (ако business policy го изисква)
          └─ return operation ID + non-final status / explicit error contract
~~~

**Idempotent attach:** стабилен Workflow ID, основан на business operation ID, ни позволява при retry да намерим вече стартирания Workflow, вместо да започнем независимо второ изпълнение.

Не спирай до `WorkflowExecutionAlreadyStarted`:
- **Workflow ID conflict policy** управлява нов старт, докато execution с това ID работи.
- **Workflow ID reuse policy** управлява повторна употреба след приключил execution.
- Възстановяване по `operationId` трябва да валидира payload fingerprint и tenant scope; еднакъв ID с различна сума не е retry.
- Идентичността на Workflow не заменя идемпотентността на downstream Activities.

**Алтернатива:** ако бизнесът допуска eventual outcome, `202 Accepted + operationId` и status endpoint често са по-честен API contract от „успех/неуспех“ след кратък HTTP timeout. Ако POS/плащането изисква моментално окончателно решение, трябва да приемем, че timeout може да означава `UNKNOWN / RECOVERY_PENDING` и да дефинираме целия последващ lifecycle.

## 7. Participant-side fencing / cancellation tombstone

За да се избегне race от раздел 4, **самият authoritative participant** може да пази монотонно състояние за всяко `operationId`:

~~~text
ABSENT ──hold──► HELD ──compensate──► RELEASED
   │                                      │
   └──compensate──► CANCEL_REQUESTED      └─late hold──► RELEASED
                          │
                          └──late hold──► CANCEL_REQUESTED
~~~

Това е **примерен бизнес дизайн**, не built-in Temporal функция.

Условието е transitions и истинският business side effect да са **атомарно защитени** в съответния participant (DB transaction/constraints, CAS/locking и правилна интеграция с външния provider).

В нашата минимална Java лаборатория:

- [`HoldingParticipant.java`](./src/main/java/bg/hristomanov/education/saga/cancellation/HoldingParticipant.java) — синхронизиран in-memory state model с cancellation marker и идемпотентни transitions;
- [`HoldingCancellationRaceTest.java`](./src/test/java/bg/hristomanov/education/saga/cancellation/HoldingCancellationRaceTest.java) — проверка на конкретните interleavings.

**Ограничение:** `synchronized` + `HashMap` не са durable и не са безопасни за множество instances, рестарти или независим provider. Тези тестове доказват **логиката на state transitions**, а не distributed consistency и не поведението на Temporal.

**Защо не е достатъчно „release if exists“:** ако release не оставя marker/tombstone, следващ закъснял hold отново може да блокира средствата. Продължителността на marker retention трябва да надхвърля прозореца за закъснели retries/commands, или да има друг доказан fencing механизъм.

## 8. Reconciliation — последната линия, а не заместител на защитите

**Видеото:** [22:40–23:33 — защо само workflow не стига](https://www.youtube.com/watch?v=c45C2wNSZFc&t=1360s).

Skyro използват отделен Reconciliation Service за проверка на несъответствия между операцията и участващите системи. Той допълва, а не замества надеждните Workflow compensations.

Практична матрица:

| Authoritative business intent | Participant status | Действие |
| --- | --- | --- |
| `HELD` | `HELD` | Потвърди съответствието |
| `CANCELLED` | `HELD` | Идемпотентен release, retry, audit |
| `CANCELLED` | `ABSENT` | Провери in-flight commands; осигури tombstone/fencing |
| `HELD` | `ABSENT` | Query/retry hold със същия operation ID според contract |
| `UNKNOWN` | което и да е | Не гадай — query provider/event log, отложи решение, алармирай |

**Не изпълнявай автоматично destructive „repair“, ако не е ясно коя система държи окончателния business intent.**

Минимален reconciliation runbook:

1. По `operationId` вземи **durable** intent от orchestration/source-of-truth.
2. Прочети действителното състояние от всеки participant.
3. Сравни ги с business invariant-а и с възможните in-flight requests.
4. Използвай същия idempotency key за коригираща команда; запиши опит/резултат.
5. При неизвестен status, постоянна грешка или конфликт — `RECOVERY_REQUIRED` + alert/manual intervention.
6. Измервай `orphaned_holds`, `oldest_unresolved_hold_age`, `compensation_failures`, `reconciliation_lag` и `cancellation_to_release_latency`.

**Контрапример:** помисли дали може да настъпи нов hold точно след reconciliation scan, преди да завърши repair. Reconciliation само по себе си не елиминира races — трябва да работи съвместно с fencing/idempotency и подходящо event/scan покритие.

## 9. README → код / доказателство

| Концепция | Демонстрация | JUnit сценарий |
| --- | --- | --- |
| Premature compensation | Naive participant без cancellation marker | `naiveCompensationBeforeDelayedHoldLeavesFundsReserved` |
| Tombstone before hold | [HoldingParticipant](./src/main/java/bg/hristomanov/education/saga/cancellation/HoldingParticipant.java) | `cancellationMarkerPreventsTheLateHoldFromRecreatingAReservation` |
| Applied side effect + lost reply | Същият participant | `responseLossAfterCommittedHoldStillRequiresCompensation` |
| Idempotent retry / compensation | Същият participant | `duplicateForwardAndCompensationAttemptsHaveOneLogicalEffect` |
| Credit/debit partial outcome | Два independent participant instances | `twoParticipantCancellationHandlesAnAlreadyHeldAndANotYetAppliedStep` |
| Reconciliation при authoritative CANCEL intent | Сравнение на expected/actual | `reconciliationRepairsAnOrphanWhenCancellationIntentIsAuthoritative` |

Команда от root на repository:

~~~bash
mvn -pl spring/messaging/saga -am test
~~~

Горните **state-model** тестове не използват Temporal SDK и проверяват само participant transitions. **Допълнително** се изпълняват Temporal интеграционните тестове по-долу с истински SDK/Worker/TestWorkflowEnvironment.

### Реализирана Temporal SDK 1.40.0 лаборатория — real Workflow + Activity Worker + test server

В основния `saga` Maven модул са добавени съвместими `io.temporal:temporal-sdk` и `io.temporal:temporal-testing` 1.40.0. Тестовият сървър е in-memory: за `mvn test` **не** е необходимо да стартираш Temporal Docker container.

| Отговорност | Реален код |
| --- | --- |
| Workflow API/operation ID | [`HoldWorkflow.java`](./src/main/java/bg/hristomanov/education/saga/temporal/HoldWorkflow.java) |
| Activity contract: външни странични ефекти | [`HoldActivities.java`](./src/main/java/bg/hristomanov/education/saga/temporal/HoldActivities.java) |
| Safe Saga orchestration, WAIT cancellation, detached scope, final cancellation check | [`HoldWorkflowImpl.java`](./src/main/java/bg/hristomanov/education/saga/temporal/HoldWorkflowImpl.java) |
| In-memory credit/debit adapters, единствено за demonstration | [`InMemoryHoldActivities.java`](./src/main/java/bg/hristomanov/education/saga/temporal/InMemoryHoldActivities.java) |
| Manual demo с Temporal CLI | [`TemporalHoldDemo.java`](./src/main/java/bg/hristomanov/education/saga/temporal/TemporalHoldDemo.java) |
| Bounded HTTP-wait boundary + attach-on-retry | [`HoldGateway.java`](./src/main/java/bg/hristomanov/education/saga/temporal/HoldGateway.java) |
| Temporal test server + реален worker + JUnit | [`TemporalHoldWorkflowIntegrationTest.java`](./src/test/java/bg/hristomanov/education/saga/temporal/TemporalHoldWorkflowIntegrationTest.java) |
| Intentional TRY_CANCEL race: naive release vs fencing | [`TemporalTryCancelRaceTest.java`](./src/test/java/bg/hristomanov/education/saga/temporal/TemporalTryCancelRaceTest.java) / [`UnsafeTryCancelHoldWorkflowImpl.java`](./src/test/java/bg/hristomanov/education/saga/temporal/UnsafeTryCancelHoldWorkflowImpl.java) |

**Действително реализирани интеграционни сценарии:**

1. Happy path през реален Workflow и две Activities.
2. Credit hold commit + изгубен ACK, със задействане на компенсация.
3. Debit hold commit + изгубен ACK, със reverse compensation.
4. Cancellation докато **последната** Activity е в полет, с detached cleanup.
5. Cancellation по време на **първата** Activity, без orphaned hold.
6. Кратък **client getResult timeout** без автоматична отмяна на Workflow.
7. Втори start със **същия Workflow ID** при активен първи execution.
8. Failed credit compensation след debit failure — failure остава видим и изисква reconciliation.
9. След client timeout повторен request **се свързва към стария Workflow** чрез `HoldGateway`, вместо да стартира нов.
10. **Истински Temporal `TRY_CANCEL` race**: release се случва преди външната Activity да завърши. При наивен участник остава orphaned debit hold; при cancellation tombstone късният hold не може да възстанови блокировката.

Изпълни:

```bash
mvn -pl spring/messaging/saga -am test
# само Temporal интеграционния тест:
mvn -pl spring/messaging/saga -Dtest=TemporalHoldWorkflowIntegrationTest test
```

Ръчен пример срещу локален Temporal Server:

```bash
temporal server start-dev
# във втори terminal от education/:
mvn -pl spring/messaging/saga -DskipTests compile exec:java \
  -Dexec.mainClass=bg.hristomanov.education.saga.temporal.TemporalHoldDemo
```

`temporal server start-dev` е отделен процес, който може да се инсталира чрез [Temporal CLI](https://docs.temporal.io/cli). Demo използва **in-memory** participants: не може да доказва crash recovery на външна база.

**Оставащи production-grade fault injection направления** (не ги бъркаме с готови тестове): `TRY_CANCEL`/heartbeat latency измерване при реален remote HTTP, Toxiproxy network faults, durable PostgreSQL participant, persist/restart/crash recovery и reconciliation срещу реален provider. `TemporalTryCancelRaceTest` доказва **локална Temporal SDK междупоследователност**, но не WAN/network semantics. Точно там са оставащите неопределености при преминаване от embedded Temporal Test Server към distributed deployment.

## 10. Кога Temporal / Saga е подходяща и кога — не

| Решение | Подходящо | Ограничение |
| --- | --- | --- |
| Temporal-orchestrated Saga | Много стъпки, retries, durable workflow history, сложен compensation flow | Трябва да познаваме SDK cancellation semantics и operational footprint |
| Custom state machine + DB | По-малък workflow, вече имаме надежден transactional store | Ние поддържаме retries, compensation, audit, monitoring |
| Async command + Outbox/Inbox | Бизнесът допуска eventual outcome и reliable delivery е приоритет | Клиентският контракт трябва да приема pending state |
| Една local ACID transaction | Данните и side effects са в един transactional boundary | Не решава external services |
| 2PC | Контролирани XA participants със силен atomicity contract | Координационна цена, locks, blocking и сложна експлоатация |

**Допълнително:** [Case Study 003 — Skyro и Temporal cancellation](../../../docs/case-studies/003-skyro-temporal-saga-cancellation.md) пази production симптомите/решенията като отделна лесно откриваема инженерна справка.

**Не взимай архитектурно решение само защото library-то има `Saga` клас.** Първо определи какво означават success, cancel, timeout и „никога да не останат блокирани пари“ в бизнеса.

## 11. Разговор за code review / интервю

1. Ако remote `hold` е commit-нал, но HTTP call е timeout-нал, какъв е outcome от гледна точка на caller-а?
2. Защо `addCompensation` след Activity е опасно? Какви изисквания възникват при преместването му преди Activity?
3. Защо detached scope е нужен след Workflow cancellation? Защо създаването без `run()` не стига?
4. Какво точно чака `WAIT_CANCELLATION_COMPLETED`? Кога може да блокира необичайно дълго?
5. Защо е нужна cancellation check и **след последната** Activity?
6. Как ще предотвратиш `release → late hold` race без да разчиташ единствено на времеви delay?
7. Как ще възстановиш операция след client timeout, без да стартираш ново логическо плащане?
8. Кои данни са нужни на reconciliation процеса, за да избегне опасен „поправящ“ side effect?

**Senior-level въпрос:** проектът трябва да освобождава блокирани средства най-късно за 30 секунди след отказ. Има недостъпен downstream, който може да обработи `hold` след 45 секунди. Какъв точно contract трябва да договориш с него, за да можеш изобщо да обещаеш това SLO? Ако не може, как ще обясниш business trade-off-а?

## 12. Mental model и практически checklist

**Основна идея:** `Workflow cancelled`, `Activity cancelled`, `HTTP timed out` и `business effect undone` са **четири различни твърдения**.

~~~text
A request failed to observe a result
          ≠
the remote effect did not happen
          ≠
an in-flight effect was cancelled
          ≠
the business state was restored
~~~

Преди production release:

- [ ] Уникална business operation identity + tenant/payload fingerprint.
- [ ] Компенсацията е регистрирана преди потенциалния side effect.
- [ ] Forward и compensation operations са идемпотентни.
- [ ] Има гаранция срещу late hold след успешен release или последващ repair process.
- [ ] `TRY_CANCEL` / `WAIT_CANCELLATION_COMPLETED` / heartbeat са избрани съзнателно.
- [ ] Cleanup работи и след cancellation, без да бъде погълнат или отчетен като успех.
- [ ] Cancellation след **последната** Activity е покрита.
- [ ] Request deadline е отделен от business completion deadline.
- [ ] Compensation failure и `UNKNOWN` състоянията остават видими и recoverable.
- [ ] Има reconciliation, аларми и измеримо максимално време до възстановяване.

## Оригинални източници

- **Лекция:** Andrei Sulyz (Skyro), *Solving Distributed Transactions with the Saga Pattern and Temporal*, Devoxx Belgium 2026, 6 октомври 2026 — https://www.youtube.com/watch?v=c45C2wNSZFc
- **Официална страница на лекцията:** https://m.devoxx.com/events/dvbe26/talks/16418/solving-distributed-transactions-with-the-saga-pattern-and-temporal
- **Статия от същия автор, 15 януари 2026:** https://medium.com/skyro-tech/solving-distributed-transactions-with-the-saga-pattern-and-temporal-27ccba602833
- **Temporal Java SDK Saga:** https://javadoc.io/static/io.temporal/temporal-sdk/1.37.0/io/temporal/workflow/Saga.html
- **Temporal Java SDK CancellationScope:** https://www.javadoc.io/static/io.temporal/temporal-sdk/1.37.0/io/temporal/workflow/CancellationScope.html
- **Temporal Java SDK Workflow:** https://javadoc.io/static/io.temporal/temporal-sdk/1.37.0/io/temporal/workflow/Workflow.html
- **Activity cancellation semantics:** https://docs.temporal.io/encyclopedia/detecting-activity-failures
- **Workflow ID policies:** https://docs.temporal.io/develop/java/nexus/development-walkthrough/send-messages
- **Официални Java примери:** https://github.com/temporalio/samples-java/blob/main/core/src/main/java/io/temporal/samples/hello/HelloSaga.java и https://github.com/temporalio/samples-java/blob/main/core/src/main/java/io/temporal/samples/hello/HelloDetachedCancellationScope.java
- **Свързана лаборатория:** [Saga Fundamentals](./README.md), [Fault Tolerance](../../reliability/fault-tolerance/README.md), [Idempotency](../../reliability/idempotency/README.md).

## Изходен въпрос — връщане към реалния казус

При `cancel()` на клиентско плащане ти обещаваш ли, че Workflow е получил request за отмяна, че Activity е прекратила работа, или че кредитната система **действително вече не държи блокирани средства**?

**Правилният отговор трябва да назове конкретната доказуема граница** и как възстановяваме консистентността отвъд нея.
