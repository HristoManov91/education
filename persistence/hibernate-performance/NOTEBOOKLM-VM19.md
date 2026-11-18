# NotebookLM — Vlad Mihalcea VM-19 Hibernate caching

## Импортирай

- [VM-19 глава](docs/VM-19-HIBERNATE-CACHE.md).
- [CachedCatalogItem](src/main/java/bg/hristomanov/education/hperf/domain/CachedCatalogItem.java) и [четирите JCache теста](src/test/java/bg/hristomanov/education/hperf/HibernateCacheLabTest.java).
- [Vlad — Second-level cache](https://vladmihalcea.com/jpa-hibernate-second-level-cache/), [Query cache](https://vladmihalcea.com/how-does-hibernate-query-cache-work/).
- [Hibernate 7.4](https://docs.hibernate.org/orm/7.4/userguide/html_single/#caching).

## Audio Overview prompt

> Създай задълбочено аудио обучение **на български** за Mid+/Early Senior Java backend developer. Начален production incident: direct JDBC UPDATE променя cached product price от 20 на 99, но приложението връща старата 20.
>
> Обясни L1 Persistence Context Identity Map срещу L2 SessionFactory cache и opt-in query cache + update timestamp region. Сравни READ_ONLY, READ_WRITE, NONSTRICT_READ_WRITE, TRANSACTIONAL и OFF със схема проблем → bad/good → когато/когато НЕ → trade-offs → тест.
>
> Използвай точно четирите H2/JCache теста: нови sessions + L2 hit, versioned ORM update, query cache invalidation, out-of-band JDBC stale data + explicit eviction. Подчертавай, че този test-only single-process provider не доказва multi-node consistency или production speedup. Не представяй Spring Cache annotations като Hibernate entity cache. Завърши с 5 конкретни code-review въпроса.
