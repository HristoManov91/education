# High-Performance SQL — Vlad Mihalcea, VM-01 → VM-04

**Изпълним учебен блок**, Java 25 + JDBC + JUnit + H2 (бързи логически тестове) + истински PostgreSQL 17 чрез Testcontainers. Оригинални SQL/Java примери и наша документация; не е копие на статия или платен курс.

## 1. Реалният казус

Екран „Клиенти с платени поръчки“ показва **един клиент два пъти**, а месечен отчет **пропуска клиентите без продажби**. Колега добавя индекс на `purchase_order.status`, но бавният отчет не се подобрява. Имаме три различни проблема:

1. **SQL correctness**: `JOIN` умножава редовете, докато `EXISTS` проверява съществуване.
2. **Data integrity**: невалидни записи и счупени външни ключове трябва да бъдат спрени от самата база.
3. **Performance**: индексът не е „ускорител по подразбиране“, а избор на cost-based optimizer, зависещ от селективност, статистики и shape на заявката.

Цел: да разберем **точната семантика на SQL преди да оптимизираме SQL**, като докажем поведението с тестове и реален query plan.

## 2. Четири свързани, но самостоятелни урока

| Урок | Реален въпрос | Доказателство |
| --- | --- | --- |
| [VM-01 — SQL semantics, NULL, JOIN и EXISTS](docs/VM-01-SQL-SEMANTICS.md) | Защо отчетът дублира или губи клиенти? | H2 + реален PG тест |
| [VM-02 — релационен модел и ограничения](docs/VM-02-RELATIONAL-CONSTRAINTS.md) | Защо Java validation не стига? | Negative constraint tests, PostgreSQL SQLSTATE |
| [VM-03 — CTE, recursive CTE и window functions](docs/VM-03-CTE-WINDOWS.md) | Как да намерим най-добрия клиент по регион и йерархия? | Детерминистичен ROW_NUMBER, рекурсия |
| [VM-20 — Oracle срещу PostgreSQL: SQL semantics, portability и plans](docs/VM-20-POSTGRESQL-ORACLE-COMPARISON.md) | Какво точно се променя при миграция? | 4 real PostgreSQL tests + ръчен Oracle 19c SQL |
| [VM-04 — индекси, селективност и plans](docs/VM-04-INDEXES-AND-PLANS.md) | Защо създаденият индекс понякога не се използва? | PostgreSQL 20 000 записа, JSON EXPLAIN ANALYZE |
| [Упражнения и диагностика](EXERCISES.md) | Можеш ли да обясниш и докажеш избора? | Възпроизводими задачи |
| [NotebookLM аудио урок](NOTEBOOKLM-PROMPT.md) | Дълбоко разбиране при слушане | Сценарии и проверяващи въпроси |

## 3. Кратък mental model

~~~text
HTTP/JDBC -> SQL query -> relational semantics -> result cardinality
                        -> DB constraints -> valid persisted state
                        -> optimizer + stats + indexes -> execution plan
~~~

**Correctness преди speed:** бърза грешна заявка остава грешна заявка.

## VM-23 — Native SQL и database functions

[VM-23 Native SQL/procedures](docs/VM-23-NATIVE-SQL-PROCEDURES.md) свързва [4 PostgreSQL integration tests](src/test/java/bg/hristomanov/education/sql/PostgresNativeSqlIT.java) с JDBC binding, типизирани result models, функция и constraints. PostgreSQL SQL function не е еквивалент на portable PL/SQL stored procedure.

## VM-24/25 — atomic UPSERT и версия на schema

- [VM-24 — PostgreSQL UPSERT + Oracle MERGE differences](docs/VM-24-ATOMIC-UPSERT.md) — [concurrent PostgreSQL tests](src/test/java/bg/hristomanov/education/sql/PostgresUpsertLabIT.java) и [Oracle manual SQL](sql/oracle/vm24-merge.sql).
- [VM-25 — Flyway migrations/validation](docs/VM-25-FLYWAY-MIGRATIONS.md) — [real PostgreSQL tests](src/test/java/bg/hristomanov/education/sql/PostgresFlywayMigrationIT.java), [V1](src/test/resources/vm25/sql/V1__create_vm25_account.sql) и [V2](src/test/resources/vm25/sql/V2__add_active_flag.sql).
- [NotebookLM аудио урок](VM-24-25-NOTEBOOKLM-PROMPT.md) и [практически упражнения](EXERCISES.md).

## VM-29 — Cycle-safe recursive SQL и window ranking

- [VM-29 — recursive CTE, bounded depth, path cycle detection, RANK vs ROW_NUMBER](docs/VM-29-HIERARCHICAL-ANALYTICS.md)
- [PostgreSQL JDBC reader](src/main/java/bg/hristomanov/education/sql/PostgresHierarchyReader.java), [3 real PostgreSQL tests](src/test/java/bg/hristomanov/education/sql/PostgresHierarchyAnalyticsIT.java), [Oracle 19c manual comparison](sql/oracle/vm29-hierarchy.sql)

## 4. README → код

| Какво гледаме | Production-like код / SQL | Доказателство |
| --- | --- | --- |
| `EXISTS` вместо дублиращ JOIN | [SqlReadRepository](src/main/java/bg/hristomanov/education/sql/SqlReadRepository.java) | [H2SqlSemanticsTest](src/test/java/bg/hristomanov/education/sql/H2SqlSemanticsTest.java) |
| Constraints и relationship-и | [schema.sql](src/main/resources/sql/postgresql/schema.sql) и [seed.sql](src/main/resources/sql/postgresql/seed.sql) | [H2](src/test/java/bg/hristomanov/education/sql/H2SqlSemanticsTest.java) и [PostgreSQL](src/test/java/bg/hristomanov/education/sql/PostgresSqlLabIT.java) |
| `ROW_NUMBER` / recursive CTE | [SqlReadRepository](src/main/java/bg/hristomanov/education/sql/SqlReadRepository.java) | [H2SqlSemanticsTest](src/test/java/bg/hristomanov/education/sql/H2SqlSemanticsTest.java) |
| Skew / индекс / optimizer | [performance.sql](src/main/resources/sql/postgresql/performance.sql) + [manual-explain.sql](sql/postgresql/manual-explain.sql) | [PostgresSqlLabIT](src/test/java/bg/hristomanov/education/sql/PostgresSqlLabIT.java) |
| VM-20 portable vs vendor-specific SQL | [PostgreSQL examples](sql/postgresql/vm20-dialect-lab.sql) · [Oracle manual](sql/oracle/vm20-dialect-lab.sql) | [PostgresSqlLabIT](src/test/java/bg/hristomanov/education/sql/PostgresSqlLabIT.java) (PG only) |
| Oracle диалект и manual validation | [Oracle DDL](sql/oracle/schema.sql), [Oracle report & plan](sql/oracle/reports-and-plan.sql) | Самостоятелна лаборатория; **не е автоматизирано тествана срещу Oracle** |

## 5. Стартиране

От корена на Education, в IntelliJ с настроен JDK 25:

~~~bash
# Бързи тестове без Docker; H2 проверява логика, НЕ PostgreSQL optimizer.
mvn -pl database/sql-labs test

# Пълни реални PostgreSQL тестове (изисква работещ Docker/compatible container runtime).
mvn -pl database/sql-labs -Ppostgres-integration verify
~~~

**Важна граница:** бързите тестове не се използват за доказателство на planner, locks, MVCC или Oracle особености. `PostgresSqlLabIT` се изпълнява от [таргетиран CI workflow](../../.github/workflows/vlad-sql-labs.yml), а не от общия Maven реактор. `mvn verify` без профил не стартира този IT.

За ръчна лаборатория можеш да стартираш PostgreSQL 17 (локално/контейнер), да изпълниш последователно [schema.sql](src/main/resources/sql/postgresql/schema.sql), [seed.sql](src/main/resources/sql/postgresql/seed.sql), [performance.sql](src/main/resources/sql/postgresql/performance.sql) и [manual-explain.sql](sql/postgresql/manual-explain.sql) през DataGrip/psql.

**Не изпълнявай seed/performance SQL върху production база.** Скриптовете създават специални учебни таблици и 20 000 допълнителни реда.

## 6. Къде в нашия Education стои този блок

- [VM roadmap и източници](../../docs/sources/vlad-mihalcea/README.md) → системна програма, не 30 статии едно към едно.
- [QueryDSL / Specification](../../persistence/specification-querydsl/README.md) → динамичните Java predicates трябва да се оценяват по **реалния SQL и plan**.
- [JPA performance](../../persistence/jpa-performance/README.md) → ORM добавя fetch plans, N+1 и transaction/connection lifecycle върху същата SQL основа.
- [Repository / Unit of Work](../../persistence/repository-unit-of-work/README.md) → entity lifecycle не замества DB integrity и isolation.

## VM-20 Oracle/PostgreSQL comparison

- [Съпоставени бизнес примери, version matrix, decision contract и негативни проверки](docs/VM-20-POSTGRESQL-ORACLE-COMPARISON.md).
- [Ръчен PostgreSQL SQL](sql/postgresql/vm20-dialect-lab.sql) и [ръчен Oracle 19c SQL](sql/oracle/vm20-dialect-lab.sql).
- [NotebookLM аудио материал](NOTEBOOKLM-VM20.md).

## 7. Изходен checklist

- Доказах ли exact row cardinality и поведението при `NULL`?
- Има ли DB constraints, а не само validation в service слоя?
- Избрах ли `EXISTS`, когато ми трябва само наличието на дъщерен запис?
- При ranking дефинирах ли deterministic tie-breaker?
- Кой е реалният DB engine/version? Има ли `EXPLAIN (ANALYZE, BUFFERS)`, статистики и сравнение на редове?
- Не твърдя ли недоказано „индексът винаги е по-бърз“?

## Оригинални източници

- Vlad Mihalcea: [SQL tutorials](https://vladmihalcea.com/tutorials/sql/), [SQL EXISTS](https://vladmihalcea.com/sql-exists/), [Index Selectivity](https://vladmihalcea.com/index-selectivity/).
- [Оригинален GitHub с примери на Vlad](https://github.com/vladmihalcea/high-performance-java-persistence).
- [PostgreSQL 17 constraints](https://www.postgresql.org/docs/17/ddl-constraints.html), [planner/EXPLAIN](https://www.postgresql.org/docs/17/using-explain.html).
- [Oracle 19c DBMS_XPLAN](https://docs.oracle.com/en/database/oracle/oracle-database/19/arpls/DBMS_XPLAN.html).

**Състояние:** VM-01–04 са мърджнати след успешни PostgreSQL tests; VM-20 остава `implementation-in-review` до CI/ревю. Oracle examples са ръчни и не са Oracle CI evidence; никой урок не се счита за лично усвоен без диагностика.
