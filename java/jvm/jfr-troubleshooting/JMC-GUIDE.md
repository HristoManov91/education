# JDK Mission Control — first-use guide за тази лаборатория

Този файл е нарочно практичен. Целта е първият път да не се изгубиш в JMC UI-а.

## 1. Какво е JMC

**JDK Mission Control (JMC)** е desktop приложение за анализ на Java runtime telemetry, включително JFR recordings.

JFR и JMC не са едно и също:

```text
JFR
→ записва runtime events

JMC
→ визуализира и анализира recording-а
```

Можеш да използваш JFR без JMC чрез `jcmd` и `jfr` CLI.

Можеш и да събереш `.jfr` файл на server, после да го анализираш на developer workstation.

Официална страница:

https://www.oracle.com/java/technologies/jdk-mission-control.html

OpenJDK project:

https://github.com/openjdk/jmc

---

## 2. Създай recording от лабораторията

Стартирай Spring Boot приложението.

Вземи PID:

```bash
curl http://localhost:8080/api/jfr/info
```

Стартирай:

```bash
jcmd <PID> JFR.start name=education settings=profile maxage=10m maxsize=256M
```

Генерирай workload:

```bash
curl -X POST http://localhost:8080/api/jfr/scenario
```

Dump:

```bash
jcmd <PID> JFR.dump name=education filename=java/jvm/jfr-troubleshooting/target/education.jfr
```

После можеш да спреш recording-а:

```bash
jcmd <PID> JFR.stop name=education
```

---

## 3. Отвори файла в JMC

В JMC отвори `java/jvm/jfr-troubleshooting/target/education.jfr`.

Точните имена/позиции на страниците могат леко да се различават между JMC версиите, но mental model-ът е същият.

Първо НЕ кликай безразборно всичко.

Работи по въпрос.

---

# ВЪПРОС 1: „Къде отива CPU времето?“

## 4. Търси execution/method sampling информация

Пусни преди dump:

```bash
curl "http://localhost:8080/api/jfr/cpu?iterations=20000000"
```

В JMC търси views около:

- Method Profiling;
- Execution Samples;
- Threads / stack traces;
- Flame Graph / Flame View, ако текущата JMC версия го предлага.

Търси:

```text
CpuHotspotService.burnCpu(...)
```

Не се фиксирай само в class name.

Отвори stack-а и проследи:

```text
HTTP request
→ controller
→ CpuHotspotService
→ burnCpu
```

### Какво искаме да заключим

Не:

> „Има някакъв CPU.“

А:

> „Samples многократно попадат в този method/call path, следователно той е силен CPU hotspot кандидат.“

Sampling е статистическо evidence, не exact per-invocation stopwatch.

---

# ВЪПРОС 2: „Имаме ли allocation/memory pressure?“

## 5. Генерирай retention

Докато recording-ът работи, пусни няколко пъти:

```bash
curl -X POST "http://localhost:8080/api/jfr/memory/retain?megabytes=16"
```

Провери:

```bash
curl http://localhost:8080/api/jfr/memory/status
```

В JMC разгледай:

- Memory;
- Garbage Collections;
- Allocations;
- TLAB / allocation samples, ако са налични в recording-а;
- Old Object / leak-related information, ако configuration/version го съдържа.

Търси:

```text
byte[]
MemoryLeakService
```

### Важна разлика

Allocation pressure и memory leak не са синоними.

```text
много allocations
→ може бързо да се освобождават
→ няма задължително leak

retained strong references
→ objects остават reachable
→ potential leak/retention
```

В нашия пример умишлено пазим strong references.

След упражнението:

```bash
curl -X DELETE http://localhost:8080/api/jfr/memory
```

---

# ВЪПРОС 3: „Threads чакат ли lock?“

## 6. Генерирай monitor contention

```bash
curl -X POST "http://localhost:8080/api/jfr/locks?workers=8&holdMillis=200"
```

В JMC търси:

- Threads;
- Locks;
- Java Monitor Enter;
- contention-related pages/events.

Кодът е в:

[`LockContentionService.java`](./src/main/java/bg/hristomanov/education/jfr/service/LockContentionService.java)

Root cause-ът е:

```text
много workers
→ един sharedMonitor
→ само един е вътре
→ останалите чакат
```

### Защо това е важно

Service може да е бавен, докато CPU не е максимално натоварен.

Ако гледаме само CPU dashboard, може да пропуснем lock bottleneck.

---

# ВЪПРОС 4: „Има ли I/O?“

## 7. Генерирай file I/O

```bash
curl -X POST "http://localhost:8080/api/jfr/io?megabytes=32"
```

В JMC потърси file read/write events.

Виж:

- path;
- bytes;
- duration;
- thread;
- stack trace, когато recording configuration го предоставя.

После се върни към:

[`FileIoService.java`](./src/main/java/bg/hristomanov/education/jfr/service/FileIoService.java)

Целта е да можеш да свържеш визуалното събитие с concrete code path.

---

# ВЪПРОС 5: „Коя business операция е била бавна?“

## 8. Custom OrderProcessing event

Пусни:

```bash
curl -X POST "http://localhost:8080/api/jfr/order?itemCount=10"
```

Нашият event type е:

```text
bg.hristomanov.education.jfr.OrderProcessing
```

В JMC event browser-а/filter-а търси този event.

Трябва да видиш:

- duration;
- `orderId`;
- `itemCount`;
- `result`;
- thread;
- stack trace.

После сравни времевия прозорец му с CPU/GC/thread/I/O events.

Това е истинската сила на custom events:

```text
business event
+
JVM internals
+
same timeline
```

---

# Automated Analysis Results

## 9. Използвай го като подсказка, не като oracle

JMC има автоматични rules/analysis резултати.

Те са полезни за:

- първоначален triage;
- намиране на подозрителни категории;
- бърз overview.

Но не приемай:

```text
rule warning
=
root cause доказан
```

Винаги отвори underlying events / stack traces / времевия контекст.

---

# Selection / time range

## 10. Най-полезният JMC навик

Когато проблемът е в конкретни 5 секунди, не анализирай механично целия 30-минутен recording.

Избери проблемния time range, когато UI-ът позволява, и разглеждай данните за него.

Причината:

```text
30 min normal traffic
+
5 sec incident
=
агрегираните стойности могат да скрият incident-а
```

Това е същата логика като при metrics/traces — контекстът във времето е критичен.

---

# CLI + JMC заедно

## 11. Добър practical workflow

Първо:

```bash
jfr summary education.jfr
```

После targeted CLI views:

```bash
jfr view hot-methods education.jfr
jfr view contention-by-site education.jfr
```

И чак след това JMC за по-дълбока exploration.

Така в JMC вече влизаш с въпрос, вместо просто да разглеждаш десетки панели.

---

# Checklist при отваряне на непознат recording

- [ ] Какъв период покрива?
- [ ] С коя JFR configuration е събран?
- [ ] Има ли problem timestamp?
- [ ] CPU симптом ли разследваме?
- [ ] Memory/GC?
- [ ] Lock/thread waiting?
- [ ] I/O?
- [ ] Има ли custom application events?
- [ ] Кои stack traces сочат application package-а?
- [ ] Данните достатъчни ли са или трябва втори targeted recording?
- [ ] След fix имаме ли comparison recording?

---

# Какво да НЕ правиш

Не започвай с:

> „Коя графика изглежда най-страшно?“

Започни с:

> „Какъв въпрос се опитвам да отговоря?“

JFR/JMC съдържат много data. Без hypothesis-driven analysis (анализ с конкретна проверима хипотеза) лесно се намират случайни „интересни“ неща, които нямат връзка с incident-а.
