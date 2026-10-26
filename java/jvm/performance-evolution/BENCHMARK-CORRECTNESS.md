# P1 — Benchmark Correctness & Performance Measurement

> **Deep Technical Lecture · 30–40 минути + executable lab · Mid+ → Early Senior**
>
> Източник: [Devoxx Belgium 2026 — *When Benchmarks Go Bad: What We Learned from Measuring Performance Wrong*](https://www.youtube.com/watch?v=yePre1hOvHE), Francesco Nigro и Holly Cummins (~54 мин.).
>
> **Място в education:** допълва [JVM Performance / Project Leyden](README.md), [JFR Troubleshooting](../jfr-troubleshooting/README.md) и [AI Era Spring Boot Testing](../../../spring/testing/ai-assisted-testing/README.md). Не дублираме нов JVM или Testcontainers курс.

## 1. Реалният проблем: зелени числа, грешно инженерно решение

Колега твърди: „Преминах от Thread Pools към Virtual Threads. throughput се подобри с 40%, p99 е едва 10 ms — внедряваме!“

Втора проверка показва p99=1.8 секунди. Възможно е **и двата експеримента да са правилно изпълнени**, но да измерват **различни workload-и или чужд bottleneck**, защото:

- първият генератор пуска нов request **чак след** отговора на предишния;
- вторият генерира **фиксиран поток от независимо пристигащи** requests;
- единият има различен SQL dataset, cache state, OS scheduler или container proxy;
- едни резултати броят само успешни отговори, други — error и timeout;
- едни измерват Java heap, други — process RSS.

**Основният mental model:** преди да сравняваме две реализации, трябва да докажем **какво мерим, при какви условия и за какво ще използваме числата**.

## 2. Трите R на достоверния benchmark

[Видео 08:00–25:00](https://youtu.be/yePre1hOvHE?t=480).

| Критерий | Значение | Капан |
| --- | --- | --- |
| **Reproducibility** | Сходни резултати при повторен експеримент със същите условия | Неразбрано CPU scaling, контейнери със stale images, различен JIT warm-up, cache, scheduler |
| **Realism** | Workload, data, hardware и network path приличат на production | 100 SQL реда вместо милиони, неправилна read/write смес, празен connection pool |
| **Relevance** | Метриката отговаря на реалното business решение | Максимален req/s, докато потребителите страдат от p99 latency, 500s или startup |

**Не можеш да максимизираш безкрайно и трите.** Например pinned CPU cores и изолирана database могат да направят теста по-повторяем, но да не отразяват реалната динамична production среда.

Примери за различни legitimate цели:
- cost efficiency → успешни business operations / resource cost;
- customer UX → p95/p99 + errors при production arrival model;
- autoscaling → process start до **първи успешен HTTP отговор** през реалния ingress;
- Oracle 19c срещу PostgreSQL → SQL + data parity, transaction semantics, latency и throughput със същия workload;
- Java 21 срещу 25 → еднакъв application build, steady-state latency, throughput, CPU, RSS, GC.

## 3. P1: Coordinated Omission — „пауза има, p99 я няма“

[Видео 31:59–39:30](https://youtu.be/yePre1hOvHE?t=1919).

**Coordinated omission** е систематично изкривяване на latency измерването: когато системата се забави, измервателят намалява или пропуска samples, така че част от реално очакваното чакане не влиза в latency distribution.

### BAD за open production traffic: closed-loop client

```text
send request → wait response → think 100 ms → send next request
                         ↑ stall: 2 seconds
   НЯМА нови arrivals, докато клиентът чака
```

При блокирал server клиентът просто **не пуска** нова работа. Ако production всъщност има независимо пристигащи заявки, тестът не моделира образуването на опашка. Той коректно измерва само latencies на **изпратените** заявки, но „липсващите“ заявки не участват.

### GOOD за open production traffic: independent arrivals

```text
time  0   100  200  300 ... 1000 1100 1200 ... 3000 ms
      ↓    ↓    ↓    ↓        ↓    ↓    ↓         ↓
   planned arrivals, regardless of completion times
                        server unavailable 1000–3000 ms
                        → queue / delay / timeout / failures
```

**Коректно разграничение:** closed-loop **не е грешен модел по природа**. Ако бизнесът действително обслужва фиксиран брой работници, всеки от които започва следваща работа само след края на предишната, closed-loop може да е правилен. Грешката е **разминаването между production arrival semantics и генератора**.

### Runnable Java 25 comparison — deterministic, NOT real benchmark

[Source](src/main/java/bg/hristomanov/education/performance/benchmark/BenchmarkValidityLab.java) · [JUnit proof](src/test/java/bg/hristomanov/education/performance/benchmark/BenchmarkValidityLabTest.java)

```java
var scenario = new BenchmarkValidityLab.Scenario(
        200, 100, 10, 1_000, 3_000);
var open = BenchmarkValidityLab.openLoop(scenario);
var closed = BenchmarkValidityLab.closedLoop(scenario);
```

Симулацията моделира:
- 200 attempts, един serial worker, 10 ms за обработка;
- server stall между 1000 и 3000 ms;
- open mode: arrival на всеки 100 ms, независимо от отговора;
- closed mode: една виртуална сесия с 100 ms think-time **след отговора**;
- неограничена опашка, без dropped requests и timeout.

**Точни математически резултати, потвърдени от JUnit**:

| Метрика | Open-loop | Closed-loop |
| --- | ---: | ---: |
| Requests общо | 200 | 200 |
| Последен arrival | 19 900 ms | 23 790 ms |
| p95 на completed latencies | 1 110 ms | 10 ms |
| p99 на completed latencies | 1 830 ms | 10 ms |
| Max latency | 2 010 ms | 1 910 ms |

**Извод:** closed-loop има заявка с почти 2 секунди забавяне, но p99 остава 10 ms, защото **само една от 200** е засегната. Open-loop включва чакането на новите arrivals. Важно: това са **различни подадени натоварвания**, не реални Quarkus/Spring/HTTP benchmarks.

### HdrHistogram и correction

[HdrHistogram](https://github.com/HdrHistogram/HdrHistogram) предоставя `recordValueWithExpectedInterval` и `copyCorrectedForCoordinatedOmission`. Не ги използвай механично.

- Корекцията има смисъл само ако има **обоснован expected arrival interval** и конкретен познат missing-samples model.
- Добавените samples са моделирана компенсация; не са реално изпълнени business operations.
- Ако load generator **не е пратил** заявки, корекцията не означава, че сме тествали реалния arrival rate.
- Не коригирай два пъти една и съща omission.

**Първо** избери workload модел и timestamps, **после** histogram и correction.

### Load testing tools

- [Gatling Open/Closed Workload Models](https://docs.gatling.io/testing-concepts/workload-models/) — различава arrival rate от capped concurrent users.
- [Hyperfoil](https://hyperfoil.io/) — load tests с workload orchestration и telemetry.
- JMeter и други инструменти: провери конкретния thread/injection модел и как отчитат failures и latency. Не превръщай категорично мнение за tool от лекцията в правило за всички версии и конфигурации.

## 4. P1: инфраструктурен bottleneck, погрешно приписан на framework-а

[Видео 45:00–49:30](https://youtu.be/yePre1hOvHE?t=2700).

### Case Study: Quarkus/Spring и rootless Podman

Екипът на Quarkus публикува [разследване на 08.06.2026](https://quarkus.io/blog/hidden-cost-rootless-container-networking/) с реален PostgreSQL CRUD benchmark.

| Environment | Quarkus TPS | Spring TPS | Ratio |
| --- | ---: | ---: | ---: |
| Local: Podman pasta + nftables | 15 504 | 13 062 | 1.19× |
| Host network | 24 116 | 13 368 | 1.80× |
| Host network, no nftables | 26 039 | 13 214 | 1.97× |
| Separate performance lab / hardware | 24 472 | 11 783 | 2.08× |

**Root cause:** един CPU core зает с `pasta` и по-скъпо network forwarding + nftables ограничава Quarkus, **преди** да достигне application capacity. Spring при този workload остава под този праг. Чрез flamegraph, kernel stacks, `mpstat` и `pgbench` екипът открива асиметричния proxy ceiling.

Това **не** означава, че Quarkus е универсално 2× по-бърз от Spring. Сравнението важи за **този код, настройки, SQL workload и среда**.

### Изпълним mini model

```java
observedCapacity(24_000, 12_000); // 12_000
observedCapacity(13_000, 12_000); // 12_000
```

Две приложения с различни intrinsic capacities изглеждат еднакви при shared downstream ceiling 12 000. При ceiling 18 000 видимата разлика се появява.

**Това е аритметично учебно доказателство на принципа, не копиране на Quarkus резултатите.**

### Active Benchmarking = наблюдаваме цялата верига, докато измерваме

Използвай **USE** метода на Brendan Gregg за ресурси:

| Resource | Utilization | Saturation | Errors |
| --- | --- | --- | --- |
| CPU | per-core CPU, throttling | runnable/queue wait | CPU/cgroup errors |
| DB/JDBC | active connections, DB CPU | pending pool threads, SQL locks | SQL/connection timeouts |
| Proxy/Network | process CPU, packets/bytes | socket/backlog/wait queue | resets, dropped packets |
| JVM/Heap | heap, allocation/GC activity | GC pressure, stop-the-world tails | OOME/allocation failures |

При HTTP service добави и **RED**: Rate, Errors, Duration.

**Съмнително еднакви results са повод за разследване**, но не са математическо доказателство за общ bottleneck. Не премахвай production firewall/security controls само за да ускориш benchmark — създай и документирай контролирана лаборатория.

## 5. Измервай правилната метрика, не само красив throughput

[Видео 25:00–45:00](https://youtu.be/yePre1hOvHE?t=1500).

### Offered rate ≠ completed outcome rate ≠ successful throughput

Ако 1 000 responses са отчетени за секунда, но 100 са failures, successful business throughput е **900/s**, не 1 000/s. Нашият [RunAccounting](src/main/java/bg/hristomanov/education/performance/benchmark/BenchmarkValidityLab.java) и [test](src/test/java/bg/hristomanov/education/performance/benchmark/BenchmarkValidityLabTest.java) изискват `attempted = succeeded + failed` и пресмятат success/failure отделно.

Не всички HTTP 4xx непременно са инфраструктурен дефект; кое е **успешна бизнес операция** трябва да е дефинирано предварително.

### Latency — timestamp contract

Кое точно мериш:
- intended arrival;
- actual send;
- accepted connection;
- first response byte;
- full response;
- или complete business transaction?

Отчиташ ли timeouts, 429 и 5xx отделно? Ако p99 е само от успешно приключилите заявки, това трябва да е изрично написано. Ако има client-side queue, timestamp преди или след нея може да промени драматично резултата.

### Startup time

Измерването до JVM log „started“ не е същото като **time to first successful request**. Може DB migrations, authentication, ingress routing или runtime initialization още да не са готови. Избери бизнес-коректен readiness criterion и измервай реалния отговор.

### Heap ≠ process RSS ≠ container memory

- **Heap** — managed Java object memory;
- **RSS** — resident memory на процеса, включително JVM native components, thread stacks и други;
- **cgroup/container memory** — релевантен общ memory budget за контейнеризиран deployment.

`-Xmx2g` не означава RSS ≤ 2 GB. Също така сравнявай при същия етап — first request vs warm steady state. GC може да trade-off-не RSS срещу throughput/latency.

## 6. Benchmark setup: „едни и същи условия“ не става на доверие

[Видео 11:00–25:00](https://youtu.be/yePre1hOvHE?t=660).

Фиксирай и документирай: Java vendor/build, source SHA, image digest, JVM flags/heap/GC, CPU governor/scheduler/NUMA/cgroups, DB version и **размер на данните**, indexes/statistics, cache warm/cold state, network/proxy path, load generator capacity, open/closed model, test duration, warm-up, repetitions и errors.

**Trade-off:** pinning и изключване на CPU turbo помагат за repeatability, но могат да намалят realism. Не копирай blanket Linux flags от чужд benchmark без да разбираш този компромис.

## 7. Практически лаборатории и senior checkpoint

[Benchmark Correctness Labs — Java 25](BENCHMARK-CORRECTNESS-LABS.md)

Там има изпълнимите тестове, interpret-the-output упражнение, design на *falsifiable experiment* за Oracle/PostgreSQL, Loom/WebFlux, Java 21/25 или G1/ZGC, и checklist за независим code review.

```bash
mvn -pl java/jvm/performance-evolution -Dtest=BenchmarkValidityLabTest test
```

**Не наричай това реален benchmark.** Симулаторът няма sockets, real scheduling/queues, GC/JIT, database или latency от production; учим measurement bias и истинското значение на metrics.

## 8. Кога това знание е най-полезно?

- Проверяваш PR с „40% по-бързо“ или оптимизация, предложена от AI.
- Сравняваш virtual threads и reactive backend.
- Оценяваш дали Java 25/JVM flags ускоряват конкретния workload.
- Сравняваш Oracle и PostgreSQL с милиони редове.
- Изследваш нестабилен p99, 5xx или разминаване между локален и CI/load test.

### Най-важните 7 извода

1. **Measure the intended system, not an accidental bottleneck.**
2. Повторяемото измерване все още може да е нерелевантно.
3. Closed workload може да скрие забавяне при open production traffic.
4. Percentiles са полезни само с ясен arrival/sampling/error contract.
5. Общ proxy/DB CPU ceiling може да изкриви framework сравнение асиметрично.
6. Без independent resource evidence throughput резултатът често остава двусмислен.
7. Performance improvement е **хипотеза, която се доказва**, не процент от чужда презентация.

## Оригинални източници

1. [Devoxx 2026 — When Benchmarks Go Bad](https://www.youtube.com/watch?v=yePre1hOvHE).
2. [Quarkus — Why isn't Quarkus 2x faster than Spring on my machine?](https://quarkus.io/blog/hidden-cost-rootless-container-networking/).
3. [Quarkus/Spring original performance benchmark repository](https://github.com/quarkusio/spring-quarkus-perf-comparison).
4. [Brendan Gregg — USE Method](https://www.brendangregg.com/usemethod.html) и [Performance Methodology](https://www.brendangregg.com/methodology.html).
5. [Gatling — Open/Closed Workload Models](https://docs.gatling.io/testing-concepts/workload-models/).
6. [HdrHistogram — Coordinated Omission](https://github.com/HdrHistogram/HdrHistogram).

**Финален mental model: benchmark е контролиран експеримент с валиден workload, правилна метрика и независими доказателства.**
