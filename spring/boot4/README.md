# P1 — Bootiful Spring Boot 4: решения, компромиси и доказателства

> Devoxx Belgium 2026 · Josh Long · Java 25 (LTS) · Spring Boot **4.1.2** · Spring Framework 7 · изпълнима лаборатория + архитектурни разширения.

## 1. Реалният казус — не „какво е ново“, а „кога ми трябва“

Поддържаме Java/Spring бекенд с REST клиенти, база данни и периодични интеграции. Стар мобилен клиент очаква стария JSON договор; новият иска допълнително поле. При внезапно натоварване Virtual Threads позволяват повече входящи заявки, но Hikari pool-ът свършва. Външна read-only услуга понякога връща временна грешка. Екипът предлага „да мигрираме на Spring Boot 4, да сложим retry на всичко и да увеличим броя нишки“.

**Това НЕ е решение.** То смесва четири различни въпроса: API съвместимост, физически ресурси, отказоустойчивост и безопасна миграция.

```text
Client (old/new) ─► Spring MVC (versioned endpoint)
                            │
                            ├─► @Transactional ─► Hikari pool ─► DB
                            │        └─► бавно I/O? [риск]
                            └─► @Retryable + @ConcurrencyLimit ─► downstream
```

**Цел:** да можем да защитим всяка технология с причинно-следствена аргументация, да покажем кога е полезна, кога вреди и с какъв тест доказваме поведението.

## 2. Карта на лекцията и граница между източник и допълнение

| Приблизително време | Демонстрация от Josh Long | Нашата надстройка |
| --- | --- | --- |
| 08–12 мин | Java 27, Spring Initializr, бързи security updates | проверка на runtime/version и стратегия за upgrade |
| 13–16 мин | Virtual Threads, `spring.datasource.connection-fetch=lazy` | transaction boundary/connection-pool proof с Hikari |
| 17–23 мин | Spring MVC API versioning, HTTP clients | стар/нов JSON контракт, negative HTTP tests |
| 23–27 мин | `@Retryable`, `@ConcurrencyLimit`, SSRF | точен retry budget, proxy boundary, resource cap |
| 27–35 мин | Observability, Spring gRPC и Protobuf | кога gRPC е по-добър от REST + contract tests за бъдещ lab |
| 35–46 мин | JobRunr, `BeanRegistrar` | Quartz vs JobRunr и ownership на фонови задачи |
| 47–49 мин | Project Leyden / AOT cache | честно benchmark сравнение без обещание за универсален speed-up |

Видеото **не е migration tutorial**, не показва изчерпателни тестове и не доказва, че всяка демонстрирана функционалност е универсално полезна. Следващите глави са наша практическа надстройка върху източника и официалните спецификации.

## 3. Задължителна decision matrix

| Подход | Какъв проблем решава | Кога го ползваме | Кога НЕ го ползваме | Печелим | Губим / риск |
| --- | --- | --- | --- | --- | --- |
| Boot 4 migration | legacy baseline, нови APIs и поддръжка | когато зависимости/тестове са готови | без inventory и rollback | актуални библиотеки, поддръжка | breaking changes, миграционно време |
| API versioning | стар и нов HTTP contract | при реална клиентска несъвместимост | за всяко малко добавено поле | предвидима еволюция | паралелна поддръжка, тестова матрица |
| Lazy JDBC connection | рано заемане на ограничена DB връзка | при transaction преди първия SQL | като оправдание за дълги транзакции | по-малко излишно задържане преди SQL | допълнителна конфигурация; връзката остава заета след SQL |
| Virtual Threads | скъпо thread-per-request I/O чакане | при много блокиращи I/O операции | за CPU-bound ускоряване | по-евтини concurrent задачи | риск от претоварване на DB/remote pool |
| `@Retryable` | временни грешки | за безопасни, ограничени и измерими повторения | за неидемпотентни записи без защита | повече успешни transient заявки | load amplification, допълнителна latency |
| `@ConcurrencyLimit` | едновременно претоварване на ограничен ресурс | когато знаем лимит/капацитет | вместо timeout или backpressure на цялата система | предвидим максимум на изпълненията | чакане, contention, нужда от deadline |
| gRPC/Protobuf | типизиран binary RPC договор | вътрешни service-to-service APIs и streaming | ако прост публичен HTTP/JSON е достатъчен | schema-first, генерирани клиенти | по-сложна debugging и прокси инфраструктура |
| JobRunr | durable background work | задачи, преживяващи рестартиране | за елементарен локален timer | persistence, retries/dashboard (според edition) | DB/infra/лицензни особености |
| BeanRegistrar | условна/програмна bean регистрация | при динамичен брой beans | за 1–2 обикновени класа | гъвкав DI composition | по-скрита конфигурация |
| Leyden AOT cache | част от startup overhead | след измерен startup bottleneck | без сравнение на startup и deployment constraints | потенциално по-бърз startup | build/training сложност и cache invalidation |

**Не копирай избор от видеото директно в production.** Първо назови ограничението, което оптимизираш.

## 4. README → код → доказателство

| Казус | Production-like код | Доказателство |
| --- | --- | --- |
| Съвместими V1/V2 договори | [DogCatalogController](src/main/java/bg/hristomanov/education/boot4/DogCatalogController.java), [ApiVersionConfiguration](src/main/java/bg/hristomanov/education/boot4/ApiVersionConfiguration.java) | [ApiVersioningHttpTest](src/test/java/bg/hristomanov/education/boot4/ApiVersioningHttpTest.java) |
| Retry и точен budget | [ResilientCatalogClient](src/main/java/bg/hristomanov/education/boot4/ResilientCatalogClient.java), [fake downstream](src/main/java/bg/hristomanov/education/boot4/FlakyCatalogDependency.java) | [ResilienceBehaviorTest](src/test/java/bg/hristomanov/education/boot4/ResilienceBehaviorTest.java) |
| Ограничена конкуренция (BLOCK/REJECT) | [ConcurrencyProbe](src/main/java/bg/hristomanov/education/boot4/ConcurrencyProbe.java) | [ResilienceBehaviorTest](src/test/java/bg/hristomanov/education/boot4/ResilienceBehaviorTest.java) |
| Транзакция преди/след SQL | [TransactionBoundaryProbe](src/main/java/bg/hristomanov/education/boot4/TransactionBoundaryProbe.java), [Hikari config](src/main/resources/application.properties) | [LazyConnectionsTest](src/test/java/bg/hristomanov/education/boot4/LazyConnectionsTest.java) |
| Версии по HTTP | [IntelliJ HTTP client](http/boot4.http) | същите MVC contract tests |

Това са три реални **тестови сценария**, не случайни smoke tests. H2 е само за възпроизводимост; измерване с PostgreSQL/Oracle е отделна задача.

## 5. Как да стартираме

Изисквания: **JDK 25**, Maven, без Docker. Работи на Windows/macOS/Linux. Съзнателно използваме Java 25 вместо демонстрираната Java 27; не използваме preview APIs.

```bash
# От root на education:
mvn -pl spring/boot4 -am test

# Самостоятелно:
mvn -pl spring/boot4 spring-boot:run
curl -i -H "X-Dogs-Version: 1.0" http://localhost:8080/dogs
curl -i -H "X-Dogs-Version: 2.0" http://localhost:8080/dogs
```

Можеш да пуснеш и четирите заявки от [boot4.http](http/boot4.http) с IntelliJ Run. Примерни резултати:

<details>
<summary>V1, V2 и unsupported version</summary>

```json
{"dogs":[{"id":1,"name":"Rex"}]}
{"dogs":[{"id":1,"name":"Rex","description":"Rescued dog"}]}
```

За `99.0` и липсващ header очаквай HTTP 400; тялото на грешката зависи от web конфигурацията.

</details>

`spring.datasource.connection-fetch=lazy` означава, че JDBC връзката се придобива при първо SQL statement. Не означава, че старата връзка автоматично се връща в pool-а между две SQL операции в една транзакция.

## 6. Подробни учебни глави (без дублиране)

1. [01 — Upgrade Spring Boot 3.5 → 4.1](01-MIGRATION-AND-COMPATIBILITY.md) — migration order, Jackson 3, modular starters, test infrastructure.
2. [02 — Virtual Threads, @Transactional и Hikari](02-TRANSACTIONS-VIRTUAL-THREADS.md) — naive/препоръчана граница и изпълним proof.
3. [03 — API Versioning, Retry и Concurrency Limits](03-API-VERSIONING-RESILIENCE.md) — синхронизация на контракти, failure budgets и AOP.
4. [04 — gRPC, JobRunr/Quartz, BeanRegistrar](04-GRPC-JOBS-BEAN-REGISTRATION.md) — P2 архитектурни decision guides, **не** изпълними gRPC/JobRunr лаборатории.
5. [05 — Observability, SSRF, Leyden и performance](05-OBSERVABILITY-SECURITY-JVM.md) — измервания, сигурност и граници.
6. [NotebookLM — промптове и упражнения](NOTEBOOKLM-PROMPTS.md).

Където вече имаме работа, вместо да копираме: [Loom vs Reactive](../concurrency/loom-vs-reactive/README.md), [Resilience4j](../reliability/fault-tolerance/README.md), [Security](../security/bootiful-security/README.md), [JFR](../../java/jvm/jfr-troubleshooting/README.md), [Testcontainers](../testing/testcontainers/README.md).

## 7. Край на историята — какво решихме

**Преди:** един JSON договор се променяше за всички; виртуалните нишки скриваха дефицита на JDBC връзки; retry се добавяше без бюджет.

**След:** два проверени договора; тест, който показва кога Hikari заема физическа връзка; retry със строго две допълнителни опита; лимит от две едновременни изпълнения.

**Какво НЕ решихме:** добри SQL индекси, end-to-end deadlines, retry на неидемпотентни write операции, реален high-load benchmark, schema evolution за gRPC или гаранция за exactly-once background job.

## 8. Code-review checklist

- [ ] Какъв конкретен production симптом обяснява тази нова технология?
- [ ] Кой е owner-ът на transaction / request / background job lifecycle?
- [ ] Какво става при failure, timeout и повторна доставка?
- [ ] Какво точно ограничаваме: threads, pool, downstream attempts или DB connections?
- [ ] Има ли сравнение с по-простия вариант и negative test?
- [ ] Има ли версия на dependency/API, подкрепена с официална документация?
- [ ] Назовани ли са cost, lock-in, operational burden и rollback?
- [ ] Показваме ли поне един случай, в който **няма** да използваме подхода?

## 9. Оригинални източници

- [Josh Long — Bootiful Spring Boot 4 (Devoxx, YouTube)](https://www.youtube.com/watch?v=1PtdpItmzTo) — използван е и потребителският транскрипт; при неточност официалните docs имат приоритет.
- [Spring Boot 4.1.2 — release announcement](https://spring.io/blog/2026/10/23/spring-boot-4-1-2-available-now/).
- [Spring Boot 4.0 Migration Guide](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide).
- [Spring Boot 4.1 Release Notes](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.1-Release-Notes).
- [Spring Framework 7 API versioning](https://docs.spring.io/spring-framework/reference/7.0/web/webmvc/mvc-config/api-version.html).
- [Core Spring Resilience Features](https://spring.io/blog/2025/09/09/core-spring-resilience-features/).
- [Spring Boot JDBC lazy fetch](https://docs.spring.io/spring-boot/reference/data/sql.html#data.sql.datasource.connection-fetch).

