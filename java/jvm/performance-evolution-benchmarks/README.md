# P1 JVM Runtime Efficiency — standalone JMH labs

> Съпътстващ изпълним benchmark модул към [Big Runtimes Make Efficient Programs](../performance-evolution/BIG-RUNTIMES-JIT-ABSTRACTIONS.md). Не е отделен notebook. Java 25 LTS.

## Защо е отделен Maven модул?

JMH е benchmark harness (контролирано изпълнение на microbenchmarks), а не JUnit. Заради warm-up, forks и generated benchmark metadata официалният [OpenJDK JMH проект](https://github.com/openjdk/jmh) препоръчва standalone проект. Тук тества **същия production-like код**, който използва и Spring Boot/JFR lab.

## README → код

| Измерваме | Къде | Functional proof |
| --- | --- | --- |
| 1, 2, 3 receiver типа на една call site; interface срещу manual dispatch | [ShapeDispatchBenchmark.java](src/main/java/bg/hristomanov/education/performance/benchmarks/ShapeDispatchBenchmark.java) | [ShapeDispatchTest.java](../performance-evolution/src/test/java/bg/hristomanov/education/performance/runtime/ShapeDispatchTest.java) |
| Fresh vs reused mutable event, GC allocations/op | [AllocationReuseBenchmark.java](src/main/java/bg/hristomanov/education/performance/benchmarks/AllocationReuseBenchmark.java) | [MutableEventTest.java](../performance-evolution/src/test/java/bg/hristomanov/education/performance/runtime/MutableEventTest.java) |
| Dot product scalar/ILP4/FMA срещу Vector API върху идентични данни | [DotProductBenchmark.java](src/main/java/bg/hristomanov/education/performance/benchmarks/DotProductBenchmark.java) | [DotProductsTest.java](../vector-api/src/test/java/bg/hristomanov/education/vector/DotProductsTest.java) |
| Annotation processing + executable JAR | [pom.xml](pom.xml) | JMH generated benchmark list |
| Reflection/MethodHandles (link vs execute) | [MethodDispatchBenchmark.java](src/main/java/bg/hristomanov/education/performance/benchmarks/MethodDispatchBenchmark.java) | [Method Handles Java25/JUnit](../method-handles/README.md) |
| FFM native strlen cached vs repeated linkage | [FfmLinkageBenchmark.java](src/main/java/bg/hristomanov/education/performance/benchmarks/FfmLinkageBenchmark.java) | [NativeCStringLengthTest.java](../native-interoperability/src/test/java/bg/hristomanov/education/ffi/NativeCStringLengthTest.java) |

## Стартиране от repository root

~~~bash
mvn -pl java/jvm/performance-evolution-benchmarks -am -DskipTests package

# Проверка, че annotation processor е генерирал harness:
java -jar java/jvm/performance-evolution-benchmarks/target/benchmarks.jar -l

java -jar java/jvm/performance-evolution-benchmarks/target/benchmarks.jar \
  ShapeDispatchBenchmark -wi 3 -i 5 -f 2 -prof gc

java -jar java/jvm/performance-evolution-benchmarks/target/benchmarks.jar \
  AllocationReuseBenchmark -wi 3 -i 5 -f 2 -prof gc

# Vector API е incubator: флагът е нужен и за benchmark JVM:
java --add-modules jdk.incubator.vector -jar java/jvm/performance-evolution-benchmarks/target/benchmarks.jar \
  DotProductBenchmark -wi 3 -i 5 -f 2 -prof gc
~~~

**Четем измерванията правилно:** JMH average ns/op измерва целия метод, включително цикъла и масива. GC profiler показва allocation rate, но не гарантира old-generation residence. Blackhole прави резултата наблюдаем, но може да има JIT-specific оптимизации. Събери JDK build, collector, Xms/Xmx, CPU, heap, forks, warm-up, samples и сравнявай повторения.

**Ограничение:** повторно използваният mutable event има друга identity/ownership семантика от fresh DTO (тестът доказва aliasing риска). Това не са автоматично взаимозаменяеми production реализации.

За HTTP latency p95/p99 и throughput използвай [JFR Spring Boot приложение](../jfr-troubleshooting/README.md) плюс **отделен** load generator с дефиниран arrival model. За сравнение на benchmark валидността виж [Benchmark Correctness](../performance-evolution/BENCHMARK-CORRECTNESS.md). Не вграждай фиксирани проценти „ускорение“ като CI assertions.

[P1 ILP / benchmark fairness](../performance-evolution/SIMD-ILP-BENCHMARKING.md) · [P2 runnable Java Vector API](../vector-api/README.md) · [P3 Elastic/Lucene case study](../performance-evolution/ELASTICSEARCH-SIMD-CASE-STUDY.md).

**Не смесвай scalar без ILP с vector+ILP, когато искаш да изолираш SIMD ефекта.** За числена коректност виж JUnit, а за production impact е нужен реален workload. Няма фиксирана очаквана скорост.

## JDK 26–28 допълнения към същия JMH harness

| Java25/JDK27/28 experiment | JMH source | Коректност и ограничения |
| --- | --- | --- |
| Record `hashCode()` и `HashMap` constructor / `putAll` | [MapCopyAndRecordHashBenchmark.java](src/main/java/bg/hristomanov/education/performance/benchmarks/MapCopyAndRecordHashBenchmark.java) | [Hashing lab](../../algorithms/03-hashing/JDK26-27-MAP-PERFORMANCE.md): HashMap, unmodifiable wrapper, LinkedHashMap |
| C2 SuperWord subword casts (16/65/4096 items) | [SubwordLoopsBenchmark.java](src/main/java/bg/hristomanov/education/performance/benchmarks/SubwordLoopsBenchmark.java) | [Vector lab](../vector-api/JDK27-SUBWORD-AUTO-VECTORIZATION.md): signedness, tail |
| Tiny confined native arena allocations (8/32/64/128 bytes) | [ConfinedArenaPoolBenchmark.java](src/main/java/bg/hristomanov/education/performance/benchmarks/ConfinedArenaPoolBenchmark.java) | [FFM lab](../native-interoperability/JDK28-CONFINED-ARENA-POOLS.md): zero-init, lifetime |

```bash
mvn -pl java/jvm/performance-evolution-benchmarks -am -DskipTests package
"$JDK25_HOME/bin/java" -jar java/jvm/performance-evolution-benchmarks/target/benchmarks.jar \
  MapCopyAndRecordHashBenchmark -wi 3 -i 5 -f 2 -prof gc
"$JDK27_HOME/bin/java" -jar java/jvm/performance-evolution-benchmarks/target/benchmarks.jar \
  MapCopyAndRecordHashBenchmark -wi 3 -i 5 -f 2 -prof gc
```

Не сравнявай различни method names като еднотипни операции. Няма фиксиран performance threshold в CI, JMH не доказва наличието на конкретни SIMD инструкции.


## Devoxx 2026 · Method Handles / Reflection / FFM linkage (ново P1/P2)

**Входен проблем:** една и съща typed operation изпълнява много заявки. Предварителната подготовка може да се измерва отделно от самата invocation. Не правим нов benchmark Maven module.

```bash
mvn -pl java/jvm/performance-evolution-benchmarks -am -DskipTests package

# Direct / cached Method.invoke / cached MethodHandle / new lookup-per-call.
java --add-modules jdk.incubator.vector \
  -jar java/jvm/performance-evolution-benchmarks/target/benchmarks.jar \
  MethodDispatchBenchmark -wi 3 -i 5 -f 2 -prof gc

# Only on a platform with supported 64-bit C strlen ABI (native access opt-in):
java --enable-native-access=ALL-UNNAMED --add-modules jdk.incubator.vector \
  -jar java/jvm/performance-evolution-benchmarks/target/benchmarks.jar \
  FfmLinkageBenchmark -wi 3 -i 5 -f 2 -prof gc
```

- [MethodDispatchBenchmark.java](src/main/java/bg/hristomanov/education/performance/benchmarks/MethodDispatchBenchmark.java): една и съща `Point.translate` операция. Методите не са взаимозаменяеми за **exception wrapping**, затова correctness договорът е в [JUnit](../method-handles/src/test/java/bg/hristomanov/education/handles/HandleInvocationLabTest.java). JEP 416 (Java 18) означава, че Core Reflection вече използва MH — не обещаваме универсално по-ниски ns/op за всяка комбинация.
- [FfmLinkageBenchmark.java](src/main/java/bg/hristomanov/education/performance/benchmarks/FfmLinkageBenchmark.java): еднакъв `strlen` workload, но разходът за native symbol/ABI linking присъства само при uncached variant. И двата варианта създават и затварят `Arena.ofConfined()` на всяка операция. Няма JMH assertion за скорост.
- **Benchmark correctness:** JVM build, CPU/ABI, GC, warmup, forks, компилаторни optimizations и `-prof gc` трябва да бъдат документирани. Външен HTTP/Spring workload, а не microbenchmark, доказва effect on p99. Виж [Benchmark Correctness](../performance-evolution/BENCHMARK-CORRECTNESS.md).

**Източници:** [оригинална Devoxx лекция](https://www.youtube.com/watch?v=4ck-T2gaMQ4) · [JEP 416](https://openjdk.org/jeps/416) · [JMH](https://github.com/openjdk/jmh).

## Източници

- [Ron Pressler — Devoxx 2026](https://www.youtube.com/watch?v=vhDwy1eCXbs).
- [JMH OpenJDK](https://github.com/openjdk/jmh).
- [Java 25 GC tuning](https://docs.oracle.com/en/java/javase/25/gctuning/).
