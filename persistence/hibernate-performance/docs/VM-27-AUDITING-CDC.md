# VM-27 — Hibernate Envers, auditing, soft delete и CDC

## Входен production казус

Поръчка е променила статус от DRAFT на APPROVED. След инцидент екипът трябва да отговори кога е станало това, каква е предишната стойност и дали събитие е било публикувано към други системи. **История на Entity ≠ надеждно доставено бизнес събитие.** Hibernate Envers съхранява ревизии в DB, а CDC и Transactional Outbox са интеграционни механизми.

## Mental model

- **Envers**: при Hibernate-managed INSERT/UPDATE/DELETE пише audit revision редове. Работи с промени, които минават през Hibernate ORM и неговите listeners; direct SQL извън ORM няма автоматично да бъде проследен.
- **Soft delete**: бизнес избор да запазим реда с логически флаг. Не замества аудита и може да усложни уникалните индекси, relations и recovery.
- **CDC**: логически stream от database change log (напр. PostgreSQL WAL); наблюдава DB промени извън конкретен ORM SessionFactory, но изисква инфраструктура, snapshot/restart, schema evolution и event processing.
- **Transactional Outbox**: запис на домейн събитие в същата DB транзакция с business state; relay изнася събитията с idempotency/retries.

## Bad → root cause → good

**Bad:** записваме status единствено в текущата таблица, а след това извикваме remote API за "audit" по време на транзакцията. Ако remote call успее и DB rollback-не, губим consistency; ако DB commit-не и network call се загуби, другата система не узнава промяната.

**Good за локална история:** [AuditedInvoice](../src/main/java/bg/hristomanov/education/hperf/domain/AuditedInvoice.java) има `@Audited`. Първа транзакция създава DRAFT; втора променя на APPROVED. [AuditingAndInheritanceTest](../src/test/java/bg/hristomanov/education/hperf/AuditingAndInheritanceTest.java) използва `AuditReaderFactory` да прочете двете revisions и валидира историята.

**Good за интеграция:** разгледай [Transactional Outbox/Inbox](../../../spring/messaging/outbox-inbox/README.md) и [Decathlon PostgreSQL CDC case study](../../../docs/case-studies/001-decathlon-kafka-business-table-cdc.md). Не включваме Debezium/Kafka container само за този Envers урок; CDC частта е **архитектурна бележка** с връзки към вече съществуващ материал, не тестван в настоящия модул.

## Decision contract

| Подход | Кога | Кога НЕ | Полза | Компромис |
|---|---|---|---|---|
| Hibernate Envers | История на Hibernate-managed entities | Direct JDBC/друг writer е основният път за промени | Ревизии и лесен JPA read API | Audit tables, storage, schema/version complexity |
| Soft delete | Задължително запазване на записи / restore | Данните трябва реално да се изтрият по retention policy | Възстановяване на запис | Predicate leakage, uniqueness, joins |
| CDC | Наблюдаваме database changes извън ORM | Не можем да оперираме WAL/stream pipeline | Интеграционен change stream | Snapshot, offsets, schema drift, at-least-once |
| Outbox | Reliable domain event след commit | Само локална история ни е нужна | Atomic write на state + outbox | Relay, retries, deduplication |

**Алтернатива:** ако трябва само "last modified by/at", две колони и DB/application validation може да са по-прости от пълна Envers система.

## README → код и доказателство

- [AuditedInvoice.java](../src/main/java/bg/hristomanov/education/hperf/domain/AuditedInvoice.java)
- [AuditingAndInheritanceTest.java](../src/test/java/bg/hristomanov/education/hperf/AuditingAndInheritanceTest.java) — 2 транзакции → 2 revisions → стара и нова статусна стойност
- [Existing CDC engineering case](../../../docs/case-studies/001-decathlon-kafka-business-table-cdc.md)
- [Outbox/Inbox](../../../spring/messaging/outbox-inbox/README.md)

`mvn -pl persistence/hibernate-performance test`

**Доказано:** Envers H2 single-process JPA lifecycle, не PostgreSQL WAL replay, Oracle triggers, distributed delivery или правни retention гаранции.

## Оригинални източници

- [Vlad Mihalcea — Hibernate tutorials](https://vladmihalcea.com/tutorials/hibernate/)
- [Hibernate ORM Envers Guide](https://docs.hibernate.org/orm/7.4/userguide/html_single/#envers)
- [Debezium PostgreSQL connector](https://debezium.io/documentation/reference/stable/connectors/postgresql.html)
- [PostgreSQL 17 logical decoding](https://www.postgresql.org/docs/17/logicaldecoding.html)
