# VM-30 — Hibernate upgrades, internal SPI и vendor-specific portability

## Production incident

Екипът upgrade-ва Hibernate минорна/major версия заради нови оптимизации, без да тества query shape, JSON types, dialect detection, collection pagination и Envers. Локалният H2 suite е зелен, но PostgreSQL или Oracle плановете и mappings се различават.

Няма безопасен „само увеличи version“ подход при ORM upgrade. Изискваме **baseline, compatibility contract и real vendor tests**.

## Mental model

```text
Baseline Java/Spring/Jakarta/Hibernate versions
  → compile against supported public API
  → H2/JPA semantics and mapping regression tests
  → PostgreSQL 17 integration + EXPLAIN + query-count checks
  → manual Oracle 19c smoke/plan for Oracle-specific SQL
  → staged rollout with database/resource metrics
```

## Доказателство и README → код

- [HibernateUpgradeGuardTest.java](../src/test/java/bg/hristomanov/education/hperf/HibernateUpgradeGuardTest.java) проверява наличната major version 7 и че локалният test dialect е **H2**, не PostgreSQL/Oracle. Тестът умишлено ще сигнализира за неизследван upgrade към major 8.
- [PostgresHibernateDialectIT.java](../src/test/java/bg/hristomanov/education/hperf/PostgresHibernateDialectIT.java) със ServiceConnection проверява, че реалната DB e PostgreSQL 17, а избраният Hibernate dialect е PostgreSQLDialect.
- [VM-22 JSON mapping и Postgres IT](VM-22-ADVANCED-TYPES.md), [VM-13 bounded fetch/pagination](VM-13-DTO-PAGINATION.md), [VM-27 Envers revisions](VM-27-AUDITING-CDC.md) и [VM-20 Oracle vs PostgreSQL](../../../database/sql-labs/docs/VM-20-POSTGRESQL-ORACLE-COMPARISON.md) са отделни regression checkpoints.

**Внимание:** `SessionFactoryImplementor` е Hibernate SPI (internal API), не обещание за source compatibility при major upgrade. Използван е **само в тестовете**, за да проверим dialect contract. Production кодът трябва да предпочита standard JPA/Hibernate public APIs.

## Bad → Good

**Bad:** `hibernate.hbm2ddl.auto=update` в production, без versioned миграции; suppress warning за in-memory pagination; нов Hibernate с празни интеграционни тестове.

**Good:** фиксирай и провери dependency tree, compiler bytecode и Jakarta version; пусни всички SQL/JPA tests, включително targeted PostgreSQL профил. Сравни SQL count, INSERT batching, fetch plans, dialect-specific JSON и temporal mapping. Спри rollout при невалидни инварианти, дори всички unit tests да са зелени.

## Decision contract

| Въпрос | Отговор |
|---|---|
| Проблем | ORM/dialect regression при library и database upgrades |
| Кога | Всеки Hibernate, Spring Data, JDBC driver, DB version upgrade |
| Кога НЕ | Не добавяй dependency или Hibernate extension без измерима нужда |
| Печалба | Коректност и контролирана миграция |
| Загуба | Повече CI, версия-специфични tests, multi-vendor support cost |
| По-проста алтернатива | Остани на поддържана baseline версия до планиран upgrade window |
| Доказателство | [Hibernate version test](../src/test/java/bg/hristomanov/education/hperf/HibernateUpgradeGuardTest.java) + [Postgres dialect IT](../src/test/java/bg/hristomanov/education/hperf/PostgresHibernateDialectIT.java) |

## Граници

Тези тестове доказват използваната версия и dialect. **Не доказват** Oracle compatibility, пълна API/ABI migration readiness или че всяка Hibernate internal оптимизация е идентична. Всяка актуализация изисква изпълнение на функционални, performance и schema tests, реална PostgreSQL и отделен Oracle test environment при нужда.

`mvn -pl persistence/hibernate-performance test`

`mvn -pl persistence/hibernate-performance -Ppostgres-integration verify`

## Оригинални източници

- [Vlad — Hibernate tutorials](https://vladmihalcea.com/tutorials/hibernate/)
- [Hibernate ORM migration guides](https://docs.hibernate.org/orm/7.4/migration-guide/migration-guide.html)
- [Spring Boot dependency management](https://docs.spring.io/spring-boot/reference/using/build-systems.html)
- [Jakarta Persistence 3.2 specification](https://jakarta.ee/specifications/persistence/3.2/)
