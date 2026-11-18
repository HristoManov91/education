# VM-03 — CTE, recursive CTE и window functions

**Реален казус:** бизнесът иска **клиента с най-много платени продажби във всеки регион**, включително региони с клиенти без поръчки. Втори екран трябва да показва дървото от подчинени служители/клиенти под даден manager.

Един общ `findAll()`, следван от Java stream групиране, изглежда лесен, но прехвърля много данни и скрива SQL cardinality/pagination. Не всеки analytics use case изисква loading на entities.

## 1. CTE: именуваме междинната релационна идея

~~~sql
WITH revenue AS (
    SELECT c.id AS customer_id, c.region,
           COALESCE(SUM(CASE WHEN o.status = 'PAID' THEN o.total END), 0) AS paid_total
    FROM customer c
    LEFT JOIN purchase_order o ON o.customer_id = c.id
    GROUP BY c.id, c.region
)
SELECT * FROM revenue ORDER BY region, customer_id;
~~~

`CTE` (`WITH`) подобрява четимостта и повторната употреба **в рамките на заявката**. Не твърдим, че винаги материализира или е по-бързо; PostgreSQL версията, planner настройките и дали CTE се използва повторно са важни.

## 2. Window function: rank без загуба на редовете

~~~sql
WITH revenue AS (...),
ranked AS (
    SELECT region, customer_id, paid_total,
           ROW_NUMBER() OVER (
               PARTITION BY region
               ORDER BY paid_total DESC, customer_id ASC
           ) AS row_position
    FROM revenue
)
SELECT region, customer_id, paid_total
FROM ranked
WHERE row_position = 1
ORDER BY region;
~~~

Резултатът от фикстурата е `BG → Boris (180.00)` и `DE → Clara (90.00)`.

**Защо не `GROUP BY region, MAX(...)`?** Максимумът сам по себе си не идентифицира еднозначно клиента. **Защо не `RANK()`?** `RANK` може да върне няколко клиенти на първо място при равенство, докато `ROW_NUMBER` с стабилен tie-breaker избира **точно един**.

| Функция | При tie на първо място | Кога |
| --- | --- | --- |
| `ROW_NUMBER` | 1,2...; избира един при `WHERE position=1` | Един победител/детерминистично top-N |
| `RANK` | 1,1,3... | Всички споделени победители с gaps |
| `DENSE_RANK` | 1,1,2... | Споделени победители без gaps |

Window functions работят върху row set-а и обичайно се филтрират чрез външен query/CTE, не чрез `WHERE` в същия SELECT level.

## 3. Recursive CTE: йерархия без N round-trips

~~~sql
WITH RECURSIVE hierarchy(id, depth) AS (
    SELECT id, 0 FROM customer WHERE id = ?
    UNION ALL
    SELECT c.id, h.depth + 1
    FROM customer c JOIN hierarchy h ON c.manager_id = h.id
    WHERE h.depth < 100
)
SELECT id, depth FROM hierarchy ORDER BY depth, id;
~~~

От root `1` очакваме `(1,0), (2,1), (5,1), (4,2)`. Стойността `?` се подава като JDBC bind parameter; не правим string concatenation.

**Production предупреждение:** ако schema позволява цикъл в manager relationships, неограничената рекурсия може да е опасна. За произволни graphs добави cycle detection/maximum depth или `CYCLE` в подходящия PostgreSQL dialect; този пример използва acyclic fixture и защитен максимален depth 100, но ограничаването на дълбочината **не доказва липса на цикъл**, може да дублира възли и не е generic graph traversal.

**Oracle 19c:** recursive subquery factoring използва `WITH hierarchy (id,depth) AS (...)` **без `RECURSIVE`**; [ръчен Oracle пример](../sql/oracle/reports-and-plan.sql). И `CONNECT BY` е възможна алтернатива, но с различен синтаксис/възможности.

## Decision contract

| Въпрос | Отговор |
| --- | --- |
| Проблем | Top-N по група и йерархии без раздуване на application memory |
| Кога | Аналитична заявка, която базата може да изчисли в bounded result set |
| Кога НЕ | За малки неизменни обекти, налични вече в memory; при arbitrarily cyclic graph без cycle policy |
| Печелим | По-малко JDBC rows, ясна query semantics, върнат само нужният read model |
| Губим | По-сложен SQL, различия между Oracle/PostgreSQL, възможен sort/window cost |
| Алтернатива | Две стъпки Java grouping са валидни при малък bounded набор, не за безкраен `findAll` |
| Доказателство | [SqlReadRepository](../src/main/java/bg/hristomanov/education/sql/SqlReadRepository.java) + [H2](../src/test/java/bg/hristomanov/education/sql/H2SqlSemanticsTest.java) / [PG](../src/test/java/bg/hristomanov/education/sql/PostgresSqlLabIT.java) |

## README → код и финална проверка

- [Repository queries](../src/main/java/bg/hristomanov/education/sql/SqlReadRepository.java) — истински JDBC prepared statements, records за read model, deterministic ranking.
- [Seed](../src/main/resources/sql/postgresql/seed.sql) — данни с `PAID`, `PENDING`, без orders и manager hierarchy.
- [Integration tests](../src/test/java/bg/hristomanov/education/sql/PostgresSqlLabIT.java) — поведението се сверява на реална PostgreSQL.
- [Oracle SQL](../sql/oracle/reports-and-plan.sql) — documented manual comparison, без недоказан CI pass.

**След code review попитай:** `GROUP BY` ли ми трябва, или `window`? При tie какво е очакваното поведение? Може ли планът да сортира много милиони редове? За CTE нужна ли е реална materialization? Каква е cycle policy?

### Оригинални източници

- Vlad — [Why you should learn SQL Window Functions](https://vladmihalcea.com/why-you-should-definitely-learn-sql-window-functions/), [SQL Recursive WITH CTE](https://vladmihalcea.com/sql-recursive-with-cte/), [SQL tutorials](https://vladmihalcea.com/tutorials/sql/).
- [PostgreSQL 17 window functions](https://www.postgresql.org/docs/17/tutorial-window.html), [WITH queries](https://www.postgresql.org/docs/17/queries-with.html).
- [Oracle 19c analytic functions](https://docs.oracle.com/en/database/oracle/oracle-database/19/sqlrf/Analytic-Functions.html).
