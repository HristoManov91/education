# Backend scoped instructions

These instructions refine the root `AGENTS.md` for work under `backend/`.

## Ownership

- Backend owns business rules and authoritative state.
- Controllers stay thin; domain/application services own behavior.
- Do not expose persistence entities as public API contracts.

## Persistence

- Schema changes use migrations.
- Released migrations are append-only history; create a new migration for new behavior.
- Check constraints, indexes, transaction behavior and query cardinality deliberately.

## Correctness

- State-changing flows require explicit transaction/concurrency reasoning.
- Cross-instance correctness must not rely only on JVM-local locks/state.
- Add regression tests for changed behavior.