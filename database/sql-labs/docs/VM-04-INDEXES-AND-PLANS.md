# VM-04 — B-tree indexes, selectivity, statistics и execution plans

**Реален казус:** Spring/QueryDSL отчита заявка за „обработи чакащите задачи“ по статус. Добавяме индекс върху `status`, но при `DONE` optimizer прави table scan. „Не използва индекса“ **не означава** „планът е грешен“.

## 1. Какво физически се случва

- **Index scan:** намира адресите на подходящите записи през index structure; може да добави random heap/table lookups.
- **Sequential scan:** чете последователно таблицата; често добър, когато ни трябват голяма част от редовете.
- **Cost-based optimizer:** избира план спрямо статистики, размери, селективност, I/O модел, нужни колони и достъпни алгоритми.
- **Estimated rows vs actual rows:** голяма разлика показва проблем в оценките; не е достатъчно просто да видиш `Index Scan`.

В [performance.sql](../src/main/resources/sql/postgresql/performance.sql) има 20 000 `task_event` реда: 200 `RETRY` (1%) и 19 800 `DONE` (99%). И двата predicate-а са с еднакъв syntax, но много различна селективност.

## 2. Bad → good investigation

**Bad:** „Създадох index, затова е задължително заявката да го използва.“ Или: „Винаги увеличи броя индекси“.

~~~sql
CREATE INDEX idx_task_event_status ON task_event(status);
-- Това НЕ гарантира Index Scan при status='DONE'.
~~~

**Good:** измерваме реално, на истинска база, със събрани статистики:

~~~sql
ANALYZE task_event;

EXPLAIN (ANALYZE, BUFFERS)
SELECT * FROM task_event WHERE status = 'RETRY';

EXPLAIN (ANALYZE, BUFFERS)
SELECT * FROM task_event WHERE status = 'DONE';
~~~

`EXPLAIN ANALYZE` **изпълнява заявката**. Никога не го изпълнявай без оценка на риска върху production `UPDATE/DELETE`; за DML експерименти използвай sandbox или транзакция с последващ `ROLLBACK` и прецени side effects.

Не очакваме фиксиран milliseconds или гарантирано `Index Scan`. Файловете демонстрират защо **различни data distributions → различен оптимален план**. Виж [PostgresSqlLabIT](../src/test/java/bg/hristomanov/education/sql/PostgresSqlLabIT.java), който проверява редовете и отпечатва двата **истински** JSON plan-а, без крехки assertions за избрания operator.

## 3. Composite index: как подреждаме колоните

Workload: „дай последните 10 `RETRY` задачи за tenant 1“.

~~~sql
CREATE INDEX idx_task_event_tenant_status_created
    ON task_event(tenant_id, status, created_at DESC, id DESC);

SELECT id FROM task_event
WHERE tenant_id = 1 AND status = 'RETRY'
ORDER BY created_at DESC, id DESC
LIMIT 10;
~~~

Това е добър **кандидат** за индекс, защото използва equality predicates отпред и полета, нужни за сортировката, след тях. Но планът зависи от статистики, таблица, конкуриращи индекси и expected rows. Не е универсална формула „равенство-диапазон-сортировка“, която автоматично печели винаги.

`id DESC` е tie-breaker: без тотален ред pagination може да е нестабилна. **OFFSET** може да има висока цена при дълбоки страници; `keyset pagination` разглеждаме по-подробно във VM-13.

## 4. Практическо четене на plan

| Поле | Защо ни интересува |
| --- | --- |
| `Node Type` | Seq Scan, Index Scan, Bitmap Scan, Hash/Nested Loop/Merge Join и др. |
| `Plan Rows` | Estimated cardinality |
| `Actual Rows` и `Actual Loops` | Реално прочетени/върнати редове; броят се тълкува с loops |
| `Buffers` | Кои страници идват от buffer cache, I/O и други ефекти |
| `Planning Time` / `Execution Time` | Важни числа, но не пряк заместител на end-to-end latency |

`EXPLAIN (FORMAT JSON)` дава машинно обработваем план. Тестът проверява JSON структура/статистики, а не точен `cost` и node, понеже те варират с machine, cache, статистики и версия.

## Decision contract

| Въпрос | Избор |
| --- | --- |
| Проблем | Slow selective queries, sort work и ненужни прочетени редове |
| Кога индекс | Често изпълняван, измерен workload с добре подбран predicate/order/access path |
| Кога НЕ | Силно write-heavy таблица; query връща почти всички редове; индексът дублира друг или няма осезаем ефект |
| Печелим | Потенциално по-нисък I/O, CPU и latency за конкретния access path |
| Губим | Disk, memory/cache pressure, write amplification, schema maintenance |
| Алтернативи | Query rewrite, projections, pagination, partitioning, актуални статистики, denormalized read model (след доказателство) |
| Доказваме | [performance.sql](../src/main/resources/sql/postgresql/performance.sql) + [manual-explain.sql](../sql/postgresql/manual-explain.sql) + [PostgresSqlLabIT](../src/test/java/bg/hristomanov/education/sql/PostgresSqlLabIT.java) |

## 5. PostgreSQL vs Oracle

- **PostgreSQL 17:** `ANALYZE table` събира статистики; `EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON)` за измерване; composite index и `LIMIT`.
- **Oracle 19c:** `DBMS_STATS.GATHER_TABLE_STATS` за статистики; `DBMS_XPLAN.DISPLAY_CURSOR(..., 'ALLSTATS LAST')` за execution stats **ако** средата/правата го позволяват; pagination `FETCH FIRST 10 ROWS ONLY`.
- Oracle може да има index fast full scan, full table scan, index range scan; не копираме 1:1 PG plan термините.

Пример: [Oracle reports-and-plan.sql](../sql/oracle/reports-and-plan.sql). **Ръчен**, не „проверен в Oracle CI“.

## README → код и exit checklist

- [Data fixture](../src/main/resources/sql/postgresql/performance.sql) — skew, indexes, ANALYZE.
- [JSON plan tests](../src/test/java/bg/hristomanov/education/sql/PostgresSqlLabIT.java) — тест за действителни редове; отпечатване на измерения план; нулеви фиксирани timing promises.
- [Oracle scripts](../sql/oracle/reports-and-plan.sql) — comparator при наличен 19c.
- **След урока:** за slow query умееш да представиш query text, bind values, stats age, estimated/actual rows, plan и latency distribution, преди да съветваш „добави индекс“.

### Оригинални източници

- Vlad Mihalcea — [Index Selectivity](https://vladmihalcea.com/index-selectivity/), [SQL execution plan Oracle](https://vladmihalcea.com/sql-execution-plan-oracle/), [SQL tutorials](https://vladmihalcea.com/tutorials/sql/).
- [PostgreSQL 17 EXPLAIN guide](https://www.postgresql.org/docs/17/using-explain.html), [Multicolumn indexes](https://www.postgresql.org/docs/17/indexes-multicolumn.html), [ANALYZE](https://www.postgresql.org/docs/17/sql-analyze.html).
- [Oracle 19c DBMS_XPLAN](https://docs.oracle.com/en/database/oracle/oracle-database/19/arpls/DBMS_XPLAN.html), [Oracle optimizer statistics](https://docs.oracle.com/en/database/oracle/oracle-database/19/tgsql/optimizer-statistics-concepts.html).
