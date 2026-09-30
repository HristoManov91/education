# Example Project — agent instructions

Keep this file small and stable. It is a map and repository-wide contract, not a copy of all project documentation.

## Repository orientation

- `backend/`: authoritative business behavior and persistence.
- `frontend/`: client UI/state; do not duplicate backend business formulas.
- `docs/`: canonical architecture and domain documentation.
- `tasks/`: current feature/task contracts.

Read `docs/architecture.md` before a non-trivial cross-boundary change.

## Source of truth

When sources disagree, use this project order:

1. Latest explicit human decision for the current task.
2. Current task contract under `tasks/`.
3. Current code and tests.
4. Canonical docs under `docs/`.
5. Historical comments or old plans.

Do not silently invent missing product behavior.

## Before editing

1. Read relevant implementation and tests.
2. Search important callers/usages before changing public contracts.
3. Read the scoped `AGENTS.md` for each subtree you modify.
4. Prefer the smallest coherent change.

## Validation

Use the project's canonical test/build commands. A skipped or unexecuted check must never be reported as passing.

## Git workflow

- Work on a focused branch.
- Keep implementation changes reviewable.
- Implementation and independent review are separate responsibilities.
- Do not merge without the approval required by project policy.

## Reusable skills

Use `.agents/skills/implement-change` for an agreed implementation task. Skills refine workflow but never override the current task contract or source-of-truth order.