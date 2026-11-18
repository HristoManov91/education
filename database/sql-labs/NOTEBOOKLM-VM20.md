# NotebookLM — VM-20 Oracle 19c vs PostgreSQL 17

## Източници

1. [VM-20 главен учебен материал](docs/VM-20-POSTGRESQL-ORACLE-COMPARISON.md).
2. [PostgreSQL Java integration tests](src/test/java/bg/hristomanov/education/sql/PostgresSqlLabIT.java) и [PG SQL](sql/postgresql/vm20-dialect-lab.sql).
3. [Oracle 19c manual counterpart](sql/oracle/vm20-dialect-lab.sql).
4. [Vlad Mihalcea SQL tutorials](https://vladmihalcea.com/tutorials/sql/), [Oracle EXPLAIN](https://vladmihalcea.com/sql-execution-plan-oracle/).
5. [PostgreSQL 17 SELECT](https://www.postgresql.org/docs/17/sql-select.html), [Oracle 19c SELECT](https://docs.oracle.com/en/database/oracle/oracle-database/19/sqlrf/SELECT.html).

## Audio Overview prompt

> Създай задълбочено аудио обучение на български за Mid+/Early Senior Java backend developer. Започни от production миграция на SQL от Oracle 19c към PostgreSQL 17, при която филтър по празен string променя резултата. Раздели семантиката (празен string vs NULL, ORDER BY), синтаксиса (sequences, recursive WITH, JSON), concurrency (isolation, SKIP LOCKED) и DB physical plans.
>
> За всяка техника използвай **проблем → naive → точен root cause → good → кога НЕ → trade-offs → как се доказва**. Избягвай абсурдното твърдение, че Oracle и PostgreSQL имат различен default NULL ordering за ASC: и двата обичайно са NULLS LAST, но е по-безопасно да се задава явно. Също не твърди, че PostgreSQL/Oracle sequence гарантира gap-free numbering.
>
> Опиши какво се доказва от нашите 4 PostgreSQL Testcontainers tests и кои Oracle скриптове са предназначени само за ръчна проверка. Не приписвай резултати от PG тестове на Oracle. Включи въпроси за JSONB vs JSON_VALUE, FETCH FIRST, EXPLAIN ANALYZE vs DBMS_XPLAN и SQL dialect behavior.
