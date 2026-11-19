# VM-24 — PostgreSQL UPSERT / Oracle MERGE: concurrency correctness

## Реалният проблем

Два worker-а обработват едно и също външно събитие с `tenant_id + external_ref`. И двата първо правят `SELECT` и виждат „няма запис“, после изпълняват `INSERT`. Наивната проверка е **race**: може да има duplicated event или unique constraint violation.

Трябва да дефинираме **business key**, поведение при конфликт и какво всъщност означава идемпотентност.

## Bad → root cause

```java
if (!dao.exists(tenantId, externalRef)) {
    dao.insert(event);
}
```

Това не е atomic operation. JVM `synchronized` не решава проблема между различни application instances. Само `SELECT ... FOR UPDATE` без съществуващ row също не е общо „заключи липсващия ред“ решение.

## Good → atomic unique key and UPSERT

[PostgresUpsertRepository.java](../src/main/java/bg/hristomanov/education/sql/PostgresUpsertRepository.java):

```sql
INSERT INTO vm24_ledger(tenant_id,external_ref,amount)
VALUES (?,?,?)
ON CONFLICT (tenant_id,external_ref)
DO UPDATE SET amount = EXCLUDED.amount
RETURNING id,amount;
```

`UNIQUE(tenant_id,external_ref)` е correctness gate при concurrent writers. `RETURNING` връща identity и записаната стойност. **Това е last-write-wins replacement, НЕ автоматична идемпотентност на payment processing**. Ако duplicate delivery има различна стойност, бизнес policy може да изисква reject, compare hash или `DO NOTHING` и проверка. Не ползвай сляпо last-write-wins за счетоводни движения.

## Как доказваме

[PostgresUpsertLabIT](../src/test/java/bg/hristomanov/education/sql/PostgresUpsertLabIT.java) върху PostgreSQL 17:

- втори UPSERT запазва същия database ID и променя сумата;
- еднакъв external reference при **различни tenants** не се конфликтува;
- два workers, синхронизирани с `CyclicBarrier`, пишат едновременно еднакъв business key: **един ред**, една identity; крайният amount може да е един от двата;
- `CHECK(amount >= 0)` остава действащ и при `ON CONFLICT DO UPDATE`, SQLSTATE 23514.

Тестът доказва DB uniqueness/atomicity, не network-level exactly-once, бизнес идемпотентност или throughput.

## Oracle 19c comparison

Oracle `MERGE INTO ... USING ... ON (...) WHEN MATCHED THEN UPDATE ... WHEN NOT MATCHED THEN INSERT ...` има различни concurrency semantics. Наличието на `MERGE` **не** прави Oracle portable replica на PostgreSQL `ON CONFLICT`; при едновременни insert-и трябва реален Oracle тест, unique constraint и conflict/retry policy. За SQL outline виж [ръчния Oracle пример](../sql/oracle/vm24-merge.sql).

## Decision contract

| Въпрос | Решение |
|---|---|
| Проблем | TOCTOU race при check-then-insert |
| Кога | Един стабилен DB unique key, ясна conflict policy, concurrent writers |
| Кога НЕ | Няма дефиниран business key, или не можем да презаписваме вече обработено събитие |
| Печалба | Atomic DB statement и уникалност при concurrency |
| Загуба | DB-specific syntax и конфликти, които изискват business interpretation |
| Алтернатива | Explicit transaction + locks или insert+handle unique violation, ако policy го изисква |
| Доказателство | [PostgreSQL concurrency integration test](../src/test/java/bg/hristomanov/education/sql/PostgresUpsertLabIT.java) |

## Стартиране и code review

`mvn -pl database/sql-labs -Ppostgres-integration verify`

Провери: business key, уникалност, tenant isolation, versioning, optimistic conflict, `DO UPDATE` vs `DO NOTHING`, retries с idempotency key, audit trail и DB statement count.

## Оригинални източници

- [Vlad Mihalcea — SQL tutorials](https://vladmihalcea.com/tutorials/sql/)
- [PostgreSQL 17 INSERT ON CONFLICT](https://www.postgresql.org/docs/17/sql-insert.html)
- [Oracle 19c MERGE](https://docs.oracle.com/en/database/oracle/oracle-database/19/sqlrf/MERGE.html)
