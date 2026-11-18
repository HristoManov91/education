# VM-20 — Oracle 19c срещу PostgreSQL 17: SQL семантика, query plans и portability

> Тема от [Vlad Mihalcea track](../../../docs/sources/vlad-mihalcea/README.md). PostgreSQL поведението е **изпълнимо проверено** с Testcontainers; Oracle 19c примери са за **ръчен** SQL*Plus/DataGrip тест в отделна schema. Не представяме PG/H2 тестове като автоматично доказателство за Oracle.

## 1. Реален казус: мигрираме справка от Oracle към PostgreSQL

След миграция имаме две различни бизнес аномалии: филтър `WHERE external_ref = ''` изглежда безобиден, но в Oracle празният `VARCHAR2` се третира като `NULL`; в PostgreSQL празният текст и `NULL` са две отделни стойности. Междувременно други заявки използват Oracle `sequence.NEXTVAL` и `DBMS_XPLAN`, които не съществуват с този синтаксис в PostgreSQL.

Не решаваме това с механично търсене/замяна в SQL. Разделяме **изнесени business semantics**, SQL dialect и **физически планове**.

## 2. Compatibility matrix (version-specific)

| Механизъм | PostgreSQL 17 | Oracle 19c | Правилен подход |
|---|---|---|---|
| Празен string | `''::text` е празен string, **не** `NULL` | `''` във `VARCHAR2` се счита за `NULL` | Изрична бизнес нормализация; негативни DB tests |
| Сортиране на NULL | `NULLS FIRST/LAST` | `NULLS FIRST/LAST` | И двата обикновено имат `ASC NULLS LAST`; **не измисляме разлика**, декларираме желания ред |
| Pagination | `LIMIT` или `FETCH FIRST n ROWS ONLY` | `FETCH FIRST n ROWS ONLY` (12c+) | `ORDER BY` с уникален tie-breaker, проверен на DB |
| Sequence | `nextval('name')` | `name.NEXTVAL` | И двата генератора могат да имат gap при rollback/cache |
| Recursive CTE | `WITH RECURSIVE name(...) AS (...)` | `WITH name(...) AS (...)` без `RECURSIVE` | Диалектна SQL форма и cycle policy |
| JSON | `json/jsonb`, `::jsonb`, `#>>`, operators/indexes | JSON в `VARCHAR2`/`CLOB`/`BLOB` с JSON functions/check constraint; 19c няма същия native `jsonb` | Explicit mapping, сериализация и index tests |
| SQL execution plans | `EXPLAIN (ANALYZE, BUFFERS)` (изпълнява query) | `EXPLAIN PLAN` / `DBMS_XPLAN.DISPLAY_CURSOR` (actual stats при права/инструментация) | Не сравнявай cost/node names 1:1 |
| Locking | `FOR UPDATE NOWAIT/SKIP LOCKED` | `FOR UPDATE NOWAIT/SKIP LOCKED` | Не приемай идентични ограничения и lock behavior; сложна pagination/locking комбинация иска DB test |
| Isolation | READ COMMITTED и PostgreSQL REPEATABLE READ, SERIALIZABLE | READ COMMITTED, SERIALIZABLE, READ ONLY transaction | Няма директно същото Oracle ниво `REPEATABLE READ` |
| Индекси и планове | B-tree, Bitmap Index Scan/Heap Scan, cost model | B-tree, bitmap index, различна CBO статистика | DB-specific cardinality/plan измервания |
| Upsert/MERGE | `INSERT ... ON CONFLICT`, `MERGE` | `MERGE` | Различни concurrency и returning/contracts; отделна VM-24 |
| Типове/време | `timestamptz`, `timestamp`, `boolean` | `TIMESTAMP WITH TIME ZONE`, `TIMESTAMP`, SQL BOOLEAN limitations в 19c | Версионни и driver-specific roundtrip tests |

Таблицата не твърди, че един engine е „по-добър“. За големи миграции провери Unicode/collation, `CHAR` comparison, Oracle empty-string NULL, implicit conversions, optimizer statistics, JDBC driver и transaction semantics.

## 3. Bad → root cause → good

**Bad:** пренасяме следния predicate като идентичен:

```sql
WHERE external_ref = ''
```

В PostgreSQL той може да намери реален празен string. В Oracle predicate с празен `VARCHAR2` фактически става сравнение с `NULL` и няма да даде TRUE при `=`. Това е **correctness bug**, не performance problem.

**Good:** дефинираме бизнес contract: празна стойност допустима ли е, нормализира ли се до `NULL`, какъв API contract е валиден? Ако е нужна нормализация, прилагаме я преди запис и подсилваме с DB constraint/validation. При сравнение с nullable column използваме `IS NULL` вместо `= NULL`.

## 4. Как го доказваме

### PostgreSQL — автоматизирани integration tests

[PostgresSqlLabIT](../src/test/java/bg/hristomanov/education/sql/PostgresSqlLabIT.java) има отделни VM-20 проверки:

1. `''::text` има length=0, `IS NULL=false`, `NULLIF('', '') IS NULL=true`.
2. Explicit `NULLS FIRST/LAST` върху същите rows връща точно определен ред.
3. Sequence `nextval` **не връща издадената стойност** след transaction rollback.
4. `::jsonb #>>` извлича status, а SQL-standard `FETCH FIRST 2 ROWS ONLY` връща bounded и детерминистични резултати.

Използвай [PG standalone script](../sql/postgresql/vm20-dialect-lab.sql) за възпроизводим експеримент в DataGrip/psql.

### Oracle 19c — ръчен test plan

[Oracle script](../sql/oracle/vm20-dialect-lab.sql) дава същите бизнес въпроси, но с Oracle синтаксис: `VARCHAR2` empty string → NULL, `JSON_VALUE`, `sequence.NEXTVAL`, `FETCH FIRST`, recursive subquery factoring и `DBMS_XPLAN`. Той изисква real Oracle 19c schema. **Не твърдим green Oracle tests**, докато няма такъв run и evidence.

И при двата engine-а **sequence ID не гарантира gap-free numbering**. Не използвай sequence за законово последователни документи без отделен business numbering contract.

## 5. Decision contract

| Въпрос | Решение |
|---|---|
| Проблем | Невярна SQL семантика или план след Oracle ↔ PostgreSQL миграция |
| Кога | При миграция, multi-database product, ORM dialect/driver upgrade |
| Кога НЕ | За да заменим всички SQL изрази с огромен универсален compatibility abstraction без реална нужда |
| Печалба | По-малко correctness регресии, проверими допускания, ясни DB граници |
| Загуба | Двойни schema/scripts/tests, CBO differences, operational maintenance |
| По-проста алтернатива | Избери един поддържан DB dialect и explicit native SQL, ако portability не е изискване |
| Доказателство | [PostgresSqlLabIT](../src/test/java/bg/hristomanov/education/sql/PostgresSqlLabIT.java) + [Oracle manual script](../sql/oracle/vm20-dialect-lab.sql) (само ръчен) |

## 6. README → код и runbook

- [PG schema](../src/main/resources/sql/postgresql/schema.sql) и [seed](../src/main/resources/sql/postgresql/seed.sql).
- [PG VM-20 executable tests](../src/test/java/bg/hristomanov/education/sql/PostgresSqlLabIT.java).
- [PostgreSQL dialect SQL](../sql/postgresql/vm20-dialect-lab.sql) и [Oracle 19c dialect SQL](../sql/oracle/vm20-dialect-lab.sql).
- [VM-04 query plans](VM-04-INDEXES-AND-PLANS.md), [VM-06 isolation](../../../database/transaction-labs/docs/VM-06-MVCC-ISOLATION.md), [VM-07 locks](../../../database/transaction-labs/docs/VM-07-LOCKING.md), [VM-11 identifiers](../../../persistence/hibernate-performance/docs/VM-11-IDENTIFIERS.md).

```bash
mvn -pl database/sql-labs -Ppostgres-integration verify
```

**Review checklist:** какво става при празен string и `NULL`; дали ORDER BY е детерминистичен; expected rows/ties; PG vs Oracle JSON/storage/index; driver type mappings; каква е точно Oracle версията; кои реални DB tests ни липсват; сравняваме ли actual plans и cardinality, не „cost“ цифри между engines?

## Оригинални източници

- [Vlad Mihalcea SQL tutorial index](https://vladmihalcea.com/tutorials/sql/).
- [Vlad — SQL execution plan Oracle](https://vladmihalcea.com/sql-execution-plan-oracle/).
- [PostgreSQL 17 SELECT and locking](https://www.postgresql.org/docs/17/sql-select.html), [WITH](https://www.postgresql.org/docs/17/queries-with.html), [EXPLAIN](https://www.postgresql.org/docs/17/using-explain.html).
- [Oracle 19c SELECT and recursive factoring](https://docs.oracle.com/en/database/oracle/oracle-database/19/sqlrf/SELECT.html).
- [Oracle 19c sequence pseudocolumns](https://docs.oracle.com/en/database/oracle/oracle-database/19/sqlrf/Sequence-Pseudocolumns.html).
- [Oracle 19c DBMS_XPLAN](https://docs.oracle.com/en/database/oracle/oracle-database/19/arpls/DBMS_XPLAN.html).
