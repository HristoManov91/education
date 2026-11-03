# P3 · Engineering Case Study — Elasticsearch, BBQ, native `simdvec` и Swiss-style hashes

> **Архитектурна бележка, не runnable Elasticsearch/FFM lab.** Основа: [Piotr Przybył — *SIMD for Java: how Elasticsearch already benefits from the Panama Vector API*, Devoxx Belgium 2026](https://www.youtube.com/watch?v=adFS-_sCTQ4); сегменти ~29:00–50:30 от автоматичния транскрипт. Актуализирано с първични източници на Elastic и Lucene (линкове в края).

## 1. Производственият проблем

Vector search може да обработва огромен брой candidate distances. Един dot product е CPU работа над `float[]`, но **memory bandwidth, layout и количество кандидати** може да доминират. Времето за един kernel ≠ времето за цялата search заявка.

**Илюстративна памет:** 1 000 000 вектора × 768 dimensions × 4 bytes (float32) = **3 072 000 000 bytes (3.072 GB)** за числовите стойности. При 1 бит/измерение чистият quantized payload е 96 000 000 bytes (96 MB), тоест 32× по-малко *само за тези стойности*. Реалният индекс включва metadata, graph structures, оригинални vectors за rerank и други overhead-и. 32× **не означава** 32× по-малък Elasticsearch индекс или същата точност на ranking.

## 2. Четири инженерни решения

### A. Lucene Vector API + безопасен fallback

[Lucene VectorUtil](https://github.com/apache/lucene/blob/main/lucene/core/src/java/org/apache/lucene/util/VectorUtil.java) документира SIMD при наличен и включен `jdk.incubator.vector`. [VectorizationProvider](https://github.com/apache/lucene/blob/main/lucene/core/src/java/org/apache/lucene/internal/vectorization/VectorizationProvider.java) избира default fallback при неподдържани условия. Lucene има `-Dlucene.useScalarFMA` и `-Dlucene.useVectorFMA` със стойности `auto|true|false`: оптималният CPU path е зависим от платформата.

**Печалба:** Java SIMD без custom native dependency за поддържаните операции. **Цена:** incubator/runtime flags, JIT/CPU compatibility, алгоритмични ограничения. **Най-проста алтернатива:** Lucene default provider или scalar код; не копирай SIMD директно в произволен Spring сервиз.

### B. Better Binary Quantization (BBQ) — променяме формата на данните

Лекторът описва 1-bit quantized document vectors и по-богато 4-bit query представяне с **bit planes**, `AND` и population count/bit counting. Вместо много float multiplications може да се работи с компактен битов layout. Това е **концептуално обяснение, не имплементация на целия BBQ алгоритъм** — специфични centroids, scoring correction и reranking детайли трябва да се вземат от версията на библиотеката.

**Кога:** когато memory/bandwidth и индексният размер са реални bottlenecks и бизнесът допуска approximate search. **Кога НЕ:** задължителна точна float32 семантика и неизмерен recall. **Печалби:** по-малко данни за четене; **цена:** quantization error, качество на retrieval, rerank и допълнителна latency. **Доказателство:** recall@k/nDCG/quality за представителни queries + memory footprint + query latency.

### C. Native `simdvec` през FFM, не „Java Vector API = FFM“

Elastic разработва native C++ SIMD kernels, извиквани от Java чрез **Foreign Function & Memory API** (стандартен от JDK 22; JEP 454). Това осигурява specialized ISA kernels за x86/ARM, quantized formats, bulk scoring, prefetch и memory layouts. [Официалният simdvec материал](https://www.elastic.co/search-labs/blog/elasticsearch-vector-search-simdvec-engine) описва FFI call overhead **в единици наносекунди**, не „5 milliseconds“ от автоматичния SRT около 46:00.

**Кога:** workload със специфични операции, които Java path не обслужва достатъчно добре, и готовност за native CI, deployment и ABI lifecycle. **Кога НЕ:** общ backend без native profiling, multi-platform поддръжка или fallback. **Печалби:** низови ISA-specific и batch optimizations; **цена:** C++/JNI-like operational burden, ABI/version/OS concerns. **Алтернатива:** Java Vector API или готова библиотека. **Доказателство:** independent per-ISA native benchmark + end-to-end search load.

### D. ES|QL Swiss-style hash tables — locality преди arithmetic

При high-cardinality `GROUP BY`/stats много random hash probes водят до cache misses. Elastic използва **control bytes** за slot metadata и кратък hash fingerprint, сканирани на блокове от **16 байта** с Java Vector API. Кандидатите се проверяват реално чак след SIMD prefilter. [Elastic Swiss-style ES|QL article](https://www.elastic.co/search-labs/blog/esql-swiss-hash-stats) съобщава **до 2–3×** ускорение на build/iteration при конкретни uniform high-cardinality workloads — **не** универсално 2–3× за всяка заявка или `HashMap`.

**Кога:** профилиран проблем с висок cardinality и probing/cache misses. **Кога НЕ:** малки таблици или incompatible ownership/deletion semantics; конкретната имплементация избягва deletions/tombstones. **Печалби:** cache locality, намален memory traffic; **цена:** specialized data structure/layout/metadata. **Доказателство:** representative query benchmark и CPU cache профил.

## 3. Decision contract за проектите ни

| Подход | Кога се използва | Кога НЕ | Trade-off |
| --- | --- | --- | --- |
| Java Vector API | Contiguous primitive arrays + CPU hotspot + JDK support | CRUD / Oracle round-trip / strict stable API | SIMD ↔ incubator, JIT/ISA, error tolerance |
| BBQ/quantization | Memory pressure и приемлив approximate retrieval | Exact ranking contract | Compaction ↔ recall/rerank overhead |
| Native simdvec / FFM | Специализиран tested kernel и ясна deployment ownership | Нямаме подкрепа за native deployment | ISA control ↔ ABI/ops cost |
| Swiss hashing | High-cardinality hash probes ограничават анализа | Малък dataset или неподходящ lifecycle | Locality ↔ complexity и extra metadata |

**Свързани runnable материали:** [P2 Java Vector API с JUnit](../vector-api/README.md) и [P1 Benchmark Fairness/ILP](SIMD-ILP-BENCHMARKING.md). Case study не създава Elasticsearch cluster, не доказва конкретен speedup и не подменя отделното [P2 FFM обучение](../native-interoperability/README.md).

## 4. Senior recall и извод

1. Защо 32× по-малък raw payload ≠ 32× по-малък индекс?
2. Какъв retrieval quality contract се променя при BBQ и как го измерваме?
3. Защо native FFI cost и bulk kernel throughput трябва да се оценяват отделно?
4. Как control bytes ограничават random reads и защо това е алгоритмична оптимизация?
5. Какво трябва да има във fallback test matrix (JDK, CPU/ISA, OS, floating-point tolerance)?

**Вземаме инженерния метод, не задължително Elasticsearch dependency:** профил → data layout → честен benchmark + correctness/quality → архитектурно решение → production evidence.

## Оригинални източници

1. [Piotr Przybył, Devoxx Belgium 2026 — оригиналното видео](https://www.youtube.com/watch?v=adFS-_sCTQ4).
2. [Elastic — simdvec architecture](https://www.elastic.co/search-labs/blog/elasticsearch-vector-search-simdvec-engine).
3. [Elastic — Swiss-style ES|QL hash tables](https://www.elastic.co/search-labs/blog/esql-swiss-hash-stats).
4. [Lucene VectorUtil.java](https://github.com/apache/lucene/blob/main/lucene/core/src/java/org/apache/lucene/util/VectorUtil.java).
5. [Lucene VectorizationProvider.java](https://github.com/apache/lucene/blob/main/lucene/core/src/java/org/apache/lucene/internal/vectorization/VectorizationProvider.java).
6. [JEP 454 — FFM API, final in JDK 22](https://openjdk.org/jeps/454).
7. [JEP 537 — Vector API, twelfth incubator](https://openjdk.org/jeps/537).
