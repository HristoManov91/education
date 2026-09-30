# AI-assisted development — как се организира проект за работа с coding agents

Този модул не е урок за конкретен продукт. Codex, Claude Code, GitHub Copilot, Gemini CLI, Cursor и други инструменти са само различни **agent harnesses** (среда, която свързва модела с файлове, terminal, tools и workflow).

Целта е да разберем преносимата архитектура:

```text
HUMAN INTENT
    ↓
TASK CONTRACT
    ↓
PROJECT INSTRUCTIONS
    ↓
REUSABLE SKILLS
    ↓
TOOLS / DATA
    ↓
PLANNING
    ↓
IMPLEMENTATION
    ↓
INDEPENDENT REVIEW
    ↓
CI / GUARDS
    ↓
MERGE / RESULT
```

След темата трябва да можеш да отговориш не само „как се пише AGENTS.md“, а:

- коя информация е постоянна инструкция и коя принадлежи на конкретната задача;
- кога правило трябва да е в root `AGENTS.md`, кога в nested/scoped файл и кога изобщо не трябва да е там;
- кога повтаряем процес трябва да стане `SKILL.md`; 
- какво е разликата между skill, prompt template, tool/MCP и subagent;
- защо planner, implementer и reviewer са различни роли;
- как се проектира workflow, който не позволява агентът да си измисля requirements или да approve-ва сам себе си;
- кои части са open standards и кои са vendor-specific;
- как да смениш coding agent без да пренаписваш целия project knowledge.

---

# Реалният проблем

Наивният начин за работа е:

```text
нов chat
→ обяснявам проекта отначало
→ обяснявам coding conventions
→ казвам кои тестове да пусне
→ обяснявам Git workflow
→ напомням кои файлове не трябва да пипа
→ обяснявам как да направи review
```

След няколко задачи започваме да copy/paste-ваме огромен prompt.

Това има няколко проблема:

```text
context drift
→ различни sessions получават различни правила

duplication
→ една и съща информация живее на 5 места

staleness
→ command или architecture rule се променя, но стар prompt остава

role confusion
→ implementer започва да взема product решения или да review-ва сам себе си

tool confusion
→ агентът знае какво трябва да направи, но няма capability да го направи
```

Решението не е „един гигантски prompt“.

Решението е да разделим **policy, knowledge, procedure, task, capability и execution role**.

---

# Най-важният mental model

```text
README / docs
→ knowledge за проекта и хората

AGENTS.md / equivalent instructions
→ durable rules за agent-а

SKILL.md
→ reusable procedure / expertise

Issue / task contract
→ конкретната работа, която трябва да се свърши

Tool / MCP
→ capability: чети/пиши GitHub, DB, browser, Jira...

Agent / subagent
→ execution role със собствен context и permissions

Orchestrator
→ управлява state transitions между ролите
```

Една информация трябва да има **един естествен owner**.

---

# Шестте основни нива

## 1. Repository instructions — root `AGENTS.md`

`AGENTS.md` е open format за coding-agent инструкции. Мисли за него като:

> README за агента, не като енциклопедия на проекта.

В него слагаме стабилни правила, които почти всяка задача трябва да знае:

- кратка ориентация в repository-то;
- source-of-truth order;
- архитектурни boundaries;
- build/test entry points;
- security/compatibility invariants;
- Git/PR правила;
- указатели към canonical docs.

Не слагаме:

- 300 реда domain specification;
- детайлите на една конкретна feature задача;
- огромни command matrices, ако вече имат canonical document;
- workflow, който е нужен само при един тип задача;
- secrets или personal preferences.

Подробно: [INSTRUCTION-LAYERS.md](./INSTRUCTION-LAYERS.md).

---

## 2. Reusable skills — `SKILL.md`

Skill е:

> повтаряемо знание или workflow, който не трябва да стои постоянно в context-а.

Примери:

```text
plan-feature
review-pr
database-migration
security-audit
release-check
generate-api-client
```

Open Agent Skills format използва:

```text
my-skill/
├── SKILL.md
├── scripts/       # optional
├── references/    # optional
└── assets/        # optional
```

`SKILL.md` има YAML frontmatter с поне `name` и `description`; description е критичен, защото помага на agent harness-а да реши кога skill-ът е релевантен.

Подробно: [SKILLS-TOOLS-AND-MCP.md](./SKILLS-TOOLS-AND-MCP.md).

---

## 3. Planning / decomposition

Planning не е „напиши ми списък с TODO“.

Правилният planning flow е:

```text
human idea
→ inspect real repository
→ find existing owners / constraints
→ search duplicate work
→ separate product decisions from technical decisions
→ choose decomposition
→ define acceptance criteria
→ produce implementation-ready task contract
```

Важно:

> Plan-ът не трябва да измисля business semantics, които човекът не е решил.

И не всяка задача има нужда от отделен plan file. Малка локална промяна може да има само кратък in-session plan. Голям refactor, migration или multi-day feature има полза от durable plan/Issue.

---

## 4. Scoped instructions — nested `AGENTS.md` / path rules

Monorepo често има различни engineering правила:

```text
repo/
├── AGENTS.md
├── backend/
│   └── AGENTS.md
└── frontend/
    └── AGENTS.md
```

Root file:

```text
правила за целия project
```

`backend/AGENTS.md`:

```text
PostgreSQL / migrations / transactions / API conventions
```

`frontend/AGENTS.md`:

```text
state ownership / accessibility / localization / frontend tests
```

Ползата е **context locality**: agent-ът получава специфичните правила там, където са нужни, вместо root file-ът да се превърне в огромна енциклопедия.

Но nested instructions са правилният избор само за **стабилни subtree правила**, не за всяка feature.

---

## 5. Specialized agents / subagents

Skill и subagent НЕ са едно и също.

```text
SKILL
→ procedure / expertise

SUBAGENT
→ isolated execution context + role + tools/permissions
```

Пример:

```text
Planner
  read-only
  inspect + decompose

Implementer
  read/write code
  follows task contract

Reviewer
  read-only over exact diff + CI
  independently verifies
```

Subagent е полезен когато:

- задачата има самостоятелен workstream;
- искаме context isolation;
- искаме различни permissions;
- искаме независим review;
- parallel work действително е възможен.

Не е полезен за:

- trivial single-file edit;
- work, което изисква непрекъснато shared reasoning между стъпките;
- „да има повече agents“, без реална boundary.

---

## 6. Orchestration

Orchestrator е state machine, не „най-умният prompt“.

Пример:

```text
DISCUSSION
    ↓
PLANNED
    ↓
IMPLEMENTING
    ↓
DRAFT_PR
    ↓
REVIEWING
   ↙      ↘
FIXES     CLEAN
  ↓         ↓
IMPLEMENT  READY
  └──review─┘
            ↓
           CI
            ↓
       HUMAN APPROVAL
            ↓
          MERGE
```

Orchestrator-ът трябва да знае:

- какъв е current state;
- коя роля има право на следващото действие;
- какви evidence/guards са нужни;
- кога трябва human approval;
- как се връща назад при finding/failure;
- кое е retryable infrastructure failure и кое е real defect.

Подробно: [PLANNING-AGENTS-ORCHESTRATION.md](./PLANNING-AGENTS-ORCHESTRATION.md).

---

# Седмият cross-cutting слой: Tools / MCP

Той не заменя шестте нива, а ги прави изпълними.

```text
Skill:
„Провери PR срещу Issue contract.“

Tool:
„Ето API за fetch PR diff, CI logs и comments.“
```

Skill = знае **как**.

Tool = може **да**.

MCP (Model Context Protocol) е open protocol за свързване на AI applications с tools, resources и prompts.

Примери:

```text
GitHub tool
→ fetch issue / diff / create PR

DB tool
→ inspect schema / run query

Browser tool
→ open docs / interact with web UI

Jira tool
→ create/update ticket
```

Никога не слагай API credentials в skill или AGENTS.md. Permissions/auth принадлежат на runtime/tool layer.

---

# Portable core vs vendor adapter

Добре организиран project има две части:

```text
PORTABLE CORE
├── canonical docs
├── task contracts
├── AGENTS.md
├── .agents/skills/*/SKILL.md
└── CI / tests

VENDOR ADAPTER
├── CLAUDE.md / .claude/*          (ако е нужен)
├── GEMINI.md / settings           (ако е нужен)
├── .github/copilot-instructions   (ако е нужен)
├── product-specific agent profiles
└── product-specific tool config
```

Целта е да не дублираме project truth във всеки adapter.

Вместо:

```text
AGENTS.md      = 200 lines
CLAUDE.md      = същите 200 lines
GEMINI.md      = същите 200 lines
Copilot file   = същите 200 lines
```

предпочитай:

```text
canonical project knowledge
        ↑
short portable instructions
        ↑
thin adapters only where needed
```

Точната поддръжка и precedence се различават между harness-ите — винаги проверявай документацията на конкретния инструмент. Виж [VENDOR-MAPPING.md](./VENDOR-MAPPING.md).

---

# Къде да сложа дадена информация?

| Информация | Най-добрият owner |
| --- | --- |
| Как се стартира приложението за хора | `README.md` |
| Architecture / domain truth | `docs/` |
| Rule, нужен почти във всяка agent задача | root `AGENTS.md` / equivalent |
| Rule само за backend subtree | `backend/AGENTS.md` / path rule |
| Повтаряем multi-step workflow | `SKILL.md` |
| Голям reference само за конкретен skill | `skill/references/` |
| Deterministic helper logic | `skill/scripts/` |
| Output template | `skill/assets/` |
| Какво точно трябва да се имплементира сега | Issue/task contract |
| Live capability към външна система | Tool/MCP/plugin |
| Отделна специализирана execution роля | Subagent/custom agent |
| Последователност и guards между роли | Orchestrator/workflow engine |
| Secret/token | secret manager/runtime config, никога instruction file |

---

# Instruction precedence: две различни неща

Има **platform precedence** и **project source-of-truth precedence**.

## Platform precedence

Това е редът, по който конкретният AI product/harness комбинира system, organization, user, repository и scoped instructions.

Той е vendor-specific.

Не пишем собствена фантазия за него — четем документацията на конкретния tool.

## Project source of truth

Това е ред, който **ние проектираме**.

Пример:

```text
1. latest explicit human decision for current task
2. current Issue / task contract
3. code + tests on current branch/main
4. canonical architecture/domain docs
5. older comments/history
```

Този ред е изключително полезен, защото казва какво да прави agent-ът при конфликт между repository sources.

---

# Bad architecture: един giant instruction file

```text
AGENTS.md
├── architecture
├── every domain formula
├── 100 commands
├── all PR templates
├── all release steps
├── all testing docs
└── all historical decisions
```

Изглежда удобно, защото „всичко е на едно място“.

Но създава:

- голям context cost;
- stale duplicated truth;
- инструкции, които са irrelevant за 90% от задачите;
- труден review/ownership;
- по-голям шанс за contradictions.

По-добре:

```text
AGENTS.md = map + stable rules
docs/ = deep system of record
skills/ = procedures on demand
task = current scope
tools = live capabilities
```

---

# Bad architecture: skill за всичко

Skill не е правилният owner за:

- persistent repository invariant;
- one-off task requirement;
- secret;
- project architecture specification;
- tool credentials;
- agent runtime permissions.

Skill е полезен, когато можеш честно да кажеш:

> „Това е повтаряем начин на работа, който искам agent-ът да изпълнява еднакво при много задачи.“

---

# End-to-end walkthrough

Представи си задача: „Добави endpoint за export на отчет“.

```text
1. Human
   → определя business outcome

2. Planner
   → чете root/scoped instructions
   → чете docs и реалния код
   → проверява дали feature вече съществува
   → прави task contract

3. Implementer
   → зарежда implement-change skill
   → използва Git/files/terminal tools
   → прави smallest coherent change
   → пуска focused tests + canonical validation
   → отваря Draft PR

4. Reviewer
   → отделен context
   → чете task contract
   → гледа exact diff
   → проверява tests/CI
   → връща findings или clean review

5. Orchestrator
   → при findings връща state към implementation
   → при clean review проверява required guards
   → изисква human approval ако policy го казва

6. Merge
   → CI evidence + unchanged head + approval
```

Нито един слой не трябва да притежава всичко.

---

# README → примерните файлове

| Концепция | Пример |
| --- | --- |
| Root durable instructions | [`example-project/AGENTS.md`](./example-project/AGENTS.md) |
| Scoped backend instructions | [`example-project/backend/AGENTS.md`](./example-project/backend/AGENTS.md) |
| Scoped frontend instructions | [`example-project/frontend/AGENTS.md`](./example-project/frontend/AGENTS.md) |
| Canonical architecture doc | [`example-project/docs/architecture.md`](./example-project/docs/architecture.md) |
| Task contract | [`example-project/tasks/FEATURE-001.md`](./example-project/tasks/FEATURE-001.md) |
| Reusable skill | [`example-project/.agents/skills/implement-change/SKILL.md`](./example-project/.agents/skills/implement-change/SKILL.md) |
| Skill reference | [`references/implementation-checklist.md`](./example-project/.agents/skills/implement-change/references/implementation-checklist.md) |
| Skill output asset | [`assets/result-template.md`](./example-project/.agents/skills/implement-change/assets/result-template.md) |
| Orchestration state machine | [`example-project/workflow/ORCHESTRATION.md`](./example-project/workflow/ORCHESTRATION.md) |

---

# Decision guide

## Използвай persistent instructions когато

- правилото е стабилно;
- важи за почти всяка работа в scope-а;
- агентът трябва да го знае преди да започне.

## Използвай skill когато

- имаш повторяем workflow;
- procedure-ът има checklist/templates/references;
- не е нужно да стои постоянно в context.

## Използвай task/Issue когато

- информацията е специфична за текущата feature;
- има acceptance criteria;
- решението ще приключи след merge.

## Използвай subagent когато

- има отделима роля/workstream;
- isolation на context/tools има реална стойност;
- независимостта е част от correctness-а (например review).

## Използвай orchestrator когато

- задачата има durable state;
- има повече от една роля;
- има retry/failure/approval transitions;
- искаш repeatable end-to-end automation.

---

# Кога НЕ си струва

Не изграждай multi-agent platform за project с 20 файла и един developer, ако реалният flow е:

```text
prompt
→ edit
→ test
→ commit
```

Complexity има цена:

- повече configuration;
- повече context/tool calls;
- повече failure modes;
- нужда от tracing/observability;
- нужда от permission model;
- concurrency/conflict management.

Първо направи single-agent workflow deterministic. После добавяй agents там, където разделението носи стойност.

---

# Practical checklist за нов project

```text
[ ] README ясно ли е за хора?
[ ] Има ли canonical architecture/domain docs?
[ ] Root instructions кратки и стабилни ли са?
[ ] Има ли source-of-truth conflict policy?
[ ] Има ли scoped instructions само където реално са нужни?
[ ] Повтаряемите workflows изнесени ли са в skills?
[ ] Skills имат ли ясни trigger descriptions?
[ ] Tool capability отделен ли е от procedural instructions?
[ ] Secrets извън repository instructions ли са?
[ ] Task contract-ът съдържа ли конкретни acceptance criteria?
[ ] Implementer и reviewer независими ли са когато рискът го изисква?
[ ] Orchestration transitions ясни ли са?
[ ] Human approval boundary ясен ли е?
[ ] CI проверява ли механично това, което може да се провери механично?
[ ] Има ли един owner за всяко правило/knowledge source?
```

---

# Какво да запомня

1. **Instructions ≠ documentation ≠ task ≠ skill ≠ tool ≠ agent.**
2. `AGENTS.md` е за durable agent guidance, не за цялото знание на проекта.
3. `SKILL.md` е reusable procedure/expertise, зареждано при нужда.
4. Task contract-ът държи конкретния scope и acceptance criteria.
5. Tool/MCP дава capability; skill казва как да използваш capability-то.
6. Scoped rules намаляват noise в monorepo.
7. Subagent е отделен execution context, не просто Markdown checklist.
8. Reviewer трябва да проверява evidence, а не да вярва на implementer summary.
9. Orchestrator е state machine с guards и approvals.
10. Portable core + thin vendor adapters е по-устойчиво от дублирани vendor prompts.
11. Context window е ограничен resource — progressive disclosure е архитектурен принцип.
12. Най-добрият agent setup не замества tests, CI, permissions и source control.

---

# Упражнения

1. Вземи малък свой repository и раздели README knowledge от agent instructions.
2. Напиши root `AGENTS.md` под 100–150 реда.
3. Намери правило, което важи само за един subtree, и го изнеси локално.
4. Намери повтаряем prompt и го превърни в `SKILL.md`.
5. Направи skill description така, че да е ясно кога трябва и кога НЕ трябва да се активира.
6. Раздели един workflow на Planner / Implementer / Reviewer responsibilities.
7. Опиши orchestration state machine без да пишеш нито ред AI-specific code.
8. Направи vendor mapping за tool-а, който използваш в момента.
9. Смени tool-а и провери колко от project knowledge можеш да запазиш непроменено.

---

# Оригинални източници

- AGENTS.md open format: https://agents.md/
- Agent Skills specification: https://agentskills.io/specification
- Model Context Protocol: https://modelcontextprotocol.io/
- MCP TypeScript SDK overview (tools/resources/prompts): https://ts.sdk.modelcontextprotocol.io/v2/
- GitHub Copilot custom instructions: https://docs.github.com/en/copilot/reference/custom-instructions-support
- GitHub Copilot agent skills: https://docs.github.com/en/copilot/concepts/agents/about-agent-skills
- GitHub Copilot custom agents: https://docs.github.com/en/copilot/concepts/agents/copilot-cli/about-custom-agents
- Gemini CLI context files: https://geminicli.com/docs/cli/gemini-md/
- Gemini CLI Agent Skills: https://geminicli.com/docs/cli/skills/
- Claude Code project memory / AGENTS.md / CLAUDE.md: https://code.claude.com/docs/en/memory
- Claude Code Agent Skills: https://code.claude.com/docs/en/skills
- Claude Code subagents: https://code.claude.com/docs/en/sub-agents
- OpenAI — Harness engineering: https://openai.com/index/harness-engineering/
- OpenAI — ExecPlans: https://developers.openai.com/cookbook/articles/codex_exec_plans

---

# Изходен въпрос

Когато имаш нова информация за AI-assisted project, не питай първо:

> „В кой Markdown файл да я сложа?“

Питай:

> **„Какъв тип информация е това — durable rule, deep knowledge, reusable procedure, current task, capability, role или workflow state?“**

Ако отговориш правилно на това, правилното място почти винаги става очевидно.