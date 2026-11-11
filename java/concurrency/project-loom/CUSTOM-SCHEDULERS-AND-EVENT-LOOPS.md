# P1 — Virtual Thread schedulers, Netty event loops и истинските resource limits

> По [Ask the Architects, Devoxx Belgium 2026](https://www.youtube.com/watch?v=ewCPBgOrLAE), ~02:20–09:20 от предоставения SRT. Alan Bateman и други участници разглеждат custom scheduler prototypes, но **не обещават стабилен публичен Java 25 API**. Експериментален branch ≠ production feature.

## 1. Реален Spring backend казус

Имаме HTTP API, JDBC pool и външен REST service. С Virtual Threads започват повече concurrent requests, но downstream JDBC pool все още има само 20 конекции, а REST API допуска само 10 паралелни операции. Може ли отделен scheduler за всеки компонент да реши тези проблеми?

**Не автоматично.** Carrier pool обслужва virtual execution, но не увеличава CPU cores, JDBC connections или външните quotas. Отделни schedulers добавят разход и сложност по transfer между carrier thread pools и могат да направят debugging/pinning по-трудни. Архитектите подчертават нуждата от конкретни performance данни при Netty/event-loop integrations, а не „scheduler за всяка библиотека“.

### Два различни вида изолация

```text
JDK Virtual Threads (standard scheduler)    (does not create new CPU cores)
           |
    application resource guard (Semaphore / pool / rate limiter)
       /                  \
  JDBC permits: 1       REST permits: 2
       |                     |
  DB request              HTTP client
```

**Съществуващ lab:** [BoundedConcurrencyGuardTest](loom-labs/src/test/java/bg/hristomanov/education/loom/labs/limit/BoundedConcurrencyGuardTest.java) използва `Semaphore` и `StructuredTaskScope` за ограничение на скъп ресурс. **Ново допълнение:** [ResourceBulkheadsOnVirtualThreadsTest](loom-labs/src/test/java/bg/hristomanov/education/loom/labs/limit/ResourceBulkheadsOnVirtualThreadsTest.java) доказва два независими resource budgets (1 за DB и 2 за външен API) върху един стандартен Virtual Thread executor, без създаване на нов carrier scheduler.

```bash
mvn -pl java/concurrency/project-loom/loom-labs -am \
  -Dtest=ResourceBulkheadsOnVirtualThreadsTest -Dsurefire.failIfNoSpecifiedTests=false test
```

**Какво доказва JUnit:** 36 virtual tasks приключват, максимумът за DB никога не надвишава 1, за API не надвишава 2, permits се възстановяват. **Какво НЕ доказва:** scheduler fairness, production throughput, carrier pinning отсъствие, Netty compatibility или p99.

## 2. Netty: кога да останем на event loop

- **Nonblocking Netty pipeline**: малък брой event-loop threads е целенасочена архитектура. **Не** прави blocking JDBC или `Future.get()` на event loop — така спираш чужди заявки.
- **Blocking Java operations**: ако бизнес интеграцията е synchronous/IO-bound, граница към Virtual Threads може да опрости кода, но измервай context switching, resource limits и error propagation.
- **Native/FFM/monitor callbacks**: имат свои ограничения; Virtual Threads не отменят тези рискове. Виж [class-init JDK26](JDK26-CLASS-INITIALIZATION-WAIT.md).
- **Custom scheduler experiments**: не използвай неофициални internal scheduler hooks, reflection в JDK полета или `Thread` фабрики, представени като custom carrier pool. `jdk.virtualThreadScheduler.parallelism` настройва общия scheduler, **не** създава по един отделен scheduler за подсистема.

## 3. Decision contract

| Проблем | Кога да | Кога НЕ | Печалба | Цена / риск | По-просто |
| --- | --- | --- | --- | --- | --- |
| Virtual Threads | много blocked I/O calls, поддържан JDBC/HTTP stack | CPU-bound hot loop | лесен sync код при висока конкуренция | по-силен натиск върху downstream, memory footprint | platform pool + bounded queue |
| Semaphore/bulkhead | дефицитни конекции или ограничени външни API | задачата няма shared scarce resource | контролируем backlog | waiting/timeout, риск от deadlock при nested permits | existing connection pool limit |
| Няколко carrier schedulers | **сега е research тема, не стандартен Java25 API** | без profiling/особена нужда | потенциална изолация в специфични условия | JVM internals, чужда scheduling policy, overhead | default scheduler + bulkheads |
| Event-loop processing | вече nonblocking Netty система | блокиращ handler без offload | малко threads, добър IO multiplexing | blocking freezes loop; reactive complexity | Virtual Thread boundary само при доказана нужда |

**Разпознаване в production:** active JDBC connections, queue/wait times, HTTP concurrency, CPU throttling, event-loop latency и JFR VirtualThread events. Преди да промениш scheduler, измери именно дефицитния ресурс.

## 4. Изход и review checklist

**Преди:** „нов carrier scheduler ще изолира всяка подсистема“. **След:** различаваме scheduler/carrier ресурси от business resource limits и може да поставим граница на *истинското* ограничение. Не твърдим универсален отговор за Netty.

- [ ] Кой ресурс действително е ограничен: CPU, JDBC, remote quota, lock?
- [ ] Има ли блокиращ код на event-loop thread?
- [ ] Какво става при interruption и timeout докато чакаме permit?
- [ ] Има ли други nested resource limits, които може да предизвикат deadlock?
- [ ] Потвърден ли е scheduler version/flag вместо undocumented internal hook?
- [ ] Има ли representative JFR/RED/USE metrics и rollback?

## Оригинални източници

- [Ask the Architects, Devoxx 2026](https://www.youtube.com/watch?v=ewCPBgOrLAE), supplied SRT, ~02–09 мин.
- [JEP 444 — Virtual Threads](https://openjdk.org/jeps/444).
- [JEP 491 — Synchronize Virtual Threads without Pinning](https://openjdk.org/jeps/491).
- [Java 25 virtual threads guide](https://docs.oracle.com/en/java/javase/25/core/virtual-threads.html).
