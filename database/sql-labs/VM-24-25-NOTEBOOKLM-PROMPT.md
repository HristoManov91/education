# NotebookLM — VM-24 UPSERT и VM-25 Flyway migrations (BG audio)

## Import sources

- [VM-24 Atomic UPSERT](docs/VM-24-ATOMIC-UPSERT.md) и [PostgresUpsertLabIT](src/test/java/bg/hristomanov/education/sql/PostgresUpsertLabIT.java).
- [VM-25 Flyway](docs/VM-25-FLYWAY-MIGRATIONS.md) и [PostgresFlywayMigrationIT](src/test/java/bg/hristomanov/education/sql/PostgresFlywayMigrationIT.java).
- [V1 SQL](src/test/resources/vm25/sql/V1__create_vm25_account.sql), [V2 SQL](src/test/resources/vm25/sql/V2__add_active_flag.sql).
- [PostgreSQL ON CONFLICT](https://www.postgresql.org/docs/17/sql-insert.html), [Oracle MERGE](https://docs.oracle.com/en/database/oracle/oracle-database/19/sqlrf/MERGE.html), [Flyway PostgreSQL](https://documentation.red-gate.com/flyway/reference/database-driver-reference/postgresql-database).

## Audio prompt

> Направи задълбочен урок на български за Mid+ / Early Senior Java backend engineer. Започни с два реалистични инцидента: (1) два workers изпълняват exists → insert и създават race при еднакъв tenant/reference; (2) developer променя стара Flyway V1 миграция след deploy и коректността на production schema вече не е сигурна.
>
> Премини през PostgreSQL unique constraints, ON CONFLICT DO UPDATE, RETURNING, final-write vs idempotent semantics, tenant isolation, Oracle MERGE limitations и concurrent negative тест. После обясни Flyway immutable versioned migration, schema history, checksum validation, additive migration, deployment ordering и защо transaction rollback не е универсална DDL rollback стратегия.
>
> Използвай нашите тестове за доказателство, без да измисляш performance числа. За всяка техника: какво решава, кога/кога НЕ, bad/good, trade-offs, по-проста алтернатива и конкретна команда. Завърши с въпроси за code review и кратък checkpoint.
