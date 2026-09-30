# Architecture — canonical knowledge example

Този файл показва как deep project knowledge стои в `docs/`, а не се copy/paste-ва в `AGENTS.md`.

## Boundaries

```text
frontend
   ↓ HTTP
backend application layer
   ↓
domain
   ↓
persistence
```

Backend е authoritative за business calculations.

Frontend визуализира резултати и управлява interaction state, но не поддържа паралелна business formula.

## Change rule

Когато API contract се промени, inspect-ни:

```text
controller/DTO
→ service/domain
→ persistence if needed
→ API consumers
→ tests
→ docs
```

Това е architecture knowledge. Root `AGENTS.md` трябва само да казва, че този документ е canonical source.