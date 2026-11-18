# VM-12 — Hibernate fetch plans: N+1, JOIN FETCH и @EntityGraph

## Реален казус

Endpoint връща 7 поръчки. Controller сериализира `order.customer.name`. HTTP response изглежда малък, но базата получава **6 SQL statements**: един SELECT за поръчките и още пет SELECT за различните customers.

Няма грешка в JDBC или connection pool — грешката е, че **fetch plan** не е съгласуван с read use case-а.

## Mental model: различни стратегии

| Стратегия | Семантика и цена |
|---|---|
| `LAZY` | отлага асоциацията, докато не потрябва; N+1 се проявява при обхождане на много entities |
| `EAGER` | JPA изисква relation да бъде заредена, но не гарантира един JOIN; може да създаде secondary SELECT-и |
| `JOIN FETCH` | в един query request-ва нужната асоциация; особено добър за `to-one` |
| `@EntityGraph` | декларативен per-query fetch plan, удобен за Spring Data |
| `@BatchSize` / batch-fetch | групира secondary SELECT-и; понякога подходящ за LAZY голям граф, но не е универсален заместител на explicit fetch |
| DTO projection | избира само нужните колони; няма managed entity graph и lazy relationships |

## Bad — неявен N+1

~~~java
List<PerfOrder> orders = reader.naiveOrders(); // 1 SELECT
orders.forEach(o -> o.getCustomer().getName()); // +5 SELECT за различни customers
~~~

[Перфектно валиден code example](../src/main/java/bg/hristomanov/education/hperf/read/PerfOrderReader.java), който става проблем при растящи входни данни.

## Good — explicit to-one JOIN FETCH

~~~java
select o from PerfOrder o join fetch o.customer order by o.id
~~~

или [Spring Data `@EntityGraph(attributePaths="customer")`](../src/main/java/bg/hristomanov/education/hperf/repository/PerfOrderRepository.java).

[H2 тестът](../src/test/java/bg/hristomanov/education/hperf/HibernatePerformanceTest.java) измерва Hibernate `getPrepareStatementCount()` и твърди точно **6 vs 1 vs 1** за фикстурата с пет различни customers, изчистен persistence context и изключено global batch fetching. [PG IT](../src/test/java/bg/hristomanov/education/hperf/PostgresHibernatePerformanceIT.java) повтаря 4 customers → **5 vs 1** в истински PostgreSQL. Това е correctness/SQL count evidence, **не** benchmark за latency.

## Защо не JOIN FETCH всичко?

`JOIN FETCH` върху паралелни големи колекции може да създаде картезианско умножение, много дублирани DB редове или `MultipleBagFetchException`. По-старите версии могат да направят in-memory pagination, но Hibernate 7.4 може да генерира nested SQL при подходящ dialect. [VM-13](VM-13-DTO-PAGINATION.md) доказва raw JOIN+LIMIT риска и безопасното поведение на реалния Hibernate 7.4 runtime.

## Decision contract

| Въпрос | Отговор |
|---|---|
| Какъв проблем | Скрит N+1 и неограничен брой DB round-trips |
| Кога | Четем известен to-one graph в bounded list/detail |
| Кога НЕ | Две големи collections наведнъж или paginated collection fetch |
| Печалба | Намален брой SQL queries при използваните отношения |
| Загуба | Възможен overfetch, повече JOIN work и прехвърлени колони |
| Алтернативи | DTO, batch/subselect fetching, отделни заявки или two-step page |
| Доказателство | [Repository + tests](../src/test/java/bg/hristomanov/education/hperf/HibernatePerformanceTest.java) с Hibernate counters |

## Review checklist

Проверихме ли `@ManyToOne` default EAGER? Колко **различни** relations има? Колко SQL statements на cold persistence context? Има ли row multiplication? Нужни ли са managed entities, или record projections са по-точни?

### Оригинални източници

- Vlad — [N+1 Query Problem](https://vladmihalcea.com/n-plus-1-query-problem/); [Detect N+1 in testing](https://vladmihalcea.com/how-to-detect-the-n-plus-one-query-problem-during-testing/).
- [Hibernate ORM 7.4 — Fetching](https://docs.hibernate.org/orm/7.4/userguide/html_single/#fetching).
- [Spring Data JPA @EntityGraph](https://docs.spring.io/spring-data/jpa/reference/jpa/entity-graph.html).
