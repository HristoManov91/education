# Практически упражнения — Spring Boot Optimizing Writes & Reads

> Съпътства [подробния урок по двете лекции](docs/WEBINAR-READ-WRITE-PERFORMANCE.md). Обучението е на база **извлечените английски субтитри** и [оригиналния PetClinic fork](https://github.com/vladmihalcea/spring-petclinic), а проверките са нашите вече съществуващи runnable лаборатории. **Без измислени benchmark резултати и без автоматичен Oracle тест.**

## Преди да започнем

**Цел:** да сравняваме **поведение**, не само stopwatch. Всички команди от корена на `education/`.

```bash
# H2/JPA — бързи semantics и SQL count тестове
mvn -pl persistence/hibernate-performance test

# Реални PostgreSQL 17 integration tests (изисква Docker)
mvn -pl persistence/hibernate-performance -Ppostgres-integration verify
```

Ако Maven reactor има нужда от dependencies, използвай инструкциите в [главния Hibernate модул](README.md). **Не твърдим, че тези команди са изпълнени от тази PR сесия**. Реалният Postgres test suite използва Testcontainers; без Docker очаквай инфраструктурно ограничение, не performance conclusion.

## Микросесия A — writes: „Batch в Java“ ≠ JDBC batch (20–30 мин)

1. Предвиди от какво зависи дали Hibernate ще групира INSERT-и при `IDENTITY` и `SEQUENCE`. Кога точно получава ID?
2. Отвори [`IdentityEntry`](src/main/java/bg/hristomanov/education/hperf/domain/IdentityEntry.java) и [`SequenceEntry`](src/main/java/bg/hristomanov/education/hperf/domain/SequenceEntry.java). Провери generation strategy и allocation.
3. Стартирай тестовете `sequenceDefersInsertUntilFlushButIdentityNeedsRowToObtainId` и `sequenceGeneratorActuallyExecutesJdbcBatchesAndIdentityDoesNot` в [HibernatePerformanceTest](src/test/java/bg/hristomanov/education/hperf/HibernatePerformanceTest.java).
4. Напиши **три числа/факта**: SQL statement count преди flush, реални JDBC batch events след flush, колко rows са persisted.
5. Негативна хипотеза: ако настроим `hibernate.jdbc.batch_size=500` върху `IDENTITY` и нищо друго — защо не получаваме същия ефект? Как би го доказал?
6. С real PostgreSQL теста [`postgresSequenceAllocationProducesActualJdbcBatchExecutions`](src/test/java/bg/hristomanov/education/hperf/PostgresHibernatePerformanceIT.java) потвърди, че H2 observation ≠ PostgreSQL execution details.

**Decision record:** кога обикновен JPA и настройки са достатъчни; кога явно JDBC batch; кога data-loader; какъв е рискът при schema/PK миграция.

## Микросесия B — writes: flush/clear, driver rewrite, StatelessSession и COPY (20–30 мин)

1. Изпълни `periodicFlushAndClearKeepsPersistenceContextBounded`. Защо `clear()` освобождава tracking, но може да наруши очаквания за cascade/relations?
2. Изпълни `bulkDmlBypassesManagedEntityStateUntilExplicitClear`. Защо managed entity е stale след bulk update? В кои случаи `@Modifying(clearAutomatically=true)` би изхвърлил още незаписани промени?
3. Сравни [оригиналния batch rewrite branch](https://github.com/vladmihalcea/spring-petclinic/tree/postgres-test-data-jdbc-batch-rewrite) с [pgJDBC документацията](https://jdbc.postgresql.org/documentation/use/#connection-parameters). Формулирай защо `reWriteBatchedInserts` няма аналогична настройка в Oracle.
4. Прочети [StatelessSession оригинален код](https://github.com/vladmihalcea/spring-petclinic/tree/postgres-test-data-stateless-session) и [many-to-many варианта](https://github.com/vladmihalcea/spring-petclinic/tree/postgres-test-data-stateless-session-many-to-many). Кои JPA cascade/managed-state гаранции липсват?
5. Стартирай PostgreSQL IT тестовете `statelessSessionInsertMultiplePersistsRowsAndAssignsGeneratedIds` и `statelessSessionDoesNotCascadeToAssociatedEntities`. Обясни защо `insertMultiple` не доказва автоматично едно server-side SQL изпълнение и защо children трябва да се запишат изрично.
6. Стартирай `pgJdbcRewriteBatchedInsertsReducesServerStatementExecutions`. Тестът използва `pg_stat_statements`, сравнява 64 INSERT-а с rewrite off/on и проверява реалните server calls. Обясни защо това е PostgreSQL/driver-specific и какво трябва да се провери отделно за Oracle.
7. Стартирай `postgresCopyManagerParticipatesInTransactionRollback`. Това е автоматизираният Testcontainers вариант на следния ръчен SQL proof на `COPY` с PostgreSQL 17/psql върху **локална тестова DB**:

```bash
psql -X -v ON_ERROR_STOP=1 -d postgres -f persistence/hibernate-performance/sql/copy-transaction-demo.sql
```

Очакване: в транзакцията **2 rows**, след `ROLLBACK` **0 rows**. Това коригира опасно тълкуване на устното „COPY bypasses transaction engine“. Ръчният psql скрипт и автоматизираният `CopyManager` Testcontainers test проверяват едно и също транзакционно свойство по два различни начина.

**Negative tests, които да планираш:** duplicate PK по време на COPY; FK violation; rollback след грешка; wrong CSV encoding; optimistic locking и audit callback при bulk DML.

## Микросесия C — reads: hidden graph, N+1, overfetching (20–30 мин)

1. Представи `100 Owner → 5 Pet → 100 Visit`. Колко visit реда могат да се заредят? Ако UI иска само owner names, защо това е архитектурен дефект?
2. Отвори [VM-12](docs/VM-12-FETCH-NPLUS1.md) и [`PerfOrderReader`](src/main/java/bg/hristomanov/education/hperf/read/PerfOrderReader.java). Разграничи EAGER от LAZY, JOIN FETCH от DTO.
3. Стартирай `naiveLazyToOneProducesNPlusOneWhileFetchJoinAndEntityGraphUseOneSql` и `postgresNPlusOneAndJoinFetchHaveMeasurablyDifferentStatementCounts`.
4. Напиши защо преминаването само към `LAZY` **не** отстранява N+1. Какво променя use-case-specific query?

**Code review:** `GET /owners` не бива да serialize-ва visits; `GET /owners/{id}/pets` и `GET /pets/{id}/visits` трябва да имат bounded contract, deterministic ordering и разрешен размер на result.

## Микросесия D — reads: projection, keyset и nested page (20–30 мин)

1. Отвори [`OrderRow`](src/main/java/bg/hristomanov/education/hperf/read/OrderRow.java), [`PerfOrderReader`](src/main/java/bg/hristomanov/education/hperf/read/PerfOrderReader.java) и [VM-13](docs/VM-13-DTO-PAGINATION.md).
2. Изпълни `projectionAndKeysetCursorReturnStableOrderedWindows` и `hibernate74UsesBoundedNestedFetchAndTwoPhasePageReturnsSameParents`.
3. На реален PostgreSQL изпълни `postgresKeysetAndTwoPhaseCollectionFetchAreBounded`.
4. Защо `ORDER BY updatedAt` сам по себе си не е достатъчен стабилен cursor? Покажи `(updatedAt, id)`, tie-break, null handling, index и delete/insert между pages.
5. Избери между `Page`, `Slice` и keyset за (а) малка admin таблица с page numbers; (б) 10 милиона historical events със „Зареди още“. Кога трябва COUNT?
6. Прецени дали [Blaze Entity Views от лекторите](https://github.com/vladmihalcea/spring-petclinic/tree/postgres-test-data-blaze-persistence) добавя реална стойност за твоите DTO-та.
7. Стартирай [BlazeEntityViewTest](src/test/java/bg/hristomanov/education/hperf/BlazeEntityViewTest.java). Провери parent page от 2 customers, пълните им nested order collections, total count и SQL statement count. Blaze-Persistence 1.6.20 е само test dependency; сравни това с нашия plain `record` DTO вариант и обясни защо библиотеката не трябва да се добавя автоматично към production.

## Микросесия E — доказателство: benchmark и migration decision

Попълни за всяко `before → after`:

| Поле | Before | After |
| --- | --- | --- |
| Build/JDK/Spring/Hibernate/DB driver versions | … | … |
| Dataset: rows/relations/columns | … | … |
| Transaction/commit semantics | … | … |
| API response shape и correctness | … | … |
| DB SQL statements/actual batch executions | … | … |
| SQL plan actual rows/buffers/locks | … | … |
| Average + p50/p95 latency или rows/sec | … | … |
| Heap/GC/JDBC pool pressure | … | … |
| Trade-offs, migrations, rollback | … | … |

**Benchmark правилата:** минимум няколко warmup и повторени независими runs, еднакви inputs и constraints, отбелязано cold/warm cache, без production load. Разделяй correctness tests от performance measurements. Не сравнявай „пълно owner+visits graph“ с „само 50 DTO owner имена“ като еднакво количество работа.

## Самопроверка (без готов отговор)

- Защо `saveAll()` сам по себе си не доказва JDBC batching?
- Какво означават Hibernate prepared statements срещу `executeBatch` events?
- При кои условия ще предпочетеш `StatelessSession` пред `JdbcTemplate` и JPA? Какво доказва тестът за cascade?
- Как `pg_stat_statements` доказва ефекта от `reWriteBatchedInserts` и защо не е benchmark за latency?
- Вярно ли е, че PostgreSQL `COPY` не участва в rollback? Какво показва Testcontainers и ръчният SQL тест?
- Кога Blaze Entity Views оправдава още една зависимост спрямо plain DTO projection?
- Защо `LAZY` може да създаде N+1 и защо `EAGER` може да е още по-зле?
- Можеш ли да гарантираш, че keyset pagination е O(1)? Как индексът, планът и filter selectivity влияят?
- Кои неща от видеата са version-specific и кои принципи можеш да приложиш при Spring Boot 3.3 + Oracle 19c?
- Кога `@Transactional(readOnly=true)` е само hint и не е database security policy?

### Оригинални източници

- [Writes](https://youtu.be/p4-8gRoIaAg), [Reads](https://youtu.be/r_Akpdq5tUw), [PetClinic fork](https://github.com/vladmihalcea/spring-petclinic).
- [Hibernate 7.4 user guide](https://docs.hibernate.org/orm/7.4/userguide/html_single/), [PostgreSQL 17 COPY](https://www.postgresql.org/docs/17/sql-copy.html), [Spring Data JPA](https://docs.spring.io/spring-data/jpa/reference/).
