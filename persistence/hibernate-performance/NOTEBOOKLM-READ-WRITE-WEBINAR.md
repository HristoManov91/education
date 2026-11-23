# NotebookLM — Spring Boot Reads & Writes, Vlad Mihalcea / Jos Roseboom

## Източници за добавяне

1. [Основен, подробен урок](docs/WEBINAR-READ-WRITE-PERFORMANCE.md) — **водещ за коректността** и разграничението „демонстрация vs измерване“.
2. [Упражнения и checklist](WEBINAR-EXERCISES.md).
3. [Writes — оригинална лекция](https://youtu.be/p4-8gRoIaAg), [Reads — оригинална лекция](https://youtu.be/r_Akpdq5tUw); английските субтитри са анализирани при подготовката на урока.
4. [Vlad Mihalcea Spring PetClinic demo repository](https://github.com/vladmihalcea/spring-petclinic) и branches за `SEQUENCE`, batching, driver rewrite, `StatelessSession`, COPY, Blaze-Persistence и lazy loading (линкове в основния урок).
5. [VM-11–VM-14/VM-16, runnable H2 & PostgreSQL tests](README.md), [VM-21 observability](VM-21-SQL-OBSERVABILITY.md).
6. [Hibernate 7.4 reference](https://docs.hibernate.org/orm/7.4/userguide/html_single/), [PostgreSQL 17 COPY](https://www.postgresql.org/docs/17/sql-copy.html), [pgJDBC](https://jdbc.postgresql.org/documentation/use/), [Spring Data JPA](https://docs.spring.io/spring-data/jpa/reference/).

## Готов prompt за задълбочена аудио лекция (български)

Създай диалог между двама senior backend инженери (Java 21/25, Spring Boot, Hibernate, PostgreSQL и Oracle). Дай **много детайлен, разговорен аудио урок**, не кратко резюме. Започни с конкретен production incident: 156k rows import е бавен, а GET owners зарежда огромен граф от pets/visits. Раздели в **Writes** и **Reads**, после ги обедини в evidence-driven optimization workflow.

За Writes: обясни защо Java loop/saveAll не е JDBC batch; `IDENTITY` vs `SEQUENCE` и pooled allocation; batch_size/order_inserts/order_updates; flush/clear; pgJDBC `reWriteBatchedInserts`; `StatelessSession.insertMultiple`; `JdbcTemplate.batchUpdate`; PostgreSQL `COPY`. За всеки вариант дай кога е подходящ, кога НЕ, correctness и operational trade-offs, какъв тест го доказва, как се чете real SQL, какво не се пренася от PostgreSQL към Oracle.

За Reads: `EAGER` vs `LAZY`, N+1, JOIN FETCH, `@EntityGraph`, DTO projections, Blaze-Persistence Entity Views, keyset/seek vs offset, Page/Slice/Window и отделна pagination на nested collections. Изясни защо промяната на `Owner → Pet → Visit` API response shape не е идентичен workload comparison.

**Изисквай корекции:** PostgreSQL COPY НЕ заобикаля транзакциите по общо правило; `BIGINT=64 бита=8 байта`, `UUID=128 бита=16 байта`; `keyset` не гарантира константно време без подходящ индекс/план. Разграничи платения Hypersistence Optimizer от open-source BootUI; никакви неподкрепени „25×“ обещания.

След всеки раздел задай кратък въпрос „как би го доказал?“ и препрати към конкретните съществуващи H2/PostgreSQL тестове от README. Завърши с два практични казуса и сравнение на риска при промяна на ID strategy или fetch contract. **Не измисляй стартирани тестове, локални benchmark числа или Oracle измервания.** Текстът на глава от Education има приоритет за коректност пред автоматично генерираните субтитри.

### Оригинални източници

- [Vlad Mihalcea / Jos Roseboom — Writes](https://youtu.be/p4-8gRoIaAg), [Reads](https://youtu.be/r_Akpdq5tUw).
