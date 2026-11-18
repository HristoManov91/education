# VM-11–VM-14, VM-16 — упражнения, диагностика, self-check

## VM-19 — Hibernate caching и consistency (20–30 мин)

1. Защо L1 EntityManager identity cache не е същото като SessionFactory L2 cache?
2. Ако Hibernate cached query върне повторно SQL rows без DB hit, каква е ролята на update-timestamps region?
3. Кога `READ_ONLY` и `READ_WRITE` са подходящи? Кога cache OFF е по-добра идея?
4. Защо direct JDBC update може да остави cached entity stale, въпреки @Version?
5. Пусни [HibernateCacheLabTest](src/test/java/bg/hristomanov/education/hperf/HibernateCacheLabTest.java). Обясни кои 4 semantics доказва и какво НЕ доказва за multi-node реплики.

[Пълен VM-19 урок](docs/VM-19-HIBERNATE-CACHE.md) · [NotebookLM](NOTEBOOKLM-VM19.md)

## Микросесия 1: Identifier generation (20–30 мин)
1. След `em.persist(new SequenceEntry(...))` как ID може да е известен, докато в DB още няма ред? Докажи чрез JDBC SELECT, не JPQL.
2. Защо `IDENTITY` ограничава Hibernate INSERT batching?
3. Какво става, ако `allocationSize=20`, но DB sequence DDL е със несъвместим increment? Какъв е risk при два JVM процеса?

## Микросесия 2: N+1/fetch (20–30 мин)
1. Защо 7 orders, сочещи към 5 различни customers, могат да дадат 6 SQL, а не 8?
2. Кога `@EntityGraph` е по-четим от JPQL `JOIN FETCH`? Може ли EAGER да произведе N+1?
3. Какво се случва, ако fetch-нем две много големи колекции в една SQL заявка?

## Микросесия 3: pagination (20–30 мин)
1. Защо `ORDER BY createdAt` без ID tie-breaker не е детерминистичен?
2. Защо keyset cursor съхранява и timestamp, и ID? Какво се случва при update на sort key между requests?
3. Как Hibernate 7.4 ограничава parent IDs чрез nested SQL при `JOIN FETCH collection` + `setMaxResults`, а наивният raw `JOIN + LIMIT` разделя колекцията?
4. Напиши 2-step алгоритъм: bounded parent ID page + ограничен fetch на съответните children.

## Микросесия 4: batching/bulk (20–30 мин)
1. Защо `Statistics.getPrepareStatementCount()` НЕ е директно броят JDBC batches?
2. Какво наблюдава `SessionEventListener.jdbcExecuteBatchStart()`?
3. Защо `flush/clear` на всеки 10 реда намалява managed graph-а, но може да е проблем при незавършен aggregate?
4. Защо JPQL bulk UPDATE оставя managed entity stale?

## Микросесия 5: Spring Data JPA (20–30 мин)
1. Кога `findById` е по-добър от `getReferenceById`, въпреки допълнителния SELECT?
2. Как `Page` и `Slice` се различават по семантика, SQL cost и UX?
3. Защо не премахваме `save()` навсякъде механично?

## Тестове

~~~bash
mvn -pl persistence/hibernate-performance test
mvn -pl persistence/hibernate-performance -Ppostgres-integration verify
mvn -pl persistence/specification-querydsl test
~~~

Всички готови отговори се проверяват чрез [HibernatePerformanceTest](src/test/java/bg/hristomanov/education/hperf/HibernatePerformanceTest.java) и [PostgresHibernatePerformanceIT](src/test/java/bg/hristomanov/education/hperf/PostgresHibernatePerformanceIT.java). Готов CI ≠ усвоена концепция. За самостоятелно учене използвай [VM-11](docs/VM-11-IDENTIFIERS.md) → [VM-14](docs/VM-14-BATCH-BULK.md) в този ред.
