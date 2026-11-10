# Case 005 — Hardwood: AI-assisted Parquet engineering с независими проверки

> **P1 AI-assisted engineering / P2 Java performance** · [Gunnar Morling — Hardwood: Building a Parquet Parser From Scratch With a Little Help From AI, Devoxx 2026](https://www.youtube.com/watch?v=6mzvcPr30kg).  
> Основен източник: предоставеният автоматичен SRT (~52 минути, timestamps са ориентировъчни). Сверени са [изходният код на Hardwood](https://github.com/hardwood-hq/hardwood), [описанието на автора](https://www.morling.dev/blog/hardwood-new-parser-for-apache-parquet/) и [Hardwood 1.0](https://www.morling.dev/blog/hardwood-1-0-fast-lightweight-apache-parquet-reader-for-the-jvm/).  
> **Вид:** реален engineering case, **НЕ** production incident, реален Parquet parser за Education или доказан benchmark на нашия код.

## 1. Контекст: защо авторът прави още една Parquet библиотека?

[00:58–11:15](https://youtu.be/6mzvcPr30kg?t=58). Parquet е **columnar file format** за аналитични workloads: file → row groups → column chunks → pages. Footer съдържа schema и metadata, а вложените structures разчитат на definition/repetition levels. Форматът е голям: codecs, encodings, nested data, logical types, nulls, statistics, Bloom filters, encryption и продължаващо развитие.

Авторът разглежда **два конкретни недостатъка** на съществуващото JVM решение `parquet-java` за неговия use case: тежък transitive dependency graph заради Hadoop и по същество single-threaded reader. Не твърдим, че всички JVM Parquet workflows задължително са еднонишкови — екипите могат да паралелизират и външно, но author-driven goal е читателят сам да декодира efficiently един файл на много cores.

**Архитектурно решение:** Hardwood — нов reader, а по време на презентацията вече и beta writer; минимални **задължителни** зависимости, optional codecs/S3 integrations, multicore page decoding, Java 21+ API и ecosystem compatibility. Версиите в лекцията описват **състоянието към събитието**, не днешна гаранция за latest release.

**Кога това има смисъл:** dependency footprint/ABI/native packaging и multicore decode са доказани изисквания; имаш дългосрочен format/compatibility owner. **Кога НЕ:** обикновен Spring CRUD проект или рядък Parquet import с поддържана библиотека. Разходът е огромна спецификация, поддръжка, множество edge cases, backward compatibility и performance regressions.

## 2. Какви технически решения разглеждаме

### A. Row cursor, column batches и projection ([11:20–15:05](https://youtu.be/6mzvcPr30kg?t=680))

Hardwood row cursor използва typed getters и не алокира нов row object на всяка итерация. Column API връща primitive arrays/batches, избягва boxing и е подходящ за analytic aggregations/SIMD. **Projection** зарежда само необходимите колони, например 3 от 20, което спестява I/O, особено при remote object storage.

| Избор | Кога | Кога НЕ | Печелим ↔ губим |
| --- | --- | --- | --- |
| Row cursor | Nested records и последователен бизнес достъп | Tight numeric aggregation, при която boxing доминира | Малко allocations ↔ lifetime/index API дисциплина |
| Column batches | Numeric/analytical bulk processing | Изискваш удобни immutable per-row POJOs | Primitive/vectorized loops ↔ batch boundaries и data layout |
| Projection | Използваш малка част от колоните | Наистина са нужни всички колони | Много по-малко I/O ↔ metadata/selection complexity |

Не приема̀й, че всички JIT/compiler оптимизации са автоматични — измервай в реалния workload.

### B. Granularity на паралелизма ([15:20–19:05](https://youtu.be/6mzvcPr30kg?t=920))

~~~text
One Parquet file
  └─ 1 row group
       ├─ column A -> page0 page1 page2 ...
       └─ column B -> page0 page1 page2 ...
                          │
                    bounded page loads
                      (I/O tasks)
                          │
                    bounded CPU decode
                          │
                    ordered consumption
~~~

Паралелизация само по row groups може да остави 15 от 16 cores неизползвани, ако има един row group. Column chunks също може да не са достатъчно на брой; някои колони се декодират по-бавно и създават imbalance. Лекторът избира **page-level parallelism** плюс adaptive prefetch за slow columns. Използва virtual threads за подходящите blocking операции и modern Java mechanisms за decoding.

**Печалба:** повече независими задачи и по-добро използване на cores. **Цена:** scheduling, ordering, I/O pressure, backpressure, memory buffers, cleanup/cancellation и concurrency bugs. Virtual threads не добавят CPU cores.

**Нашият executable contract:** [PagePipelineLab](../../java/jvm/performance-evolution/src/main/java/bg/hristomanov/education/performance/hardwood/PagePipelineLab.java) + [JUnit](../../java/jvm/performance-evolution/src/test/java/bg/hristomanov/education/performance/hardwood/PagePipelineLabTest.java). Един row group / четири pages, ограничени in-flight futures, virtual I/O срещу ограничен platform-thread CPU pool, ред на изхода и fail-on-error. **Не възпроизвеждаме реалния Hardwood adaptive prefetcher, Parquet page reader или byte-level memory budget.**

### C. Predicate pushdown: най-евтината работа е пропуснатата ([22:35–25:15](https://youtu.be/6mzvcPr30kg?t=1355))

Min/max statistics, dictionaries и Bloom filters могат да доказват, че конкретна страница няма нужните стойности, и да спестят remote I/O + decompress/decode. Bloom semantics:
- definite absent → безопасно `CANNOT_MATCH`;
- probably present → само `MIGHT_MATCH` (false positives са допустими, false negatives — не).

**Nulls са капан:** за `value > 5` и min=10 **не** можем да кажем `ALWAYS_MATCHES`, ако има nulls или null count е unknown. За missing stats избираме консервативно `MIGHT_MATCH`. Реалният Parquet има и сложни правила за logical types, NaN, sort order, truncated ranges, page indexes и nested levels.

Точно това демонстрира [PagePruningPolicy](../../java/jvm/performance-evolution/src/main/java/bg/hristomanov/education/performance/hardwood/PagePruningPolicy.java) с [независим fixture-based JUnit oracle](../../java/jvm/performance-evolution/src/test/java/bg/hristomanov/education/performance/hardwood/PagePruningPolicyTest.java). **Нашият модел работи върху доверени фиктивни metadata, не чете Parquet.** Виж [Parquet Page Index](https://parquet.apache.org/docs/file-format/pageindex/) и [Bloom filters](https://parquet.apache.org/docs/file-format/bloomfilter/).

### D. Encoded fast path при fixed-length lists ([26:00–30:50](https://youtu.be/6mzvcPr30kg?t=1560))

При 3D coordinates/embeddings с фиксиран брой елементи definition/repetition streams могат да са силно предвидими. Авторът разпознава повтарящи се **encoded patterns** и избягва materialization на големи временно декодирани масиви. Докладва ускорения за конкретния format/workload, но не общ Java speedup.

**Поука:** по-добрият data representation и липсата на излишна работа може да са по-ценни от добавяне на threads. **Не добавяме encoded Parquet fast path в Education:** това би изисквало пълен форматен correctness набор, несъразмерен с целта.

### E. Custom JFR events и performance measurement ([19:00–25:50](https://youtu.be/6mzvcPr30kg?t=1140))

Hardwood според автора излъчва custom JFR events за decoded page и закъснял prefetch. JVM/JFR CPU data само по себе си не обяснява *защо* конкретен column pipeline чака: domain-specific events дават връзка между CPU, buffer, remote I/O и decode.

**Recognition metrics:** page decode duration, prefetch misses, pages in flight, bytes buffered, row-group/column id, skipped page counts, GC/CPU/utilization. Не изнасяй sensitive row values в trace.

Лекторът показва NYC taxi workload на определена AWS машина и споменава около **16.5 млн. реда/сек** при multi-core column reader за ранната показана версия. Това е **негово измерване**, а не универсална прогноза или наш benchmark. Важно е, че сравнява и **single-core** срещу `parquet-java`; all-core срещу single-core не е честно apples-to-apples доказателство.

За методологията използваме съществуващите [JFR diagnostics](../../java/jvm/jfr-troubleshooting/README.md), [Benchmark Correctness](../../java/jvm/performance-evolution/BENCHMARK-CORRECTNESS.md) и [JMH](../../java/jvm/performance-evolution-benchmarks/README.md).

## 3. P1: Как AI помага, без да е source of truth ([35:15–46:25](https://youtu.be/6mzvcPr30kg?t=2115))

**Най-широко приложимото знание за нашите Codex workflows.** Gunnar разполага с:
1. **Parquet specification** — определя необходимия contract и семантика.
2. **Стотици conformance fixtures** — външен, предварително дефиниран test input.
3. **Differential testing** срещу `parquet-java` и DuckDB — сигнал за различно поведение.

Когато тестовете не съвпадат, agent може да разследва; но **reference parser може също да греши** (авторът споменава намерени bugs в parquet-java). Тогава сверяваме срещу specification, други oracles и конкретния fixture, вместо да копираме сляпо първия expected result.

~~~text
Authoritative specification + acceptance criteria
 → independently sourced fixtures and reference outputs
 → AI proposes implementation
 → correctness tests (positive/negative/differential)
 → human/independent review of contract and edge cases
 → JFR/profile + controlled performance regression suite
 → evidence-backed delivery / rollback
~~~

### Реално описани проблеми с AI output ([39:25–41:20](https://youtu.be/6mzvcPr30kg?t=2365))

- 3–5 различни formatting implementations се появяват из проекта → inconsistent UI.
- Agent понякога „поправя“ failing test, като го изключва → **false green**.
- Автоматични small refactors могат да създадат performance regression, който се открива чрез regression suite/`git bisect`.
- Developer носи отговорност за PR; „AI го написа“ не е достатъчно оправдание.

Това са **наблюдения на лектора**, не статистическа оценка за всички AI модели.

### AI profiling ≠ performance доказателство ([37:00–40:00](https://youtu.be/6mzvcPr30kg?t=2220))

Gunnar използва профилираща машина, skill и SSH към нея, за да анализира hot paths и дори disassembly. Описва случай, при който hoisting на field access от inner loop към local variable е помогнал. **Няма универсална гаранция**, че този синтактичен refactor ще ускори HotSpot; компилаторът може вече да го прави. Необходим е controlled benchmark, CPU/JIT evidence, correctness и regression tests.

**Security:** не даваме SSH credentials на AI agent само заради този пример. При реален подобен workflow permissions трябва да са least-privilege и audit-able, с изолирана машина/данни.

### Review pyramid — най-важни са трудно обратимите решения ([43:20–45:10](https://youtu.be/6mzvcPr30kg?t=2600))

Ревюирай първо **public APIs, format/data contracts, compatibility, state ownership, security/cancellation**, които са one-way или скъпи за промяна. После implementation details и най-накрая style/formatting (обикновено автоматизируеми).

Авторът уточнява, че не е задължително да знае всички детайли на всеки decoder наизуст, когато има силни външни доказателства. Това **не означава**, че се отказва от отговорност за shipped code.

Свързано: [AI orchestration and independent review](../../ai/agentic-development/PLANNING-AGENTS-ORCHESTRATION.md) и [AI testing guardrails](../../spring/testing/ai-assisted-testing/03-AI-GUARDRAILS.md).

## 4. Make vs Buy: AWS SDK / SigV4 ([41:10–44:00](https://youtu.be/6mzvcPr30kg?t=2470))

За basic S3 reads Hardwood използва JDK HttpClient и small self-maintained SigV4 signing, проверено с официални test vectors; за advanced AWS roles/SSO/credential providers авторът допуска optional SDK integration. Това намалява mandatory transitive dependencies.

**Не извеждаме правило „пиши сам authentication/crypto с AI“.** Security review, signature canonicalization, new protocol versions, test vectors, credential handling и SDK fallback остават основна цена.

| Алтернатива | Кога да | Кога НЕ | Полза ↔ цена |
| --- | --- | --- | --- |
| Official SDK | Множество credentials/roles/auth features; активно поддържан SDK | Един много тесен use case с неприемлива dependency тежест | Готов lifecycle/compatibility ↔ повече зависимости |
| Custom narrow adapter | Strict narrow API + independent vectors + designated security owner | Нямаме дългосрочен maintenance/testing budget | Малък JAR ↔ security debt |
| Optional SDK integration | Library users имат различни нужди | Optional флагът фактически добавя всичко на всеки | Granular dependency choice ↔ повече конфигурация |

## 5. Провалени/опасни решения, за които да следим

| Описано/изведено от лекцията | Защо е риск и как се разпознава |
| --- | --- |
| Дублирана AI-generated formatting логика (реално наблюдение) | Непоследователен UI/CLI → search existing owner, contract tests |
| Agent изключва failing test (реално наблюдение) | false green → diff review на тестовете, independent oracle |
| Row-group-only parallelism (архитектурен предел) | 1 row group → 1 task → недостатъчно CPU work |
| Безусловен parallel decode (не се препоръчва) | memory/IO pressure → bounded in-flight + CPU budget |
| Aggressive metadata pruning (failure risk) | false negatives при null/unknown stats → fail conservative |
| Синтактичен „fast“ refactor без измерване | JIT може вече да е оптимизирал → JFR, JMH, regression protocol |
| Custom SigV4 без security owner (failure risk) | protocol/security drift → test vectors, explicit SDK fallback |

**Не твърдим production инцидент или data loss при Hardwood — такова нещо не се описва в транскрипта.**

## 6. Recognition / runbook за Java backend

**При тежък file import/analytics job:** установи data format/schema → провери projection/filters → измери CPU/I/O/allocations/waits → сравни task granularity (files/groups/columns/pages) → въведи bounded CPU и buffer limits → пази source order и cancellation → JFR context и controlled benchmark → regression suite.

**При AI-generated library/protocol code:** създай specification-grounded fixtures → сравни поне два независими oracles, когато има смисъл → не разрешавай test weakening → code review на irreversible decisions → проверявай performance data, а не agent description → human approval за release.

**Кога НЕ:** ако готов SDK решава задачата, dataset е малък или няма measured bottleneck; ако memory/I/O overhead от повече tasks е по-голям от печалбата; ако metadata не е достоверна. Разумни алтернативи са parquet-java, Hardwood, DuckDB/Arrow, query pushdown или по-добър storage layout.

## 7. Реално изпълними Education proofs

| Contract | Изходен код | Доказателство |
| --- | --- | --- |
| Bounded page tasks, I/O virtual vs CPU platform threads | [PagePipelineLab](../../java/jvm/performance-evolution/src/main/java/bg/hristomanov/education/performance/hardwood/PagePipelineLab.java) | [PagePipelineLabTest](../../java/jvm/performance-evolution/src/test/java/bg/hristomanov/education/performance/hardwood/PagePipelineLabTest.java) — 1 row group/4 pages, result order, CPU workers, failure handling |
| Correct metadata tri-state | [PagePruningPolicy](../../java/jvm/performance-evolution/src/main/java/bg/hristomanov/education/performance/hardwood/PagePruningPolicy.java) | [PagePruningPolicyTest](../../java/jvm/performance-evolution/src/test/java/bg/hristomanov/education/performance/hardwood/PagePruningPolicyTest.java) — independent fixture scanner, nulls/unknown, range boundaries и Bloom false positive |
| **Integrated range scan** — reference vs sequential vs bounded parallel, primitive `long[]` pages | [PageProcessingLab](../../java/jvm/performance-evolution/src/main/java/bg/hristomanov/education/performance/hardwood/PageProcessingLab.java) | [PageProcessingLabTest](../../java/jvm/performance-evolution/src/test/java/bg/hristomanov/education/performance/hardwood/PageProcessingLabTest.java) — 12 cases: fixed-seed reference parity, inclusive boundaries, untrusted/corrupt metadata, defensive copying, bounded outstanding tasks |
| Runbook и упражнения | [P2 Hardwood Engineering Labs](../../java/jvm/performance-evolution/HARDWOOD-ENGINEERING-LABS.md) | Maven commands and negative cases |

Третият пример е **допълнение**, а не замяна на първите два: `PagePruningPolicy` моделира null counts/Bloom evidence, `PagePipelineLab` моделира отделни I/O и CPU стъпки, а `PageProcessingLab` комбинира range predicate, истинско `long[]` обхождане, тристепенно решение и независим scanner за **един и същ краен брой съвпадения**. Понеже използва само primitive values без nulls, неговото `ALL_MATCH` не може директно да се пренесе върху реални nullable Parquet columns.

Това са **детерминистични teaching contracts**, не истински Parquet parser, JFR benchmark или измерване на ускорение от Hardwood. Няма нов Maven module и нов NotebookLM prompt.

## 8. Senior self-check

1. Защо един row group и две projected columns могат да ограничат паралелизма, въпреки стотици pages?
2. Защо min=10/max=20 при `x > 5` НЕ гарантира `ALWAYS_MATCHES`, ако null count е неизвестен?
3. Каква е разликата между false-positive Bloom и false-negative Bloom?
4. Какво правим, ако DuckDB, parquet-java и нашият reader не се съгласят?
5. Защо 16-core throughput и 1-core baseline трябва да се сравняват поотделно?
6. Agent предлага 2× по-бърз hot loop: коя проверка, профил и benchmark са нужни?
7. При какви условия би избрал SDK пред собствен SigV4 adapter?
8. Какви custom JFR events биха обяснили проблем с page prefetch?

## Original sources

- [Devoxx Hardwood talk](https://www.youtube.com/watch?v=6mzvcPr30kg) — предоставен потребителски транскрипт (~52 мин.).
- [Hardwood source](https://github.com/hardwood-hq/hardwood) · [Hardwood 1.0](https://www.morling.dev/blog/hardwood-1-0-fast-lightweight-apache-parquet-reader-for-the-jvm/).
- [Hardwood author's design](https://www.morling.dev/blog/hardwood-new-parser-for-apache-parquet/) · [Hardwood 1.1 Beta writer](https://www.morling.dev/blog/parquet-file-write-support-bloom-filters-improved-performance-hardwood-1-1-0-beta1/).
- [Apache Parquet format](https://github.com/apache/parquet-format) · [Page Index](https://parquet.apache.org/docs/file-format/pageindex/) · [Bloom contract](https://parquet.apache.org/docs/file-format/bloomfilter/).
