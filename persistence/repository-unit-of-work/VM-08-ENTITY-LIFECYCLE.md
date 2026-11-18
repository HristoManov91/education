# VM-08 — Managed, detached, merge и first-level cache

## Входен казус

Имаме `PurchaseOrder`, редактиран в request след като първата транзакция е приключила. Developer-ът сменя статуса на Java обекта и очаква Hibernate автоматично да го запише. Но обектът вече е **detached**, а dirty checking следи само **managed** entities в текущия persistence context.

## Mental model

`new/transient → persist() → managed → detach()/clear()/close() → detached`. Отделен `merge(detached)` **копира данните в managed entity** и връща **managed copy**; оригиналният argument остава detached. При `remove(managed)` entity влиза в removed state; SQL DELETE идва при flush.

Когато един `EntityManager` зареди един и същ ред два пъти, връща същата managed Java instance (Identity Map). Това **не е** гаранция за database-level `REPEATABLE READ` за всички нови scalar/native SQL queries.

## Bad → root cause

`detached.markPaid()` след `entityManager.clear()` променя само Java state; липсва контекст, който да следи dirty fields. Това е абсолютно валидно поведение и вече се доказва от [detachThenChange()](src/main/java/bg/hristomanov/education/uow/service/PersistenceContextProbeService.java) + [RepositoryUnitOfWorkTest](src/test/java/bg/hristomanov/education/uow/RepositoryUnitOfWorkTest.java).

## Good

[mergeDetachedChangeAndCheckIdentity()](src/main/java/bg/hristomanov/education/uow/service/PersistenceContextProbeService.java):

`EntityManager.detach(old) → old.markPaid() → managed = EntityManager.merge(old) → managed != old → EntityManager.contains(managed) → flush/commit`.

Ново [VladPersistenceLifecycleTest](src/test/java/bg/hristomanov/education/uow/VladPersistenceLifecycleTest.java) доказва copy identity и persisted `PAID`, с версия 1. Важно: `merge` не е удобен „update всичко“ инструмент без последствия — stale detached graph може да презапише актуално състояние. При конфликт `@Version` трябва да защити транзакцията. Предпочитай load + modify в една кратка transaction, ако е възможно.

## Decision contract

| Въпрос | Решение |
|---|---|
| Проблем | Промени върху detached entity не се записват; погрешни очаквания за `merge` |
| Кога да ползвам merge | Когато действително имам detached entity graph и съм проверил конкурентните версии |
| Кога НЕ | За managed entity, която Hibernate вече проследява; за patch/update с непълен и stale detached object |
| Печалба | Ясен entity lifecycle, възстановяване на detached state в managed копие |
| Загуба | Допълнителни SQL checks, cascade complexity, stale overwrites при неподходящ модел |
| Алтернатива | `EntityManager.find` + application patch на нужните полета в transaction |
| Доказателство | [Code](src/main/java/bg/hristomanov/education/uow/service/PersistenceContextProbeService.java) · [Tests](src/test/java/bg/hristomanov/education/uow/VladPersistenceLifecycleTest.java) |

## README → код

- [PurchaseOrder with @Version](src/main/java/bg/hristomanov/education/uow/domain/PurchaseOrder.java).
- [PersistenceContextProbeService](src/main/java/bg/hristomanov/education/uow/service/PersistenceContextProbeService.java).
- [Existing proof: identity and detached](src/test/java/bg/hristomanov/education/uow/RepositoryUnitOfWorkTest.java).
- [New merge proof](src/test/java/bg/hristomanov/education/uow/VladPersistenceLifecycleTest.java).

## Приключване на инцидента

Преди да питаш „трябва ли `save()`?“, отговори: кой е transaction boundary; обектът managed ли е; има ли optimistic conflict policy; нужно ли е merge, или просто load + modify? Няма универсален `repository.save(entity)` contract, който прави всеки detached object безопасен.

### Оригинални източници

- Vlad — [Entity State Transitions](https://vladmihalcea.com/a-beginners-guide-to-jpa-hibernate-entity-state-transitions/).
- Vlad — [First-level Cache](https://vladmihalcea.com/jpa-hibernate-first-level-cache/).
- [Jakarta Persistence 3.2 — EntityManager](https://jakarta.ee/specifications/persistence/3.2/apidocs/jakarta.persistence/jakarta/persistence/entitymanager).
- [Hibernate 7.1 User Guide](https://docs.hibernate.org/orm/7.1/userguide/html_single/).
