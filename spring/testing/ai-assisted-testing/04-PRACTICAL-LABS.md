# 1.4 — Practical Java 25 / Spring Boot 4.1 Testing Labs

> **P1 / Hands-on: business contract → bad test → correct test → proof → trade-offs**

## Problem

AI generated code for user registration. We need to prove: a customer must be 18+, explicitly consent, pass email validation, and only STAFF may submit or read a registration. A unit test calling the Controller Java method cannot prove Spring MVC routing, JSON validation or authentication.

## Acceptance criteria

1. Registration on exact 18th birthday succeeds; one day earlier fails.
2. Underage customers with consent still fail; adult without consent fails.
3. Time-dependent policy uses injected Clock, not machine clock.
4. Controller POST returns 201 with Location header, UUID, email and timestamp.
5. Invalid email → HTTP 400.
6. Anonymous request → 401; VIEWER role → 403.
7. Real HTTP request authenticates through actual Basic credentials in a test-only configuration.
8. Spring TestContext with identical configuration is the **same object**; different test properties → new context.
9. Maven Surefire runs *Test and Failsafe runs *IT during verify.
10. Mutation testing is opt-in; a green JUnit run does **not** imply a measured PIT score.

## 1. Run

```bash
mvn -pl spring/testing/ai-assisted-testing test
mvn -pl spring/testing/ai-assisted-testing verify
mvn --no-transfer-progress verify
```

The first command runs pure unit tests, MockMvc slice and TestContext cache. The second also runs a real embedded HTTP integration test using Failsafe. No Docker is required for this module.

## 2. RegistrationPolicy — pure Java and boundaries

**Code:** [RegistrationPolicy](src/main/java/bg/hristomanov/education/aitesting/domain/RegistrationPolicy.java)  
**Proof:** [RegistrationPolicyTest](src/test/java/bg/hristomanov/education/aitesting/domain/RegistrationPolicyTest.java)

The fixed Clock is 2026-10-26. Tests exercise exact 18th birthday vs day-before, consent, future birth date and null date.

**Red/Green exercise:** change the implementation condition from age less than 18 to age less than or equal to 18. The exact-boundary test must fail. Restore the logic. Explain why a single test using a 30-year-old never caught the bug.

## 3. Service unit — real collaborator, no Spring

**Code:** [CustomerService](src/main/java/bg/hristomanov/education/aitesting/service/CustomerService.java)  
**Proof:** [CustomerServiceTest](src/test/java/bg/hristomanov/education/aitesting/service/CustomerServiceTest.java)

Construct service with real RegistrationPolicy and a fixed Clock. Check generated ID, stored data and exact Instant. No mocked business policy and no Spring context startup.

**Limitation:** ConcurrentHashMap fixture does not demonstrate real DB durability, transaction boundaries or SQL dialect. For this, reuse the [existing Testcontainers lab](../testcontainers/README.md).

## 4. MVC slice — where a pure unit test is insufficient

**Code:** [CustomerController](src/main/java/bg/hristomanov/education/aitesting/api/CustomerController.java)  
**Proof:** [CustomerControllerTest](src/test/java/bg/hristomanov/education/aitesting/api/CustomerControllerTest.java)

The test uses @WebMvcTest with @MockitoBean service, real MVC routing, Jackson, Jakarta Validation and SecurityFilterChain. Assertions cover 201, Location, JSON fields, 400, 401 and 403.

**Break-it exercise:** change @PostMapping route and see that pure service tests stay green while MVC slice fails.

**Trade-off:** Since service is mocked, MVC slice does not prove actual customer persistence.

## 5. Real HTTP integration

**Proof:** [CustomerHttpIT](src/test/java/bg/hristomanov/education/aitesting/api/CustomerHttpIT.java)

Spring Boot starts embedded servlet container on random port; Java HttpClient performs actual localhost HTTP requests. Anonymous → 401; authenticated VIEWER → 403; STAFF → 201 with Location; GET Location → 200; invalid email → 400.

Test credentials are declared only in the test class. @WithMockUser does **not** authenticate a separate HTTP client thread — a real Authorization header is required.

**Trade-off:** This is localhost integration, not Keycloak, gateway or browser E2E.

## 6. Context cache proof

**Proof:** [ContextCacheIdentityTest](src/test/java/bg/hristomanov/education/aitesting/context/ContextCacheIdentityTest.java)

Two test classes with same ContextConfiguration get identical ApplicationContext objects. A test with additional TestPropertySource gets another instance.

This demonstrates cache-key equality and context reuse, not an elapsed-time benchmark. Observe real timings and cache hit/miss metrics before declaring CI speed improvements.

## 7. Maven lifecycle

```text
mvn test   → Surefire (*Test)
mvn verify → Surefire (*Test) + Failsafe (*IT)
```

POM includes maven-failsafe-plugin explicitly. If it were missing, a *IT class could compile without ever running in CI. Inspect GitHub Actions class-level logs, not only its green badge.

## 8. PIT mutation quality (optional)

```bash
mvn -pl spring/testing/ai-assisted-testing -Pmutation test pitest:mutationCoverage
```

Maven profile targets RegistrationPolicy and RegistrationPolicyTest. HTML output is generated only after a successful run in target/pit-reports; this is **not** in normal CI.

Do not claim any mutation percentage without executing the command.

## 9. Senior exercises

**Exercise A:** AI proposes deleting exact-18 test to make code pass. Why is that an acceptance-oracle violation?

**Exercise B:** Two integration tests assert global findAll size while sharing a DB. Explain why parallel execution produces flaky results and how unique keys / isolation solve the problem.

**Exercise C:** A test class adds @MockitoBean or changes ActiveProfiles. Explain why this can create a different cached Spring context; when is the extra context necessary?

**Exercise D:** An integration test uses @Transactional in its JUnit thread and calls a RANDOM_PORT server. Explain why the server-side commit is not rolled back by the test transaction.

**Exercise E:** An AI-created controller test directly calls the Java method; identify all Spring concerns it fails to test.

## 10. Exit checklist

- [ ] Test level matches actual failure boundary.
- [ ] Positive, negative and boundary values covered.
- [ ] Independent business oracle; not tautological mocks.
- [ ] Clock, IDs and async waits deterministic.
- [ ] Surefire + Failsafe results both visible.
- [ ] Context reuse assessed without breaking isolation.
- [ ] Production-like DB gaps documented and cross-linked.
- [ ] No false mutation, performance or coverage claims.

**Mental model:** testing tools are selected by what they can prove, not by their annotation count or convenience.
