# 1.1 — Choosing the cheapest test that proves the behavior

> **P1 Fundamentals** · Видеото [15:29–59:56](https://www.youtube.com/watch?v=d6D8nef-32c&t=929s).

## Входен проблем

`POST /api/customers` трябва да валидира JSON, да откаже malformed email с 400, да се извиква само от STAFF и да върне 201 с Location header.

**BAD:**

```java
var controller = new CustomerController(mock(CustomerService.class));
ResponseEntity<?> result = controller.register(request);
assertThat(result.getStatusCode().value()).isEqualTo(201);
```

Това доказва Java method invocation, но **не** Spring MVC routing, bean validation, serialization или SecurityFilterChain. AI може да промени `@PostMapping` и тестът пак да остане зелен.

**GOOD:** [CustomerControllerTest](src/test/java/bg/hristomanov/education/aitesting/api/CustomerControllerTest.java) упражнява `@WebMvcTest`, `@MockitoBean`, `MockMvc`, `@WithMockUser`. Тества route, HTTP 201, JSON, Location, HTTP 400, 401 и 403.

## Test decision tree

```text
Business rule without framework?          -> pure JUnit, fixed Clock
Spring MVC binding / JSON / security?      -> @WebMvcTest + MockMvc
JPA mapping / real SQL / transactions?     -> @DataJpaTest + production-like Testcontainers
Outbound HTTP client / error handling?    -> @RestClientTest + mock server
Full app wiring / embedded HTTP server?    -> @SpringBootTest(RANDOM_PORT)
Browser, cookies, external systems?        -> E2E / contract tests
```

**Не следвай dogmatic 80% unit tests / 20% integration tests.** Съставът зависи от реалните risks.

## Pure Java tests и mutation boundary

[RegistrationPolicy](src/main/java/bg/hristomanov/education/aitesting/domain/RegistrationPolicy.java) има ясна business граница:

```java
int age = Period.between(birthDate, LocalDate.now(clock)).getYears();
if (age < 18) return TOO_YOUNG;
```

Ако AI промени `< 18` на `<= 18`, happy-path тест с 30-годишен човек няма да го открие. Тест на **точно 18 г.** ще го открие. Затова използваме **boundary values, negatives, invariant assertions и fixed time**, не просто coverage.

**Mockito tip:** не mock-вай метод на обекта, чието поведение твърдиш, че тестваш. `when(policy.isEligible()).thenReturn(true)` + `assertTrue(...)` доказва Mockito stubbing, не бизнес логика.

## Boot 4 test starters

`spring-boot-starter-test` предоставя общите testing tools; за MVC slice в Boot 4 използвай и `spring-boot-starter-webmvc-test`. Новите imports са:

```java
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
```

За Spring Security tests добави `spring-boot-starter-security-test`. В стандартния MVC slice има истинска filter chain, но test-authentication може да се зададе чрез `@WithMockUser`.

## Real HTTP vs MockMvc

`@SpringBootTest(webEnvironment=RANDOM_PORT)` пуска embedded server на свободен port. Тестът прави реална TCP/HTTP заявка с `java.net.http.HttpClient`. Това е същинската граница, на която `@WithMockUser` **не** пренася magic thread-local identity към server worker-а — трябва реална test authentication.

Нашият [CustomerHttpIT](src/test/java/bg/hristomanov/education/aitesting/api/CustomerHttpIT.java) упражнява истински `Authorization: Basic ...`. Credentials съществуват **единствено** в тестов `@TestConfiguration`.

**MockMvc≠Tomcat benchmark:** не използвай `MockMvc`, за да твърдиш production throughput/connection handling. И двата начина могат да проверяват business outcome, но имат различна execution boundary.

## Maven Surefire vs Failsafe

- `*Test` → Surefire по време на `mvn test`;
- `*IT` → Failsafe `integration-test` + `verify` goals, изпълнявани при `mvn verify`.

Това е **съзнателна naming/build convention**, а не академична дефиниция, че Spring slice тестове не са integration по значение. Цел: бързо feedback first, истинските HTTP tests след това.

**Нюанс:** Maven Failsafe `integration-test` phase сам по себе си не трябва да е финалната команда; `verify` оценява резултатите, а и завършва lifecycle-а.

## Други инструменти

- `@JsonTest` — Jackson configuration и custom serializers;
- `@RestClientTest` — real client serialization/error decisions с mock HTTP;
- `@DataJpaTest` + real DB — SQL/JPA behavior, който Mockito не проверява;
- Awaitility — wait for **condition** със срок, а не `Thread.sleep` / необоснован retry.

**Оригинални източници:** [Devoxx](https://www.youtube.com/watch?v=d6D8nef-32c), [Spring Boot Test Slices](https://docs.spring.io/spring-boot/reference/testing/spring-boot-applications.html), [Maven Failsafe](https://maven.apache.org/surefire/maven-failsafe-plugin/).

## Self-check (Mid→Senior)

1. Защо controller unit тест не доказва Bean Validation?
2. Каква грешка може да пропусне MockMvc, но да открие RANDOM_PORT?
3. Защо `@WithMockUser` не удостоверява реален remote request?
4. Защо boundary test има по-голяма стойност от тест с произволна валидна възраст?
5. Кога `mvn test` е зелено, но `mvn verify` правилно е червено?
