# VM-17 — Dynamic filtering с QueryDSL/Spring Specification, Page vs Slice, COUNT и bind parameters

## Реален случай

Имаме таблица с много optional filters и изискване за странициране. Съществуващата [Specification + QueryDSL лаборатория](README.md) вече показва правилно композираните predicates. Но новият frontend иска **Page** с total и **Slice** за infinite scroll, а QueryDSL implementation просто прави `fetch()` върху всички редове.

## Първо правилната семантика

Една и съща бизнес спецификация трябва да определя **content и count**, иначе UI може да показва 2 резултата при total=400. В [PaymentQuerydslRepository.java](src/main/java/bg/hristomanov/education/specification/repository/PaymentQuerydslRepository.java) изнесохме всички optional predicates в един метод `predicate(criteria)`, използван и от page content, и от explicit COUNT.

~~~java
List<PaymentEntity> content = queryFactory.selectFrom(PAYMENT)
    .where(predicate(criteria))
    .orderBy(PAYMENT.createdAt.desc(), PAYMENT.id.asc())
    .offset(pageable.getOffset())
    .limit(pageable.getPageSize())
    .fetch();

Long total = queryFactory.select(PAYMENT.id.count())
    .from(PAYMENT).where(predicate(criteria)).fetchOne();
~~~

Не използваме deprecated `fetchResults/fetchCount`, а отделно оформен count query. За complex JOIN/GROUP BY броенето може да се наложи да използва друга, семантично еквивалентна query shape.

## Разлика между Page и Slice

| Контракт | Какво се изпълнява | Кога |
|---|---|---|
| Page | SQL с `OFFSET/LIMIT` + втори `COUNT` | Точен брой редове/страници |
| Slice | SQL с `LIMIT size+1`, определя `hasNext`, **без COUNT** | Infinite scroll / Next |
| Keyset | Cursor от sort keys, без растящ OFFSET | Големи append-heavy datasets, sequential navigation |

[PaymentSearchService](src/main/java/bg/hristomanov/education/specification/service/PaymentSearchService.java) съпоставя `pageWithSpecifications` с `pageWithQuerydsl` и `sliceWithQuerydsl`.

[Test](src/test/java/bg/hristomanov/education/specification/PaymentSearchServiceTest.java) доказва:
- С еднакъв PAID/CARD/BG филтър Spring Specification и QueryDSL връщат същите две страници в еднакъв ред и `total=2`.
- QueryDSL `Page` използва **2 SQL statements**; `Slice` **1 SQL statement**, измерени чрез Hibernate statistics с чист counter.
- Стойност от типа `' OR 1=1 --` е данни за bind parameter, не SQL код; няма неволно „всички редове“.
- Невалиден page size/index се отхвърля.

## Защо не query string concatenation?

**Bad:** `"... WHERE reference LIKE '%" + input + "%'"` е injection risk; допуска различни HQL shapes и трудни query-plan/cache сравнения.

**Good:** QueryDSL `BooleanExpression` с bound value и composable predicate; string manipulation само вътре в безопасни expression APIs. Ако искаме да мерим query plan cache, сравняваме `getQueryPlanCacheHitCount()` и `getQueryPlanCacheMissCount()`, но **не** бъркаме това с DB plan cache, index selection или network latency. Версионно различните HQL/Criteria реализации трябва да се проверят на реалния runtime.

## Decision contract

| Въпрос | Избор |
|---|---|
| Проблем | Несъответстващ count, unbounded dynamic search, SQL injection и скъп COUNT |
| Кога Specification | Business-named reusable predicates в Spring Data, без излишен custom DSL |
| Кога QueryDSL | Typed paths, сложни composable predicates, projections и динамичен query shape |
| Кога НЕ | Прост `findByReference` или отчет с vendor-specific CTE/window SQL |
| Печалба | Correct paging, shared filter semantics, safe bind, ясна отговорност |
| Загуба | Q-class generation/dependency, втори count query, version coupling |
| Алтернативи | Spring Data derived queries/Specifications, explicit @Query, plain SQL |
| Доказателство | [PaymentSearchServiceTest](src/test/java/bg/hristomanov/education/specification/PaymentSearchServiceTest.java) + [QuerydslMetamodelTest](src/test/java/bg/hristomanov/education/specification/QuerydslMetamodelTest.java) |

## Review checklist

Има ли total count само ако действително трябва? Content и count използват ли един и същи predicate? Sort order уникален ли е (това упражнение използва createdAt+id)? Page bounded ли е? Bind ли са стойностите? Кеш counters са за Hibernate HQL или за DB execution plan?

### Оригинални източници

- Влад Mihalcea — [Spring Data findAll](https://vladmihalcea.com/spring-data-findall-anti-pattern/); [JOIN FETCH and pagination](https://vladmihalcea.com/join-fetch-pagination-spring/).
- [Spring Data JPA query methods и count](https://docs.spring.io/spring-data/jpa/reference/jpa/query-methods.html).
- [Hibernate Statistics Javadocs 7.4](https://docs.hibernate.org/orm/7.4/javadocs/org/hibernate/stat/Statistics.html).
- [QueryDSL repository](https://github.com/querydsl/querydsl).
