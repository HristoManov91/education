# Vlad Mihalcea — тематична карта и implementation backlog

> Дата на одит: 2026-11-18. Това е **план и проверка на пропуски**, не списък с готови лаборатории. Всяка бъдеща тема получава собствена дефиниция за готовност и доказателство.

## Реализирани учебни блокове

[SQL лаборатории](../../../database/sql-labs/README.md) съдържат 4 подробни урока, Fast H2 tests, real PostgreSQL Testcontainers integration suite, Oracle manual comparisons и targeted GitHub Actions workflow. VM-01..04 са **мърджнати в main** с успешно изпълнени unit+PostgreSQL tests. Следващите [VM-05..07 JDBC/MVCC/locking](../../../database/transaction-labs/README.md) и [VM-08..10 Hibernate](../../../persistence/repository-unit-of-work/README.md) имат изпълним код и са в процес на независима CI проверка. VM-11–VM-14 и VM-16–VM-17 са реализирани с H2/PostgreSQL/QueryDSL тестове (PR #103); VM-15 е мърджнат с 6 Spring/JPA теста (PR #104); VM-18 е мърджнат като JDBC streaming лаборатория (PR #105); VM-21 е мърджнат diagnostics runbook с реални предходни тестове (PR #106), а VM-26 е мърджнат с Hikari + PostgreSQL тестове (PR #107); VM-20 е мърджнат с PG runtime tests + Oracle 19c manual comparison (PR #109); VM-19 има JCache/H2 cache tests в PR за review; VM-22–VM-30 остават. Реализираните модули и глави не се смятат за лично усвоени без диагностика. Нито един материал не е автоматично `learned`.

## P0 — първо механиката на базата и ORM

| ID | Учебна тема / реален проблем | Състояние | Къде се реализира/разширява | Как доказваме |
| --- | --- | --- | --- | --- |
| VM-01 | SQL logical processing, NULL, JOIN cardinality — защо филтърът връща грешни/дублирани редове | implemented | `database/sql-labs` | SQL fixtures + expected result sets, PostgreSQL/Oracle dialect notes |
| VM-02 | Table design, PK/FK, natural/surrogate keys, constraints | implemented | `database/sql-labs` | schema constraints, invalid insert tests, indexing trade-offs |
| VM-03 | JOIN strategies, EXISTS vs JOIN, CTE, recursive CTE, window functions | implemented | `database/sql-labs` | резултати, EXPLAIN/DBMS_XPLAN, query correctness |
| VM-04 | Indexes, composite selectivity, statistics, execution plans | implemented | `database/sql-labs` | EXPLAIN ANALYZE на PostgreSQL, Oracle plan scripts; actual vs estimates |
| VM-05 | JDBC Connection/DataSource, PreparedStatement, pooling, queueing | implemented | `persistence/jpa-performance` + `database/transaction-labs` | active/idle/pending Hikari metrics, hold time, load scenario |
| VM-06 | ACID, MVCC, 2PL, anomalies, isolation boundaries | implemented | `database/transaction-labs` | конкурентни transaction тестове с реален DB; no H2-only claim |
| VM-07 | Optimistic/pessimistic locking, lost updates, deadlocks, SKIP LOCKED/NOWAIT | implemented | `database/transaction-labs` | две конкурентни транзакции, retry/idempotency, lock waits |
| VM-08 | Entity lifecycle: transient/managed/detached/removed, first-level cache | implemented | `persistence/repository-unit-of-work` | identity, detach/merge, query-count integration tests |
| VM-09 | Dirty checking, flush modes, action queue, write-behind | implemented | `persistence/repository-unit-of-work` | SQL order, flush timing, update count and failures |
| VM-10 | Mapping associations, lazy proxies, equals/hashCode, cascades, orphanRemoval | implemented | `persistence/repository-unit-of-work` + mapping lab | mapping matrix, SQL trace, orphan/remove tests |
| VM-11 | Identifier strategies: IDENTITY vs SEQUENCE, pooled/pooled-lo, batching side effects | implemented | `persistence/hibernate-performance` | insert round-trips, sequence tests, dialect/version checks |
| VM-12 | Fetch plans, N+1, EAGER defaults, EntityGraph, JOIN FETCH | implemented | `persistence/jpa-performance` | statement counts that fail before fix and pass after |
| VM-13 | DTO projections, pagination, keyset, collection-fetch pagination | implemented | `persistence/jpa-performance` + `persistence/specification-querydsl` | bounded SQL, deterministic page stability, no in-memory paging |
| VM-14 | Batch INSERT/UPDATE/DELETE, bulk DML, flush/clear, order_inserts | implemented | `persistence/hibernate-performance` | batch statistics, correctness, persistence-context staleness |
| VM-15 | Spring transactions, propagation, self-invocation, OSIV, connections | implementation-in-review | `persistence/repository-unit-of-work` | transaction boundaries and connection lease tests |
| VM-16 | Spring Data repository API traps: findAll, findById, save, exists, projections | implemented | `persistence/repository-unit-of-work` + Spring Data lab | SQL counts, bounded reads, find/getReference behavior |
| VM-17 | QueryDSL/Specification, count queries, query plan caching and parameter binding | implemented | `persistence/specification-querydsl` | same results, SQL plan, count and pagination tests |

## P1 — оптимизация, диагностика и реални production решения

| ID | Учебна тема | Състояние | Дестинация | Критерий за готовност |
| --- | --- | --- | --- | --- |
| VM-18 | JDBC fetch size, streaming, client/server statement cache | implemented | `database/transaction-labs` | memory/round-trip measurements, driver-specific behavior |
| VM-19 | Hibernate L1/L2/query cache: invalidation, consistency, cache strategies | implementation-in-review | `persistence/hibernate-performance` + existing `spring/caching` | stale-data counterexample и hit/miss metrics |
| VM-20 | PostgreSQL vs Oracle: plans, sequences, locking, JSON, pagination | implemented | `database/sql-labs` | двуколонни dialect scripts + unsupported feature notes |
| VM-21 | SQL/ORM observability: query counts, logs, pooling, slow query, Hypersistence Utils | implemented | `persistence/jpa-performance` + `persistence/hibernate-performance/VM-21-SQL-OBSERVABILITY.md` | Existing H2/PG SQL-count, batching, Hikari/plan tests mapped to diagnosis flow |
| VM-22 | JSON/ARRAY/enum mappings, time zones, converters and portable alternatives | new | `persistence/advanced-types` | DB round-trip + update tracking + serialization tests |
| VM-23 | Native queries, SQL functions and stored procedures | new | `persistence/native-sql-procedures` | parameter binding, result mapping, portability tests |
| VM-24 | Upsert/MERGE, bulk operations, consistency and unique constraints | new | `database/upsert-bulk` | concurrent upsert race reproduction, dialect comparison |
| VM-25 | Schema evolution: Flyway, validation, hbm2ddl safety | new | `persistence/schema-evolution` | migration and rollback-failure scenarios |
| VM-26 | Read-only routing, pool sizing, backpressure, DB throughput | implemented | `database/transaction-labs` | saturated pool, bottleneck-shift tests and metrics |

## P2 — специализирани допълнения (без излишна дълбочина в началото)

| ID | Тема | Състояние | Къде / критерий |
| --- | --- | --- | --- |
| VM-27 | Soft delete, Envers/auditing, triggers, CDC/Debezium | new | `persistence/auditing-cdc` + existing outbox; consistency tests |
| VM-28 | Entity inheritance, polymorphic queries and mapping costs | new | `persistence/entity-inheritance`; schema/query trade-offs |
| VM-29 | Hierarchical queries, recursive fetching, SQL window-based reporting | new | `database/advanced-sql`; correct pagination/recursion tests |
| VM-30 | Vendor-specific extensions, portability boundaries and Hibernate upgrades | new | `persistence/vendor-extensions`; versioned migration tests |

## Ред на изпълнение (dependency-aware)

1. **Фаза A — SQL reasoning:** VM-01→04. Това покрива SQL фундаментите преди Hibernate оптимизациите.
2. **Фаза B — transactions и state:** VM-05→10, после VM-15. Без това N+1 и connection tuning се превръщат в механично копиране на настройки.
3. **Фаза C — ефективно четене и писане:** VM-11→14, VM-16→17. Използваме съществуващите лаборатории като отправна точка.
4. **Фаза D — диагностика и database-specific performance:** VM-18→21 и VM-26, с PostgreSQL+Oracle доказателства.
5. **Фаза E — специализирани:** VM-22→25 и VM-27→30 според реални use cases.

## Общ Definition of Done за всяко VM-XX

- [ ] Оригинални публични статии на Vlad + официална документация + версионни бележки.
- [ ] **Реален production казус**; проблемът, кога се използва, кога не, печалби, загуби, алтернативи.
- [ ] Съществуваща тема се *разширява* вместо дублиране; иначе нов Maven/SQL lab на правилното място.
- [ ] README на български с `README → код`, `bad`/`good`, команди за стартиране.
- [ ] Изпълними Java/SQL integration и negative tests, или изрично обозначена концептуална бележка.
- [ ] PostgreSQL/Oracle разграничение; DB-specific claim се доказва на съответната база.
- [ ] SQL counts/locks/plans/connection metrics вместо фиктивни performance цифри.
- [ ] NotebookLM аудио prompt, практически задачи и финален decision checklist.
- [ ] Ревю на целия diff, работещи линкове, зелени релевантни тестове преди merge.

## Граници на текущия одит

Съществуващото repository и официалните три тематични индекса са проверени. Статусите са **ориентировъчна първа класификация по README**, а не построчен анализ на всички тестове или пълно преброяване на стотиците статии. `catalog.yaml` съдържа проверена първа селекция и се разширява при всяка изпълнена тема; не обявяваме '100% coverage' без реално покритие.
