# VM-23 — Native SQL, functions, procedures и безопасно result mapping

## Production казус

JPA Criteria/QueryDSL са удобни за бизнес филтри, но отчетът използва PostgreSQL-specific JSON/array operators и database function. Разработчикът започва да изгражда SQL чрез string concatenation, загубва tenant isolation и разчита на column order без проверка на types.

## Mental model

- **JPQL/Criteria** описват entity model и Hibernate генерира SQL.
- **Native SQL** изразява dialect features директно. Hibernate/JDBC не правят автоматична portability.
- **SQL function** връща scalar или result set, обичайно се използва във `SELECT`.
- **Stored procedure** има отделен invocation, transaction и parameter contract (`CALL` / `CallableStatement`, DB-specific).
- **Result mapping** е изрична отговорност на application слоя: JDBC types и `NULL` обработка, а не unchecked cast.

## Bad → good

**Bad:** `"SELECT * FROM invoice WHERE tenant_id=" + tenantId`, `SELECT *` със positional assumptions, и „безопасно е, защото id е число“. При dynamic SQL стойностите са параметри, identifier-и за таблици/колони се allowlist-ват, а бизнес constraints важат и при native writes.

**Good:** JDBC `PreparedStatement` със `tenant_id = ?`, explicit selected columns, `OffsetDateTime` за PostgreSQL `TIMESTAMPTZ`, record DTO и проверка, че резултатът не пресича tenant boundary.

В [PostgresNativeSqlIT](../src/test/java/bg/hristomanov/education/sql/PostgresNativeSqlIT.java) имаме `vm23_invoice` със `CHECK(amount >= 0)` и typed SQL function `vm23_invoice_count(p_tenant BIGINT)`. Тестовете доказват:
- свързването на bind parameter и record mapping връща **само tenant 10** и два реда;
- function с bind `20` връща 1;
- PostgreSQL `int[] @>` array containment;
- negative native INSERT с отрицателна сума получава `SQLSTATE 23514`.

Тези доказателства са за **PostgreSQL 17**, не Oracle 19c. Oracle може да използва `CallableStatement`/PLSQL, `SYS_REFCURSOR` и различни types. SQL `SELECT function(?)` в този lab не е доказателство за portable JDBC stored procedure `CALL`.

## Decision contract

| Въпрос | Избор |
|---|---|
| Проблем | ORM abstraction не представя добре DB-specific query/report/function |
| Кога | Complex analytics, vendor SQL features, добре дефинирани stored logic |
| Кога НЕ | Прост `findById`, CRUD, където JPQL/Specifications са по-четими |
| Печалба | Full DB feature access и точен SQL shape |
| Загуба | Dialect lock-in, ръчен DTO mapping, миграции и safety review |
| Алтернатива | JPQL projection, QueryDSL или database view + read-only entity |
| Доказателство | [JDBC + PostgreSQL тестове](../src/test/java/bg/hristomanov/education/sql/PostgresNativeSqlIT.java) |

## Проверка на production use case

Дефинирай input/output schema, privileges (function execution), SQL injection surface, transaction scope, retries, cursor lifecycle, DB query plan, error SQLSTATE и versioned migration на function. Не скривай business-critical stored logic в production, ако никой не version-control-ва deployment-а ѝ.

## Оригинални източници

- [Vlad SQL tutorials](https://vladmihalcea.com/tutorials/sql/)
- [PostgreSQL functions](https://www.postgresql.org/docs/17/sql-createfunction.html)
- [JDBC CallableStatement](https://docs.oracle.com/en/java/javase/25/docs/api/java.sql/java/sql/CallableStatement.html)
- [Hibernate 7.4 native SQL](https://docs.hibernate.org/orm/7.4/userguide/html_single/#sql)

`mvn -pl database/sql-labs -Ppostgres-integration verify`
