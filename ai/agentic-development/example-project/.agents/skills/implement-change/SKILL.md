---
name: implement-change
description: Implement an already agreed repository task end to end, including inspection, code changes, focused validation and a reviewable delivery. Use when the task contract is implementation-ready; do not use for unresolved product design or independent PR review.
---

# Implement Change

## Preconditions

- A current task contract exists.
- Product decisions needed for implementation are resolved.

## Workflow

1. Read root and applicable scoped instructions.
2. Read the complete current task contract.
3. Inspect relevant production code, tests, docs and callers before editing.
4. Implement the smallest coherent scope that satisfies acceptance criteria.
5. Run focused validation, then applicable canonical validation.
6. Prepare a reviewable result; do not silently broaden scope.

## Evidence

Record:

- changed boundaries/files;
- exact validation that ran;
- failures/skips/limitations;
- remaining risks.

## Guardrails

- Do not invent missing requirements.
- Do not weaken tests to make the change pass.
- Do not describe unexecuted validation as successful.
- Do not self-approve an independent-review requirement.

Before finalizing, read [references/implementation-checklist.md](references/implementation-checklist.md).

Use [assets/result-template.md](assets/result-template.md) for the result structure.