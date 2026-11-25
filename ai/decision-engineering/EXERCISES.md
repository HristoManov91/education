# Упражнения и диагностика (AI-DE)

Самостоятелен отговор преди проверка. P1 first; запис на оценка само след реална диагностика.

| ID | Задача | Какво проверяваме |
| --- | --- | --- |
| 01 | 20 PR-а, implementation по-бърз, review чака. Попълни experiment card; различи commit-to-prod от idea-to-value. | Hypothesis, baseline, confounders, guardrails |
| 02 | Нов payment handler зад flag, стар handler може да изпрати външен payout. Изготви ADR и rollback rehearsal. | Няма duplicate external side effect; flag ≠ data rollback |
| 03 | Agent намалява complexity, но редактира assertions. Кои gates трябва да откажат PR? | Independent oracle, scope, review |
| 04 | Старият partner bypass е подозрителен. Стартирай lab; класифицирай observed vs intended; премахни bypass само в approved variant. | Characterization, approved policy, negative control |
| 05 | От synthetic interview в глава 05 изведи events и uncertainties; добави противоречив SME факт. | Grounding, domain validation, privacy |
| 06 | Три екипа копират refactor agents. Нарисувай interactions, избери mode и измери dependency wait. | Enabling vs platform vs central queue; diffusion |
| 07 | 10 приети задачи, 30h × 50 EUR + 200 EUR. Колко допълнителни часа унищожават benefit? Какво ако няма приети задачи? | Unit economics, break-even, failure accounting |

## Критерии за проверка (след собствен отговор)

01: ограничен experiment, compare similar items, цел и stop; не оптимизираш по LOC. 02: idempotency, compatibility, no side effects in shadow, state recovery. 03: отделна frozen acceptance spec, negative tests, independent reviewer. 04: parity не е бизнес correctness; доказателство за промяна по owner-approved fixture. 05: unknown ≠ fact; разрешение и redaction. 06: time-boxed collaboration → service само при stable contract; learning-rich work не се автоматизира сляпо. 07: 300 EUR / 50 EUR/h = 6h до break-even, след това negative; denominator zero → undefined, cost остава.

## Оригинални източници

[Source map](SOURCES-AND-EVIDENCE.md) и [lab](lab/README.md).
