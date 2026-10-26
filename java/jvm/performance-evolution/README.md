# P1 · Modern JVM Performance / Project Leyden & Compact Object Headers

> **Source:** [Modern Java in Practice, Devoxx 2026 — performance](https://youtu.be/zC2tbq7S5UE?t=2220), Nicolai Parlog and Falk Sippach.
>
> Java 25 LTS code and **repeatable manual experiment**. No unmeasured performance claims. This complements, rather than duplicates, [our JFR troubleshooting lab](../jfr-troubleshooting/README.md).

## Реален проблем: upgrade на JDK не означава гарантирано по-бързо приложение

Мигрираме Java 21 → 25 и виждаме много малки objects, студен startup на Spring Boot workers и непостоянен response time. Чуваме:
- „JDK 25 е с по-малки object headers, значи throughput се увеличава 20%.“
- „AOT cache намалява startup 50%, просто включи флаг.“
- „JFR отчита време на бавен метод, значи е CPU-bound.“
  
И трите твърдения могат да са **неверни за конкретния workload**, защото смесват различни performance характеристики.

**Цел:** да разберем какво *може* да се подобри, с кой JVM feature, как да го измерим честно и какво от измерването не следва.

## 1. Performance dimensions — фундаментален mental model

| Метрика | Какво мери | Грешно обобщение |
| --- | --- | --- |
| **Startup** | От launch на JVM/процеса до service readiness | Time inside main() ≠ целия process startup |
| **Warm-up** | Времето, през което JIT/кешовете достигат стабилен режим | Startup speed ≠ steady throughput |
| **Throughput** | Completed operations / time at steady load | Спестена памет ≠ непременно повече req/s |
| **Latency p95/p99** | Времето на индивидуални заявки, особено tail | Average ≠ user-visible tail latency |
| **GC pause** | Stop-the-world/GC effects (зависи от collector и фаза) | Малък pause ≠ по-нисък CPU overhead |
| **RSS / heap footprint** | Native + managed memory, resident pages, heap live data | Object header reduction ≠ еднаква RSS разлика |
| **CPU time** | Реално консумирани CPU cycles | Wall-clock waiting on DB ≠ CPU hotspot |

**Изборът на оптимизация следва симптома.** Ако 90% от request е JDBC waiting, малък GC tuning ефект може да е без значение за клиентската latency.

## 2. Compact Object Headers (JEP 519)

На JDK 25 функцията е **product feature, но не е включена по подразбиране**. Активирай с `-XX:+UseCompactObjectHeaders`.

Тя компресира обичайния Java object header до 64 bits спрямо 96/128 bits в стандартния layout. Най-полезна е при милиони **малки обекти**, където header-ът е значим дял от размера. Но може да има CPU cost за unpacking, различно GC поведение и други trade-offs.

```bash
java -Xmx2g -Xlog:gc*:file=target/gc-normal.log:time,uptime,level,tags \
  -cp target/classes bg.hristomanov.education.performance.StartupWorkload 25000

java -Xmx2g -XX:+UseCompactObjectHeaders \
  -Xlog:gc*:file=target/gc-compact.log:time,uptime,level,tags \
  -cp target/classes bg.hristomanov.education.performance.StartupWorkload 25000
```

**JDK 27:** Compact Object Headers вече са default enabled. Използвай `-XX:-UseCompactObjectHeaders` за контролирано сравнение, докато този flag още се поддържа; не приемай стария layout за вечен. Версията се проверява с `java -version` и текущите release notes.

**Не използвай горния малък demo като memory benchmark**: нужни са достатъчен heap live set, identical object population, collector/heap flags, jcmd/JFR/GC data и repeated runs. За exact object layout използвай специализиран Java Object Layout tool/JOL, ако действително е необходимо.

## 3. Project Leyden — какво е AOT Cache?

AOT не е „Java се превръща изцяло в native binary“. За JDK 25 JEP 483/514/515 оптимизира стартирането и warm-up чрез **training run → cache creation → subsequent run uses cache**.

```text
JVM training launch (representative class loading / method execution)
    └─ gather class/linking and profiling data
       └─ create .aot cache
          └─ Java production-like JVM launch reads compatible cache
             ├─ potential faster startup
             └─ potential shorter JIT warm-up
```

Едностъпковият JDK 25 training флаг `-XX:AOTCacheOutput=...` организира training+assembly. В production-like run се подава `-XX:AOTCache=...`.

### Runnable lab: build and train

От root на repository:

```bash
mvn -pl java/jvm/performance-evolution package
cd java/jvm/performance-evolution

# JDK 25+ Java executable; training + cache creation:
java -XX:AOTCacheOutput=target/workload.aot \
    -cp target/classes bg.hristomanov.education.performance.StartupWorkload 25000

# Manual cached run:
java -XX:AOTCache=target/workload.aot \
    -cp target/classes bg.hristomanov.education.performance.StartupWorkload 25000

# Automated child JVM comparison, within one environment:
java -cp target/classes \
    bg.hristomanov.education.performance.AotProcessComparison target/workload.aot
```

**Изпълнимите файлове:**
- [StartupWorkload.java](src/main/java/bg/hristomanov/education/performance/StartupWorkload.java) — стабилно повтаряемо изчисление;
- [AotProcessComparison.java](src/main/java/bg/hristomanov/education/performance/AotProcessComparison.java) — отделни child JVM launches, warm-ups, alternating cached/uncached and medians;
- [BenchmarkStatistics.java](src/main/java/bg/hristomanov/education/performance/BenchmarkStatistics.java) — median и проверки;
- [PerformanceExperimentsTest.java](src/test/java/bg/hristomanov/education/performance/PerformanceExperimentsTest.java) — deterministic/checksum/statistics тестове.

**Критично:** кодът измерва **wall-clock до приключване на subprocess-а**, а не само production service readiness, p99, GC или CPU throughput. Този synthetic workload може да е прекалено кратък и да не покаже видим gain. **Няма hardcoded "expected speedup".**

### Cache validity constraints

AOT cache се създава за съвместима комбинация:
- exact JDK release/distribution и runtime flags;
- application classpath/modules и code artifacts;
- OS/architecture и class-loading patterns;
- JDK/tool agents могат да променят loaded bytecode и да намалят/изключат съвместимостта.

При build/upgrade/deploy трябва да регенерираш cache. AOT cache не е magic data-independent optimization — нужна е **представителна training run**, съизмерима с production workload-а. При неправилен/несъвместим cache JVM може да откаже употребата му; провери runtime diagnostic logs.

### Startup ≠ warm-up

В реално Spring Boot приложение измери отделно:
1. OS process start → port bound → readiness, включително DB migrations;
2. първите N заявки при студен JVM;
3. throughput и latency при стабилен load;
4. heap/RSS/GC pauses и CPU across control/test runs.

Не смесвай Leyden AOT caching с GraalVM native-image. Различни deployment models, compilation trade-offs и operational constraints.

## 4. ZGC / G1 / Virtual Thread pinning и защо не е едно число

Java 25/26/27 включват GC и runtime оптимизации, които може да помогнат на различни workloads.

**Бавен p99:** сравни G1 и ZGC само при съпоставим heap/CPU/latency SLO, като измериш GC pauses **и общ CPU overhead**. Лекторът показва примери от конкретни приложения, не универсален benchmark.

**Virtual Threads:** при Java 24+ synchronized pinning е намалено (JEP 491), но native callbacks, monitor/FFM/JNI boundaries и downstream scarce resources остават важни. Нашият [Project Loom lab](../../concurrency/project-loom/README.md) покрива thread lifecycle, scoping и limitation-тата.

**Vector API / FFM:** могат да бъдат по-полезни от GC tuning за data-heavy или native interaction workflows. Не ги внедрявай без профилиран hotspot и compatibility review.

## 5. Как използваме JFR

[Съществуващият JFR модул](../jfr-troubleshooting/README.md) показва:
- `jcmd` recording, `jfr view` и JMC;
- allocations/GC, CPU, locks, file I/O;
- Java 25 Method Timing & Tracing, experimental CPU time sampling.

Правилен процес:

```text
Observed symptom → baseline recording/metrics
→ hypothesis (allocation count, JIT warm-up, GC, thread blocking, DB wait)
→ one JVM flag/code change
→ repeated comparable measurement
→ regression test / rollback path
```

Добавеният [Java 27 JFR data redaction note](../jfr-troubleshooting/JDK-27-JFR-REDACTION.md) описва как recording може да скрие чувствителни JVM arguments/system property values, но не гарантира, че всички custom events са автоматично safe.

## 6. Benchmark protocol (production-like)

- Използвай **същия JDK build**, OS/CPU architecture, resource limits, GC, heap sizes и имидж.
- Сменяй **само един JVM фактор** наведнъж: например Compact Headers ON/OFF, AOT cache ON/OFF.
- Прави warm-ups и 7+ trials, редувай control/treatment, отчитай median и разсейване.
- За throughput използвай JMH и representative request/data load; **не** единичен loop с `System.nanoTime()`.
- Събирай resource metrics: CPU, allocation rate, heap occupancy, RSS, GC pause distribution, JFR hotspots.
- Не приемай резултат, ако бизнес инвариантите са променени, app е с различен build или load generator не е стабилен.

## 7. Interview-style exercises

1. Как обясняваш разликата между старт/ready/warm-up/steady throughput?
2. Защо AOT training трябва да има representative runtime?
3. Защо Compact Headers могат да понижат heap, без да увеличат throughput?
4. Кога ZGC е оправдан, ако p99 SLA е основният приоритет?
5. Каква е разликата между wall-clock, JFR execution sampling и CPU-time profiling?
6. Как ще гарантираш, че експериментът не променя опасно production behavior?

**Mental model:** JVM optimization е **хипотеза, която измерваме**, не копиран процент от чужд keynote.

## Original sources

- [Devoxx 2026 — Modern Java in Practice](https://youtu.be/zC2tbq7S5UE?t=2220)
- [JEP 519 — Compact Object Headers](https://openjdk.org/jeps/519)
- [Java 25 GC Tuning — Compact Headers](https://docs.oracle.com/en/java/javase/25/gctuning/other-considerations.html)
- [Oracle JDK 27 Release Notes](https://www.oracle.com/java/technologies/javase/27-relnote-issues.html)
- [JEP 483 — Ahead-of-Time Class Loading & Linking](https://openjdk.org/jeps/483)
- [JEP 514 — AOT Command-Line Ergonomics](https://openjdk.org/jeps/514)
- [JEP 515 — Ahead-of-Time Method Profiling](https://openjdk.org/jeps/515)
- [Inside Java — Run AOT Cache](https://inside.java/2026/01/09/run-aot-cache/)
- [JEP 491 — Synchronize Virtual Threads without Pinning](https://openjdk.org/jeps/491)
