# Example project

Това е **учебна структура**, не framework и не repository, който трябва да copy/paste-неш дословно.

```text
example-project/
├── README.md
├── AGENTS.md
├── backend/
│   └── AGENTS.md
├── frontend/
│   └── AGENTS.md
├── docs/
│   └── architecture.md
├── tasks/
│   └── FEATURE-001.md
├── .agents/
│   └── skills/
│       └── implement-change/
│           ├── SKILL.md
│           ├── references/
│           │   └── implementation-checklist.md
│           └── assets/
│               └── result-template.md
├── agent-roles/
│   ├── README.md
│   ├── planner.md
│   ├── implementer.md
│   └── reviewer.md
└── workflow/
    └── ORCHESTRATION.md
```

## Кое е стандарт и кое е conceptual?

- `AGENTS.md`: open convention за coding-agent instructions.
- `.agents/skills/*/SKILL.md`: Agent Skills open format.
- `docs/` и `tasks/`: project conventions, не AI standard.
- `agent-roles/*.md`: **conceptual role specs** за обучение; конкретният harness може да изисква друго местоположение/формат.
- `workflow/ORCHESTRATION.md`: human-readable state model; runtime implementation зависи от избрания orchestrator.

Целта е responsibility separation, не конкретните имена на всички папки.