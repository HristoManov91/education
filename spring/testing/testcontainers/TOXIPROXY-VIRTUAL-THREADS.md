# P2 · Testcontainers + Toxiproxy — реална мрежова латентност и Virtual Thread pinning

> Devoxx 2026 [Learning modern Java the playful way, 2:18:00–2:25:40](https://youtu.be/QBQ11aL2aZc?t=8280). **Изпълним Java 25 integration test + opt-in Java 21 срещу 25 cross-JDK probe.** Това разширява съществуващите [Testcontainers](README.md) и [Loom](../../../java/concurrency/project-loom/README.md) обучения, не нов микросървис.

## 1. Реалният казус

В Java 21 service ползва virtual threads, но изпълнява HTTP I/O вътре в `synchronized`. При бавна downstream услуга carrier threads могат да бъдат pinned и throughput да падне. Java 24 JEP 491 премахва стария monitor-based pinning, но **native / foreign blocking, CPU, DB pools и неспазване на interruption** остават отделни ограничения. Не твърдим, че всеки блокиращ API автоматично е interruptible.

```text
Test JVM                         Docker Network
HttpClient → mapped port 8666 → Toxiproxy → nginx:80
                         └── downstream latency toxic (650 ms)

[Optional comparison]
JDK 21 container — Java 21 source / virtual threads / synchronized block
JDK 25 container — СЪЩИЯТ Java 21 source и същият proxy
```

Няма фиксирани host ports и няма изключена authentication в реално приложение; nginx е **само лабораторна публична static HTTP страница**. Не използвай тези non-TLS настройки в production.

## 2. README → код → доказателство

| Цел | Конкретен файл | Проверка |
| --- | --- | --- |
| Real Docker network + proxy + nginx | [ToxiproxyScenario.java](src/test/java/bg/hristomanov/education/containers/ToxiproxyScenario.java) | [ToxiproxyLatencyIntegrationTest.java](src/test/java/bg/hristomanov/education/containers/ToxiproxyLatencyIntegrationTest.java) |
| Inject TCP downstream delay | `Proxy.toxics().latency(...)` в [test](src/test/java/bg/hristomanov/education/containers/ToxiproxyLatencyIntegrationTest.java) | HTTP 200 + доказано закъснение (долна граница, **не speedup**) |
| JDK21/25 comparison | [VirtualThreadPinningJdkComparisonTest.java](src/test/java/bg/hristomanov/education/containers/VirtualThreadPinningJdkComparisonTest.java) | [PinningProbe.java](src/test/resources/pinning/PinningProbe.java), еднакъв Java 21 source и вход |

### Normal CI lab (пускан само ако Docker е наличен)

```bash
mvn -pl spring/testing/testcontainers -Dtest=ToxiproxyLatencyIntegrationTest test
```

Testcontainers стартира nginx и Toxiproxy с частна Docker network. Добавяме `ToxicDirection.DOWNSTREAM` delay `650 ms` без jitter. `HttpClient` извиква **само** proxied endpoint-а, проверява HTTP 200/body и поне 400ms наблюдавана delay. Ресурсите и toxic се чистят и при failure. Това доказва integration topology, **не** pinning/CPU производителност.

### Opt-in сравнителен тест (не влиза в обичайния CI)

```bash
# Linux/macOS:
RUN_JDK_COMPARISON=true mvn -pl spring/testing/testcontainers \
  -Dtest=VirtualThreadPinningJdkComparisonTest test

# PowerShell (Windows):
$env:RUN_JDK_COMPARISON = 'true'
mvn -pl spring/testing/testcontainers -Dtest=VirtualThreadPinningJdkComparisonTest test
Remove-Item Env:RUN_JDK_COMPARISON
```

Тестът допълнително дърпа **две JDK image-и** (21/25) и изпълнява **еднакъв** source file с `java --source 21`. Всеки virtual thread използва **собствен монитор** (за да изолираме carrier pinning от сериализация върху общ lock), прави `Thread.sleep` и HTTP през Toxiproxy. Изходът показва runtime version/брой успешни заявки/време и диагностиката `-Djdk.tracePinnedThreads=full` където е поддържана (JDK 21).

**Не правим** flaky `JDK25 duration < JDK21 duration` CI assertion. За reliability/speedup conclusions прави repeated trials с еднакви CPU quotas, carrier parallelism, JFR `jdk.VirtualThreadPinned`, scheduling и CPU observations; възможно е избраният network client да offload-ва и да не показва същата pinning картина като JDK21 пример от лекцията. JDK25 може да има други pinning причини. Огледай реалните events, не само elapsed time.

### Negative controls, които да пробваш

1. Премахни toxic-а и виж, че HTTP 200 остава, но латентността е друга. Това **не** е автоматично доказателство за production gain.
2. Замени блокиращата операция в пробата с `synchronized` върху **един общ** lock: ще получиш lock contention и различна хипотеза. Не приписвай всичко на pinning.
3. Подай невалиден endpoint или спри upstream: провери exception / timeout path, без да изпращаш side-effecting POSTs.
4. За Java 21 използвай JFR/`jdk.tracePinnedThreads` и виж stack с monitor; за Java 25 провери native calls и други hotspots.

## 3. Decision contract

| Въпрос | Решение |
| --- | --- |
| Решаваме | Трудно възпроизводими IO stalls, поведение при latency/timeout, зависимости от JDK версия. |
| Кога | Virtual-thread migration, high concurrency I/O, external latency regressions, resilience testing. |
| Кога НЕ | Не правим общи speedup claims по един synthetic delay; не симулираме production със случайни `Thread.sleep` unit tests. |
| Печалби | Реален TCP поток и deterministic fault injector, работещи CI integration contract-и. |
| Загуби | Docker images/network overhead, CPU scheduling variance, test duration; допълнителни зависимости. |
| По-просто | Unit test за business exception; JDK JFR локално; Testcontainers без proxy ако не изследваш мрежата. |
| Доказателство | [Integration test](src/test/java/bg/hristomanov/education/containers/ToxiproxyLatencyIntegrationTest.java), optional [cross-JDK lab](src/test/java/bg/hristomanov/education/containers/VirtualThreadPinningJdkComparisonTest.java). |

## 4. Финален mental model

Избирай **fault injection**, когато проблемът е в network/IO границата. Разделяй `monitor contention`, `carrier pinning`, `downstream timeout` и `resource pool exhaustion` — това са различни механизми. При Java 25 не премахвай `synchronized` механично заради стари Java 21 истории. CI доказва HTTP/proxy contract; сравнителното performance твърдение изисква **отделно JFR профилиране и повторяем експеримент**.

## Оригинални източници

- [Devoxx, 2:18:00](https://youtu.be/QBQ11aL2aZc?t=8280), предоставен Tactiq transcript.
- [Testcontainers Toxiproxy module](https://java.testcontainers.org/modules/toxiproxy/).
- [Toxiproxy toxics reference](https://github.com/Shopify/toxiproxy#toxics).
- [JEP 491 — Synchronize Virtual Threads without Pinning](https://openjdk.org/jeps/491).
- [JDK 21 virtual threads / pinning](https://openjdk.org/jeps/444).
- [Testcontainers 2.x relocated ToxiproxyContainer](https://javadoc.io/doc/org.testcontainers/testcontainers-toxiproxy/2.0.5/org/testcontainers/toxiproxy/ToxiproxyContainer.html).
