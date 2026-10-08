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
| **P1** | Containers / CI / Testcontainers (advanced) | Runtime, deployment, testing |
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

**Налични лаборатории:** [Repository & Unit of Work](../../persistence/repository-unit-of-work/README.md), [Specification + QueryDSL](../../persistence/specification-querydsl/README.md).

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

## P1 и P2 — след P0 основата, без да ги забравяме

- **P1:** Distributed Systems/System Design; JVM Performance & Observability; DS&A (вече разделено на малки topics); Redis/Caching; Docker/CI; AI-assisted Engineering.
- **P2:** Design Patterns и advanced architectural approaches, след като fundamentials и prerequisites са доказано ясни.
- Ако важна работна задача съвпадне с тема от P1/P2, допускаме **временен скок**, но отбелязваме необходимите пропуски и се връщаме към тях.

## Първа активна спирка

`JAVA-FND-01` е **в процес на диагностика**; не е завършена. Виж [PROGRESS.md](PROGRESS.md) за точния въпрос, известната оценка и следващата проверка. След нея минаваме към `JAVA-FND-02` с адаптивна трудност. Не повтаряме вече дадени отговори.
