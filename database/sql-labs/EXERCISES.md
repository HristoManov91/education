# VM-01–VM-04 — Упражнения, диагностика и проверки

Работи върху [реалния сценарий и стартиране](README.md). Не приемаме „модулът е научен“ само защото тестовете са зелени. Всеки въпрос има техническо обяснение, контрапример и проверим резултат.

## VM-24 / VM-25 — упражнения

1. Защо `existsByReference` преди `INSERT` не е защита срещу race между два workers?
2. Сравни `ON CONFLICT DO UPDATE` с наистина идемпотентно бизнес събитие.
3. Изпълни [конкурентните PostgreSQL тестове](src/test/java/bg/hristomanov/education/sql/PostgresUpsertLabIT.java): защо запазваме един database ID?
4. Защо вече приложена Flyway V1 миграция не бива да се редактира? Как `validateWithResult()` отчита промяната?
5. Как би направил expand/contract за NOT NULL колона с вече съществуващи записи?
6. Защо Oracle `MERGE` concurrency не бива да се приравнява автоматично на PostgreSQL `ON CONFLICT`?

## Session A (20–30 мин) — VM-01 SQL semantics

1. Напиши две заявки, които връщат **всичките пет** клиента, дори без `PAID` order, и обясни защо първата може да върне повече от 5 реда.
2. Върни **само** клиентите с минимум един `PAID` order, без `DISTINCT` и без `GROUP BY`.
3. Как се променят `COUNT(*)` и `COUNT(manager_id)` и какво става при `WHERE manager_id = NULL`?

**Доказателство:** [H2SqlSemanticsTest](src/test/java/bg/hristomanov/education/sql/H2SqlSemanticsTest.java) — тестове `leftJoin...`, `exists...`, `nullEquality...`.

<details>
<summary>Насоки за self-check</summary>

`LEFT JOIN...ON` запазва 5 customer identities, но Ada има две PAID поръчки. `EXISTS` дава [1,2,3] без дублиране. `NULL = NULL` не е TRUE в SQL тристойностна логика.
</details>

## Session B (20–30 мин) — VM-02 integrity

1. Премахни `UNIQUE(customer_id,external_ref)` от **копие** на схемата. Какъв concurrency race вече не се предотвратява?
2. Опитай да insert-неш order с `customer_id=999`. Обясни защо „провери в Java преди INSERT“ не е достатъчно.
3. Допустимо ли е `total=NULL` при единствен `CHECK(total >= 0)` без `NOT NULL`? Защо?

**Доказателство:** `databaseConstraintsRejectInvalidState...` и PostgreSQL `postgresEnforcesForeignKey...`. Сравни `23503` / `23514` с Oracle equivalents `ORA-02291` / `ORA-02290`.

## Session C (20–30 мин) — VM-03 analytics

1. Добави `Eva` като клиент без поръчка и провери, че не изчезва от `revenue` CTE.
2. Промени данните така, че Ada и Boris да имат равни платени totals. Как се различават `ROW_NUMBER`, `RANK`, `DENSE_RANK`?
3. Изпълни `descendantsOfPostgres(1)` и опиши защо naive N+1 Java recursive fetch може да е проблем.
4. Какво е решението, ако структурата допуска цикли?

**Доказателство:** `windowFunctionGetsTopPaidCustomerInEachRegion` и `recursiveCteTraversesRootAndDescendants`.

## VM-20 — PostgreSQL ↔ Oracle verification challenge

1. Обясни с точна SQL three-valued logic защо празен `VARCHAR2` в Oracle 19c се третира различно от празен `text` в PostgreSQL.
2. Напиши query с explicit `NULLS FIRST` и `NULLS LAST`, който връща еднакъв ред в двата engine-а. И двата ли имат различен default за ASC?
3. Кога PostgreSQL `nextval` и Oracle `sequence.NEXTVAL` могат да пропуснат стойности?
4. Препиши PostgreSQL `::jsonb #>>` чрез Oracle 19c `JSON_VALUE` с ясна storage boundary.
5. Кой SQL изпълнява реално SELECT-а: PG `EXPLAIN (ANALYZE...)` или Oracle `EXPLAIN PLAN`? Как получаваме actual rows в Oracle?

[VM-20 lesson](docs/VM-20-POSTGRESQL-ORACLE-COMPARISON.md) · [PostgreSQL test](src/test/java/bg/hristomanov/education/sql/PostgresSqlLabIT.java) · [Oracle manual script](sql/oracle/vm20-dialect-lab.sql)

## Session D (20–30 мин) — VM-04 plan investigation

1. Изпълни [manual-explain.sql](sql/postgresql/manual-explain.sql) срещу PostgreSQL със schema/seed/performance fixtures.
2. Сравни `RETRY` (200/20 000) и `DONE` (19 800/20 000): estimated vs actual rows, selected scans, buffers, timing. **Не се опитвай да наложиш един scan type.**
3. Изследвай composite query за tenant 1: защо `(tenant_id,status,created_at DESC,id DESC)` е правдоподобен индекс, но не е гарантирано най-добрият?
4. Какъв би бил write overhead, ако добавяме 10 индекса „за всеки случай“?
5. Какво е вярното значение на `EXPLAIN ANALYZE UPDATE ...`? Безопасно ли е върху production?

**Доказателство:** [PostgresSqlLabIT](src/test/java/bg/hristomanov/education/sql/PostgresSqlLabIT.java) отпечатва real JSON plan-ове и assert-ва cardinality, не конкретни ms.

## Senior checkpoint — 4 сценария

- Endpoint връща дублирани редове: проблемът в ORM ли е, в JOIN ли е, или в data model-а? Как ще различиш?
- `CHECK(total > 0)` без `NOT NULL` защитава ли от nullable values?
- Съществува индекс, но query прави Seq Scan: кои доказателства събираш **преди** да смениш индекс?
- BI изисква „top-1 за всяка държава“: кога се нуждаеш от `ROW_NUMBER`, а кога `RANK`?

## Критерии за преминаване

Разбираш логически SQL flow; предсказваш правилните 5/4/3 counts; умееш да покажеш валидиращ constraint; можеш да обясниш window ranking и cycle policy; интерпретираш estimates/actuals без фиксирани benchmark обещания; знаеш границите между H2, PostgreSQL и Oracle.

Отговорите в реалната учебна диагностика се оценяват отделно съгласно [ASSESSMENT-PROTOCOL](../../docs/learning/ASSESSMENT-PROTOCOL.md).
