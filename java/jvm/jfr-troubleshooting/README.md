# JDK Flight Recorder — production profiling и troubleshooting

> Тази лаборатория е за първо реално използване на **JDK Flight Recorder (JFR)**. Целта не е да запомним команди, а да изградим навик: **симптом → recording → доказателство → root cause → fix → нов recording**.

Основният материал е JavaOne 2026 сесията **“The Power of JDK Flight Recorder: Efficient Profiling and Troubleshooting for Java Applications”** на Mikael Vidstedt. Лабораторията е разширена с Java 25-specific възможности, production-like Spring Boot сценарии и автоматизиран тест на custom JFR event.

---

# ВХОД В ТЕМАТА

## 1. Реалният казус: „приложението понякога е бавно“

Представи си Spring Boot service в production.

Получаваме оплакване:

> „От време на време някои заявки отнемат 3–5 секунди. След restart всичко пак изглежда нормално.“

Имаме стандартните dashboards:

- CPU понякога се качва;
- heap постепенно расте;
- няма очевиден exception;
- database latency изглежда приемлива;
- логовете показват, че request-ът е започнал и приключил, но не **къде** е отишло времето.

Възможните причини са коренно различни:

```text
бавен request
│
├── CPU hotspot?
├── прекалено много allocations / GC?
├── memory retention / leak?
├── threads чакат lock?
├── file/socket I/O?
├── JIT / class loading / safepoint?
└── конкретна business операция?
```

Ако започнем да гадаем, лесно стигаме до това:

```text
симптом
→ добавяме още логове
→ deploy
→ чакаме проблемът да се повтори
→ логовете пак не стигат
→ още логове
→ още един deploy
```

Тази лаборатория показва друг workflow:

```text
симптом
→ JFR recording
→ виждаме какво реално е правила JVM
→ намираме evidence (доказателство)
→ локализираме root cause
→ поправяме
→ втори recording доказва подобрението
```

---

## 2. Защо този проблем е важен

Production performance проблемите често са **временни** и **контекстни**:

- CPU spike-ът е минал, когато отворим debugger;
- lock contention се появява само при concurrency;
- leak-ът се натрупва с часове;
- GC проблемът зависи от allocation rate;
- I/O latency зависи от външен ресурс;
- thread dump показва само един момент, а не историята преди него.

JFR е ценен именно защото записва **времева линия от runtime events** (събития за поведението на JVM и приложението), а не само snapshot на един момент.

---

## 3. Какво ще научим

След лабораторията трябва да можеш:

1. да обясниш какво е JFR и какво **не** е;
2. да стартираш recording върху вече работеща JVM с `jcmd`;
3. да dump-неш recording в `.jfr` файл;
4. да направиш първичен анализ с `jfr summary`, `jfr view` и `jfr print`;
5. да отвориш файла в JDK Mission Control (JMC);
6. да разпознаеш CPU hotspot;
7. да разпознаеш allocation/memory pressure;
8. да видиш monitor contention;
9. да видиш file I/O;
10. да дефинираш application-specific custom JFR event;
11. да тестваш custom event автоматично;
12. да знаеш кои нови JFR възможности в Java 25 са practically relevant;
13. да различаваш JFR от logs, metrics, distributed tracing и APM.

---

## 4. Mental model: JFR като „черна кутия“ на JVM

Не мисли за JFR като за още един logger.

Мисли за него като за **event recorder**, вграден в JVM:

```text
Spring Boot application
        │
        ▼
┌──────────────────────────────┐
│ JVM / HotSpot                │
│                              │
│ CPU samples                  │
│ GC events                    │
│ allocations                  │
│ thread / lock events         │
│ file / socket I/O            │
│ class loading / JIT          │
│ custom application events    │
└──────────────┬───────────────┘
               │
               ▼
         Flight Recording
               │
        ┌──────┴──────┐
        ▼             ▼
      jfr CLI      JDK Mission
                    Control
```

Един event обикновено има:

- timestamp (кога е станал);
- duration (ако има продължителност);
- thread;
- payload полета;
- понякога stack trace.

Това позволява да свържем:

> „Business операцията беше бавна“

с:

> „По същото време този thread чакаше monitor / имаше heavy allocations / CPU samples сочат този method.“

### Правило за запомняне

> **Metrics казват, че имаме проблем. JFR често помага да разберем какво е правила JVM, когато проблемът е възникнал.**

---

# README → код

| Концепция | Production-like пример | Доказателство / runnable пример |
| --- | --- | --- |
| Spring Boot boundary | [`JfrLabController.java`](./src/main/java/bg/hristomanov/education/jfr/controller/JfrLabController.java) | [`jfr-lab.http`](./http/jfr-lab.http) |
| CPU hotspot | [`CpuHotspotService.java`](./src/main/java/bg/hristomanov/education/jfr/service/CpuHotspotService.java) | `GET /api/jfr/cpu` |
| Memory retention | [`MemoryLeakService.java`](./src/main/java/bg/hristomanov/education/jfr/service/MemoryLeakService.java) | [`MemoryLeakServiceTest.java`](./src/test/java/bg/hristomanov/education/jfr/service/MemoryLeakServiceTest.java) |
| Monitor contention | [`LockContentionService.java`](./src/main/java/bg/hristomanov/education/jfr/service/LockContentionService.java) | `POST /api/jfr/locks` |
| File I/O | [`FileIoService.java`](./src/main/java/bg/hristomanov/education/jfr/service/FileIoService.java) | `POST /api/jfr/io` |
| Custom business event | [`OrderProcessingEvent.java`](./src/main/java/bg/hristomanov/education/jfr/jfr/OrderProcessingEvent.java) | [`OrderProcessingEventRecordingTest.java`](./src/test/java/bg/hristomanov/education/jfr/jfr/OrderProcessingEventRecordingTest.java) |
| Business operation + event lifecycle | [`OrderProcessingService.java`](./src/main/java/bg/hristomanov/education/jfr/service/OrderProcessingService.java) | `POST /api/jfr/order` |
| One-click demo scenario | [`DemoScenarioService.java`](./src/main/java/bg/hristomanov/education/jfr/service/DemoScenarioService.java) | `POST /api/jfr/scenario` |
| JMC анализ | [`JMC-GUIDE.md`](./JMC-GUIDE.md) | отвори generated `.jfr` файл |
| Java 25 JFR новости | [`JDK-25-JFR.md`](./JDK-25-JFR.md) | JEP 509 / 518 / 520 |

---

# РАЗБИРАНЕ НА JFR

## 5. Термини

| Термин | Какво означава |
| --- | --- |
| **JFR** | JDK Flight Recorder — event recording framework, вграден в OpenJDK/HotSpot |
| **event** | структурирано runtime събитие |
| **recording** | период от време, в който избран набор events се записват |
| **configuration / settings** | кои events са включени и с какви thresholds/periods |
| **default** | low-overhead конфигурация, подходяща за по-дълго/continuous наблюдение |
| **profile** | по-богата конфигурация за кратко investigation, с повече overhead |
| **sample** | статистическа снимка на текущ execution stack, не instrumentation на всеки call |
| **stack trace** | call stack-ът, довел до събитието |
| **JMC** | JDK Mission Control — GUI за анализ на JFR recordings |
| **jcmd** | JDK diagnostic command tool за управление на работещ JVM process |
| **jfr** | CLI tool за анализ/филтриране на `.jfr` файлове |
| **custom event** | JFR event, дефиниран от нашето приложение |

---

## 6. JFR не е profiler „само за CPU“

Това е важно разграничение.

CPU profiling е **само една част**.

JFR може да съдържа информация за:

- execution samples;
- CPU load;
- garbage collections;
- allocation samples;
- old objects;
- thread starts/stops;
- monitor contention;
- park/sleep;
- file reads/writes;
- socket reads/writes;
- exceptions;
- class loading;
- JIT compilation;
- safepoints;
- custom application events.

Точно затова е useful за troubleshooting: различни root causes могат да бъдат видени по общ timeline.

---

## 7. `default` срещу `profile`

JDK идва с predefined configurations.

### `default`

Използвай го, когато искаш по-дълъг recording и минимално влияние.

```text
continuous / always-on thinking
↓
settings=default
```

### `profile`

Събира повече detail и е по-подходящ за кратък investigation.

```text
имаме конкретен проблем
↓
искаме повече stack/sample detail
↓
settings=profile
```

Не приемай `profile` като „по-добрия default“. Повече telemetry означава и повече работа.

---

# ПЪРВО СТАРТИРАНЕ — СТЪПКА ПО СТЪПКА

## 8. Prerequisites

- JDK 25;
- Maven 3.9+;
- IntelliJ IDEA;
- по желание JDK Mission Control.

От root директорията:

```bash
mvn --no-transfer-progress verify
```

Само този модул:

```bash
mvn -pl java/jvm/jfr-troubleshooting test
```

---

## 9. Стартирай приложението

От root директорията:

```bash
mvn -pl java/jvm/jfr-troubleshooting spring-boot:run
```

Или отвори [`JfrTroubleshootingApplication.java`](./src/main/java/bg/hristomanov/education/jfr/JfrTroubleshootingApplication.java) в IntelliJ и натисни **Run**.

Провери process информацията:

```bash
curl http://localhost:8080/api/jfr/info
```

<details>
<summary>Примерен response</summary>

```json
{
  "pid": 18432,
  "javaVersion": "25.0.x+...",
  "vmName": "OpenJDK 64-Bit Server VM",
  "osName": "Windows 11"
}
```

PID и version стойностите естествено ще са различни.

</details>

Най-удобният начин е [`http/jfr-lab.http`](./http/jfr-lab.http) — IntelliJ показва ▶ до всяка заявка.

---

## 10. Стартирай първия JFR recording върху вече работещото приложение

Това е workflow-ът, който искам да запомниш първо.

### 10.1 Намери JVM process-а

Можеш да използваш PID от `/api/jfr/info`, или:

```bash
jcmd -l
```

Ще видиш Java процесите.

### 10.2 Стартирай recording

Замени `<PID>`:

```bash
jcmd <PID> JFR.start name=education settings=profile maxage=10m maxsize=256M
```

Какво казваме:

- `name=education` — даваме име;
- `settings=profile` — искаме profiling detail;
- `maxage=10m` — пазим до 10 минути история;
- `maxsize=256M` — ограничаваме размера.

Провери:

```bash
jcmd <PID> JFR.check
```

### 10.3 Генерирай интересна работа

В IntelliJ пусни request-а `fullScenario` от [`jfr-lab.http`](./http/jfr-lab.http).

Или:

```bash
curl -X POST http://localhost:8080/api/jfr/scenario
```

Той последователно създава:

```text
CPU hotspot
→ monitor contention
→ file write/read
→ OrderProcessing custom JFR event
```

### 10.4 Dump-ни recording-а

```bash
jcmd <PID> JFR.dump name=education filename=java/jvm/jfr-troubleshooting/target/education.jfr
```

**Важно:** `JFR.dump` копира текущите recording данни във файл, но recording-ът продължава.

### 10.5 Спри го, когато приключиш

```bash
jcmd <PID> JFR.stop name=education
```

Това е основният lifecycle:

```text
JFR.start
   ↓
application workload
   ↓
JFR.check
   ↓
JFR.dump
   ↓
analysis
   ↓
JFR.stop
```

---

# ПЪРВИ АНАЛИЗ БЕЗ GUI

## 11. `jfr summary`

```bash
jfr summary java/jvm/jfr-troubleshooting/target/education.jfr
```

Това е първата проверка:

- каква е продължителността;
- колко events има;
- кои event types присъстват.

Не търсим root cause още. Проверяваме **какъв материал имаме**.

---

## 12. `jfr view`

`jfr view` агрегира events в човекочитаеми таблици.

Полезни views:

```bash
jfr view hot-methods java/jvm/jfr-troubleshooting/target/education.jfr
jfr view allocation-by-site java/jvm/jfr-troubleshooting/target/education.jfr
jfr view contention-by-site java/jvm/jfr-troubleshooting/target/education.jfr
jfr view file-reads-by-path java/jvm/jfr-troubleshooting/target/education.jfr
jfr view file-writes-by-path java/jvm/jfr-troubleshooting/target/education.jfr
```

За списък с predefined views:

```bash
jfr --help view
```

За да изпълниш всички predefined views върху recording-а:

```bash
jfr view all-views java/jvm/jfr-troubleshooting/target/education.jfr
```

Имената на наличните predefined views могат да се развиват между JDK версиите.

---

## 13. `jfr print`

`print` е полезно, когато искаме raw-ish detail за конкретен event type.

Нашият custom event:

```bash
jfr print --events bg.hristomanov.education.jfr.OrderProcessing java/jvm/jfr-troubleshooting/target/education.jfr
```

Можеш да видиш:

- start time;
- duration;
- orderId;
- itemCount;
- result;
- event thread;
- stack trace.

Това е една от най-важните идеи в лабораторията: **business context вътре в същия recording като JVM telemetry**.

---

# СЦЕНАРИЙ 1 — CPU HOTSPOT

## 14. Проблемът

[`CpuHotspotService.java`](./src/main/java/bg/hristomanov/education/jfr/service/CpuHotspotService.java) изпълнява много математически операции.

```text
HTTP request
→ CpuHotspotService
→ tight calculation loop
→ CPU samples сочат този call path
```

Пусни по-тежък вариант:

```bash
curl "http://localhost:8080/api/jfr/cpu?iterations=10000000"
```

После:

```bash
jfr view hot-methods java/jvm/jfr-troubleshooting/target/education.jfr
```

### Какво доказваме

Не казваме:

> „CPU е високо, сигурно Spring е бавен.“

Искаме stack/sample evidence, което сочи конкретен метод.

### Важно за sampling

Sampling не означава „измерихме абсолютно всяко извикване“.

JFR периодично наблюдава execution stacks. Ако един method се среща често в samples, това е силен сигнал, че прекарваме значително execution време там.

---

# СЦЕНАРИЙ 2 — MEMORY RETENTION / LEAK-LIKE BEHAVIOR

## 15. Проблемът

[`MemoryLeakService.java`](./src/main/java/bg/hristomanov/education/jfr/service/MemoryLeakService.java) пази `byte[]` масиви в instance list.

```text
request
→ new byte[16 MiB]
→ retainedBlocks.add(block)
→ strong reference остава
→ GC НЕ може да освободи масива
```

Пусни няколко пъти:

```bash
curl -X POST "http://localhost:8080/api/jfr/memory/retain?megabytes=16"
```

Статус:

```bash
curl http://localhost:8080/api/jfr/memory/status
```

След упражнението:

```bash
curl -X DELETE http://localhost:8080/api/jfr/memory
```

### Защо има safety limit

Това е учебен leak, не chaos test. Максимумът е 256 MiB.

Целта е да създадем достатъчно allocation/retention signal, без умишлено да стигаме до `OutOfMemoryError`.

### Какво гледаме

- allocation-by-site;
- object statistics;
- old object / memory leak related views, когато recording configuration ги съдържа;
- GC activity.

При реален leak може да се използва и GC-root информация, но това е по-тежка диагностична операция и не трябва механично да се включва постоянно.

---

# СЦЕНАРИЙ 3 — MONITOR CONTENTION

## 16. Проблемът

[`LockContentionService.java`](./src/main/java/bg/hristomanov/education/jfr/service/LockContentionService.java) създава няколко worker threads, които едновременно искат един `synchronized` monitor.

Един thread влиза:

```text
Thread-1
└── synchronized(sharedMonitor)
    └── държи lock 150 ms
```

Останалите:

```text
Thread-2 ── waiting for monitor
Thread-3 ── waiting for monitor
Thread-4 ── waiting for monitor
...
```

Пусни:

```bash
curl -X POST "http://localhost:8080/api/jfr/locks?workers=6&holdMillis=150"
```

После:

```bash
jfr view contention-by-site java/jvm/jfr-troubleshooting/target/education.jfr
```

### Ключовото знание

Лоша latency **не означава задължително висок CPU**.

Thread може просто да чака.

Затова това:

```text
CPU = 30%
request latency = 2s
```

не доказва, че application code-ът е „лек“.

Може да имаме сериен bottleneck около shared lock.

---

# СЦЕНАРИЙ 4 — FILE I/O

## 17. Проблемът

[`FileIoService.java`](./src/main/java/bg/hristomanov/education/jfr/service/FileIoService.java) записва и прочита временен файл.

Пусни:

```bash
curl -X POST "http://localhost:8080/api/jfr/io?megabytes=16"
```

После:

```bash
jfr view file-reads-by-path java/jvm/jfr-troubleshooting/target/education.jfr
jfr view file-writes-by-path java/jvm/jfr-troubleshooting/target/education.jfr
```

Идеята е да различаваме:

```text
CPU-bound
→ процесорът активно изпълнява инструкции

I/O-bound
→ thread-ът прекарва време около външна I/O операция
```

В production същият reasoning важи и за socket/network I/O.

---

# СЦЕНАРИЙ 5 — CUSTOM APPLICATION EVENT

## 18. Защо JVM events не винаги са достатъчни

JFR знае много за JVM, но не знае автоматично:

- какво е `orderId`;
- коя операция е „изчисляване на оферта“;
- кой business flow е важен;
- дали конкретна стъпка е SUCCESS/FAILURE.

Затова можем да дефинираме собствен event.

**Код:** [`OrderProcessingEvent.java`](./src/main/java/bg/hristomanov/education/jfr/jfr/OrderProcessingEvent.java)

Съществената идея е:

```java
public class OrderProcessingEvent extends Event {
    public String orderId;
    public int itemCount;
    public String result;
}
```

В production-like service-а:

**Код:** [`OrderProcessingService.java`](./src/main/java/bg/hristomanov/education/jfr/service/OrderProcessingService.java)

Lifecycle:

```text
new OrderProcessingEvent()
→ event.begin()
→ business work
→ event.end()
→ event.shouldCommit()
→ event.commit()
```

### Защо `shouldCommit()`

Представи си, че преди commit трябва да построим скъп diagnostic payload.

Ако event type-ът е disabled или event-ът е под configured threshold, няма смисъл да плащаме тази цена.

Затова pattern-ът е:

```java
event.end();

if (event.shouldCommit()) {
    // тук бихме събрали по-скъп optional diagnostic context
    event.commit();
}
```

---

## 19. Custom event + JVM events = много по-силен timeline

Можем да получим:

```text
OrderProcessing(orderId=123) ─────────────────────────────
        │
        ├── CPU execution samples
        ├── allocations
        ├── file/socket events
        ├── monitor events
        └── GC
```

Това е много по-полезно от отделен log line:

```text
Processing order 123
```

JFR event-ът е структуриран, timed и анализируем заедно с runtime telemetry.

---

# ДОКАЗАТЕЛСТВО С АВТОМАТИЧЕН ТЕСТ

## 20. Не разчитаме само на „отвори JMC и виж“

[`OrderProcessingEventRecordingTest.java`](./src/test/java/bg/hristomanov/education/jfr/jfr/OrderProcessingEventRecordingTest.java) използва официалния Java API:

1. създава `Recording`;
2. enable-ва нашия custom event;
3. стартира recording;
4. изпълнява `OrderProcessingService`;
5. спира recording;
6. dump-ва временен `.jfr` файл;
7. чете го обратно чрез `RecordingFile`;
8. assert-ва payload-а.

Mental model:

```text
JUnit
  │
  ├── Recording.start()
  │
  ├── business operation
  │      └── OrderProcessingEvent.commit()
  │
  ├── Recording.stop()
  │
  ├── dump .jfr
  │
  └── RecordingFile.readAllEvents()
          └── assertions
```

Това доказва, че custom event-ът не е само код, който „изглежда правилен“.

---

# JFR API — КОГА БИХ ГО ИЗПОЛЗВАЛ

## 21. `jcmd` срещу programmatic `Recording`

За production troubleshooting първата ми препоръка е да започнеш от:

```text
jcmd + existing process
```

Защо?

- не смесваш business code и diagnostic lifecycle;
- можеш да започнеш recording след възникване на проблем;
- operations екипът може да работи без application redeploy.

Programmatic API е полезен, когато:

- искаме test като нашия;
- приложението управлява специализиран recording;
- правим tooling;
- използваме event streaming;
- имаме конкретна automation нужда.

Не добавяй programmatic JFR management в application code само защото API-то съществува.

---

# JDK MISSION CONTROL

## 22. Кога CLI вече не стига

CLI е чудесен за:

- бърза server проверка;
- automation;
- конкретен event/view;
- SSH среда.

JMC е по-подходящ за:

- exploration;
- flame graph / stack analysis;
- времева корелация;
- GC/memory exploration;
- threads/locks;
- първо обучение.

Подробният first-use guide е в [`JMC-GUIDE.md`](./JMC-GUIDE.md).

---

# JAVA 25 — КАКВО Е НОВО ЗА JFR

## 23. Три важни JEP-а

Тази лаборатория е Java 25 baseline.

Java 25 добавя особено интересни JFR подобрения:

- **JEP 518 — JFR Cooperative Sampling**;
- **JEP 509 — JFR CPU-Time Profiling (Experimental)**;
- **JEP 520 — JFR Method Timing & Tracing**.

Подробно: [`JDK-25-JFR.md`](./JDK-25-JFR.md).

### Важна Windows бележка

Java 25 CPU-Time Profiling от JEP 509 е experimental и е Linux-specific.

Ако работиш локално на Windows, нормално е да използваш стандартните execution samples / `hot-methods` вместо да очакваш `jdk.CPUTimeSample` да работи.

Това **не означава**, че JFR като цяло не работи на Windows.

---

# BAD / НАИВЕН TROUBLESHOOTING ПОДХОД

## 24. „Ще добавим още логове“

Логовете са необходими, но не решават автоматично runtime profiling.

Например:

```java
log.info("Starting calculation");
calculate();
log.info("Finished calculation");
```

може да ни даде duration.

Но не казва автоматично:

- кой method консумира CPU;
- дали thread-ът чака monitor;
- какви allocations стават;
- какъв GC е имало;
- дали имаме file/socket events;
- кои stack traces доминират.

Друг naive подход:

> „Ще включим profiler чак когато проблемът се повтори.“

Проблемът може вече да е минал.

JFR е създаден именно с идеята за low-overhead recording и production troubleshooting.

---

# GOOD TROUBLESHOOTING WORKFLOW

## 25. Evidence-first подход

```text
1. Опиши симптома
2. Уточни времевия прозорец
3. Вземи JFR recording
4. Провери summary
5. Избери подходящи views/events
6. Намери stack/site/thread evidence
7. Формулирай root-cause hypothesis
8. Направи промяната
9. Повтори същия workload
10. Сравни recordings
```

Критична стъпка е №10.

Не приключвай с:

> „Направихме optimization, би трябвало да е по-добре.“

Искаме:

> „При същия workload вторият recording показва по-малко contention / по-нисък allocation rate / hotspot-ът е премахнат.“

---

# JFR ≠ LOGS ≠ METRICS ≠ TRACING

## 26. Те се допълват

### Logs

Отговарят добре на:

> „Какво business/application събитие се случи?“

### Metrics

Отговарят добре на:

> „Колко? Колко често? Как се движи във времето?“

Например:

```text
CPU = 95%
p99 latency = 2.4 s
heap = 78%
```

### Distributed tracing

Отговаря добре на:

> „През кои services мина request-ът и къде е distributed latency?“

### JFR

Отговаря добре на:

> „Какво се случваше вътре в тази JVM — CPU stacks, GC, allocations, locks, I/O, runtime events?“

### APM

Обикновено комбинира части от няколко категории и добавя agent/backend/UI.

Практически:

```text
metrics
→ алармира

distributed trace
→ локализира service-а

JFR
→ влиза по-дълбоко в конкретната JVM
```

Това не е универсална последователност, а полезен mental model.

---

# END-TO-END WALKTHROUGH

## 27. От нула до root-cause evidence

### Стъпка 1

Стартираме Spring Boot app.

### Стъпка 2

Вземаме PID:

```text
GET /api/jfr/info
```

### Стъпка 3

Стартираме:

```text
JFR.start settings=profile
```

### Стъпка 4

Пускаме:

```text
POST /api/jfr/scenario
```

### Стъпка 5

В JVM се случват:

```text
CpuHotspotService
   ↓
LockContentionService
   ↓
FileIoService
   ↓
OrderProcessingService
        ↓
OrderProcessingEvent
```

### Стъпка 6

Dump-ваме:

```text
education.jfr
```

### Стъпка 7

CLI:

```text
summary
hot-methods
contention-by-site
file-reads-by-path
custom OrderProcessing event
```

### Стъпка 8

Отваряме същия файл в JMC и разглеждаме timeline/stack данните.

Това е пълният loop — не просто „стартирахме profiler“.

---

# ПРЕДИ / СЛЕД

## 28. Как се променя начинът на debugging

| Преди | След |
| --- | --- |
| „Май CPU е проблемът“ | виждаме hot stack/method evidence |
| „Май GC прави пауза“ | гледаме реални GC events |
| „Май нишките блокират“ | гледаме monitor/thread events |
| „Май имаме leak“ | гледаме allocations/retained-object evidence |
| „Този order беше бавен“ | custom timed event + JVM timeline |
| restart и губим контекста | dump-ваме recording за analysis |
| fix по усещане | before/after recordings |

---

# PRODUCTION CONSIDERATIONS

## 29. Какво JFR не решава

JFR не поправя:

- лош SQL;
- липсващ index;
- недостатъчен DB pool;
- rate limit на downstream service;
- архитектурен distributed bottleneck;
- memory leak;
- lock contention.

То дава **evidence**, с което по-точно да ги диагностицираме.

---

## 30. Recording configuration има значение

Не enable-вай механично всичко.

Някои events са евтини, други могат да са по-скъпи.

Затова има:

- enabled/disabled;
- threshold;
- period;
- stack trace settings;
- throttling;
- predefined configurations.

Принцип:

> Събирай достатъчно data за въпроса, който разследваш — не максимално възможното количество data.

---

## 31. Не прави broad method tracing в production без причина

JEP 520 добавя method timing/tracing чрез instrumentation.

Това е различно от statistical sampling.

Ако кажеш:

> „Инструментирай огромна част от application code-а“

цената може да стане значима.

По-добре:

```text
имаме конкретен подозрителен method
→ trace/time точно него
→ кратък targeted recording
```

---

## 32. Sensitive data

Custom event payload-ът се записва в diagnostic artifact.

Не слагай безмислено:

- passwords;
- access tokens;
- personal data;
- full request/response bodies;
- secrets.

Treat-вай `.jfr` файла като диагностичен файл, който може да съдържа чувствителна operational информация.

---

# КОГА НЕ СИ СТРУВА

## 33. Не всяко bug investigation изисква JFR

JFR не е първата стъпка, ако:

- имаме очевиден deterministic unit-test bug;
- stack trace директно показва грешката;
- SQL explain plan вече доказва DB проблема;
- problem domain е извън JVM и имаме по-добър специализиран инструмент.

Използвай го, когато runtime behavior е част от неизвестното.

---

# УПРАЖНЕНИЯ

## 34. След като минеш basic flow-а

1. Увеличи CPU iterations и сравни два recordings.
2. Пусни само lock scenario и намери contention site-а.
3. Промени `LockContentionService`, така че `Thread.sleep()` да е извън `synchronized`, и сравни recording-а.
4. Retain-ни 16 MiB няколко пъти и наблюдавай allocations/heap behavior.
5. Изчисти memory references и наблюдавай как се променя картината след GC.
6. Увеличи file I/O размера и намери file paths във JFR.
7. Добави поле `customerId` към custom event.
8. Добави втори custom event за отделна подоперация.
9. Задай по-висок `@Threshold` и виж кои events вече не се commit-ват.
10. Направи before/after optimization на CPU loop-а.
11. На Linux/JDK 25 опитай experimental CPU-Time Profiling от [`JDK-25-JFR.md`](./JDK-25-JFR.md).
12. Използвай JEP 520 Method Timing върху точно един service method.
13. Отвори recording-а в JMC без да използваш CLI и опитай сам да стигнеш до същите изводи.
14. После направи обратното: само CLI, без JMC.

---

# ИЗХОД ОТ ТЕМАТА

## 35. Какво точно решихме

В началото имахме неясен production симптом:

> „Java service-ът понякога е бавен.“

Проблемът беше, че логовете и high-level metrics не ни казват автоматично какво е правила JVM вътре в този времеви прозорец.

Добавихме лаборатория, която възпроизводимо създава:

- CPU pressure;
- heap retention;
- monitor contention;
- file I/O;
- custom business event.

Научихме се да:

```text
start recording
→ generate workload
→ dump
→ inspect
→ correlate
→ формулираме evidence-based root cause
```

И доказахме custom event-а с автоматичен `.jfr` recording test.

---

## 36. Mental model за запомняне

### Правило 1

> **JFR записва runtime events; не е просто CPU profiler.**

### Правило 2

> **Първо evidence, после optimization.**

### Правило 3

> **След fix направи втори recording. Иначе имаш предположение, не доказателство.**

---

## 37. Practical checklist за реален incident

Когато имаш production JVM проблем:

- [ ] Какъв е точният симптом?
- [ ] От кога до кога е наблюдаван?
- [ ] CPU, memory, latency или throughput?
- [ ] Имаме ли подходящ JFR recording за този прозорец?
- [ ] `default` или кратък `profile` recording е по-подходящ?
- [ ] Какво показва `jfr summary`?
- [ ] Кои event/view категории отговарят на въпроса?
- [ ] Имаме ли stack/site/thread evidence?
- [ ] Корелира ли се с business event/log/trace?
- [ ] Каква е root-cause hypothesis?
- [ ] Можем ли да я възпроизведем?
- [ ] Имаме ли before/after recording след fix?
- [ ] Проверили ли сме дали diagnostic artifact-ът съдържа sensitive data?

---

# Оригинални източници

## Основният материал

- JavaOne 2026 / YouTube — **The Power of JDK Flight Recorder: Efficient Profiling and Troubleshooting for Java Applications**  
  https://www.youtube.com/watch?v=vu0XEBplqpg
- Inside Java page за сесията:  
  https://inside.java/2026/08/11/efficient-java-apps-profiling-troubleshooting/
- Demo/troubleshooting repository, посочен сред ресурсите на материала:  
  https://github.com/jaokim/inside-java-dumpster

## Официална JFR документация

- dev.java — JDK Flight Recorder learning material:  
  https://dev.java/learn/jvm/jfr/
- Java 25 Flight Recorder API Programmer's Guide:  
  https://docs.oracle.com/en/java/javase/25/jfapi/
- Java 25 `jdk.jfr` API:  
  https://docs.oracle.com/en/java/javase/25/docs/api/jdk.jfr/jdk/jfr/package-summary.html
- Java 25 `RecordingStream` API:  
  https://docs.oracle.com/en/java/javase/25/docs/api/jdk.jfr/jdk/jfr/consumer/RecordingStream.html
- JDK Mission Control:  
  https://www.oracle.com/java/technologies/jdk-mission-control.html
- OpenJDK JMC project:  
  https://github.com/openjdk/jmc

## JFR JEP-ове

- JEP 328 — Flight Recorder:  
  https://openjdk.org/jeps/328
- JEP 509 — JFR CPU-Time Profiling (Experimental):  
  https://openjdk.org/jeps/509
- JEP 518 — JFR Cooperative Sampling:  
  https://openjdk.org/jeps/518
- JEP 520 — JFR Method Timing & Tracing:  
  https://openjdk.org/jeps/520

## Допълнителни Inside Java материали

- What's new for JFR in JDK 25:  
  https://inside.java/2025/06/03/new-jfr-jdk25/
- What's New in Java 25 in 2 Minutes — секциите за JFR:  
  https://inside.java/2025/10/17/new-in-jdk-25-2-mins/
- JFR View Command:  
  https://inside.java/2023/09/26/sip082/

---

Лабораторията използва Java 25 като project baseline. JavaOne 2026 материалът сочи и към Java 26 документация; core JFR mental model-ът е същият, но винаги проверявай конкретната JDK версия за event availability и нови capabilities.
