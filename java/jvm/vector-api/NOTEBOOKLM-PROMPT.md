# NotebookLM · 25–35 минути български аудио урок — SIMD / Java Vector API

**Източници за добавяне:** [Devoxx лекция](https://www.youtube.com/watch?v=adFS-_sCTQ4), [runnable Vector API lab](README.md), [P1 SIMD Benchmark Fairness](../performance-evolution/SIMD-ILP-BENCHMARKING.md), [P3 Elasticsearch Case Study](../performance-evolution/ELASTICSEARCH-SIMD-CASE-STUDY.md) и оригиналните документи, линкнати там.

## Prompt за Deep Dive на български

Създай технически разговор на български между Java performance инженер и backend разработчик със Spring Boot/Oracle опит. Не прави само преразказ на лекцията; учи чрез примери, проверими твърдения и точни английски термини.

1. Започни с CPU-heavy dot product use case и защо обикновен CRUD не печели автоматично от SIMD.
2. Обясни embedding vector срещу CPU SIMD vector, species, lanes, register width; ILP НЕ е многопоточност; JIT auto-vectorization срещу explicit Vector API.
3. Обясни benchmark trap от лекцията (~15–17 мин.): scalar без ILP срещу SIMD с четири независими accumulators; как правим справедлива контролна група.
4. Разходи се през `ScalarDotProducts` и `VectorDotProducts` с малки входове, непълни vector blocks, FMA и floating-point rounding. Прегледай JUnit и JMH; отдели correctness от speed и microbenchmark от end-to-end.
5. Представи четирите production решения от Elastic: Lucene provider fallback, BBQ quantization/reranking, C++ simdvec през FFM и Swiss-style ES|QL hash tables.
6. Финализирай с decision contract: проблем, кога да/кога НЕ, печалби, компромиси, по-просто решение, proof и пет Senior code-review въпроса.

**Важни ограничения:** не измисляй speedup от нашия CPU; Vector API е incubator в JDK 27, а FFM е final от JDK 22. Транскриптът вероятно греши с „5 milliseconds“ FFI overhead (~46:00); официалният Elastic източник говори за единици **наносекунди**. 32× compressed raw payload не е 32× цял Elasticsearch index. Давай честни изводи и предупреждения за production usage.
