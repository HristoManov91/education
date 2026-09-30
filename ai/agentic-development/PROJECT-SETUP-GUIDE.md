# Project setup guide — от празен repository до организирана AI работа

Това е practical sequence. Не е нужно да създадеш всичко в ден 1.

---

# Stage 0 — Преди AI configuration

Първо repository-то трябва да е разбираемо **без AI**:

```text
README
source tree
build
tests
CI
architecture/domain docs where needed
```

Ако човек не може да разбере как се build/test-ва проектът, giant agent prompt няма да поправи фундаменталния documentation problem.

---

# Stage 1 — Root instructions

Създай root `AGENTS.md` когато coding agent започне да работи регулярно по repository-то.

Първа версия:

```text
Project orientation
Source of truth
Before editing
Validation owner
Git/PR rules
Important safety/architecture invariants
```

Не се опитвай да предвидиш всички бъдещи грешки.

Добавяй rule когато е:

- устойчив;
- повторяем;
- project-specific;
- трудно discoverable само от кода.

---

# Stage 2 — Canonical docs + scoped instructions

Когато root file започне да расте, не добавяй още 500 реда.

Питай:

```text
Това deep knowledge ли е?
→ docs/

Това правило само за subtree ли е?
→ scoped AGENTS.md / path rule

Това workflow ли е?
→ skill
```

---

# Stage 3 — Skills

Следи какво copy/paste-ваш в prompts.

Ако редовно казваш:

```text
прочети Issue
провери dependencies
направи change
пусни tests
подготви result
```

това е кандидат за implementation skill.

Започни с един skill, не библиотека от 30.

---

# Stage 4 — Structured task contracts

При non-trivial feature използвай durable task contract.

Най-простият вариант е GitHub Issue/Jira ticket.

Contract:

```text
Goal
Baseline
Scope
Acceptance criteria
Constraints
Tests
Out of scope
Open decisions
```

Task contract не трябва да повтаря всички repository rules.

---

# Stage 5 — Tools / MCP

Добавяй capability когато agent-ът реално има нужда да действа върху външна система.

Пример:

```text
repo editing → filesystem/git tools
PR lifecycle → GitHub tool
tickets → Jira tool
database diagnosis → DB read tool
browser-only system → browser/computer tool
```

Permissions:

```text
read by default
write only where needed
destructive actions behind approval
secrets through runtime auth
```

---

# Stage 6 — Role separation

Добави Planner/Implementer/Reviewer само когато имаш реална полза.

Добър trigger:

- задачите са достатъчно сложни;
- review independence е важен;
- planning context е голям;
- workstreams могат да се разделят;
- permissions трябва да се различават.

---

# Stage 7 — Orchestration

Автоматизирай чак когато manual workflow вече е ясен и повтаряем.

Преди code напиши state machine:

```text
states
transitions
guards
approvals
failure routing
retry policy
```

Ако не можеш да го опишеш ясно на хартия, AI orchestrator няма да го направи по-ясен.

---

# Stage 8 — Handoff / resume (само ако реално е нужен)

Когато задачи редовно прескачат между sessions, agents, AI tools или хора, дефинирай portable handoff format.

Минимално:

```text
task + goal
workflow state
PR/branch + exact head
completed work
locked decisions
validation/evidence
open findings
next action
```

Не създавай handoff като втори source of truth. Той трябва да се генерира/обновява от current repository evidence и receiving agent трябва да го reconcile-не срещу реалното състояние.

За малък project или uninterrupted single-agent flow този stage може напълно да се пропусне.

Виж [HANDOFF-AND-RESUME.md](./HANDOFF-AND-RESUME.md).

---

# Minimal setup за малък project

```text
README.md
AGENTS.md
docs/architecture.md       # ако complexity го изисква
CI
```

Това може да е напълно достатъчно.

---

# Medium project

```text
README.md
AGENTS.md
backend/AGENTS.md          # ако backend има distinct rules
frontend/AGENTS.md
docs/
.agents/skills/
Issues/task contracts
CI
```

---

# Advanced agent-first workflow

```text
portable instructions
+ canonical docs
+ scoped rules
+ skills
+ connected tools
+ durable task contracts
+ specialized roles
+ orchestration state machine
+ CI/approval guards
+ observability/audit
+ optional handoff/resume при смяна на context owner
```

---

# Как да мигрираш съществуващ project

Не rewrite-вай всичко.

1. Запиши кои инструкции повтаряш най-често.
2. Направи root instruction file.
3. Намери canonical docs и link-ни ги.
4. Изчисти duplication.
5. Изнеси first reusable workflow като skill.
6. Структурирай следващата non-trivial задача като task contract.
7. Едва след няколко реални задачи реши дали имаш нужда от subagents/orchestration.

---

# Success criteria

Добре настроеният AI-assisted project трябва да позволява на нов compatible agent да:

```text
ориентира се
→ намери truth
→ разбере текущата задача
→ знае workflow-а
→ има само нужните capabilities
→ изпълни проверими стъпки
→ представи evidence
→ при смяна на owner/session да може да предаде compact verified handoff
```

без човекът да paste-ва 2000 реда instructions във всеки нов chat.