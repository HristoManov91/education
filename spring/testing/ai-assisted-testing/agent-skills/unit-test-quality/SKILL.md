---
name: unit-test-quality
description: Generate deterministic, independent Java 25 unit tests that kill realistic business regressions.
---

# Procedure

1. Record exact business rule, units, threshold and missing/future inputs.
2. Use real class under test; mock only external collaborators.
3. Fix time via injected Clock; never use Thread.sleep for business rule tests.
4. Include negative and boundary cases and meaningful assertions (not mocks verifying mocks).
5. Use readable JUnit names and AssertJ diagnostics.
6. Propose a mutation that tests should catch (age < 18 changed to <= 18).
7. Run actual targeted tests and report evidence.
8. Never remove tests, skip cases or weaken assertions to force green.

Reference: ../../src/test/java/bg/hristomanov/education/aitesting/domain/RegistrationPolicyTest.java.
