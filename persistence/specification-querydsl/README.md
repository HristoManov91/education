# Specification Pattern + QueryDSL — composable dynamic search

Това е първият executable модул от `BACKEND-PATTERNS-ROADMAP.md`.

Целта не е да научим само синтаксис на QueryDSL, а да разберем по-дълбокия design problem:

> Имаме много optional search criteria. Кои от тях са reusable business predicates и как да ги композираме, без repository API-то или един query method да експлодират?

---

# ВХОД В ТЕМАТА

## 1. Реалният казус

Търсим плащания по optional критерии:

```text
status
method
country
minimum amount
created from
created to
reference contains
```

Пример:

```text
PAID
AND CARD
AND BG
AND amount >= 100 EUR
AND created during September 2026
```

## 2. Naive вариант №1 — repository method explosion

```text
findByStatus(...)
findByCountryCode(...)
findByStatusAndCountryCode(...)
findByStatusAndMethod(...)
findByStatusAndMethodAndCountryCode(...)
...
```

Derived query methods са отлични за стабилни queries като:

```java
findByReference(String reference)
```

Проблемът идва при combinatorial dynamic search.

## 3. Naive вариант №2 — един огромен Criteria method

[`NaivePaymentCriteriaRepository.java`](./src/main/java/bg/hristomanov/education/specification/repository/NaivePaymentCriteriaRepository.java)

Това е **валиден JPA Criteria код**.

Проблемът не е Criteria API-то, а че всички правила са inline:

```text
if status != null → add predicate
if country != null → add predicate
if amount != null → add predicate
...
```

Когато втори use case поиска същото правило, започва copy/paste.

---

# Specification mental model

> **Specification е именуван predicate с business meaning, който може да се комбинира с други predicates.**

Примери:

```text
hasStatus(PAID)
hasCountry(BG)
amountAtLeast(100)
createdOnOrAfter(...)
referenceContains("INV")
```

После:

```text
hasStatus(PAID)
AND hasCountry(BG)
AND amountAtLeast(100)
```

Най-важната промяна е:

```text
"как да построя този query?"
        ↓
"какви business predicates композирам?"
```

---

# README → код

| Концепция | Код | Доказателство |
| --- | --- | --- |
| Entity | [`PaymentEntity.java`](./src/main/java/bg/hristomanov/education/specification/domain/PaymentEntity.java) | integration test |
| Search input | [`PaymentSearchCriteria.java`](./src/main/java/bg/hristomanov/education/specification/search/PaymentSearchCriteria.java) | integration test |
| Naive Criteria | [`NaivePaymentCriteriaRepository.java`](./src/main/java/bg/hristomanov/education/specification/repository/NaivePaymentCriteriaRepository.java) | [`PaymentSearchServiceTest.java`](./src/test/java/bg/hristomanov/education/specification/PaymentSearchServiceTest.java) |
| Spring Specifications | [`PaymentSpecifications.java`](./src/main/java/bg/hristomanov/education/specification/search/PaymentSpecifications.java) | same test |
| QueryDSL predicates | [`PaymentQuerydslPredicates.java`](./src/main/java/bg/hristomanov/education/specification/search/PaymentQuerydslPredicates.java) | same test |
| QueryDSL repository | [`PaymentQuerydslRepository.java`](./src/main/java/bg/hristomanov/education/specification/repository/PaymentQuerydslRepository.java) | same test |
| Generated Q model | Maven annotation processor | [`QuerydslMetamodelTest.java`](./src/test/java/bg/hristomanov/education/specification/QuerydslMetamodelTest.java) |
| Side-by-side comparison | [`PaymentSearchService.java`](./src/main/java/bg/hristomanov/education/specification/service/PaymentSearchService.java) | integration test |

---

# Spring Data JPA Specification

[`PaymentSpecifications.java`](./src/main/java/bg/hristomanov/education/specification/search/PaymentSpecifications.java)

Пример:

```java
public static Specification<PaymentEntity> hasStatus(PaymentStatus status) {
    if (status == null) {
        return Specification.unrestricted();
    }

    return (root, query, builder) ->
            builder.equal(root.get("status"), status);
}
```

Optional criterion, който липсва:

```text
status == null
→ unrestricted
→ не добавя restriction
```

Това позволява една композиция да работи и за 0, и за 7 активни filters.

## Composition

```java
Specification<PaymentEntity> specification =
        PaymentSpecifications
                .hasStatus(PaymentStatus.PAID)
                .and(PaymentSpecifications.amountAtLeast(
                        new BigDecimal("100.00")
                ));
```

Predicate-ът има име и може да бъде reuse-нат извън основния search.

---

# Актуална Spring Data JPA бележка

Spring Data JPA 4.x има както класическия `Specification<T>`, така и по-новия `PredicateSpecification<T>`.

`PredicateSpecification` е по-малък, query-type-agnostic contract.

В този lab използваме `Specification<T>`, защото:

- е широко разпространен;
- естествено работи с `JpaSpecificationExecutor`;
- показва classic Specification composition;
- после можем директно да сравним същия mental model с QueryDSL.

---

# QueryDSL — Specification идеята с generated typed paths

Entity:

[`PaymentEntity.java`](./src/main/java/bg/hristomanov/education/specification/domain/PaymentEntity.java)

Build-ът генерира:

```text
QPaymentEntity
```

в:

```text
target/generated-sources/annotations
```

Generated code не се commit-ва.

[`QuerydslMetamodelTest.java`](./src/test/java/bg/hristomanov/education/specification/QuerydslMetamodelTest.java) гарантира, че annotation processing реално работи.

## Type safety

Criteria:

```java
root.<BigDecimal>get("amount")
```

QueryDSL:

```java
PAYMENT.amount
```

При rename на entity field QueryDSL usage-ът се чупи на compile time.

---

# Reusable QueryDSL predicates

[`PaymentQuerydslPredicates.java`](./src/main/java/bg/hristomanov/education/specification/search/PaymentQuerydslPredicates.java)

```java
public static BooleanExpression amountAtLeast(BigDecimal minimumAmount) {
    return minimumAmount == null
            ? null
            : PAYMENT.amount.goe(minimumAmount);
}
```

Това е същият Specification mental model:

```text
named
reusable
composable
predicate
```

QueryDSL не е самият Specification pattern. То е query toolkit, върху което можем да приложим pattern-а.

---

# QueryDSL repository

[`PaymentQuerydslRepository.java`](./src/main/java/bg/hristomanov/education/specification/repository/PaymentQuerydslRepository.java)

Flow:

```text
PaymentSearchCriteria
        ↓
named BooleanExpression predicates
        ↓
BooleanBuilder
        ↓
JPAQueryFactory
        ↓
query execution
```

Repository layer-ът притежава query shape-а и ordering-а.

Predicate methods притежават отделните reusable rules.

---

# Защо null handling е explicit

Вместо да разчитаме на неочевидно library behavior:

```java
private void andIfPresent(
        BooleanBuilder builder,
        BooleanExpression expression
) {
    if (expression != null) {
        builder.and(expression);
    }
}
```

Mental model:

```text
missing filter
→ no predicate
→ repository explicitly skips it
```

В production може да изберем друга convention, но тя трябва да е еднаква навсякъде.

---

# Какво доказват тестовете

[`PaymentSearchServiceTest.java`](./src/test/java/bg/hristomanov/education/specification/PaymentSearchServiceTest.java)

Seed-ваме пет плащания и пускаме еднакъв search през:

```text
1. naive Criteria API
2. Spring Data Specification
3. QueryDSL
```

Изискваме:

```text
result(naive)
==
result(specification)
==
result(querydsl)
```

Тоест сравняваме design и maintainability, без да променяме business semantics.

Тестът доказва и:

- optional filters;
- reusable standalone Specification;
- empty criteria = no restriction;
- deterministic ordering.

---

# Specification Pattern ≠ Spring Specification

Specification е design concept.

Spring:

```text
Specification<T>
```

е една конкретна implementation форма.

QueryDSL:

```text
BooleanExpression
```

може да се използва със същия design intent.

Specification може да съществува и върху:

- pure Java domain objects;
- SQL DSL;
- Elasticsearch;
- Mongo filters;
- validation;
- rules engines.

---

# Кога Specification помага

Добър fit:

- много optional criteria;
- едни и същи predicates се reuse-ват;
- predicates имат business meaning;
- combinations растат;
- AND/OR composition е важна;
- repository interface започва да има много комбинации.

Сигнал:

```text
findByAAndB
findByAAndC
findByAAndBAndC
findByAAndBAndD
...
```

---

# Кога НЕ си струва

## Прост lookup

```java
findByReference(reference)
```

е по-четим от излишен abstraction layer.

## Силно query-specific reporting

При:

- CTE;
- window functions;
- complex joins;
- aggregates;
- database-specific functions;
- specialized projections;

dedicated QueryDSL/SQL query често е по-ясният design.

Не се опитваме всяко SQL изречение да стане Specification.

---

# Specification class ли трябва да бъде?

Не.

В Java може да е:

```java
static Specification<PaymentEntity> hasStatus(...)
```

или:

```java
static BooleanExpression hasStatus(...)
```

Pattern intent-ът не изисква class-per-predicate ceremony.

---

# AND / OR / NOT

Силата на Specification се вижда при composition:

```text
PAID
AND
(BG OR DE)
AND
amount >= 100
```

Grouping-ът трябва да бъде видим.

```text
A AND B OR C
```

без ясна структура е code-review smell.

---

# Spring Specification vs QueryDSL

| | Spring Specification | QueryDSL |
| --- | --- | --- |
| Основен predicate API | JPA Criteria wrapper | QueryDSL expressions |
| Field access | string/metamodel | generated Q-types |
| Dynamic composition | добра | отлична |
| Complex joins/projections | възможни | много удобни |
| Code generation | не е задължително | да |
| Execution | `JpaSpecificationExecutor` | custom repository / `JPAQueryFactory` |
| Framework coupling | Spring Data | QueryDSL |
| Learning curve | по-ниска | малко по-висока |

Няма универсален победител.

---

# QueryDSL trade-off

Lab-ът използва QueryDSL 5.1.0 Jakarta artifacts.

Към момента на създаване на урока 5.1.0 остава latest release в официалния QueryDSL release feed.

Това означава:

- library-то е зряло;
- Jakarta artifacts съществуват;
- но release cadence е бавен.

При major upgrade трябва да проверяваме compatibility с:

- Spring Boot;
- Hibernate;
- Jakarta Persistence;
- Java.

Type safety не означава zero maintenance.

---

# Oracle / PostgreSQL mental model

Specification composition е database-agnostic като идея.

Generated SQL не е.

Трябва да следим:

- indexes;
- case sensitivity;
- date/time types;
- null ordering;
- pagination;
- DB functions;
- execution plans.

```text
красив QueryDSL
+
лош execution plan
=
бавна система
```

Specification pattern не е performance optimization сам по себе си.

---

# Specification vs Strategy

```text
Specification
→ удовлетворява ли candidate-ът критерий?

Strategy
→ кой algorithm/policy да изпълним?
```

Пример:

```text
Specification:
payment.status == PAID

Strategy:
CardPaymentProcessor vs BankTransferPaymentProcessor
```

---

# Specification vs Chain of Responsibility

Specification:

```text
A AND B AND C
```

е logical composition.

Chain:

```text
handler A
→ handler B
→ handler C
```

има order/lifecycle и може да stop-не, mutate-не или да направи side effect.

---

# Как да стартираме

От root:

```bash
mvn -pl persistence/specification-querydsl -am test
```

Само модула:

```bash
mvn -f persistence/specification-querydsl/pom.xml test
```

H2 се използва само за deterministic CI/integration test.

---

# Какво точно решихме

Преди:

```text
optional filters
→ giant if method / repository method combinations
```

След:

```text
named reusable predicates
→ composition
→ repository execution
```

Получихме:

- по-ясни business criteria;
- reuse;
- по-малко combinatorial API;
- QueryDSL compile-time paths;
- еднакви semantics, доказани с integration tests.

Но не получихме автоматично:

- добри indexes;
- добър SQL plan;
- правилна database schema;
- причина всеки query да стане Specification.

---

# Mental model за запомняне

1. **Specification е predicate с име и business meaning.**
2. **Силата му е composition + reuse.**
3. **QueryDSL е toolkit; Specification е design idea.**
4. **Dynamic query не означава автоматично Specification.**
5. **Красив predicate composition не заменя SQL/index анализа.**

---

# Как да разпозная казуса

```text
[ ] Repository има ли много findBy... combinations?
[ ] Search method има ли много optional-filter if блокове?
[ ] Predicate logic copy/paste-ва ли се?
[ ] Има ли ясни business criteria, които заслужават име?
[ ] AND/OR правилата трудни ли са за четене?
[ ] QueryDSL repository има ли giant inline predicate block?
```

---

# Code-review checklist

```text
[ ] Predicate-ът има ли business име?
[ ] Null / missing filter semantics ясни ли са?
[ ] Reuse има ли реално, или abstraction-ът е ceremony?
[ ] AND/OR grouping-ът explicit ли е?
[ ] Q-types генерират ли се от build-а?
[ ] Persistence API details остават ли в repository layer?
[ ] Ordering deterministic ли е?
[ ] Има ли integration test върху реално query execution?
[ ] Проверени ли са indexes и execution plan?
[ ] Има ли Oracle/PostgreSQL-specific поведение?
```

---

# Упражнения

1. Добави `maximumAmount`.
2. Добави `status IN (...)`.
3. Направи `(CARD OR BANK_TRANSFER) AND amount >= 100`.
4. Добави join към Customer entity.
5. Добави projection вместо full entity.
6. Добави pagination.
7. Добави PostgreSQL Testcontainers profile.
8. Добави Oracle profile.
9. Сравни execution plan преди/след index.
10. Направи pure-domain Specification без JPA dependency.

---

# Version notes

- Java 25
- Spring Boot 4.1.1
- Spring Data JPA 4.1.x
- QueryDSL 5.1.0 Jakarta
- H2 за CI laboratory

---

# Оригинални източници

- Spring Data JPA — Specifications:  
  https://docs.spring.io/spring-data/jpa/reference/jpa/specifications.html
- Spring Data JPA — `Specification<T>` API:  
  https://docs.spring.io/spring-data/data-jpa/docs/current/api/org/springframework/data/jpa/domain/Specification.html
- QueryDSL:  
  https://github.com/querydsl/querydsl
- QueryDSL releases:  
  https://github.com/querydsl/querydsl/releases
- QueryDSL JPA 5.1.0:  
  https://central.sonatype.com/artifact/com.querydsl/querydsl-jpa/5.1.0
- Eric Evans — Domain-Driven Design, Specification concept.

---

# Изходен въпрос

Когато видиш dynamic search, не питай първо:

> „Criteria или QueryDSL?“

Питай:

> **„Кои predicates са истински business concepts, кои ще се reuse-ват и къде трябва да живее composition-ът спрямо query execution-а?“**
