# VM-13 — DTO projections, keyset pagination и collection fetch pitfalls

## Производствен проблем

Таблица с милиони поръчки изисква следващите 20 реда. Екипът изпълнява `findAll()` + Java pagination, което пренася всички записи. При опит да използва `JOIN FETCH` върху collection + `LIMIT`, Hibernate може да извърши пагинация в паметта (ако не забраним това).

Тук трябва да разграничим **bounded result set**, **deterministic ordering** и **тип на изходния модел**.

## Добро решение №1 — read DTO вместо managed entity

[OrderRow.java](../src/main/java/bg/hristomanov/education/hperf/read/OrderRow.java) е Java record (id, customerName, total, createdAt), без lazy relationships.

~~~java
select new bg.hristomanov.education.hperf.read.OrderRow(
  o.id, c.name, o.total, o.createdAt)
from PerfOrder o join o.customer c
order by o.createdAt desc, o.id desc
~~~

[PerfOrderReader.offsetPage()](../src/main/java/bg/hristomanov/education/hperf/read/PerfOrderReader.java) добавя `setFirstResult` / `setMaxResults` към SQL pagination. `ORDER BY createdAt DESC, id DESC` осигурява уникален tie-breaker.

**Компромис:** OFFSET при много дълбоки страници трябва да прескочи много редове и е нестабилен при конкурентни inserts/deletes.

## Добро решение №2 — keyset / seek pagination

[PerfOrderReader.after(cursorTimestamp,cursorId,size)](../src/main/java/bg/hristomanov/education/hperf/read/PerfOrderReader.java) добавя:

~~~sql
WHERE created_at < :timestamp
   OR (created_at = :timestamp AND id < :id)
ORDER BY created_at DESC, id DESC
LIMIT :size
~~~

Първа страница няма cursor; следващата ползва **и двете** стойности на последния ред. Не ползвай само timestamp — при равен час ще загубиш или дублираш редове.

**Важно:** keyset намалява зависимостта от растящ OFFSET и е стабилен при нови по-ранни записи в сортирането, но **не гарантира transaction snapshot across requests**, особено при промяна на sort fields. За гарантиран моментен snapshot трябват бизнес правила и подходяща DB isolation стратегия.

## Bad — наивен SQL JOIN + LIMIT върху редове, не върху родители

~~~sql
-- Грешно: първият клиент има три поръчки, LIMIT 2 ограничава joined rows.
SELECT c.id, o.id FROM perf_customers c
LEFT JOIN perf_orders o ON o.customer_id = c.id
ORDER BY c.id, o.id
LIMIT 2; -- два реда от един customer
~~~

**Версионно важно:** CI действително използва Hibernate ORM **7.4.5.Final** със Spring Boot 4.1.1. Hibernate 7.4 може да генерира вложен SQL SELECT за ограничаване на parent IDs и после да зареди колекцията. Затова `JOIN FETCH` + pagination вече не е задължително грешка. При DB dialect без такава възможност `hibernate.query.fail_on_pagination_over_collection_fetch=true` предпазва от in-memory fallback.

[H2 тестът](../src/test/java/bg/hristomanov/education/hperf/HibernatePerformanceTest.java) проверява: raw JOIN+LIMIT поврежда pagination на parent-и, Hibernate 7.4 връща двама родители с пълни колекции, а [SqlCaptureInspector](../src/test/java/bg/hristomanov/education/hperf/SqlCaptureInspector.java) доказва, че има генериран SQL limit/nested ranking. Това е version-specific capability, не универсална гаранция за всеки database dialect. Много children все още означава голям joined result set — two-phase ID page остава разумна алтернатива.

## Good — page IDs, после fetch на ограничена колекция

1. `select c.id ... order by c.id` със SQL `LIMIT 2`.
2. `select distinct c ... left join fetch c.orders where c.id in :ids`.
3. След това връщаме реда на IDs (IN не гарантира order).

[PerfOrderReader.collectionPageByIds()](../src/main/java/bg/hristomanov/education/hperf/read/PerfOrderReader.java) и [H2/PG tests](../src/test/java/bg/hristomanov/education/hperf/PostgresHibernatePerformanceIT.java) доказват точно 2 parent records. При parent с **хиляди** children и двуетапният fetch може да е тежък — има нужда от допълнително ограничаване на child access.

## Page срещу Slice

Spring Data `Page<T>` обичайно има total count и може да изисква **допълнителна count query**. `Slice<T>` чете `size+1`, за да установи дали има още резултати, без total count. [VM-16](VM-16-SPRING-DATA-TRAPS.md) и [VM-17 QueryDSL](../../specification-querydsl/VM-17-PAGE-SLICE-COUNT.md) имат изрични JDBC SQL-count tests.

## Decision contract

| Въпрос | Решение |
|---|---|
| Проблем | Unbounded reads, нестабилен sort, скрита in-memory pagination |
| Кога DTO/keyset | Read-only API с големи резултати и последователно „следваща страница“ UX |
| Кога НЕ | Managed entities са нужни за write use case; потребителят трябва директен достъп до страница 250 |
| Печалба | По-малко bytes/entity tracking, bounded result, без дълбок offset |
| Загуба | Cursor contract, индекси и поле за stable order, по-труден random access |
| Алтернативи | OFFSET за малки таблици/административни екрани; Page при реална нужда от total; двуетапен fetch при collections |
| Доказателство | [H2 tests](../src/test/java/bg/hristomanov/education/hperf/HibernatePerformanceTest.java) + [PostgreSQL IT](../src/test/java/bg/hristomanov/education/hperf/PostgresHibernatePerformanceIT.java) |

### Оригинални източници

- Vlad — [DTO projection mapping](https://vladmihalcea.com/the-best-way-to-map-a-projection-query-to-a-dto-with-jpa-and-hibernate/); [Join Fetch and Pagination](https://vladmihalcea.com/join-fetch-pagination-spring/).
- [Hibernate 7.4 — Fetching](https://docs.hibernate.org/orm/7.4/userguide/html_single/#fetching).
- [Spring Data JPA — Paging and Sorting](https://docs.spring.io/spring-data/jpa/reference/repositories/query-methods-details.html).
