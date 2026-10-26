---
name: test-selection
description: Choose the cheapest Spring Boot 4.1 test that proves the observed business risk.
---

# Steps

1. Define test oracle independently of implementation.
2. Enumerate positive, negative, boundary, failure and security scenarios.
3. Identify failure boundary:
   - pure Java decisions: JUnit/AssertJ;
   - MVC binding/validation/security: @WebMvcTest + MockMvc/@MockitoBean;
   - SQL/JPA: @DataJpaTest + production-like Testcontainers DB;
   - outbound HTTP: @RestClientTest with mock server;
   - server/real authentication: @SpringBootTest(RANDOM_PORT) + actual HTTP client.
4. Document what the selected test does **not** prove.
5. Map *Test to Surefire (test) and *IT to Failsafe (verify).
6. Confirm Spring Boot 4 starters/packages from current documentation.

**Output:** AC → risk → test level → assertion → verification command.

Reference: ../../01-TEST-SELECTION.md.
