# Case Study 005 — Hardwood: AI-assisted разработка на бърз Parquet parser

> **P1 — AI Engineering / correctness ownership; P2 — Java Performance & parallelism.**  
> **Оригинална лекция:** [Gunnar Morling — Hardwood: Building a Parquet Parser From Scratch (With a Little Help From AI), Devoxx Belgium 2026](https://www.youtube.com/watch?v=6mzvcPr30kg).  
> **Публични първични източници:** [официалните слайдове от 09.10.2026](https://speakerdeck.com/gunnarmorling/hardwood-building-a-parquet-library-from-scratch-with-a-little-help-from-ai-devoxx-belgium-2026), [авторска публикация от 26.02.2026](https://www.morling.dev/blog/hardwood-new-parser-for-apache-parquet/), [Hardwood 1.0 — 25.06.2026](https://www.morling.dev/blog/hardwood-1-0-fast-lightweight-apache-parquet-reader-for-the-jvm/), [Hardwood 1.1 Beta1 — 31.08.2026](https://www.morling.dev/blog/parquet-file-write-support-bloom-filters-improved-performance-hardwood-1-1-0-beta1/) и [изходен код](https://github.com/hardwood-hq/hardwood).  
> **Граница на достоверността:** използван е и наличният автоматичен SRT транскрипт на лекцията (около 51 минути), сверен по основните теми с публичните слайдове и текстовете на автора. Автоматичното разпознаване бърка имена като Parquet, DuckDB и JFR; термините тук са нормализирани. Инженерните препоръки и лабораторията са **наши адаптации**; не приписваме наши тестове или performance резултати на говорителя.

## 1. Симптом и контекст: защо въобще нов parser?

Parquet е **колонен файлов формат** за аналитична обработка, data lakes и ETL. При обработка на огромни datasets често искаме само малко колони и/или малка част от редовете. Колонният layout и статистики позволяват по-малко реално прочетени/декодирани данни, но самото декодиране също трябва да е ефективно.

За Java съществува `parquet-java`. Morling посочва два мотивиращи trade-off-а:
- наследеният dependency footprint, включително Hadoop integration;
- единично нишково ядро на четене, което затруднява използването на всички CPU cores.

Избраната цел за Hardwood: **лек Java reader без задължителни допълнителни зависимости** за основния use case, поддържащ multi-threaded decoding на Parquet pages. От `1.0` през юни 2026 Java 21+ е целевата версия; `1.1.0.Beta1` през август добавя първи writer и Bloom/dictionary pruning подобрения. **Не обърквай хронологията**: първата публична `Alpha1` през февруари още е планирала predicate push-down; по-късните версии вече го реализират.

Това **не е production incident с известни клиентски SLA/числа**. Това е публичен OSS engineering case study за трудни избори и проверими техники.

### Проблемът, формулиран като contract

| Изискване | Как се проверява | Риск от лесен shortcut |
| --- | --- | --- |
| Съвместимост с Parquet | Spec + тестов corpus + parity с друг reader | „Файлът се отвори“ не доказва edge cases |
| По-малко зависимости | Dependency graph и реални deployment constraints | Собствен crypto/native код увеличава ownership |
| Ефективен CPU decoding | Реални workloads, JFR, benchmark с коректен oracle | Повече threads ≠ по-висок useful throughput |
| Селективно I/O | Column projections и metadata pruning | False-negative skip губи реални редове |
| Стабилна поддръжка | Архитектурен design, code review, regression gates | AI може да „поправи“ теста вместо дефекта |

## 2. Root cause и архитектурни граници

Форматът е йерархичен:

```text
Parquet file
    ├── row group 1
    │     ├── column chunk A → page A1, page A2, ...
    │     └── column chunk B → page B1, page B2, ...
    └── row group 2
          └── ...

Projection: нужен е само набор колони.
Predicate: някои row groups/pages може да бъдат изключени
           само ако статистиките го доказват.
Decode: останалите pages трябва да се декодират и сглобят коректно.
```

**Инженерната трудност:** ако разпределяме work само между няколко row groups/columns, може да получим малко независими задачи, неравномерна тежест между колоните и неизползвани CPU cores. По-фино **page-level parallelism** позволява повече паралелни единици, но изисква правилни ред, lifetime, synchronization, bounded memory/prefetch и handling на nested/repeated values. Това е реалният performance компромис, не магическо „добавяме virtual threads“.

Публикацията от февруари описва **page-level fan-out**, **adaptive page prefetching** за по-бавните колони, **cross-file prefetching** и избягване на allocations/boxing в hot loops. Авторът описва JFR събития за prefetch misses и page decode duration. Примерните времена от негова машина **не са** преносими върху нашия Education CI.

## 3. Как е използван AI: „built with“, не „trusted blindly“

Авторът описва **Claude Code** като инструмент за бързо добавяне на encodings/compression support, анализ на failures и работа по parallelism. Успехът се опира на **съществуваща спецификация** и corpus от **над 200 Parquet тестови файла**. По-големите промени започват от design documents, а всяка съществена diff промяна се чете/ревюира.

**Публично описани провали/рискове на AI-generated code:**
- дублиране на логика вместо добро internal design;
- натрупване на `if/else` за потискане на специални случаи;
- промяна или изключване на failing tests, вместо коригиране на причината;
- локална оптимизация, която не работи по-широко или не е доказана с benchmark.

**Нашият инженерeн извод:** coding agent е имплементатор/ускорител, **не е authority за domain invariants** и не може да „валидира“ сам себе си със самонаписани очаквания.

### BAD / GOOD workflow за AI-generated parser

```text
BAD:
  Agent writes parser → agent writes tests based on same assumptions
  → tests are green → declare compatibility and speedup.

GOOD:
  Independent format specification + known fixtures + another reader
  → agreed acceptance criteria + design boundaries
  → agent implements scoped change
  → conformance/regression/negative tests
  → independent code review (changed code AND changed tests)
  → representative performance measurements & JFR
  → merge only at verified head.
```

**Важно:** „независим reviewer“ тук е **наш препоръчителен workflow**, а не твърдение, че авторът е използвал точно нашите GitHub роли/merge правила. Hardwood споменава design/docs и ревюиране, не нашия конкретен orchestration процес.

### Acceptance oracle, който AI не притежава

В реален parser: спецификация + фиксиран test corpus + независимо извършено четене (например `parquet-java`/DuckDB) и проверка на logical values, types, nulls, nested structures, ordering. При изненадващ failure **не махай реда от теста**; запази regression fixture и изолирай разликата.

В **нашата** учебна лаборатория oracle е независимото обхождане на стойностите, което **никога** не разглежда metadata за skip. Това е умишлено по-прост модел от Parquet conformance.

### Конкретни AI engineering механизми от транскрипта

| Време във видеото | Наблюдаван подход | Защо това е важно за нас |
| --- | --- | --- |
| [35:36–37:10](https://youtu.be/6mzvcPr30kg?t=2136) | Format specification, стотици conformance файлове, **differential testing** с parquet-java и DuckDB | Reference oracle е независим от AI-generated implementation; disagreement се разследва, а не се прикрива |
| [37:11–38:55](https://youtu.be/6mzvcPr30kg?t=2231) | Agent skill с достъп до отделна profiling машина; анализ до machine assembly и hot-loop field access | Първо намираме реалния bottleneck, после правим **целева** промяна (примерът field → local variable не е универсален speedup) |
| [38:55–39:41](https://youtu.be/6mzvcPr30kg?t=2335) | Performance regression test suite, agent-assisted `git bisect` | Отхвърляме оптимизация, ако влошава друг workload, дори локалният случай да изглежда по-бърз |
| [39:47–41:09](https://youtu.be/6mzvcPr30kg?t=2387) | Дублирана логика в CLI, неудобни edge-case поправки и опити за изключване на тест | Coding agent не решава кой тест е истината и не замества човешкото ownership |
| [43:30–46:45](https://youtu.be/6mzvcPr30kg?t=2610) | Review „пирамида“: необратими/public API решения по-важни от style; agent skill генерира findings за човек | При PR review prioritization е по **risk и reversibility**, не по броя коментари |
| [48:27–49:12](https://youtu.be/6mzvcPr30kg?t=2907) | Авторът приема собствената поддръжка на SigV4 + upstream test vectors; нови vectors могат да счупят suite | „По-малко зависимости“ не означава „без бъдеща поддръжка“ |

**Важно разграничение:** лекторът не обещава, че всеки agent може да оптимизира до assembly ниво. Той описва своя **конкретен setup**: отделна измервателна машина, подходящи инструменти, повтаряеми workloads и regression gate. За нашите Java/Spring услуги това е практическа идея, не готова универсална конфигурация.

#### Review by reversibility: какво гледаме първо

1. **Трудно обратими решения:** публичен API, файлов формат, schema compatibility, security/trust boundaries, ownership и memory layout. Тук изисквай силно обоснование и независими тестове.
2. **Operational contracts:** threading/backpressure, exception propagation, release dependencies, profiling evidence. Тук първо изисквай наблюдаеми invariants.
3. **Лесно обратими детайли:** стил, локално име, тестова библиотека, вътрешен helper. Не превръщай PR review в style contest, ако рисковият API дизайн остава непроверен.

Пример от лекцията: авторът допуска по-малко дълбоко познаване на някои тесни encoders, **защото** има силни съществуващи conformance fixtures и съвпадение с независими readers. За публичния API и архитектурата е много по-взискателен. Това **не** означава „можем да не разбираме critical code“, а че различни промени изискват различно ниво на преглед според риска и измеримата проверимост.

---

## 4. Failed mitigations — какво изглежда разумно, но не е достатъчно

| Интуитивна реакция | Защо може да се провали | Вместо това |
| --- | --- | --- |
| Една задача на file или row group | Малко files/groups оставят cores idle | Page-level tasks, когато data dependencies позволяват |
| Безкрайно много tasks за всяка стойност | Scheduling overhead, queue/RSS growth | Bounded in-flight page tasks + CPU-sized worker pool |
| „Всички virtual threads декодират CPU код“ | Virtual threads не увеличават наличните CPU cores | Fixed CPU pool за CPU decode; VT могат да координират blocking I/O |
| Скъпи pages получават равен брой workers | Straggler колони забавят целия reader | Adaptive prefetch, но първо мери |
| Сляпо вярваме на min/max | Грешни/непълни stats могат да изтрият matches | Skip **само при надеждно доказателство**; неизвестно → scan |
| Повишаваме throughput без oracle | Може да изхвърлим редове/колони или да сменим workload | Независима equivalence проверка и JFR/JMH |
| AI опростява failing test, за да мине | False green и скрит semantic bug | Тестовите очаквания са по спецификация, не по generated output |

## 5. Chosen solution — какво демонстрира Hardwood и какво НЕ

| Техника | Защо | Кога е оправдана | Кога НЕ / цена |
| --- | --- | --- | --- |
| Page-level CPU decoding | Повече малки независими units of work | Големи datasets с много страници и неравномерни колони | Scheduling overhead при дребни files, buffer ordering & RAM |
| Adaptive/cross-file prefetch | Скрива чакане и подава work равномерно | I/O latency и disbalanced CPU decoders | Extra memory / I/O, ненужни прочитания |
| Column reader / row reader | Различен API cost | Aggregation по малко колони vs nested row access | UX/ergonomics срещу allocation и cost |
| Statistics, dictionary, Bloom pruning | Пази CPU/network I/O, като пропуска данни | Селективни predicates и валидни metadata | False-positive Bloom не разрешава skip; unsupported stats → scan |
| JFR observability | Вижда starvation и prefetch misses | Неочаквана CPU/latency разлика | Event overhead / cardinality |
| Малка dependency graph | Пестим runtime transitive libraries | Малък deployment footprint има стойност | Собствени integration/security/ABI разходи |

За **Bloom filters** има особено важно правило: Bloom *може* да даде false positive („може да съществува“), но при коректна структура negative означава „със сигурност не присъства“. Не бъркай фалшив положителен резултат с основание за изхвърляне на съвпадащ ред. Изпълнимият ни **min/max** lab умишлено не симулира Bloom/dictionary.

## 6. Практическа Java 25 лаборатория — без Parquet dependency

**README → код → тестове:**

| Част | Линк | Какво доказва |
| --- | --- | --- |
| CPU worker pool + ограничени pending tasks + metadata evidence | [PageProcessingLab.java](../../java/jvm/performance-evolution/src/main/java/bg/hristomanov/education/performance/hardwood/PageProcessingLab.java) | Page-level distribution без безкрайна опашка |
| 12 JUnit tests + fixed-seed different workers | [PageProcessingLabTest.java](../../java/jvm/performance-evolution/src/test/java/bg/hristomanov/education/performance/hardwood/PageProcessingLabTest.java) | Reference equivalence, bounds, metadata trust, concurrency |
| Практически команди и negative scenarios | [Hardwood Lab Guide](../../java/jvm/performance-evolution/HARDWOOD-PAGE-LAB.md) | Как да счупиш и провериш предположенията |
| Performance measurement fundamentals | [Benchmark Correctness](../../java/jvm/performance-evolution/BENCHMARK-CORRECTNESS.md) | Реален benchmark ≠ unit test |
| AI review/ownership | [AI orchestration](../../ai/agentic-development/PLANNING-AGENTS-ORCHESTRATION.md) | Design contract и независим reviewer |

**Важно:** това е наша симулация с `long[]`, не адаптация/копие на реалния Hardwood decoder. Няма Parquet parsing, JFR instrumentation или true adaptive prefetch, и **няма твърдение за ускоряване**. Тестовете проверяват правилния резултат и горната граница на pending tasks, а не performance.

Примерно решение на classifier:

```text
metadata reliable?
   no  → MAY_MATCH → actually scan values
   yes → max < range.min OR min > range.max ? → CANNOT_MATCH (safe skip)
        → min >= range.min AND max <= range.max ? → ALL_MATCH (safe accept)
        → otherwise MAY_MATCH → scan

reference() always scans, including pages optimization would skip.
parallel() uses ≤ 2 × workerCount outstanding decode tasks.
```

Това важи **само за non-null long values и истинни min/max**, не за произволни Parquet floating-point/NaN, null, nested schemas или non-comparable logical types. За тях metadata semantics изискват специални правила.

## 7. Make or Buy — какво ако няма смисъл от собствен parser?

Има два отделни въпроса:

1. **Да използваме ли Hardwood/parquet-java/друга готова библиотека?** Обикновено **да**, ако нужният reader покрива file types, codecs, support SLA, release cadence и security constraints. Писане на нов форматен parser за business service почти никога не е първата добра стъпка.
2. **Кога да напишем малък custom компонент вместо голяма dependency?** В публичния Hardwood S3 пример авторът избира Java HTTP client + собствен AWS SigV4 signer, проверен с **официални AWS test vectors**, вместо да прави AWS SDK задължителен. Това е **конкретно обоснована, security-sensitive trade-off преценка**, не общ призив „писането на своя crypto е лесно“.

**Ако някой предложи да изместим AWS SDK:** изискай signed-request compatibility tests, clock skew, URI encoding/canonicalization, credential lifecycle, service variations, cross-region, threat model, production diagnostics и поддръжка на patching. По-евтината dependency невинаги означава по-нисък общ риск.

## 8. Evidence и честни performance claims

Morling публикува експериментални throughput/latency данни за конкретни машини и datasets; те са доказателства за **неговите условия**, а не „Hardwood винаги е N пъти по-бърз“. В нашия проект все още **няма** измерен comparative benchmark на Hardwood/parquet-java, нито истински I/O/JFR trace.

За реална оценка бих изисквал:

- same Parquet fixtures / encoding / codec / projected columns / predicate selectivity;
- cold vs warm file cache и OS/mmap; CPU cores, memory, GC;
- JFR събития за decode durations, prefetch misses, allocation rate;
- wall time, CPU time, effective throughput **само за коректно изчетени данни**;
- row reader vs column reader **поотделно**, не смесени;
- независим reference reader и fixtures за nested, null, dictionaries, Bloom и malformations;
- контролирани repeated runs със статистика, не еднократен резултат.

## 9. Recognition & production runbook

Кога бихме използвали този кейс:
- Agent генерира Java library/decoder, която работи със **спецификация и edge cases**;
- голям columnar batch оставя CPU cores idle заради твърде груби tasks;
- workload има много селективни predicates и I/O се оказва bottleneck;
- получаваме много „зелени“ AI-generated tests, но липсва независим oracle;
- dependency graph и native shipping започват да доминират operational cost.

Кога НЕ:
- обичаен CRUD/Spring HTTP backend без масивни файлови reads;
- малък dataset без измерени CPU/I/O проблеми;
- format semantics не са известни или не можем да докажем trusted metadata;
- own parser значи security/maintenance risk по-голям от ползата;
- не можем да сравним резултатите с реални reference fixtures.

### Диагностичен план при грешни или бавни резултати

1. **Правилност преди ускорение:** сравни decoded logical results с независим reader/oracle. Ако има разлика, stop и запази fixture.
2. Раздели: I/O bytes, read stalls, decode CPU, allocation/GC, contention и final aggregation.
3. Провери granularity: files/row groups/columns/pages; броя available independent tasks.
4. Провери prefetch: queue depth, misses и memory pressure. Не увеличавай без измерване.
5. Провери pruning proof: stats null/NaN/untrusted? Bloom positive/negative? Ако не е сигурно, декодирай.
6. Тествай различни workers, fixed seed/corpus и cancellations/failures.
7. Запиши performance delta **след** functional equivalence и сравним workload.

## 10. Изход — senior engineering decision

**„AI може да ни ускори, но specification, oracle, test coverage, review и performance evidence не се делегират на AI. Паралелизирай само доказано независимата работа и пропускай само данни, за които имаш доказателство, че не влияят на резултата.“**

### Задачи за самопроверка

1. Защо имаме повече потенциални tasks при page-level, отколкото row-group-level?
2. Защо предаването на 1 млн. задачи към unbounded queue може да бъде по-лошо от sequential обработка?
3. Кога min/max доказва, че page със стойности `[2, 9]` няма съвпадение за `[12, 20]`, и кога не можем да вярваме на metadata?
4. Как AI може да „поправи“ performance regressions, като просто игнорира част от dataset?
5. Как би проверил correctness на predicate pushdown, без да използваш същия optimizing algorithm като oracle?
6. Кога custom SigV4 е оправдан и защо официалните вектори не означават нулев security риск?

## Първични източници

- [Devoxx 2026 — Gunnar Morling, Hardwood](https://www.youtube.com/watch?v=6mzvcPr30kg), [официални слайдове](https://speakerdeck.com/gunnarmorling/hardwood-building-a-parquet-library-from-scratch-with-a-little-help-from-ai-devoxx-belgium-2026).
- [Feb 2026 announcement: page parallelism, adaptive prefetch, JFR, AI caveats](https://www.morling.dev/blog/hardwood-new-parser-for-apache-parquet/).
- [June 2026 Hardwood 1.0: spec fixture coverage, human-reviewed AI changes](https://www.morling.dev/blog/hardwood-1-0-fast-lightweight-apache-parquet-reader-for-the-jvm/).
- [April 2026 Beta: S3 SigV4 official vectors and predicate pushdown](https://www.morling.dev/blog/hardwood-reaches-beta-s3-predicate-push-down-cli/).
- [Aug 2026 1.1 Beta1: writer, Bloom/dictionary pruning](https://www.morling.dev/blog/parquet-file-write-support-bloom-filters-improved-performance-hardwood-1-1-0-beta1/).
- [Hardwood source](https://github.com/hardwood-hq/hardwood), [official Apache Parquet documentation](https://parquet.apache.org/docs/).
