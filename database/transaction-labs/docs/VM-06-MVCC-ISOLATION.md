# VM-06 — ACID, MVCC, READ COMMITTED, REPEATABLE READ

## Production incident

В една business operation четем складовата наличност на два пъти; друг request я променя между тях. Първото четене връща 10, второто 12. Има ли bug? **Зависи каква isolation contract обещава операцията.**

## Обяснение

**ACID**: atomicity (всичко/нищо), consistency (инварианти в рамките на transaction/constraints), isolation (какво виждаме под concurrency), durability (committed data survives failures според DB гаранциите). Не бъркай DB \`consistency\` с автоматично коректна application business rule.

**MVCC (multi-version concurrency control):** PostgreSQL държи версии на редовете, така че readers и writers често не се блокират взаимно. Това не отменя row-level locking при UPDATE/SELECT FOR UPDATE. **2PL** (two-phase locking) е различен concurrency mechanism; различните DB системи комбинират MVCC и locks по различен начин.

| PostgreSQL 17 isolation | Какво вижда повторен SELECT |
|---|---|
| READ UNCOMMITTED | PostgreSQL реално го третира като READ COMMITTED |
| READ COMMITTED (default) | Нов snapshot **за всеки statement**; 10, после 12 е възможно |
| REPEATABLE READ | Стабилен snapshot от първия non-transaction-control statement; 10, после 10; **PostgreSQL** предотвратява и някои аномалии над SQL standard minimum |
| SERIALIZABLE | Целта е еквивалентен serial outcome; при конфликт може да има SQLSTATE **40001** и трябва retry на **цялата** транзакция |

## Bad: приемаме, че @Transactional означава repeatable reads

```java
@Transactional
public int compareTwice(long id) {
    int before = dao.read(id);
    // different request may commit right here
    return dao.read(id); // not necessarily 'before' on READ COMMITTED
}
```

Spring `@Transactional` задава boundary; isolation по подразбиране може да следва database default. Hibernate first-level cache може да върне същата вече управлявана entity в един persistence context, но това **не доказва** DB-level repeatable read за всички SQL/projections.

## Good: избираме семантика според операцията

- За независим read-only list/report: READ COMMITTED често е достатъчен.
- За consistency-heavy multi-statement invariant може да са нужни explicit locks или SERIALIZABLE с **whole-transaction retries**.
- За row-level lost update предпочети версиониране (`@Version` / conditional UPDATE) или атомарно `UPDATE quantity=quantity+1` според use case.
- Не настройвай глобално SERIALIZABLE без измерване на abort/retry rates и lock contention.

## Как го доказваме

[PostgresTransactionLabIT](../src/test/java/bg/hristomanov/education/tx/PostgresTransactionLabIT.java) съдържа реални 2-connection сценарии:
- uncommitted UPDATE е невидим; след rollback данните остават непроменени;
- READ COMMITTED → 10, после 12;
- REPEATABLE READ → 10, после 10 в първата транзакция; нова транзакция → 12.
Няма H2-only изводи за MVCC.

## Decision contract

| Въпрос | Отговор |
|---|---|
| Какво решава | Правилен избор на видимост, атомарност и retry policy |
| Кога | При транзакции с няколко SELECT/DML, concurrency, бизнес инварианти |
| Кога НЕ | Не използвай SERIALIZABLE за всяка CRUD операция без нужда |
| Печелим | Изричен correctness contract, по-малко implicit предположения |
| Губим | По-дълъг snapshot/locks, serialization failures, retry complexity |
| Алтернатива | Една атомарна SQL операция или optimistic locking вместо широк serializable workflow |
| Доказателство | 3 [PostgreSQL IT сценария](../src/test/java/bg/hristomanov/education/tx/PostgresTransactionLabIT.java) |

## Oracle разлика

Oracle **не** предлага напълно идентична на PostgreSQL `REPEATABLE READ` isolation настройка; налични са `READ COMMITTED` и `SERIALIZABLE` (вкл. `READ ONLY` transaction), с Oracle-specific semantics. Не копирай без промяна `Connection.TRANSACTION_REPEATABLE_READ` като production Oracle solution. За Oracle поведението използвай реален Oracle instance, а не H2/PG тест.

## Оригинални източници

- [PostgreSQL 17: Transaction Isolation](https://www.postgresql.org/docs/17/transaction-iso.html).
- [PostgreSQL 17: Serialization Failure Handling](https://www.postgresql.org/docs/17/mvcc-serialization-failure-handling.html).
- [Vlad: Optimistic locking](https://vladmihalcea.com/optimistic-locking-version-property-jpa-hibernate/).
- [Oracle 19c: Data Concurrency and Consistency](https://docs.oracle.com/en/database/oracle/oracle-database/19/cncpt/data-concurrency-and-consistency.html).
