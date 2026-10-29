# P1 — Spring Boot 3.5 → 4.1: миграция с доказателства

## Проблемът
Имаме работещ Spring Boot 3.x Java backend. Само смяната на версията в `pom.xml` може да доведе до компилационни грешки, различен JSON и неработещи тестови конфигурации. Счупеното не е непременно бизнес логиката — променени са **dependency graph** (граф на зависимостите), auto-configuration (автоматична конфигурация) и API contract.

Тази глава е наша **надстройка**, не стъпка-по-стъпка демонстрация от сцената.

## Decision card

| Въпрос | Отговор |
| --- | --- |
| Какво решава? | Отваря път за поддържан Spring стек, поправки по сигурността и нови Framework 7 API-та. |
| Кога? | След inventory на библиотеки, integration tests, environment compatibility и rollback план. |
| Кога не? | Ако липсва compatibility за critical driver, Spring Cloud release train или deployment platform. |
| Печалби | Поддръжка, актуални зависимости, по-ясни Boot 4 модулни starters. |
| Загуби | Миграционно време, retraining, промяна в сериализация, тестова инфраструктура и build. |
| Доказателство | Compile + integration + contract tests + staging regression; не само `Spring context starts`. |

## Препоръчан ред

1. Обнови първо до **последната 3.5.x maintenance версия**, отстрани deprecated API-та и пусни CI.
2. Опиши `mvn dependency:tree`, production JVM и third-party starters (включително Spring Cloud, JDBC driver, Jackson customization).
3. Проверете Java ≥17, Jakarta EE 11 / Servlet 6.1 и съвместимост с контейнера; в Education използваме Java 25 LTS.
4. Мигрирай един модул/поток в отделен PR, без успоредни feature промени.
5. Замени deprecated/старите starters с целеви; при временен migration bridge може да се ползва `spring-boot-starter-classic`, но не го оставяй като крайна архитектура.
6. Извърши JSON contract regression на реални DTO-та (dates, null values, enum names, unknown fields), плюс security, transaction и scheduler regression.
7. Deploy към тестова среда с наблюдение на HTTP 4xx/5xx, pool saturation, SQL и memory. Едва след това rollout с rollback plan.

## Breaking-change карта

| Старо / възможен проблем | Boot 4 подход | Какво проверяваме |
| --- | --- | --- |
| `spring-boot-starter-web` | `spring-boot-starter-webmvc` | MVC/Servlet runtime и тестове |
| Test starter + MockMvc implicit | `spring-boot-starter-webmvc-test` + `@AutoConfigureMockMvc` | TestContext/MockMvc injection |
| `spring-boot-starter-aop` | `spring-boot-starter-aspectj` | AOP/proxy behavior |
| JSON mapper в `com.fasterxml.jackson.databind` | Jackson 3 API в `tools.jackson`; exception: annotations остават `com.fasterxml.jackson.annotation` | JSON compatibility, import-и, custom modules |
| `spring-boot-starter-batch` и JDBC metadata | `spring-boot-starter-batch-jdbc` ако metadata трябва да е durable | Resume/restart semantics |
| Директна Flyway/Liquibase библиотека | dedicated Boot 4 starter, където е приложимо | auto-config, migrations |
| Spring Retry управляван чрез стария BOM | Framework 7 core retry или отделно pin-ната версия на Spring Retry | retry budget и различни annotation contracts |
| `@MockBean` / `@SpyBean` от Boot | `@MockitoBean` / `@MockitoSpyBean` от Spring TestContext | test overrides без скрит контекст |
| `org.springframework.lang.Nullable` | JSpecify `org.jspecify.annotations.Nullable` | nullness и Kotlin interoperability |

**Важно:** `spring-boot-properties-migrator` е временна помощ за диагностика на properties, не production dependency, която да остава постоянно.

## Пример за различен JSON contract

Преди: endpoint без version header променя response от `{"id":1,"name":"Rex"}` до версия с `description`. Дори добавянето на поле може да счупи строг JSON клиент, schema validation или кеш ключ. По-добрият вариант НЕ е винаги версия за всяка добавка; трябва да има доказана client-side несъвместимост.

В [DogCatalogController](src/main/java/bg/hristomanov/education/boot4/DogCatalogController.java) показваме две версии, а [ApiVersioningHttpTest](src/test/java/bg/hristomanov/education/boot4/ApiVersioningHttpTest.java) доказва, че V1 остава непроменена. Това е реален migration regression gate.

## Какво още може да се счупи под капака

- `@SpringBootTest` вече **не включва автоматично MockMvc** — добавяй `@AutoConfigureMockMvc` и test starter.
- Jackson 3 има отделни `JsonMapper` / `XmlMapper`; custom `ObjectMapper` в стар проект не означава непременно override на правилния mapper.
- За external servlet container провери Servlet 6.1 съвместимост.
- Spring Boot 4 изисква Java 17+, **не Java 27**. Java 27 е изборът на Josh Long за сценичната демонстрация.
- Framework 7 `@Retryable(maxRetries=2)` означава **до две повторения плюс първоначалния опит**, различно от старите Spring Retry очаквания.

## Кога не бих мигрирал точно сега

Ако интеграционният JDBC driver/протокол няма тествана съвместимост или съществуват непокрити бизнес-critical flows, първо намаляваме риска с contract tests и upgrade към поддържана 3.5.x. **Отлагане с критерии** е различно от безкрайно отлагане.

## Тестове, упражнения и решение

- [ApiVersioningHttpTest](src/test/java/bg/hristomanov/education/boot4/ApiVersioningHttpTest.java) — потребителският contract не се сменя неусетно.
- [ResilienceBehaviorTest](src/test/java/bg/hristomanov/education/boot4/ResilienceBehaviorTest.java) — AOP proxy и retry семантика след upgrade.
- [LazyConnectionsTest](src/test/java/bg/hristomanov/education/boot4/LazyConnectionsTest.java) — transaction semantics.
- Упражнение: мигрирай примерен `@MockBean` тест към `@MockitoBean` и провери `@AutoConfigureMockMvc` при `@SpringBootTest`.
- Упражнение: направи CSV списък на старите и новите starters и за всеки дай regression proof.

**Какво решихме:** вместо „сменям версията и се надявам“, имаме проверими migration gates: build, contracts, resources, security, rollback. **Ограничение:** настоящият малък lab не е доказателство за compatibility на произволен корпоративен stack.

## Review checklist

- [ ] 3.5.x baseline verified?
- [ ] Driver, Cloud, Jackson, security, Batch и servlet dependencies compatible?
- [ ] JSON contract regression включва strict clients?
- [ ] Test slice и MockMvc конфигурацията мигрирана?
- [ ] Security patches и rollback са планирани?

## Първични източници
- [Spring Boot 4 Migration Guide](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide).
- [Spring Boot 4.1.2 release](https://spring.io/blog/2026/10/23/spring-boot-4-1-2-available-now/).
- [Spring Boot 4.1 release notes](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.1-Release-Notes).
- [Оригиналната Devoxx лекция](https://www.youtube.com/watch?v=1PtdpItmzTo).

