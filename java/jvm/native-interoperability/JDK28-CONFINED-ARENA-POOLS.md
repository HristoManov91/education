# P2 — JDK 28 EA: faster `Arena.ofConfined()` с native pooling

> Лекция: [Per-Åke Minborg — Yearly Java Performance Update 2026](https://www.youtube.com/watch?v=rNOzf12RORI), ~35–37 мин (предоставен SRT). Точният механизъм е проверен в [Inside Java — 5 октомври](https://inside.java/2026/10/05/confined-pools/) и [23 октомври 2026](https://inside.java/2026/10/23/confined-pools/). **JDK 28 е Early Access**, не основната Java 25 LTS версия.

## 1. Реален проблем: native scratch memory за всеки малък FFM call

Нашата [FFM лаборатория](README.md) използва `Arena.ofConfined()` за native calls и `MemorySegment`. Кратък C call може да има нужда от само 8 bytes scratch buffer, но създаването, native allocation и cleanup може да струват повече от самата работа.

**Naive:** държим една вечна shared arena, за да избегнем `malloc/free`. Това нарушава ownership/lifetime и може да превърне кратък memory region в leak.

**Good:** запазваме `try (Arena arena = Arena.ofConfined())` и коректния scoped lifetime. JDK 28 променя **имплементацията** на малките allocations, не API contract-а.

## 2. Как JDK 28 реализира optimization (source facts vs experiment)

Според публикацията на Inside Java JDK 28 използва **lazy, confined native-memory pools**: всеки platform/carrier thread може да кешира по подразбиране до **четири 64-byte** pools. Пул се взема при първа подходяща allocation в arena; nested arenas притежават независими pools. Ако размерът или alignment не пасват, се използва обичайният allocator.

При `Arena.close()`: segment lifetime приключва незабавно, използваната част се занулява и pool може да се върне в cache. При Virtual Threads pool не е привързан за всяка виртуална нишка: временно се заема от carrier thread, после arena поема ownership и може да мигрира.

**Граници:** не всяка 64-byte allocation се обслужва от pool (alignment/capacity); 128 bytes вече е очевиден fallback кандидат. Pooled native memory може да остане resident до по-късно, **close на arena не означава непременно native `free()`**. Zero-init и after-close memory safety остават задължителни.

## 3. README → код → тест → JMH

| Договор | Java25 код | Доказателство |
| --- | --- | --- |
| Confined arena allocation/write/read | [ConfinedArenaAllocationLab.java](src/main/java/bg/hristomanov/education/ffi/ConfinedArenaAllocationLab.java) | [ConfinedArenaAllocationLabTest.java](src/test/java/bg/hristomanov/education/ffi/ConfinedArenaAllocationLabTest.java) |
| Fresh memory is zero и memory invalid after close | същият `Arena` код | JUnit с 1/5/8/20/32/64/128-byte граници и negative after-close |
| Small native allocation throughput | [ConfinedArenaPoolBenchmark.java](../performance-evolution-benchmarks/src/main/java/bg/hristomanov/education/performance/benchmarks/ConfinedArenaPoolBenchmark.java) | JMH, **не** performance JUnit threshold |

```bash
# Runnable Java 25 LTS: functional safety
mvn -pl java/jvm/native-interoperability -am test

# JMH harness е общ, без нов Maven модул:
mvn -pl java/jvm/performance-evolution-benchmarks -am -DskipTests package

# С JDK 25 и 27 за reference baseline:
"$JDK25_HOME/bin/java" -jar java/jvm/performance-evolution-benchmarks/target/benchmarks.jar \
  ConfinedArenaPoolBenchmark -wi 3 -i 5 -f 2 -prof gc

# **Optional JDK 28 Early Access**, само ако JDK 28 EA binary е инсталиран:
"$JDK28_EA_HOME/bin/java" -jar java/jvm/performance-evolution-benchmarks/target/benchmarks.jar \
  ConfinedArenaPoolBenchmark -wi 3 -i 5 -f 2 -prof gc
```

Тестовете доказват **API semantics**, не JDK28 speedup. Бенчмаркът получава променяща се byte value за всеки invocation, за да не премахва лесно компилаторът observable result. Сравнявай еднакви sizes 8/32/64/128 между JDK25, JDK27 и JDK28 EA, а не 8-byte на една версия срещу 128-byte на друга. За отчитане на native memory използвай OS RSS/NMT, не `-prof gc` като единствена мярка.

## 4. Decision contract

| Кога | Кога НЕ | Печалба | Загуба / риск | По-проста алтернатива | Proof |
| --- | --- | --- | --- | --- | --- |
| Много кратки FFM/native calls с малки confined allocations; measured native allocation bottleneck | няма FFM или scratch region не е hotspot; large/aligned allocations извън pool fast path | potential latency reduction без source change | native retention в cache, JDK28 EA compatibility, microbench != service speedup | остави съществуващия scoped `Arena.ofConfined()`; не пишеш собствен pool | JUnit [contract](src/test/java/bg/hristomanov/education/ffi/ConfinedArenaAllocationLabTest.java) + JMH |

**Не копирай JDK 28 EА „6–18×“ от статията като наше измерване.** Там има специфични 5/20 byte, platform thread, CPU/OS резултати. При нашия workload и Virtual Threads числата може да са различни.

## 5. Финален mental model / review

**Преди:** кратък native region може да плаща цената на allocator всеки път. **След:** JDK 28 има малък lazy pool, но същите ownership и lifetime правила. **Не печелим:** право да четем затворен segment, право да споделяме confined arena между нишки или гаранция, че RSS пада при close.

Checklist: [ ] нужни ли са малки FFM allocations; [ ] explicit owner/close; [ ] zero-init + wrong-thread + after-close; [ ] native memory/RSS, не само heap; [ ] size/alignment matrix; [ ] JDK28 EA risk и fallback; [ ] реален workload и benchmark methods.

## Оригинални източници

- [Devoxx 2026 — Per-Åke Minborg](https://www.youtube.com/watch?v=rNOzf12RORI).
- [Inside Java — Making Arena.ofConfined() Even Cheaper in JDK 28](https://inside.java/2026/10/05/confined-pools/).
- [Inside Java — 6–18× Small Off-Heap Allocations (source benchmark)](https://inside.java/2026/10/23/confined-pools/).
- [JDK-8385697](https://bugs.openjdk.org/browse/JDK-8385697), [OpenJDK PR #31365](https://github.com/openjdk/jdk/pull/31365).
- [Java 25 Arena specification](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/foreign/Arena.html).
