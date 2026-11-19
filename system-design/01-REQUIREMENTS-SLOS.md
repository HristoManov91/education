# SD-01/02 — Requirements, latency, SLIs and SLOs

> **P1 · учебна глава / scenario-based decisions**. Официална основа: [Google SRE Workbook — Implementing SLOs](https://sre.google/workbook/implementing-slos/). Това не е executable module; конкретните числа в примерите са **хипотетични**.

## 1. Реалният казус

Една информационна система обслужва три user journeys:
1. **Submit** — потребител създава подписван документ; correctness и transaction semantics са критични.
2. **Search** — потребител филтрира голям списък; p95 latency и DB plan се виждат директно.
3. **Export** — изтегля справка със стотици хиляди редове; операцията може да е seconds/minutes.

Наивната архитектурна заявка е: „трябва да се справя с много хора“. Без конкретни числа и user journeys никой не може да докаже дали трябва нова база, queue, кеш или по-прост SQL.

## 2. Как от бизнес изискване стигаме до measurable architecture

| Слой | Въпрос | Учебен пример (допускане, НЕ production SLA) |
| --- | --- | --- |
| Functional | Какво прави системата? | Submit, Search, Export |
| Quality attribute | Какво означава „добра“? | Search е бърз и правилен; Submit не дублира side effects |
| SLI | Какво измерваме? | Дял валидни search requests под 500 ms, по server-side duration |
| SLO | Каква цел и период? | >= 99% от валидните searches под 500 ms за rolling 28 days |
| Availability SLO | Кое е success? | >= 99.9% от eligible requests завършват **бизнес успешно** |
| Constraints | Какво не може да се променя? | Oracle schema, access controls, operational budget |
| Proof | Какво трябва да видим? | Baseline telemetry, load test, failure test, read/write metrics |

**Внимавай:** HTTP 200 не винаги означава успешен business journey; batch accepted `202` не означава completed export. Определи SLI „успех“ на правилната boundary.

## 3. SLI, SLO, SLA и error budget

- **SLI** — измерител: например `successful eligible operations / eligible operations`.
- **SLO** — вътрешна измерима цел: „>= 99.9% за rolling 28 days“.
- **SLA** — договор с клиента, с условия и често последици, които не се изчерпват с числовата SLO.
- **Error budget** — допустимият брой/дял отклонения по даден SLO в рамките на прозореца.

Пример: 1 000 000 eligible requests / 28 дни, 99.9% availability SLO.

```text
error budget fraction = 1 - 0.999 = 0.001
budgeted failed requests = 1,000,000 × 0.001 = 1,000 requests
```

Това е **request-based budget**, не „43 минути downtime“. Downtime equivalent може да се пресметне за time-based availability при подходящо дефиниран модел, но не е автоматично еквивалентно, ако трафикът е силно неравномерен.

## 4. Latency percentiles и правилният бюджет

- **mean** latency отразява усреднена величина; **p95/p99** описват опашката на разпределението.
- `p99 = 500 ms` означава приблизително 99% от разглежданите измервания са <= 500 ms, **не** че всяка заявка е толкова бърза.
- **Latency budget** трябва да отчита client + gateway + app + DB + downstream; не събирай p99 на компоненти механично и не наричай сумата p99 на journey. За съставния percentile трябват реални end-to-end samples, correlations и разпределения.
- Възможно е search и export да имат различни SLOs — не ги слепвай в един dashboard.

### Примерен бюджет за request със срок 800 ms

```text
gateway/authorization       ~  40 ms
service validation         ~  30 ms
Oracle query              ~ 250 ms
external lookup           ~ 200 ms
serialization + network   ~ 100 ms
reserve                   ~ 180 ms
------------------------------------
illustrative allocation      800 ms
```

Това е **планова разбивка, не статистическо p99 доказателство**. Измери критичния path и процентили със trace/load tests. Retry или timeout, който надхвърля end-to-end deadline, не е правилен.

## 5. Decision card: да имаме ли SLO за всичко?

| Въпрос | Решение |
| --- | --- |
| Какъв проблем? | Reliability work се приоритизира без доказателство или според loudest incident |
| Кога? | Важни user journeys с business impact и измерими граници |
| Кога НЕ? | Всяка вътрешна helper функция като отделна сложна SLO policy |
| Печалба | Обективен разговор за reliability, user impact, feature vs operational work |
| Цена | Telemetry, избор на eligible traffic, dashboards, alerting и периодичен преглед |
| По-просто | Един journey-centric SLI + праг + baseline measurements, преди сложни error-budget политики |
| Proof | За 4 седмици валидни request measurements и анализ на засягащи клиента инциденти |

## 6. Упражнение без отговор

Дадени са 200 000 operations, от които 120 са HTTP 500, 210 са HTTP 200 с неверен business result, а 40 са валидни `4xx` клиентски грешки. Избери eligible denominator и какво означава business failure. **Не започвай с аритметиката**, докато не дефинираш contract. Дай две SLO дефиниции и един недостатък на всяка.

**Checkpoint:** посочи защо `99.9%` availability и `p99 <= 500 ms` са различни цели и какво измерване ти липсва, за да обещаеш която и да е от тях.
