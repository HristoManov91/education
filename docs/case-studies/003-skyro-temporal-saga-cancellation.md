# Case 003 — Skyro: Saga compensation при Temporal cancellation

> **Източник:** Andrei Sulyz, Skyro, *Solving Distributed Transactions with the Saga Pattern and Temporal*, Devoxx Belgium 2026.
> **Приоритет:** Tech Radar → **P1** (System Design / Distributed Systems / Reliability).
> **Статус:** кейсът е документиран по публичната лекция; не твърдим достъп до вътрешния production код на Skyro. Нашата Temporal 1.40.0 лаборатория е **учебно възпроизвеждане**, а не оригиналната им система.

## Симптомът

Синхронно клиентско плащане блокира сума едновременно в **credit** и **debit** системи. Клиентът чака на терминал, а Temporal Workflow оркестрира участниците чрез HTTP Activities.

В определени моменти клиентът/системата изпраща **cancel**, докато hold Activity още работи. Различните технически сигнали не означават едно и също:

- Workflow получава cancellation.
- Activity получава/не получава заявка за отмяна.
- HTTP call може вече да е commit-нал side effect.
- Компенсацията може да е приключила **преди** първоначалният hold да пристигне.

**Бизнес инвариант:** след окончателна отмяна не бива тихомълком да останат блокирани клиентски средства.

## Failure chain / Root cause

1. **ACK lost:** participant commit-ва hold, но orchestration Activity хвърля exception. Ако компенсацията се добави **след** Activity, няма регистрирана обратна операция.
2. **Detached compensation:** след Workflow cancellation стандартният scope сам е cancelled и cleanup Activity може да не се изпълни. Detached scope позволява изпълнение.
3. **TRY_CANCEL race:** Workflow преминава към cleanup бързо, но реалният external HTTP request продължава. Release пристига преди hold — ако participant третира unknown release като безсъбитийно no-op, късен hold отново блокира средства.
4. **WAIT_CANCELLATION_COMPLETED:** отлага обработката на Activity result; при no-heartbeat Activity реалната cancellation може да не се достави навреме. И след завършване на последната Activity може да липсва exception, който да задейства cleanup.
5. **Изричен scope cancellation check:** проверява се cancellation state след всяка forward Activity, **включително последната**.
6. **Остатъчни races:** дори с тези мерки са възможни uncertain outcomes при външни доставчици, crash и delayed messages.

## Последователните поправки от лекцията

| Поправка | Кой проблем адресира | Какво остава |
| --- | --- | --- |
| Регистрирай compensation преди forward Activity | Lost acknowledgement | Compensation може да се случи преждевременно |
| Detached cancellation scope + `run()` | Cleanup от cancelled Workflow | External HTTP може още да работи |
| WAIT_CANCELLATION_COMPLETED | Workflow изчаква Activity terminal observation | No-heartbeat/timeout и пропуснат final check |
| Проверка след всяка Activity | Последен completed side effect при cancellation | Все още няма атомарност между външните участници |
| Idempotency / fencing на participant | Late hold след release | Трябва истинска durable atomic реализация |
| Reconciliation | Изпуснати несъответствия | Не замества synchronous correctness; recovery SLO |

**Важно:** лекцията представя Temporal механизми и reconciliation. Нашият пример с **cancellation tombstone/fencing в участника** е *допълнително предложено инженерно решение*, не доказан детайл от внедряването на Skyro.

## Как да разпознаем проблема

Търси конкретните признаци:

- Client timeout + downstream record съществува.
- Workflow terminal status = CANCELLED, но participant state = HELD.
- Компенсация записана като successful, а няколко секунди по-късно се появява нов hold със същия business operation ID.
- Различни поведения при parallel и sequential Saga compensations.
- Рязко увеличаване на unresolved holds, retries или reconciliation lag.

## Дизайн на възстановяване

1. Пази стабилен `operationId` и `workflowId` за **една бизнес операция**, не по HTTP request.
2. Изисквай downstream idempotency/operation-status contract. Ако participant не може да предотврати късно изпълнение след cancellation, не обещавай твърдо „освободено“ само по Workflow terminal state.
3. Пази `UNKNOWN`, `CANCEL_REQUESTED`, `COMPENSATION_REQUIRED` като отделни операционни състояния, когато domain-ът го допуска.
4. Компенсирай извън отменения scope; failed compensation не е успех.
5. Reconciliation сравнява business intent с participant facts; repair actions са идемпотентни, одитируеми, с alert при невъзможно възстановяване.
6. Измервай `orphaned_holds_count`, `cancellation_to_release_latency`, `oldest_unresolved_age`, `reconciliation_repair_rate`.

## Какво реализирахме в education

- **[Пълен урок / P1 Deep Dive](../../spring/messaging/saga/TEMPORAL-CANCELLATION-DEEP-DIVE.md)**: timeline, Temporal scope semantics, cancellation types, alternative designs, interview questions и checklist.
- **[Temporal Java Workflow](../../spring/messaging/saga/src/main/java/bg/hristomanov/education/saga/temporal/HoldWorkflowImpl.java)**: compensation registration before Activity, WAIT, final check, detached cleanup.
- **[Temporal integration tests](../../spring/messaging/saga/src/test/java/bg/hristomanov/education/saga/temporal/TemporalHoldWorkflowIntegrationTest.java)**: in-memory Temporal server с реален SDK/Worker, забавена Activity и lost ACK.
- **[Participant state test](../../spring/messaging/saga/src/test/java/bg/hristomanov/education/saga/cancellation/HoldingCancellationRaceTest.java)**: детерминистично доказателство на premature release → late hold bug и cancellation marker.
- **[HoldGateway](../../spring/messaging/saga/src/main/java/bg/hristomanov/education/saga/temporal/HoldGateway.java)**: bounded caller wait, `PENDING` и attach на retry към stable Workflow ID.

Това НЕ е production implementation с истински банкови системи, durability на participants или реална network failure injection.

## Кога да използваме подхода

**Подходящо:** multi-step distributed operations с компенсируеми ефекти и нужда от durable orchestration/retries (payment hold, inventory reservation, cloud resource provisioning).

**Неподходящо като самоцел:** единична локална транзакция; non-compensatable бизнес операции; неизяснени downstream semantics и липсващ operation status endpoint.

## Източници

- [Лекцията в YouTube](https://www.youtube.com/watch?v=c45C2wNSZFc)
- [Devoxx Belgium talk](https://m.devoxx.com/events/dvbe26/talks/16418/solving-distributed-transactions-with-the-saga-pattern-and-temporal)
- [Публикация на лектора в Skyro Tech](https://medium.com/skyro-tech/solving-distributed-transactions-with-the-saga-pattern-and-temporal-27ccba602833)
- [Temporal — cancellation](https://docs.temporal.io/develop/java/workflows/cancellation)
- [Temporal — Java testing](https://docs.temporal.io/develop/java/best-practices/testing-suite)
