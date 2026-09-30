# Vendor mapping — една концепция, различни имена

Този файл е snapshot към 2026-09-30. Product behavior се променя бързо; винаги проверявай current documentation.

Главният принцип:

> **Не учи filename-а като концепция. Учи концепцията и после mapping-а към harness-а.**

---

# 1. Persistent project instructions

| Concept | Portable / product examples |
| --- | --- |
| Open agent instructions | `AGENTS.md` |
| Claude Code | `CLAUDE.md`, `.claude/rules/`, също поддържа `AGENTS.md` |
| Gemini CLI | `GEMINI.md` по default; context filename е configurable |
| GitHub Copilot | `.github/copilot-instructions.md`, path-specific `.instructions.md`, agent instructions според surface |
| Codex | `AGENTS.md` hierarchy / product config |

Не приемай, че precedence и load timing са идентични.

---

# 2. Skills

Agent Skills е open format с `SKILL.md`.

Към момента различни harnesses поддържат формата, но:

- discovery directories могат да се различават;
- optional frontmatter extensions могат да са vendor-specific;
- permission/consent semantics могат да са различни;
- някои harnesses auto-activate, други изискват explicit invocation/consent.

Затова portable skill трябва да използва standard fields, освен ако умишлено правиш vendor extension.

---

# 3. Custom agents / subagents

Няма един универсален cross-vendor agent-profile filename.

Conceptually profile-ът описва:

```text
name
description/routing
system instructions
tools
permissions
model/effort
skills
memory/context behavior
```

Конкретният serialization/location е harness-specific.

Пример:

- Claude Code има project/user subagent definitions;
- GitHub Copilot има custom agent profiles;
- други systems дефинират agents programmatically или през UI/config.

Следователно:

```text
role specification = portable concept
agent profile file = adapter
```

---

# 4. Tools

Всеки harness има tool model.

Portable options:

- MCP servers;
- command-line programs;
- HTTP APIs wrapped като tools;
- repository-native scripts.

Vendor adapter-ът определя как tool-ът се регистрира и permission-ва.

---

# 5. Planning

Няма универсален `PLAN.md`, който всички coding agents магически разбират еднакво.

Portable pattern:

```text
durable task/Issue
+
optional execution plan format documented by project
```

Tool-specific plan modes са удобни, но не трябва да са единственото място, където живее multi-day contract.

---

# 6. Orchestration

Няма universal `ORCHESTRATOR.md` runtime standard.

Варианти:

- harness-native subagent delegation;
- GitHub Actions / CI;
- SDK/application-level state machine;
- workflow engine;
- issue/PR automation.

Portable е **state model-ът**, не implementation API-то.

---

# 7. Recommended repository strategy

```text
repo/
├── README.md
├── AGENTS.md
├── docs/
├── .agents/
│   └── skills/
│       └── .../SKILL.md
├── backend/AGENTS.md        # only if needed
└── frontend/AGENTS.md       # only if needed
```

После добавяй thin adapters само когато текущият harness има нужда от тях.

---

# 8. Не прави това

```text
AGENTS.md
CLAUDE.md
GEMINI.md
.github/copilot-instructions.md
```

с четири независими копия на едни и същи 250 реда.

Това гарантира drift.

Ако tool-ът може да използва portable format директно — използвай го.

Ако не може — adapter-ът трябва да е кратък и да сочи/отразява canonical project sources.

---

# 9. Sources

- https://agents.md/
- https://agentskills.io/specification
- https://code.claude.com/docs/en/memory
- https://code.claude.com/docs/en/skills
- https://code.claude.com/docs/en/sub-agents
- https://geminicli.com/docs/cli/gemini-md/
- https://geminicli.com/docs/cli/skills/
- https://docs.github.com/en/copilot/reference/custom-instructions-support
- https://docs.github.com/en/copilot/concepts/agents/about-agent-skills
- https://docs.github.com/en/copilot/concepts/agents/copilot-cli/about-custom-agents