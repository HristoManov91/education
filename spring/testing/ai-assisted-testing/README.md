# Spring Boot Testing in the AI Era — P1 Deep Technical Lecture

> **Tech Radar P1 / Testing & Reliability + AI-assisted Engineering**  
> **Java 25 · Spring Boot 4.1.1 · Maven · JUnit · MockMvc · Spring Security · PIT**
>
> **Първичен източник:** [Philip Riecks — Prompt It Right: Spring Boot Testing in the AI Era](https://www.youtube.com/watch?v=d6D8nef-32c), Devoxx Belgium 2026, публикувано на 06.10.2026 (запис ~2h33). Примерите са **наши**, адаптирани към education.

## Входен production-like казус — AI PR е зелен, но съдържа дефект

AI агент пише endpoint за регистрация: 18+ години, задължително съгласие, валиден email и роля STAFF. Създава „юнит тест“, който извиква Controller като Java обект с mocked Service. Тестът е зелен, но Spring MVC binding/validation, SecurityFilterChain, JSON mapping и истински HTTP contract **не се изпълняват** при това извикване.

Ако AI напише и имплементацията, и тестовете според **едно и също погрешно допускане**, зелен тест не е доказателство за business correctness.

**Наша цел:** избираме най-евтиния тест, който реално доказва нужния бизнес или infrastructure invariant. Осигуряваме независим oracle, CI feedback, context reuse и production recovery.

## Учебна програма

| Модул | Учебен въпрос | Материал |
| --- | --- | --- |
| **1. Test Selection** | Кога JUnit, MVC slice, DB/Testcontainers, real HTTP? | [01 — Fundamentals](01-TEST-SELECTION.md) |
| **2. Context Caching & CI** | Защо mocks, profiles, forks и context lifecycle бавят CI? | [02 — Deep Dive](02-CONTEXT-CACHING-CI.md) |
| **3. Agentic Testing** | Как AI да пише/проверява смислени тестове, а не да гони зелени чекове? | [03 — AI Guardrails](03-AI-GUARDRAILS.md) |
| **4. Practical labs** | Java code, real MVC, context cache, HTTP security, Maven Failsafe, PIT | [04 — Labs](04-PRACTICAL-LABS.md) |
| **NotebookLM** | 3 специализирани аудио лекции | [Audio prompts](NOTEBOOKLM-PROMPTS.md) |

За процедурите, които AI agents могат да зареждат при нужда: [skills index](agent-skills/README.md).

## README → code → executable proof

| Концепция | Код | Тест / доказателство |
| --- | --- | --- |
| Age threshold, consent, fixed time | [RegistrationPolicy](src/main/java/bg/hristomanov/education/aitesting/domain/RegistrationPolicy.java) | [RegistrationPolicyTest](src/test/java/bg/hristomanov/education/aitesting/domain/RegistrationPolicyTest.java) |
| Real service state without Spring context | [CustomerService](src/main/java/bg/hristomanov/education/aitesting/service/CustomerService.java) | [CustomerServiceTest](src/test/java/bg/hristomanov/education/aitesting/service/CustomerServiceTest.java) |
| @Valid, MVC routing, JSON, 401/403 | [CustomerController](src/main/java/bg/hristomanov/education/aitesting/api/CustomerController.java) | [CustomerControllerTest](src/test/java/bg/hristomanov/education/aitesting/api/CustomerControllerTest.java) |
| Actual TestContext cache identity | Spring TestContext | [ContextCacheIdentityTest](src/test/java/bg/hristomanov/education/aitesting/context/ContextCacheIdentityTest.java) |
| Servlet threads, HTTP Basic, status/headers | [SecurityConfig](src/main/java/bg/hristomanov/education/aitesting/security/SecurityConfig.java) | [CustomerHttpIT](src/test/java/bg/hristomanov/education/aitesting/api/CustomerHttpIT.java) |
| Mutation quality | [Maven opt-in PIT profile](pom.xml) | RegistrationPolicyTest (PIT отчети само при реално изпълнение) |

## Run

```bash
# Unit, MVC slice, context cache (Surefire), БЕЗ *IT:
mvn -pl spring/testing/ai-assisted-testing test

# Всички тестове (Surefire + Failsafe):
mvn -pl spring/testing/ai-assisted-testing verify

# Целият education repository:
mvn --no-transfer-progress verify

# Отделен optional PIT mutation run:
mvn -pl spring/testing/ai-assisted-testing -Pmutation test pitest:mutationCoverage
```

**Важно:** За Maven Failsafe е необходима фаза `verify`, а не само `test`. PIT не е част от стандартната CI проверка; не твърдим измерен mutation score без изпълнението му.

## Testing levels — цена срещу доказателство

| Test | Spring Context | Реален HTTP | Доказва |
| --- | --- | --- | --- |
| JUnit pure Java | Не | Не | Business decisions / thresholds |
| @WebMvcTest | MVC slice | Не, MockMvc | Route, validation, authorization, JSON |
| @SpringBootTest(RANDOM_PORT) | Full Boot Context | Да | Embedded server, HTTP security & request/response |
| DB integration/Testcontainers | Persistence + DB process | Не непременно | SQL dialect, JPA, schema, transactions |

**Нюанс:** в Spring Boot 4 web MVC test support е отделен `spring-boot-starter-webmvc-test`, а `@MockitoBean` се намира в Spring Test. Copy/paste на Boot 3 imports не е коректен подход. <https://docs.spring.io/spring-boot/reference/testing/>

## Какво НЕ доказва този module

- Няма production DB: `CustomerService` използва **in-memory ConcurrentHashMap**. За PostgreSQL/Redis integration разгледай [Testcontainers lesson](../testcontainers/README.md).
- Security example използва HTTP Basic и CSRF disabled **само за тази демонстрация на stateless clients**. Не прилагай това на cookie/session-based web apps; истинско приложение изисква TLS, credential management и security review.
- Няма Keycloak/JWT, Kafka, browser/E2E, Toxiproxy или distributed failure tests.
- Context cache test измерва reuse по identity, **не бързина в секунди**. Performance твърдения се доказват с повторни CI timings.
- `@WithMockUser` е тестов SecurityContext за MockMvc; **не замества реален Authorization header** при RANDOM_PORT.

## Връзки със съществуващите уроци

- [Testcontainers & Docker](../testcontainers/README.md) — истинска инфраструктура, service connection и изолация.
- [AI-assisted Engineering](../../../ai/agentic-development/README.md) — agent instructions/skills; тук създаваме **specific testing guidance**.
- [Temporal Saga](../../messaging/saga/TEMPORAL-CANCELLATION-DEEP-DIVE.md) — real cancellation races, които изискват integration тестове.
- [Idempotency](../../reliability/idempotency/README.md) — retries, duplicate requests и DB uniqueness.
- [TEST-01–06 roadmap](../../../docs/learning/ROADMAP.md) — предпоставки, без автоматично отбелязване за усвоено знание.

## Финален инженерeн mental model

**AI може да делегира писането на код, но разработчикът носи отговорност за correctness и production risk.**

Test suite = **fast & trustworthy feedback**, не процент coverage. Освен нея са нужни добри deployments: feature flags, metrics, alerts, rollback и runbooks.

## Оригинални източници

1. [Philip Riecks, Devoxx — Prompt It Right: Spring Boot Testing in the AI Era](https://www.youtube.com/watch?v=d6D8nef-32c)
2. [Spring Boot 4.1.1 — Testing](https://docs.spring.io/spring-boot/reference/testing/)
3. [Spring Framework — TestContext Caching](https://docs.spring.io/spring-framework/reference/testing/testcontext-framework/ctx-management/caching.html)
4. [Spring Boot — MVC test slices](https://docs.spring.io/spring-boot/reference/testing/spring-boot-applications.html)
5. [Maven Surefire/Failsafe](https://maven.apache.org/surefire/maven-failsafe-plugin/)
6. [PIT Mutation Testing](https://pitest.org/quickstart/maven/)
7. [Testcontainers Java](https://java.testcontainers.org/)
8. [Spring Security testing](https://docs.spring.io/spring-security/reference/servlet/test/index.html)
