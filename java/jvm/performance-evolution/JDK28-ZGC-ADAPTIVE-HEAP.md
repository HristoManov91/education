# P2 — JDK 28 EA: ZGC Adaptive Heap Sizing (JEP 546)

> **Статус към 11.11.2026:** [JEP 546 е насочен към JDK 28](https://inside.java/2026/10/19/jep546-target-jdk28/). [Официалните JDK 28 EA опции](https://download.java.net/java/early_access/jdk28/docs/specs/man/java.html) описват `-XX:+ZAdaptiveHeapSizing` като **изключен по подразбиране**. Нито флагът, нито резултатите са част от Java 25 LTS baseline.  
> Лекционна мотивация: [Ask the Architects, Devoxx 2026, ~47–50 мин](https://www.youtube.com/watch?v=ewCPBgOrLAE); тогава Per-Åke Minborg говори за предложението като желано бъдещо подобрение. **Текущият target статус е проверен отделно след лекцията**.

## 1. Реалният казус

Имаме Java service в контейнер със споделени CPU/memory ресурси. `-Xmx` е максимален лимит, но настройването на реалното heap потребление има trade-off: повече headroom може да намали GC pressure, но увеличава memory footprint и contention с останалите процеси. ZGC и преди JDK 28 има ergonomic/uncommit механизми. JEP 546 цели **по-динамична настройка на heap capacity според memory/CPU pressure**, а не магически unlimited heap или универсален performance boost.

**Входен въпрос:** има ли реална разлика в RSS, CPU usage и p95/p99 при същия application workload и еднакви ресурси, когато включим adaptive sizing?

## 2. Точна матрица на версията

| JVM | Избор | Изпълнимо тук |
| --- | --- | --- |
| JDK 25 LTS | ZGC съществува, но JEP 546 не е негов API/feature | съществуващите [Java25 GC/benchmark labs](README.md) |
| JDK 27 | отделни ZGC improvements; не приемаме `ZAdaptiveHeapSizing` | version notes, не опитвай без проверка на flags |
| JDK 28 EA, build с JEP 546 | `-XX:+UseZGC -XX:+ZAdaptiveHeapSizing`, default е OFF | **опционален manual script**, не Java25 CI |

**Няма стандартно „ZGC вече е default collector“ правило от тази лекция.** За JDK27 G1 default смяната виж [existing GC upgrade](JDK26-27-GC-UPGRADE.md).

## 3. Manual reproducible control: same binary, same JDK, only one GC flag

Първо build-ваш съществуващия workload с Java 25:

```bash
mvn -pl java/jvm/performance-evolution -am verify
bash java/jvm/performance-evolution/scripts/compare-zgc-28-ea.sh \
  "$JDK28_HOME/bin/java"
```

[compare-zgc-28-ea.sh](scripts/compare-zgc-28-ea.sh) отказва грешна major версия и неподдържан `ZAdaptiveHeapSizing` build, използва **един и същ .class файл, JDK 28 EA и входни данни** в два отделни процеса. Разликата е единствено `-XX:-ZAdaptiveHeapSizing` срещу `-XX:+ZAdaptiveHeapSizing`. И при двата: `-XX:+UseZGC`, `-Xms64m`, `-Xmx256m`, `ActiveProcessorCount=2`; запазва GC logs и изисква същия бизнес `CHECKSUM`.

**Това е compatibility/correctness smoke, НЕ performance benchmark.** Субсекунден синтетичен workload не показва самостоятелно как adaptive sizing реагира на memory contention. Няма генерирани p99/RSS резултати, защото **не е изпълняван JDK 28 EA benchmark**. Ръчното изпълнение изисква подходящ EA build, OS permissions и стабилна тестова среда.

### Production-like benchmark protocol (отделен експеримент)

1. Една и съща application версия и JDK28 EA build/flags/CPU, memory cgroups, heap min/max и input traffic. Контролно „adaptive OFF“, после „ON“, редувани повторения.
2. Warmup → steady load, отделен load generator и реален arrival model. Измери completed throughput и response latency p50/p95/p99, `RSS`, committed heap, CPU throttling, GC pauses и time in GC; фиксирай JVM uptime.
3. Симулирай **външна** memory contention само в disposable lab с ясни ресурси, не production. Провери OOMKilled/OOME и дали adaptive behavior намалява disruption без да влошава p99.
4. Върни `-XX:-ZAdaptiveHeapSizing` и контролен rollback image след теста. Няма guarantee за по-ниска RAM/CPU при всеки workload.

## 4. Decision contract

| Кога да | Кога НЕ | Печалба | Цена / риск | По-проста алтернатива | Доказателство |
| --- | --- | --- | --- | --- | --- |
| JDK28 EA, multiple colocated processes, measured heap/RSS/CPU trade-off | JDK25/27 production, без доказан bottleneck, fixed Xms=Xmx | потенциално better neighbor memory behavior | EA version dependence, tuning noise, GC/workload variation | Standard ZGC ergonomics + measured heap/cgroups | [manual script](scripts/compare-zgc-28-ea.sh) + JFR/OS metrics |

**Изход:** научихме как да изпитаме конкретен GC flag без да смесваме JDK 25 с 28 и без да разчитаме на прогнози от конференция.

## Източници

- [Ask the Architects — Per-Åke Minborg](https://www.youtube.com/watch?v=ewCPBgOrLAE), supplied SRT, ~47–50 мин.
- [JEP 546](https://openjdk.org/jeps/546).
- [Inside Java — JEP 546 targeted to JDK 28 (19 October 2026)](https://inside.java/2026/10/19/jep546-target-jdk28/).
- [Official OpenJDK 28 EA `java` options](https://download.java.net/java/early_access/jdk28/docs/specs/man/java.html).
- [ZGC tunings and uncommit fundamentals](https://docs.oracle.com/en/java/javase/25/gctuning/z-garbage-collector.html).
- [Benchmark correctness protocol](BENCHMARK-CORRECTNESS.md).
