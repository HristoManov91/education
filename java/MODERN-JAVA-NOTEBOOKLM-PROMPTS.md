# NotebookLM — Modern Java in Practice (Devoxx 2026)

**Video:** [Nicolai Parlog & Falk Sippach — Modern Java in Practice: The Features That Matter](https://youtu.be/zC2tbq7S5UE), 2h41. Прочетен е целият транскрипт. Не генерирай едно 2.5-часово общо резюме: подготви тематични **P1 Deep Technical Lectures**, **P2 Focused Lectures** и **P3 справка**.

**Upload sources:** оригиналното YouTube видео; нашите [DOP](language/data-oriented-programming/README.md), [Gatherers](streams/stream-gatherers/README.md), [JVM Performance](jvm/performance-evolution/README.md), [Sequenced Collections](collections/sequenced-collections/README.md), [Loom MDC](concurrency/project-loom/MDC-CONTEXT-PROPAGATION.md), [JFR redaction](jvm/jfr-troubleshooting/JDK-27-JFR-REDACTION.md), [Java Evolution Guide](JAVA-EVOLUTION-GUIDE.md).

**General rules for every audio:** говори на естествен български като Senior Java инженер; утвърдените APIs/термини са на английски. Следвай „реален казус → bad approach → why it fails → good approach → actual tests → trade-offs“. Не диктувай дълги snippets символ по символ. Не смесвай released/stable с preview/EA APIs. Не измисляй benchmark резултати за нашия проект.

## P1 Audio 1 — Data-Oriented Programming (30–40 мин)

```text
Създай пълна задълбочена българска лекция на ниво Mid+ → Early Senior Java за Data-Oriented Programming, използвайки видеото 05:00–36:00 и нашия data-oriented-programming/README.md.

Започни с бизнес случай, където един mutable DosageDTO съдържа флаг infusion плюс tablet counts и може да бъде в противоречиви състояния. Обясни product types vs sum types, records, sealed interfaces, transparent/validated state, compact constructors, Math.addExact overflow, shallow immutability и List.copyOf.

Проследи реалния Java 25 код Dose, Tablets, Infusion, Medication, Prescription и MedicationOperations. Обясни exhaustive switch без default, record patterns, nested patterns, guarded when cases, dominance и null behavior; защо добавянето на нов permitted subtype провокира compiler errors вместо тихи runtime omissions.

Сравни OOP polymorphism/Strategy/Visitor с DOP. Покажи trade-off: стабилен набор от data variants + често нови операции срещу често добавяни polymorphic variants. Добави 5 конкретни инженерни казуса, 2 compile-time review упражнения и 5 интервю въпроса.

Без неверни твърдения, че record е deep immutable, че Java 25 primitive patterns са финални или че всички input constraints се решават от sealed classes.
```

## P1 Audio 2 — Stream Gatherers (25–35 мин)

```text
Създай самостоятелно задълбочено българско обучение за Java 24/25 finalized Stream Gatherers от видеото 2:05–2:13 и нашия Stream Gatherers модул.

Започни с обработка на business event stream: fixed batches, sliding windows, running totals, distinct by key, bounded concurrent remote enrichment. За всяка нужда сравни излишно eager List materialization / ad-hoc loops с междинен gather().

Разгледай Gatherer<T,A,R> четирите functions: initializer, integrator, combiner, finisher. Обясни защо gather е intermediate, а Collector е terminal; как и кога се push-ват 0/1/N елемента, short-circuit и downstream cancellation.

Изследвай Gatherers.windowFixed, windowSliding, scan, fold, mapConcurrent чрез точни очаквани резултати и edge cases. Обясни, че mapConcurrent използва virtual threads, запазва encounter order, но има real downstream limits и cancellation/side effect trade-offs.

Проследи нашия custom distinctBy на Gatherer.ofSequential(HashSet::new,...): защо state трябва да е per stream evaluation, защо static HashSet е грешен, защо combiner не се добавя небрежно, защо parallel() не гарантира parallel evaluation при sequential gatherer.

Дай 5 quiz въпроса и 1 Senior code review challenge за ordering, idempotency и bounded concurrency. Не прави производствени latency твърдения без benchmark.
```

## P1 Audio 3 — JVM Performance / Project Leyden (30–40 мин)

```text
Направи дълбока българска лекция за Java 25 JVM performance, startup/warm-up/throughput/memory/GC, AOT Class Loading & Linking / Method Profiling, Compact Object Headers, G1/ZGC и ограничения при измерване. Използвай видеото 37–47 мин и нашия performance-evolution/README.md.

Започни с хипотетичен Spring Boot microservice, който стартира бавно или има memory pressure. Разграничи startup till readiness, JIT warm-up, steady-state throughput, p95/p99 latency, GC pause, heap/RSS и CPU vs wall-clock.

Обясни Compact Headers Java 25 opt-in, Java 27 default, кои малки objects печелят и кога extra CPU може да изяде ползата. Обясни Project Leyden training run → cache creation → compatible production launch, JDK/OS/classpath constraints, JDK25 -XX:AOTCacheOutput vs -XX:AOTCache.

Разгледай нашите StartupWorkload, AotProcessComparison и BenchmarkStatistics; ясно кажи, че процесните median времена в този lab НЕ са JMH throughput, не са пълна production readiness metric и не са събирани в CI. Покажи правилен baseline/controlled trial/canary подход. Свържи със съществуващия JFR module (method timing, CPU, GC) и формулирай практичен diagnostic runbook.

Накрая 5 interview questions: как AOT може да помогне на startup без throughput gain; защо compact headers не гарантират speedup; как сравняваме G1/ZGC; как се избягват JVM microbenchmarks mistakes.
```

## P2 Audio 4 — Sequenced Collections (15–25 мин)

```text
Създай focused българска Java 25 лекция по Devoxx 1:58–2:05 и нашия Sequenced Collections lab. Обясни SequencedCollection, SequencedSet и SequencedMap vs List/Set/SortedSet. Дай точното значение на encounter order и разграничението му от comparator sort order.

Влез в reversed() като backed view, а не copy; демонстрирай mutation на reversed и промяната в original, после List.copyOf snapshot. Покажи LinkedHashSet addFirst, LinkedHashMap firstEntry/lastEntry, TreeSet addFirst/addLast → UnsupportedOperationException.

Дай 4 кратки самопроверки с exact results и trade-off кога интерфейсът е по-точен от List. Не твърди, че всеки implementation поддържа всички mutators или че API гарантира O(1) операции.
```

## P2/P3 Audio 5 — Modern Java API & Security Evolution (20–25 мин)

```text
Направи сфокусиран български Java version guide за JDK 17 → 21 → 25 → 26 → 27 и proposed JDK 28 въз основа на нашия JAVA-EVOLUTION-GUIDE.md. Фокус: кои APIs са stable в 25, кои са preview в 27 и кои не са GA в 28.

Кратко обясни Java 25 compact source/instance main, source launcher, JDK 26 opt-in HTTP/3 (QUIC/UDP и downgrade), Stable Values/Lazy Constants preview, Post-Quantum TLS hybrid и PEM API preview в 27, JFR in-process redaction (не защитава всички custom events), JavaDoc Markdown и snippets as docs-as-code.

Свържи се със съществуващия Loom курс и новия MDC propagation пример. Не повтаряй целия Virtual Threads/Structured Concurrency материал; кажи кои конкретни нови нюанси са добавени и кога да ги използвам.

Завърши със safety/version checklist: vendor LTS support, preview flag, current JEP, rollback plan, compatibility, actual tests and benchmark. Не представяй preview / Java 28 EA като production stable.
```

## After listening: learning checks

Използвай [education assessment protocol](../docs/learning/ASSESSMENT-PROTOCOL.md): по един реален Java question на микро-тема, професионални корекции на техническия изказ и D+1/D+7/D+30 review. Generated NotebookLM audio **не е автоматично доказателство за усвоен материал**.
