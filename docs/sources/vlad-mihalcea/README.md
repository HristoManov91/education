# Vlad Mihalcea → High-Performance Persistence & SQL

> Източников каталог за нашия Education. Създаден на 2026-11-18. **Това не е готов курс:** учебните модули се изграждат и проверяват поетапно, а прогресът се отчита само след реални упражнения.

## Защо този каталог съществува

Vlad Mihalcea публикува стотици статии за JDBC, SQL, JPA/Hibernate и Spring Data. Не създаваме отделен Maven модул за всяка статия. **Организираме знанията според проблема и зависимостите**, обединяваме припокриващи се източници и разширяваме наличните лаборатории, когато има смисъл.

- [Карта на темите, пропуските и реда на реализация](TOPIC-MAP.md)
- [Проверени оригинални статии и тематични индекси](catalog.yaml)
- [Стандарт за всеки учебен модул](../../LEARNING-MODULE-TEMPLATE.md)
- [План за системно учене (DB-01–DB-16)](../../learning/ROADMAP.md)

## Оригинални източници

- [Hibernate tutorials](https://vladmihalcea.com/tutorials/hibernate/)
- [SQL tutorials](https://vladmihalcea.com/tutorials/sql/)
- [Spring tutorials](https://vladmihalcea.com/tutorials/spring/)
- [High-Performance Java Persistence — съдържание на книгата](https://vladmihalcea.com/books/high-performance-java-persistence/)
- [Официален GitHub repository с примери](https://github.com/vladmihalcea/high-performance-java-persistence)
- [Курсове — за ориентация по теми, не като включени материали](https://vladmihalcea.com/courses/)

Публичните статии са достатъчни за стартиране. **Не препечатваме платени книги/видеа**, не представяме уроци от платен курс като достъпни без лиценз; изготвяме самостоятелни обяснения и реални тестове с линкове към първоизточника.

## Следващ изпълним блок — VM-11–VM-14 и VM-16–VM-17

- [High-Performance Hibernate: идентификатори, N+1, fetch, pagination, batching, bulk SQL и Spring Data](../../../persistence/hibernate-performance/README.md)
- [QueryDSL vs Specification: explicit COUNT / count-free Slice и bind safety](../../../persistence/specification-querydsl/VM-17-PAGE-SLICE-COUNT.md)
- [Fast + PostgreSQL CI, only changed paths](../../../.github/workflows/vlad-hibernate-performance.yml)

**Статус:** VM-11–VM-14 и VM-16–VM-17 са реализирани и тестувани в PR #103. VM-15 остава за отделен Spring propagation/AOP блок. Сливането на PR не означава доказано усвояване.

## VM-15 — Spring transaction propagation и AOP (implementation in review)

- [Учебна глава и decision guide: REQUIRED, REQUIRES_NEW, NESTED и rollback правила](../../../persistence/repository-unit-of-work/VM-15-SPRING-TRANSACTION-PROPAGATION.md)
- [6 изпълними Spring Boot tests](../../../persistence/repository-unit-of-work/src/test/java/bg/hristomanov/education/uow/VladSpringTransactionsTest.java), [упражнения](../../../persistence/repository-unit-of-work/VM-15-EXERCISES.md), [NotebookLM](../../../persistence/repository-unit-of-work/NOTEBOOKLM-VLAD-VM15.md)
- [CI: в съществуващия PostgreSQL/JPA workflow с нов guard](../../../.github/workflows/vlad-transactions.yml), без още един отделен CI job.

## P1 — VM-19 Hibernate Cache

- [Second-level entity cache, Query Cache, update timestamps, external JDBC stale-data example](../../../persistence/hibernate-performance/docs/VM-19-HIBERNATE-CACHE.md).
- [Изпълнимите JCache/H2 тестове](../../../persistence/hibernate-performance/src/test/java/bg/hristomanov/education/hperf/HibernateCacheLabTest.java) · [NotebookLM](../../../persistence/hibernate-performance/NOTEBOOKLM-VM19.md).

## P1 — VM-20 Oracle / PostgreSQL portability

- [SQL semantics, CBO plans, NULL/empty-string, sequences, JSON, paging and locking comparison](../../../database/sql-labs/docs/VM-20-POSTGRESQL-ORACLE-COMPARISON.md).
- [4 real PostgreSQL tests](../../../database/sql-labs/src/test/java/bg/hristomanov/education/sql/PostgresSqlLabIT.java) и [Oracle 19c manual scripts](../../../database/sql-labs/sql/oracle/vm20-dialect-lab.sql) — без недоказани Oracle CI claims.

## P1 — VM-26 Resource budgets

- [Connection pool tuning, bounded admission control и read-only routing pitfalls](../../../database/transaction-labs/VM-26-POOL-BACKPRESSURE.md).
- [Hikari in-process lab](../../../database/transaction-labs/src/test/java/bg/hristomanov/education/tx/PoolAdmissionGateTest.java) и [PostgreSQL read-only test](../../../database/transaction-labs/src/test/java/bg/hristomanov/education/tx/PostgresTransactionLabIT.java).

## VM-22–23 — Advanced mappings и Native SQL

- [VM-22 Hibernate JSONB / enum / timestamp mapping](../../../persistence/hibernate-performance/docs/VM-22-ADVANCED-TYPES.md) с real PostgreSQL тестове.
- [VM-23 Native SQL / функции и DTO mapping](../../../database/sql-labs/docs/VM-23-NATIVE-SQL-PROCEDURES.md) с PostgreSQL тестове.

## P1 — VM-21 Observability

- [SQL/ORM observability runbook: Hibernate statements, JDBC batching, Hikari pool and database plans](../../../persistence/hibernate-performance/VM-21-SQL-OBSERVABILITY.md). Това е концептуална диагностична глава, свързана с работещите лаборатории.

## Продължение — VM-05–VM-10

- [JDBC, MVCC, isolation, optimistic/pessimistic locking, deadlocks — Java + PostgreSQL Testcontainers](../../../database/transaction-labs/README.md)
- [VM-08: entity lifecycle, merge, identity](../../../persistence/repository-unit-of-work/VM-08-ENTITY-LIFECYCLE.md)
- [VM-09: dirty checking и AUTO flush](../../../persistence/repository-unit-of-work/VM-09-FLUSH-DIRTY-CHECKING.md)
- [VM-10: JPA mapping, cascade, LAZY, orphanRemoval](../../../persistence/repository-unit-of-work/VM-10-MAPPINGS-CASCADE-ORPHAN.md)
- [Targeted PostgreSQL/JPA CI](../../../.github/workflows/vlad-transactions.yml)

**Статус:** VM-01..10 са мърджнати с успешни тестове, но не са автоматично „усвоени“.

## Първи изпълним блок (VM-01–VM-04)

- [SQL labs: 4 учебни глави, JDBC implementation и tests](../../../database/sql-labs/README.md)
- [Практически задачи и checkpoint](../../../database/sql-labs/EXERCISES.md)
- [NotebookLM аудио материал](../../../database/sql-labs/NOTEBOOKLM-PROMPT.md)
- [PostgreSQL CI — само на промени в SQL lab](../../../.github/workflows/vlad-sql-labs.yml)

**Статус:** `implemented` — VM-01..04 са мърджнати след зелени GitHub unit/PG tests. Това не означава, че темите са усвоени.

## Налични лаборатории — преизползваме, не дублираме

| Съществуващ материал | Покритие | Нашето следващо действие |
| --- | --- | --- |
| [Repository & Unit of Work](../../../persistence/repository-unit-of-work/README.md) | Repository, Identity Map, entity states, flush | Разширяване с explicit merge/detach, flush ordering и SQL assert тестове |
| [When ORM Becomes OMG](../../../persistence/jpa-performance/README.md) | Hikari, OSIV, N+1, DTO, transaction boundaries | Собствени интеграционни lab-ове и измервания на Hibernate SQL |
| [Specification & QueryDSL](../../../persistence/specification-querydsl/README.md) | Dynamic filtering и композируеми predicates | SQL планове, count queries, pagination, query-plan caching |
| [Testcontainers](../../../spring/testing/testcontainers/README.md) | Контейнеризирани integration tests | PostgreSQL доказателства; Oracle скриптове/тестове само при осигурена среда |

Покритие на част от концепцията **не означава завършен модул**; това се отбелязва като `partial` в картата.

## Работен ред

1. **Проверка на източника:** намираме оригинална статия и релевантната официална документация; отбелязваме версии и дали препоръката още е валидна.
2. **Gap analysis:** сравняваме с вече съществуващите README, Java/SQL код, тестове и DB-микротеми.
3. **Обогатяване или нов lab:** един проблем → един ясен учебен модул, без да създаваме копия на вече реализирана тема.
4. **Обяснение и решение:** production сценарий → защо naive подходът изглежда разумен → root cause → bad/good → trade-offs → по-проста алтернатива.
5. **Доказателство:** реални SQL statements/query counts/locks/plan/connection metrics, negative tests и ясна команда за стартиране. Никакви недоказани performance числа.
6. **Преговор:** `README → код`, упражнения, NotebookLM prompt, директни оригинални източници, диагностика и checklist за code review.
7. **Merge:** след проверка на tests/links, PR и преглед; не отбелязваме курса за усвоен при merge.

## Технологични граници

- Основната executable линия е **Java 25** и съвместимият Spring Boot **4.x**, със dependency версии от Maven, а не наизуст от стара статия.
- Примерите по SQL и concurrency разграничават **PostgreSQL** и **Oracle**, включително различия в SQL syntax, MVCC/isolation, драйвери, locking, sequence generation и query planning.
- H2 може да е полезен за базова семантика, но **не е доказателство** за специфично поведение на Oracle или PostgreSQL. За DB-specific claims ползваме истинската база (например Testcontainers за PostgreSQL) или ясно обозначаваме неподкрепения test.
- Отделяме измерените резултати от примерни числа. Разграничаваме JVM/framework ефектите от DB ефектите.
- Hypersistence Utils е open-source инструментариум; Hypersistence Optimizer е отделен продукт. Не добавяме платен dependency като изискване за курса.

## Какво значат статусите

`existing-partial` = има свързан стар материал, но не пълно покритие; `new` = липсва тематичен собствен lab; `implemented` = README, runnable код, tests и доказателство са прегледани; `verified` = допълнителна независима проверка; `learned` = само след реална диагностика в учебната система.

**Следваща стъпка:** реализиране на P0 секциите от [TOPIC-MAP.md](TOPIC-MAP.md) по правилото *основи → ORM поведение → измерване → оптимизация*.
