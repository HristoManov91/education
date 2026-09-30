# Hands-on — проектиране на AI-assisted repository

Тук не стартираме конкретен agent product. Упражненията са за **архитектурата на context/workflow-а**.

Използвай [`example-project/`](./example-project/) като reference.

---

# Lab 1 — Класифицирай информацията

За всяко твърдение избери owner:

```text
A. "PostgreSQL е production database."
B. "За Issue #42 новият endpoint връща HTTP 202."
C. "При PR review първо провери acceptance criteria, после diff и CI."
D. "GitHub token = ..."
E. "Backend migrations са append-only."
F. "Architecture diagram за event flow."
```

Очакван reasoning:

- stable project/subtree rule → instructions;
- current task behavior → Issue/task contract;
- repeatable procedure → skill;
- secret → secret/runtime layer;
- deep system knowledge → docs.

---

# Lab 2 — Намали giant AGENTS.md

Представи си 600-line `AGENTS.md`.

Раздели го на:

```text
root AGENTS.md
scoped AGENTS.md
docs/
skills/
task contract
```

Цел: root file да остане map + stable invariants.

---

# Lab 3 — Напиши skill trigger

Лошо:

```yaml
description: Helps with implementation.
```

Напиши description, която различава:

- planning;
- implementation;
- review.

После провери дали един user prompt би match-нал два skills двусмислено.

---

# Lab 4 — Skill или agent?

Имаш workflow:

```text
read Issue
inspect diff
run focused tests
report findings
```

Първо го направи skill.

После реши дали имаш нужда от отделен Reviewer agent.

Hint: отделен agent има смисъл ако искаш независим context/permissions, не само различен checklist.

---

# Lab 5 — Least privilege

За трите роли определи tools:

```text
Planner
Implementer
Reviewer
```

Пример:

Planner може да има read/search/issues, но не code write.

Reviewer обикновено няма нужда от merge permission.

---

# Lab 6 — State machine

Напиши transitions за:

```text
NEW
PLANNED
IMPLEMENTING
DRAFT_PR
NEEDS_FIXES
READY
MERGED
```

За всяка transition добави guard.

Пример:

```text
DRAFT_PR -> READY
guard = independent review clean AND head unchanged
```

---

# Lab 7 — Human approval

Маркирай кои actions могат да са autonomous и кои искат approval:

- create branch;
- edit code;
- run tests;
- create Draft PR;
- mark Ready;
- merge;
- production deploy;
- destructive DB migration.

Няма universal answer; това е governance design.

---

# Lab 8 — Vendor swap

1. Избери текущия си coding agent.
2. Map-ни portable core към неговите native files/settings.
3. Представи си, че утре сменяш tool-а.
4. Изброи кои файлове остават непроменени.

Цел:

> project knowledge да не е заключен в един proprietary prompt format.

---

# Lab 9 — Audit на реален repository

Провери:

```text
[ ] Има ли duplicate instructions?
[ ] Има ли stale commands?
[ ] Има ли business rules само в prompts, но не и в canonical docs/tests?
[ ] Има ли skills, които всъщност са permanent rules?
[ ] Има ли AGENTS rules, които всъщност са procedures?
[ ] Има ли broad permissions без причина?
[ ] Reviewer независим ли е?
[ ] Merge guard mechanical ли е където може?
```