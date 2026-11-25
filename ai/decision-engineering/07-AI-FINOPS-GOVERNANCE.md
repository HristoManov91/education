# AI-DE-07 · AI FinOps, ROI & Governance (P2)

## Проблем и казус

Refactor pipeline работи локално. При rollout към много екипи token spend, повторения, review и platform support растат. Евтиният code generation може да е скъп end-to-end.

**От лекцията (~18:34–21:22, 30:38–31:42, 36:36–41:38):** cost attribution, избор на подходящ модел, ограничени експерименти, ROI по конкретен use case и governance; авторите не отчитат завършен ROI за всички пилоти. Не твърдят, че AI трябва да се използва навсякъде.

### Примерни числа (наша учебна сметка; не цени на реален модел)

| За 10 сходни приети задачи | Без AI | С AI |
| --- | ---: | ---: |
| Implementation + review + rework | 40 h | 30 h |
| Часова full-loaded ставка | 50 EUR | 50 EUR |
| Допълнителен AI/platform разход | 0 EUR | 200 EUR |
| Общ пряк разход | 2000 EUR | 1700 EUR |
| Cost / accepted task | 200 EUR | 170 EUR |

При съпоставимо качество, scope и период: 500 EUR спестен трудов капацитет минус 200 EUR допълнителен разход = 300 EUR оценена нетна стойност. ROI върху допълнителните 200 EUR = 150% за този период и тази дефиниция. Не е гарантиран cash saving: освободените часове имат стойност само ако реално се пренасочат. Ако допълнителният review/rework е още 8 h, цената става 2100 EUR и net benefit е -100 EUR. Нулев accepted count → няма дефиниран cost/accepted task; отчети failed attempts, не ги изключвай от cost. Следи quality/latency/security; не сравнявай неравни задачи.

### Governance contract (non-runnable policy/worksheet)

- Usage by team, product, task and model; всички retries, review, eval, infrastructure, data handling, operational cost. Allocate shared cost прозрачно.
- Budget и timeout на workflow; лимит на retries/tool calls; избор на най-прост подход (deterministic script пред LLM, малък модел пред голям при доказана quality).
- Allowlist tools/permissions, least privilege, explicit approval за write/destructive actions; untrusted content и LLM output не са authorization.
- Redact PII/secrets, approved provider/region/retention, audit trail, data classification. При leakage/unsafe action stop независимо от ROI.
- Baseline + quality gate + pilot + revisit date; monitor adoption, accepted outcome, cost distribution, rework и incidents. Stop/pivot/scale, не sunk-cost fallacy.

Decision contract: за повторяеми AI workloads с реален разход и измерим output; не изграждай тежка metering platform за единичен малък експеримент (но спазвай data/security). Печелим cost visibility и контрол; губим instrumentation и admin overhead. Алтернатива: проста таблица с total cost и quality. Доказателство: пресметни чувствителността в [exercises](EXERCISES.md), попълни реален pilot sheet, не обещавай ROI.

## Оригинални източници

[Лекция](https://www.youtube.com/watch?v=2pSyF44THEc) · [FinOps for AI overview](https://www.finops.org/wg/finops-for-ai-overview/) · [FinOps unit economics](https://www.finops.org/framework/capabilities/unit-economics/) · [OWASP prompt injection](https://genai.owasp.org/llm2026/llm012026-prompt-injection/) · [OWASP sensitive disclosure](https://genai.owasp.org/llm2026/llm022026-sensitive-information-disclosure/).
