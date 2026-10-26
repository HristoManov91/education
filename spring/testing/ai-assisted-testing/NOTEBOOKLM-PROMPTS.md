# NotebookLM — Spring Boot Testing in the AI Era (P1)

**Main source:** [Philip Riecks, Devoxx Belgium 2026](https://www.youtube.com/watch?v=d6D8nef-32c). Upload this entire video and the files [README](README.md), [Test Selection](01-TEST-SELECTION.md), [Context Caching](02-CONTEXT-CACHING-CI.md), [Agentic Testing](03-AI-GUARDRAILS.md), [Labs](04-PRACTICAL-LABS.md). Supplement with [Spring Boot 4 testing](https://docs.spring.io/spring-boot/reference/testing/) and [Spring TestContext caching](https://docs.spring.io/spring-framework/reference/testing/testcontext-framework/ctx-management/caching.html).

Make **three separate Bulgarian technical audio lectures**. Do not shorten important causal explanations. Keep technical terms in English but explain them in Bulgarian. Do not dictate long Java code verbatim; explain execution flow, framework responsibilities, and failure boundaries.

## Audio A — Spring Boot testing fundamentals (30–40 minutes)

```text
Create a deep, precise Bulgarian lecture for an experienced Mid+ to early Senior Java 25 / Spring Boot 4.1 developer.

Start with the concrete flaw: AI writes a registration controller and a Java unit test calling the controller directly. The test is green, but Spring HTTP binding, validation, security and JSON serialization are not checked. Explain why this happens, with a bad-good-proof comparison.

Cover choosing the cheapest test that proves behavior: plain JUnit/Mockito/AssertJ; MVC slice with @WebMvcTest and @MockitoBean; full @SpringBootTest with MOCK and RANDOM_PORT; JPA/Data/Jackson/RestClient slices and Testcontainers for real DB SQL. Cover Basic vs @WithMockUser, thread-local security versus real HTTP Authorization headers. Emphasize new modular Spring Boot 4 test starters and correct imports.

Walk through exact-age-18 and 17-year-364-days tests using an injected Clock, email validation, 201/400/401/403, Location and JSON fields, and the actual customer HTTP test. Explain how a happy-path-only test misses boundary regressions.

Explain Maven Surefire versus Failsafe, why mvn test differs from mvn verify and what should be shown by CI. End with five technical Q&A prompts, pausing before answers.
```

## Audio B — TestContext caching and fast deterministic CI (30–40 minutes)

```text
Create a deep Bulgarian technical lecture centered on Spring TestContext caching. Start with a 30-minute CI build and frequent unnecessary Spring ApplicationContext startups. Explain MergedContextConfiguration as cache key: profiles, properties, MockitoBean, DynamicPropertySource, initializers and other customizers. Explain equality, static JVM cache, default size 32 with LRU, @DirtiesContext, forks and Spring 7 context pause.

Trace ContextCacheIdentityTest: identical config yields same ApplicationContext instance; different TestPropertySource gives a new instance. This verifies behavior, not measured speed. Explain how to measure actual build times, hit/miss stats, logging and cache-profiler evidence.

Compare JUnit thread parallelism versus Surefire forked JVMs. Include transaction isolation, random-port server thread, unique business keys, clean test data, not deleting all rows during parallel tests, condition-based awaits instead of Thread.sleep, Testcontainers singleton/reuse and database connection pool pressure.

Finish with an actionable CI diagnostic runbook for education, differentiating verified findings from unmeasured proposed optimizations.
```

## Audio C — AI-generated test quality, mutation testing & ownership (35–45 minutes)

```text
Create a deep Bulgarian P1 engineering lesson using the last half of the Devoxx video. Explain the bottleneck shift from coding to verification. Agent-generated code plus agent-generated tests can share the same wrong assumption, so green coverage alone does not guarantee business correctness.

Give explicit meaning of a test: deterministic, isolated, representative boundary, meaningful independent assertions, informative failures, negative/edge cases and parallel safety. Show how mocking the tested behavior creates a tautology. Demonstrate the age < 18 to <= 18 mutation and how the exact-boundary test catches it.

Teach PIT mutation testing, killed/survived mutants and why coverage percentage is not sufficient. Our PIT profile is optional and no mutation score is to be claimed without running it. Explain version-controlled Markdown skills for test selection, unit tests, quality review and CI diagnostics; link to agent-skills/README.md.

Give a safe agent workflow: requirements and oracle fixed first, test matrix, implementation, correct Spring test type, verification, CI logs, review, evidence. Do not allow an agent to weaken assertions or skip tests to force green.

Discuss test-first/TDD as a trade-off not a universal rule, current Spring Boot docs/MCP, read-only CI access, browser E2E where needed, feature flags, rollback, alerting, operational runbooks and human accountability.

Finish with 10 senior interview/code-review questions and a short production checklist. Do not hallucinate faster build timings, production infrastructure, or PIT results for this repo.
```

## How to use in education

Audio A → use TEST-01/03/04 knowledge checks. Audio B → optimize measured CI only after baseline evidence. Audio C → apply agent skills and perform independent PR/test review. These audios are **learning artifacts, not evidence of mastery**. Use [assessment protocol](../../../docs/learning/ASSESSMENT-PROTOCOL.md) after learning.
