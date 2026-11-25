# AI-DE-05 · AI-Assisted Discovery & EventStorming (P2)

## Казус и техника

Claims lead time е бавен; архитектът подозира legacy database, SME разказва за чакане на документи и неясни approvals. От едно наблюдение не следва root cause.

**От лекцията (~10:13–12:59, 23:47–28:26):** guided conversations, recordings/workshop artifacts, LLM synthesis и EventStorming-inspired lens извличат events и pain points; върни ги към хората за shared understanding. IM4 view на същия процес може да открие bottleneck на различно място. Използването на AI не означава заместване на domain discussion.

### Възпроизводим workshop (non-runnable)

1. С SME, product owner, developer и operations формулирай въпроси за success/failure paths. При реален запис осигури разрешение, data minimization и retention; тук използваме измислена бележка.
2. Бележка: „Иск е подаден; служителят чака документ; при липса на документ праща напомняне; след комплектоване преценява сумата; при спор търси SME.“
3. Извлечи candidates: ClaimSubmitted, DocumentRequested, DocumentReceived, ClaimAssessed, ManualReviewRequested. Това са предложения, не потвърдени domain events.
4. Накарай AI да върне structured table: candidate, exact evidence pointer, actor, uncertainty, missing path, question. Не добавяй неподкрепени business rules.
5. SME коригира имена/причинност; маркирай disagreements и owner; отдели business event от технически log. Измери wait time и test data gap, не само време за transcription.
6. От избрания bottleneck → [hypothesis card](01-DECISION-HYPOTHESES.md). Ако проблемът е approval ownership, не предлагай нова база по подразбиране.

Bad: автоматично генерирана EventStorming диаграма се приема за ground truth. Good: всеки важен event има evidence и validation; липсата на evidence е явна. AI suggestion може да бъде грешна или malicious.

Decision contract: подходящо при много artifacts/разпределена експертиза; не при чувствителни данни без одобрена среда или разговор, който може да се реши за минути. Печелим synthesis speed и visibility; губим validation време, риск от изкривяване и privacy. Алтернатива: фасилитатор + ръчни sticky notes. Доказателство: повтори със synthetic note, проверка срещу текста, добави липсващ failure path като въпрос, а не факт. Виж [EventStorming workshop](../../spring/architecture/strategic-ddd/04-EVENTSTORMING-WORKSHOP.md) и [exercises](EXERCISES.md).

## Оригинални източници

[Лекция](https://www.youtube.com/watch?v=2pSyF44THEc) · [IM4](https://www.equalexperts.com/blog/ai/ai-in-the-sdlc/) · [OWASP sensitive disclosure](https://genai.owasp.org/llm2026/llm022026-sensitive-information-disclosure/).
