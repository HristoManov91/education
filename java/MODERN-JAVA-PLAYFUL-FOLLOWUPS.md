# Devoxx 2026 · Learning Modern Java the Playful Way — само новата стойност

> [Marit van Dijk и Piotr Przybył — оригиналният почти 3-часов deep dive](https://www.youtube.com/watch?v=QBQ11aL2aZc) + предоставен Tactiq транскрипт. **Не е нов 3-часов курс**: фокус само върху пропуските в съществуващото `education`.

## Карта на новите допълнения

| Приоритет | Момент от лекцията | Къде е учебният материал | Вид доказателство |
| --- | --- | --- | --- |
| **P1** | [42:00](https://youtu.be/QBQ11aL2aZc?t=2520) — parallelStream starvation | [ForkJoinPool vs mapConcurrent](streams/stream-gatherers/PARALLEL-STREAM-STARVATION.md) | Java25/JUnit controlled saturation |
| **P1** | [49:45](https://youtu.be/QBQ11aL2aZc?t=2985) — Logpoints/Stream Debugger | [IntelliJ playbook](jvm/jfr-troubleshooting/INTELLIJ-DEBUGGER-PLAYBOOK.md) | Guided IDE walkthrough, не CI lab |
| **P2** | [2:18:00](https://youtu.be/QBQ11aL2aZc?t=8280) — latency/pinning | [Toxiproxy + Cross-JDK probe](../spring/testing/testcontainers/TOXIPROXY-VIRTUAL-THREADS.md) | Docker integration + opt-in 21/25 |
| **P2** | [2:34:00](https://youtu.be/QBQ11aL2aZc?t=9240) — constructors | [JEP 513 Java25](language/data-oriented-programming/FLEXIBLE-CONSTRUCTOR-BODIES.md) | Java25/JUnit |
| **P2** | [1:14:00](https://youtu.be/QBQ11aL2aZc?t=4440) — lazy constants | [JEP531 Java27](experiments/jdk27-lazy-constants/README.md) | Java27 preview self-test, separate workflow |
| **P1/P2** | [1:48:35](https://youtu.be/QBQ11aL2aZc?t=6515) — Simple JSON | [JEP540/JDK28 EA real lab](experiments/jdk28-simple-json/README.md) + [Viktor Klang / Alan Bateman lecture](https://www.youtube.com/watch?v=5y_82lCevHg) | **Runnable JDK28 incubator + Java25/Jackson correctness**, отделен CI, без adoption в production |
| **P3** | [20:50](https://youtu.be/QBQ11aL2aZc?t=1250) — RRF/hybrid search | [RRF algorithm lab](algorithms/04-searching-sorting/HYBRID-RANK-FUSION.md) | Java25/JUnit, без Elasticsearch dependency |

## Съзнателно не дублираме

Records, DOP, Gatherers core API, Structured Concurrency/Joiners, Scoped Values, Virtual Threads, SIMD/FFM, Valhalla, Sequenced Collections, Compact Object Headers, Markdown JavaDoc вече имат отделни учебни материали. Промяната тук е **доказателствена лаборатория, ново edge-case поведение или version-specific корекция**, не още едно API overview.

## Защо тази класификация

- P1 темите влияят пряко на ежедневното разследване на bugs и избора на backend concurrency механизъм.
- P2 Java25/27/21→25 са конкретни version/lifecycle рискове; включват тестове и честно разделят compilation support от runtime behavior.
- JEP540 вече има [P1/P2 executable EA лаб](experiments/jdk28-simple-json/README.md), но **не е production feature** и не налага JSON dependency в Spring Boot. Hybrid search остава P3.

**Прилагане:** използвай следния ред при 20–30 минутна сесия: реален казус → хипотеза → bad/good → тест → trade-offs → кога не се използва → 2 recall въпроса. Няма автоматични оценки за усвояване, преди да бъдат решени задачи.

## Оригинални източници

- [Devoxx 2026 talk](https://www.youtube.com/watch?v=QBQ11aL2aZc).
- [Speaker slides](https://speakerdeck.com/mlvandijk/learning-modern-java-the-playful-way-devoxx-deep-dive).
- [DeepDiveJava25](https://github.com/pioorg/DeepDiveJava25), [DeepDiveJava27](https://github.com/pioorg/DeepDiveJava27), [DeepDiveJava28](https://github.com/pioorg/DeepDiveJava28).
- [JDK release JEPs index](https://openjdk.org/jeps/0).
