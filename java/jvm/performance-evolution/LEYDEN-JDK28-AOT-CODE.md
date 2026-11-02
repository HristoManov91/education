# P2 · Project Leyden, JDK 28 — AOT Code Compilation (JEP 544)

> **Поглед към JDK 28 EA**, без нов Maven модул и без дублиране на [Java 25 Leyden lab](README.md).  
> **Оригинал:** [Nicolai Parlog — Java Next, Devoxx 2026, 10:25–24:23](https://youtu.be/ufhs9THbYZI?t=625).  
> **Източник за текущия статус:** [JEP 544](https://openjdk.org/jeps/544) — targeted/integrated in JDK 28 EA към 2026-11-02; JDK 28 **не е GA**.
>
> Съдържанието идва от предоставения SRT, а ограниченията за съвместимост са сверени с JEP 544; не възпроизвеждаме числови ускорения от различни приложения.

## Проблем: application ready ≠ peak throughput

Дългоживеещ Spring Boot service се стартира след rollout. Виждаме first HTTP response след 2 s, но първите стотици заявки са по-бавни. Докато приложението е в steady state, throughput се подобрява.

Това са **две различни** фази:

- **Startup:** launch → първа полезна операция/успешен HTTP response (зависи от дефиницията за readiness).
- **Warm-up:** първа полезна операция → стабилизиране на изпълнението и JIT optimizations; няма едно универсално „peak reached“ събитие.

JDK 25 Leyden вече има AOT класове/линкване и предварително събрани профили. **JDK 28 JEP 544 разширява този модел и с оптимизиран native machine code**, подготвен в training run, за да е готов при production startup. Това не замества Java Virtual Machine и JIT.

## README → код

| Концепция | Реален код | Тест / експеримент |
| --- | --- | --- |
| JDK 25 AOT cache workflow | [StartupWorkload.java](src/main/java/bg/hristomanov/education/performance/StartupWorkload.java) | [PerformanceExperimentsTest.java](src/test/java/bg/hristomanov/education/performance/PerformanceExperimentsTest.java) |
| Повторни отделни JVM стартирания | [AotProcessComparison.java](src/main/java/bg/hristomanov/education/performance/AotProcessComparison.java) | [BenchmarkStatistics.java](src/main/java/bg/hristomanov/education/performance/BenchmarkStatistics.java) |
| JIT inlining/бързо деградиране при нов профил | [Big Runtimes P1](BIG-RUNTIMES-JIT-ABSTRACTIONS.md) | [ShapeDispatchBenchmark.java](../performance-evolution-benchmarks/src/main/java/bg/hristomanov/education/performance/benchmarks/ShapeDispatchBenchmark.java) |
| Production-like JVM profiling | [JFR Troubleshooting](../jfr-troubleshooting/README.md) | \`jcmd\`, \`jfr\`, JMC |

## Версии и еволюция — без смесване

| JDK | Какво има в Leyden | С какво помага |
| --- | --- | --- |
| 24 / JEP 483 | AOT Class Loading & Linking | Част от class load/link работата става предварително |
| 25 / JEP 514, 515 | По-удобен AOT workflow и cached execution profiles | По-бърз startup и потенциално по-кратък profiling warm-up |
| 26 / JEP 516 | GC-agnostic cached object representation | AOT class/object archive не налага старото 1:1 GC ограничение |
| 28 EA / **JEP 544** | AOT Code Compilation | Оптимизиран native code може да е наличен при първите заявки |

**Критична разлика:** въпреки че **cached objects** са GC-agnostic след JEP 516, **AOT native code** от JEP 544 включва GC-specific barriers. За да се използва AOT кодът, production и training трябва да имат **един и същ collector**. При mismatch JVM може да използва другите части на cache-а, но не и предварително компилирания код. Не прави извод „JDK 26 премахна всяко GC изискване завинаги“.

## Как работи: 3 фази

~~~text
Representative training (application runs)
    ↓ records class/profile behavior; selected hot methods compile
AOT cache assembly
    ↓ persists linked classes, profiles and optimized native code
Production process
    ├─ matched CPU+GC+runtime → can use AOT code immediately
    ├─ different workload → HotSpot may deoptimize and re-JIT
    └─ mismatch CPU features/GC → may ignore native code portion
                               while preserving other AOT data
~~~

**Training → assembly → production** може да бъде управлявано от едностъпковия \`-XX:AOTCacheOutput\` флаг. Едностъпковият workflow е наличен още в JDK 25; новото в JDK 28 е **какво допълнително се кешира**.

## Примерен JDK 28 EA smoke test (ръчен, не Java 25 CI)

Първо компилирай детерминистичния Java 25 workload:

~~~bash
mvn -pl java/jvm/performance-evolution package
cd java/jvm/performance-evolution

# JVM: JDK 28 Early Access, не Java 25
java -XX:AOTCacheOutput=target/java28-workload.aot \
    -cp target/classes bg.hristomanov.education.performance.StartupWorkload 25000

# JVM: същата съвместима JDK 28 EA версия / platform / CPU / collector
java -Xlog:aot=info -XX:AOTCache=target/java28-workload.aot \
    -cp target/classes bg.hristomanov.education.performance.StartupWorkload 25000
~~~

**Задължителни ограничения:** този synthetic workload е прекалено кратък за надежден warm-up benchmark и не гарантира, че конкретни методи ще бъдат AOT-compiled. \`-Xlog:aot=info\` помага за диагностика, но **не е само по себе си доказателство за измерено ускорение или изпълнена AOT функция**. За целта са нужни достатъчно training requests и profile/compilation logs, сравнение с контролен run без cache, със същите условия.

### Compatibility contract

1. **JDK/cache/app:** генерирай от същата приложна версия, classpath, JDK build, runtime flags и съвместима среда.
2. **CPU:** native code не е cross-compiled. Съвпадат CPU architecture и нужните instruction-set features (например AVX).
3. **GC:** native code съдържа collector-specific barriers. Дори AOT object archive да е portable между collectors, native code изисква същия GC.
4. **Workload:** training трябва да активира важните hot paths; иначе JIT все пак довършва оптимизацията при работа.
5. **Fallback:** при неподходящ cache/feature subset очаквай fallback/частично използване, не correctness промяна. Разследвай реалните диагностични logs.
6. **Security/operations:** кешът е build/deployment artifact, не го използвай от непроверени източници; rebuild при upgrade.

## Кога, кога НЕ и trade-offs

| Подход | Подходящ | Не е подходящ | Печалба ↔ цена |
| --- | --- | --- | --- |
| JDK 25 AOT cache | Java 25 production с измерен cold startup/warm-up проблем | Няма наблюдаван startup симптом | Cached metadata/profiles ↔ training and cache lifecycle |
| JDK 28 JEP 544 native AOT code | Experimental JDK 28 deployment с подобни машини/GC | Java 25 runtime, хетерогенен CPU fleet без compatibility стратегия | По-кратък warm-up ↔ CPU/GC coupling, invalidation |
| Само JIT | Променливи workloads, продължително steady state | Ако cold start е критичен bottleneck | Runtime specialization ↔ warm-up CPU time |
| GraalVM Native Image | Нисък startup при closed-world compatible deployment | Динамична reflection/classloading среда без migration strategy | Standalone native ↔ build/config complexity |

**По-проста алтернатива:** първо измери JDK 25 + JFR при реални първи заявки. Добави JDK 28 trial едва ако има релевантна нужда и контролирани training/production условия.

## Практическа схема за честно измерване

- Baseline: JDK 28 без cache; trained run: същия artifact/JDK/CPU/GC с AOT code.
- 7+ независими fork процеса за контрол и лечение, редувано, с еднакъв ready/HTTP endpoint contract.
- Метрики: process-to-first successful response, 1–100–1000 request warm-up curve, steady throughput, p95/p99, CPU/RSS/GC.
- Изрично отбележи environment и run artifacts; не сравнявай само \`main()\` duration.
- Canary и rollback: ако mismatch на CPU/GC обезсили AOT native code, това не бива да се превръща в мълчалива performance регресия.

**Постоянният принцип:** Leyden измества работа извън критичния път на startup, но не променя правилата за коректност. Production ефектът се доказва с workload, не с процент от keynote.

## Оригинални източници

- [Nicolai Parlog — Java Next, Devoxx 2026](https://www.youtube.com/watch?v=ufhs9THbYZI), [слайдове](https://slides.nipafx.dev/java-next/2026-10-02-jcp/).
- [JEP 544 — Ahead-of-Time Code Compilation](https://openjdk.org/jeps/544).
- [JEP 483 — AOT Class Loading & Linking](https://openjdk.org/jeps/483).
- [JEP 514 — AOT Command-Line Ergonomics](https://openjdk.org/jeps/514).
- [JEP 515 — AOT Method Profiling](https://openjdk.org/jeps/515).
- [JEP 516 — AOT Object Caching with Any GC](https://openjdk.org/jeps/516).
- [OpenJDK Leyden repository](https://github.com/openjdk/leyden).
- [Existing Java 25 Leyden lab](README.md) · [Benchmark Correctness](BENCHMARK-CORRECTNESS.md).
