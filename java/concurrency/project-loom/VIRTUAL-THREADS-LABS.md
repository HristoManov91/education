# Virtual Threads — допълнителни практически лаборатории

Този документ надгражда основната лаборатория
[Project Loom — Virtual Threads, Structured Concurrency и Scoped Values](./README.md).

Целта не е да повторим какво е virtual thread. Основният модул вече обяснява
platform/virtual/carrier threads, mount/unmount, Structured Concurrency, Scoped Values,
cancellation и ограниченията на Loom.

Тук правим следващата стъпка: **доказваме с малки executable експерименти как избираме
concurrency policy, как се държи request context-ът, как измерваме scalability и как
наблюдаваме много virtual threads с JDK tooling.**

> Baseline: Java 25 LTS. Structured Concurrency е preview API, затова build и runtime
> командите използват `--enable-preview`.

---

## 1. Реалният казус

Имаме типичен backend, който:

1. приема HTTP request;
2. прави няколко blocking calls към външни услуги;
3. иска да пази request/correlation context;
4. понякога иска **най-бързия** успешен отговор;
5. друг път иска **най-добрия** от няколко отговора;
6. под товар трябва да обслужва много едновременно чакащи операции;
7. при проблем трябва да можем да видим какво реално правят virtual threads.

Наивното решение е да приемем:

- "щом е virtual thread, всичко автоматично ще скалира";
- "един Joiner е достатъчен за всички случаи";
- "ThreadLocal е request context, значи child thread-овете ще го виждат";
- "ако приложението се чувства по-бързо, значи virtual threads са го ускорили".

Точно тези предположения проверяваме тук.

---

# Lab 1 — First successful vs best successful result

## Проблемът

Да кажем, че питаме няколко provider-а.

Понякога business requirement-ът е:

> Дай ми първия успешен резултат възможно най-бързо.

Тогава built-in policy-то:

```java
StructuredTaskScope.Joiner.anySuccessfulResultOrThrow()
```

е отлично решение.

Но друг requirement може да бъде:

> Изчакай всички достъпни provider-и и избери резултата с най-високо качество.

Това вече **не е същата concurrency policy**.

## Код

- [BestSuccessfulResultJoiner.java](./loom-labs/src/main/java/bg/hristomanov/education/loom/labs/joiner/BestSuccessfulResultJoiner.java)
- [JoinerPolicyDemo.java](./loom-labs/src/main/java/bg/hristomanov/education/loom/labs/joiner/JoinerPolicyDemo.java)
- [BestSuccessfulResultJoinerTest.java](./loom-labs/src/test/java/bg/hristomanov/education/loom/labs/joiner/BestSuccessfulResultJoinerTest.java)

Demo-то има три provider-а:

```text
fast-provider
    latency ~150 ms
    quality 70

flaky-provider
    latency ~220 ms
    failure

quality-provider
    latency ~350 ms
    quality 95
```

### Latency-first

```text
fast-provider ───── success ─────┐
quality-provider ────────────────┼── first success => STOP
flaky-provider ──────────────────┘
```

Очакваме резултат около:

```text
fast-provider / quality 70
```

### Quality-first

Custom Joiner-ът не cancel-ва при първия success:

```text
fast-provider ───── quality 70 ─────┐
flaky-provider ───── failure ───────┼── wait all
quality-provider ─── quality 95 ────┘
                                      ↓
                                 choose best
                                      ↓
                               quality-provider
```

Ако има поне един успешен резултат, отделен provider failure не проваля операцията.

Ако **всички** fail-нат, Joiner-ът създава aggregate exception и пази конкретните
provider failures като suppressed exceptions.

## Защо custom Joiner-ът е generic

Не слагаме business правило от типа:

```java
return result.creditScore() > ...
```

в самия Joiner.

Вместо това той приема:

```java
Comparator<? super T>
```

Така Joiner-ът описва общото concurrency поведение:

> събери успешните резултати, изчакай всички и избери максималния според comparator-а.

А business кодът решава какво означава "по-добър".

## Важна thread-safety подробност

Java 25 API изрично позволява `onComplete(...)` да бъде извикан едновременно
от няколко child threads.

Затова mutable collections вътре в custom Joiner не могат да бъдат обикновени
несинхронизирани `ArrayList`-и.

---

# Lab 2 — ThreadLocal vs ScopedValue

## Проблемът

Имаме `requestId`, който не искаме да прокарваме като parameter през десет метода.

Историческият reflex е:

```java
ThreadLocal<String>
```

Но трябва да сме точни какво означава "thread local".

## BAD — обикновен ThreadLocal

Код:

[ThreadLocalRequestContext.java](./loom-labs/src/main/java/bg/hristomanov/education/loom/labs/context/bad/ThreadLocalRequestContext.java)

```text
parent thread
ThreadLocal=request-42
        │
        └── StructuredTaskScope.fork(...)
                    │
                    ▼
              child virtual thread
              ThreadLocal = null
```

Обикновеният `ThreadLocal` принадлежи на конкретния `Thread`.
Structured child task-ът не получава автоматично стойността му.

Освен това lifecycle-ът е manual:

```java
REQUEST_ID.set(...);

try {
    ...
} finally {
    REQUEST_ID.remove();
}
```

## GOOD — ScopedValue

Код:

[ScopedValueRequestContext.java](./loom-labs/src/main/java/bg/hristomanov/education/loom/labs/context/good/ScopedValueRequestContext.java)

```text
ScopedValue.where(REQUEST_ID, "request-42")
        │
        └── StructuredTaskScope.open()
                    │
                    └── fork(...)
                           │
                           ▼
                     child sees request-42
```

Binding-ът има bounded lifetime:

```text
before call    -> unbound
inside call    -> request-42
child task     -> request-42
after call     -> unbound
```

Няма `remove()`.

## Executable доказателство

- [ContextPropagationDemo.java](./loom-labs/src/main/java/bg/hristomanov/education/loom/labs/context/ContextPropagationDemo.java)
- [ContextPropagationTest.java](./loom-labs/src/test/java/bg/hristomanov/education/loom/labs/context/ContextPropagationTest.java)

Очакваният mental model е:

```text
ThreadLocal
= state attached to a Thread

ScopedValue
= immutable-like value bound to a bounded execution scope
  and inherited through the structured task tree
```

---

# Lab 3 — Platform Threads vs Virtual Threads под товар

## Какво искаме да докажем

Virtual threads **не правят един blocking HTTP call магически по-бърз**.

Ако downstream операцията отнема 700 ms, virtual thread не я превръща в 70 ms.

Печалбата е другаде:

```text
много concurrent requests
        +
голяма част от времето се чака I/O
        ↓
thread-per-request моделът вече може да скалира много по-добре
```

## Server profiles

### Platform threads

[application-platform.properties](./bank-api/src/main/resources/application-platform.properties)

```properties
spring.threads.virtual.enabled=false
server.tomcat.threads.max=16
```

Малкият pool е умишлен за лабораторията, за да видим queueing-а лесно.

### Virtual threads

[application-virtual.properties](./bank-api/src/main/resources/application-virtual.properties)

```properties
spring.threads.virtual.enabled=true
```

Business кодът е същият.

Това е важно: не сравняваме две различни архитектури. Сменяме threading mode-а
под един и същ blocking Spring MVC flow.

## Load generator

[VirtualThreadLoadExperiment.java](./loom-labs/src/main/java/bg/hristomanov/education/loom/labs/load/VirtualThreadLoadExperiment.java)

Той:

- изпраща по default 80 заявки;
- държи до 40 едновременно активни;
- използва virtual threads от страната на generator-а;
- измерва total time;
- requests/sec;
- p50;
- p95;
- max latency;
- success/failure count.

Това **не е JMH benchmark** и не трябва да се публикува като универсално performance число.
Целта е локално да видиш queueing и throughput поведението.

## Стъпка 1 — стартирай dummy downstream service

От:

```text
java/concurrency/project-loom
```

изпълни:

```bash
mvn -pl bank-services spring-boot:run
```

## Стъпка 2 — platform profile

В друг terminal:

```bash
mvn -pl bank-api spring-boot:run -Dspring-boot.run.profiles=platform
```

Стартирай от IntelliJ:

```text
VirtualThreadLoadExperiment.main()
```

Запиши:

```text
Requests/sec
p50
p95
max
total time
```

## Стъпка 3 — virtual profile

Спри само `bank-api` и го стартирай отново:

```bash
mvn -pl bank-api spring-boot:run -Dspring-boot.run.profiles=virtual
```

Пусни **същия** load experiment със същите:

```text
request count
concurrency
machine
downstream service
```

После сравни резултатите.

## Как да интерпретираш резултата

Не търси задължително:

```text
virtual = X пъти по-бързо
```

Търси:

- дали platform pool-ът създава queueing;
- как се променя p95;
- как се променя total throughput;
- дали downstream вече става bottleneck;
- какво става при по-висока concurrency.

След определена точка virtual threads също няма да помагат, ако bottleneck-ът е:

- DB connection pool;
- downstream rate limit;
- CPU;
- lock contention;
- memory;
- бавен SQL.

---

# Lab 4 — Observability: виж много virtual threads

Код:

[VirtualThreadDumpDemo.java](./loom-labs/src/main/java/bg/hristomanov/education/loom/labs/observability/VirtualThreadDumpDemo.java)

Demo-то създава по default:

```text
2 000 virtual threads
```

и ги блокира временно върху `CountDownLatch`.

След стартиране отпечатва PID, например:

```text
PID: 12345
```

Докато процесът е жив:

```bash
jcmd 12345 Thread.dump_to_file -format=json virtual-threads.json
```

Така можеш реално да видиш virtual threads в JDK thread dump, вместо само:

```java
Thread.currentThread().isVirtual()
```

След default 30 секунди demo-то освобождава latch-а, изчаква всички threads и приключва.

## Връзка с JFR модула

Тази лаборатория отговаря основно на:

> "Какви threads съществуват в момента?"

За performance troubleshooting продължението е отделният модул:

[JDK Flight Recorder — production profiling и troubleshooting](../../jvm/jfr-troubleshooting/README.md)

Там вече разглеждаме JFR/JMC, CPU, locks, memory, file I/O и custom events.

---

# Как да build-нем лабораториите

От repository root:

```bash
mvn clean verify
```

Само Loom labs:

```bash
mvn -pl java/concurrency/project-loom/loom-labs -am test
```

Structured Concurrency е preview API в Java 25, затова Maven конфигурацията на
`project-loom` вече добавя `--enable-preview` за compilation и tests.

За директно стартиране след compile:

```bash
cd java/concurrency/project-loom
mvn -pl loom-labs compile

java --enable-preview -cp loom-labs/target/classes \
  bg.hristomanov.education.loom.labs.joiner.JoinerPolicyDemo
```

На Windows можеш просто да стартираш `main()` класовете от IntelliJ IDEA,
което е и препоръчаният начин за тези учебни примери.

---

# README → код

| Въпрос | Код | Доказателство |
| --- | --- | --- |
| First successful или best successful? | [JoinerPolicyDemo.java](./loom-labs/src/main/java/bg/hristomanov/education/loom/labs/joiner/JoinerPolicyDemo.java) | [BestSuccessfulResultJoinerTest.java](./loom-labs/src/test/java/bg/hristomanov/education/loom/labs/joiner/BestSuccessfulResultJoinerTest.java) |
| Как се пише custom Joiner? | [BestSuccessfulResultJoiner.java](./loom-labs/src/main/java/bg/hristomanov/education/loom/labs/joiner/BestSuccessfulResultJoiner.java) | success + all-failed tests |
| Защо ThreadLocal не е ScopedValue? | [bad/ThreadLocalRequestContext.java](./loom-labs/src/main/java/bg/hristomanov/education/loom/labs/context/bad/ThreadLocalRequestContext.java) / [good/ScopedValueRequestContext.java](./loom-labs/src/main/java/bg/hristomanov/education/loom/labs/context/good/ScopedValueRequestContext.java) | [ContextPropagationTest.java](./loom-labs/src/test/java/bg/hristomanov/education/loom/labs/context/ContextPropagationTest.java) |
| Как доказваме throughput ефекта? | [VirtualThreadLoadExperiment.java](./loom-labs/src/main/java/bg/hristomanov/education/loom/labs/load/VirtualThreadLoadExperiment.java) | platform/virtual profiles |
| Как виждаме много virtual threads? | [VirtualThreadDumpDemo.java](./loom-labs/src/main/java/bg/hristomanov/education/loom/labs/observability/VirtualThreadDumpDemo.java) | `jcmd Thread.dump_to_file` |

---

# Какво научихме

След тези лаборатории mental model-ът трябва да е:

```text
Virtual Threads
    ≠ faster CPU
    ≠ unlimited downstream capacity
    = cheap thread-per-task model for high-concurrency blocking workloads

StructuredTaskScope
    = lifecycle / ownership tree

Joiner
    = policy кога structured operation е готова и какъв outcome връща

ScopedValue
    = bounded context, естествено наследяван от structured children

ThreadLocal
    = state на конкретен Thread с manual lifecycle

Observability
    = не предполагаме; гледаме thread dump / JFR / metrics
```

## Practical checklist за реален backend

Преди да включим virtual threads в production приложение:

1. workload-ът основно I/O-bound ли е;
2. blocking libraries работят ли коректно с virtual threads;
3. къде са реалните resource limits — DB pool, HTTP connections, quotas;
4. имаме ли bounded concurrency към чувствителни downstream услуги;
5. правилната Joiner policy следва ли business requirement-а;
6. cancellation/interruption обработва ли се коректно;
7. request context-ът подходящ ли е за `ScopedValue`;
8. имаме ли metrics/JFR/thread dumps, с които можем да проверим поведението;
9. сравнили ли сме под реалистичен load platform и virtual mode;
10. избягваме ли твърдението "virtual threads са по-бързи" без да уточним throughput vs latency.

---

# Оригинални източници

## Видео, което мотивира тези допълнителни лаборатории

- YouTube — https://www.youtube.com/watch?v=4_UpZv21D3k

## Java 25

- JEP 444 — Virtual Threads: https://openjdk.org/jeps/444
- JEP 505 — Structured Concurrency: https://openjdk.org/jeps/505
- JEP 506 — Scoped Values: https://openjdk.org/jeps/506
- StructuredTaskScope API: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/concurrent/StructuredTaskScope.html
- StructuredTaskScope.Joiner API: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/concurrent/StructuredTaskScope.Joiner.html
- ScopedValue API: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/ScopedValue.html

## Spring

- Spring Boot reference: https://docs.spring.io/spring-boot/reference/
