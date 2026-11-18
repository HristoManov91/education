# VM-19 — Hibernate L1 / L2 / Query Cache, invalidation и consistency

> Java 25, Hibernate 7.4, Spring Boot 4.1.1, test-only JCache/Caffeine provider и H2. **Еднопроцесен correctness lab, не доказателство за multi-node consistency или speedup.**

## 1. Реален production incident

Service чете цени от database и разработчикът добавя second-level entity cache. SQL count пада, но maintenance job изпълнява direct JDBC `UPDATE price=99` и следващите HTTP requests продължават да връщат `20`. Външният writer не е уведомен за кешираните Hibernate regions.

Проблемът не е „дефектен Hibernate“. **Кешът трябва да знае кой пише и кой отговаря за invalidation.**

## 2. Mental model

```text
EntityManager / Session
   | L1 = Identity Map + managed state, живее с persistence context
   v cache miss
SessionFactory
   | L2 = disassembled entity state по ID, между различни sessions
   v cache miss
Database

Отделно: HQL + parameters + pagination
   -> opt-in Query Results Cache + update timestamps на засегнатите таблици
   -> при stale/absent result: SQL срещу Database
```

**L1** не е shared cross-request cache. **L2** не споделя една Java object reference между отделни `EntityManager`-и. **Query cache** не е автоматичен за всички JPQL queries и не гарантира, че entity graph е кеширан.

## 3. Bad → root cause → good

**Bad:** включваме `hibernate.cache.use_query_cache=true` и смятаме, че всеки SQL става по-бърз и direct SQL writers автоматично invalidatе-ват region-и.

**Root cause:** Hibernate знае собствените dirty updates и query-space timestamps. Външен SQL/друга app може да промени DB state без текущият `SessionFactory` да научи. TTL/eviction не гарантират strong consistency.

**Good:** кеширай selective read-heavy entities с измерен reuse. За mutable versioned state избери подходяща strategy (например `READ_WRITE`) и provider. Кеширай само конкретни стабилни queries. Поддържай update-timestamps region със съобразена retention политика. За external writes: изрична eviction/invalidation, coordination с writer или **без L2 за тази entity**.

## 4. Сравнение на стратегиите

| Стратегия | Кога | Кога НЕ / цена |
|---|---|---|
| `READ_ONLY` | Истински immutable reference data | Mutable entities |
| `READ_WRITE` | Versioned mutable entities при известна coordination policy | Invalidation overhead; external JDBC пише без сигнал |
| `NONSTRICT_READ_WRITE` | Допускаме stale window и eventual consistency | Финансови/силно консистентни reads |
| `TRANSACTIONAL` | Поддържан provider с истинска transaction integration | Няма подходящ JTA/transactional cache provider |
| Cache OFF | Чести външни промени, нисък hit rate или висока цена на invalidation | Повече DB queries |

**Update timestamps cache:** query results се валидират чрез timestamps на засегнатите таблици. Изтичането/изхвърлянето на timestamp entries преди cached results може да доведе до stale results. Не конфигурирай TTL на сляпо.

## 5. README → код и изпълними тестове

[CachedCatalogItem.java](../src/main/java/bg/hristomanov/education/hperf/domain/CachedCatalogItem.java) използва `@Cacheable`, `@Cache(READ_WRITE)` и `@Version`. `hibernate-jcache` и Caffeine JCache са **само test dependencies**.

[HibernateCacheLabTest.java](../src/test/java/bg/hristomanov/education/hperf/HibernateCacheLabTest.java) проверява:

1. Нови persistence contexts: различни Java instances, едно DB SELECT и действителен L2 cache hit.
2. ORM update с `@Version`: последващ session вижда committed price и нова версия.
3. Query cache: повторен JPQL query hit; след ORM update върху същата таблица старият result се invalidatе-ва.
4. **Negative proof:** direct JDBC UPDATE не invalidatе-ва Hibernate L2; следва stale цена, а explicit `EntityManagerFactory.getCache().evict` поправя следващия read.

Последният тест умишлено доказва риск, не production recommendation. [Spring Cache labs](../../../spring/caching/README.md) са различна caching система. [VM-21](../VM-21-SQL-OBSERVABILITY.md) събира SQL-count, DB и pool metrics.

```bash
mvn -pl persistence/hibernate-performance test
mvn -pl persistence/hibernate-performance -Ppostgres-integration verify
```

Тестовете доказват **H2 + един SessionFactory**, не multi-replica consistency, network packet count, production latency или Oracle behavior.

## 6. Decision contract

| Въпрос | Решение |
| --- | --- |
| Проблем | Излишни entity/query reads; stale след external mutation |
| Кога | Read-heavy, stable, repeatable и измерен access pattern |
| Кога НЕ | Чести external SQL writes, критична strong consistency, low hit ratio |
| Печалба | Възможни по-малко SQL и DB pressure |
| Загуба | RAM, provider setup, invalidation, multi-node coordination, debugging |
| По-проста алтернатива | DTO/projection и добър fetch plan/index, без ORM L2 |
| Доказателство | [H2/JCache tests](../src/test/java/bg/hristomanov/education/hperf/HibernateCacheLabTest.java) |

## 7. Code review checklist

- Кой пише таблицата: Hibernate, JDBC, друг service, migration?
- Изисква ли бизнесът read-your-writes или bounded stale window?
- Query cache region и timestamps retention настроени ли са коректно?
- Има ли tenant-sensitive data в cache key?
- Коректно ли е кеширането на mutable entity и нейния version?
- Имаме ли реални hit/miss metrics, а не assumptions?
- Може ли caching да скрие N+1 или да удвои invalidation overhead?

## Оригинални източници

- [Vlad Mihalcea — Second-level cache](https://vladmihalcea.com/jpa-hibernate-second-level-cache/).
- [Vlad — Query cache](https://vladmihalcea.com/how-does-hibernate-query-cache-work/).
- [Vlad — Hibernate cache entries](https://vladmihalcea.com/how-does-hibernate-store-second-level-cache-entries/).
- [Hibernate ORM 7.4 User Guide: caching](https://docs.hibernate.org/orm/7.4/userguide/html_single/#caching).
- [Hibernate introduction: JCache providers](https://docs.hibernate.org/stable/orm/introduction/html_single/).
- [Caffeine JCache](https://github.com/ben-manes/caffeine).
