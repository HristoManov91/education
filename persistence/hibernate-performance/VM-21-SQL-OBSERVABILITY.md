# VM-21 — Production SQL/ORM observability: Hibernate, JDBC pool и DB execution plans

> Надграждане, което **преизползва вече работещите tests**, вместо да копира N+1/locking лабораториите. Java 25, Spring Boot 4.x, Hibernate 7.x, PostgreSQL 17; Oracle сравнението е manual. Няма фиктивни benchmark стойности.

## 1. Реалният казус

След release даден endpoint става бавен при натоварване. В log се вижда „само една SQL заявка“, но Hikari pending connections растат. Сляпо добавяме индекс или по-голям pool и проблемът остава. Трябва да установим **къде точно е чакането**: API → Spring transaction → JDBC checkout → ORM SQL fanout → DB parse/plan/execute → locks → network → serialization.

**SQL log alone is not enough.** Той не показва дали connection-ът е бил заеман по време на remote HTTP call, нито дали DB е чакала lock или CPU.

## 2. Диагностичен flow

```text
HTTP p95/p99 elevated?
    |
    +-- Hikari pending / connection acquire time elevated?
    |       +-- active connections + lease duration + transaction span
    |       +-- SQL -> external HTTP -> SQL? OSIV / lazy checkout?
    |
    +-- JDBC statements per request elevated?
    |       +-- N+1, eager relationships, serialization outside service
    |       +-- compare fetch-join / EntityGraph / DTO projection
    |
    +-- SQL execution time elevated?
            +-- real query + bind shape + cardinality + actual DB plan
            +-- index selectivity, statistics, lock wait, CPU, I/O
```

Едновременно наблюдавай **throughput** и **error rate**, иначе оптимизацията на една метрика може да влоши останалите.

## 3. Коректни измервания и граници

| Сигнал | Какво доказва | Какво НЕ доказва |
|---|---|---|
| Hibernate `Statistics.getPrepareStatementCount()` | Брой Hibernate prepared statements в наблюдавания scope | Брой wire packets, DB round-trips или физически executions при batching |
| Hibernate `StatementInspector` | Генерирания SQL текст и query shape | Bind values, runtime plan или query latency |
| `SessionEventListener.jdbcExecuteBatchStart` | Hibernate batch execution event | Точен batch packet count, latency или inserted-row count |
| Hikari `active/idle/pending` | Pool pressure / checkout availability | DB CPU, lock contention или correctness |
| PostgreSQL `EXPLAIN (ANALYZE, BUFFERS)` | Real executor plan, actual/estimated rows и buffers за конкретната заявка | End-to-end HTTP p99 при production load |
| DB lock & wait views | Waiting sessions/locks | Автоматично кой application use case е причинил проблема без correlation |

**Обхватът е решаващ:** Hibernate statistics може да е factory-wide; ако тестове/requests работят паралелно, броят не принадлежи непременно само на един user request. Използвай изолирани fixtures, trace IDs, безопасни sampled spans и ясно означен measurement window.

## 4. Bad → Good

**Bad:** виждаме 25 SQL lines в log и заключаваме „25 DB round trips“; или увеличаваме Hikari `maximumPoolSize` от 10 на 100, без да разбираме DB concurrency и задържането на connections.

**Good:** измерваме независимо:
1. **SQL count и fetch plan** върху фиксирана fixture, с чист persistence context.
2. **Batch events** вместо да броим само SQL логовете.
3. **Connection lease / pending** при възпроизводим pool exhaustion.
4. **DB execution plan** с real bind shape, статистики и примерна cardinality.
5. **Request traces** за correlation със service и DB spans, без да записваме чувствителни стойности.

## 5. README → реален код и тестове

| Диагностичен въпрос | Доказателство |
|---|---|
| N+1: 6 statements срещу 1 с JOIN FETCH/EntityGraph | [HibernatePerformanceTest](src/test/java/bg/hristomanov/education/hperf/HibernatePerformanceTest.java) — `naiveLazyToOneProducesNPlusOneWhileFetchJoinAndEntityGraphUseOneSql` |
| Реален PostgreSQL N+1: 5 срещу 1 | [PostgresHibernatePerformanceIT](src/test/java/bg/hristomanov/education/hperf/PostgresHibernatePerformanceIT.java) |
| Hibernate executeBatch events | [HibernatePerformanceTest](src/test/java/bg/hristomanov/education/hperf/HibernatePerformanceTest.java) и PostgreSQL IT |
| SQL shape + bounded collection pagination | [SqlCaptureInspector](src/test/java/bg/hristomanov/education/hperf/SqlCaptureInspector.java) и `hibernate74UsesBoundedNestedFetchAndTwoPhasePageReturnsSameParents` |
| Pool occupied with zero SQL | [ConnectionLeaseTest](../../database/transaction-labs/src/test/java/bg/hristomanov/education/tx/ConnectionLeaseTest.java) |
| Skewed data, actual PostgreSQL plan (1%/99%) | [PostgresSqlLabIT](../../database/sql-labs/src/test/java/bg/hristomanov/education/sql/PostgresSqlLabIT.java) и [manual EXPLAIN](../../database/sql-labs/sql/postgresql/manual-explain.sql) |
| Isolation/deadlock and SQLSTATE | [PostgresTransactionLabIT](../../database/transaction-labs/src/test/java/bg/hristomanov/education/tx/PostgresTransactionLabIT.java) |

Започни с тези тестове; ако предстои production диагностика, добави correlation/observability върху app конкретния endpoint, а не върху всяка entity getter операция.

## 6. Hypersistence: кога инструментът помага

- **Hypersistence Utils** е open-source библиотека с utility types и решения, не универсална магическа performance оптимизация. Включвай конкретна dependency **само при действителна необходимост**, с версия съвместима с текущия Hibernate.
- **Hypersistence Optimizer** е отделен продукт за откриване на persistence/mapping проблеми. Автоматичните предупреждения са **hypothesis generator**, не заместител на SQL plan/DB counters или load testing. Не изискваме платен инструмент, за да работят нашите лаборатории.

## 7. Decision contract

| Въпрос | Решение |
|---|---|
| Проблем | Не знаем дали bottleneck е ORM, connection pool, DB plan или lock |
| Кога | Slow endpoints, regression след release, DB bottleneck и code review |
| Кога НЕ | Не активирай шумен SQL/bind logging и full statistics навсякъде в production без privacy/perf контрол |
| Печалба | Evidence-driven diagnosis, по-малко random tuning |
| Загуба | Overhead, metrics cardinality, log volume, риск от PII/bind leakage |
| По-проста алтернатива | Targeted reproduction + SQL plan при единична известна бавна заявка |
| Доказателство | Съществуващи изолирани JPA, Hikari и PostgreSQL тестове (линкове горе) |

## 8. Практическа процедура

```bash
# Fast ORM statements and batching proof
mvn -pl persistence/hibernate-performance test

# Real DB plan + real Hibernate SQL/Batch evidence (Docker)
mvn -pl persistence/hibernate-performance -Ppostgres-integration verify
mvn -pl database/sql-labs -Ppostgres-integration verify

# Hikari pool, MVCC/locks
mvn -pl database/transaction-labs -Ppostgres-integration verify
```

Production troubleshooting checklist: incident timeframe, exact endpoint, DB vendor/version, query fingerprint, bind parameter **types/shape** (не secrets), request ID/trace, pool active/pending/usage, SQL count, transaction duration, DB wait event, actual plan и suspected alternative. Прави една промяна наведнъж, пази baseline и докажи regression protection в тест.

## 9. PostgreSQL / Oracle

PostgreSQL: `pg_stat_statements` (ако е активиран), `pg_stat_activity`, `pg_locks`, `EXPLAIN (ANALYZE, BUFFERS)`. Oracle: `V$SQL`, `V$SESSION`, `DBMS_XPLAN.DISPLAY_CURSOR`, AWR/ASH при подходящ license/privileges. **Не пренасяй метрики и функции 1:1** и не предполагай, че Oracle diagnostics са без лиценз/права. В този проект Oracle evidence е само ръчно описано, не автоматично потвърдено.

## 10. Какво научихме

SQL count ≠ DB round-trips ≠ pool occupancy ≠ latency. Hibernate може да е източник на много SQL, но може и една DB заявка да е бавна заради план/locks. Доказателството е **съвкупност** от независими сигнали, събрани за една и съща операция.

## Оригинални източници

- Vlad Mihalcea: [Hibernate articles](https://vladmihalcea.com/tutorials/hibernate/), [Detecting N+1 during testing](https://vladmihalcea.com/how-to-detect-the-n-plus-one-query-problem-during-testing/), [Connection pool sizing](https://vladmihalcea.com/optimal-connection-pool-size/).
- [Hibernate ORM User Guide — statistics](https://docs.hibernate.org/orm/7.4/userguide/html_single/#statistics).
- [PostgreSQL 17 monitoring](https://www.postgresql.org/docs/17/monitoring-stats.html), [EXPLAIN](https://www.postgresql.org/docs/17/using-explain.html).
- [Hypersistence Utils GitHub](https://github.com/vladmihalcea/hypersistence-utils).
- [Oracle 19c DBMS_XPLAN](https://docs.oracle.com/en/database/oracle/oracle-database/19/arpls/DBMS_XPLAN.html).
