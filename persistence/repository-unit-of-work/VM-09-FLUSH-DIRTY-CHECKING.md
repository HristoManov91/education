# VM-09 — Dirty checking, flush modes, write-behind и action queue

## Входен казус

Business service изпълнява `order.markPaid()` без `save`. Същата транзакция прави JPQL scalar query за статуса; Hibernate връща `PAID`. После възниква exception — въпреки че сме видели SQL UPDATE в логовете, DB status след rollback е `NEW`.

Трябва да различаваме **in-memory managed state**, **flush на SQL statements** и **commit на транзакцията**.

## Mental model

`managed state changes → dirty checking → Hibernate ActionQueue → SQL at flush → DB transaction COMMIT or ROLLBACK`.

`flush()` НЕ е commit, не „заключва“ автоматично всички бизнес стойности и не е заместител на изолация/версиониране. Hibernate обикновено прилага write-behind и може да групира INSERT/UPDATE; изпълнението не следва непременно реда на Java `persist/remove` извикванията.

## Съществените flush режими

| Режим | Практически contract | Риск |
|---|---|---|
| JPA `AUTO` | Provider осигурява видимост за релевантни заявки в текущия persistence context; Hibernate обичайно flush-ва на commit и преди overlapping JPQL queries | Допълнителни SQL flush по средата на transaction |
| JPA `COMMIT` | Provider може да отложи flush до commit; видимостта на pending changes за междинни заявки е provider-specific | Query може да не види очакваното in-memory изменение |
| Hibernate `ALWAYS` | Flush преди query | Повече SQL/overhead |
| Hibernate `MANUAL` | Caller решава кога да flush-не | Риск от незаписани промени; не настройвай без ownership contract |

Някои native queries също могат да предизвикат flush. Точният trigger зависи от EntityManager/Session API, query spaces, provider, flush mode и version; не обобщавай от един тест за всички queries.

## Bad → root cause → good

**Bad:** да извикаш `entityManager.flush()` след всяка setters операция „за сигурност“; или да приемеш, че извършен SQL UPDATE означава persisted success след rollback.

**Good:** използвай контролирана transaction boundary, разчитай на `AUTO` по подразбиране, извиквай explicit `flush` когато е нужно ранно DB constraint validation или последващ SQL depend-ва от предишни операции, и винаги осмисляй rollback.

Съществуващият [markPaidFlushThenFail](src/main/java/bg/hristomanov/education/uow/service/PersistenceContextProbeService.java) + [тест](src/test/java/bg/hristomanov/education/uow/RepositoryUnitOfWorkTest.java) доказват rollback след flush. Новият [autoFlushBeforeOverlappingScalarQuery](src/main/java/bg/hristomanov/education/uow/service/PersistenceContextProbeService.java) прави JPQL scalar query в същата table след in-memory status change; [тестът](src/test/java/bg/hristomanov/education/uow/VladPersistenceLifecycleTest.java) доказва, че заявката вижда новия статус.

**Action queue caution:** Hibernate [official flush ordering](https://docs.hibernate.org/orm/7.1/userguide/html_single/#flushing-flush-order) описва orphan removals, inserts, updates, collections и deletes. Този урок **не** претендира за автоматизиран independent proof на всяка вътрешна ActionQueue фаза; тестовете проверяват business-visible semantics. Ако зависим от конкретен SQL ordering в production, създаваме отделен targeted StatementInspector/DB test и фиксираме Hibernate версията.

## Decision contract

| Въпрос | Решение |
|---|---|
| Проблем | Неразбиране на persistence context flush и DB commit |
| Кога explicit flush | Ранен constraint check или необходим междинен DB-visible state в същата transaction |
| Кога НЕ | След всеки entity setter; „за всеки случай“ |
| Печалба | Управляван synchronization point |
| Загуба | Допълнителен I/O, constraint failures по-рано, по-трудно batching |
| Алтернатива | Остави default `AUTO` + commit; раздели неподходящо дълга transaction |
| Доказателство | [VladPersistenceLifecycleTest](src/test/java/bg/hristomanov/education/uow/VladPersistenceLifecycleTest.java), [RepositoryUnitOfWorkTest](src/test/java/bg/hristomanov/education/uow/RepositoryUnitOfWorkTest.java) |

## Review checklist

Кога точно започва DB transaction? Dirty checking enabled ли е? JPQL overlap trigger-ва ли flush? Кои SQL statements се изпълняват преди commit? Има ли версия/locking за конкурентни writes? При exception rollback-ва ли се целият Unit of Work?

### Оригинални източници

- Vlad — [Flush Strategies](https://vladmihalcea.com/a-beginners-guide-to-jpahibernate-flush-strategies/), [AUTO flush](https://vladmihalcea.com/how-does-the-auto-flush-work-in-jpa-and-hibernate/).
- [Hibernate ORM 7.1: Flushing](https://docs.hibernate.org/orm/7.1/userguide/html_single/#flushing).
- [Jakarta Persistence 3.2 EntityManager](https://jakarta.ee/specifications/persistence/3.2/apidocs/jakarta.persistence/jakarta/persistence/entitymanager).
