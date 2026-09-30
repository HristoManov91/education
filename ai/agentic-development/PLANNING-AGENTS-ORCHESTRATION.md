# Planning, subagents и orchestration

След instructions и skills идва execution architecture.

---

# 1. Planning е repository-grounded reasoning

Лош planning:

```text
User: Добави export.
Agent:
1. Create ExportService
2. Create ExportController
3. Add test
```

Това е generic guess.

Добър planning:

```text
inspect existing export flows
→ identify owner/service/API patterns
→ inspect security/authorization
→ inspect data volume/pagination
→ inspect existing issue/duplicate
→ resolve product choices
→ produce acceptance criteria
```

Plan трябва да отговаря:

- какъв е current baseline;
- какво точно се променя;
- кои boundaries са засегнати;
- как ще докажем correctness;
- какво е out of scope;
- какви human decisions още липсват.

---

# 2. Кога durable plan?

Durable plan/Issue е полезен при:

- multi-day work;
- cross-module feature;
- migration/refactor;
- риск за persistence/concurrency/security;
- повече от един implementer/agent;
- workflow, който трябва да resume-не след прекъсване.

За малка промяна:

```text
кратък in-session plan
→ edit
→ tests
```

е достатъчен.

---

# 3. Task contract

Task contract е boundary между product decision и implementation.

Минимално:

```text
Goal / problem
Verified baseline
Scope
Acceptance criteria
Constraints / compatibility
Tests / validation
Out of scope
Open decisions
```

Agent-ът не трябва да „довършва“ missing product semantics чрез догадки.

---

# 4. Agent role

Agent role трябва да дефинира:

```text
goal
inputs
allowed tools
permissions
skills
stopping condition
output contract
```

Примерен Planner:

```text
Goal: produce implementation-ready task
Tools: read/search/issues
Write code: NO
May create/update task: YES
Stop when: product decisions resolved or clearly escalated
```

Implementer:

```text
Goal: satisfy task contract
Tools: read/write/test/Git
May change requirements: NO
May open Draft PR: YES
May approve/merge own PR: NO
```

Reviewer:

```text
Goal: independently verify
Tools: read/diff/tests/CI
Write production code: normally NO
Trust implementer summary: NO
Stop when: findings reported or clean evidence complete
```

---

# 5. Защо independent reviewer?

Self-review е полезен, но не е същото като independent review.

Implementer има context bias:

- знае какво е искал да направи;
- лесно приема собственото си explanation за evidence;
- може да пропусне requirement, който е мислел че е изпълнил.

Independent reviewer започва от:

```text
task contract
+ exact diff
+ current code
+ tests
+ CI
```

и сам доказва claims.

---

# 6. Subagent кога?

Subagent е отделен context window / execution environment вътре или под parent agent.

Ползвай когато:

```text
parallel research
isolated review
high-volume logs/search
specialized tool permissions
independent workstream
```

Не ползвай автоматично когато:

```text
single-file edit
sequential tightly-coupled reasoning
communication overhead > work
```

---

# 7. Orchestrator като state machine

Не започвай с „AI manager persona“.

Започни със states:

```text
NEW
PLANNING
READY_FOR_IMPLEMENTATION
IMPLEMENTING
DRAFT_REVIEW
NEEDS_FIXES
READY
MERGE_AUTHORIZED
MERGED
BLOCKED
```

И transitions:

```text
NEW -> PLANNING
PLANNING -> READY_FOR_IMPLEMENTATION
READY_FOR_IMPLEMENTATION -> IMPLEMENTING
IMPLEMENTING -> DRAFT_REVIEW
DRAFT_REVIEW -> NEEDS_FIXES
NEEDS_FIXES -> IMPLEMENTING
DRAFT_REVIEW -> READY
READY -> MERGE_AUTHORIZED   [human approval if required]
MERGE_AUTHORIZED -> MERGED  [same-head CI/guards]
```

Това вече може да бъде имплементирано чрез:

- coding-agent harness;
- CI workflows;
- GitHub Issues/PR state;
- custom application;
- workflow engine;
- combination от тях.

---

# 8. Guards

Guard е проверимо условие за transition.

Примери:

```text
implementation -> review
requires: Draft PR exists + validation evidence

review -> ready
requires: clean independent review + unchanged head

ready -> merge
requires: CI success + approval + mergeable head
```

Колкото повече guard може да бъде mechanical, толкова по-малко трябва да разчитаме на natural-language obedience.

---

# 9. Human-in-the-loop

Human approval е нужен когато:

- product decision е ambiguous;
- действие е destructive/irreversible;
- production deployment;
- financial/security risk;
- policy изисква separation of duties;
- merge policy го изисква.

Autonomy не означава премахване на governance.

---

# 10. Retry и failure semantics

Orchestrator трябва да различава:

```text
CI compile failure
→ real work failure

GitHub API timeout
→ potentially retryable infrastructure failure

review finding
→ state transition back to implementation

missing product decision
→ BLOCKED / human input
```

Без това agent loop може да прави безкрайни retries или да „поправя“ нещо, което всъщност изисква decision.

---

# 11. Shared state

Не разчитай само на chat memory за multi-step workflow.

Durable state може да бъде:

- Issue labels/status;
- PR Draft/Ready state;
- comments/results;
- workflow database;
- checked-in plan file;
- orchestration service.

Избери според project complexity.

---

# 12. Handoff и resume между sessions/tools

Durable state и handoff решават различни проблеми.

```text
checkpoint
→ machine/workflow position

handoff
→ transfer package за следващия agent/tool/human
```

Полезен handoff съдържа:

- task/Issue и goal;
- current workflow state;
- branch/PR и exact head SHA;
- какво вече е направено;
- locked decisions;
- validation/evidence;
- remaining findings/blockers;
- relevant files/docs;
- exact next action.

Той е **derived snapshot, не authoritative state**. Receiving agent трябва да fetch-не current repository evidence и да reconcile-не handoff-а преди да продължи.

Handoff е особено полезен при:

```text
Codex -> Claude Code
new session -> resumed task
agent -> human developer
implementer -> external specialist
```

Но ако същият orchestrator може евтино да възстанови state-а от Issue/PR/checkpoint, отделен handoff може да е излишен overhead.

Виж [HANDOFF-AND-RESUME.md](./HANDOFF-AND-RESUME.md).

---

# 13. Observability

При повече agents трябва да можеш да отговориш:

```text
кой agent извърши действието?
с кой task/head SHA?
какви tools извика?
какъв evidence използва?
защо transition-ът беше позволен?
къде се провали?
```

Без trace/audit multi-agent automation става трудно debuggable.

---

# 14. Най-малкият полезен maturity ladder

```text
LEVEL 0
chat prompt only

LEVEL 1
root instructions + canonical docs

LEVEL 2
scoped instructions + reusable skills

LEVEL 3
structured task contracts + CI evidence

LEVEL 4
planner / implementer / reviewer roles

LEVEL 5
orchestrated state machine + approvals + observability
```

Не прескачай директно на Level 5.

---

# 15. Checklist

```text
[ ] Planning grounded ли е в real repo?
[ ] Product и technical decisions разделени ли са?
[ ] Task contract implementation-ready ли е?
[ ] Role boundaries ясни ли са?
[ ] Tools/permissions least-privilege ли са?
[ ] Reviewer независим ли е?
[ ] Workflow states explicit ли са?
[ ] Mechanical guards автоматизирани ли са?
[ ] Human approval boundaries ясни ли са?
[ ] Retryable и non-retryable failures различени ли са?
[ ] State durable ли е за long-running work?
[ ] Ако ownership/session/tool се сменя, има ли ясен handoff/resume mechanism?
[ ] Handoff-ът derived snapshot ли е, а не competing source of truth?
[ ] Има ли trace/audit trail?
```