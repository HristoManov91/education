# Roadmap — P0 foundations → Mid+ → Early Senior

**Последно структуриране:** 2026-10-08. Планът е **първоначален**, а не оценка за реалното ниво. Учебният ред се променя според резултатите. „Непроверено“ не значи „не го знам“!

## Приоритети и правило за избор

| Приоритет | Област | Защо |
| --- | --- | --- |
| **P0** | Java Fundamentals → Collections → Concurrency → JVM | Базови езикови механизми, correctness и runtime model |
| **P0** | SQL / Oracle / PostgreSQL / Persistence | Queries, транзакции, индекси, Hibernate/QueryDSL |
| **P0** | Spring Boot Core / Web / Security | IoC, AOP/proxies, transaction boundaries, auth flows |
| **P0** | Testing & Reliability | Unit/integration, Testcontainers, retries, idempotency |
| **P1** | System Design & Distributed Systems | Базови distributed guarantees, consistency, messaging, architectural trade-offs |
| **P1** | Performance & Observability | JFR, metrics/traces/logs, profiling и debugging |
| **P1** | Algorithms & Data Structures | Основи, след това по-дълбоки задачи и общи тестове |
| **P1** | Caching / Redis | Eviction, consistency, distributed caching |
| **P1** | Containers / Kubernetes / CI / Testcontainers (advanced) | [Kubernetes for Java lab](../../infrastructure/kubernetes/README.md): Pod, Service, probes, rollout, security, resource limits; [image hardening](../../spring/testing/testcontainers/JAVA-IMAGE-HARDENING.md) и JVM в container |
| **P1** | AI-assisted Engineering | Безопасна, проверима и възпроизводима AI работа |
| **P2** | Design Patterns / Advanced Architecture | Усъвършенстване след ключовите основи |

`P0` е фокусът, **не** ангажимент да приключим всички P0 теми строго последователно. Вътре в P0 започваме от базовите зависимости и можем да редуваме Java/SQL/Spring/Testing за разнообразие, стига да не прескачаме фундамент.

## Формат на микро-тема

- **5–10 минути** диагностика, ако имаме стабилно разбиране; иначе **20–30 минути** обща работна сесия.
- Малка концепция и ясна граница: по-добре две микро-теми по 15 мин, отколкото едно общо видео 2 часа.
- Всеки идентификатор е **стабилен**: оценките и повторенията се записват срещу него.
- **Jr / Mid / Senior** са нива на *въпроса*, не етикети на човека.
- При 4–8 свързани микро-теми: комбиниран checkpoint; в края на голяма група — практически сценарий.
- Паралелните големи материали от NotebookLM могат да са допълнение, но не заменят диагностиката.
- Когато дадена малка тема стане прекалено широка, я разделяме (например `JAVA-COL-05a`, `JAVA-COL-05b`) без загуба на резултатите.

## P0-A — Java Core: старт от fundamentals

| ID | Микро-тема | Проверяваме |
| --- | --- | --- |
| JAVA-FND-01 | Primitive values vs object references; aliasing; mutation | Копие на стойност, споделен mutable object |
| JAVA-FND-02 | Method arguments & reassignment | Променя ли caller-а, какво се копира |
| JAVA-FND-03 | `null`, `==`, `equals()` | Reference identity vs logical equality |
| JAVA-FND-04 | `String` immutability, concatenation, `StringBuilder` | Mutability/immutability, резултат на код |
| JAVA-FND-05 | Numeric conversions, overflow, autoboxing | Безопасност на типовете и изненади |
| JAVA-OOP-01 | Classes, objects, constructors, access modifiers | Encapsulation, initialization |
| JAVA-OOP-02 | Inheritance, overriding/overloading, polymorphism | Runtime vs compile-time dispatch |
| JAVA-OOP-03 | Interfaces, abstract classes, records | Contracts и подходящи случаи |
| JAVA-EXC-01 | Checked vs unchecked exceptions | Propagation, contract, handling |
| JAVA-EXC-02 | `try-with-resources`, suppression, cleanup | Resource ownership |
| JAVA-COL-01 | `Iterable`, `Collection`, `List`, `Set`, `Map` | Collections hierarchy/contract |
| JAVA-COL-02 | `ArrayList` | Access, insert, memory/trade-offs |
| JAVA-COL-03 | `LinkedList` и сравнение с `ArrayList` | Избор по workload, не по митове |
| JAVA-COL-04 | `HashSet`, `LinkedHashSet`, `TreeSet` | Unique/order/sorting |
| JAVA-COL-05 | `HashMap`, `LinkedHashMap`, `TreeMap` | Keys, lookup/order/sorting |
| JAVA-COL-06 | `equals()`/`hashCode()` contract | Consistent equality, hashing, mutable keys |
| JAVA-COL-07 | `Comparable`/`Comparator` | Ordering, consistency |
| JAVA-COL-08 | Iterators; fail-fast; modification | Safe iteration and behavior |
| JAVA-GEN-01 | Generics, invariance, wildcards `? extends/super` | Type safety, PECS |
| JAVA-GEN-02 | Type erasure; limitations | Runtime generics model |
| JAVA-STM-01 | Streams: lazy ops, terminal ops, side effects | Evaluation model |
| JAVA-STM-02 | Collectors, parallel streams, `Optional` | Safe APIs/trade-offs |
| JAVA-CON-01 | Processes vs threads; `Thread`, `Runnable`, `join` | Lifecycle fundamentals |
| JAVA-CON-02 | Shared state; race conditions | Lost updates, race reproduction |
| JAVA-CON-03 | Visibility, `volatile`, locks, atomics | Happens-before and limits |
| JAVA-CON-04 | Executors, pools, futures, cancellation | Resource ownership, error handling |
| JAVA-CON-05 | Virtual Threads: blocking/limits/carriers | Platform-vs-virtual model |
| JAVA-CON-06 | Structured Concurrency & interruption | Scoped lifetime and cancellation |
| JAVA-JVM-01 | JVM execution, heap, stack, references, GC intro | Model without implementation myths |
| JAVA-JVM-02 | Classloading, initialization, memory basics | Lifetime and linkage |
| JAVA-JVM-03 | GC symptoms, memory/CPU diagnostics intro | From symptom to evidence |
| JAVA-JVM-04 | JFR and production troubleshooting | Event-based diagnosis |
| JAVA-JVM-05 | [Java Agents, Byte Buddy и OTel](../../java/jvm/java-agent-instrumentation/README.md) | premain, bytecode proof, business invariants и overhead |
| JAVA-JVM-06 | [JDK26/27 GC upgrade](../../java/jvm/performance-evolution/JDK26-27-GC-UPGRADE.md) | defaults, JEP522 write barriers, checksum + flags + GC logs |
| JAVA-JVM-07 | [Map](../../java/algorithms/03-hashing/JDK26-27-MAP-PERFORMANCE.md) / [C2](../../java/jvm/vector-api/JDK27-SUBWORD-AUTO-VECTORIZATION.md) / [FFM](../../java/jvm/native-interoperability/JDK28-CONFINED-ARENA-POOLS.md) / [Loom](../../java/concurrency/project-loom/JDK26-CLASS-INITIALIZATION-WAIT.md) | Java25 functional proof и JMH без фиксирани performance твърдения |
| JAVA-JVM-08 | **P1** [Method Handles / Reflection / native linkage](../../java/jvm/method-handles/README.md) + [P2 marshalling prototype](../../java/jvm/method-handles/MARSHALLING-PROTOTYPE.md) | Lookup/MethodType, invokeExact return type, capability security, JEP416, StringConcatFactory, FFM link-once, allowlisted V1/V2 и JMH |
| JAVA-JVM-09 | **P1** [JPMS: modules / exports / opens](../../java/jvm/jpms-encapsulation/README.md) | real module-path, allowed framework reflection, forbidden Java consumer access, explicit --add-opens |
| JAVA-JVM-10 | **P1** [Safe JDK upgrade: jdeprscan / jdeps](../../java/jvm/jdk-upgrade-tooling/README.md) | deprecated vs for-removal, compiled bytecode analysis, old vs new API parity |
| JAVA-JVM-11 | **P2** [JDK28 EA adaptive ZGC](../../java/jvm/performance-evolution/JDK28-ZGC-ADAPTIVE-HEAP.md) | manual JVM flag probe, no Java25 dependency, unchanged checksum, representative metrics |

**Комбинирани тестове:** JAVA-FND (след FND-05), JAVA-OOP/EXC, JAVA-COL (след COL-04/08), JAVA-GEN/STM, JAVA-CON (след CON-04/06), JAVA-JVM. Финален: Java Core troubleshooting + explanation challenge.

**Налични лаборатории:** [Project Loom](../../java/concurrency/project-loom/README.md), [JFR](../../java/jvm/jfr-troubleshooting/README.md), [Algorithms roadmap](../../java/algorithms/README.md). Липсата на лаборатория за първите микро-теми **не пречи** на кратка диагностика в чата.

## P0-B — Databases & Persistence

| ID | Микро-тема |
| --- | --- |
| DB-01 | Relational tables, primary/foreign keys, constraints |
| DB-02 | SQL SELECT / WHERE / NULL / three-valued logic |
| DB-03 | INNER/LEFT JOIN и cardinality |
| DB-04 | GROUP BY / HAVING / aggregation и дублиране при joins |
| DB-05 | Subqueries, CTE, window functions — поотделно при нужда |
| DB-06 | B-tree indexes, composite indexes, selectivity |
| DB-07 | Execution plan, statistics, actual vs estimated rows |
| DB-08 | ACID, isolation levels, anomalies |
| DB-09 | Row locks, deadlocks, optimistic vs pessimistic locking |
| DB-10 | Transactions, commits, rollbacks, boundaries |
| DB-11 | JDBC connections, pools and batching |
| DB-12 | JPA entity states & persistence context |
| DB-13 | JPA flush, dirty checking, lazy/eager loading |
| DB-14 | N+1, fetch joins/entity graphs и pagination |
| DB-15 | QueryDSL dynamic queries and performance |
| DB-16 | Oracle vs PostgreSQL specifics (plans, syntax, locks) |

**Комбинирани:** SQL Core (01–05), Index/Plans (06–07), Transactions (08–11), ORM (12–16); финален диагностика на slow query + transaction bug.

**VM-15 Spring transaction boundaries:** [REQUIRED/REQUIRES_NEW/NESTED, self-invocation и rollback rules](../../persistence/repository-unit-of-work/VM-15-SPRING-TRANSACTION-PROPAGATION.md) · [H2 + Spring proxy lab](../../persistence/repository-unit-of-work/src/test/java/bg/hristomanov/education/uow/VladSpringTransactionsTest.java). Няма автоматично доказване на Oracle/PostgreSQL MVCC от H2 tests.

**VM-11–VM-14, VM-16–VM-17 — Hibernate performance and repository querying:** [изпълним модул, код и тестове](../../persistence/hibernate-performance/README.md) · [QueryDSL/Specification page/count lab](../../persistence/specification-querydsl/VM-17-PAGE-SLICE-COUNT.md). Статус `implementation-in-review` до зелен CI, без автоматично `learned`.

**VM-20 — SQL dialect correctness:** [Oracle 19c ↔ PostgreSQL 17 DB comparison](../../database/sql-labs/docs/VM-20-POSTGRESQL-ORACLE-COMPARISON.md), actual PostgreSQL tests + ръчни Oracle SQL scripts (не са автоматично Oracle-validated).

**VM-26 — bounded DB backpressure:** [Hikari admission gate, real pool metrics и PostgreSQL read-only correctness](../../database/transaction-labs/VM-26-POOL-BACKPRESSURE.md); положителни/negative tests и отделен PostgreSQL profile. Не е готова схема за глобален multi-pod pool sizing.

**VM-05–VM-10 — transactions + Hibernate foundations:** [connection pool/MVCC/locking real PostgreSQL lab](../../database/transaction-labs/README.md) · [entity lifecycle](../../persistence/repository-unit-of-work/VM-08-ENTITY-LIFECYCLE.md) · [dirty checking/flush](../../persistence/repository-unit-of-work/VM-09-FLUSH-DIRTY-CHECKING.md) · [associations/orphan removal](../../persistence/repository-unit-of-work/VM-10-MAPPINGS-CASCADE-ORPHAN.md). Материалите не означават автоматично проверени знания.

**VM-01–VM-04 практически SQL блок:** [JDBC + H2 correctness + PostgreSQL Testcontainers + Oracle manual scripts](../../database/sql-labs/README.md) и [упражнения](../../database/sql-labs/EXERCISES.md). Кодът е в PR; за статус **verified** изискваме зелен профил за истинска база.

**Налични лаборатории:** [Repository & Unit of Work](../../persistence/repository-unit-of-work/README.md), [Specification + QueryDSL](../../persistence/specification-querydsl/README.md).

**Vlad Mihalcea системна програма:** [източници и подход](../sources/vlad-mihalcea/README.md), [30 теми с пропуски, зависимости и Definition of Done](../sources/vlad-mihalcea/TOPIC-MAP.md). Статусът на темите е начален одит, не автоматично доказани знания.

**P1 Devoxx практическо надграждане (урок, а не нов Maven module):** [When ORM Becomes OMG — connection pools, transaction boundaries, OSIV, lazy connection fetching, N+1 и DTO projections](../../persistence/jpa-performance/README.md), [лаборатории](../../persistence/jpa-performance/EXERCISES.md). Покрива `DB-10`–`DB-14` и `SPR-04`; не означава автоматично усвоени знания.

**Съпътстващ production казус (не е лаборатория):** [Case Study 002 — Commerzbank, 11 TB Exadata cold cache и read/write trade-offs](../case-studies/002-commerzbank-exadata-performance-incident.md) — прилагай при DB-05/06/07/16, REL-01/05 и P1 System Design.

## P0-C — Spring Boot, Web & Security

| ID | Микро-тема |
| --- | --- |
| SPR-01 | IoC, dependency injection, bean definitions |
| SPR-02 | Bean scopes, lifecycle and initialization |
| SPR-03 | Spring proxies/AOP and self-invocation |
| SPR-04 | `@Transactional` boundaries/propagation/rollback |
| SPR-05 | Configuration, properties, profiles & validation |
| SPR-06 | HTTP fundamentals, headers, status codes |
| SPR-07 | MVC request flow, controllers, validation |
| SPR-08 | Error handling, filters/interceptors, servlet context |
| SPR-09 | WebFlux/Reactor: reactive pipeline and boundaries |
| SPR-10 | Authentication vs authorization; sessions/cookies |
| SPR-11 | CSRF, CORS, SameSite and browser security |
| SPR-12 | OAuth2/OIDC flows, tokens and Spring Security filter chain |

**Комбинирани:** Core/Transactions (01–05), Web (06–09), Security (10–12); финален analysis на request/auth/transaction flow.

**Налични лаборатории:** [Loom vs Reactive](../../spring/concurrency/loom-vs-reactive/README.md), [Hexagonal Architecture](../../spring/architecture/hexagonal-architecture/README.md).

## P0-D — Testing & Reliability

| ID | Микро-тема |
| --- | --- |
| TEST-01 | Unit vs integration vs contract/E2E tests |
| TEST-02 | JUnit assertions, lifecycle and parameterized tests |
| TEST-03 | Mockito stubbing, verifications, boundaries |
| TEST-04 | Spring Boot slices, @SpringBootTest and test contexts |
| TEST-05 | Testcontainers and deterministic database tests |
| TEST-06 | Test data, isolation, flaky tests, CI feedback |
| REL-01 | Timeouts, retries, backoff, retry budget |
| REL-02 | Idempotency, duplicate messages and exactly-once myths |
| REL-03 | Circuit breaker, bulkhead, fallback |
| REL-04 | Transactional Outbox & consumer deduplication |
| REL-05 | Observability, error budgets and debugging basics |

**Комбинирани:** Testing (01–06), Reliability (REL-01–05), финален failure-recovery scenario.

**Налични лаборатории:** [Testcontainers](../../spring/testing/testcontainers/README.md), [Idempotency](../../spring/reliability/idempotency/README.md), [Fault tolerance](../../spring/reliability/fault-tolerance/README.md), [Outbox/Inbox](../../spring/messaging/outbox-inbox/README.md).

**P1 production reliability (Devoxx 2026):** [Their API Went Down — четири изпълними external-API incident labs](../../spring/reliability/fault-tolerance/PRODUCTION-API-RESILIENCE-P1.md). Надгражда REL-01/REL-03 с shared Redis 429 cooldown, durable sync checkpoint, queue expiry и manual credential recovery; не променя оценките на изучените концепции.

## P1 — System Design / Strategic DDD & Context Mapping

**Материал:** [Strategic DDD & Microservice Boundaries — Deep Technical Lecture + executable architecture labs](../../spring/architecture/strategic-ddd/README.md). Основен източник: Devoxx Belgium 2026 (Steven Hillaert), приоритет **Tech Radar P1**.

| ID | Микро-тема | Проверяваме |
| --- | --- | --- |
| SYS-DDD-01 | Ownership, subdomains, bounded contexts vs deployment/team boundaries | Кой притежава decision/data/model? Кога 2 BC = 1 deployable? |
| SYS-DDD-02 | Context Mapping — upstream/downstream vs HTTP call direction | Чий success и модел зависи от кого, Conformist vs Customer–Supplier |
| SYS-DDD-03 | Anti-Corruption Layer, Shared Kernel, integration decisions | Собствен downstream model, mapping semantics, conscious coupling |
| SYS-DDD-04 | Modular monolith, microservices, runtime calls vs local projections | Latency/freshness/consistency/risk trade-off |
| SYS-DDD-05 | Hands-on code review и архитектурна диагностика | Maven/ArchUnit boundaries, stale projections, реални dependency changes |
| SYS-DDD-06 | [EventStorming — business boundaries discovery](../../spring/architecture/strategic-ddd/04-EVENTSTORMING-WORKSHOP.md) | Timeline, domain facts, actors, pivotal events, hotspots и boundary hypotheses |
| SYS-DDD-07 | [Spring Modulith — package contracts & module tests](../../spring/architecture/strategic-ddd/05-SPRING-MODULITH.md) | @NamedInterface, allowedDependencies, ApplicationModules.verify, @ApplicationModuleTest |
| SYS-DDD-08 | [Case Study 004 — Port of Rotterdam](../case-studies/004-port-of-rotterdam-modular-monolith.md) | Migration by capability, trade-offs, legacy coexistence, measured vs hypothesized benefits |

**Комбинирана проверка:** SYS-DDD-01–05 (архитектура на leasing платформа), след което SYS-DDD-06–08 (port clearance EventStorming, Spring Modulith code и migration critique). Първо изучаване и CI на модул **не са оценка за усвоено знание**; оценяваме само след реални отговори на диагностика.

---

## P1 — Spring Boot Testing in the AI Era (Devoxx 2026)

**Материал:** [P1 Deep Technical Lecture + executable unit/MVC/HTTP tests](../../spring/testing/ai-assisted-testing/README.md). Налага се от дългата лекция на Philip Riecks и допълва фундаменталните TEST-01–TEST-06; **не е оценено като усвоено**, докато не минем диагностични въпроси.

| ID | Микро-тема | Какво доказваме |
| --- | --- | --- |
| TEST-AI-01 | Unit vs Spring MVC slice vs real HTTP | Correct failure boundary и Spring Boot 4 API |
| TEST-AI-02 | Spring TestContext caching и cache keys | Profile/properties/mocks, forks, @DirtiesContext |
| TEST-AI-03 | Surefire/Failsafe, Testcontainers, isolation и CI | Unit/IT separation, reliable feedback, parallel safety |
| TEST-AI-04 | Independent AI test oracle, mutation basics | Boundary/negative tests, no green-by-cheating, coverage ≠ correctness |
| TEST-AI-05 | Versioned agent skills + production readiness | Skills, test review, CI evidence, rollback |
| TEST-AI-06 | [Mutation testing: survivor triage & meaningful code](../../spring/testing/ai-assisted-testing/05-MUTATION-TESTING-DEEP-DIVE.md) (Devoxx, Dave Aronson) | PIT bytecode, KILLED/SURVIVED/NO_COVERAGE, equivalent mutants, boundary/partial writes, redundant-code hypothesis, cost/CI gates |
| TEST-AI-07 | [Case Study 005 — Hardwood AI engineering](../case-studies/005-hardwood-ai-parquet-engineering.md) · [Integrated Java25 page scan lab](../../java/jvm/performance-evolution/HARDWOOD-ENGINEERING-LABS.md#51-integrated-experiment-c--доказваме-един-и-същ-резултат-по-три-пътя) | Specification-derived fixtures, differential oracles, 12 JUnit integration-style parity/boundary tests, metadata-trust negative proof, AI test weakening, irreversible APIs and review priority |

**Практичен checkpoint:** RegistrationPolicy, MockMvc 400/401/403, HTTP auth и TestContext cache reuse. За `TEST-AI-06`: сравни `age < 18` с `age <= 18` и изследвай [ChunkedSender / ChunkedSenderTest](../../spring/testing/ai-assisted-testing/04-PRACTICAL-LABS.md) при partial writes; обясни кога `SURVIVED` означава липсващ сценарий и кога има основание за опростяване на production code. **PIT е opt-in**; не записвай score или лично усвояване без реални тестове и диагностика.

---

## P1/P2 — Modern Java in Practice (Devoxx Belgium 2026)

**Навигация:** [Java Evolution Guide (17–28)](../../java/JAVA-EVOLUTION-GUIDE.md) · [NotebookLM P1/P2/P3 prompts](../../java/MODERN-JAVA-NOTEBOOKLM-PROMPTS.md). Java 25 LTS остава базовата версия за изпълнимите упражнения. Наличието на Maven tests **не е лично доказателство за усвоени знания**.

| ID | Приоритет | Микро-тема | Проверяваме |
| --- | --- | --- | --- |
| JAVA-MOD-01 | P1 | [Data-Oriented Programming](../../java/language/data-oriented-programming/README.md): records, sealed sum types, exhaustive switch | Modeling invalid states, constructors, null, guards и overflow |
| JAVA-MOD-07 | P1 | [Valhalla — Value Objects & JDK 28 EA migration](../../java/language/data-oriented-programming/VALHALLA-VALUE-OBJECTS.md) | identity vs statewise ==/equals, value-based 30 JDK classes, weak refs, record constructors, Java 25/JUnit + EA dual-mode proof |
| JAVA-MOD-08 | P2 | [Leyden AOT code compilation — JDK 28](../../java/jvm/performance-evolution/LEYDEN-JDK28-AOT-CODE.md) | Native code vs profiles, same CPU features/GC, training correctness and separate startup/warm-up measurements |
| JAVA-MOD-09 | P2 | [Amber — future design](../../java/language/data-oriented-programming/AMBER-FUTURE-DESIGN.md) | Primitive patterns, withers, deconstructible classes: delivered vs proposed; manual Java 25 solution |
| JAVA-MOD-10 | P3 | [Babylon / Detroit — Java Next Tech Radar](../../java/JAVA-NEXT-TECH-RADAR.md) | When to monitor vs adopt; no lab without concrete backend workload |
| JAVA-MOD-11 | P2 | [FFM API & Java Native Interoperability](../../java/jvm/native-interoperability/README.md) | Real C downcall, ABI and size_t, explicit Arena lifetime, bounds/thread safety, Java 25 JUnit evidence |
| JAVA-MOD-12 | P2 optional | [jextract — generated bindings](../../java/jvm/native-interoperability/JEXTRACT-LAB.md) | C headers → source generation → local native lib → smoke run; toolchain compatibility and limitations |
| JAVA-MOD-13 | P3 | [Babylon Code Reflection → ONNX graph case study](../../java/JAVA-NEXT-TECH-RADAR.md) | @Reflect/FuncOp, graph translation vs FFM downcall, Protobuf/ONNX boundaries; no unsupported stable API claims |
| JAVA-MOD-02 | P1 | [Stream Gatherers](../../java/streams/stream-gatherers/README.md): windowing, scan/fold, custom distinctBy | Intermediate vs terminal, mutable state, encounter order, concurrency |
| JAVA-MOD-03 | P1 | [Project Leyden / JVM Performance](../../java/jvm/performance-evolution/README.md) | Startup vs warm-up vs throughput, JDK25 AOT & compact headers with honest measurement |
| JAVA-PERF-01 | P1 | [Benchmark Correctness & Performance Measurement](../../java/jvm/performance-evolution/BENCHMARK-CORRECTNESS.md) | Reproducibility / Realism / Relevance; open vs closed; coordinated omission, USE/RED, successful throughput, independent bottleneck evidence |
| JAVA-PERF-02 | P2 | [Hardwood page scheduling & predicate-pruning labs](../../java/jvm/performance-evolution/HARDWOOD-ENGINEERING-LABS.md) | Bounded virtual I/O vs fixed CPU, stable result order, null/unknown metadata, conservative Bloom semantics and independent JUnit oracle |
| JAVA-MOD-04 | P2 | [Sequenced Collections](../../java/collections/sequenced-collections/README.md) | Reversed backed view vs snapshot, supported/unsupported mutators |
| JAVA-MOD-05 | P2 | [MDC/ThreadLocal + Virtual Threads](../../java/concurrency/project-loom/MDC-CONTEXT-PROPAGATION.md) | Context snapshot per task, cleanup и ScopedValue trade-offs |
| JAVA-MOD-06 | P2 | [JFR JDK27 sensitive-data redaction](../../java/jvm/jfr-troubleshooting/JDK-27-JFR-REDACTION.md) | Preview/version boundary, security risk in recordings |
| JAVA-MOD-07 | P2/P3 | Java 26/27/28 API evolution | HTTP/3, Stable Values preview, PQ TLS/PEM, JavaDoc, compatibility |

**Практичен checkpoint:** Model `Dose` alternatives, write an exhaustive switch, compose two gatherers, explain a reversed backed view, and reason about AOT experiment validity. Задачи и оценки се добавят само след реален диагностичен разговор.

---

## P2 — Snake in 10 Lines: Constraint-driven Design (Devoxx 2026)

**Материал:** [Snake in 10 Lines — Java 25 mini lab](../../java/algorithms/01-algorithmic-thinking-complexity/SNAKE-CONSTRAINT-DRIVEN-DESIGN.md), оригинал: [Guus de Wit, Devoxx 2026](https://www.youtube.com/watch?v=LD2tKMpToYg) + предоставен auto-generated SRT (~48:28). Само разширение на съществуващия Java Algorithms Module 01, без нов Maven модул или framework.

| ID | Приоритет | Микро-тема | Проверка |
| --- | --- | --- | --- |
| ALG-SNAKE-01 | P2 | Minimal specification vs implicit scope | Шест думи → explicit rules, кога ги допълваме с invariants |
| ALG-SNAKE-02 | P2 | Move head/tail, 2D→1D и boundaries | Deque/Set trade-offs, illegal row wrap, vacating tail |
| ALG-SNAKE-03 | P2 | Bounded food generation, terminal state | Win/full board, reverse и collision negative JUnit |
| ALG-SNAKE-04 | P2 | Refactoring correctness / independent oracle | Сравнение на 300 детерминистични 2D и 1D сценария |

**Proof:** [SnakeGameTest.java](../../java/algorithms/01-algorithmic-thinking-complexity/src/test/java/bg/hristomanov/education/algorithms/complexity/snake/SnakeGameTest.java) + [SnakeReferenceParityTest.java](../../java/algorithms/01-algorithmic-thinking-complexity/src/test/java/bg/hristomanov/education/algorithms/complexity/snake/SnakeReferenceParityTest.java), [NotebookLM prompt](../../java/algorithms/01-algorithmic-thinking-complexity/SNAKE-NOTEBOOKLM-PROMPT.md). Не маркирай усвоено преди решени упражнения и тестове.

---

## P1/P2 — Quarkus Build-time Metaprogramming и Jackson/JIT regression (Devoxx 2026)

**Материали:** [P2 Quarkus build-time extension / Jandex / Gizmo](../../spring/framework-internals/quarkus-build-time/README.md) с Quarkus 3.40.1.2 LTS HTTP integration test и [P1 Jackson/JIT investigation](../../java/jvm/performance-evolution/QUARKUS-JACKSON-JIT-REGRESSION.md) с Java25 JUnit/JMH dispatch модел. Източници: [Devoxx видео](https://www.youtube.com/watch?v=SgK0zMNayq0), SRT (~2:28) и [оригиналните слайдове](https://speakerdeck.com/mariofusco/the-quarkus-advantage-turning-build-time-metaprogramming-into-runtime-performances).

| ID | Приоритет | Микро-тема | Проверяваме |
| --- | --- | --- | --- |
| FRAME-QUARK-01 | P2 | Jandex / BuildStep / BuildItem | Build-time annotation index, DAG ordering, immutable handoff |
| FRAME-QUARK-02 | P2 | Gizmo GeneratedBean / CDI | Реална Quarkus augmentation + HTTP positive/metadata negative tests |
| JVM-JIT-QUARK-01 | P1 | Polymorphism / preclassified dispatch | Runtime instanceof vs cached kind, extension fallback parity |
| JVM-JIT-QUARK-02 | P1 | Jackson 3 / JIT regression | Inlining, spill, profile pollution, unrolling, benchmark limitations |

**Проверките** са отделни: root Java25 Maven изпълнява dispatch tests, path-filtered Quarkus job изпълнява реалната extension интеграция. Новото Quarkus обучение НЕ влиза в Spring Boot root reactor, а JMH се изпълнява ръчно (CI само компилира/list-ва harness). Не записвай „усвоено“ без отговори от [упражненията](../../spring/framework-internals/quarkus-build-time/EXERCISES.md).

---

## P1/P2 — The Year of the Runtime: AI, evolvability и Java25 JFR (Devoxx 2026)

**Основен източник:** [Ron Pressler — The Year of the Runtime](https://www.youtube.com/watch?v=tle2mA7EwWU), предоставен SRT (~14:49). **P1:** [Runtime-first Java in the AI era](../../java/jvm/performance-evolution/RUNTIME-FIRST-AI-ERA.md) — correctness ≠ evolvability, JVM JIT/GC/Leyden/jlink/security trade-offs и AI review. **P2:** [Real Java25 JFR @Contextual лаборатория](../../java/jvm/jfr-troubleshooting/AI-ASSISTED-JFR-INVESTIGATION.md) — локално recording, JUnit event metadata, same-thread nesting, synthetic allowed ID и безопасен агрегат. [NotebookLM audio prompt](../../java/jvm/performance-evolution/YEAR-OF-RUNTIME-NOTEBOOKLM-PROMPT.md).

| ID | Приоритет | Микро-тема | Проверяваме |
| --- | --- | --- | --- |
| JVM-RUNTIME-01 | P1 | AI correctness срещу evolvability | Locality of changes, invariants, runtime metrics, security, CI |
| JVM-RUNTIME-02 | P1 | JIT/GC/Leyden/JDK upgrade в реални граници | Java25 vs JDK28 EA, performance evidence, no invented speedup |
| JFR-CONTEXT-01 | P2 | Java25 `@Contextual` metadata | Real RecordingFile; event interval и same-thread ownership |
| JFR-AI-01 | P2 | Evidence-only AI triage | Allowlist summary, no raw trace ID, hypotheses, counter-tests, privacy |

**Граница:** не създаваме нов Maven reactor; кодът живее в съществуващия JFR module. След успешни тестове следва самостоятелно решаване на въпросите, **не** автоматична оценка като усвоено.

---

## P1/P2/P3 — Valhalla nullable flattening, class-file hints и null future-watch (Devoxx 2026)

**Източник:** [José Paumard и Rémi Forax — Will Valhalla Fix Tony's Billion Dollar Mistake?](https://www.youtube.com/watch?v=vLDbzmiT7Xs), предоставен SRT (~2:40:57). Не създаваме нов курс: разширяваме [P1 Value Objects](../../java/language/data-oriented-programming/VALHALLA-VALUE-OBJECTS.md) и [JDK28 EA compatibility lab](../../java/language/data-oriented-programming/JDK28-EA-LAB.md), добавяме [P3 future-watch](../../java/JAVA-NEXT-TECH-RADAR.md).

| ID | Приоритет | Микро-тема | Доказателство |
| --- | --- | --- | --- |
| VAL-NULL-01 | P1 | Nullable marker + atomicity при heap flattening | 32-bit compressed ref + short/int + marker; `final`/strict init и layout trade-offs |
| VAL-PERF-02 | P1 | Scalarization vs heap flattening; small vs large array | Конференционен benchmark **не** e local speedup; JIT/GC/CPU caveats |
| VAL-CLASSFILE-03 | P2 | `LoadableDescriptors` и optional early loading | [JDK28 EA CLI check](../../java/language/data-oriented-programming/jdk28-ea/verify-valhalla-layout.sh): стар срещу preview class-file metadata |
| VAL-NULLABLE-04 | P2 | `value record` **не** е null-free | [NullableFlatLayoutPreview.java](../../java/language/data-oriented-programming/jdk28-ea/NullableFlatLayoutPreview.java): nullable field/array + equality |
| VAL-FUTURE-05 | P3 | `Song!`, null-restricted storage, primitive classes | Proposal review, API/binary compatibility, кога НЕ е available Java API |

**Практика:** JDK25 `mvn verify` остава неизменен; [съществуващият JDK28 EA workflow](../../.github/workflows/valhalla-jdk28-ea.yml) изпълнява новия classfile/prototype check само при релевантни промени. [NotebookLM](../../java/language/data-oriented-programming/VALHALLA-NOTEBOOKLM-PROMPT.md) е разширен. Не маркираме темата като усвоена без проверени отговори.

---

## P2 — GraalVM Native Image / Layers / Crema (Devoxx 2026)

**Материал:** [GraalVM Next — Native Image, Reachability Metadata, Layers, Crema, Web Image](../../java/jvm/graalvm-native-image/README.md); оригинал: [Alina Yurenko и Thomas Wuerthinger, Devoxx 2026](https://www.youtube.com/watch?v=OLQ98Ko4p3Q) + предоставен SRT. Java 25/JUnit е standard CI, Native Image и Web Image са **manual experiments**, не тествани автоматично.

| ID | Микро-тема | Проверяваме |
| --- | --- | --- |
| JVM-GRAAL-01 | JVM JIT vs Leyden cache vs Native Image | Проблем/кога/кога не, cold-start, throughput, memory и release/CI цена |
| JVM-GRAAL-02 | Reflection metadata / tracing agent | Reachability, allowlist, positive/negative Java tests и real native validation |
| JVM-GRAAL-03 | Native Image Layers / RSS/PSS | .nil build vs runtime libs, 2 consumers, Linux sum PSS и deployment negative test |
| JVM-GRAAL-04 | Crema / Byte Buddy | JUnit за нови класове, JVM vs experimental native, interpreter/JIT ограничения |
| JVM-GRAAL-05 | Web Image — P3 radar | JS wrapper+Wasm, browser/Node vs standalone WASI, кога НЕ |

**Практичен checkpoint:** [JUnit tests + упражнения](../../java/jvm/graalvm-native-image/EXERCISES.md), измерване на един и същ workload за JVM/Leyden и ръчни opt-in Native/Layer експерименти. Материалът **не означава усвоени знания** без реални отговори и проверка.

---

## P2 — Post-Quantum Cryptography / Java Backend (Devoxx 2026)

**Материал:** [Post-Quantum Cryptography for Java Backend — Java 25 ML-KEM/HKDF/AES-GCM и ML-DSA](../../spring/security/post-quantum-cryptography/README.md). Лекция: [Julian Burr — Post Quantum Today](https://www.youtube.com/watch?v=yUMnuh5U5k4) + предоставен SRT. За TLS 1.3 виж [manual migration checklist](../../spring/security/post-quantum-cryptography/TLS-AND-MIGRATION.md); не смесваме Java 25 crypto primitive със JSSE 27 negotiated hybrid TLS.

| ID | Микро-тема | Проверяваме |
| --- | --- | --- |
| SEC-PQC-01 | Password hashing vs RSA/ECC/AES, Shor, HNDL | Кой primitive е под риск и защо не сменяме Argon2id с ML-KEM |
| SEC-PQC-02 | ML-KEM → HKDF → AES-GCM + ML-DSA | Correctness, wrong key, tamper, AAD, implicit rejection и negative tests |
| SEC-PQC-03 | TLS 1.3 hybrid / Gateway / PKI / JWT | Реален TLS terminator, negotiated group, classical fallback, canary/rollback |

**Практичен checkpoint:** 2 Java 25 JUnit labs + 3-hop crypto inventory и migration decision; **не е записано като усвоено**, докато не се решат [упражненията](../../spring/security/post-quantum-cryptography/EXERCISES.md).

---

## P2 — Maven Reproducible Builds & Build Integrity (Devoxx 2026)

**Материал:** [Reproducible Builds & Build Integrity with Maven — Spring Boot 4 / Java 25 practical lab](../../java/build/reproducible-maven/README.md). Източник: [Maarten Mulders, Devoxx](https://www.youtube.com/watch?v=9vtYvAI2Dtk). Това е **кратка практически проверима P2 надстройка**, не нова голяма Maven дисциплина.

| ID | Микро-тема | Проверяваме |
| --- | --- | --- |
| BUILD-MVN-01 | Same commit ≠ same artifact; ZIP timestamps и build inputs | Разлика между зелен build, функционална еквивалентност и битова идентичност |
| BUILD-MVN-02 | Effective POM, dependencies/plugins и `outputTimestamp` | Кой управлява версиите; Maven 3.9 vs 3.10+; Spring Boot repackage/build-info |
| BUILD-MVN-03 | Maven artifact plugin, two-clean-checkout SHA-256 gate, clock/environment leakage | Отрицателен пример, диагностика и граници на local vs independent verification |
| BUILD-MVN-04 | [P2 Green Maven Builds — compile-once jobs](../../java/build/reproducible-maven/CI-EFFICIENCY.md) | Maven lifecycle срещу директен Surefire, artifacts/needs, provenance и отрицателен checksum тест |
| BUILD-MVN-05 | [Safe JUnit parallelism](../../java/build/reproducible-maven/CI-EFFICIENCY-LABS.md) | JUnit 5 @Execution/@ResourceLock, shared-state hazards, измерване без fake speedups |
| BUILD-MVN-06 | PR/main/release work-selection & runner resources | Fail-closed check selection, PR-only cancel, required statuses, wall time срещу runner-minutes |
| BUILD-MVN-07 | P3 Green CI energy/carbon estimates | EcoCI/Kepler assumptions и ограничения, без незадължителни production integrations |

**Практичен checkpoint:** изпълни positive и negative build profile, провери `artifact:compare` и обясни защо независима attestation изисква нещо повече от два clean checkout-а на един runner. Няма автоматично оценяване на лично усвояване; тестовете са доказателство само за кода.

## P1/P2 — Constraint Optimization (Devoxx 2026, Tom Cools / Geoffrey De Smet)

**Източник:** [„You May Now Optimize the Wedding“](https://www.youtube.com/watch?v=lsvVJaigNp8), предоставен SRT. Този учебен блок има собствен [Java 25 / Timefold 2.8 runnable module](../../java/optimization/constraint-solving/README.md), не е част от DP лекцията и **не означава вече усвоено ниво**.

| ID | Приоритет | Материал | Какво проверяваме |
| --- | --- | --- | --- |
| OPT-CSP-01 | P1 | [Assignment model + hard/medium/soft](../../java/optimization/constraint-solving/README.md) | PlanningEntity/Variable/Solution, hard feasibility, unassigned penalty, trade-offs с greedy |
| OPT-CSP-02 | P1 | [ConstraintVerifier + independent oracle](../../java/optimization/constraint-solving/README.md#3-readme--код--доказателство) | hard mismatch/conflict, soft score, 9 exhaustive states, negative `all unassigned` |
| OPT-CSP-03 | P2 | [Time windows / route reference](../../java/optimization/constraint-solving/ROUTING-AND-REPLANNING.md) | required vs optional visit, travel time, why `@PlanningListVariable` is different |
| OPT-CSP-04 | P2 | [Pinned batch replanning](../../java/optimization/constraint-solving/ROUTING-AND-REPLANNING.md#3-batch-replanning-pinning-на-вече-поети-назначения) | fixed commitments, new tasks, missing option rejection, snapshot integrity |
| OPT-CSP-05 | P2 architectural | [Spring SolverManager integration](../../java/optimization/constraint-solving/ASYNC-SPRING-INTEGRATION.md) | async job lifecycle, persistence, cancel/status, ProblemChange vs batch; **non-runnable design only** |

**Practitioner checkpoint:** explain why `0hard/-1medium` is feasible but incomplete, why `0hard/-10medium/-100soft` outranks `-1hard/0medium/0soft`, what happens if `forEachIncludingUnassigned` is omitted, and why no DB calls may occur inside constraints.

## P1/P2 — Ask the Architects (Devoxx 2026)

Източник: [панелът Ask the Architects](https://www.youtube.com/watch?v=ewCPBgOrLAE) и предоставеният SRT. **Не е нов общ Java курс**, а отделни проверими допълнения с актуални API/status бележки към 11.11.2026.

| ID | Приоритет | Материал | Проверяваме |
| --- | --- | --- | --- |
| JAVA-ARCH-01 | P1 | [Virtual Threads schedulers и Netty event loops](../../java/concurrency/project-loom/CUSTOM-SCHEDULERS-AND-EVENT-LOOPS.md) | 2 resource caps, virtual tasks и защо няма Java25 custom scheduler API |
| JAVA-ARCH-02 | P1 | [Modern Java agent review](../../ai/agentic-development/MODERN-JAVA-REVIEW-CONTRACT.md) | record vs class, permissions, tests, no fake perf |
| JAVA-ARCH-03 | P2 | [FFM String / native copy](../../java/jvm/native-interoperability/STRING-NATIVE-COPY-BOUNDARY.md) | UTF-16/UTF-8, NUL, native lifetime, independent copy |
| JAVA-ARCH-04 | P3 | [Primitive patterns withdrawal / String Templates](../../java/language/data-oriented-programming/AMBER-FUTURE-DESIGN.md) | JDK25–27 preview vs removal in JDK28, proposed syntax not runnable |

**Изходна проверка:** отличи сигурен от предположен JVM behavior, soft workaround от root cause и feature от preview/proposal. Лабораториите доказват само декларираните contracts, не throughput/p99.

## P1/P2 — Gavin Bierman: Project Amber, Devoxx Belgium 2026

**Основа:** [лекция](https://www.youtube.com/watch?v=Go76-9Q51wU) + предоставен автоматичен SRT. Новите примери използват стабилен Java25; имат отделни JUnit и не предполагат, че evolving language design вече съществува.

| ID | Приоритет | Материал | Проверяваме |
| --- | --- | --- | --- |
| JAVA-AMBER-01 | **P1** | [Record reconstruction invariants](../../java/language/data-oriented-programming/RECORD-RECONSTRUCTION-INVARIANTS.md) | interdependent components, canonical constructor, no invalid intermediate, defensive copy и structured `List<TaskPriority>` |
| JAVA-AMBER-02 | **P1** | [Exact numeric conversion](../../java/language/data-oriented-programming/NUMERIC-CONVERSION-EXACTNESS.md) | `Integer.MAX_VALUE` saturating cast trap, 2^24 boundary, IEEE754 oracle 10 000 values |
| JAVA-AMBER-03 | **P2** | [Data interfaces / deconstruction](../../java/language/data-oriented-programming/DATA-INTERFACES-AND-DECONSTRUCTION.md) | public interface + package-private implementation, client-substitutability, proposal vs Java25 |
| JAVA-AMBER-04 | **P3 watch** | [Amber Future Design](../../java/language/data-oriented-programming/AMBER-FUTURE-DESIGN.md) | withdrawn primitive patterns in JDK28, Constant Patterns, pattern declarations, reconstruction proposals |
| JAVA-AMBER-05 | **P1** | [Deserialization invariants](../../java/language/data-oriented-programming/DESERIALIZATION-AND-ENCAPSULATION.md) | legacy `Serializable` constructor bypass; valid/invalid record deserialization; safe readObject guard/filter |
| JAVA-AMBER-06 | **P1** | [JEP500 Java25/26 integrity](../../java/jvm/final-field-integrity/README.md) | real runtime: default warn, debug, deny и explicit caller-module enable; no preview syntax |

**Checkpoint:** защо двата последователни wither-а могат да се провалят при дължини 2→3? Кога `List<Pair>` е по-добър от два списъка? Защо `int→float→int` не доказва точност? Защо public interface не дава автоматично record pattern за скрита класа?

## P1 и P2 — след P0 основата, без да ги забравяме

- **P2 Analytical SQL / OLAP:** [From Laptop to Lakehouse — DuckDB JDBC/CSV/Parquet](../../persistence/analytical-sql-duckdb/README.md) — 12 Java25/JUnit tests с фалшиви GPS данни, `EXPLAIN`, DuckLake catalog/Quack trade-offs; **не** замества OLTP Oracle и не стартира S3 в CI.

- **P1 JSON correctness:** [Everyday JSON Java25/Jackson tests](../../java/experiments/jdk28-simple-json/README.md) — duplicate members, missing/null, numeric coercion и business validation; **P2 JDK28 EA experiment** компилира real `jdk.incubator.json` и анализира реален `jcmd` JSON dump.

- **P1 Integration Contracts:** [Solve the Right Integration Problem](../../spring/messaging/integration-patterns/04-SOLVE-THE-RIGHT-INTEGRATION-PROBLEM.md) и [Protocol Adoption V1/V2 Java25 JUnit](../../spring/messaging/integration-patterns/05-PROTOCOL-ADOPTION-AND-COMPATIBILITY.md) — producer/consumer compatibility, rollout, semantic downgrade safety.
- **P2 Engineering Case 007:** [PDF/Brotli standards and adoption](../case-studies/007-pdf-brotli-standards-adoption.md) — industry extension не доказва installed reader support и не е PDF library lab.

- **P1 supply-chain integrity:** [VEX/Backport Evidence](../../spring/testing/testcontainers/VEX-BACKPORT-EVIDENCE.md) — 18 offline negative tests; не е автоматична security clearance.
- **P2 OSS Operations:** [EOL/ELS strategy](../../java/build/reproducible-maven/OPEN-SOURCE-LIFECYCLE-EOL.md) и [Live Patching vs Rolling](../../infrastructure/kubernetes/LIVE-PATCHING-VS-ROLLING-UPDATES.md) — decision guides, без vendor lock-in.

- **P1:** Distributed Systems/System Design; JVM Performance & Observability; DS&A (вече разделено на малки topics); Redis/Caching; Docker/CI; AI-assisted Engineering.
- **P2:** Design Patterns и advanced architectural approaches, след като fundamentials и prerequisites са доказано ясни. [The Hexagonal Hangover — practical architecture decision-making](../../spring/architecture/hexagonal-architecture/P2-ARCHITECTURE-DECISIONS.md) е кратко допълнение към реализирания Hexagonal lab, **не** нова микро-тема с автоматично доказано усвояване.
- Ако важна работна задача съвпадне с тема от P1/P2, допускаме **временен скок**, но отбелязваме необходимите пропуски и се връщаме към тях.

## Първа активна спирка

`JAVA-FND-01` е **в процес на диагностика**; не е завършена. Виж [PROGRESS.md](PROGRESS.md) за точния въпрос, известната оценка и следващата проверка. След нея минаваме към `JAVA-FND-02` с адаптивна трудност. Не повтаряме вече дадени отговори.
