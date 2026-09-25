# Loom vs Reactive — кога Virtual Threads не заменят WebFlux

Практическа Spring лаборатория за **WebMVC + Virtual Threads + Structured Concurrency** срещу **WebFlux + Reactor** върху едни и същи backend проблеми.

Целта не е да изберем победител, а да изградим правилен mental model: Loom прави synchronous blocking code много по-практичен при висока I/O concurrency, докато Reactive Streams дават различни свойства — streaming, demand/backpressure и pipelines без естествен край.

Основният Java/Loom материал е в [`java/concurrency/project-loom`](../../../java/concurrency/project-loom/README.md).

---

## 1. Реалният казус

Aggregation service трябва да реши четири задачи:

1. два независими downstream calls паралелно;
2. един общ timeout budget за цялата операция;
3. 10 calls към downstream, който допуска максимум 2 едновременно;
4. поток от събития без предварително известен край.

Грешният shortcut е:

```text
Virtual Threads са евтини
        ↓
значи Reactor/WebFlux вече не са нужни
```

Това смесва две теми: **евтино изпълнение на blocking tasks** и **моделиране на data streams + demand**.

---

## 2. Структура

```text
loom-vs-reactive/
├── backend-service/     # controllable fast/slow/rate-limited endpoints
├── mvc-loom/            # WebMVC + RestClient + StructuredTaskScope
├── webflux-reactor/     # WebFlux + WebClient + Mono/Flux
└── http/                # IntelliJ HTTP Client requests
```

| Модул | Порт |
| --- | ---: |
| backend-service | 9091 |
| mvc-loom | 9092 |
| webflux-reactor | 9093 |

---

## 3. Finite fan-out / fan-in

И двата варианта извикват `/backend/fast` и `/backend/slow` паралелно и комбинират резултатите.

### WebMVC + Loom

[`LoomComparisonController.java`](./mvc-loom/src/main/java/bg/hristomanov/education/concurrency/mvcloom/LoomComparisonController.java)

```text
HTTP request virtual thread
        ↓
StructuredTaskScope
   ├── child virtual thread → fast
   └── child virtual thread → slow
        ↓
      join()
        ↓
normal synchronous result
```

Това е естествено за **bounded request/response work** с ясен край.

### WebFlux + Reactor

[`ReactorComparisonController.java`](./webflux-reactor/src/main/java/bg/hristomanov/education/concurrency/webflux/ReactorComparisonController.java)

```java
return Mono.zip(fast, slow,
        (fastResult, slowResult) -> fastResult + " + " + slowResult);
```

Тук concurrency се описва чрез composition на publishers, без blocking wait върху downstream I/O.

---

## 4. Global timeout / deadline

Backend-ът има fast call ~100 ms и slow call ~1500 ms. И двете приложения имат общ budget 500 ms.

### Loom

```java
StructuredTaskScope.open(
        joiner,
        configuration -> configuration.withTimeout(Duration.ofMillis(500)))
```

Timeout-ът започва при отварянето на scope-а. При изтичане scope-ът се cancel-ва, unfinished child threads получават interrupt и `join()` хвърля `StructuredTaskScope.TimeoutException`.

Критичният урок е: **timeout != hard kill**. Cancellation е cooperative. Ако child code игнорира interruption, `close()` може да чака след изтичането на budget-а.

Това се доказва executable в [`TimeoutAndCancellationDemo.java`](../../../java/concurrency/project-loom/loom-labs/src/main/java/bg/hristomanov/education/loom/labs/timeout/TimeoutAndCancellationDemo.java).

### Reactor

```java
Mono.zip(fast, slow, ...)
        .timeout(Duration.ofMillis(500))
```

Timeout operator-ът обгръща composed publisher-а.

**Per-call timeout** и **global operation budget** не са едно и също.

---

## 5. Bounded concurrency — пазим scarce resource-а

`/backend/items/{id}` допуска максимум **2 едновременни заявки**.

Virtual threads решават scarcity на threads, но не connection limits, DB pools, partner quotas, memory, CPU или downstream capacity.

### Loom

```java
Semaphore downstreamPermits = new Semaphore(2);
```

Не правим pool от два virtual threads. Tasks пак са евтини и могат да са много; permit-ът пази само критичния downstream resource.

Отделният bad/good demo е [`BoundedConcurrencyDemo.java`](../../../java/concurrency/project-loom/loom-labs/src/main/java/bg/hristomanov/education/loom/labs/limit/BoundedConcurrencyDemo.java): unbounded вариантът нарочно получава HTTP 429, bounded вариантът използва `Semaphore(2)`.

### Reactor

```java
Flux.range(0, 10)
        .flatMap(this::fetchItem, 2)
```

Вторият аргумент задава max concurrency за inner publishers — естествена stream processing policy.

---

## 6. Infinite stream — границата на Structured Concurrency

WebFlux endpoint-ът `/compare/events` връща `Flux.interval(...)` като Server-Sent Events.

```text
event-0
event-1
event-2
...
няма предварително известен край
```

Structured Concurrency има естествен bounded lifecycle:

```text
open scope → fork known work → join → close
```

Infinite stream има друг lifecycle:

```text
subscribe → data → data → data → ... → cancel/disconnect
```

Затова `StructuredTaskScope` не е stream abstraction. Imperative Java може да прави streaming чрез други APIs, но virtual threads сами по себе си не дават stream composition protocol.

---

## 7. Backpressure

Spring WebFlux е Reactive Streams based и моделира demand между consumer и producer. Virtual thread означава евтин Java Thread за task; той сам по себе си не определя колко data consumer-ът може да приеме, как producer-ът разбира demand, buffering policy или stream transformations.

Това е причината твърдението **„Loom прави Reactive ненужно“** да е прекалено широко.

---

## 8. Сравнителна карта

| Проблем | WebMVC + Loom | WebFlux + Reactor |
| --- | --- | --- |
| blocking synchronous code | естествен | не трябва да block-ваме event loop |
| finite fan-out/fan-in | `StructuredTaskScope` | `Mono.zip` |
| global timeout | scope `withTimeout` | `.timeout(...)` |
| max downstream concurrency | `Semaphore` / limiter | `flatMap(..., concurrency)` |
| child work lifecycle | lexical structured scope | subscription/disposal graph |
| blocking JDBC | естествено с virtual threads | blocking isolation или reactive driver |
| continuous stream | не е основната abstraction | `Flux` |
| Reactive Streams backpressure | не идва от virtual threads | core property |
| stream transformations | друг abstraction / ръчно | core strength |

Това не е класация — сравняваме свойства.

---

## 9. Как да стартираме

От repository root:

```bash
mvn clean verify
```

Само този модул:

```bash
mvn -pl spring/concurrency/loom-vs-reactive -am verify
```

Backend:

```bash
cd spring/concurrency/loom-vs-reactive
mvn -pl backend-service spring-boot:run
```

MVC/Loom (Structured Concurrency е preview в Java 25):

```bash
mvn -pl mvc-loom spring-boot:run -Dspring-boot.run.jvmArguments=--enable-preview
```

WebFlux:

```bash
mvn -pl webflux-reactor spring-boot:run
```

После отвори [`http/loom-vs-reactive.http`](./http/loom-vs-reactive.http) и пускай заявките от IntelliJ.

---

## 10. Какво да наблюдаваш

- **fan-out:** еднакъв business outcome, различен concurrency model;
- **global timeout:** гледай response-а и реалното elapsed time — blocking library може да не реагира веднага на interrupt;
- **bounded:** и двата good варианта трябва да пазят downstream под concurrency 2;
- **events:** SSE връзката продължава да получава values до disconnect/cancel.

---

## 11. Practical checklist

1. Работата finite ли е или stream-ът може да живее неограничено?
2. Използваме ли blocking JDBC/HTTP libraries?
3. Искаме ли imperative call-stack style code?
4. Къде е реалният scarce resource?
5. Имаме ли global request deadline, а не само individual timeouts?
6. Cancellation cooperative ли е по целия call chain?
7. Има ли producer/consumer speed mismatch?
8. Трябва ли ни backpressure protocol?
9. Имаме ли сложни stream transformations?
10. Избираме ли abstraction според problem semantics, а не според мода?

---

## 12. README → код

| Концепция | Loom | Reactor |
| --- | --- | --- |
| fan-out | [`LoomComparisonController`](./mvc-loom/src/main/java/bg/hristomanov/education/concurrency/mvcloom/LoomComparisonController.java) | [`ReactorComparisonController`](./webflux-reactor/src/main/java/bg/hristomanov/education/concurrency/webflux/ReactorComparisonController.java) |
| global timeout | `Configuration.withTimeout` | `Mono.timeout` |
| bounded downstream | `Semaphore(2)` | `flatMap(..., 2)` |
| infinite stream | — | `Flux.interval` + SSE |
| controlled downstream | [`DemoBackendController`](./backend-service/src/main/java/bg/hristomanov/education/concurrency/backend/DemoBackendController.java) | същият backend |
| executable requests | [`loom-vs-reactive.http`](./http/loom-vs-reactive.http) | същият файл |

---

## Оригинални източници

- YouTube — **Concurrency and Streaming in the Age of Loom**: https://www.youtube.com/watch?v=yWVxJLRtCxI
- Spring I/O 2026 session: https://2026.springio.net/sessions/concurrency-and-streaming-in-the-age-of-loom/
- Original demo: https://github.com/chemicL/springio-2026-loom
- JEP 444 — Virtual Threads: https://openjdk.org/jeps/444
- JEP 505 — Structured Concurrency: https://openjdk.org/jeps/505
- Java 25 StructuredTaskScope: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/concurrent/StructuredTaskScope.html
- Spring WebFlux: https://docs.spring.io/spring-framework/reference/web/webflux.html
- Reactive Streams: https://www.reactive-streams.org/

---

## Финален mental model

```text
Loom
→ евтини threads
→ structured finite tasks
→ imperative request/response code

Reactor
→ publisher/subscriber model
→ stream composition
→ demand/backpressure
→ continuous pipelines
```

Правилният избор започва от проблема, не от API-то.