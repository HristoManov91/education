# VM-18 — JDBC fetch size, cursor streaming и statement caching

## Production проблем

Export чете стотици хиляди редове с `queryForList()`, държи всички в Java heap и причинява дълги GC паузи. Наивното решение е по-голям heap или `statement.setFetchSize(100)`, но `fetchSize` е driver-specific hint и не гарантира ограничена памет, когато application кодът пак събира цял `List`.

## Mental model

Разграничаваме (1) query execution/plan в базата, (2) wire retrieval по fetch batches, (3) JDBC ResultSet/transaction lifecycle и (4) Java object retention. Оптимизирането на един слой не доказва оптимизация на останалите.

## Bad → Good

**Bad:** `List<Long> ids = jdbcTemplate.queryForList("SELECT id FROM stream_event", Long.class)` за неограничени данни.

**Good:** [JdbcStreamingReader](src/main/java/bg/hristomanov/education/tx/JdbcStreamingReader.java) взема собствена връзка, изключва autocommit, използва forward-only PreparedStatement, положителен fetch size, обработва редовете с callback без натрупване в List, затваря ресурсите с try-with-resources и rollback-ва при consumer failure.

При **pgJDBC** server-side cursor retrieval изисква подходящ forward-only query, `autoCommit=false` и положителен `fetchSize`. Важна последица: **връзката и транзакцията остават отворени през обработката**. Consumer-ът не трябва да прави бавни HTTP извиквания за всеки ред; за това често е по-добре bounded keyset chunk processing.

## Доказателство

[PostgresFetchingLabIT](src/test/java/bg/hristomanov/education/tx/PostgresFetchingLabIT.java) проверява върху истински PostgreSQL 17, че fetch size 1 и 10 обработват еднаквите 105 реда с checksum 5565, че exception от consumer не пречи на повторна заявка и че невалиден fetch size се отхвърля. **Не доказваме** брой network round trips, пикова heap памет, план или измерена latency; тези твърдения изискват отделен benchmark/protocol instrumentation.

## Statement cache

PreparedStatement дава параметризирани заявки и възможност за driver/server caching, но не гарантира server-side plan reuse при всяко изпълнение. При pgJDBC настройките `prepareThreshold`, `preparedStatementCacheQueries` и `preparedStatementCacheSizeMiB` са релевантни. Oracle има различна implicit/explicit statement cache система. HikariCP препоръчва statement caching на driver ниво вместо самостоятелен pool-wide statement cache. Тук **не твърдим измерени cache hits**.

## Decision contract

| Въпрос | Решение |
|---|---|
| Проблем | Huge result sets, heap retention, много round trips |
| Кога | Batch export с bounded per-row processing |
| Кога НЕ | Малки резултати; дълги/непредсказуеми consumers; HTTP calls в отворена transaction |
| Печалба | Ограничена памет в нашия consumer, контрол върху retrieval |
| Загуба | Дълъг JDBC lease, driver-specific semantics, resource ownership |
| Алтернатива | Keyset pagination с отделни кратки транзакции |
| Доказателство | Реален [PostgreSQL тест](src/test/java/bg/hristomanov/education/tx/PostgresFetchingLabIT.java) |

## Стартиране

```bash
mvn -pl database/transaction-labs -Ppostgres-integration verify
```

## PostgreSQL и Oracle

Този изпълним тест покрива PostgreSQL, не Oracle. Oracle JDBC row prefetch/statement caching и transaction behavior трябва да се проверят на Oracle; не копирай механично pgJDBC настройки.

## Оригинални източници

- [pgJDBC cursor-based fetching](https://jdbc.postgresql.org/documentation/query/#getting-results-based-on-a-cursor)
- [pgJDBC connection parameters](https://jdbc.postgresql.org/documentation/use/#connection-parameters)
- [Vlad Mihalcea — Hibernate scrollable results](https://vladmihalcea.com/hibernate-scrollable-results/)
- [Vlad Mihalcea — JDBC statement caching](https://vladmihalcea.com/statement-caching-jdbc/)
- [HikariCP statement cache notes](https://github.com/brettwooldridge/HikariCP#statement-cache)
- [Oracle JDBC statement caching](https://docs.oracle.com/en/database/oracle/oracle-database/19/jjdbc/statement-caching.html)
