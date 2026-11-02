# P2 Hands-on · Hardwood lessons: page scheduling, safe predicate pruning, AI correctness

> Изпълним Java 25 / Maven / JUnit лабораторен набор към [Engineering Case 005 — Hardwood](../../../docs/case-studies/005-hardwood-ai-parquet-engineering.md), по транскрипта на [Gunnar Morling, Devoxx 2026](https://youtu.be/6mzvcPr30kg).  
> **Това НЕ е реален Parquet parser, query engine или performance benchmark.** Използваме миниатюрни входове, за да докажем два механизма: bounded page concurrency и fail-conservative metadata pruning.

## 1. What problem are we solving?

Малък Parquet file може да съдържа **един row group**, две projected columns и много pages. Ако изпълняваш задача само на row-group level, имаш един worker. Ако декодираш всички pages на неограничен брой виртуални нишки, можеш да изчерпиш CPU/heap/IO capacity. Ако агресивно пропускаш pages въз основа на неточни статистики, можеш **безшумно да изгубиш** валидни резултати.

Тук упражняваме две решения, които **не са еквивалентни на реалния Hardwood алгоритъм**:
- ограничен прозорец от **page-level tasks**; loading на virtual threads, decoding на fixed platform-worker pool;
- консервативна тристойностна метаданна оценка: `CANNOT_MATCH`, `MIGHT_MATCH`, `ALWAYS_MATCHES`.

## 2. Code → тест → evidence

| Задача | Source | JUnit |
| --- | --- | --- |
| Page-level scheduling / ordered consumption / failure | [PagePipelineLab.java](src/main/java/bg/hristomanov/education/performance/hardwood/PagePipelineLab.java) | [PagePipelineLabTest.java](src/test/java/bg/hristomanov/education/performance/hardwood/PagePipelineLabTest.java) |
| Page predicate decisions / nulls / Bloom | [PagePruningPolicy.java](src/main/java/bg/hristomanov/education/performance/hardwood/PagePruningPolicy.java) | [PagePruningPolicyTest.java](src/test/java/bg/hristomanov/education/performance/hardwood/PagePruningPolicyTest.java) |
| Overall case and context | [Case 005](../../../docs/case-studies/005-hardwood-ai-parquet-engineering.md) | Транскрипт + author/source links |
| Existing performance proof rules | [Benchmark Correctness](BENCHMARK-CORRECTNESS.md) | [Benchmark labs](BENCHMARK-CORRECTNESS-LABS.md) |

## 3. Run on Java 25

От root директорията на Education:

```bash
# Всички нови тестове по тази тема:
mvn -B --no-transfer-progress -pl java/jvm/performance-evolution \
  -Dtest=PagePruningPolicyTest,PagePipelineLabTest test

# Всички тестове за съществуващия performance-evolution module:
mvn -B --no-transfer-progress -pl java/jvm/performance-evolution verify

# Пълният 59+ module reactor:
mvn -B --no-transfer-progress verify
```

**Windows / PowerShell:** изпълни Maven командата на един ред или използвай IntelliJ Maven Run Configuration. Не са нужни Docker, Redis, S3, Hadoop, Hardwood JAR или външен Parquet fixture.

## 4. Experiment A — един row group, четири pages

~~~text
Pages:
  (rowGroup=0, column=fare, pageIndex=0) → byte 10
  (rowGroup=0, column=fare, pageIndex=1) → byte 11
  (rowGroup=0, column=fare, pageIndex=2) → byte 12
  (rowGroup=0, column=fare, pageIndex=3) → byte 13

run(pages, maxInFlight=2, cpuWorkers=2)
   → ordered decoded values: 10, 11, 12, 13
   → maxOutstandingPages: 2 (count of scheduled futures)
   → loader runs in virtual threads
   → decoder runs in fixed platform-worker threads
~~~

**Tests** enforce each claim: [PagePipelineLabTest](src/test/java/bg/hristomanov/education/performance/hardwood/PagePipelineLabTest.java). Втори тест изпълнява 12 page tasks с 3 pending futures и максимум 2 CPU workers; не твърди, че точно 2 cores ще работят едновременно или че това е по-бързо.

**Ограничения:** Броят pending pages **не е memory byte budget**. Ако една page е 100 MB и друга — 1 MB, фиксиран брой pages не гарантира постоянен heap footprint. Изходът е ordered, затова най-старата бавна задача може да предизвика head-of-line blocking. Реалният Hardwood прави по-сложен adaptive prefetch и balancing по колони.

### Break-it questions

1. Какво ще стане при `maxInFlight=0` или `cpuWorkers=0`? Защо изхвърляме изключение **преди** да стартираме workers?
2. Какво става ако loader хвърли `IOException`? Защо **не** връщаме непълен успешен резултат?
3. Какво ще стане при `loader.load(page)==null`? Защо изричният failure е по-добър от silently empty payload?
4. Защо CPU-bound decoder не трябва просто да създава неограничено нови virtual threads?
5. Ако най-старата страница се бави, как би подобрил ingestion с completion-order queue, без да нарушиш реда на крайния резултат?

## 5. Experiment B — conservative metadata decisions

[PagePruningPolicy.java](src/main/java/bg/hristomanov/education/performance/hardwood/PagePruningPolicy.java) не чете истински Parquet metadata. Работи със `PageStatistics(valueCount, knownNullCount, minimum, maximum)`, където `null` означава **unknown metadata**, не null dataset value.

| Metadata | Predicate | Decision | Защо |
| --- | --- | --- | --- |
| min=10,max=30, nullCount=0 | `x > 30` | `CANNOT_MATCH` | Максимумът не е по-голям от 30 |
| min=10,max=30, nullCount=0 | `x > 9` | `ALWAYS_MATCHES` | Всички са non-null и над 9 |
| min=10,max=30, nullCount=1 | `x > 9` | `MIGHT_MATCH` | Null не удовлетворява predicate |
| min/max=null, nullCount=null | `x == 10` | `MIGHT_MATCH` | Няма доказателство за отсъствие |
| min=10,max=20, Bloom=definitely absent | `x == 15` | `CANNOT_MATCH` | Валиден Bloom negative е proof |
| min=10,max=20, Bloom=might contain | `x == 15` | `MIGHT_MATCH` | False positive е позволен |

[PagePruningPolicyTest.java](src/test/java/bg/hristomanov/education/performance/hardwood/PagePruningPolicyTest.java) съдържа **independent value scanner oracle**: сравнява decision-а с буквални fixture values, включително nulls. За всеки кандидат в [-1,12], ако planner каже `CANNOT_MATCH`, действителните стойности трябва да имат 0 hits; ако каже `ALWAYS_MATCHES`, **всяка** стойност трябва да е non-null match.

**Много важен trade-off:** метаданната оптимизация е правилна само когато min/max/knownNullCount/Bloom evidence са от **доверен, коректен** източник с правилни semantics. При unreadable/corrupted/unsupported metadata трябва да изберем safe fallback (обикновено full scan или error), а не да предполагаме, че няма съвпадение. Този модел не доказва Parquet handling на float/NaN, nested repetition, truncated string bounds или corrupted files.

### Negative exercises

1. Промени сравнение `maximum <= threshold` на `maximum < threshold` и виж boundary тестовете. Ще има correctness или само performance regression?
2. Премахни `hasNoNulls()` от `ALWAYS_MATCHES`: кой JUnit тест трябва да те спре?
3. Напиши Bloom predicate, който връща false за реално съществуваща стойност — защо това би било **недопустим false negative**, а не Bloom optimization?
4. Върни `CANNOT_MATCH` когато min/max липсват: покажи с fixture защо това може да загуби редове.
5. Кои метаданни ти трябват, за да поддържаш SQL `IS NULL` правилно? Нашият модел не го имплементира.

## 6. P1 Task contract за coding agent (не vendor-specific prompt)

**Примерна задача:** „Добави `lessThan` към PagePruningPolicy без да променяш старите decisions.“

- **Source of truth:** explicit comparison semantics (`x < threshold`), правилата за null/unknown и [Parquet Page Index](https://parquet.apache.org/docs/file-format/pageindex/) като външен design reference.
- **Before changes:** запази старите тестове, benchmark baseline и API contract; изясни дали `null` е unknown или истински null.
- **Acceptance criteria:** false skip и false all-match са недопустими; independent fixture oracle проверява всички boundary cases; invalid stats fail closed.
- **AI implementation:** агентът може да предложи малък diff и unit tests, **не** може да изключва тестове, да променя expected outputs, да твърди speedup без измерване или да редактира CI за green status.
- **Review:** публичния API и semantic correctness се проверяват преди code style; независим reviewer проверява Git head/CI evidence.
- **Deployment:** няма production adoption от лабораторията, тъй като тя не parse-ва real Parquet data.

Това прилага архитектурното послание на Gunnar, без да добавя отделен agent skill. Сравни [AI-assisted development](../../../ai/agentic-development/README.md), [planning/review](../../../ai/agentic-development/PLANNING-AGENTS-ORCHESTRATION.md) и [testing guardrails](../../../spring/testing/ai-assisted-testing/03-AI-GUARDRAILS.md).

## 7. Не твърдим резултат, който не сме измерили

**Тестовете доказват:** Java scheduling contract, корректен ред, граници, resource limits, error propagation и безопасен metadata decision при описаните inputs.

**НЕ доказват:** реална Parquet decoding speed, zero-copy, Hardwood performance, адаптивен prefetch, memory bytes budget, native vectorization, реален JFR анализ или numeric benchmarking резултат. Тези теми изискват външен reproducible workload и сравнение с реален format reader.

### Self-check

- Кое е по-голям риск: да сканираме една излишна страница или да пропуснем страница с matching rows?
- Кой е независимият oracle и защо не го генерираме от същата production implementation?
- Може ли 4 virtual I/O tasks да се изпълняват на 2 CPU cores без опасност от неограничен CPU parallelism?
- Защо aggregate time на 2 cores не означава два пъти по-добър throughput?
- Какво би показал custom JFR event за prefetch miss и защо не е нужен нов JFR урок?

## Original sources

- [Gunnar Morling — Hardwood Devoxx talk](https://www.youtube.com/watch?v=6mzvcPr30kg), user SRT.
- [Hardwood design: page-level parallelism, adaptive prefetching](https://www.morling.dev/blog/hardwood-new-parser-for-apache-parquet/).
- [Apache Parquet Bloom Filter contract](https://parquet.apache.org/docs/file-format/bloomfilter/) и [Page Index](https://parquet.apache.org/docs/file-format/pageindex/).
- [Case 005](../../../docs/case-studies/005-hardwood-ai-parquet-engineering.md).
