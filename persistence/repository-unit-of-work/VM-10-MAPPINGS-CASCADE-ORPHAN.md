# VM-10 — Association mappings, cascade, LAZY, orphanRemoval и equals/hashCode

## Реален казус

Order aggregate има списък с order lines. Developer добавя `@OneToMany` без `mappedBy` и Hibernate създава допълнителна join table. След това изтрива елемент от колекцията, но child record остава в DB. И накрая опитва да сериализира detached entity и получава `LazyInitializationException`.

Това са три различни механизма: ownership на foreign key, entity lifecycle cascade и lazy loading boundary.

## Mental model и owning side

[TrainingOrder.java](src/main/java/bg/hristomanov/education/uow/domain/TrainingOrder.java):

`@OneToMany(mappedBy="order", cascade=ALL, orphanRemoval=true)` — inverse side на асоциацията, но aggregate parent управлява lifecycle.

[TrainingOrderLine.java](src/main/java/bg/hristomanov/education/uow/domain/TrainingOrderLine.java):

`@ManyToOne(fetch=LAZY, optional=false) @JoinColumn(name="order_id",nullable=false)` — owning side е child, чиято таблица физически съдържа FK.

**Поддържай и двете страни синхронизирани** чрез `TrainingOrder.addLine/removeLine`. Не използвай произволно `List.remove` без да разбираш parent/child lifecycle.

## Bad → root cause → good

### 1. Cascade и orphanRemoval НЕ са едно и също

- `cascade=PERSIST/ALL`: при persist parent Hibernate може да persist-не children, които принадлежат на aggregate.
- `orphanRemoval=true`: когато managed parent премахне child от асоциацията, ORM планира DELETE на orphan child при flush.
- `CascadeType.REMOVE`: propagation при премахване на **самия parent**, не автоматично при всяко `collection.remove`.
- DB `ON DELETE CASCADE` е отделен механизъм; не го бъркай с Hibernate `CascadeType.REMOVE`.

Тестът [VladMappingTest.cascadePersistAndOrphanRemovalChangeActualChildRows](src/test/java/bg/hristomanov/education/uow/VladMappingTest.java) прави parent + 2 lines → persist/commit → 2 DB rows → remove one line inside transaction → flush/commit → 1 DB row.

### 2. Lazy loading трябва да има fetch boundary

`@OneToMany` default е LAZY, а `@ManyToOne` JPA default е EAGER, затова задаваме explicit `fetch=LAZY` на child side. Ако detached entity се сериализира/обхожда извън transaction и колекцията не е заредена, има риск от `LazyInitializationException`. Тестът го доказва без OSIV.

**Good:** зареди необходимото чрез explicit projection или `JOIN FETCH` / `EntityGraph` в правилната граница, а не чрез глобално EAGER или включване на OSIV. При paginated results с collection fetch има опасност от row multiplication и in-memory pagination — разглеждаме детайлно в VM-12/13.

### 3. equals/hashCode и Java collections

- Нестабилен generated ID в `hashCode` може да счупи `HashSet` след `persist`.
- Lombok `@Data` върху entities може да включи LAZY relations в `equals/hashCode/toString` и да доведе до допълнителни SQL, циклична рекурсия или грешки извън transaction.
- В този малък aggregate умишлено оставяме object identity equality; за бизнес identity при по-голям модел може да дефинираме стабилен immutable natural key, ако той действително съществува.
- Изборът е business-specific; не копирай „винаги сравнявай по id“ без null/transient/proxy анализ.

## Decision contract

| Въпрос | Отговор |
|---|---|
| Проблем | Неочаквани SQL, duplicate relationships, orphan rows и lazy exceptions |
| Кога cascade + orphanRemoval | Child lifecycle реално се притежава от aggregate root |
| Кога НЕ | Споделени reference entities (например `Product` или `User`), които не принадлежат на parent |
| Печалба | По-малко manual child persist/delete calls, правилен FK ownership |
| Загуба | Скрит persistence graph, SQL overhead, риск от cascade delete на ценни данни |
| Алтернатива | Explicit child repository + FK updates/deletes, отделни DTO projections |
| Доказателство | [TrainingOrder](src/main/java/bg/hristomanov/education/uow/domain/TrainingOrder.java) + [TrainingOrderLine](src/main/java/bg/hristomanov/education/uow/domain/TrainingOrderLine.java) + [VladMappingTest](src/test/java/bg/hristomanov/education/uow/VladMappingTest.java) |

## README → код / изходно решение

Това не е „всяко поле LAZY, навсякъде orphanRemoval“. Правилото е **relationship ownership + конкретен query plan + проверка на SQL/DB state**. Използвай cascading само за истински lifecycle aggregate ownership; read-only endpoints да имат explicit fetch/projection. Прегледай реалното DB schema, когато се генерира join table или orphan rows.

## Оригинални източници

- Vlad — [ManyToOne best practices](https://vladmihalcea.com/manytoone-jpa-hibernate/), [Hibernate collections optimistic locking](https://vladmihalcea.com/hibernate-collections-optimistic-locking/).
- [Hibernate ORM 7.1 Associations and orphan removal](https://docs.hibernate.org/orm/7.1/userguide/html_single/#associations).
- [Jakarta Persistence 3.2](https://jakarta.ee/specifications/persistence/3.2/).
