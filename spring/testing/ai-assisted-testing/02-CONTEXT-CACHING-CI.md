# 1.2 — Spring TestContext Caching, Test Isolation & CI Performance

> **P1 Deep Dive** · Видео [1:00:12–1:16:45](https://www.youtube.com/watch?v=d6D8nef-32c&t=3612s), [1:58:35–2:11:04](https://www.youtube.com/watch?v=d6D8nef-32c&t=7115s).

## Production symptom — CI се забавя при добавяне на още integration tests

В repository с много Spring Boot integration tests всеки нов клас изглежда стартира цял ApplicationContext, Flyway, Hibernate, security и embedded server. Разработчиците чакат дълго, agent-ът продължава да прави commits без feedback, а flaky test проваля целия PR.

**Bad reaction:** повече тестови JVM forks, `@DirtiesContext` навсякъде, или изключване на най-скъпите integration tests.

**Good mental model:** Spring TestContext има **static process-local context cache**. Два теста с равен `MergedContextConfiguration` могат да използват **същия** Spring ApplicationContext.

```text
Test A (profile=test, mocks=M1) ─┐
                                 ├── cache KEY A → ApplicationContext A
Test B (same configuration) ─────┘
Test C (extra @MockitoBean M2) ───── cache KEY B → new ApplicationContext B
Test D (@ActiveProfiles("other")) ── cache KEY C → new ApplicationContext C
```

Не е кеширане на JUnit test result; споделяме **жив Spring context и bean instances**, което е и полза, и **risk за shared mutable state**.

## 1. От какво се състои cache key?

Официалният Spring TestContext `MergedContextConfiguration` включва:

- configuration classes/locations, context loader, hierarchy;
- `@ActiveProfiles`;
- `@TestPropertySource` и inline properties;
- context initializers, customizers, включително `@MockitoBean`, `@MockitoSpyBean`, `@DynamicPropertySource`;
- web resource base path.

**Следствие:** „удобен mock за този един тест“ или различни profile flags може неволно да създаде допълнителен контекст и по-бавен suite. Но **не унифицирай контекстите с цената на неправилен тест** — correctness е първа.

### Реален executable proof

[ContextCacheIdentityTest.java](src/test/java/bg/hristomanov/education/aitesting/context/ContextCacheIdentityTest.java) създава TestContext за две различни test classes с идентичен config и за трети клас с различно `@TestPropertySource`.

```text
Context for FirstConsumer  === Context for SecondConsumer
Context for FirstConsumer  !== Context for DifferentPropertiesConsumer
```

Това **доказва behavior**, но не измерва време. За истински performance claim използвай повторени timing runs и compare metrics.

## 2. Default cache size, LRU и процесната граница

Default context cache maximum = **32** (версията на Spring Framework може да се промени; проверявай документацията за своя runtime). Когато лимитът се достигне, има LRU eviction/close. Размерът може да се настрои чрез JVM property `spring.test.context.cache.maxSize`; по-голям кеш заема и повече памет.

**Forked JVMs имат отделни static caches.** Ако Maven Surefire стартира отделна JVM за всеки клас, Spring не може да споделя контекстите между процесите. Един и същ repository с няколко отделни Maven modules също **не** получава глобален общ static cache по магия.

Spring Framework 7.x допълнително може да **pause/restart** неактивни test contexts: това променя lifecycle на background runners и трябва да се провери при `@Scheduled`, consumer threads и подобни fixtures; не пренасяй предположенията от Spring 6.

## 3. Защо @DirtiesContext е скъпо

`@DirtiesContext` изхвърля context от cache, така че следващ test със същата configuration трябва да го стартира наново. Това е полезно **само ако контекстът реално е корумпиран или мутирал по начин, който не може безопасно да бъде възстановен**.

**Bad practice:** използваме `@DirtiesContext`, за да „чистим данни в DB“. По-добре управлявай **test data** (transaction boundaries, уникални business IDs, rollback/cleanup на fixture) без да изхвърляш целия ApplicationContext.

## 4. Maven Surefire/Failsafe и parallelization

Нашият [pom.xml](pom.xml) отделя:

```text
validate → compile → test-compile
→ test (Surefire: *Test)
→ package → integration-test (Failsafe: *IT)
→ verify (Failsafe reports failures)
```

**Ползи:** бързото unit/slice feedback пристига рано и integration тестовете могат да имат различна инфраструктурна/parallelization policy.

Паралелизацията не е автоматично ускорение:

- Surefire forks → separate JVM caches, higher RAM.
- JUnit parallel threads → shared static caches/state, трябва thread-safe fixtures.
- Embedded DB/real PostgreSQL → transactional/test data conflict.
- Multiple Spring contexts → отделни connection pools, които могат да изчерпят PostgreSQL max connections.
- Unstable global test keys `ID=42` → random collision; `findAll().hasSize(2)` е fragile при shared DB.
- При `@SpringBootTest(RANDOM_PORT)` client thread и server thread са различни; `@Transactional` върху test method **не rollback-ва автоматично** server request transaction.

Нашият HTTP test използва **уникален email** и проверява entity по **ID, върнат от API**, а не глобален count. Не изтрива всички записи след всеки тест (това може да наруши други паралелни сценарии).

## 5. Data isolation contract

Когато CI е flaky, запиши и докажи:

- who owns test data, transaction boundary и cleanup;
- кой test използва конкретен external port/topic/schema;
- уникални business keys и correlation IDs;
- дали тестовете могат да стартират в случаен ред;
- retry budget само за изчакване на eventual state, **не** за скриване на бизнес failure;
- реално наблюдаван target SLA за async completion (Awaitility with timeout).

**Не слагай Thread.sleep като заместител на доказателство.** Ако service/event consumer е async, poll-вай конкретния observable invariant с ограничен срок.

## 6. Testcontainers optimization — къде е капанът?

[Съществуващият ни Testcontainers lesson](../testcontainers/README.md) демонстрира реален PostgreSQL и Redis, Docker networking, ServiceConnection и fallback.

За performance анализ:
- pin image version към production-compatible DB;
- не стартирай отделен PostgreSQL container за всеки test method без причина;
- shared container може да помогне, **но** изисква изолирани schemas/IDs и надеждно cleanup;
- `withReuse(true)` е удобство за **локално dev** при изрично включване, но тестовете не трябва да разчитат на leftover state, особено в CI;
- броят стартирани контейнери, connection pools и schema migrations е измерим operational fact.

**Не променяме масово съществуващите тестове** само защото лекцията предлага оптимизации. Първо събираме данни.

## 7. Диагностичен runbook за реалния education CI

Използвай съществуващата GitHub Actions `Verify` pipeline:

1. Пусни същия commit поне два пъти в контролирана среда, запази Maven timestamps.
2. Групирай tests по модул, време на Spring context startup, DB/testcontainers startup и собствена test execution time.
3. Активирай DEBUG за `org.springframework.test.context.cache` **в диагностичен run**, не глобално за всеки CI (много шум).
4. Сравни cache hit/miss/context size, profiles, customizers и `@MockitoBean` разлики.
5. Виж има ли `@DirtiesContext`, ненужни `@SpringBootTest`, особено там, където MVC slice/Java unit е достатъчен.
6. Провери Surefire/Failsafe split, forks, тестова parallelization, реалната RAM/CPU.
7. Промени **една** настройка, измери CI няколко пъти, запиши преди/след.

**Не твърдим, че съществуващият education build е ускорен**, докато няма измерване върху неговата реална pipeline. Резултатът 26→12 минути в лекцията е **пример, съобщен от лектора за друг проект**, не доказана стойност за нашия repository.

## Self-check

- Защо добавяне на `@MockitoBean` може да увеличи броя Spring contexts?
- Защо forks могат да убият cache reuse?
- Защо `@DirtiesContext` не е default DB cleanup?
- Защо `@Transactional` на HTTP integration test не гарантира rollback на сървърния worker?
- Кои metrics трябва да запишеш преди да рекламираш CI improvement?

**Източници:** [Devoxx](https://www.youtube.com/watch?v=d6D8nef-32c), [Spring Framework Context Caching](https://docs.spring.io/spring-framework/reference/testing/testcontext-framework/ctx-management/caching.html), [Spring Framework 7 release notes](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-7.0-Release-Notes).
