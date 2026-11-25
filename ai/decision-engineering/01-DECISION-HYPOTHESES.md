# AI-DE-01 · Decisions & Hypothesis-Driven Engineering (P1)

## Казус и mental model

Екипът съкращава implementation от 3 дни на 1 ден, но PR чака 4 дни и approval още 2. В синтетичен последователен поток 3+4+2=9 дни, след промяната 1+4+2=7 дни, не 3× ускорение на цялата доставка. Реалните етапи могат да се застъпват; измери elapsed time, не просто сумата. Не заключавай, че approval е причината за всеки бавен PR: провери rework, dependency и queue.

**От лекцията (~03:44–07:30):** lenses → observations → hypotheses → validate/invalidate и scale или change; документираният operating model може да не съвпада с реалната работа. Mikado effect: локална промяна засяга свързани дейности. Авторите не обещават универсална рецепта.

**От executive brief:** IM4 = Imagine, Model, Make, Move, Maintain. Това е аналитична перспектива, не задължителен линеен процес. Същия поток разглеждаме от бизнес value, архитектура, feedback, team interactions, governance и stability.

## Практически experiment card (наша адаптация, non-runnable)

| Поле | Попълнен пример |
| --- | --- |
| Observation | За 20 последователни сходни PR-а има много часове чакане на review. |
| Hypothesis | Малки PR-и + назначен reviewer ще намалят review queue. |
| Alternative | Без AI: по-малък WIP limit и по-ясни acceptance criteria. |
| Baseline | p50/p90 review wait, end-to-end lead time, rework rate, escaped defects; отбележи размера и риска. |
| Experiment | Две седмици, една услуга, ограничен тип промяна; сравни сходни задачи, документирай confounders. |
| Success/guardrails | Примерна цел: p90 review wait -25%; без влошаване на escaped defects/rework; минимум наблюдения предварително. |
| Stop / pivot | Нарушение на safety guardrail или недостатъчни данни → stop/удължи по договорен критерий, не обявявай успех. |
| Owner / revisit | Team lead + reviewer; преглед след пилота; при успех rollout поетапно. |

**Bad:** lines generated, commits или token consumption като proxy за value. **Good:** бизнес outcome, queue wait, end-to-end flow, качеството на промените. DORA change lead time започва от commit до production, не от идея; отделно измерваме discovery-to-value. Не сравнявай хора по DORA metrics.

## Decision contract

Проблем: локална оптимизация мести bottleneck. Кога: неясна причина и скъпа промяна. Кога не: еднозначен инцидент, изискващ незабавно ограничаване на щетите, или тривиална поправка. Печелим evidence и по-малко rework; губим време за измерване, възможно объркване от малки samples. Алтернатива: кратка директна поправка с наблюдение. Доказателство: попълни card, изпълни pilot, запиши baseline и резултат; нашият пример е хипотетичен, не измерване.

Checklist: имаме наблюдение, не само мнение; измерим outcome; alternative без AI; owner; stop condition; reversible scope; решение follow-up. Виж [exercises](EXERCISES.md) и [capacity](../../system-design/02-CAPACITY-PERFORMANCE.md).

## Оригинални източници

[Лекция](https://www.youtube.com/watch?v=2pSyF44THEc) · [Executive brief / IM4](https://www.equalexperts.com/blog/ai/ai-in-the-sdlc/) · [DORA metrics](https://dora.dev/guides/dora-metrics/).
