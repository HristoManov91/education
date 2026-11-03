# P2 · Java SIMD — Vector API, ILP и реално измерване

> **Java 25 LTS · изпълнима лаборатория · 20–30 минути за основата + JMH експеримент**  
> Източник: [Piotr Przybył, *SIMD for Java: how Elasticsearch already benefits from the Panama Vector API*, Devoxx Belgium 2026](https://www.youtube.com/watch?v=adFS-_sCTQ4). Използван е предоставеният автоматичен SRT транскрипт, който може да има грешки в техническите думи.  
> **Статус към 2026-11-03:** Vector API продължава да бъде **incubator** (`jdk.incubator.vector`), включително в JDK 27 (JEP 537). FFM API е **стандартизиран от JDK 22** — различни API-та с различна зрелост.

## 1. Реалният казус — защо dot product става CPU bottleneck

Имаме in-memory similarity search: една заявка трябва да сравни embedding с много кандидат-вектори. Един dot product изисква умножение и събиране за всяко измерение. При милиони кандидати и стотици измерения това е сериозна CPU работа. Не бъркай embedding vector с CPU SIMD vector: първият е *данни*, вторият е *инструкция над няколко primitive стойности*.

Бърз цикъл не е автоматично по-бързо приложение: ако реалният bottleneck е SQL, HTTP, I/O или memory bandwidth, Vector API може да не даде видима полза. Първо профилираме и дефинираме метрика.

**Цели:** (1) различаваме SIMD от threads и ILP; (2) сравняваме честно scalar baseline с ILP и SIMD; (3) обработваме всяка дължина, включително непълен вектор; (4) разбираме FMA, floating-point rounding и ISA differences; (5) използваме JUnit + JMH, без фиксиран speedup.

## 2. Mental model — три различни оптимизации

- **Scalar:** `sum += left[i] * right[i]` — един `float` по един. JIT *може* да векторизира цикъла автоматично, но това трябва да се докаже за конкретния HotSpot/CPU/код.
- **ILP (Instruction-Level Parallelism):** четири независими акумулатора намаляват веригата от зависимости; не означава четири Java threads и само по себе си не е SIMD.
- **SIMD (Single Instruction, Multiple Data):** `FloatVector` изпълнява една операция върху много `float` lanes; `VectorSpecies` определя формата/дължината според JVM/CPU. `SPECIES_PREFERRED` не е обещание за определен брой lanes.
- **FMA (fused multiply-add):** умножението + събирането се закръглят веднъж, затова FMA и отделните `mul` + `add` могат да дадат *различни* последни битове. FMA също не е винаги по-бързо на всяка платформа.

Една обща концепция от лекцията (около 15–17 мин.): лекторът първо вижда голям speedup, но признава, че scalar вариантът не използва ILP, докато SIMD вариантът използва. След ILP scalar сравнение ускорението намалява. **Не сравнявай SIMD+ILP със scalar без ILP и не приписвай цялата разлика на SIMD.**

## 3. README → работещ код и доказателство

| Какво учим | Реализация | Тест / benchmark |
| --- | --- | --- |
| Наивен scalar, ILP ×4, scalar FMA | [ScalarDotProducts.java](src/main/java/bg/hristomanov/education/vector/ScalarDotProducts.java) | [DotProductsTest.java](src/test/java/bg/hristomanov/education/vector/DotProductsTest.java) |
| Vector API, независими accumulators, tail, FMA, малък scalar fallback | [VectorDotProducts.java](src/main/java/bg/hristomanov/education/vector/VectorDotProducts.java) | [DotProductsTest.java](src/test/java/bg/hristomanov/education/vector/DotProductsTest.java) |
| JMH сравнение на *еднакви данни* с различни размери | [DotProductBenchmark.java](../performance-evolution-benchmarks/src/main/java/bg/hristomanov/education/performance/benchmarks/DotProductBenchmark.java) | [JMH README](../performance-evolution-benchmarks/README.md) |
| Защо „по-бързо“ не значи автоматично „по-добре“ | [P1 ILP/Benchmark протокол](../performance-evolution/SIMD-ILP-BENCHMARKING.md) | [P1 Benchmark Correctness](../performance-evolution/BENCHMARK-CORRECTNESS.md) |
| Как Lucene/Elastic вземат реални решения | [P3 Case Study](../performance-evolution/ELASTICSEARCH-SIMD-CASE-STUDY.md) | Архитектурен анализ, **не** runnable Lucene lab |

### Защо има две SIMD реализации?

`VectorDotProducts.multiplyAdd` прави отделни `mul` и `add`. `VectorDotProducts.fusedMultiplyAdd` използва `FloatVector.fma`. И двете обработват четири пълни `VectorSpecies` блока с независими акумулатори, след това оставащите пълни блокове и накрая scalar tail за всяка дължина. Не използваме маски, защото explicit scalar tail е по-прост за тази лаборатория.

`VectorDotProducts.smallInputFallback` избира scalar ILP за размер под **четири SIMD блока**. Това е **илюстративен праг**, а не доказана оптимална политика. Той работи *след* като incubator module е включен — не замества runtime fallback при липсващ Vector API. За production-like dynamic provider разгледай [Lucene VectorizationProvider](https://github.com/apache/lucene/blob/main/lucene/core/src/java/org/apache/lucene/internal/vectorization/VectorizationProvider.java): там има module/platform checks и default implementation. Не копирай сляпо heuristics от Lucene в нашите услуги.

## 4. Run / reproduce

От root на `education`, с **JDK 25** и Maven:

```bash
java -version
mvn -pl java/jvm/vector-api test
mvn -pl java/jvm/performance-evolution-benchmarks -am -DskipTests package

# JVM флагът е задължителен и при изпълнение на JMH:
java --add-modules jdk.incubator.vector \
  -jar java/jvm/performance-evolution-benchmarks/target/benchmarks.jar \
  'DotProductBenchmark' -wi 3 -i 5 -f 2 -prof gc
```

За кратка проверка на single parameter: `-p dimensions=1024`. В IntelliJ IDEA за директно стартиране на `VectorDotProducts`/benchmark добави VM option `--add-modules jdk.incubator.vector`. Maven compiler и Surefire вече са конфигурирани в [pom.xml](pom.xml). Самото наличие на Vector API **не означава гарантиран hardware SIMD** — JVM може да използва неускорен execution path.

**Тестов договор:** `DotProductsTest` проверява празни/кратки/големи масиви, species boundaries, нестандартни дължини, null и невалидни размери. Резултатите се сравняват с **double precision reference в числов толеранс**, не по битова идентичност. Ако приложението изисква конкретен floating-point ред на операциите, SIMD варианта не е автоматична замяна.

## 5. Decision contract — когато обмисляме SIMD

| Въпрос | Решение |
| --- | --- |
| **Проблем** | CPU-hot, data-parallel изчисление над много primitives; сериализирано accumulator dependency. |
| **Кога да** | Продукционен CPU hotspot, големи contiguous arrays, количествено доказателство в JMH + end-to-end load test, правилен numerical tolerance. |
| **Кога НЕ** | JDBC/HTTP wait, малки sporadic масиви, нестабилен API dependency, нужна е exact bitwise семантика, CPU hotpath не е профилиран. |
| **Печелим** | Потенциално повече throughput / по-ниско CPU време; явна структура на изчислението. |
| **Губим** | Incubator compatibility/flags, ISA-specific решения, JMH+ops cost, повишена сложност, floating-point rounding differences и fallback lifecycle. |
| **Алтернативи** | Оставаме с прост scalar цикъл; първо ILP, данни/layout, JIT optimization; ползваме Lucene/библиотека вместо собствен SIMD kernel. |
| **Доказателство** | [JUnit](src/test/java/bg/hristomanov/education/vector/DotProductsTest.java), [JMH](../performance-evolution-benchmarks/src/main/java/bg/hristomanov/education/performance/benchmarks/DotProductBenchmark.java), след това реален performance профил. |

**Важен компромис:** дори да е по-бърз single dot product, search pipeline може да се ограничава от memory traffic, candidate selection или reranking. Не правим извод за Elasticsearch end-to-end latency от JMH микробенчмарк на един метод.

## 6. Практическо упражнение и recall

1. Защо ILP4 не е паралелизъм с четири threads? Защо е честно да сравним `scalarIlp4` и `vectorMulAdd`?
2. Какво става за дължина `lanes * 4 + 1` и защо tail не бива да бъде загубен?
3. Може ли vector FMA да е числено различен от `scalar`? Как дефинираме приемлив error budget?
4. Ако `dimensions=16` не се ускорява, а `dimensions=8192` се ускорява, какво може да е обяснението?
5. Ако Spring Boot service чака Oracle 95% от времето, защо SIMD не е първото действие?
6. Кои три въпроса ще зададеш на PR, който твърди „10× faster“?

**Правило за code review:** ако имаме CPU профил + числен договор + честен benchmark — обмисли SIMD; ако не — по-прост scalar/JIT path е предпочитан. Ако специализираният native kernel дава предимство, сравни го със стабилна библиотека и отчети native deployment/ABI риска.

## Оригинални източници

1. [Devoxx 2026 — Piotr Przybył, SIMD for Java](https://www.youtube.com/watch?v=adFS-_sCTQ4) (автоматичен SRT транскрипт, предоставен за този урок).
2. [JEP 508 — Vector API, Tenth Incubator (JDK 25)](https://openjdk.org/jeps/508).
3. [JEP 537 — Vector API, Twelfth Incubator (JDK 27)](https://openjdk.org/jeps/537).
4. [JDK 27 Vector API documentation](https://docs.oracle.com/en/java/javase/27/docs/api/jdk.incubator.vector/jdk/incubator/vector/package-summary.html).
5. [Lucene VectorUtil implementation and documented FMA configuration](https://github.com/apache/lucene/blob/main/lucene/core/src/java/org/apache/lucene/util/VectorUtil.java).
6. [OpenJDK JMH](https://github.com/openjdk/jmh).
7. [Elastic — simdvec implementation](https://www.elastic.co/search-labs/blog/elasticsearch-vector-search-simdvec-engine).
