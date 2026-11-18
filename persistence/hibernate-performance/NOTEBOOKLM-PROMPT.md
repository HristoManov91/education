# NotebookLM — Vlad Mihalcea: High-Performance Hibernate (VM-11..14, VM-16/17)

## Sources за NotebookLM
- [Основен учебен модул](README.md), [VM-11](docs/VM-11-IDENTIFIERS.md), [VM-12](docs/VM-12-FETCH-NPLUS1.md), [VM-13](docs/VM-13-DTO-PAGINATION.md), [VM-14](docs/VM-14-BATCH-BULK.md), [VM-16](docs/VM-16-SPRING-DATA-TRAPS.md).
- [VM-17 QueryDSL](../specification-querydsl/VM-17-PAGE-SLICE-COUNT.md), [практическите задачи](EXERCISES.md).
- [H2 integration tests](src/test/java/bg/hristomanov/education/hperf/HibernatePerformanceTest.java) и [real PG tests](src/test/java/bg/hristomanov/education/hperf/PostgresHibernatePerformanceIT.java).
- [Оригинален Vlad блог](https://vladmihalcea.com/tutorials/hibernate/), [Hibernate ORM 7.4 guide](https://docs.hibernate.org/orm/7.4/userguide/html_single/).

## Audio Overview prompt

> Създай задълбочен технически аудио урок **на български**, 40–50 минути, за Java 25/Spring Boot/Hibernate разработчик (Mid+ → Senior). Не прави рекламно summary на Vlad Mihalcea; вземи неговите практични теми и обясни нашата една обща production ситуация: batch import с IDENTITY, N+1 при JSON response, collection join-fetch с пагинация и stale managed entity след bulk SQL.
>
> Във всяка техника следвай: реален проблем → привлекателен, но грешен код → root cause → mental model → good solution → **кога/кога НЕ** → печалби и загуби → тест с истински очаквания. Не давай fake benchmarks.
>
> Използвай точно fixture: 7 orders, 5 distinct customers, наивно 6 SQL срещу JOIN FETCH/EntityGraph 1; 23 SEQUENCE entries с изпълнени JDBC batches и 4 IDENTITY entries без такива; 53 inserts с flush/clear; DTO/page keyset до 3 реда; Page с count query срещу Slice без count. Обясни защо batch event не е гаранция за по-малко network packets.
>
> Разгледай `getReferenceById` vs `findById`, `@Version` за stale updates, pooled sequence allocation и DB DDL, LAZY vs EAGER, JPQL constructor projections, ORDER BY tie-breaker, keyset cursor, как Hibernate 7.4 прави nested SQL за collection paging и кога fail-on-pagination предотвратява in-memory fallback, Spring Specification vs QueryDSL count predicates и безопасно binding на параметри.
>
> Отдели PostgreSQL и Oracle semantics — в тази лаборатория PostgreSQL е реално тестван, Oracle не е. Накрая задай 6 practically reasoned review въпроса и посочи кои SQL/tests доказват всяка теза. Не отбелязвай темите за научени от слушането.

Започни с [EXERCISES.md](EXERCISES.md), ако слушателят има вече стабилна основа.
