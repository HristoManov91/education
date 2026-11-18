# VM-05 — JDBC connection lifecycle, HikariCP, PreparedStatement и bounded resources

## Реален incident

REST service прави 2 SQL операции и между тях чака външен HTTP service. Ако една физическа връзка стои заемана през това чакане, pool се изчерпва при сравнително малко паралелни заявки. Thread pool, HTTP request, Spring transaction, Hibernate persistence context и **физически JDBC lease** не са синоними.

## Mental model

```text
HTTP request ───────────────────────────────────────────────────────┐
transaction ─────────────── може да обхваща повече от SQL ─────────┤
SQL1 ─ remote service wait ─ SQL2                                  │
       ^ физическата връзка може да остане заета през чакането       │
connection checkout ─────────────────────── release                ┘
```

**Важно:** логическа `@Transactional` сама по себе си не доказва точния момент на физически checkout. Spring Boot/Hibernate настройките (например lazy connection fetching), auto-commit и OSIV влияят на времето на заемане/връщане. Първо измери конкретния runtime, не предполагай.

## Bad вариант

```java
@Transactional
public Summary load(Long id) {
    var order = repository.findById(id).orElseThrow(); // SQL
    riskClient.check(order);                           // HTTP wait while transaction active
    return repository.getSummary(id);                  // SQL
}
```

Причината да изглежда естествено: `@Transactional` обещава удобен scope. Но тук външното I/O не е част от database consistency boundary и може да удължи connection lease/lock lifetime. Не премахвай transaction-а на сляпо, ако две промени трябва да останат атомарни.

## Good: кратка boundary и bounded resources

- Постави транзакционните операции в малки service методи; оркестрацията на външни HTTP calls е извън транзакцията, **ако бизнес consistency позволява това**.
- При директен JDBC използвай `try-with-resources`, `PreparedStatement` с bind parameters и проверявай резултатите от `executeUpdate()`.
- Измервай `active`, `idle`, `pending`, `connectionTimeout`, средно/p99 usage time и DB CPU/locks едновременно.
- Не увеличавай `maximumPoolSize` без DB capacity/throughput evidence; по-голям pool може да влоши queueing и DB performance.

[ConnectionWork.java](../src/main/java/bg/hristomanov/education/tx/ConnectionWork.java) затваря connection веднага след действителната SQL работа. [ConnectionLeaseTest](../src/test/java/bg/hristomanov/education/tx/ConnectionLeaseTest.java) доказва **pool=1 → active=1 → borrow timeout → close → active=0 → успешен повторен checkout**. Няма sleep-based concurrency assertions или измислено throughput измерване.

## Кога и кога не

| Критерий | Решение |
|---|---|
| Проблем | DB connection occupancy, раздута транзакционна boundary, SQL injection |
| Кога се ползва | Във всеки JDBC API, особено при външни calls и ограничен DB pool |
| Кога НЕ | Не разделяй атомарен write workflow в множество DB transactions само за performance |
| Печалба | По-кратки leases, по-предсказуеми resource limits, safe parameter binding |
| Загуба | Повече ясни service boundaries; между два reads може да има различни DB snapshots |
| Алтернатива | `TransactionTemplate` или Spring proxy methods; lazy connection fetching за specific cold path, но не решава SQL→HTTP→SQL |
| Доказателство | [Hikari lease test](../src/test/java/bg/hristomanov/education/tx/ConnectionLeaseTest.java), production monitoring трябва да включва pool metrics и traces |

## Debug checklist

1. Има ли изобщо physical checkout преди HTTP call?
2. В каква транзакция и DB isolation се изпълнява кодът?
3. OSIV държи ли EntityManager отворен след service метода?
4. Сравнил ли си Hikari `pending`, `active`, wait-time и DB locks?
5. Може ли data consistency да се запази с две отделни read transactions?

## Оригинални източници

- [Vlad: Spring Transaction and Connection Management](https://vladmihalcea.com/spring-transaction-connection-management/).
- [Vlad: Optimal Connection Pool Size](https://vladmihalcea.com/optimal-connection-pool-size/).
- [HikariCP](https://github.com/brettwooldridge/HikariCP).
- [Spring transactions](https://docs.spring.io/spring-framework/reference/data-access/transaction.html).
