# Portable, version-controlled agent testing skills

These are **educational templates**: not automatically installed in any coding assistant and do not grant permissions.

| Skill | When to use |
| --- | --- |
| [test-selection](test-selection/SKILL.md) | Choose appropriate unit/slice/integration test |
| [unit-test-quality](unit-test-quality/SKILL.md) | Generate deterministic meaningful pure-Java tests |
| [test-quality-review](test-quality-review/SKILL.md) | Critically assess generated tests before PR |
| [ci-test-diagnostics](ci-test-diagnostics/SKILL.md) | Read-only analysis of slow/flaky Maven CI |

Consult existing [AI-assisted development](../../../../ai/agentic-development/README.md) and [vendor mapping](../../../../ai/agentic-development/VENDOR-MAPPING.md) for tool-specific placement.

**Guardrails:** Never assert PASS, mutation score or CI acceleration without evidence. Never weaken/remove acceptance tests to make green. Never mistake Mockito stubs for tested business behavior. Read-only CI is the default; use least privilege and protect secrets.
