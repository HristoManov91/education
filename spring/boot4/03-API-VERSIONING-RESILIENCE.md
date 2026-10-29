# P1 — HTTP API Versioning, Retry и Concurrency Limits

## 1. Казус

Стар клиент очаква `/dogs` във V1 форма; новият изисква V2 поле. Отделен downstream read-only каталог периодично връща временна грешка. При 100 конкурентни потребителя той може да се претовари.

Има **три различни проблема**: contract evolution, transient error handling и downstream capacity. Не ги решаваме с една annotation.

## 2. Decision cards

| Техника | Решава | Кога | Кога не | Печелим | Губим |
| --- | --- | --- | --- | --- | --- |
| `@GetMapping(version=...)` | несъвместими API версии | външни клиенти трябва да мигрират постепенно | без breaking contract change | паралелни договори | повече тестове и lifecycle на deprecated версии |
| `@Retryable` | transient IO failure | read/idempotent operation + bounded attempts | payment/write без idempotency | transient recovery | latency/load amplification |
| `@ConcurrencyLimit` | едновременно изчерпване на ресурс | целевият downstream има известен cap | вместо timeout/deadline | лимит на in-flight requests | чакащи заявки и contention |
| Declarative HTTP interface | Java interface за HTTP client | много типизирани calls с обща политика | ако един `RestClient` е по-прост | по-малко transport boilerplate | настройки и implicit client behavior |

## 3. API Versioning

**Naive:** сменям response на `GET /dogs` на място и приемам, че всички JSON клиенти ще го прочетат. Това често минава при tolerant consumers, но може да счупи strict schema clients или код, който предполага точно определени полета.

**Good:** [DogCatalogController](src/main/java/bg/hristomanov/education/boot4/DogCatalogController.java) използва `@GetMapping(path="/dogs", version="1.0")` и `"2.0"`. [ApiVersionConfiguration](src/main/java/bg/hristomanov/education/boot4/ApiVersionConfiguration.java) избира `X-Dogs-Version`. Избираме **изрично задължителна версия**, за да няма неусетна промяна за стар клиент. Header/query/path/media-type са възможни варианти; политиката трябва да се запише в API contract, SDK и gateway.

```text
GET /dogs + X-Dogs-Version: 1.0 → {dogs: [{id, name}]}
GET /dogs + X-Dogs-Version: 2.0 → {dogs: [{id, name, description}]}
GET /dogs + X-Dogs-Version: 99.0 → HTTP 400
GET /dogs без header → HTTP 400
```

[ApiVersioningHttpTest](src/test/java/bg/hristomanov/education/boot4/ApiVersioningHttpTest.java) проверява точно тези четири случая; [boot4.http](http/boot4.http) е runnable в IntelliJ.

**Алтернатива:** настройка на default version може да облекчи миграция на стари клиенти, но скрива contract mismatch; baseline versions (`1.0+`) позволяват преизползване на еднакви handlers, когато семантиката е идентична.

## 4. Retry: важно разминаване между поколенията

`@Retryable` в Spring **Framework 7** е `org.springframework.resilience.annotation.Retryable`; не го смесвай с `org.springframework.retry.annotation.Retryable` от стария Spring Retry.

Framework 7: `maxRetries = 2` означава **до две повторения след първия опит**, общо до три извиквания. За сравнение, старият Spring Retry отчита началния опит в `maxAttempts`. Това е миграционен footgun (подвеждащо сходство) — провери semantics преди production migration.

В [ResilientCatalogClient](src/main/java/bg/hristomanov/education/boot4/ResilientCatalogClient.java):

```java
@Retryable(includes = TransientCatalogException.class, maxRetries = 2, delay = 1)
public String readSafely() {
    return dependency.read();
}
```

[FlakyCatalogDependency](src/main/java/bg/hristomanov/education/boot4/FlakyCatalogDependency.java) се проваля точно два пъти и успява на третия. [ResilienceBehaviorTest](src/test/java/bg/hristomanov/education/boot4/ResilienceBehaviorTest.java) проверява **exactly 3 physical invocations**.

**Не retry-ваме:** validation errors, постоянни 4xx, транзакции с irreversible side effects без idempotency key, timeout без бюджет и shared outage, където повторенията задълбочават кризата.

**Production next step:** exponential backoff, jitter, end-to-end deadline, bulkhead/circuit breaker и метрики. Тези теми вече са разработени в [Resilience4j lab](../reliability/fault-tolerance/README.md) и [production incident notes](../reliability/fault-tolerance/PRODUCTION-API-RESILIENCE-P1.md) — не ги копираме тук.

## 5. `@ConcurrencyLimit` — не е circuit breaker

[ConcurrencyProbe](src/main/java/bg/hristomanov/education/boot4/ConcurrencyProbe.java) има `@ConcurrencyLimit(2)`. Външният Spring-managed caller минава през proxy-то; тест с 4 Virtual Threads показва, че най-много 2 са едновременно вътре в метода.

Съзнателно не използваме `this.call(...)` — self-invocation заобикаля proxy interception. Този лимит **не** прави външната система по-бърза, **не** отменя HTTP timeout, **не** е distributed глобален лимит между много инстанции и **не** гарантира fairness.

Примерен end-to-end поток:

```text
HTTP request → version resolver → @GetMapping(version)
Read operation → Spring proxy(@Retryable) → fake downstream attempt 1/2/3 → result
Load test → Spring proxy(@ConcurrencyLimit(2)) → 2 allowed + remaining waiting → release
```

### BLOCK срещу REJECT — две различни стратегии

От Spring Framework **7.0.3** `@ConcurrencyLimit` предлага `policy`; по подразбиране е `ConcurrencyLimit.ThrottlePolicy.BLOCK`:

| Policy | При претоварване | Кога бих го избрал | Цена / риск |
| --- | --- | --- | --- |
| `BLOCK` | допълнителните callers **чакат**, докато се освободи слот | вътрешни short-lived задачи с контролирана входяща конкуренция | може да задържи Virtual Threads след клиентски timeout; липсва fairness гаранция |
| `REJECT` | извън лимита се хвърля `InvocationRejectedException` (подтип на `RejectedExecutionException`) | HTTP edge или downstream, където fail-fast и 429/503 е по-добро от безкрайно изчакване | caller трябва да обработи отказа; retry без backoff може да влоши претоварването |

**Работеща проверка:** [ConcurrencyProbe.callRejecting](src/main/java/bg/hristomanov/education/boot4/ConcurrencyProbe.java) + [ResilienceBehaviorTest.rejectPolicyFailsFastRatherThanQueueingAnExcessCaller](src/test/java/bg/hristomanov/education/boot4/ResilienceBehaviorTest.java). Първите две задачи държат permits; трета получава незабавен отказ, а не стои на опашка. Тестът за `BLOCK` доказва обратното — всички четири задачи в крайна сметка завършват, но пикът остава две едновременни.

Това е **per-method / per-Spring bean** throttle; ако имаш N инстанции с лимит 2, общата конкуренция може да достигне приблизително 2×N. За глобални ограничения използвай отделен distributed rate/concurrency control.

## 6. Declarative HTTP clients — границата на нашия лабораторен обхват

Лекцията показва декларативен клиент, базиран на `@HttpExchange`/`@GetExchange`. В Spring Boot 4 клиентските интерфейси могат да се регистрират с `@ImportHttpServices`; конкретният транспорт/timeout/observability се конфигурира отделно.

**Илюстрация (не е част от изпълнимия lab и не претендира за тестван client wiring):**

```java
@HttpExchange("/catalog")
interface CatalogHttpClient {
    @GetExchange("/{id}")
    CatalogDto find(@PathVariable long id);
}
```

**Кога:** няколко стабилни HTTP операции, ясно типизиран договор. **Кога не:** един случаен call или динамично маршрутизиране, при което `RestClient` остава по-прозрачен. **Печалба:** по-малко boilerplate. **Загуба:** config/proxy indirection и нужда от contract tests. Следващ изпълним lab трябва да покрие MockWebServer, read/connect timeout и status mapping, преди да се твърди, че клиентската част е завършена.

## 7. Security — SSRF

Ако client-ът получава URL от непроверен потребителски вход, дори хубав declarative API не предотвратява **SSRF** (Server-Side Request Forgery — насочване на сървъра към нежелани адреси). Използвай фиксирана trusted base URL, allowlist на destinations, защита от redirect към private IP ranges и egress policies. Точните controls зависят от deployment мрежата.

## 8. Упражнения и тестова стратегия

- Развали V1 contract, като добавиш `description` в V1, и виж кой regression test улавя това.
- Промени `maxRetries` от 2 на 1; тестът вече трябва да показва, че third-attempt success не е възможен.
- Премахни `@ConcurrencyLimit`, пусни concurrency proof и виж peak >2.
- Промени `REJECT` на `BLOCK` и обясни защо negative test за отказ вече не е валиден и може да увисне без timeout.
- [ ] Идемпотентна ли е операцията? Има ли общ deadline?
- [ ] Отчитаме ли **първоначален + повторни** опити?
- [ ] Наистина ли Spring proxy intercept-ва external invocation?
- [ ] Ограничението per bean ли е или се нуждаем от shared/distributed control?
- [ ] API migration има ли V1/V2/missing/unsupported тестове?

## 9. Какво решихме

Имаме едновременно стар и нов API, детерминиран retry success след две грешки и доказано ограничена конкуренция. **Не сме решили:** circuit breaker, глобален rate limiter, exactly-once writes, HTTP client integration тестовете или автоматично премахване на V1. Това са отделни инженерни решения.

## Първични източници
- [Bootiful Spring Boot 4 — Josh Long](https://www.youtube.com/watch?v=1PtdpItmzTo).
- [API versioning — Spring Framework](https://docs.spring.io/spring-framework/reference/7.0/web/webmvc/mvc-config/api-version.html).
- [Spring Boot HTTP services](https://docs.spring.io/spring-boot/reference/io/rest-client.html).
- [Spring Framework 7 Resilience (актуална референтна документация)](https://docs.spring.io/spring-framework/reference/7.0/core/resilience.html).

