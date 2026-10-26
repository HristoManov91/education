---
name: ci-test-diagnostics
description: Read-only diagnostics of Maven CI slowness, Spring TestContext cache fragmentation and flaky tests.
---

# Procedure

1. Capture SHA, workflow/job URL, actual timings, retries, failures and skipped tests.
2. Compare Surefire/Failsafe phases, fork policies and active test suites.
3. Collect Spring TestContext cache logs (optional temporary DEBUG).
4. Check different @MockitoBean, @TestPropertySource, @ActiveProfiles, @DynamicPropertySource and @DirtiesContext.
5. Review Testcontainers count, DB pool pressure, unique business keys and data isolation.
6. Check invalid parallel assumptions: global findAll sizes, fixed keys/ports, shared cleanup.
7. Suggest one targeted modification and a before/after measurement plan.
8. Never disable essential integration coverage solely to improve a benchmark.

Reference: ../../02-CONTEXT-CACHING-CI.md.
