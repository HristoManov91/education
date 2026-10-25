# NotebookLM prompt — Temporal Saga, Cancellation & Reconciliation

**Tech Radar → P1 Deep Technical Lecture**. Препоръчителна продължителност: **30–40 минути**. Препоръчителен input: лекцията, официалната Temporal документация и [нашия завършен технически урок](TEMPORAL-CANCELLATION-DEEP-DIVE.md).

## Източници за NotebookLM

1. Оригинална лекция: https://www.youtube.com/watch?v=c45C2wNSZFc
2. Пълен Deep Dive от education: [TEMPORAL-CANCELLATION-DEEP-DIVE.md](TEMPORAL-CANCELLATION-DEEP-DIVE.md)
3. Официална документация: https://docs.temporal.io/develop/java/workflows/cancellation
4. Официален Temporal sample: https://github.com/temporalio/samples-java/blob/main/core/src/main/java/io/temporal/samples/hello/HelloDetachedCancellationScope.java
5. Нашият Java пример: [HoldWorkflowImpl.java](src/main/java/bg/hristomanov/education/saga/temporal/HoldWorkflowImpl.java)
6. Нашите integration tests: [TemporalHoldWorkflowIntegrationTest.java](src/test/java/bg/hristomanov/education/saga/temporal/TemporalHoldWorkflowIntegrationTest.java), [TemporalTryCancelRaceTest.java](src/test/java/bg/hristomanov/education/saga/temporal/TemporalTryCancelRaceTest.java)

## Готов prompt

> Създай задълбочена българска аудио лекция на ниво силен Mid → Senior Java/Spring backend инженер. Обяснявай понятията с точна терминология на английски и пояснения на български. Продължителност приблизително 30–40 минути; не бързай и не пропускай failure modes.
>
> Започни с реалния fintech казус от Devoxx лекцията: една синхронна операция трябва да блокира средства в credit и debit системи, а потребителят чака на терминал. Разграничи очакван бизнес инвариант от действителните гаранции на HTTP, Workflow, Activity и participant DB.
>
> Проследи **хронологично** шестте проблема и поправки: (1) commit + lost response, (2) компенсация регистрирана прекалено късно, (3) detached cancellation scope и задължителният run(), (4) TRY_CANCEL и release-before-late-hold race, (5) WAIT_CANCELLATION_COMPLETED, heartbeat и проверката след последната Activity, (6) reconciliation и остатъчни рискове.
>
> За всяка стъпка дай: реалистична последователност във времето, защо наивният вариант изглежда правилен, какво се чупи, коректен mental model и инженерно решение. Не заменяй урока с повърхностен обзор на Temporal.
>
> Обясни разликата между **Workflow cancel requested**, **Activity cancellation acknowledged**, **client timeout**, **external HTTP side effect committed** и **business compensation completed**. Изрично кажи защо никое от първите четири не доказва последното.
>
> Разгледай Java кода на нашите HoldWorkflowImpl, HoldGateway и тестовете. Покажи къде регистрираме компенсацията преди forward Activity, защо detached scope е нужен, защо проверяваме cancellation след последната стъпка, и как stable Workflow ID помага при client retry. Разграничи надеждността на orchestration от истинския downstream financial state.
>
> Представи два сравнителни сценария от реалния Temporal test server: **naive TRY_CANCEL + release → delayed hold остава** и **participant-side cancellation marker → delayed hold не може да възстанови блокировката**. Обясни, че marker/fencing дизайнът е наше допълнително инженерно решение, а не потвърден production детайл за Skyro.
>
> Разгледай практичните ограничения: Activity heartbeats, StartToClose timeout, fail на compensation, защо TestWorkflowEnvironment не е production cluster, participant durability, exactly-once illusion, операция със status UNKNOWN, reconciliation SLO и нужда от alert/manual intervention.
>
> Сравни Temporal orchestration със Spring state machine/Outbox + Inbox и с локална DB transaction. Кога Temporal си струва и кога добавя ненужна сложност? Не внушавай, че той решава глобалната ACID атомарност.
>
> На всеки няколко минути задавай по един конкретен инженерeн въпрос и обяснявай отговора. Завърши с: 5 технически правила, 3 въпроса за code review, 1 senior design challenge и най-важния mental model.
>
> **Използвай само предоставените източници.** Не измисляй числа за Skyro, не твърди, че лабораторията изпълнява Toxiproxy или PostgreSQL durability, ако това не е показано. Ако източниците не доказват дадено твърдение, кажи изрично, че е предположение или предложение.
