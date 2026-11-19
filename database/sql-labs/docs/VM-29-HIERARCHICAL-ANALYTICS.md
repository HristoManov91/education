# VM-29 — Recursive hierarchical SQL, cycle detection и window ranking

## Реален казус

Справка за organisational tree изпълнява Java рекурсия и прави N+1 отделни SQL заявки за всяко дете. При циклични manager записи traversal може да зацикли, а `LIMIT` върху JOIN връща разкъсана йерархия.

Друг endpoint иска top scorer по team; ако има равенство, `MAX(score)` не определя един човек.

## Bad → Good

**Bad:** за всяко node `SELECT children WHERE parent_id=?`, после recursive Java fetch, без cycle guard или max depth. **Good:** PostgreSQL recursive CTE с вързан `rootId` и `maximumDepth`, натрупан масив от visited IDs и `NOT child.id = ANY(visited)`.

[PostgresHierarchyReader.java](../src/main/java/bg/hristomanov/education/sql/PostgresHierarchyReader.java) връща само `(id, depth)`, не целия граф или ORM entities. Client не конкатенира ID в SQL. Алгоритъмът има explicit depth boundary, но **не е универсален graph traversal** за много родителски връзки; целта е parent/child tree с възможен лош цикъл.

Window functions:
- `ROW_NUMBER() OVER (PARTITION BY team ORDER BY score DESC, worker_id)` връща един deterministic winner.
- `RANK() OVER (PARTITION BY team ORDER BY score DESC)` дава **двама победители на първо място** при равни points.

## README → код и проверки

- [Java JDBC recursive reader](../src/main/java/bg/hristomanov/education/sql/PostgresHierarchyReader.java)
- [PostgresHierarchyAnalyticsIT](../src/test/java/bg/hristomanov/education/sql/PostgresHierarchyAnalyticsIT.java), 3 реални PostgreSQL tests:
  1. От root 1 виждаме nodes 1/2/4/3 в depth order; bound 1 връща първите 3.
  2. Изкуствен цикъл root→leaf е валиден FK state, но резултатът остава finite/без повторен root.
  3. `RANK` отчита двамата с equal score, `ROW_NUMBER` избира един по worker ID.
- [VM-03](VM-03-CTE-WINDOWS.md) е основният урок, който този разширява — няма отделен дублиращ Maven модул.

## Decision contract

| Въпрос | Решение |
|---|---|
| Проблем | N+1 recursion, цикли, недетерминистичен top-N |
| Кога | Йерархични SQL read models и grouped ranking |
| Кога НЕ | Силен cyclic graph с множество edges/paths без ясна business traversal policy |
| Печалба | Един SQL round-trip, bounded traversal, deterministic rank |
| Загуба | DB-specific array syntax, recursive memory/CPU и сложни plans |
| По-проста алтернатива | Fixed-level self-joins или materialized path, ако workload го оправдава |
| Доказателство | [PostgreSQL IT](../src/test/java/bg/hristomanov/education/sql/PostgresHierarchyAnalyticsIT.java) |

## Oracle сравнение

Oracle 19c предлага recursive subquery factoring без keyword `RECURSIVE` и `CONNECT BY NOCYCLE`; `CYCLE` има различна SQL форма. Нашият PostgreSQL array path не може да се копира директно. [Oracle manual example](../sql/oracle/vm29-hierarchy.sql) е за ръчна проверка в отделна схема; **няма Oracle CI**.

`mvn -pl database/sql-labs -Ppostgres-integration verify`

## Оригинални източници

- [Vlad — SQL recursive CTE](https://vladmihalcea.com/sql-recursive-with-cte/)
- [Vlad — Window functions](https://vladmihalcea.com/why-you-should-definitely-learn-sql-window-functions/)
- [PostgreSQL 17 WITH/recursive queries](https://www.postgresql.org/docs/17/queries-with.html)
- [Oracle 19c Hierarchical Queries](https://docs.oracle.com/en/database/oracle/oracle-database/19/sqlrf/Hierarchical-Queries.html)
