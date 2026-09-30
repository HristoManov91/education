# Frontend scoped instructions

These instructions refine the root `AGENTS.md` for work under `frontend/`.

## State ownership

- Reuse existing shared state owners before introducing another store.
- Backend business results remain authoritative.
- Prevent stale async responses from overwriting newer state.

## UX

- Preserve loading, empty, error and retry states.
- Player/user-facing text uses the localization system.
- Interactive changes remain keyboard accessible and focus-visible.

## Validation

- Add unit/component coverage for state behavior.
- Add browser coverage when route/focus/responsive integration changes.