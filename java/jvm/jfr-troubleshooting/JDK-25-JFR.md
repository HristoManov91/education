# JFR в Java 25 — practically relevant промени

Основният runnable код в този модул е Java 25.

JFR съществува много преди Java 25, но тази версия е особено интересна за profiling.

---

# 1. JEP 518 — JFR Cooperative Sampling

JEP:

https://openjdk.org/jeps/518

Inside Java summary:

https://inside.java/2025/07/21/jep518-target-jdk25/

## Какво решава

JFR sampling трябва да взима stack samples от threads.

Java 25 променя вътрешния sampling механизъм към cooperative подход.

За application developer-а най-важното е:

> Това е подобрение на вътрешната JFR sampling инфраструктура, а не нов API, който трябва да извикваме във всеки service.

Тоест обичайният ни workflow остава:

```text
record
→ sample
→ analyze stacks
```

но implementation-ът на stack sampling е подобрен.

## Какво да запомним

Не търси:

```java
new CooperativeSampler()
```

Няма такъв application workflow.

Това е JVM/JFR capability.

---

# 2. JEP 509 — JFR CPU-Time Profiling (Experimental)

JEP:

https://openjdk.org/jeps/509

## Какъв проблем решава

Класически execution sampling е свързан с wall-clock execution observation.

Но понякога искаме по-прецизно да разграничим:

```text
thread exists/runs over wall-clock time
```

от:

```text
thread действително консумира CPU cycles
```

Java 25 добавя experimental CPU-time profiling.

Event:

```text
jdk.CPUTimeSample
```

## Важно ограничение в Java 25

Тази experimental възможност е Linux-specific.

Ако разработваш на Windows:

- JFR работи;
- `ExecutionSample`/normal profiling работят;
- `jfr view hot-methods` остава полезно;
- но не очаквай Java 25 CPU-Time profiler-а да е наличен като на Linux.

Това е version/platform limitation, не проблем в проекта.

## Пример на Linux

След package:

```bash
mvn -pl java/jvm/jfr-troubleshooting package
```

Стартирай jar-а с CPU-time event:

```bash
java -XX:StartFlightRecording=jdk.CPUTimeSample#enabled=true,filename=java/jvm/jfr-troubleshooting/target/cpu-time.jfr,settings=profile \
  -jar java/jvm/jfr-troubleshooting/target/jfr-troubleshooting-1.0-SNAPSHOT.jar
```

Генерирай CPU workload и спри приложението.

После:

```bash
jfr view cpu-time-hot-methods java/jvm/jfr-troubleshooting/target/cpu-time.jfr
```

## Experimental означава

Не приемай API/event contract-а като толкова стабилен, колкото final feature.

При upgrade към следваща JDK версия проверявай актуалната документация.

---

# 3. JEP 520 — JFR Method Timing & Tracing

JEP:

https://openjdk.org/jeps/520

Inside Java:

https://inside.java/2025/07/25/jep520-target-jdk25/

## Защо е различно от sampling

Sampling:

```text
периодично гледаме stack-а
→ statistical picture
```

Method tracing/timing:

```text
target-ваме конкретни methods
→ instrumentation
→ получаваме per-method timing/trace data
```

Това е много силно, но трябва да се използва по-целенасочено.

---

## Method Timing

Примерна цел:

```text
CpuHotspotService::burnCpu
```

Искаме aggregate:

- invocation count;
- minimum time;
- average time;
- maximum time.

Startup пример:

```bash
java "-XX:StartFlightRecording=method-timing=bg.hristomanov.education.jfr.service.CpuHotspotService::burnCpu,dumponexit=true,filename=java/jvm/jfr-troubleshooting/target/method-timing.jfr" \
  -jar java/jvm/jfr-troubleshooting/target/jfr-troubleshooting-1.0-SNAPSHOT.jar
```

След workload и shutdown:

```bash
jfr view method-timing java/jvm/jfr-troubleshooting/target/method-timing.jfr
```

Ако shell quoting-ът на конкретната ОС третира символите различно, предай целия `-XX:StartFlightRecording=...` argument като един argument.

---

## Method Trace

Искаме не само aggregate timing, а individual trace events за targeted method.

Концептуално:

```text
jdk.MethodTrace
filter = package.Class::method
```

Пример:

```bash
java "-XX:StartFlightRecording:jdk.MethodTrace#filter=bg.hristomanov.education.jfr.service.OrderProcessingService::process,filename=java/jvm/jfr-troubleshooting/target/method-trace.jfr" \
  -jar java/jvm/jfr-troubleshooting/target/jfr-troubleshooting-1.0-SNAPSHOT.jar
```

После:

```bash
jfr view --cell-height 30 --width 200 jdk.MethodTrace java/jvm/jfr-troubleshooting/target/method-trace.jfr
```

---

# 4. Кога sampling и кога method timing/tracing

## Sampling

Предпочитам го, когато:

- още не знаем кой method е виновен;
- искаме broad profiling;
- търсим hotspots;
- искаме нисък overhead и statistical picture.

```text
не знаем root cause
→ sampling
→ намираме suspect method
```

## Method Timing / Tracing

Предпочитам го, когато:

- вече имаме конкретен suspect;
- искаме invocation-level timing;
- искаме targeted traces;
- можем да държим filter-а тесен.

```text
sampling показа suspect
→ targeted method timing/tracing
→ по-дълбоко доказателство
```

---

# 5. Защо да не trace-ваме „всичко“

Instrumentation има различна cost model от sampling.

Лош подход:

```text
trace all methods in huge application
```

По-добър подход:

```text
problem
→ narrow suspect
→ exact class/method filter
→ short recording
```

Иначе самият diagnostic механизъм може да промени performance характеристиките, които се опитваме да измерим.

---

# 6. Как това влиза в нашия learning flow

Първо минаваме basic JFR:

```text
jcmd
→ recording
→ hot-methods / contention / allocations
```

След това:

```text
suspect method
→ JEP 520 method timing/tracing
```

На Linux можем допълнително:

```text
CPU hotspot investigation
→ JEP 509 CPU-time samples
```

Така новите Java 25 capabilities се появяват в правилния момент, вместо да започваме от advanced flags без mental model.

---

# 7. Източници

- JEP 509 — https://openjdk.org/jeps/509
- JEP 518 — https://openjdk.org/jeps/518
- JEP 520 — https://openjdk.org/jeps/520
- Inside Java — What's new for JFR in JDK 25: https://inside.java/2025/06/03/new-jfr-jdk25/
- Inside Java — What's New in Java 25 in 2 Minutes: https://inside.java/2025/10/17/new-in-jdk-25-2-mins/
