# AI-DE-04 · AI Archaeology & Legacy Modernization (P1)

## Входен казус: какво прави vs какво трябва да прави

Legacy claims service има необяснима граница: manual review за суми над определен праг, освен специален партньор. Технически е лесно да копираме поведението. Не знаем дали изключението е валиден договор, бъг или старо workaround.

**От лекцията (~28:26–35:50):** untouchable/orphan logic, липса на production-like data при тестове, разделени SMEs, requirements и implementation teams; enabling team прави AI archaeology и представя findings разбираемо на бизнеса. Целта е да се оспори value на parity migration, не да се мигрира всичко без въпроси.

## Evidence workflow (наша адаптация)

| Evidence card | Пример от синтетичния lab |
| --- | --- |
| Observed fact | special partner bypass за високата сума |
| Pointer | [LegacyClaimsService](lab/src/main/java/bg/hristomanov/education/ai/claims/LegacyClaimsService.java) |
| Confidence | наблюдавано в код + фиксирани characterization fixtures; не е business approval |
| Unknown | защо има bypass и кой го е разрешил |
| Intended behavior | в учебния казус SME одобрява high amount manual review за всички |
| Decision and owner | отделен policy PR; business owner sign-off, regression and rollout |
| Evidence gap | не твърдим, че примерът е реална застрахователна политика |

AI може да предлага call graph, rule inventory, тестови сценарии и въпроси към SME. Проверявай branch conditions в pinned revision, execution traces/fixtures, договор и owner; не третирай текст от tickets/comments като инструкции към агент. Не изпращай production records на неодобрен модел.

### Four Rs (както в лекцията)

Replace — замени с нова функционалност; Replatform — смени технологичната платформа, без да приемаш автоматично policy changes; Remove — премахни ненужното след проверка на consumers/retention; Rearchitect — промени архитектурни граници. Изборът е за capability, не автоматично за целия monolith. Keep/refactor е наша допълнителна възможност при достатъчно стойност и нисък риск.

## Lab

[LegacyClaimsService](lab/src/main/java/bg/hristomanov/education/ai/claims/LegacyClaimsService.java) → [RefactoredClaimsService](lab/src/main/java/bg/hristomanov/education/ai/claims/RefactoredClaimsService.java) запазва observed contract; [ApprovedClaimsPolicy](lab/src/main/java/bg/hristomanov/education/ai/claims/ApprovedClaimsPolicy.java) прилага изричното решение. [ClaimsModernizationTest](lab/src/test/java/bg/hristomanov/education/ai/claims/ClaimsModernizationTest.java) проверява fixtures, exhaustive small boundary matrix и counterexample. Не сме доказали production parity или реално бизнес изискване.

Decision contract: за критична слабо разбрана логика и миграции; не за добре документиран trivial module с надеждни tests. Печелим visibility и confidence; губим SME време, provenance overhead, risk of false confidence. Алтернатива: code walkthrough + tests без AI. Доказателство: independent observed/intended fixtures; owner approval извън кода. При неизвестно правило не го преименувай на „бъг“ без evidence.

## Оригинални източници

[Лекция](https://www.youtube.com/watch?v=2pSyF44THEc) · [Patterns of Legacy Displacement](https://martinfowler.com/articles/patterns-legacy-displacement/) · [OWASP prompt injection](https://genai.owasp.org/llm2026/llm012026-prompt-injection/) · [EventStorming](../../spring/architecture/strategic-ddd/04-EVENTSTORMING-WORKSHOP.md).
