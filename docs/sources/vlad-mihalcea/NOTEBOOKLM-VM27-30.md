# NotebookLM — Завършващ блок VM-27–VM-30

## Източници

- [VM-27 — Envers / CDC / Outbox](../../../persistence/hibernate-performance/docs/VM-27-AUDITING-CDC.md) и [JPA revisions test](../../../persistence/hibernate-performance/src/test/java/bg/hristomanov/education/hperf/AuditingAndInheritanceTest.java)
- [VM-28 — JOINED inheritance](../../../persistence/hibernate-performance/docs/VM-28-ENTITY-INHERITANCE.md)
- [VM-29 — recursive SQL / cycle detection](../../../database/sql-labs/docs/VM-29-HIERARCHICAL-ANALYTICS.md) и [PostgreSQL tests](../../../database/sql-labs/src/test/java/bg/hristomanov/education/sql/PostgresHierarchyAnalyticsIT.java)
- [VM-30 — Hibernate upgrade/dialect](../../../persistence/hibernate-performance/docs/VM-30-HIBERNATE-UPGRADE-PORTABILITY.md)
- [Hibernate 7.4 User Guide](https://docs.hibernate.org/orm/7.4/userguide/html_single/), [Vlad Hibernate articles](https://vladmihalcea.com/tutorials/hibernate/), [PostgreSQL recursive WITH](https://www.postgresql.org/docs/17/queries-with.html).

## Prompt за български аудио урок

> Създай задълбочен технически аудио урок на български, Java 25 + Spring Boot 4.1 + Hibernate 7.x. Изгради последователна production история: липсват audit revisions и надеждно event delivery; Java inheritance променя relational SQL shape; организационен graph съдържа цикъл; Hibernate upgrade преминава H2, но не и PostgreSQL vendor validation.
>
> Обясни root cause и mental model за всеки проблем, след което сравни bad/good solution и кога да изберем/не изберем техниката, плюсове/минуси, по-проста алтернатива, реален код и тест от Education.
>
> Разграничавай строго: Envers history ≠ CDC ≠ transactional outbox; JOINED/SINGLE_TABLE/TABLE_PER_CLASS/MappedSuperclass; recursive SQL cycle policy и RANK срещу ROW_NUMBER; Hibernate public API срещу internal SPI, vendor dialect и version guard.
>
> Не твърди, че сме провели real Oracle test или Debezium CDC pipeline. H2 Envers inheritance tests потвърждават JPA semantics, PostgreSQL integration tests потвърждават конкретния dialect; не прави недоказани performance обещания.
>
> Завърши с practical code review checklist и 8 въпроса за Mid+ / Early Senior. Подсказките да водят през доказателство, без да издават всички отговори предварително.
