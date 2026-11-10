# P1 · SIMD Benchmark Fairness: ILP, JIT, FMA и причинно заключение

> **Надграждане на [Benchmark Correctness](BENCHMARK-CORRECTNESS.md), не дублиращ курс.** Източник: [Piotr Przybył — *SIMD for Java*, Devoxx Belgium 2026](https://www.youtube.com/watch?v=adFS-_sCTQ4), особено ~15:00–17:00. Използван е автоматичният SRT транскрипт; числата от демото не са наши измервания.

## 1. Реален проблем: „Vector API ни ускорява 8 пъти!“

В PR за CPU-heavy dot product авторът сравнява scalar цикъл с **един** акумулатор срещу SIMD implementation с **четири независими акумулатора**. Изглежда, че SIMD е единствената причина за ускорението. Но експериментът променя поне две независими неща: instruction width и instruction-level parallelism (ILP). В Devoxx демонстрацията лекторът признава този bias и добавя scalar ILP baseline; разликата намалява. Това е методологична демонстрация, не универсален speedup.

**Цел:** да правим честно сравнение при еквивалентна изчислителна работа, numerical contract и performance metric, вместо да приписваме всички промени на новата технология.

## 2. Mental model — какво сравняваме

- **Scalar**: едно `sum += a[i] * b[i]`; JIT може да оптимизира/auto-vectorize при определени условия.
- **ILP**: четири независими акумулатора прекъсват зависимостта на последователното натрупване; **това не са 4 threads**.
- **SIMD**: една CPU операция за множество еднотипни lanes; `VectorSpecies` и хардуерът влияят на изпълнението.
- **FMA**: една fused multiply-add операция с едно закръгляване, различна numerical семантика от два отделни floating-point оператора; не винаги по-бързо на всеки CPU.
- **Memory-bound**: ако лимитът е memory bandwidth/cache miss, по-бърз arithmetic kernel не означава същото ускорение на заявката.

### README → код / доказателство

| Сравнение | Код | Доказателство |
| --- | --- | --- |
| Scalar baseline, scalar ILP4, scalar FMA4 | [ScalarDotProducts](../vector-api/src/main/java/bg/hristomanov/education/vector/ScalarDotProducts.java) | [JUnit tests](../vector-api/src/test/java/bg/hristomanov/education/vector/DotProductsTest.java) |
| SIMD ILP4 + mul/add, SIMD ILP4 + FMA, small-input policy | [VectorDotProducts](../vector-api/src/main/java/bg/hristomanov/education/vector/VectorDotProducts.java) | [JUnit tests](../vector-api/src/test/java/bg/hristomanov/education/vector/DotProductsTest.java) |
| Същите данни и размери за всеки вариант | [DotProductBenchmark](../performance-evolution-benchmarks/src/main/java/bg/hristomanov/education/performance/benchmarks/DotProductBenchmark.java) | [JMH run guide](../performance-evolution-benchmarks/README.md) |

## 3. Шест контроли в нашия JMH

| Benchmark | Изолираният въпрос | Важно ограничение |
| --- | --- | --- |
| `scalar` | Какво прави простият цикъл? | Не е достатъчен ILP baseline. |
| `scalarIlp4` | Какво променят 4 независими accumulators? | JIT може да auto-vectorize и двата варианта. |
| `scalarFma4` | Какво променя scalar FMA? | Редът на закръгляване се променя. |
| `vectorMulAdd` | Какво носи explicit SIMD спрямо scalar ILP4? | Редукцията е в друг ред. |
| `vectorFma` | Какво прави Vector API FMA? | CPU/ISA-dependent. |
| `vectorSmallInputFallback` | Има ли полза scalar path за малки входове? | Прагът е учебен, не auto-tuned. |

## 4. Експериментален протокол

1. **Correctness първо.** Същите входни масиви и размери, double-precision reference, допустим floating-point толеранс. Няма bitwise equality contract между различните accumulation orders.
2. **JMH, не `System.nanoTime()` около един loop.** Зададените учебни `warmup=3`, `measurement=5`, `fork=2` са начало, не доказателство за статистическа значимост. Методите връщат резултат, който harness обработва.
3. **Идентични условия:** CPU model/architecture, JDK vendor/build, JVM flags, power/turbo settings, load на машината, размери, heap, GC, dataset. Запиши run metadata и вариация, не само средно ускорение.
4. **Размери:** 16, 256, 1024, 8192 dimensions. Има ли достатъчно работа за векторните инструкции? Как работят cache и memory bandwidth?
5. **JIT:** не твърди, че scalar цикълът е останал scalar след JIT, без профил/assembly проверка; C2 може да векторизира. FMA може да се държи различно на ARM и x86.
6. **Крайният bottleneck:** потвърди с JFR/CPU профил, успешни операции/s и end-to-end latency, че microbenchmark действително е релевантен.

**Negative control:** benchmark „vector+ILP4 срещу scalar“ е валиден за *сравнение на реализациите*, но **не** изолира причинно ефекта на SIMD. За този въпрос сравни и scalar ILP4; по възможност потвърди emitted instructions.

## 5. Decision contract

| Въпрос | Отговор |
| --- | --- |
| Какъв проблем решава? | Performance PR, базиран на подвеждащ или непълен baseline, може да влоши production. |
| Кога да? | При speedup claim за SIMD, JIT, FMA или native kernel. |
| Кога НЕ? | Не използвай microbenchmark като единствено доказателство за HTTP p99, DB performance или search latency. |
| Печалби | Коректна причинна интерпретация; по-малко погрешни оптимизации. |
| Компромиси | Повече контролни варианти, hardware runs и време за измерване. |
| По-просто решение | Преди performance rewrite използвай JFR, JMH за hotspot и оптимизирай алгоритъма/data layout. |
| Как доказваме | JUnit за correctness + JMH за microbenchmark + отделен realistic system-level test. |

### Senior code review

- Сравняват ли се **еквивалентни** изчисления и error budget?
- Отделени ли са scalar ILP, SIMD, FMA, data-layout и JIT effects?
- Измерени ли са правилните ресурси и реалният bottleneck?
- Осигурен ли е fallback за неподдържан runtime и какво струва maintenance?

**Ако имаме профилиран CPU hotspot, избери контролиран benchmark и докажи подобрението. Ако не — остави простия път и оптимизирай реалния bottleneck.**

## Оригинални източници

1. [Devoxx 2026 — Piotr Przybył](https://www.youtube.com/watch?v=adFS-_sCTQ4).
2. [OpenJDK JMH](https://github.com/openjdk/jmh).
3. [Lucene VectorUtil — configurable FMA](https://github.com/apache/lucene/blob/main/lucene/core/src/java/org/apache/lucene/util/VectorUtil.java).
4. [JEP 508 — Vector API (Tenth Incubator)](https://openjdk.org/jeps/508).
5. [Benchmark Correctness](BENCHMARK-CORRECTNESS.md).
