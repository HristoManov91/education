# VM-01 — SQL correctness: NULL, LEFT JOIN, EXISTS и cardinality

**Входен production инцидент:** UI показва два пъти клиент Ada (има две `PAID` поръчки), но Eva изчезва от отчета (няма поръчки). SQL-ът е синтактично валиден. Грешката е **в семантиката**, не в JDBC или Hibernate.

## Какво трябва да можеш

Да обясниш точно как `ON`, `WHERE`, `NULL` и `JOIN` променят резултатите; да избираш между `JOIN` и `EXISTS` според бизнес въпроса; да отличаваш row count от distinct customer count.

## Mental model преди API

~~~text
customer (5 rows) --LEFT JOIN--> purchase_order (0..N rows)
  ├─ Ada   -> PAID, PAID, PENDING
  ├─ Boris -> PAID, CANCELLED
  ├─ Clara -> PAID
  ├─ Dima  -> PENDING
  └─ Eva   -> [no orders]
~~~

`JOIN` образува комбинации от съответстващи редове; клиент с две съответстващи поръчки може да се появи два пъти. `EXISTS` отговаря на въпрос „съществува ли поне един?“ и не умножава родителските редове.

## Bad → root cause → good

**Bad №1: подменяме LEFT JOIN с INNER semantics, без да искаме.**

~~~sql
-- Искаме ВСИЧКИ клиенти, дори тези без платени поръчки.
SELECT c.id, o.id
FROM customer c
LEFT JOIN purchase_order o ON o.customer_id = c.id
WHERE o.status = 'PAID';
~~~

`WHERE` отхвърля редовете с `NULL`-extended дясна страна. Остават 3 уникални клиента, вместо 5.

~~~sql
-- Филтърът на дясната страна принадлежи в ON, ако искаме всички клиенти.
SELECT c.id, o.id
FROM customer c
LEFT JOIN purchase_order o
    ON o.customer_id = c.id AND o.status = 'PAID';
~~~

Този вариант запазва всички 5 клиенти, но Ada все още има два реда. Това **не е** решение за списък „един ред на клиент“.

**Bad №2: JOIN за проверка за съществуване.**

~~~sql
SELECT c.id
FROM customer c
JOIN purchase_order o ON o.customer_id = c.id
WHERE o.status = 'PAID'; -- [1, 1, 2, 3]
~~~

~~~sql
-- Good: семантиката е semijoin; без излишно DISTINCT.
SELECT c.id
FROM customer c
WHERE EXISTS (
    SELECT 1 FROM purchase_order o
    WHERE o.customer_id = c.id AND o.status = 'PAID'
)
ORDER BY c.id; -- [1, 2, 3]
~~~

Обратният въпрос („нямат нито една PAID поръчка“) решаваме с `NOT EXISTS`; `NOT IN (subquery)` има клопка при `NULL` елементи.

**Bad №3: NULL се сравнява като нормална стойност.**

~~~sql
SELECT COUNT(*) FROM customer WHERE manager_id = NULL; -- 0
SELECT COUNT(*) FROM customer WHERE manager_id IS NULL; -- 2
SELECT COUNT(*) FROM customer; -- 5
SELECT COUNT(manager_id) FROM customer; -- 3
~~~

SQL използва **three-valued logic** (TRUE/FALSE/UNKNOWN). `WHERE` допуска само TRUE. `COUNT(column)` игнорира `NULL`, `COUNT(*)` брои всички редове.

## Логически SQL ред (mental model, не физическа последователност)

`FROM` + `JOIN/ON` → `WHERE` → `GROUP BY` → `HAVING` → window calculations → `SELECT` → `DISTINCT` → `ORDER BY` → `LIMIT/FETCH`. Planner има право да пренарежда физическите операции, **но трябва да запази съответната SQL семантика**.

## Decision contract

| Въпрос | Критерий |
| --- | --- |
| Проблем | Дублирани/липсващи клиентски редове, неправилно `NULL` поведение |
| Кога `EXISTS` | Когато от child table проверяваме само наличието на ред, а не проектираме child columns |
| Кога НЕ | Когато трябва да върнем child columns или да агрегираш върху отделните child редове; тогава JOIN/агрегация |
| Печалба | Правилна cardinality, не се налага `DISTINCT` да маскира грешен join |
| Загуба | Няма директен достъп до child columns, ако изберем `EXISTS` |
| Алтернатива | JOIN + `DISTINCT` може да е правилно за някои изходи, но често скрива причината за дублирането |
| Доказателство | [H2SqlSemanticsTest](../src/test/java/bg/hristomanov/education/sql/H2SqlSemanticsTest.java) + [PostgresSqlLabIT](../src/test/java/bg/hristomanov/education/sql/PostgresSqlLabIT.java) |

## README → код

- [Production-like JDBC repository: EXISTS и NOT EXISTS](../src/main/java/bg/hristomanov/education/sql/SqlReadRepository.java).
- [schema.sql](../src/main/resources/sql/postgresql/schema.sql), [seed.sql](../src/main/resources/sql/postgresql/seed.sql) — лесни за inspect конкретни 5 клиента и 7 поръчки.
- [H2SqlSemanticsTest](../src/test/java/bg/hristomanov/education/sql/H2SqlSemanticsTest.java) — тестовете са **negative proof**: 4 JOIN rows vs 3 EXISTS rows; 5 LEFT JOIN clients vs 3 filtered.
- [PostgresSqlLabIT](../src/test/java/bg/hristomanov/education/sql/PostgresSqlLabIT.java) — отделно потвърждение на същите query semantics на PostgreSQL.

## Изход и code review

Началният казус е решен: `EXISTS` дава един ред на клиент, `LEFT JOIN ... ON` запазва клиенти без paid поръчки, `IS NULL` е коректният null predicate. Преди code review попитай: „Каква cardinality обещава този endpoint?“ и „Какво става с NULL/липсващо отношение?“.

### Оригинални източници

- Vlad Mihalcea — [SQL EXISTS and NOT EXISTS](https://vladmihalcea.com/sql-exists/), [SQL topic index](https://vladmihalcea.com/tutorials/sql/).
- [PostgreSQL 17 SELECT](https://www.postgresql.org/docs/17/sql-select.html).
- [PostgreSQL 17 comparison/NULL functions](https://www.postgresql.org/docs/17/functions-comparison.html).
