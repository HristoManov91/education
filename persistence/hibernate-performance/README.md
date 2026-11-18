# High-Performance Hibernate — Vlad Mihalcea (VM-11–VM-14 и VM-16)

**Java 25 / Spring Boot 4.1.1 / Hibernate ORM 7.4.5.Final (проверен в CI) / H2 / PostgreSQL 17 Testcontainers.** Продължение на [VM-01–VM-10](../../docs/sources/vlad-mihalcea/README.md), не препечатка на оригиналните статии.

## 1. Първо реалният проблем

В production имаме 3 symptoms: import на хиляди orders изпълнява твърде много INSERT calls; API за поръчки прави N+1 и изпраща прекалено много SQL; страницирането на joined collection започва да връща резултати само след изтегляне на огромен набор в memory.

Не решаваме тези проблеми с „по-голям JDBC pool“, а като разберем **ID generation → batching → fetch plan → bounded pagination → managed state**. Този модул превръща всяка хипотеза в изпълним тест.

## 2. Глави / ред за учене

| ID | Проблем и подход | Доказателство |
|---|---|---|
| [VM-11: IDENTITY vs SEQUENCE](docs/VM-11-IDENTIFIERS.md) | Кога ID става известен и дали може да се batch-ва INSERT | [Fast+PostgreSQL tests](src/test/java/bg/hristomanov/education/hperf/HibernatePerformanceTest.java) |
| [VM-12: N+1 и fetch plans](docs/VM-12-FETCH-NPLUS1.md) | LAZY getter → 6 SQL, JOIN FETCH/EntityGraph → 1 SQL | [Fast N+1 test](src/test/java/bg/hristomanov/education/hperf/HibernatePerformanceTest.java) + [PG IT](src/test/java/bg/hristomanov/education/hperf/PostgresHibernatePerformanceIT.java) |
| [VM-13: DTO, keyset, collection pages](docs/VM-13-DTO-PAGINATION.md) | Bounded projection, stable cursor, Hibernate 7.4 nested collection pagination | [Reader](src/main/java/bg/hristomanov/education/hperf/read/PerfOrderReader.java) и tests |
| [VM-14: JDBC batching / bulk DML](docs/VM-14-BATCH-BULK.md) | Реални batch events, flush/clear, bulk stale entity | [SessionEventListener proof](src/test/java/bg/hristomanov/education/hperf/HibernatePerformanceTest.java) |
| [VM-19: Hibernate L2 и Query Cache](docs/VM-19-HIBERNATE-CACHE.md) | Кога L2 е полезен, как query cache invalidates, кога direct SQL оставя stale data? | [CachedCatalogItem](src/main/java/bg/hristomanov/education/hperf/domain/CachedCatalogItem.java) · [5 cache tests](src/test/java/bg/hristomanov/education/hperf/HibernateCacheLabTest.java) |
| [VM-16: Spring Data pitfalls](docs/VM-16-SPRING-DATA-TRAPS.md) | findAll vs Page/Slice, findById vs getReferenceById | [Repositories](src/main/java/bg/hristomanov/education/hperf/repository/PerfOrderRepository.java) и tests |
| [VM-17: QueryDSL + Specification](../specification-querydsl/VM-17-PAGE-SLICE-COUNT.md) | Dynamic filters, explicit count, count-free slice | Надградена [Specification + QueryDSL лаборатория](../specification-querydsl/README.md) |

## VM-21 — Измерване преди оптимизация

- [Production SQL/ORM observability: как различаваме Hibernate SQL count, Hikari pool pressure, DB plans и locks](VM-21-SQL-OBSERVABILITY.md). Архитектурен runbook с директни връзки към съществуващите изпълними тестове, без дублиране.

## VM-19 — separate cache lab

- [Подробен урок с decision contract, сравнение READ_ONLY/READ_WRITE/NONSTRICT_READ_WRITE/TRANSACTIONAL и сценарий за stale reads](docs/VM-19-HIBERNATE-CACHE.md).
- [Четири теста с test-only Caffeine JCache provider](src/test/java/bg/hristomanov/education/hperf/HibernateCacheLabTest.java).
- [NotebookLM аудио урок](NOTEBOOKLM-VM19.md).

## 3. README → код

| Mental model | Code | Real test |
|---|---|---|
| DB-generated identity vs pooled sequence | [IdentityEntry](src/main/java/bg/hristomanov/education/hperf/domain/IdentityEntry.java) / [SequenceEntry](src/main/java/bg/hristomanov/education/hperf/domain/SequenceEntry.java) | [HibernatePerformanceTest](src/test/java/bg/hristomanov/education/hperf/HibernatePerformanceTest.java) |
| Explicit to-one fetch / EntityGraph | [PerfOrderReader](src/main/java/bg/hristomanov/education/hperf/read/PerfOrderReader.java) / [PerfOrderRepository](src/main/java/bg/hristomanov/education/hperf/repository/PerfOrderRepository.java) | SQL statement count tests |
| Record projection / seek pagination | [OrderRow](src/main/java/bg/hristomanov/education/hperf/read/OrderRow.java) / [Reader](src/main/java/bg/hristomanov/education/hperf/read/PerfOrderReader.java) | Offset/keyset parity tests |
| Collection fetch pagination | [Reader](src/main/java/bg/hristomanov/education/hperf/read/PerfOrderReader.java) | raw JOIN/LIMIT negative + nested SQL capture + two-step ID page |
| JDBC batch vs prepared statement count | [application.properties](src/main/resources/application.properties) | Hibernate SessionEventListener + Postgres IT |
| L2 + Query Cache + direct JDBC invalidation | [CachedCatalogItem](src/main/java/bg/hristomanov/education/hperf/domain/CachedCatalogItem.java) | [HibernateCacheLabTest](src/test/java/bg/hristomanov/education/hperf/HibernateCacheLabTest.java) |
| Spring Data Page vs Slice | [Repo](src/main/java/bg/hristomanov/education/hperf/repository/PerfOrderRepository.java) | H2 SQL-count comparison |

## 4. Как се стартира

~~~bash
# От root, бързи H2/JPA тестове:
mvn -pl persistence/hibernate-performance test
mvn -pl persistence/specification-querydsl test

# С работещ Docker: PostgreSQL 17 реално потвърждение на N+1, batching и pages:
mvn -pl persistence/hibernate-performance -Ppostgres-integration verify
~~~

Testcontainers ITs (`*IT.java`) **не са част от default Surefire**; Failsafe се включва само от explicit profile. [Таргетиран GitHub Actions workflow](../../.github/workflows/vlad-hibernate-performance.yml) се пуска само при релевантни промени и проверява XML reports/брой изпълнени tests. Общият root Maven reactor продължава с H2-only tests.

## 5. Граници на експериментите

- SQL statement counts са **контекстно зависими**: фикстура, първо ниво cache, Hibernate версия, fetch settings.
- `SessionEventListener.jdbcExecuteBatchStart` е реален Hibernate JDBC execution event, но **не е network packet counter**, нито универсална performance оценка.
- PostgreSQL IT доказва PostgreSQL behavior; **Oracle 19c не е тестван**. Oracle SEQUENCE syntax, driver specifics и query plans изискват отделна лаборатория.
- Тестовете доказват technical correctness; **„усвоено“ изисква проверени отговори** по [учебния протокол](../../docs/learning/ASSESSMENT-PROTOCOL.md).
- Всяка техника трябва да има причина, „кога/кога НЕ“, trade-off, по-проста алтернатива и работещ тест — виж отделните глави.

## 6. Финален checklist на началния казус

- ID strategy: имам ли реална нужда от SEQUENCE, настроен ли е allocation size?
- INSERT batching: проверих ли `jdbcExecuteBatchStart`, вместо да съдя по броя SQL логове?
- API: имам ли N+1 при cold persistence context, и може ли DTO да намали overfetch?
- Collection + Page: прави ли Hibernate 7.4 nested SQL limit, или in-memory fallback?
- Page/Slice: нужна ли е точна total count query?
- Bulk UPDATE: managed entity остава ли stale до `refresh/clear`?

### Оригинални източници

- [Vlad Mihalcea — Hibernate articles](https://vladmihalcea.com/tutorials/hibernate/), [book topic index](https://vladmihalcea.com/books/high-performance-java-persistence/).
- [Hibernate 7.4 ORM User Guide](https://docs.hibernate.org/orm/7.4/userguide/html_single/), [Jakarta Persistence 3.2](https://jakarta.ee/specifications/persistence/3.2/).
- [Spring Data JPA Reference](https://docs.spring.io/spring-data/jpa/reference/).
