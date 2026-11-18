# VM-07 — Lost update, optimistic locking, NOWAIT, SKIP LOCKED, deadlocks

## Production incident

Двама служители прочитат количество 10 и независимо прибавят 1. И двамата записват 11, вместо 12. Други две операции взимат locks върху редове A и B в противоположен ред, след което блокират една друга.

Това са **различни класове проблеми**, не един „бавен Hibernate“ проблем.

## Bad → root cause

```java
int quantity = repository.loadQuantity(id); // two clients read 10
repository.saveQuantity(id, quantity + 1);   // both overwrite with 11
```

Наивният read-modify-write overwrites stale state без проверка. [Тестът](../src/test/java/bg/hristomanov/education/tx/PostgresTransactionLabIT.java) умишлено доказва загубения increment (11 вместо 12).

## Good №1 — compare-and-set / @Version

```sql
UPDATE account_balance
SET quantity = ?, version = version + 1
WHERE id = ? AND version = ?;
```

[ConnectionWork.java](../src/main/java/bg/hristomanov/education/tx/ConnectionWork.java) връща count-а от `executeUpdate()`. Един caller получава 1, другият 0. **0 не е success**: конфликтът изисква policy (обяви conflict или изпълни нова транзакция/retry). Hibernate `@Version` прави еквивалентна проверка при dirty checking/flush, но границите на entity и DB транзакцията все още имат значение.

**Алтернатива:** `UPDATE account_balance SET quantity=quantity+1 WHERE id=?` е достатъчна за прост атомарен increment. Не усложнявай с version check, ако цялата бизнес операция е една SQL команда.

## Good №2 — pessimistic locks

- `SELECT ... FOR UPDATE`: резервира ред до commit/rollback; други конфликтиращи updates чакат.
- `NOWAIT`: при вече заключен ред отказва **55P03** в PostgreSQL вместо да чака.
- `SKIP LOCKED`: прескача заключени редове — полезно при job queue с няколко workers, **не** за банков отчет, където пропускането на редове е логически недопустимо.
- `ORDER BY ... LIMIT ... FOR UPDATE SKIP LOCKED` е разумна queue стратегия; използвай claim/update в същата transaction и idempotent обработка на job-а след commit.

[PostgresTransactionLabIT](../src/test/java/bg/hristomanov/education/tx/PostgresTransactionLabIT.java) доказва NOWAIT и два workers, които claim-ват различни work items.

## Deadlock: защо съществува и как се обработва

```text
TX-A: lock(id=1) -> waits for lock(id=2)
TX-B: lock(id=2) -> waits for lock(id=1)
PostgreSQL: detects cycle -> aborts one TX with SQLSTATE 40P01
```

**Production rule:** еднакъв ред на lock acquisition е превенция; кратки транзакции намаляват риска; при `40P01` или `40001` повтори **цялата** транзакция след rollback (и с bounded retries/backoff), не само последния UPDATE. Ако в workflow има външен HTTP/плащане, retry трябва да е idempotent/outbox-aware.

Тестът използва две реални JDBC connections, `CyclicBarrier` и timeout; двата първи row locks са задържани, преди второто UPDATE. Очакваме точно един `40P01` и един commit, а не „и двете успешни“.

## Decision contract

| Подход | Кога | Кога НЕ | Печалба | Цена |
|---|---|---|---|---|
| Conditional UPDATE / @Version | read-modify-write между requests / нисък conflict rate | Една атомарна DB команда е достатъчна | Открива stale writes | Conflict/retry UX, version column |
| FOR UPDATE | силна serialized row consistency в транзакция | дълго външно I/O / масови read-only screens | Предотвратява конкурентни промени докато държим lock | Waits, deadlocks, throughput |
| NOWAIT | low-latency fail-fast при contention | предпочитаме bounded wait | Бързо решение за conflict | Error handling |
| SKIP LOCKED | queue, independent work items | reporting/financial snapshot | Work distribution без блокиране | Пропуснати заключени редове, fairness |
| SERIALIZABLE | cross-row сложни invariants | без whole-transaction retry policy | Serial-equivalent outcomes | Abort/retry overhead |

## Как го доказваме / изходен checklist

- [PostgreSQL integration tests](../src/test/java/bg/hristomanov/education/tx/PostgresTransactionLabIT.java): stale write, conditional update, NOWAIT SQLSTATE 55P03, SKIP LOCKED distinct claims, live deadlock 40P01.
- За code review: кой е consistency owner? Има ли unique constraints? Кой lock mode и докога? Има ли retry/idempotency policy? При конфликти статистиките показват ли същинската причина?

## Оригинални източници

- [Vlad: Optimistic locking @Version](https://vladmihalcea.com/optimistic-locking-version-property-jpa-hibernate/).
- [Vlad: Database job queue with SKIP LOCKED](https://vladmihalcea.com/database-job-queue-skip-locked/).
- [PostgreSQL 17 explicit locking](https://www.postgresql.org/docs/17/explicit-locking.html).
- [PostgreSQL 17 serialization/deadlock retries](https://www.postgresql.org/docs/17/mvcc-serialization-failure-handling.html).
