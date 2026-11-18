# NotebookLM — VM-26 connection pool tuning, backpressure и replicas

## Импортирай

- [VM-26 главен урок](VM-26-POOL-BACKPRESSURE.md).
- [PoolAdmissionGate](src/main/java/bg/hristomanov/education/tx/PoolAdmissionGate.java) и [Hikari тестове](src/test/java/bg/hristomanov/education/tx/PoolAdmissionGateTest.java).
- [Real PostgreSQL read-only test](src/test/java/bg/hristomanov/education/tx/PostgresTransactionLabIT.java).
- [Vlad Mihalcea — Optimal Connection Pool Size](https://vladmihalcea.com/optimal-connection-pool-size/), [HikariCP](https://github.com/brettwooldridge/HikariCP).

## Audio Overview prompt

> Създай техническо аудио обучение на български за Mid+/Early Senior Java backend developer. Започни с производствен инцидент: увеличаваме Hikari maximumPoolSize, но request p99 и DB lock waits стават по-лоши. Разграничи HTTP concurrency, admission budget, Hikari pending/active/usage, physical connection lease, SQL execution и DB capacity. Обясни чрез проблем → bad → root cause → good → тест → кога НЕ → trade-offs.
>
> Демонстрирай Hikari pool=1 + Semaphore gate; първата операция държи връзката, следващата се отхвърля преди JDBC checkout, pending=0, след release има success. Без да измисляш benchmark числа, уточни, че local Semaphore не е global limit при 4 Kubernetes pods и Quartz jobs. Дай примери за fail-fast vs queue и кога bounded retries са валидни.
>
> Обясни, че `@Transactional(readOnly=true)` ≠ replica routing. Реалният PostgreSQL тест с `SET TRANSACTION READ ONLY` доказва SQLSTATE `25006` при DML; не доказва автоматично избран replica datasource. Обясни replica lag и read-your-writes. Завърши с 5 проверяващи въпроса, без веднага да разкриваш отговорите.
