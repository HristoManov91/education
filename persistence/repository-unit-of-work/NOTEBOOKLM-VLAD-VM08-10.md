# NotebookLM — Vlad Mihalcea, VM-08..VM-10

## Импортирай
- [Repository & Unit of Work](README.md).
- [VM-08 lifecycle](VM-08-ENTITY-LIFECYCLE.md), [VM-09 flush](VM-09-FLUSH-DIRTY-CHECKING.md), [VM-10 mappings](VM-10-MAPPINGS-CASCADE-ORPHAN.md).
- [VladPersistenceLifecycleTest](src/test/java/bg/hristomanov/education/uow/VladPersistenceLifecycleTest.java) + [VladMappingTest](src/test/java/bg/hristomanov/education/uow/VladMappingTest.java).
- [Vlad — Hibernate First Level Cache](https://vladmihalcea.com/jpa-hibernate-first-level-cache/), [Flush Strategies](https://vladmihalcea.com/a-beginners-guide-to-jpahibernate-flush-strategies/), [Hibernate 7.1](https://docs.hibernate.org/orm/7.1/userguide/html_single/).

## Audio prompt

> Създай обстоен учебен аудио урок **на български** за Java 25 / Hibernate ORM / Spring Boot 4.x. Започни с production incident: detached order е редактиран, промяната не е запазена; следва flush преди query и rollback; по-късно при премахване на дете остава orphan. Свържи managed entity lifecycle, Identity Map, dirty checking, AUTO vs COMMIT vs ALWAYS vs MANUAL, flush vs commit, Hibernate ActionQueue ordering, owning-side of FK, cascade vs orphanRemoval vs database ON DELETE CASCADE, lazy loading/OSIV.
>
> Във всяка секция: проблем → root cause → bad/good → кога/кога НЕ → trade-offs → независим code/test proof. Разглеждай правилно, че merge **връща managed copy**, но original detached instance остава detached. Никога не представяй Hibernate first-level cache като database-level repeatable read.
>
> Разграничи какво доказват тестовете (business-visible DB state) и какво не (точен SQL order на вътрешния ActionQueue, Oracle behavior без Oracle instance). Завърши с упражнения и code review checklist.

Урокът няма автоматично статус `learned` без реална диагностика.
