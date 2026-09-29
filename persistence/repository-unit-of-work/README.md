# Repository + Unit of Work + JPA Persistence Context

Този модул е втората стъпка от backend/application patterns roadmap-а.

Целта е да разберем три неща:

1. какво всъщност означава Repository pattern;
2. какво означава Unit of Work;
3. защо при JPA/Hibernate често вече използваме Unit of Work, без да имаме class с име `UnitOfWork`.

---

# ВХОД В ТЕМАТА

## 1. Реалният казус

Имаме business use case: `mark order as PAID`.

Директният EntityManager вариант е напълно валиден:

```java
@Transactional
public void markPaid(long id) {
    PurchaseOrder order = entityManager.find(PurchaseOrder.class, id);
    order.markPaid();
}
```

Repository pattern не съществува, защото EntityManager е лош. Въпросът е къде искаме persistence boundary-то и на какъв език да говори application/domain кодът.

Baseline кодът е в [`DirectEntityManagerOrderService.java`](./src/main/java/bg/hristomanov/education/uow/service/DirectEntityManagerOrderService.java).

---

# 2. Repository mental model

```text
application / domain
        ↓
OrderRepository
        ↓
JPA / SQL / друг persistence adapter
```

Application code-ът иска `find order`, `add order`, `find by reference`, а не `EntityManager`, JPQL, CriteriaBuilder или Hibernate Session.

- Contract: [`OrderRepository.java`](./src/main/java/bg/hristomanov/education/uow/domain/OrderRepository.java)
- JPA adapter: [`JpaOrderRepository.java`](./src/main/java/bg/hristomanov/education/uow/persistence/JpaOrderRepository.java)

Repository си заслужава, когато добавя реална aggregate-oriented boundary, а не когато е само wrapper, който препредава 1:1 `save/findById/delete`.

---

# 3. Spring Data Repository vs Repository pattern

Spring Data repository abstraction намалява boilerplate-а в data access layer и често може директно да бъде достатъчната Repository boundary.

Важно е да не изпадаме в догма:

```text
JpaRepository директно
може да е правилно

custom domain Repository interface
може да е правилно
```

Изборът зависи от желаното dependency direction, domain vocabulary, reuse и инфраструктурна независимост.

---

# 4. Unit of Work mental model

Unit of Work следи промените в една business transaction и координира записването им.

```text
transaction starts
      ↓
load objects
      ↓
change managed objects in memory
      ↓
persistence context tracks state
      ↓
flush
      ↓
commit / rollback
```

Не е нужно механично да пишем собствен `UnitOfWork.java`, когато JPA/Hibernate вече дават тези semantics.

---

# 5. Persistence context = голяма част от Unit of Work + Identity Map

Jakarta Persistence дефинира persistence context като set от managed entity instances, като за една entity identity има една managed instance в конкретния context.

Hibernate описва persistence context и като transactional write-behind cache.

Mental model:

```text
managed entity changes
       ↓
in-memory state
       ↓
dirty checking
       ↓
flush
       ↓
INSERT / UPDATE / DELETE
```

---

# README → код

| Концепция | Код | Доказателство |
| --- | --- | --- |
| Aggregate | [`PurchaseOrder.java`](./src/main/java/bg/hristomanov/education/uow/domain/PurchaseOrder.java) | integration tests |
| Repository contract | [`OrderRepository.java`](./src/main/java/bg/hristomanov/education/uow/domain/OrderRepository.java) | [`RepositoryBoundaryTest.java`](./src/test/java/bg/hristomanov/education/uow/RepositoryBoundaryTest.java) |
| JPA adapter | [`JpaOrderRepository.java`](./src/main/java/bg/hristomanov/education/uow/persistence/JpaOrderRepository.java) | integration tests |
| Transaction boundary | [`OrderApplicationService.java`](./src/main/java/bg/hristomanov/education/uow/service/OrderApplicationService.java) | integration tests |
| Identity Map / flush / detach | [`PersistenceContextProbeService.java`](./src/main/java/bg/hristomanov/education/uow/service/PersistenceContextProbeService.java) | [`RepositoryUnitOfWorkTest.java`](./src/test/java/bg/hristomanov/education/uow/RepositoryUnitOfWorkTest.java) |

---

# 6. Dirty checking — защо няма save()

Ключовият метод е [`OrderApplicationService.markPaid()`](./src/main/java/bg/hristomanov/education/uow/service/OrderApplicationService.java):

```java
@Transactional
public void markPaid(long orderId) {
    PurchaseOrder order = orderRepository.getRequired(orderId);
    order.markPaid();
    // няма save(order)
}
```

Flow:

```text
@Transactional starts
→ repository loads managed PurchaseOrder
→ order.markPaid()
→ dirty checking detects changed state
→ flush
→ UPDATE
→ commit
```

Тестът `managedEntityChangeIsPersistedWithoutExplicitSaveCall()` доказва, че update-ът остава в DB без explicit save.

---

# 7. Трябва ли никога да викаме save()?

Не.

За managed entity explicit `save()` често не е нужен за persistence correctness, но може да съществува по други причини:

- repository contract convention;
- domain events около save;
- audit wrapper;
- non-JPA implementation;
- explicit application semantics.

Важното е да знаем защо е там, а не да махаме или добавяме `save()` механично.

---

# 8. Identity Map

В една transaction:

```java
PurchaseOrder first = orderRepository.getRequired(id);
PurchaseOrder second = orderRepository.getRequired(id);
```

тестът доказва:

```text
first == second
→ true
```

Това пази identity consistency вътре в persistence context-а.

---

# 9. Managed vs detached entity

Lifecycle mental model:

```text
new
→ managed
→ detached
→ managed again via load/merge
→ removed
```

В [`detachThenChange()`](./src/main/java/bg/hristomanov/education/uow/service/PersistenceContextProbeService.java):

```text
load managed order
→ entityManager.clear()
→ entity becomes detached
→ order.markPaid()
→ dirty checking no longer tracks it
→ DB remains NEW
```

---

# 10. Flush НЕ е commit

```text
flush != commit
```

Flush синхронизира pending changes към database transaction-а. Commit завършва transaction-а.

Лабораторията доказва:

```text
markPaid
→ entityManager.flush()
→ UPDATE е изпратен
→ throw exception
→ rollback
→ DB state остава NEW
```

Следователно SQL log с UPDATE не доказва успешен commit.

---

# 11. @Transactional е lifecycle boundary

`@Transactional` не е decoration. То определя scope, в който:

- persistence context е активен;
- entity instances са managed;
- dirty checking работи;
- flush/commit/rollback се координират.

Грешна transaction boundary може да доведе до detached state, lazy-loading проблеми и счупена consistency логика.

---

# 12. Repository и Unit of Work са различни patterns

```text
Repository
→ как application/domain получава aggregates без persistence details

Unit of Work
→ как промените в business transaction се track-ват и commit/rollback-ват
```

При JPA практичното mapping е:

```text
Repository boundary
→ EntityManager / Spring Data adapter

Unit of Work semantics
→ persistence context + transaction
```

---

# 13. Repository vs DAO

Полезна, но не абсолютна разлика:

```text
DAO
→ data-source operations / rows / queries

Repository
→ domain aggregate collection semantics
```

Имената не са закон. По-важни са abstraction level и responsibility.

---

# 14. Direct EntityManager кога е ОК

Не добавяй Repository abstraction, ако:

- application-ът е малък;
- persistence dependency е приемлива;
- няма domain boundary, която печели от abstraction;
- wrapper-ът ще е само delegation ceremony.

Pattern трябва да намалява coupling/complexity, не да увеличава class count.

---

# 15. In-memory Repository test — какво доказва и какво НЕ

[`RepositoryBoundaryTest.java`](./src/test/java/bg/hristomanov/education/uow/RepositoryBoundaryTest.java) показва, че application-facing contract може да има implementation без JPA.

Това доказва architectural dependency direction.

Не доказва:

- JPQL correctness;
- mappings;
- constraints;
- transactions;
- Oracle/PostgreSQL differences.

Затова persistence adapters продължават да имат integration tests.

---

# 16. Optimistic locking

`PurchaseOrder` има `@Version`.

Това напомня, че Unit of Work не отменя concurrency проблемите. Трябва отделна policy:

- optimistic locking;
- pessimistic locking;
- serialization;
- domain-specific conflict resolution.

---

# 17. Repository не поправя N+1

Можем да имаме красив Repository contract и лош fetch plan.

Repository design трябва да върви с:

- fetch strategy;
- projections;
- entity graphs;
- pagination;
- query plans;
- indexes.

Abstraction не отменя performance engineering.

---

# 18. Връзка със Specification

Предишният lab е [`Specification + QueryDSL`](../specification-querydsl/README.md).

```text
Specification
→ как композирам query criteria

Repository
→ къде е persistence access boundary

Unit of Work
→ как се координират промените в transaction
```

Трите concepts се комбинират, но не са едно и също.

---

# 19. Какво доказват тестовете

[`RepositoryUnitOfWorkTest.java`](./src/test/java/bg/hristomanov/education/uow/RepositoryUnitOfWorkTest.java) доказва:

```text
managed entity change
→ no save()
→ commit
→ DB updated

two loads in same context
→ same Java instance

flush
→ exception
→ rollback
→ DB unchanged

detach
→ change Java object
→ DB unchanged
```

---

# 20. Как да стартираме

От root:

```bash
mvn -pl persistence/repository-unit-of-work -am test
```

Само модула:

```bash
mvn -f persistence/repository-unit-of-work/pom.xml test
```

---

# ИЗХОД ОТ ТЕМАТА

Правилният извод не е:

> трябва да си напиша UnitOfWork class

а:

> **трябва да разбирам Unit of Work semantics, които JPA/Hibernate вече изпълняват за мен.**

И правилният извод за Repository не е:

> винаги wrap-вай Spring Data

а:

> **създай Repository boundary, когато тя реално подобрява dependency direction, domain vocabulary или persistence isolation.**

---

# Mental model за запомняне

1. **Repository е persistence access boundary на езика на application/domain-а.**
2. **Persistence context дава Unit-of-Work + Identity-Map-like semantics.**
3. **Managed entity промяна не изисква explicit save за dirty checking.**
4. **Flush не е commit.**
5. **Detached object промени не се dirty-check-ват.**
6. **`@Transactional` определя lifecycle boundary.**
7. **Repository abstraction е trade-off, не задължителен wrapper.**

---

# Code-review checklist

```text
[ ] Repository contract-ът domain-oriented ли е?
[ ] Добавя ли boundary или е delegation ceremony?
[ ] Кой притежава transaction boundary-а?
[ ] Entity managed ли е при промяната?
[ ] Нужно ли е explicit save() и защо?
[ ] Какво trigger-ва flush?
[ ] Какво става при exception след flush?
[ ] Кога entity става detached?
[ ] Има ли optimistic locking strategy?
[ ] Persistence adapter има ли integration test?
[ ] Fetch plan/index/query semantics проверени ли са?
```

---

# Упражнения

1. Добави `OrderItem` collection и cascade semantics.
2. Добави orphan removal.
3. Направи optimistic-lock conflict test.
4. Сравни `persist()` и `merge()` върху detached entity.
5. Покажи AUTO flush преди overlapping query.
6. Добави Spring Data repository implementation и сравни boundaries.
7. Добави PostgreSQL profile.
8. Добави Oracle profile.
9. Покажи N+1 и го оправи.
10. Свържи Repository със Specification lab-а.

---

# Оригинални източници

- Martin Fowler — Unit of Work: https://martinfowler.com/eaaCatalog/unitOfWork.html
- Martin Fowler — Patterns of Enterprise Application Architecture catalog: https://martinfowler.com/eaaCatalog/
- Jakarta Persistence 3.2: https://jakarta.ee/specifications/persistence/3.2/jakarta-persistence-spec-3.2
- Jakarta Persistence `EntityManager`: https://jakarta.ee/specifications/persistence/3.2/apidocs/jakarta.persistence/jakarta/persistence/entitymanager
- Hibernate ORM User Guide: https://docs.jboss.org/hibernate/orm/current/userguide/html_single/Hibernate_User_Guide.html
- Spring Data JPA repository support: https://docs.spring.io/spring-data/jpa/reference/jpa.html
- Spring Data JPA repository definitions: https://docs.spring.io/spring-data/jpa/reference/repositories/definition.html

---

# Изходен въпрос

Когато видиш:

```java
entity.setStatus(...);
repository.save(entity);
```

не казвай автоматично, че `save` е грешен.

Питай:

> **Този entity managed ли е? Каква е transaction boundary-та? Какъв contract иска Repository-то? И какви Unit-of-Work semantics вече ни дава persistence provider-ът?**
