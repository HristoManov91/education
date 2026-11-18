# NotebookLM — Vlad Mihalcea, VM-05..VM-07 (BG audio)

## Източници за импортиране

- [Главен урок](README.md), [VM-05 JDBC](docs/VM-05-JDBC-POOL.md), [VM-06 isolation](docs/VM-06-MVCC-ISOLATION.md), [VM-07 locking](docs/VM-07-LOCKING.md) и [упражнения](EXERCISES.md).
- [VM-18 JDBC streaming, fetch-size и statement cache](VM-18-JDBC-FETCH-SIZE-STREAMING.md) и [интеграционният тест](src/test/java/bg/hristomanov/education/tx/PostgresFetchingLabIT.java).
- [Пълният PostgreSQL интеграционен тест](src/test/java/bg/hristomanov/education/tx/PostgresTransactionLabIT.java) и [ConnectionLeaseTest](src/test/java/bg/hristomanov/education/tx/ConnectionLeaseTest.java).
- [Vlad — Spring Transaction and Connection Management](https://vladmihalcea.com/spring-transaction-connection-management/), [Vlad — Optimistic Locking](https://vladmihalcea.com/optimistic-locking-version-property-jpa-hibernate/).
- [PostgreSQL 17 isolation](https://www.postgresql.org/docs/17/transaction-iso.html) и [explicit locking](https://www.postgresql.org/docs/17/explicit-locking.html).

## Audio Overview prompt

> Създай задълбочен аудио урок на **български** за Mid+ / Early Senior Java backend engineer. Построй единен production incident с 3 явления: Hikari pool exhaustion при външен HTTP call в DB transaction; READ COMMITTED повторно четене 10→12; lost update 10→11 вместо 12. Проследи root cause до JDBC connection leases, MVCC snapshots и locking conflict policy.
>
> Не изброявай API механично. За всяка техника обясни: **проблем → кога/кога НЕ → bad → good → точно кои тестове го доказват → алтернатива → плюсове/минуси**. Разграничи transaction/connection/persistence context/HTTP request и READ COMMITTED/REPEATABLE READ/SERIALIZABLE. Обясни failure SQLSTATE 40001/40P01/55P03, optimistic conditional UPDATE, NOWAIT, SKIP LOCKED, bounded whole-transaction retry, идемпотентност и единичен lock order.
>
> Запази примера от fixture: account quantity 10, optimistic conflict = 0 updated rows, deadlock от две обратни заключвания, 2 workers claim-ват различни jobs. Не измисляй производителност или latency.
>
> Накрая постави 5 въпроса без незабавно да дадеш отговорите. Покажи кога да изберем по-простото атомарно SQL UPDATE вместо versioning, и кога дълга транзакция е вредна.

Това е първичен материал за аудио. Редовете и грешките от теста се проверяват с действително изпълнение срещу PostgreSQL.
