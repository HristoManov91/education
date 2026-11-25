# AI-DE-03 · AI-Assisted Refactoring & Technical Debt (P1)

## Проблемът

Сложен claims service прави следващата feature скъпа. Static analyzer маркира complexity; AI може да раздроби класа, но по-нисък score не доказва запазване на бизнес поведение. Не бъркай refactor (observable behavior запазено) с bug fix/feature.

**Лекция (~12:59–18:34):** видима/невидима стойност и отрицателни ефекти; екипът приоритизира технически дълг чрез static analysis, автоматизирани backlog items, LLM-assisted prioritization/refactor/tests, повторен анализ и human review. Примерът се отнася за вътрешна организация на код, не за промяна на domain/API.

### Pipeline (наша адаптация)

1. Детерминиран scan (SonarQube/CodeScene като сигнали, не единствен oracle). Добави churn, defect history, business impact и dependencies.
2. Създай малка задача: contract, files, forbidden changes, independent tests, budget. Ограничени permissions; AI може да предложи план, но не да променя quality rules.
3. Преди refactor фиксирай observed outputs чрез characterization tests; отделно business acceptance tests се одобряват от domain owner.
4. Промяна в малък diff, без API/schema/behavior changes. Run targeted tests и необходимите integration/contract checks. Re-scan; не затваряй само по score.
5. Human reviewer проверява semantics, security, performance, test quality, test oracle independence. Merge по съществуващия процес.

Bad: auto-fix всичко и auto-merge при green CI; агентът може едновременно да промени имплементация и expected output. Good: frozen fixtures, reviewer-owned acceptance criteria, negative controls и малки PR-и. В [lab](lab/README.md) old/refactored са равни по observed outputs, но старото правило не е автоматично правилно. [JUnit tests](lab/src/test/java/bg/hristomanov/education/ai/claims/ClaimsModernizationTest.java).

Decision contract: механични промени с ясни граници, повторяеми tests и rollback → подходящи. Не за неизвестни business policies, security-critical redesign или промени без oracle. Печелим throughput и consistent review; губим review време, токени, риск от regression и noisy backlog. По-просто: IDE refactor/OpenRewrite/детерминиран script, когато трансформацията е формализируема. Доказваме behavior preservation и негативен контрол; не претендираме за Sonar integration или LLM run.

Checklist: refactor ≠ policy change; baseline; независими assertions; data/API invariants; manual approval; new metrics; commit separation. Виж [AI testing](../../spring/testing/ai-assisted-testing/README.md), [agentic development](../agentic-development/README.md).

## Оригинални източници

[Лекция](https://www.youtube.com/watch?v=2pSyF44THEc) · [Executive brief](https://www.equalexperts.com/blog/ai/ai-in-the-sdlc/) · [DORA metrics](https://dora.dev/guides/dora-metrics/).
