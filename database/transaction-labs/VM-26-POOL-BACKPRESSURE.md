# VM-26 — Connection pool sizing, admission control, backpressure и read-only routing

> Java 25, HikariCP/H2 за физически pool lease и PostgreSQL 17 за DB read-only transaction. **Oracle/репликация не са тествани.**

## 1. Реален production incident

Имаме API с 100 concurrent HTTP заявки. Spring транзакциите включват SQL и чакане на външен HTTP service. Hikari отчита `pending` и acquire timeouts, макар DB CPU да не е постоянно 100%. Увеличаваме `maximumPoolSize` от 10 на 50 и получаваме още по-висок p99 latency и lock contention.

**Проблемът е несъответствие между входния concurrency и капацитета на DB**, не задължително малък pool. Не всички входящи заявки трябва безкрайно да чакат връзка.

## 2. Mental model

```text
request bursts / worker threads
        ↓
bounded admission budget ──→ fail fast, overload response
        ↓
Hikari acquire queue (active/idle/pending)
        ↓
physical JDBC leases
        ↓
DB locks / CPU / execution plan / I/O
```

HTTP request, thread, Spring transaction, persistence context, physical JDBC lease и DB executor work са **различни** ресурси. При стабилно състояние Little's Law качествено свързва throughput и average residence time със средната concurrency, но няма магическа формула за pool size без actual DB metrics, I/O, lock waits и latency distribution.

## 3. Bad → root cause → good

**Bad**: `hikariConfig.setMaximumPoolSize(200)` без да намалим transaction hold time, N+1 или concurrency. Резултатът може просто да е повече DB lock contention.

**Good**: приемай ограничен брой DB операции **преди** да вземеш connection. [PoolAdmissionGate.java](src/main/java/bg/hristomanov/education/tx/PoolAdmissionGate.java) използва non-blocking `Semaphore.tryAcquire()`; при претоварване хвърля `RejectedExecutionException`; при успех винаги освобождава permit с `finally`.

```java
PoolAdmissionGate gate = new PoolAdmissionGate(8); // учебна стойност, НЕ production default
var summary = gate.execute(() -> repository.loadSummary(orderId));
```

На HTTP границата overload може да стане `503` или `429` според API contract и retry policy. Използвай bounded retry/backoff, не насърчавай безкрайно повторение. Ако работата не допуска отказ, вариантите са bounded job queue или async processing с explicit delivery/expiry guarantees.

**Това е локален in-process gate**, не глобален лимит за 5 Kubernetes replicas, Quartz jobs и други приложения. `maxInFlight=poolSize` не е автоматично оптимално и може да изчерпи резервирания капацитет за другите операции.

## 4. Доказателство с Hikari и PostgreSQL

[PoolAdmissionGateTest.java](src/test/java/bg/hristomanov/education/tx/PoolAdmissionGateTest.java) стартира Hikari pool=1:
1. Първата операция взема връзка и я държи с контролирани `CountDownLatch`-ове.
2. Втората е отхвърлена **преди JDBC checkout**.
3. Доказваме `active=1`, `pending=0`, `rejected=1`, `peakInFlight=1`.
4. След release, JDBC и admission permit са свободни и следваща операция успява.
5. Negative tests доказват release след failure и отказ при невалидна конфигурация.

Сравни с [ConnectionLeaseTest](src/test/java/bg/hristomanov/education/tx/ConnectionLeaseTest.java): **без** admission gate вторият caller чака Hikari до `SQLTransientConnectionException`.

### Read-only transaction ≠ read replica routing

`@Transactional(readOnly=true)` е framework hint и само по себе си не доказва, че сме върху реплика или че driver/DB отказва DML. В [PostgresTransactionLabIT](src/test/java/bg/hristomanov/education/tx/PostgresTransactionLabIT.java) изпълняваме `SET TRANSACTION READ ONLY` срещу **реален PostgreSQL**, проверяваме позволен `SELECT` и отказ за `UPDATE` с SQLSTATE `25006`. След rollback стойността е непроменена.

За read replica routing са нужни отделни primary/replica datasources, подходящ transaction-aware router, ясно дефиниран момент на JDBC checkout, replica-lag monitoring и read-your-writes policy. Току-що записан order може да се прочете стар от асинхронна реплика. Ако consistency е критична, route към primary или използвай explicit stickiness/consistency window. Сегашните tests **не доказват** routing.

## 5. Decision contract

| Въпрос | Критерий |
| --- | --- |
| Проблем | Pool exhaustion при bursts; DB contention при твърде много SQL operations |
| Кога | Ендпойнти с измерени bursts и предвидима граница за in-flight DB work |
| Кога НЕ | Дълги HTTP calls в `gate.execute`; global multi-pod limit само с Java Semaphore; операции без дефинирана reject/queue policy |
| Печелим | Bounded concurrency, по-бързо overload signal, по-малко pending JDBC callers |
| Губим | Повече отхвърлени заявки, UX/retry complexity, tuning и fairness |
| По-проста алтернатива | Съкрати transactions, намали N+1, подобри query plans и Hikari timeout |
| Доказателство | [Hikari admission tests](src/test/java/bg/hristomanov/education/tx/PoolAdmissionGateTest.java) и [PostgreSQL read-only SQLSTATE тест](src/test/java/bg/hristomanov/education/tx/PostgresTransactionLabIT.java) |

## 6. Production investigation — практичен ред

1. Вземи request rate, burst, p50/p95/p99, timeout и error rate за **конкретния endpoint**.
2. Събери **за едно време** Hikari active/idle/pending/acquire/usage, DB CPU, wait events, lock contention и query counts.
3. Разграничи physical lease time, SQL execution, external HTTP wait и transaction boundaries.
4. Провери излишни fetches/N+1, тежки SQL plans, lock waits, дълги transactions **преди** pool resizing.
5. В staging измери няколко sizes под идентичен load; после сравни с admission budget и наблюдавай `rejected`/`acquire timeout`.
6. При replicas добави измерване на lag, consistency policy и route на конкретния query.

**Не твърдим оптимален pool size или доказан performance gain от H2 тест.**

## 7. README → код, стартиране и упражнения

- [PoolAdmissionGate](src/main/java/bg/hristomanov/education/tx/PoolAdmissionGate.java) → [3 Hikari tests](src/test/java/bg/hristomanov/education/tx/PoolAdmissionGateTest.java).
- [ConnectionLeaseTest](src/test/java/bg/hristomanov/education/tx/ConnectionLeaseTest.java) → negative example с изчерпан pool.
- [PostgreSQL isolation and read-only test](src/test/java/bg/hristomanov/education/tx/PostgresTransactionLabIT.java).
- [VM-05](docs/VM-05-JDBC-POOL.md) и [VM-21](../../persistence/hibernate-performance/VM-21-SQL-OBSERVABILITY.md) — какво измерваме.

```bash
mvn -pl database/transaction-labs test
mvn -pl database/transaction-labs -Ppostgres-integration verify
```

**Въпроси:** 4 pod-а × 8 permits = какъв максимум приемаме само от тези pod-ове? Какво правим с Quartz jobs извън gate? Кой е consistency risk при read replica? Кога по-голям Hikari pool реално може да помогне?

## Оригинални източници

- [Vlad Mihalcea — Optimal connection pool size](https://vladmihalcea.com/optimal-connection-pool-size/).
- [Vlad — Spring transaction and connection management](https://vladmihalcea.com/spring-transaction-connection-management/).
- [HikariCP configuration](https://github.com/brettwooldridge/HikariCP#configuration-knobs-baby).
- [PostgreSQL 17 SET TRANSACTION](https://www.postgresql.org/docs/17/sql-set-transaction.html).
- [Spring transaction propagation](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/tx-propagation.html).
