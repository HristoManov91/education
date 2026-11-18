# Проверка на знанията — VM-08..VM-10

1. Два `find()` в един Persistence Context гарантират ли две независими SQL queries? А гарантират ли PostgreSQL `REPEATABLE READ`?
2. След `entityManager.detach(entity)` променяш поле. Какво се записва? Какво променя `merge(entity)` и кое reference става managed?
3. След `flush()` възниква exception. Какъв е DB state след transaction rollback?
4. Hibernate AUTO flush прави ли SQL при всяка setter операция? Какво е overlapping query?
5. В кои случаи `CascadeType.REMOVE` и `orphanRemoval=true` имат различна семантика?
6. Коя страна притежава FK при `@OneToMany(mappedBy="order")` / `@ManyToOne`?
7. Може ли `equals/hashCode` да trigger-не LAZY loading? Какво става с generated ID и `HashSet`?

## Run

`mvn -pl persistence/repository-unit-of-work test`

## Практика

Пусни [VladPersistenceLifecycleTest](src/test/java/bg/hristomanov/education/uow/VladPersistenceLifecycleTest.java) и [VladMappingTest](src/test/java/bg/hristomanov/education/uow/VladMappingTest.java). Добави трети `TrainingOrderLine` и докажи, че orphan removal на един от трите не премахва останалите. След това предизвикай `LazyInitializationException` с detached parent и реши use case-а чрез explicit fetch/projection, а не чрез OSIV.

Материалът се счита за **усвоен** след real answers и проверка, не от CI alone.
