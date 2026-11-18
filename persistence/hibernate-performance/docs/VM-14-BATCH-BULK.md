# VM-14 — Hibernate JDBC batching, flush/clear и JPQL bulk DML

## Production incident

Import job обработва 50 000 реда. Всяко `persist()` генерира собствен INSERT, persistence context расте и GC натоварването се увеличава. Developer задава `hibernate.jdbc.batch_size=10` и вижда по-малко `PreparedStatement` обекти, след което обявява „десет пъти по-бързо“.

**Не можем да заключим това без доказателство за действителното executeBatch и измерване на end-to-end latency.**

## Реалният механизъм

- Hibernate `persist` обикновено отлага entity INSERT до flush (ако ID generator го позволява).
- `hibernate.jdbc.batch_size=10` допуска групиране на съвместими JDBC operations.
- `hibernate.order_inserts=true` подрежда INSERT-и по entity/SQL shape и може да подобри групирането, но има CPU/ordering trade-off.
- Hibernate `SequenceEntry` има `@SequenceGenerator(allocationSize=20)`; `IdentityEntry` използва `IDENTITY` и не може да групира Hibernate entity INSERT със същия batching mechanism.

## Доказателство — не само статистики

[HibernatePerformanceTest](../src/test/java/bg/hristomanov/education/hperf/HibernatePerformanceTest.java) регистрира `SessionEventListener.jdbcExecuteBatchStart()` върху текущата Hibernate Session:

~~~java
session.addEventListeners(new SessionEventListener() {
    @Override public void jdbcExecuteBatchStart() {
        batchExecutions.incrementAndGet();
    }
});
~~~

При 23 `SequenceEntry` inserts очакваме повече от една реално изпълнена batch група. При 4 `IdentityEntry` inserts — **0 JDBC batch events**. [PG IT](../src/test/java/bg/hristomanov/education/hperf/PostgresHibernatePerformanceIT.java) доказва реални batch events и persisted 23 records върху PostgreSQL 17.

**Това измерва batch executions на Hibernate/session ниво, не задължително network round-trips на PostgreSQL драйвера.** `Statistics.getPrepareStatementCount()` сам по себе си не е batch counter. Не твърдим фиксирано ускорение.

## Flush/clear в дълъг import

~~~java
for (int i = 0; i < rows.size(); i++) {
    entityManager.persist(toEntity(rows.get(i)));
    if ((i + 1) % 10 == 0) {
        entityManager.flush();
        entityManager.clear();
    }
}
entityManager.flush();
~~~

Това намалява броя **managed** entities в паметта. Но `clear()` detach-ва всички — включително parent entities и pending relationships! Използвай целенасочено само при подходящ import workflow. [H2 test](../src/test/java/bg/hristomanov/education/hperf/HibernatePerformanceTest.java) доказва 53 записани реда и че първата entity вече не е managed след clear.

## Bulk UPDATE bypasses persistence context

~~~java
PerfOrder managed = em.find(PerfOrder.class, id); // total = 10
em.createQuery("update PerfOrder o set o.total = 990 where o.total = 10")
  .executeUpdate();
// managed.getTotal() все още връща 10!
em.clear();
PerfOrder fresh = em.find(PerfOrder.class, id); // total = 990
~~~

[PerfOrderReader.bulkSetTotal()](../src/main/java/bg/hristomanov/education/hperf/read/PerfOrderReader.java) използва bind parameters; [negative/positive test](../src/test/java/bg/hristomanov/education/hperf/HibernatePerformanceTest.java) показва stale managed state преди `clear` и DB state след него.

**Внимание:** JPQL bulk updates не извършват стандартното per-entity dirty checking/cascades/entity callbacks/`@Version` update поведение. Ако трябва optimistic locking/version increment — implement-вай го изрично, иначе рискуваш lost updates. Това е особено важно при масови промени в production.

## Decision contract

| Въпрос | Кога/компромис |
|---|---|
| Проблем | INSERT overhead, голям persistence context, stale managed state от bulk DML |
| Кога batch | Много еднотипни INSERT/UPDATE при измерен bottleneck и съвместим ID generator |
| Кога НЕ | Единичен INSERT, разнородни SQL statements или business flow с identity-generation |
| Печалба | По-малко JDBC batch calls; по-нисък resource overhead в подходящи workloads |
| Загуба | Flush/clear complexity, DB-specific batch limits, ordering side effects, failure recovery |
| Алтернатива | JDBC batch API/driver-native bulk COPY, SQL upsert за специализирани задачи |
| Доказателство | Hibernate session batch listener + persisted rows + negative IDENTITY control |

## Изходен checklist

Измери броя `jdbcExecuteBatchStart`, batch utilization, rows, server CPU/IO, locked rows и wall-clock time; сравни със и без batching върху същата fixture, не в различни среди. При bulk DML реши изрично кога `flush/clear/refresh` и дали трябва versioning.

### Оригинални източници

- Vlad — [pooled-lo optimizer](https://vladmihalcea.com/hibernate-hidden-gem-the-pooled-lo-optimizer/); [Batch inserts with JPA/Hibernate](https://vladmihalcea.com/the-best-way-to-do-batch-processing-with-jpa-and-hibernate/).
- [Hibernate ORM 7.4 — Batch processing](https://docs.hibernate.org/orm/7.4/userguide/html_single/#batch).
- [Hibernate 7.4 SessionEventListener API](https://docs.hibernate.org/orm/7.4/javadocs/org/hibernate/SessionEventListener.html).
