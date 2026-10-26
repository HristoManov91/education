# Benchmark Correctness — практическа Java 25 лаборатория

> **P1 · 20–30 минути · Java 25 / JUnit 5 / AssertJ**
>
> Предпоставка: [Benchmark Correctness & Performance Measurement](BENCHMARK-CORRECTNESS.md).  
> Source: [When Benchmarks Go Bad — Devoxx Belgium 2026](https://youtu.be/yePre1hOvHE).

## 1. Входният проблем и критерии за приемане

Имаме две benchmark implementation-и, които уж мерят едно и също. Единият показва p99=10ms, другият — 1830ms. Искаме да знаем **кое точно е различно**, преди да правим заключения за производителността.

### Acceptance criteria

| AC | Какво проверяваме |
| --- | --- |
| AC1 | Open-loop arrival timestamps са фиксирани, независими от server response. |
| AC2 | Closed-loop virtual user не изпраща следваща заявка преди предходната да завърши. |
| AC3 | Еднаква server outage дава различно **sampled p99** поради различния arrival pattern. |
| AC4 | Резултатите са детерминистични, копирани защитно и percentiles валидирани. |
| AC5 | Shared resource ceiling може да скрие реалните разлики между две приложения. |
| AC6 | Total outcome throughput се различава от successful business throughput при errors. |
| AC7 | Не представяме тези симулации като real HTTP benchmark или доказан speedup. |

## 2. Run commands

От root на `education`:

```bash
# Focused proof:
mvn -pl java/jvm/performance-evolution -Dtest=BenchmarkValidityLabTest test

# Проверка и на съществуващата AOT/JVM лаборатория:
mvn -pl java/jvm/performance-evolution verify

# Пълният education Maven reactor:
mvn --no-transfer-progress verify
```

**Няма Docker, HTTP server или реална база**: така доказваме sampling/measurement reasoning с pure Java, без nondeterministic влияния. Ако искаш performance числа за production, ще е нужен **отделен реален load-test проект** със съответната инфраструктура.

## 3. Experiment A: Coordinated Omission

**Код:** [BenchmarkValidityLab.java](src/main/java/bg/hristomanov/education/performance/benchmark/BenchmarkValidityLab.java)  
**JUnit:** [BenchmarkValidityLabTest.java](src/test/java/bg/hristomanov/education/performance/benchmark/BenchmarkValidityLabTest.java)

Тази лаборатория не се нуждае от runtime scheduler, за да покаже **логическата грешка**.

### Параметри

```java
BenchmarkValidityLab.Scenario scenario =
        new BenchmarkValidityLab.Scenario(200, 100, 10, 1_000, 3_000);
```

Това означава 200 requests, интервал 100ms, по 10ms време за service, outage от 1s до 3s, един server worker и queue без timeout.

### Flow A — Open

```text
... 900ms, 1000ms, 1100ms, 1200ms, ...
                    ↓ server cannot process between 1000 and 3000
Arrivals keep occurring; requests wait in queue
```

### Flow B — Closed

```text
request arrives at 990 ms? (depending on prior completion)
response waits through outage
no next request until response + 100ms think time
```

В нашия модел конкретната засегната closed-loop заявка се изпраща на 1100 ms и завършва на 3010 ms, т.е. 1910 ms latency. Следващата заявка се изпраща едва на 3110 ms и има normal latency 10 ms.

### Изходът на тестовете

| Metric | Open | Closed |
| --- | ---: | ---: |
| Planned/sent request count | 200 | 200 |
| Last request arrival | 19 900ms | 23 790ms |
| p95 | 1 110ms | 10ms |
| p99 | 1 830ms | 10ms |
| Maximum | 2 010ms | 1 910ms |

**Защо p99 при Closed е 10ms:** nearest-rank p99 при 200 samples е 198-ата подредена стойност. 199 са нормални, една е засегната от outage; единственият бавен sample не участва в p99.

**Ниво на доказателството:** моделът е *toy server*. Няма реален JIT, scheduler, TCP, retries, saturation control или request drops. Синтетичните p99 не трябва да се сравняват директно с Java/Spring benchmark results.

### Break-it questions

1. Ако симулираме 10 000 заявки вместо 200, каква ще стане тежестта на един isolated slow request върху p99?
2. Какво става, ако реалният traffic е closed? Кой тест е representative тогава?
3. Защо не можем да коригираме непуснатите requests само със сортиране на наличните durations?
4. Как се променя резултатът, ако има max queue length/timeout? Защо percentile alone не е достатъчен?

## 4. Experiment B: shared infrastructure bottleneck

Код: `BenchmarkValidityLab.observedCapacity(applicationCapacity, proxyOrDbLimit)`.

```java
observedCapacity(24_000, 12_000); // 12_000
observedCapacity(13_000, 12_000); // 12_000
```

Две различни application capacities изглеждат еднакви, защото **по-ниският downstream limit е общ**.

Ако увеличим ceiling на 18 000, единият вариант достига 18 000, а другият 13 000. Това е и причината benchmarker да събира OS/DB/network evidence, а не само HTTP summaries.

**Реален case:** [Podman pasta и Quarkus/Spring](https://quarkus.io/blog/hidden-cost-rootless-container-networking/) разкрива аналогичен ресурсен bottleneck в конкретен CRUD/PostgreSQL workload.

### Engineer exercise

Имаш Spring MVC с Virtual Threads и WebFlux. И двата достигат същия p99 и TPS. Напиши три възможни hypotheses (една от тях — ограничен pool/proxy) и опиши за всяка **кой метрик би я опровергал**.

Пример: „JDBC pool=20 е bottleneck“ → наблюдаваме pending borrowers, active connections, DB CPU, query waits и throughput при внимателно контролирана промяна на pool size, при непроменен dataset.

## 5. Experiment C: successful throughput vs total responses

```java
var run = new BenchmarkValidityLab.RunAccounting(
        1_000, 900, 100, 1_000);
```

Значение: 1000 attempted outcomes през 1000ms, от които 900 successful и 100 failed.

Детерминистични резултати:

- observed outcome rate = 1 000/s;
- successful business throughput = **900/s**;
- failure ratio = **10%**.

Силен review въпрос: ако заменим 900 със 100 и failed с 900, можем да постигнем 1000 observed outcomes/s, но да имаме **90% failures**. „Бърз“ error path не е бизнес успех.

**Детайл:** отчети и таймаути като failures дори когато client не получи HTTP response. В реален load test различавай attempted, accepted, completed, rejected и timed-out; нашият simplified accounting обединява non-success outcomes в един брояч.

## 6. Experiment D: contract за истински load test

Избери реална задача:

- JVM 21 vs 25 за Spring Boot 4 service;
- G1 vs ZGC при фиксиран p99 error budget;
- Virtual Threads vs Reactor;
- SQL в Oracle 19c vs PostgreSQL.

Попълни:

| Поле | Примерна стойност / въпрос |
| --- | --- |
| Business hypothesis | B намалява p99 за `POST /orders` без по-нисък success rate |
| Fixed variables | Code SHA, request payload, DB dataset, heap, CPU limit, JDK flags |
| Independent variable | Само framework approach или GC/JDK choice |
| Model | Open arrivals със зададен rate или justified closed concurrent users |
| Baseline | Warm-up и cold/warm data state описани |
| Offered vs achieved load | Искаме 500rps — генераторът успява ли да подава 500rps? |
| Success definition | 201+persisted state, idempotency key, липса на duplicate side effects |
| Latency | Intended-arrival → full business response, p50/95/99 и sample count |
| Errors | HTTP 5xx/429, retries, timeouts, dropped arrivals |
| Resource metrics | CPU/core, run queue, JDBC wait, DB locks, RSS, GC, network proxy |
| Repeated trials | ≥5 при сравними условия, редуваме A/B, гледаме variance |
| Falsification | При кой резултат казваме „хипотезата е грешна“? |
| Risk | Feature flag / rollback, production canary |

Това упражнение **не изисква** да внедряваш нов load framework в `education`. Първо усвояваме дизайна на валидното измерване.

## 7. Финална самопроверка

1. Защо един benchmark е „зелено p99“ и въпреки това може да е опасен?
2. Разликата между 10 000 *offered* и 10 000 *successfully completed* req/s?
3. Какво означава coordinated omission на езика на client arrivals и histogram samples?
4. Защо same app и same JDK не гарантират reproducibility?
5. Как USE различава „лимитът е network proxy“ от „лимитът е приложението“?
6. Кога би избрал JMH microbenchmark, и кога real HTTP load generator?
7. Какъв е рискът, ако изключиш firewall в production само защото benchmark-ът се подобрява?
8. Кои допускания в нашия simulator го правят неподходящ за реални throughput predictions?

**Изход:** правилният benchmark е измерване с ясна бизнес цел, валиден workload, отделно отчетени успехи/грешки и доказан bottleneck, а не число, извадено от контекст.

## Original sources

- [Devoxx lecture](https://youtu.be/yePre1hOvHE)
- [Gatling Workload Models](https://docs.gatling.io/testing-concepts/workload-models/)
- [Brendan Gregg — USE Method](https://www.brendangregg.com/usemethod.html)
- [HdrHistogram](https://github.com/HdrHistogram/HdrHistogram)
- [Quarkus performance case](https://quarkus.io/blog/hidden-cost-rootless-container-networking/)
