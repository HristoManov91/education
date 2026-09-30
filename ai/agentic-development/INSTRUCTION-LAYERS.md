# Instruction layers — persistent context без prompt хаос

Този файл разглежда само **инструкционния слой**: root/scoped instructions, canonical docs, current task и conflict resolution.

## 1. Три вида информация, които често се смесват

### Durable instruction

Правило, което agent-ът трябва да знае почти винаги:

```text
Не редактирай released migrations.
Run canonical validation before delivery.
Backend owns business formulas.
```

Owner: `AGENTS.md` или vendor-equivalent persistent instruction.

### Deep project knowledge

```text
Как работи battle formula?
Каква е event schema?
Какъв е database lifecycle?
```

Owner: `docs/`, source code, tests.

Persistent instructions трябва да **link-ват**, не да копират цялото съдържание.

### Current task decision

```text
За feature X timeout е 30 seconds.
Field Y остава backwards compatible.
```

Owner: current Issue/task contract.

Не го слагаме в repository-wide instructions, ако няма да остане universal invariant.

---

# 2. Какво трябва да съдържа root AGENTS.md

Добър root файл обикновено има:

```text
Project orientation
Source of truth
Architecture boundaries
Implementation discipline
Validation entry point
Documentation policy
Git / PR policy
Pointers to skills and scoped instructions
```

Той трябва да помага на нов agent да се ориентира за минути.

## Добър пример

```markdown
# Project agent instructions

## Orientation
- backend/: Java/Spring
- frontend/: Vue
- docs/: canonical architecture/domain docs

## Source of truth
1. Latest explicit human task decision
2. Current Issue
3. Code + tests
4. Canonical docs

## Before editing
- Read relevant code/tests.
- Search callers before changing public contracts.
- Use docs/test-commands.md for validation.
```

## Лош пример

```text
AGENTS.md = копие на README + architecture + every command + every domain formula + every old decision
```

Това е knowledge dump, не instruction map.

---

# 3. Nested / scoped instructions

Използвай nested instructions когато subtree има устойчиви правила.

```text
repo/
├── AGENTS.md
├── backend/
│   └── AGENTS.md
└── mobile/
    └── AGENTS.md
```

Root:

```text
shared workflow + repository invariants
```

Backend:

```text
database / transaction / API conventions
```

Mobile:

```text
platform lifecycle / UI / offline rules
```

### Не създавай nested AGENTS.md когато

- имаш само 2 локални style rules, които tooling може да enforce-не;
- правилото е временно за една feature;
- просто искаш „още context“;
- няма реална различна ownership boundary.

---

# 4. Vendor-specific equivalents

`AGENTS.md` е open format, но tool-овете имат и свои native механизми.

Примери:

```text
Claude Code  → CLAUDE.md / .claude/rules + AGENTS.md support
Gemini CLI   → GEMINI.md (filename configurable)
GitHub       → .github/copilot-instructions.md + path instructions + AGENTS.md in supported agents
Codex        → AGENTS.md hierarchy
```

Това НЕ означава, че всички имат идентична load/precedence semantics.

Правилото е:

> **portable intent, vendor-specific loading semantics.**

Виж [VENDOR-MAPPING.md](./VENDOR-MAPPING.md).

---

# 5. Source-of-truth conflict policy

Repository sources неизбежно се разминават.

Пример:

```text
old docs say: timeout = 10s
current Issue says: timeout = 30s
code currently has: 10s
```

Agent-ът трябва да знае кое е target state.

Полезен project-level ред:

```text
latest explicit human decision
> current task contract
> current implementation/tests as baseline
> canonical docs
> historical discussion
```

Това не е platform instruction precedence. Това е **engineering conflict resolution policy**, която е наша.

---

# 6. Instruction като context, не като security control

Markdown instruction е guidance към model.

Не разчитай на:

```text
"Never access production"
```

като единствен security boundary.

Security enforcement трябва да е в:

- permissions;
- sandbox;
- tool allow/deny policy;
- network policy;
- secret access;
- approval hooks;
- CI/branch protection.

Добър architecture:

```text
instruction: "не deploy-вай production без approval"
+
runtime: production deploy tool изисква approval
```

---

# 7. Context budget

Всеки persistent instruction consumes context.

Следователно:

```text
always relevant → persistent instruction
sometimes relevant → skill / scoped rule / on-demand doc
task-specific → task contract
```

Това е **progressive disclosure** (постепенно зареждане на context според нуждата).

---

# 8. Checklist за AGENTS.md review

```text
[ ] Кратък ли е и лесно ли се сканира?
[ ] Има ли само stable cross-task rules?
[ ] Link-ва ли canonical docs вместо да ги дублира?
[ ] Source-of-truth precedence ясен ли е?
[ ] Build/test commands имат ли canonical owner?
[ ] Има ли secrets/personal data? (не трябва)
[ ] Има ли task-specific acceptance criteria? (не трябва)
[ ] Има ли long procedure, която трябва да стане skill?
[ ] Има ли subtree-specific rules, които трябва да се изнесат?
[ ] Има ли contradictions със scoped/vendor instructions?
```