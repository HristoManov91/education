# P1 — @Transactional + Virtual Threads + lazy JDBC connections

## 1. Реален казус

`POST /sync` стартира транзакция, извиква външен HTTP API (1–2 s), прави един SQL update и приключва. При 200 Virtual Threads и Hikari `maximumPoolSize=10` новите заявки могат да започнат евтино като threads, но **физическите connections НЕ са виртуални**.

```text
naive:
request → @Transactional BEGIN → borrow JDBC connection → HTTP wait → SQL → COMMIT
                                         └── заета връзка без SQL

better boundary:
request → HTTP wait → @Transactional { SQL; COMMIT } → response
                       └── връзка само за DB unit of work
```

## 2. Decision card

| Въпрос | Преценка |
| --- | --- |
| Проблем | Връзка от малък pool се заема твърде рано/държи твърде дълго. |
| Кога да включа `connection-fetch=lazy`? | След измерване, когато transaction започва преди първия SQL и се използва auto-configured pooled DataSource. |
| Кога НЕ е достатъчно? | Ако SQL е изпълнен **преди** бавно I/O, connection остава заета до transaction end. |
| Печеля | Избягвам физическо заемане на JDBC connection преди SQL. |
| Губя / компромис | Proxy слой и по-сложен mental model; не премахва locks, pool size, timeouts или long transaction. |
| По-проста алтернатива | Премести външното I/O **извън** транзакцията и скъси границата й. |
| Как доказвам | Измервам `HikariPoolMXBean.activeConnections` преди/след първия SQL. |

## 3. Под капака: logical transaction vs physical connection

`@Transactional` дефинира logical transaction boundary (жизнен цикъл на транзакцията). При lazy fetching Spring обгръща pooled `DataSource` с `LazyConnectionDataSourceProxy`; началото на transaction може да не изисква физическо заемане на връзка. Първият JDBC statement материализира connection. След това transaction manager я държи до commit/rollback.

**Сценичната демонстрация** на Josh Long (около 14–16 мин.) е пример за този optimization. Това не е гаранция, че всяка JPA конфигурация отлага физическия `Connection` по същия начин — JDBC driver, persistence provider, isolation, read-only и custom DataSource конфигурация могат да влияят. Тествай със своя стек.

## 4. Naive → good → test

**Naive (не поставяй тази дълга граница върху business orchestration):**

```java
@Transactional
public void synchronize(String externalId) {
    ExternalPayload payload = remoteClient.fetchSlowly(externalId);
    repository.save(map(payload));
}
```

При eager fetching physical connection може да е заета по време на remote call. Ако transaction вече е изпълнила SQL, **дори lazy** не я освобождава.

**Good:** външната заявка се изпълнява извън транзакцията; отделна Spring service извършва само DB unit of work. Не използвай `this.saveTransactional(...)`: self-invocation заобикаля proxy-то.

```java
public void synchronize(String externalId) {
    ExternalPayload payload = remoteClient.fetchSlowly(externalId);
    writer.saveAtomically(payload); // друг Spring-managed bean
}

@Transactional
public void saveAtomically(ExternalPayload payload) {
    repository.save(map(payload));
}
```

Това е **илюстративен pseudocode** за граници; реалният изпълним probe е [TransactionBoundaryProbe](src/main/java/bg/hristomanov/education/boot4/TransactionBoundaryProbe.java), а тестът е [LazyConnectionsTest](src/test/java/bg/hristomanov/education/boot4/LazyConnectionsTest.java).

**Измерване в теста:**

1. Стартира `waitBeforeSql` в отделен thread, но блокира преди първия statement.
2. Проверява `activeConnections == 0`.
3. Освобождава го; стартира `waitAfterSql` и блокира след `SELECT 1`.
4. Проверява `activeConnections == 1`, после след commit отново `0`.

[application.properties](src/main/resources/application.properties) задава `spring.datasource.connection-fetch=lazy`, `maximum-pool-size=2` и `spring.threads.virtual.enabled=true`.

За демонстрацията тестът използва H2 и най-малка целева конкуренция. За реална оценка направи [Testcontainers](../testing/testcontainers/README.md) тест с PostgreSQL, Hikari metrics (active/idle/pending), p95/p99 latency и long transaction log.

## 5. Кога да НЕ използваме Virtual Threads

Virtual Threads са евтини носители на **blocking I/O** (изчакване на входно-изходни операции). Не намаляват времето за SQL, не ускоряват CPU-bound алгоритми и не увеличават лимитите на remote services. Висока конкуренция може да усили `429`, pool pending и lock contention. Ограничаването с semaphore/`@ConcurrencyLimit` е различно от backpressure (управление на потребителското търсене) в Reactor.

Ако ти трябва непрекъснат reactive stream, backpressure или сложна stream композиция, отвори [Loom vs Reactive](../concurrency/loom-vs-reactive/README.md) вместо да дублираме темата тук.

## 6. Failure matrix

| Сценарий | Какво се случва | Какво правим |
| --- | --- | --- |
| HTTP timeout преди SQL | Lazy mode още не е заел физическа connection | прекратяване на workflow; не започвай DB work |
| HTTP timeout след SQL | connection/locks може да стоят до rollback | rollback и кратък transaction boundary |
| 1000 requests, pool 10 | pending/timeout за връзка | concurrency cap, timeout budgets, индекси и измервания |
| Self-invocation | `@Transactional` proxy не се активира | извикване през друг bean, тест на proxy boundary |
| Long report query | lazy mode не скъсява SQL | SQL/index optimization + server-side timeouts |

## 7. Какво запомняме

- „Евтина нишка“ не означава „евтина DB връзка“.
- „Transaction е започнала“ не винаги означава „физическа connection вече е заета“.
- След първия SQL lazy mode **не** освобождава connection преди transaction completion.
- Най-големият win често е **кратък transaction boundary**, не специална настройка.
- Сравни throughput и saturation, не само средна latency.

## 8. Упражнение и code review

- Изключи `spring.datasource.connection-fetch=lazy` и сравни първата асерция в теста; помисли за eager mode.
- Промени кода така, че I/O да е след SQL и виж защо настройката не помага.
- Намали Hikari pool до 1 и проследи pending wait при две одновременни транзакции.
- [ ] Има ли HTTP/sleep/remote calls вътре в `@Transactional`?
- [ ] Знаем ли първия SQL и момента на заемане на connection?
- [ ] Мерим ли active/pending, transaction duration и p95/p99 latency?

## 9. Какво решихме

В началото connection pool можеше да се държи излишно, докато task чака външен ресурс. Показахме кога се заема connection в конфигурирания lazy mode и защо правилният transaction boundary е основната защита. **Ограничение:** демонстрацията е на H2; production системите се проверяват отделно.

## Източници
- [Josh Long — Bootiful Spring Boot 4](https://www.youtube.com/watch?v=1PtdpItmzTo).
- [Spring Boot SQL / lazy connections](https://docs.spring.io/spring-boot/reference/data/sql.html).
- [Spring Boot 4.1 release notes](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.1-Release-Notes).
- [Spring transaction propagation](https://docs.spring.io/spring-framework/reference/7.0/data-access/transaction/declarative/tx-propagation.html).

