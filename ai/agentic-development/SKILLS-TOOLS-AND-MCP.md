# Skills, tools и MCP — „знае как“ срещу „може да“

Това е една от най-важните разлики в agent architecture.

```text
SKILL
→ procedural knowledge
→ как се прави нещо

TOOL
→ executable capability
→ действително извършва операция
```

Пример:

```text
review-pr skill
→ как да направиш надежден PR review

GitHub tool
→ fetch diff / comments / CI logs
```

Skill без tool може да знае идеалния workflow, но да няма достъп до evidence.

Tool без skill може да извършва операции, но няма project-specific procedure.

---

# 1. Agent Skills open format

Минимална структура:

```text
review-change/
└── SKILL.md
```

Практична структура:

```text
review-change/
├── SKILL.md
├── references/
│   └── checklist.md
├── scripts/
│   └── validate.sh
└── assets/
    └── review-template.md
```

Минимален `SKILL.md`:

```yaml
---
name: review-change
description: Review a code change against its task contract, tests and CI. Use for PR review or re-review; do not use for implementation.
---
```

След frontmatter идват Markdown instructions.

---

# 2. `name` и `description`

`name` е stable identifier.

`description` е routing contract.

Лошо:

```yaml
description: Helps with reviews.
```

Добре:

```yaml
description: Review a pull request against its task contract, changed code, tests and CI evidence. Use when asked to review, re-review or verify readiness; do not use to implement the change.
```

Добрата description казва:

- какво прави;
- кога се активира;
- понякога и кога НЕ трябва да се активира.

---

# 3. Body на SKILL.md

Body-то трябва да съдържа procedure:

```text
Inputs / preconditions
Workflow
Decision points
Evidence requirements
Failure modes
Output contract
References/assets/scripts to use
```

Не е нужно да съдържа цялата domain documentation.

---

# 4. references/

Reference е knowledge, който skill-ът може да прочете само при нужда.

Примери:

```text
security-checklist.md
api-compatibility.md
migration-rules.md
review-severity.md
```

Това спестява context: main `SKILL.md` остава кратък.

---

# 5. scripts/

Script е правилен избор когато operation е deterministic.

Вместо agent-ът всеки път да измисля:

```text
как да parse-не coverage report
как да валидира schema
как да провери naming rules
```

можеш да имаш:

```text
scripts/check-coverage.py
scripts/validate-schema.sh
```

Agent-ът оркестрира; script-ът изпълнява deterministic logic.

Важно:

- script-ът трябва да валидира inputs;
- errors трябва да са разбираеми;
- dependencies трябва да са описани;
- не приемай automatically third-party skill scripts за trustworthy.

---

# 6. assets/

Assets са output-oriented static resources:

- templates;
- example configs;
- report skeletons;
- schemas;
- static lookup data.

Пример:

```text
assets/implementation-result-template.md
```

Agent-ът копира/адаптира структурата, вместо да я изобретява всеки път.

---

# 7. Progressive disclosure

Добър skill system не зарежда всички skills изцяло постоянно.

Conceptually:

```text
startup
→ names + descriptions

task matches
→ load SKILL.md

need detail
→ read reference/asset

need deterministic operation
→ run script
```

Това позволява голяма библиотека от skills без огромен always-on context.

---

# 8. Tool layer

Tool е typed action, например:

```text
read_file(path)
run_tests(module)
get_pr(number)
query_database(sql)
open_url(url)
```

Качественият tool има:

- ясна description;
- строг input schema;
- предвидим output schema;
- permission boundary;
- error semantics;
- auditability когато действието е sensitive.

---

# 9. MCP

MCP е open protocol, чрез който AI host/client се свързва към servers, които могат да expose-нат:

```text
tools
resources
prompts
```

Conceptual architecture:

```text
AI HOST
  │
  ├── MCP client ───── MCP server: GitHub
  ├── MCP client ───── MCP server: DB
  └── MCP client ───── MCP server: internal docs
```

Tool selection обикновено е model-driven; prompts/resources могат да се expose-ват по различен начин според host-а.

---

# 10. Skill vs MCP prompt

И двете могат да съдържат instructions, но ownership е различен.

```text
Project skill
→ versioned с repository workflow
→ project/team-specific procedure

MCP prompt
→ published от server
→ често свързан с capability/data на server-а
```

Не е нужно да избереш само едното.

---

# 11. Skill vs custom agent

```text
Skill
→ „как се прави review“

Custom agent
→ „ти си reviewer; имаш read-only tools; използвай review skill“
```

Един skill може да бъде използван от различни agents.

Един agent може да използва няколко skills.

---

# 12. Security

Skills могат да бъдат supply-chain risk.

Преди да install-неш външен skill:

```text
inspect SKILL.md
inspect scripts
inspect external references/downloads
check requested tools/permissions
pin source/version when practical
```

Никога:

- не hardcode-вай secrets;
- не давай broad shell/network access без причина;
- не приемай `allowed-tools` metadata като абсолютна security enforcement във всеки product;
- не изпълнявай непроверен third-party script само защото се казва „skill“.

---

# 13. Checklist: трябва ли да стане skill?

```text
[ ] Повтарям ли тези инструкции през много задачи?
[ ] Има ли ясно начало/край и output contract?
[ ] Има ли procedure, не само един факт?
[ ] Може ли да има полезни references/assets/scripts?
[ ] Не трябва ли всъщност да е persistent rule?
[ ] Не е ли просто current Issue requirement?
[ ] Description може ли ясно да route-не задачите?
[ ] Skill-ът portable ли е или е умишлено vendor extension?
```