# Spring Boot Performance Engineering — Optimizing Writes & Reads

> **Източник:** две свързани лекции на Vlad Mihalcea и Jos Roseboom: [Optimizing Writes](https://youtu.be/p4-8gRoIaAg) и [Optimizing Reads](https://youtu.be/r_Akpdq5tUw). **Анализирани са английските субтитри на двете видеа** и [оригиналният демонстрационен fork](https://github.com/vladmihalcea/spring-petclinic). Цитирани времена са **резултати от тяхното demo**, НЕ наши възпроизведени измервания. Автоматичният транскрипт може да съдържа грешки — за технически договори използваме официалната документация.

**Място в Education:** този материал е **синтезираща учебна глава** над съществуващите изпълними лаборатории [VM-11–VM-14 и VM-16](../README.md), [VM-21 observability](../VM-21-SQL-OBSERVABILITY.md), [VM-18 JDBC streaming](../../../database/transaction-labs/VM-18-JDBC-FETCH-SIZE-STREAMING.md) и [VM-26 pool/backpressure](../../../database/transaction-labs/VM-26-POOL-BACKPRESSURE.md). Не копираме вече написаните тестове. Нова ръчна PostgreSQL лаборатория за транзакционността на COPY: [copy-transaction-demo.sql](../sql/copy-transaction-demo.sql). [Упражнения и evidence worksheet](../WEBINAR-EXERCISES.md) · [NotebookLM](../NOTEBOOKLM-READ-WRITE-WEBINAR.md).

## 1. Реален казус: бавният екран и бавният импорт

Представи си Spring Boot приложение с `Owner → Pet → Visit` (както в PetClinic). В development имаш малко редове и всичко е бързо. Пет години по-късно стотици хиляди записи се импортират нощем; екранът „Собственици“ зарежда pets, а pets зареждат visits, дори когато UI иска само имената. Появяват се два различни симптома:

- **Write path (запис):** `saveAll()` / `persist()` в Java loop, но в DB виждаме индивидуални INSERT-и, много round-trips, голям persistence context и import от десетки секунди.
- **Read path (четене):** `findAll()` + `EAGER` или `LAZY` getters водят до необятен граф, N+1, cartesian multiplication, голям JSON и бавен browser. `Page` не поправя автоматично проблема.

```text
HTTP POST /import  → Spring Data/JPA → Hibernate SQL writer → JDBC driver → DB
                                     ↳ ID generator? batch? flush? inserts ordered?
HTTP GET /owners   → repository      → fetch plan       → SQL/query plan → JSON/UI
                                     ↳ EAGER? N+1? projection? pagination? count?
```

**Важната мисъл:** „оптимизиран Spring Boot“ не е свойство на framework-а. Оптимизираме **конкретния data-access use case** — семантиката на заявката, генерираните SQL-и, драйвера, DB плана, броя rows и тежестта за клиента.

## 2. Какво да можеш след урока

1. Да обясниш защо Java „batch“ не е непременно JDBC batch; защо `IDENTITY` и `SEQUENCE` могат да се държат различно в Hibernate.
2. Да избереш между stateful ORM, `StatelessSession`, `JdbcTemplate.batchUpdate` и PostgreSQL `COPY`, без да изгубиш correctness.
3. Да различаваш `LAZY`, N+1, `EAGER` и over-fetching; да моделираш read-specific DTO/Entity Views и bounded child collections.
4. Да прецениш keyset срещу OFFSET и `Page` срещу `Slice` с правилен стабилен sort.
5. Да отчетеш **правилна метрика**: Hibernate statements ≠ JDBC batch invocations ≠ DB executions ≠ bytes ≠ latency/throughput; и да разпознаваш нееквивалентни before/after сравнения.

## 3. READМE → работещ код и доказателство

| Разглеждан механизъм | Наш код / конфигурация | Работещ тест |
| --- | --- | --- |
| `IDENTITY` / pooled `SEQUENCE`, JDBC batch | [IdentityEntry](../src/main/java/bg/hristomanov/education/hperf/domain/IdentityEntry.java), [SequenceEntry](../src/main/java/bg/hristomanov/education/hperf/domain/SequenceEntry.java), [properties](../src/main/resources/application.properties) | [`sequenceGeneratorActuallyExecutesJdbcBatchesAndIdentityDoesNot`](../src/test/java/bg/hristomanov/education/hperf/HibernatePerformanceTest.java), [real PG IT](../src/test/java/bg/hristomanov/education/hperf/PostgresHibernatePerformanceIT.java) |
| `flush()/clear()`, bulk update/stale state | [VM-14](VM-14-BATCH-BULK.md) | [`periodicFlushAndClearKeepsPersistenceContextBounded`, `bulkDmlBypassesManagedEntityStateUntilExplicitClear`](../src/test/java/bg/hristomanov/education/hperf/HibernatePerformanceTest.java) |
| N+1 срещу `JOIN FETCH` / `EntityGraph` | [PerfOrderReader](../src/main/java/bg/hristomanov/education/hperf/read/PerfOrderReader.java), [VM-12](VM-12-FETCH-NPLUS1.md) | [`naiveLazyToOneProducesNPlusOneWhileFetchJoinAndEntityGraphUseOneSql`](../src/test/java/bg/hristomanov/education/hperf/HibernatePerformanceTest.java) и [PG IT](../src/test/java/bg/hristomanov/education/hperf/PostgresHibernatePerformanceIT.java) |
| DTO/record projection, keyset и collection pagination | [OrderRow](../src/main/java/bg/hristomanov/education/hperf/read/OrderRow.java), [PerfOrderReader](../src/main/java/bg/hristomanov/education/hperf/read/PerfOrderReader.java), [VM-13](VM-13-DTO-PAGINATION.md) | [`projectionAndKeysetCursorReturnStableOrderedWindows`, `hibernate74UsesBoundedNestedFetchAndTwoPhasePageReturnsSameParents`](../src/test/java/bg/hristomanov/education/hperf/HibernatePerformanceTest.java) |
| `Page` срещу `Slice`, `findAll` traps | [PerfOrderRepository](../src/main/java/bg/hristomanov/education/hperf/repository/PerfOrderRepository.java), [VM-16](VM-16-SPRING-DATA-TRAPS.md) | [`springDataGetReferenceDefersSelectAndPageDoesCountButSliceDoesNot`](../src/test/java/bg/hristomanov/education/hperf/HibernatePerformanceTest.java) |
| PostgreSQL `COPY` е транзакционно | [Ръчен SQL/psql скрипт](../sql/copy-transaction-demo.sql) | В скрипта `COPY`, `SELECT count(*)`, `ROLLBACK`, нов `SELECT count(*)`; **не е JUnit/CI тест** |
| Driver rewrite, `StatelessSession`, Blaze Entity Views | [Оригиналният PetClinic fork, branches по-долу](#11-оригинални-източници) | **Нямаме отделен локален automated тест за тези три техники** — виж [упражненията](../WEBINAR-EXERCISES.md) |

**Не приписвай на H2 тестовете доказателство за PostgreSQL wire protocol или Oracle 19c.** Real PostgreSQL проверки са в Testcontainers IT, а Oracle специфичните ефекти засега са за ръчна верификация.

## 4. WRITES — защо 156 000 INSERT-а са бавни

### 4.1. Демонстрацията, стъпка по стъпка (лекция Writes)

Лекторите генерират ~156 000 реда за PetClinic (включително owner/pet/visit/vet/specialty отношения) и сравняват няколко имплементации. Видеото показва приблизително:

| Сценарий | Време в demo | Какво се променя | Какво НЕ е доказано |
| --- | ---: | --- | --- |
| Начален JPA import, почти ред по ред | ~36 s | Baseline; приблизително INSERT за запис | Че всички JPA приложения са толкова бавни |
| JDBC Template batch | ~2.6–2.8 s | Explicit JDBC batch, без ORM tracking | Че JDBC винаги печели |
| Само Hibernate batch настройки при `IDENTITY` | ~25 s | Малка промяна, но INSERT-и остават много | Че `batch_size` автоматично е достатъчно |
| `SEQUENCE` + batching | ~8 s | Hibernate вече може да групира INSERT-ите | Exact speedup извън демото |
| По-голям sequence allocation | ~7.5 s | По-малко sequence round-trips; diminishing returns | Че `allocationSize=1000` е оптимално |
| PostgreSQL driver rewrite | ~6.8 s | Grouped multi-values INSERT | Същият резултат при Oracle/JDBC версия |
| Hibernate `StatelessSession.insertMultiple` | ~3.7 s в показания пример | Избягва stateful ORM context; ръчно групира | Feature parity с cascade и `ManyToMany` |
| PostgreSQL `COPY FROM` | ~1.5 s | Bulk data transfer | Че всички SQL ограничения изчезват |

Числата са от отделни демонстрационни изпълнения, не controlled benchmark; между вариантите може да има разлики в execution, row shape и association handling. **Заглавието „25×“ описва конкретен сценарий, не очаквана гаранция.**

### 4.2. Проблемът `IDENTITY`: защо `saveAll` не е JDBC batch

При `GenerationType.IDENTITY` стойността се генерира при INSERT в базата. Hibernate често трябва да изпълни INSERT още при `persist()`, за да получи ID на managed entity. Това пречи на нормално групиране на Hibernate INSERT statements. При подходящ `SEQUENCE` генератор Hibernate може да получи/разпредели ID предварително и да групира операции при flush.

```properties
# Примерна конфигурация — числата се измерват, не са универсални defaults:
spring.jpa.properties.hibernate.jdbc.batch_size=50
spring.jpa.properties.hibernate.order_inserts=true
spring.jpa.properties.hibernate.order_updates=true
```

```java
// Илюстрация; runnable entity и доказателство са в README→код таблицата.
@Id
@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "orderSequence")
@SequenceGenerator(name = "orderSequence", sequenceName = "order_sequence", allocationSize = 50)
private Long id;
```

Физическата DB sequence и Hibernate optimizer/allocation трябва да съответстват на реалната миграция. Смяна на генератор в съществуваща система не е „безрискова property промяна“: ID схемата, foreign keys, reset/migration и rollback се планират. Измервай `jdbcExecuteBatchStart` и database stats, не само `getPrepareStatementCount`: batch може да има множество bind-setове към един prepared SQL.

**Decision contract — ORM batching.** Проблем: много подобни INSERT/UPDATE и round-trips. Кога: data import или bulk write на съвместим SQL и ID модел. Кога НЕ: единични writes, highly mixed statements или когато persist side effects/ID стратегия не позволяват batch. Печелим: по-малко client-driver executions. Губим: ordering/flush complexity, memory, променен SQL execution order и трудно локализиране на проблемен ред. Алтернатива: `JdbcTemplate.batchUpdate`; доказателство: [VM-14](VM-14-BATCH-BULK.md) и [тест](../src/test/java/bg/hristomanov/education/hperf/HibernatePerformanceTest.java).

### 4.3. Batch size и generator allocation НЕ са едно и също

- `hibernate.jdbc.batch_size`: колко sets от parameters да се добавят преди JDBC `executeBatch()` за съвместим SQL.
- `@SequenceGenerator(allocationSize=...)`: колко ID-та може да разпредели generator без ново DB sequence извикване (зависи от optimizer/physical sequence).
- `flush()/clear()` през ограничен брой елементи: управлява memory/persistence context; **само по себе си не активира batching**.
- `order_inserts/order_updates`: може да подобри групирането на SQL statements; сортирането има цена и трябва да се оцени за реалния graph.

В лекцията `allocationSize=1000` носи далеч по-малко от основния преход IDENTITY→SEQUENCE. Избирай batch size чрез sweep примерно `10/25/50/100`, с еднакви data shape и transaction semantics; допълнителните печалби постепенно намаляват.

### 4.4. PostgreSQL driver rewrite — допълнителен слой

```properties
# Само за pgJDBC: query параметър в URL или DataSource driver property.
spring.datasource.url=jdbc:postgresql://localhost:5432/petclinic?reWriteBatchedInserts=true
```

Това е **pgJDBC настройка**, която може да трансформира съвместим `INSERT ... VALUES (?)` JDBC batch в multi-values INSERT. Може да помогне и на `JdbcTemplate`, не само на Hibernate. Не я копирай механично за Oracle: Oracle JDBC има други batching и driver-specific механизми. Влияе на SQL shape, parameter caps и driver compatibility. Проверявай реални DB executions и correctness (generated keys, conflict/returning semantics), не само лог „брой statement-и“.

**Decision contract — rewrite.** Кога: PostgreSQL, истински активен JDBC batching, съвместим SQL. Кога НЕ: не-PostgreSQL драйвер или query shape, който не може да бъде rewrite-нат. Печалба: по-малко server-side statement executions в подходящи cases. Загуба: driver-specific behavior и ограничения, които изискват integration test. По-простата алтернатива остава обикновен JDBC batching. Референция: [официален pgJDBC driver reference](https://jdbc.postgresql.org/documentation/use/#connection-parameters) и [оригинален branch](https://github.com/vladmihalcea/spring-petclinic/tree/postgres-test-data-jdbc-batch-rewrite).

### 4.5. `StatelessSession` срещу stateful JPA

Hibernate 7 предлага `StatelessSession.insertMultiple(List<?>)`. За batch-style processing без managed graph това може да намали object-tracking overhead. **Не притежава нормалния persistence context**, не предлага автоматичния entity dirty-checking/cascading lifecycle и изисква изрично управление на insert/update/relationships. Не го използвай за бизнес операция, която разчита на каскадни правила и aggregate invariants, без да ги имплементираш и тестваш.

**Decision contract — StatelessSession.** Кога: голям data import със строго контролиран ред и ясни relationships. Кога НЕ: сложен domain write graph, богат JPA lifecycle и каскади. Печелим: по-нисък tracking overhead, explicit batch API. Губим: convenience на JPA persistence context и изисквания към correctness/recovery. Алтернатива: оптимизиран stateful JPA batch или JDBC. **Оригинален runnable пример:** [stateless-session branch](https://github.com/vladmihalcea/spring-petclinic/tree/postgres-test-data-stateless-session), [many-to-many follow-up](https://github.com/vladmihalcea/spring-petclinic/tree/postgres-test-data-stateless-session-many-to-many). **Това не е още локален automated Education тест.**

### 4.6. PostgreSQL `COPY`: много бърз импорт, но НЕ bypass на транзакциите

`COPY FROM` приема поток от структурирани данни директно през PostgreSQL protocol, без да прави един application-level INSERT за всеки ред. В лекцията е най-бързият вариант за подготовка на много тестови данни. Удобен е за ETL/import/export, **не** за всяко domain command.

**Корекция спрямо устно твърдение в лекцията:** `COPY` **не „заобикаля транзакционния engine“ по общо правило**. Нормалният `COPY FROM` е транзакционен, участва в rollback и прилага релевантни constraints/triggers; има специални режими/оптимизации с особености. Доказателство: [официален PostgreSQL 17 COPY](https://www.postgresql.org/docs/17/sql-copy.html) и нашия [ръчен rollback експеримент](../sql/copy-transaction-demo.sql).

**Decision contract — COPY.** Кога: bulk import в PostgreSQL и prepared staging/test fixtures, когато ни трябва throughput повече от JPA callbacks. Кога НЕ: единични transactional domain events, database portability или когато upstream row-level validation и mappings изискват друга обработка. Печалба: по-малък SQL parsing и client overhead. Загуба: PostgreSQL-specific protocol, encoding/schema/streaming error handling, по-малко entity-level hooks. Алтернатива: JDBC batching или Hibernate `StatelessSession`; оригинален [COPY branch](https://github.com/vladmihalcea/spring-petclinic/tree/postgres-test-data-copy-command).

### 4.7. UUID и TSID: не смесвай избор на ID със скорост на импорт

Лекторът обсъжда клиентски UUID/TSID като начин за ранно генериране на IDs. `BIGINT` е **64 бита (8 байта)**, а UUID — **128 бита (16 байта)**, не 64/128 **байта**; автоматичният транскрипт/устното изказване смесват единици. Random UUIDv4 може да влияе върху locality на B-tree индексите; time-ordered UUIDv7 или compact long-based IDs имат други trade-offs. Но важни са uniqueness policy, multi-node coordination, external references и storage/DB plan. **Не сменяй PK модел само заради benchmark от лекция.**

## 5. READS — защо 100 owners водят до хиляди/десетки хиляди редове

### 5.1. Демонстрацията (лекция Reads)

След създаване на голям тестов dataset лекторите показват owner list и owner detail. При `Owner.pets EAGER` и `Pet.visits EAGER`, зареждане на 100 owners × 5 pets × 100 visits може да изисква работа върху **50 000 visit records**, въпреки че списъкът не ги визуализира. Показани са забавяния от секунди, query timeouts и голям client payload. Има и N+1 сигнали.

След това поетапно преминават към:
1. **Read models / projection**, вместо пълно entity graph; Blaze-Persistence Entity Views за nested projections/collections.
2. **Keyset pagination** за owner list, а не дълбок OFFSET.
3. **`LAZY` mapping** за to-many relations, вместо повсеместно `EAGER` (не спира автоматично N+1).
4. **Самостоятелно bounded pagination за pets и visits**; UI detail повече не дърпа цялата история наведнъж.

След тези промени демонстрираното зареждане на owner list пада под 300 ms. **Това е подобрен endpoint/read contract**, не идентичен SQL query, ускорен единствено чрез annotation. Не представяй сравнението като измерено 25× ускорение на същия резултат.

### 5.2. `EAGER` не е „по-малко заявки“, `LAZY` не е „решен N+1“

`EAGER` казва на ORM, че асоциацията трябва да се зареди, **не** гарантира един JOIN. `LAZY` отлага зареждането, но ако iter-ваш 100 parent-и и за всеки извикаш getter, можеш да произведеш 101+ SQL-и. Предпочитай **use-case specific fetch plan**:

- Read-only owner list: ограничен DTO projection с точно нужните полета.
- Owner detail: fetch-ване на конкретно нужните to-one relationships; колекциите с отделни bounded заявки.
- Edit aggregate: managed entity + explicit JOIN FETCH/`@EntityGraph` за нужния граф.
- Множество to-many collections: без „fetch join everything“ — риск от row multiplication и грешна pagination semantics.

**Decision contract — fetch plan/projection.** Кога: DTO при read-only UI/API; fetch join за реално нужни to-one relationships. Кога НЕ: DTO, когато трябва stateful entity modification; fetch join на няколко големи collections/страницирани parents. Печелим: малък result set и избягваме N+1/overfetching. Губим: отделни модели и query contracts, изрична mapping поддръжка. Алтернатива: `@BatchSize` или two-step fetch. Доказателство: [VM-12](VM-12-FETCH-NPLUS1.md) и [H2/PG tests](../src/test/java/bg/hristomanov/education/hperf/HibernatePerformanceTest.java).

### 5.3. DTO projections срещу Blaze-Persistence Entity Views

`SELECT new ...OrderRow(...)` в [PerfOrderReader](../src/main/java/bg/hristomanov/education/hperf/read/PerfOrderReader.java) демонстрира **без допълнителна библиотека** директен read model. [Spring Data closed projections](https://docs.spring.io/spring-data/jpa/reference/repositories/projections.html) също могат да оптимизират selection; open SpEL projections и nested associations могат да променят SQL shape, така че наблюдавай реалния SQL.

**Blaze-Persistence Entity Views** е допълнителна библиотека с по-силни инструменти за nested projections и collection mapping. В лекцията е използвана за owner/pet views. Не я включваме автоматично в Education dependencies, защото съществуващата DTO + keyset лаборатория покрива основните решения без още един ORM-adjacent dependency.

**Decision contract — Entity Views.** Кога: сложни read-only view модели със nested collections и повторяем projection boilerplate. Кога НЕ: прост списък от 3 колони или когато версионната Hibernate/Blaze съвместимост и maintenance тежат повече от ползата. Печелим: централизирани declarative views. Губим: нов abstraction, dependency, debugging/upgrade surface. Алтернатива: `record` DTO + explicit queries + two-phase pages. Демонстрация: [Blaze-Persistence branch](https://github.com/vladmihalcea/spring-petclinic/tree/postgres-test-data-blaze-persistence); това е **външен пример, не локален JUnit тест**.

### 5.4. Keyset/seek срещу OFFSET и Page/Slice

```sql
-- Илюстрация: стабилен ред по (last_name, id) + подходящ индекс.
SELECT id, first_name, last_name
FROM owner
WHERE (:after_last_name IS NULL)
   OR (last_name > :after_last_name)
   OR (last_name = :after_last_name AND id > :after_id)
ORDER BY last_name ASC, id ASC
FETCH FIRST 50 ROWS ONLY;
```

SQL е **conceptual query**, не runnable repository file; конкретната изпълнима реализация е [`PerfOrderReader`](../src/main/java/bg/hristomanov/education/hperf/read/PerfOrderReader.java) с [keyset tests](../src/test/java/bg/hristomanov/education/hperf/HibernatePerformanceTest.java). За production са нужни коректна обработка на NULL, ASC/DESC, stable uniqueness tie-breaker, индекс и cursor с контекст на филтрите.

- `OFFSET`: лесен random page access, но високият offset може да изисква прочит/пропускане на много rows.
- **Keyset**: следваща страница след last sort tuple; често по-ефективен при дълбоко последователно скролване и подходящ индекс; не гарантира константно време при произволни филтри/DB plan и не предлага директен page #300.
- `Page`: обичайно носи total/count cost (в зависимост от API/context); `Slice`: нужен е само has-next; `Window`/scrolling: explicit cursor contract.
- Pagination на parents **не** означава, че nested children са bounded; направи отделна pagination за visit history.

**Decision contract — keyset.** Кога: стабилен incremental feed, голяма таблица, подходящ `(sort, id)` индекс. Кога НЕ: произволно скачане до далечна страница, non-deterministic sort или липсващ index. Печелим: избягване на дълбок OFFSET scan. Губим: cursor API, backwards navigation, filter/cursor validation, concurrency semantics. Алтернатива: OFFSET за bounded datasets; виж [VM-13](VM-13-DTO-PAGINATION.md), [Spring Data scrolling](https://docs.spring.io/spring-data/jpa/reference/data-commons/repositories/scrolling.html).

### 5.5. Index и query plan: без това не знаем дали „по-бързо“ е вярно

Преди оптимизацията документирай query predicate, index structure, estimated/actual rows и време. PostgreSQL: `EXPLAIN (ANALYZE, BUFFERS)` върху безопасна test DB; **ANALYZE изпълнява заявката**, включително DML side effects. Oracle 19c: `DBMS_XPLAN.DISPLAY_CURSOR` със съответни privileges и instrumentation за действително изпълнен cursor, не само предполагаем plan. Виж [SQL/ORM observability](../VM-21-SQL-OBSERVABILITY.md) и [PostgreSQL↔Oracle comparison](../../../database/sql-labs/docs/VM-20-POSTGRESQL-ORACLE-COMPARISON.md).

## 6. Диагностика: инструменти от видеото без vendor lock-in

| Инструмент | Какво дава | Ограничение |
| --- | --- | --- |
| `Hibernate Statistics` / SQL logs / `StatementInspector` | Query counts, generated SQL и batch events | SQL count ≠ actual DB round trips; bind-logging може да разкрие PII |
| Hikari/Micrometer / Actuator | Active, idle, pending/wait и HTTP latency | Не казва кой SQL план/lock е причината |
| PostgreSQL EXPLAIN/pg_stat*; Oracle DBMS_XPLAN | Database execution plan, I/O, waits според наличната telemetry | Достъп, overhead и DB-specific permissions |
| **Hypersistence Optimizer** | Автоматични Hibernate/JDBC mapping/performance проверки и runtime warnings | Отделен **комерсиален** продукт; предупрежденията са хипотези |
| **[BootUI](https://github.com/jdubois/boot-ui)** | Open-source local developer dashboard за Spring Boot/Quarkus, advisors | Dev-time tool, не заместител на production APM; версиите и permissions се проверяват |

Практичният workflow от лекциите е: **наблюдение → хипотеза → една промяна → correctness тест → SQL/DB проверка → load comparison**. AI може да предложи оптимизация, но не трябва да приема автоматично, че batch, projection или индекс реално работят.

## 7. Сравняваме trade-offs, не магическо „25×“

| Вариант | Основно печелим | Основно губим / рискуваме | За каква задача |
| --- | --- | --- | --- |
| Stateful Hibernate + batching | JPA lifecycle + throughput за подходящ DML | Context memory, cascades/order и flush | Редовен business write с групово записване |
| StatelessSession | По-нисък managed-state overhead | Explicit graph handling, no automatic dirty checking/cascades | Контролиран bulk job |
| JdbcTemplate batch | Предвидим SQL и driver controls | Mapping и lifecycle boilerplate | Нискониво high-volume writes |
| PostgreSQL COPY | Bulk transfer throughput | DB-specific import semantics/validation | Staging/test fixtures/ETL |
| EntityGraph/JOIN FETCH | Махаме N+1 за точно нужен graph | JOIN row explosion/overfetch | Detail/ограничен to-one graph |
| DTO projection | Само нужни колони, без managed graph | Отделен read model | Read API/list |
| Blaze Entity Views | Declarative complex projection/collections | Third-party dependency, version matching | Сложен nested read model |
| Keyset | Efficient next page при индекс | Cursor contract, без direct page N | Feed/infinite scroll |

## 8. Как се измерва честно

Не докладвай само `wall clock` от един run. Минималният comparison sheet е в [WEBINAR-EXERCISES](../WEBINAR-EXERCISES.md). Контролирай:

1. **Еднаква семантика:** same rows, columns, associations, transactions, indexes, constraints, errors, client response contract. Особено важно при замяна на EAGER с paginated DTO.
2. **Еднакъв dataset:** realistic cardinalities (напр. 100 owners × 5 pets × 100 visits), warm/cold caches разграничени, статистики актуални.
3. **Поне няколко повторения:** warmup, median/p95, throughput, CPU/heap/GC, Hikari pending, DB wait/lock, connection count и explain plan; JMH е за подходящи microbenchmarks, **не** замества DB end-to-end load testing.
4. **Unit vs integration vs perf:** H2 test доказва ORM semantics; Testcontainers PG — поведение с реален PG dialect; production-like load test — performance; Oracle се проверява отделно.
5. **Correctness first:** inserts count, foreign keys, `@Version` optimistic locking, audit callbacks/Envers, transactional rollback, idempotency, duplicate keys, committed vs rolled back rows. Масов JPQL DML bypass-ва managed objects и може да остави stale context.

Примерна диагностика за read: `HTTP p95 → Hikari lease/pending → Hibernate statement count → DB plan/rows → network JSON payload → browser render`. За write: `rows/sec → number of batch executions → DB executions/round trips → WAL/redo/index overhead → locks → heap → transaction outcome`.

## 9. PostgreSQL срещу Oracle — какво може да приложим в проекта

В SQL/JPA приложения с Oracle 12c/19c можем да използваме **принципите** за identifier allocation, Hibernate batching, `flush/clear`, DTO, N+1 и cursor pagination. **Не** пренасяй PostgreSQL-specific `reWriteBatchedInserts` или `COPY` към Oracle. Oracle има собствени JDBC batching механизми, bulk load инструменти и optimizer/redo/undo поведение; изисква отделен benchmark. Също така `@Transactional(readOnly=true)` е hint/ORM оптимизация, не универсална DB-write забрана ([Spring Data JPA transactions](https://docs.spring.io/spring-data/jpa/reference/jpa/transactions.html)).

## 10. Извод и задачи за практика

Ако имаш **write bottleneck**, първо провери `IDENTITY`, SQL count, actual batching и transaction/flush; чак после driver rewrite, stateless или native bulk loader. Ако имаш **read bottleneck**, първо измери SQL/N+1, избери projection и bounded page/children; чак после външна Entity Views библиотека или кеш. Ако нямаш baseline и еквивалентен correctness тест, **още не знаеш дали оптимизацията е успех**.

**Практически стъпки:** отвори [упражненията](../WEBINAR-EXERCISES.md), изпълни първо локалните H2/PG тестове, провери original PetClinic branches и запиши измервания в таблица. Не запускай load/DDL/COPY експерименти върху production.

## 11. Оригинални източници

### Лекции и демонстрационен код

- [How to Make Your Spring Boot App Run 25× Faster By Optimizing Writes — Vlad Mihalcea / Jos Roseboom](https://youtu.be/p4-8gRoIaAg) — **английски субтитри проверени**.
- [How to Make Your Spring Boot App Run 25× Faster By Optimizing Reads — Vlad Mihalcea / Jos Roseboom](https://youtu.be/r_Akpdq5tUw) — **английски субтитри проверени**.
- [PetClinic fork на Vlad Mihalcea](https://github.com/vladmihalcea/spring-petclinic): [baseline dataset](https://github.com/vladmihalcea/spring-petclinic/tree/postgres-test-data), [batch config](https://github.com/vladmihalcea/spring-petclinic/tree/postgres-test-data-jdbc-batch-size), [SEQUENCE](https://github.com/vladmihalcea/spring-petclinic/tree/postgres-test-data-jdbc-batch-sequence-generator), [sequence allocation](https://github.com/vladmihalcea/spring-petclinic/tree/postgres-test-data-jdbc-batch-sequence-allocation-size), [driver rewrite](https://github.com/vladmihalcea/spring-petclinic/tree/postgres-test-data-jdbc-batch-rewrite), [StatelessSession](https://github.com/vladmihalcea/spring-petclinic/tree/postgres-test-data-stateless-session), [COPY](https://github.com/vladmihalcea/spring-petclinic/tree/postgres-test-data-copy-command), [Blaze](https://github.com/vladmihalcea/spring-petclinic/tree/postgres-test-data-blaze-persistence), [lazy loading + views](https://github.com/vladmihalcea/spring-petclinic/tree/postgres-test-data-lazy-loading-load-owner-blaze), [BootUI](https://github.com/vladmihalcea/spring-petclinic/tree/boot-ui).

### Официална документация / корекции

- [Hibernate ORM 7.4 User Guide — batching/fetching](https://docs.hibernate.org/orm/7.4/userguide/html_single/); [Hibernate StatelessSession API](https://docs.hibernate.org/orm/7.4/javadocs/org/hibernate/StatelessSession.html).
- [Spring Data JPA — projections](https://docs.spring.io/spring-data/jpa/reference/repositories/projections.html), [scrolling/keyset](https://docs.spring.io/spring-data/jpa/reference/data-commons/repositories/scrolling.html), [transactions](https://docs.spring.io/spring-data/jpa/reference/jpa/transactions.html), [modifying queries](https://docs.spring.io/spring-data/jpa/reference/jpa/query-methods.html).
- [pgJDBC — reWriteBatchedInserts](https://jdbc.postgresql.org/documentation/use/#connection-parameters), [PostgreSQL 17 COPY semantics](https://www.postgresql.org/docs/17/sql-copy.html), [PostgreSQL EXPLAIN/ANALYZE](https://www.postgresql.org/docs/17/using-explain.html).
- [Oracle 19c DBMS_XPLAN](https://docs.oracle.com/en/database/oracle/oracle-database/19/tgsql/generating-and-displaying-execution-plans.html).
- [Spring Boot Actuator metrics](https://docs.spring.io/spring-boot/reference/actuator/metrics.html), [BootUI open-source repository](https://github.com/jdubois/boot-ui).
