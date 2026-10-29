# NotebookLM — Bootiful Spring Boot 4 (Devoxx 2026)

## Източници за качване

- [Оригинално видео](https://www.youtube.com/watch?v=1PtdpItmzTo) — Josh Long, „Bootiful Spring Boot 4“, Devoxx Belgium 2026.
- [Spring Boot 4 migration guide](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide).
- [Framework 7 resilience](https://spring.io/blog/2025/09/09/core-spring-resilience-features/).
- [Spring Boot JDBC docs](https://docs.spring.io/spring-boot/reference/data/sql.html).
- [Spring Framework API versioning](https://docs.spring.io/spring-framework/reference/7.0/web/webmvc/mvc-config/api-version.html).
- [Spring gRPC](https://docs.spring.io/spring-grpc/reference/getting-started.html).

Не качвай една и съща голяма документация в много notebooks, ако може да бъде споделена тематично. Провери транскрипта спрямо официалните docs: автоматично извлечените `maxAttempts`, version или method names могат да бъдат неточни.

## Prompt A — основен P1 епизод (30–40 мин.)

> Подготви техническа аудио лекция на БЪЛГАРСКИ за опитен Java/Spring backend разработчик: „Bootiful Spring Boot 4: не нови annotations, а инженерни решения“. За всяка от темите Spring Boot 3.5→4.1 migration, API versioning, lazy JDBC acquisition, Spring Framework 7 retry и concurrency limit, използвай единен шаблон: (1) какъв production проблем решава, (2) защо naive вариантът изглежда разумен, (3) root cause, (4) кога се използва, (5) кога НЕ, (6) какво печелим, (7) какво губим и оперативни trade-offs, (8) примерен Java код и проверим тест, (9) сигнал при code review, (10) mental model. Не смесвай Java 27 демото със Spring Boot минимални Java изисквания; разграничи `maxAttempts` в Framework 7 от стария Spring Retry.

## Prompt B — Transaction Boundary incident review (20–30 мин.)

> Представи production incident: 200 Virtual Threads, Hikari pool 10, `@Transactional` върху method с бавен HTTP call. Обясни два последователни случая: network wait ПРЕДИ SQL и network wait СЛЕД SQL. Кога `connection-fetch=lazy` помага и кога не? Сравни с къса транзакция, тест с CountDownLatch и метрика active connections. Накрая предложи checklist за migration review и две погрешни твърдения, които ученикът трябва да опровергае.

## Prompt C — API/Resilience decision panel (20–30 мин.)

> Двама Senior инженери обсъждат API versioning чрез header и `@Retryable`/`@ConcurrencyLimit`. Единият се опитва да добави retry на неидемпотентен write; другият настоява за idempotency key, ограничени attempts, deadline и telemetry. Изяснете какви гаранции има Spring AOP proxy и какво се губи при self-invocation. Сравнете с Resilience4j и пояснете, че concurrency limit не е circuit breaker, rate limit или backpressure.

## Prompt D — P2 architectural decisions (25–35 мин.)

> Сравни REST/OpenAPI vs gRPC/Protobuf, Quartz JDBC clustering vs JobRunr, `@Component`/`@Bean` vs BeanRegistrar, и стандартен Java startup vs Project Leyden AOT. За всяка двойка: конкретен бизнес проблем, prerequisites, подходящи/неподходящи случаи, operational complexity, версия/лиценз, как бихме измерили, какъв интеграционен тест е задължителен. Не представяй демонстрационни бенчмаркове като универсални гаранции и не твърди, че в Education вече има runnable gRPC/JobRunr/Grafana modules.

## Самопроверка след изслушване

1. Защо Virtual Threads могат да увеличат pool saturation?
2. Кога lazy fetching не скъсява задържането на JDBC connection?
3. Какво означава `maxAttempts = 2` във Framework 7?
4. Защо `@ConcurrencyLimit` не е global rate limiter?
5. Кои тестове доказват, че V1 JSON contract е стабилен?
6. Защо не бих сменил Quartz с JobRunr само след конференционно demo?
7. Какъв baseline е необходим, преди да обещаем 30% startup improvement?

