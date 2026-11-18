# VM-11 — Hibernate identifiers: IDENTITY, SEQUENCE и pooled allocation

## Реален казус

Мигрираме масов import от `GenerationType.IDENTITY` към `SEQUENCE`. Старият import записва 10 000 реда, но не групира INSERT-и в JDBC batches. Друг developer сменя само анотацията и очаква автоматично ускорение, без да провери DB sequence DDL или Hibernate конфигурацията.

**Първо разбираме кога ID става известен, после оптимизираме.**

## Mental model: два различни момента

~~~text
IDENTITY: persist(entity) -> INSERT row -> database assigns id -> application knows id
SEQUENCE: persist(entity) -> SELECT sequence / use preallocated block -> id is known
                                             -> queued INSERT -> flush -> SQL INSERT
~~~

За `IDENTITY` Hibernate трябва да изпълни INSERT, за да получи идентификатора. Това ограничава batch INSERT-а за съответната entity. При `SEQUENCE` с `allocationSize=20` генераторът може да резервира блок от IDs и да отложи редовете до flush. Това е **възможност**, а не универсално измерено ускорение.

## README → код

| Съществено понятие | Конкретен файл | Тест |
|---|---|---|
| IDENTITY row-created ID | [IdentityEntry.java](../src/main/java/bg/hristomanov/education/hperf/domain/IdentityEntry.java) | [Sequence vs IDENTITY proof](../src/test/java/bg/hristomanov/education/hperf/HibernatePerformanceTest.java) |
| SEQUENCE с `allocationSize=20` | [SequenceEntry.java](../src/main/java/bg/hristomanov/education/hperf/domain/SequenceEntry.java) | [Same test](../src/test/java/bg/hristomanov/education/hperf/HibernatePerformanceTest.java) |
| Резултат върху реален PostgreSQL | [PostgresHibernatePerformanceIT](../src/test/java/bg/hristomanov/education/hperf/PostgresHibernatePerformanceIT.java) | PG 17 / Testcontainers |

В `sequenceDefersInsertUntilFlushButIdentityNeedsRowToObtainId()` използваме **JDBC SELECT без JPA auto-flush** за проверка на реално налични DB rows: след `persist(SequenceEntry)` ID е известен, но INSERT още не е изпратен; след `flush` редът се появява. При `IdentityEntry` INSERT се изпълнява при `persist`, за да стане известен ID.

## Bad → root cause → good

**Bad:** променяме стратегията без промяна в миграцията или настройките на DB.

~~~java
@Id @GeneratedValue(strategy = GenerationType.SEQUENCE)
private Long id; // няма проверка на DB sequence increment и allocation
~~~

В нови проекти най-често избираме explicit `SEQUENCE` (ако DB я поддържа) с конфигурация, синхронизирана със schema migration:

~~~java
@Id
@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "perf_entry_sequence")
@SequenceGenerator(name = "perf_entry_sequence", sequenceName = "perf_entry_sequence",
                   allocationSize = 20)
private Long id;
~~~

**Important:** `allocationSize`, database sequence increment, optimizer (pooled vs pooled-lo), replication/failover и multi-instance usage трябва да са съвместими. Hibernate 7.1 може да конфигурира различни optimizers — от `allocationSize` **не следва**, че автоматично сме избрали точно `pooled-lo`. Празнини в идентификаторите след rollback, crash или reservation са нормални; **ID не е непрекъснат business sequence**.

## Decision contract

| Въпрос | Решение |
|---|---|
| Проблем | Много INSERT round-trips; грешно предположение за identifier lifecycle |
| Кога SEQUENCE | Масови inserts, DB с native sequence и правилно DDL/migration управление |
| Кога НЕ | Legacy schema с фиксиран IDENTITY contract; малко inserts, където допълнителната сложност не си струва |
| Печелим | Предварително налични IDs и възможност за JDBC batch; по-малко sequence round-trips при pooling |
| Губим | Пропуски в ID, внимателни migration/increment настройки, dialect coupling |
| Алтернативи | IDENTITY (прост), UUID/ULID (друг storage/index trade-off), application-assigned IDs |
| Как доказваме | JDBC row-state test + [actual batch execution listener](../src/test/java/bg/hristomanov/education/hperf/HibernatePerformanceTest.java) |

## PostgreSQL / Oracle

PostgreSQL 17 поддържа sequences и identity; Oracle 19c също поддържа sequences и identity, но SQL синтаксисът, sequence settings и драйверите са различни. *Oracle execution не е включено в този PR.* Не пренасяй резултатите на Hibernate+H2/PG към Oracle без собствена проверка.

## Завръщаме се към казуса

Import-ът получава обяснение: не „Hibernate е бавен“, а дали ID стратегията позволява групиране на INSERT, дали sequence е правилно настроена и дали DB/driver реално изпълнява batching. **Следва VM-14**, който брои реални JDBC batch events.

### Оригинални източници

- Vlad — [pooled-lo optimizer](https://vladmihalcea.com/hibernate-hidden-gem-the-pooled-lo-optimizer/); [Sequence ID generation](https://vladmihalcea.com/jpa-entity-identifier-sequence/).
- [Hibernate ORM 7.1 User Guide — identifiers](https://docs.hibernate.org/orm/7.1/userguide/html_single/#identifiers).
- [PostgreSQL 17 Sequences](https://www.postgresql.org/docs/17/sql-createsequence.html).
