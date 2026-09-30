# Handoff и resume — как незавършена работа се предава без загуба на контекст

Handoff е **portable snapshot за предаване на текущата работа** на друга сесия, друг agent, друг AI tool или човек.

Той не е нов source of truth и не замества Issue, PR, code, tests или orchestration state.

---

# 1. Какъв проблем решава?

Представи си:

```text
ден 1
Codex работи по Issue #42
→ има Draft PR
→ reviewer е намерил 2 проблема
→ единият е поправен
→ остава concurrency bug
→ част от тестовете вече са минали
```

На следващия ден искаш Claude Code, Gemini или нова Codex session да продължи.

Без handoff новият agent трябва да реконструира:

```text
каква е целта
+ какво вече е решено
+ какво вече е направено
+ кой PR/head е текущ
+ какво е проверено
+ какво остава
+ кое НЕ трябва да се преосмисля
```

Handoff събира точно тази **преходна operational context** в кратък пакет.

---

# 2. Handoff не е source of truth

Най-важното правило:

> **Handoff е derived snapshot. Repository truth остава authoritative.**

Примерен ред:

```text
latest explicit human decision
> current Issue/task contract
> current PR/head + code/tests
> canonical docs
> latest workflow/checkpoint state
> handoff snapshot
```

Handoff може да остарее веднага щом:

- PR head се промени;
- Issue contract се промени;
- нов review finding се появи;
- CI даде нов evidence;
- човекът вземе ново решение.

Затова receiving agent първо прави **reconciliation**:

```text
read handoff
→ fetch current Issue/PR/head
→ compare snapshot with reality
→ repository truth wins
→ continue
```

---

# 3. Handoff vs task contract vs checkpoint

Трите често се бъркат.

## Task contract

Отговаря:

> **Какво трябва да бъде постигнато?**

Съдържа:

```text
goal
scope
acceptance criteria
constraints
tests
out of scope
open product decisions
```

Той е contract за работата.

## Workflow checkpoint

Отговаря:

> **Къде се намира workflow state machine-ът?**

Пример:

```text
state = NEEDS_FIXES
issue = #42
pr = #57
head = abc123
next transition = implement fixes
```

Checkpoint е operational state за resume на orchestrator.

## Handoff

Отговаря:

> **Как друг agent/човек да поеме тази работа бързо и безопасно?**

Той добавя човешки/agent-friendly synthesis:

```text
какво е вече направено
какви решения са locked
какво е доказано
какви findings остават
какъв context ще е полезен на следващия изпълнител
какво точно е next action
```

Кратко:

```text
TASK CONTRACT = target
CHECKPOINT    = workflow position
HANDOFF       = transfer package
```

---

# 4. Handoff vs AGENTS.md и SKILL.md

`AGENTS.md` съдържа stable project rules.

```text
"Released migrations са append-only."
"Backend owns business rules."
```

`SKILL.md` съдържа reusable procedure.

```text
"Как review-ваме PR."
"Как имплементираме migration."
```

Handoff съдържа **ephemeral task state**.

```text
"PR #57 е на head abc123."
"Finding #1 е fixed."
"Finding #2 остава."
"Следващата стъпка е да се поправи race condition."
```

Следователно handoff информацията обикновено **не принадлежи** нито в root instructions, нито в reusable skill.

---

# 5. Минимален handoff пакет

Практичен template:

```text
# Task handoff

Repository:
<owner/repo>

Current task:
<Issue / ticket / task contract>

Goal:
<1-3 изречения>

Current workflow state:
<PLANNING / IMPLEMENTING / NEEDS_FIXES / REVIEWING / READY ...>

Current candidate:
<branch / PR / exact head SHA>

Done:
- ...

Locked decisions:
- ...

Validation/evidence:
- ...

Open findings / blockers:
- ...

Relevant files/docs:
- ...

Next action:
- ...

Do not:
- reinterpret already-decided product behavior
- create duplicate Issue/PR
- trust this handoff over newer repository state
- claim unexecuted validation passed
```

Exact identifiers са важни. "Последният PR" е по-слабо от:

```text
PR #57
head = abc123
```

---

# 6. Какво НЕ трябва да има вътре

Не включвай:

- secrets/tokens/passwords;
- hidden chain-of-thought;
- огромен dump на целия chat;
- копие на целия repository;
- stale build logs без значение;
- business rules, които вече имат canonical owner.

Handoff е **context compression**, не archive.

---

# 7. Кога е полезен?

## Смяна на AI/tool

```text
Codex
→ handoff
→ Claude Code
```

или:

```text
Claude Code
→ handoff
→ Gemini CLI
```

## Нова session след прекъсване

Особено когато task-ът е multi-day и chat context вече не е надежден.

## Предаване към друг developer

Handoff pattern-ът не е само AI concept. Добър operational handoff помага и между хора.

## External second opinion

Можеш да дадеш ограничен, чист пакет на reviewer вместо целия history.

---

# 8. Кога НЕ е нужен?

Ако:

- task-ът е малък;
- session-ът не се сменя;
- orchestrator може евтино и надеждно да реконструира current state;
- Issue + PR + checkpoint вече дават достатъчен context;
- handoff ще е по-дълъг от самата задача.

Тогава той е process overhead.

> Не създавай handoff само защото "AI workflow трябва да има handoff".

---

# 9. Skill ли трябва да бъде?

Самият **handoff artifact** не е задължително skill.

Но ако редовно правиш:

```text
inspect task
→ inspect PR/head
→ inspect latest review/CI
→ reconcile checkpoint
→ synthesize portable transfer package
```

това вече е repeatable procedure и може да бъде skill, например:

```text
prepare-handoff
```

Важно:

```text
skill = как генерираме handoff
handoff = конкретният generated snapshot
```

---

# 10. Vendor-neutral design

Няма universal `HANDOFF.md` standard, който всички tools автоматично разбират.

Portable е **структурата на информацията**, не filename-ът.

Може да бъде:

- Markdown файл;
- GitHub Issue comment;
- PR comment;
- task-system note;
- orchestration database record;
- copy-ready prompt;
- generated artifact.

Receiving harness трябва да знае къде да го получи.

---

# 11. Пример

```markdown
# Task handoff

Repository:
acme/orders

Task:
Issue #42 — idempotent payment callback

Goal:
Duplicate payment callbacks must not create duplicate ledger entries.

Current workflow state:
NEEDS_FIXES

Current candidate:
PR #57
head: abc123

Done:
- endpoint implementation complete
- unique DB constraint added
- duplicate-request regression test added

Locked decisions:
- idempotency key comes from provider event id
- duplicate callback returns HTTP 200 with original result

Validation:
- PaymentCallbackServiceTest — passed
- repository integration test — passed
- full CI not run on current head yet

Open finding:
- concurrent requests can race before insert and one path leaks constraint exception

Relevant:
- PaymentCallbackService
- PaymentEventRepository
- V18__payment_event_unique.sql
- Issue #42 acceptance criteria 2 and 4

Next action:
Fix concurrent duplicate handling, add a two-thread regression test, then request independent re-review.

Do not:
- change response semantics
- replace the unique constraint with JVM-local locking
- create a second PR
- trust this snapshot if PR #57 head changed
```

Това е достатъчно за нов agent да започне бързо, но той пак трябва да verify-не current repository state.

---

# 12. Mental model

Запомни:

```text
AGENTS.md
→ stable rules

docs/
→ deep truth

task contract
→ desired outcome

SKILL.md
→ reusable procedure

checkpoint
→ durable workflow position

handoff
→ portable transfer snapshot

repository/current head
→ actual reality
```

Handoff е полезен, когато **context ownership се сменя**. Ако ownership не се сменя и workflow state вече е reconstructable, той може спокойно да липсва.
