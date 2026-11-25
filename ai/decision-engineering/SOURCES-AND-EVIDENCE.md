# Източници, timestamps и граници на доказателствата

Проверено 2026-11-25. Оригиналният видео източник е [Dave Sammut, João Rosa — AI Made Building Cheap Now Decisions Are the Bottleneck](https://www.youtube.com/watch?v=2pSyF44THEc). Прегледан е предоставеният auto-generated SRT (~42 мин); не публикуваме целия transcript. Разпознаването има неточности (напр. Mikado, FinOps, EventStorming). Timestamps са приблизителни диапазони по SRT, не точни slide boundaries.

| Диапазон | Какво действително се обсъжда | Наш урок |
| --- | --- | --- |
| 02:08–07:30 | Организационна действителност, lenses, observations, hypotheses, validation/scale, reversibility, Mikado | 01, 02 |
| 08:24–12:59 | Insurance sales/distribution, interviews и existing compliance capabilities; не всяко решение е AI | 01, 05 |
| 12:59–17:50 | Visible/invisible value/debt; deterministic analysis + LLM refactor + human review | 03 |
| 17:50–21:22 | Team Gamma, DevEx service, AI cost attribution и model choice | 06, 07 |
| 23:47–28:26 | EventStorming-inspired conversations, pain points, SDLC view, unknown logic/test-data gaps | 04, 05 |
| 28:26–31:42 | Invisible architecture work, firefighting/operational/strategic, automation vs learning, ROI още за измерване | 06, 07 |
| 31:42–36:36 | Domain understanding, SMEs/technical teams, enabling team/AI archaeology, four Rs | 04, 06 |
| 36:36–41:55 | Culture, pivot/scale, governance, knowledge diffusion и investment in learning | 06, 07 |

## Допълнителни първични източници и какво потвърждават

- [Equal Experts executive brief, 2026-01-30](https://www.equalexperts.com/blog/ai/ai-in-the-sdlc/): Mikado и IM4 (Imagine/Model/Make/Move/Maintain), аналитичен lens.
- [White paper landing page](https://campaign.equalexperts.com/ai-in-the-sdlc-transformation): signal-driven lenses и reversible moves. **Пълният paper не е прочетен**; не приписваме невидяно съдържание.
- [Dave Sammut, 2026-10-27](https://www.equalexperts.com/blog/our-thinking/yesterdays-good-decisions-may-no-longer-be-todays-best-decisions/): преразглеждане на решения при променена икономика.
- [Team Topologies interaction modeling](https://teamtopologies.com/key-concepts-content/team-interaction-modeling-with-team-topologies): collaboration, X-as-a-Service, facilitating.
- [FinOps for AI](https://www.finops.org/wg/finops-for-ai-overview/) и [Unit Economics](https://www.finops.org/framework/capabilities/unit-economics/): allocation, quality/cost baseline и unit costs.
- [DORA metrics](https://dora.dev/guides/dora-metrics/): service-level throughput и instability, не индивидуални KPI.
- [Patterns of Legacy Displacement](https://martinfowler.com/articles/patterns-legacy-displacement/): incremental modernization и outcome orientation.
- [OWASP prompt injection](https://genai.owasp.org/llm2026/llm012026-prompt-injection/) / [Sensitive Information Disclosure](https://genai.owasp.org/llm2026/llm022026-sensitive-information-disclosure/): security boundaries при ingestion/tools.

## Claim → evidence

Лекторите разказват enterprise consulting cases, не controlled trials. Не пренасяме наблюденията им като universal causal guarantees. Липсва доказан общ ROI или production data от застрахователя. Нашите PR timing, cost numbers, policy fixtures, ADR, thresholds и checklists са synthetic teaching adaptations. [Java lab](lab/README.md) доказва конкретно behavior parity и контрапример за intended policy; не доказва AI performance или production rollout. Няма известен оригинален code repo за тази лекция в предоставения transcript/проверените публични страници. Java/JUnit execution status се отчита в PR/CI, не се измисля.
