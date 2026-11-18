# NotebookLM — VM-01..VM-04: High-Performance SQL на български

## Sources за импортиране

1. [Главен урок](README.md) и 4 тематични глави [VM-01](docs/VM-01-SQL-SEMANTICS.md), [VM-02](docs/VM-02-RELATIONAL-CONSTRAINTS.md), [VM-03](docs/VM-03-CTE-WINDOWS.md), [VM-04](docs/VM-04-INDEXES-AND-PLANS.md).
2. [Упражненията](EXERCISES.md), [Pg fixtures](src/main/resources/sql/postgresql/performance.sql) и [PostgreSQL тестът](src/test/java/bg/hristomanov/education/sql/PostgresSqlLabIT.java).
3. Vlad Mihalcea: [SQL tutorials](https://vladmihalcea.com/tutorials/sql/), [EXISTS](https://vladmihalcea.com/sql-exists/), [Index Selectivity](https://vladmihalcea.com/index-selectivity/).
4. PostgreSQL: [constraints](https://www.postgresql.org/docs/17/ddl-constraints.html) и [EXPLAIN](https://www.postgresql.org/docs/17/using-explain.html); Oracle: [DBMS_XPLAN](https://docs.oracle.com/en/database/oracle/oracle-database/19/arpls/DBMS_XPLAN.html).

## Audio Overview prompt

> Създай **задълбочено техническо аудио обучение на български**, предназначено за Java/Spring Mid+ → Early Senior backend developer. Не прави кратко резюме и не преразказвай източници един по един. Свържи VM-01–VM-04 в една production история: report дублира клиенти, LEFT JOIN губи клиентите без orders, application-only validation допуска invalid data, а добавен индекс не гарантира подобрение.
>
> Използвай format: **конкретен проблем → naive SQL → защо изглежда логичен → exact root cause → добър SQL → как тестът го доказва → кога НЕ да ползваме техниката → компромиси**.
>
> Отдели 8–10 минути на SQL three-valued logic/NULL, ON vs WHERE, cardinality и EXISTS; 8–10 минути на PK/UNIQUE/FK/CHECK, concurrency race и nullable CHECK; 10–12 минути на CTE/recursive CTE, ROW_NUMBER/RANK/DENSE_RANK и tie-breakers; 12–15 минути на selective indexes, 1% vs 99% workload, estimated vs actual rows, PostgreSQL EXPLAIN (ANALYZE, BUFFERS) и Oracle DBMS_XPLAN.
>
> При всяка концепция използвай точни числа от нашата fixture: 5 клиенти, 7 orders, 4 joined PAID rows, 3 клиенти с PAID, 20 000 task_event реда с 200 RETRY и 19 800 DONE. Не измисляй други измерени timings, scan plan или резултати.
>
> Обясни различията PostgreSQL 17 / Oracle 19c: SQL syntax, empty string NULL, `WITH RECURSIVE` vs Oracle recursive factoring, `LIMIT` vs `FETCH FIRST`, EXPLAIN и privileges. Бъди много ясен кога H2 тест не доказва PostgreSQL/Oracle optimizer и кога трябва реална база.
>
> Завърши с 5 въпроса за проверка без да издаваш отговора веднага: `EXISTS vs JOIN`, `CHECK` при `NULL`, `ROW_NUMBER vs RANK`, защо индексът може да е игнориран и какво точно измерва `EXPLAIN ANALYZE`.

## Follow-up

След аудиото изпълни [EXERCISES.md](EXERCISES.md) и отвори съответните Java/SQL файлове. Не отбелязвай урока за „усвоен“, преди отговорите да са реално проверени.
