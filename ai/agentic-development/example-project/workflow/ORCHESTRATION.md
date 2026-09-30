# Orchestration state model

Това е human-readable state machine. Не е vendor runtime config.

```text
NEW
 ↓
PLANNING
 ↓
READY_FOR_IMPLEMENTATION
 ↓
IMPLEMENTING
 ↓
DRAFT_REVIEW
 ├── findings ──→ NEEDS_FIXES ──→ IMPLEMENTING
 └── clean ─────→ READY
                     ↓
              HUMAN_APPROVAL
                     ↓
              MERGE_AUTHORIZED
                     ↓
                   MERGED
```

## Guards

### PLANNING -> READY_FOR_IMPLEMENTATION

- acceptance criteria are concrete;
- open product decisions resolved;
- dependencies known.

### IMPLEMENTING -> DRAFT_REVIEW

- candidate branch/PR exists;
- required validation evidence recorded.

### DRAFT_REVIEW -> READY

- independent review is clean;
- reviewed head has not changed.

### READY -> MERGE_AUTHORIZED

- human approval if project policy requires it;
- required CI for exact/current candidate succeeds.

### MERGE_AUTHORIZED -> MERGED

- candidate still mergeable;
- no head change invalidated review/CI.

## Failure routing

```text
product ambiguity -> BLOCKED / HUMAN
review finding    -> NEEDS_FIXES
test failure      -> IMPLEMENTING
transient API     -> retry policy
permission denied -> HUMAN / policy resolution
```