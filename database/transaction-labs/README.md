# Vlad Mihalcea / High-Performance Persistence — VM-05..VM-07

> Java 25, JDBC + HikariCP, PostgreSQL 17 + Testcontainers. Реален production incident → причината → bad/good → тест → кога/кога НЕ → компромиси. **Тези лаборатории не твърдят, че са валидирани върху Oracle.**

## Входен казус: една бавна заявка поваля целия API

Имаме Spring Boot services, които четат balance, викат външен risk API и променят две позиции. Един developer държи транзакция отворена и прави HTTP request между две SQL операции; JDBC connections остават заети. Втори екип изпълнява read-modify-write върху същата стойност и губи concurrent update. Трети worker се сблъсква с lock contention при вземане на задачи.

**Едно лечение за трите проблема няма**. Трябва да различим resource occupancy, isolation и conflict detection.

| Тема | Въпрос | Изпълнимо доказателство |
|---|---|---|
| [VM-05: Connection lease, pool, JDBC](docs/VM-05-JDBC-POOL.md) | Защо pool от 1 връзка блокира без SQL? | [ConnectionLeaseTest](src/test/java/bg/hristomanov/education/tx/ConnectionLeaseTest.java) |
| [VM-06: ACID, MVCC, isolation](docs/VM-06-MVCC-ISOLATION.md) | Защо два SELECT в една транзакция виждат различно? | [PostgresTransactionLabIT](src/test/java/bg/hristomanov/education/tx/PostgresTransactionLabIT.java) |
| [VM-07: optimistic/pessimistic locks](docs/VM-07-LOCKING.md) | Как предотвратяваме lost update и какво правят NOWAIT/SKIP LOCKED? | [PostgresTransactionLabIT](src/test/java/bg/hristomanov/education/tx/PostgresTransactionLabIT.java) |

## README → код

| Функционална идея | Production-like implementation | Тест |
| --- | --- | --- |
| Кратък JDBC connection lease, PreparedStatement | [ConnectionWork.java](src/main/java/bg/hristomanov/education/tx/ConnectionWork.java) | [ConnectionLeaseTest.java](src/test/java/bg/hristomanov/education/tx/ConnectionLeaseTest.java) |
| Pool capacity and metrics | HikariConfig в [ConnectionLeaseTest](src/test/java/bg/hristomanov/education/tx/ConnectionLeaseTest.java) | проверка за active=1, timeout, active=0 |
| Real PostgreSQL isolation and locks | JDBC connections с explicit transaction control в [PostgresTransactionLabIT](src/test/java/bg/hristomanov/education/tx/PostgresTransactionLabIT.java) | 8 положителни/negative tests |

## Стартиране (важно за CI minutes)

От root на Education:

```bash
# Бързи H2 тестове (никакъв Docker)
mvn -pl database/transaction-labs test

# Реална PostgreSQL и Testcontainers; Docker трябва да е активен
mvn -pl database/transaction-labs -Ppostgres-integration verify
```

- По подразбиране Maven Surefire стартира `*Test`; `PostgresTransactionLabIT` **не** се изпълнява, освен при изричен профил.
- [Отделен, path-filtered GitHub workflow](../../.github/workflows/vlad-transactions.yml) изпълнява PostgreSQL тестовете само при промяна на този lab или съответните JPA разширения.
- CI проверява реално генерирани test-report файлове, не само exit code 0.

## Разграничение между базите

H2 в `ConnectionLeaseTest` доказва **borrow/release/timeout на Hikari и коректност на bind parameters**. Не доказва PostgreSQL/Oracle MVCC, lock modes или deadlock detection. `PostgresTransactionLabIT` използва реален PostgreSQL 17. Oracle comparisons ще са текстови, с ясна граница докато няма Oracle instance/CI.

## Какво следва

[VM-08..VM-10](../../persistence/repository-unit-of-work/README.md) продължават същата история в Hibernate: managed/detached/merge, dirty checking, flush, cascades, lazy relationships и orphan removal.

### Оригинални източници

- Vlad Mihalcea: [Spring transaction and connection management](https://vladmihalcea.com/spring-transaction-connection-management/); [Optimal connection pool size](https://vladmihalcea.com/optimal-connection-pool-size/); [optimistic locking @Version](https://vladmihalcea.com/optimistic-locking-version-property-jpa-hibernate/); [database job queue with SKIP LOCKED](https://vladmihalcea.com/database-job-queue-skip-locked/).
- [PostgreSQL 17 Transaction Isolation](https://www.postgresql.org/docs/17/transaction-iso.html); [Explicit Locking](https://www.postgresql.org/docs/17/explicit-locking.html); [Serialization Failure Handling](https://www.postgresql.org/docs/17/mvcc-serialization-failure-handling.html).
- [JDBC Connection API](https://docs.oracle.com/en/java/javase/25/docs/api/java.sql/java/sql/Connection.html) и [HikariCP repository](https://github.com/brettwooldridge/HikariCP).

**Какво не доказваме:** precise performance gains под production load; ефективно оптимално `maximumPoolSize`; Oracle behavior; bounded root-CI cost извън тази path-filtered задача. Не пренасяй тези параметри като production defaults.
