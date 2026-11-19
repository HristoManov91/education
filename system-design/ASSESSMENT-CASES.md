# System Design & Architecture — diagnostic bank (16 cases, no solutions)

> Приложи [Education assessment protocol](../docs/learning/ASSESSMENT-PROTOCOL.md). Един въпрос наведнъж, без подсказване/solution предварително. След отговор: отделно „Техническо разбиране X/10“ и „Професионален изказ Y/10“ и explanation; след това изрично попитай дали да продължим. Оценката от един case НЕ е assessment за цялата област. [PROGRESS](../docs/learning/PROGRESS.md) остава „непроверено“ докато няма реален отговор.

За всеки казус: *requirements → assumptions → simple design → alternatives → decision → trade-offs → failure modes → proof*.

## Foundation (Junior/Mid)

### SD-Q01 — Requirements before tools
Трябва да проектираш service за търсене на документи. Product owner казва „да е бърз“. Кои 6 въпроса ще зададеш, преди да предложиш SQL indexes, Redis или Elasticsearch?

### SD-Q02 — SLI/SLO vs SLA
1 млн. eligible requests в rolling 28 days, цел 99.9% availability. Разграничи indicator, objective, agreement и error budget. Промени ли се budget-ът, ако 50% от трафика се случва в 2 часа?

### SD-Q03 — Percentile
End-to-end p99=900 ms и mean=150 ms. Можем ли да изведем p99 на Oracle query само от p99 на gateway и Spring service? Какво измерване липсва?

### SD-Q04 — Traffic calculation
8.64 млн. заявки/ден, peak 5× average, mean latency 200 ms. Покажи среден и peak RPS и приблизително mean in-flight. Кои assumptions трябва да се потвърдят в production?

### SD-Q05 — Load test vs estimate
6 реплики изчислени от 150 RPS/instance; данните идват от laptop benchmark, Oracle в тестова среда е на друг hardware. Какво е допустимо да твърдиш? Какъв reproducible test ти трябва?

### SD-Q06 — Workload shape
Един endpoint връща 10 реда, друг — 1 млн. реда. И двата са 100 RPS. Кога общ RPS dashboard подвежда? Предложи unit за capacity planning и bounded response work.

## Mid application

### SD-Q07 — Layered/modular vs services
Имаме един екип, shared Oracle schema, два бизнес процеса и един deployable. Кога modular monolith ще е по-смислен от microservices? Какво би променило решението?

### SD-Q08 — Data ownership
Service A пише signature status, service B чете същата таблица. Обясни защо независим deployment не дава independent data ownership. Какъв миграционен план предлагаш?

### SD-Q09 — Sync/async
След запис на документ трябва email, audit и recalculation. Кои действия трябва да са transactionally guaranteed? Кои могат да станат asynchronously? Какво проверяваме при rollback и duplicate events?

### SD-Q10 — Caching vs correctness
Redis има 92% hit ratio, но след подписване UI понякога показва стар статус. Каква е границата на authority, и кога cache трябва да се bypass-не или invalidatе-ва?

### SD-Q11 — Overload
500 RPS, p99=5 sec, CPU 15%, Hikari wait high, Oracle lock waits high. Сложиш ли 10× more pods? Назови bottleneck hypothesis и първите три доказателства.

### SD-Q12 — Retry storm
3 microservices в chain, всеки прави 3 опита към следващия при timeout. Как расте worst-case request amplification? Как ограничаваме concurrency, attempts и time budget?

### SD-Q13 — Queue semantics
POST започва background export и връща 202 с job ID. Worker crash след file write преди ACK. Какво означава „готово“? Как ще избегнем double-visible output и как ще измерим lag?

## Early Senior

### SD-Q14 — Critical availability
Product manager иска 99.99% availability; инфраструктурата е 2 app pods, 1 database instance и 1 DNS resolver. Кои са failure domains? Какво трябва да промени архитектурата или самото обещание?

### SD-Q15 — C4 & ADR
Нарисувай (описателно) C4 L1 и L2 на reporting platform, включително auth/DB/worker. Напиши кратък ADR за modular monolith vs microservices с best simple alternative и revisitation trigger.

### SD-Q16 — End-to-end failure review
След sign HTTP timeout има successful DB commit и duplicate event publication; в UI read projection изостава. Покажи sequence of events, два independent идемпотентни boundaries и policy за stale read when signature is legally relevant.

## Scoring rubric (запис само при реален отговор)

| Dimension | Observable evidence |
| --- | --- |
| Correctness / mechanism | Обяснява защо design работи и при отказ/конкурентност |
| Evidence | Дава формули/метрики и тестове, не интуитивни обещания |
| Trade-offs | Посочва simpler alternative, losses, operational cost |
| Professional communication | Точни термини, ясни causal relationships |

**Group checkpoint** след 4–8 свързани микро-теми; full capstone: [Reporting Platform](07-REPORTING-PLATFORM-CASE.md). Ако няма достатъчно данни за оценка, статусът е „непроверено“.
