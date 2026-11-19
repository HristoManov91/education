# VM-28 — Hibernate entity inheritance и polymorphic SQL cost

## Входен казус

Транспортна система съхранява VAN и DRONE с общи id/code, но различни специфични атрибути. Избираме JPA inheritance механично и след месец polymorphic `findAll` се оказва бавен: ORM прави JOIN към subtype таблици или широк SELECT с много nullable колони. Това не е проблем на Java inheritance сама по себе си, а на **relational mapping и query workload**.

## Mental model

| Стратегия | Физическа схема | При polymorphic reads | Trade-off |
|---|---|---|---|
| `SINGLE_TABLE` | Една таблица + discriminator, subtype колони nullable | Без joins между subtypes | Широка таблица, subtype constraints са по-трудни |
| `JOINED` | Base table + subtype table | JOIN към нужни subtype данни; polymorphic query може да е скъп | Релационна нормализация, DB constraints |
| `TABLE_PER_CLASS` | Отделна цяла таблица на subtype | UNION/polymorphic query complexity | Повторени колони, ID generation/query cost |
| `@MappedSuperclass` | Няма queryable base entity | Няма polymorphic base query | Много просто споделяне на fields |

Няма универсален победител: решението зависи от cardinality, subtype ratios, write/read и SQL execution plan.

## Good изпълним пример

[TransportAsset](../src/main/java/bg/hristomanov/education/hperf/domain/TransportAsset.java) е `@Inheritance(strategy=JOINED)` с общ `code`, а [FleetVan](../src/main/java/bg/hristomanov/education/hperf/domain/FleetVan.java) и [FleetDrone](../src/main/java/bg/hristomanov/education/hperf/domain/FleetDrone.java) имат отделни subclass таблици. [AuditingAndInheritanceTest](../src/test/java/bg/hristomanov/education/hperf/AuditingAndInheritanceTest.java) прави две операции `persist`, чете polymorphic `TransportAsset` query и проверява реалните base/subtype SQL таблици и subtype-specific стойности.

## Bad → root cause

**Bad:** добавяме `@Inheritance` по подразбиране, без SQL plan и schema review; позволяваме `@Data`/Lombok да включи lazy subtype relationships в `equals/hashCode/toString`, или прехвърляме цял entity graph към REST.

**Good:** избираме strategy според реалната схема и query shape, преглеждаме actual SQL plan, предпочитаме DTO projections за read-only API, изрично проверяваме subtype identity и ограничения.

## Decision contract

| Въпрос | Отговор |
|---|---|
| Проблем | Сложен mapping, overfetch, неочаквани JOIN/UNION заявки |
| Кога JOINED | Подтиповете имат ясно различни колони и искаме relational constraints |
| Кога НЕ | Силно polymorphic read-heavy списъци с минимални subtype различия |
| Печелим | Нормализирана схема, subtype-specific schema и polymorphic model |
| Губим | Повече tables/joins и сложни queries/migrations |
| По-проста алтернатива | Composition или отделни entities с explicit read DTO |
| Доказваме | [H2 JPA integration tests](../src/test/java/bg/hristomanov/education/hperf/AuditingAndInheritanceTest.java) |

`mvn -pl persistence/hibernate-performance test`

**Граници:** H2 tests доказват JPA semantics, а не Oracle/PostgreSQL execution plans. За рефакторинг на реална база ползвай `EXPLAIN`/DBMS_XPLAN и реален dataset.

## Оригинални източници

- [Vlad Mihalcea — Hibernate tutorials](https://vladmihalcea.com/tutorials/hibernate/)
- [Hibernate ORM 7.4 — Inheritance](https://docs.hibernate.org/orm/7.4/userguide/html_single/#entity-inheritance)
- [Jakarta Persistence 3.2](https://jakarta.ee/specifications/persistence/3.2/)
