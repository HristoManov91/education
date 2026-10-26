---
name: test-quality-review
description: Read-only review of AI-generated test quality, shared-error risk and missing failure boundaries.
---

# Review checklist

1. Read ticket/AC independently of modified code.
2. Check all essential output/side-effect/security invariants.
3. Look for assertion-free, flaky, mock-only and copy-production-logic tests.
4. Check actual runtime boundaries: Spring MVC vs pure method, real HTTP vs @WithMockUser, Oracle/PostgreSQL vs H2.
5. Check deleted/disabled/relaxed assertions for explicit justification.
6. Review complete CI result on exact PR head SHA (PASS vs SKIP vs FAIL).
7. Consider mutation testing; distinguish proposed PIT run from actual score.
8. Return concise severity-ranked findings and reproductions; do not silently change code.

Reference: ../../03-AI-GUARDRAILS.md.
