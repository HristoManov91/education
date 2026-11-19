# Design Patterns Mastery Track — от разпознаване към избор и доказателство

> **P2 curriculum (P1 когато пряко подобрява текущ production code).** Каталогът [22/22](README.md) вече е готов. Това е **нов учебен слой**, а не пренаписване или дублиране на съществуващите реализации. Готов код ≠ лично усвоени знания. Отворени са диагностиките; **не записваме успех без самостоятелно решение**.

## Какъв проблем решава тази програма

Да знаеш, че Strategy използва interface, не е достатъчно: можеш да поставиш Strategy върху прост `switch` и да усложниш кода. В production трябва да видиш кое варира, кой контролира жизнения цикъл, какъв failure contract трябва да остане стабилен и дали по-простото решение е по-добро.

```text
симптом в production code
  → дефинирай variation/ownership
  → предложи прост вариант БЕЗ pattern
  → сравни 1–2 patterns
  → избери / отхвърли
  → рефакторирай без промяна на contract
  → negative/edge тест
  → код ревю и trade-offs
```

**Режим:** 20–30 минути на микроурок; първо 1 въпрос, след оценка спираме и питаме за продължаване. При вече стабилно знание 5–10 минути диагностика са достатъчни. Следва recall D+1/D+7/D+30 според [ASSESSMENT-PROTOCOL](../docs/learning/ASSESSMENT-PROTOCOL.md).

## Phase 0 — Entry diagnostic

Отвори [ASSESSMENT-CASES.md](ASSESSMENT-CASES.md). 3–6 адаптивни въпроса или 1–2 code review казуса, **без подсказване преди отговор**. Запис в [PROGRESS.md](../docs/learning/PROGRESS.md), само за реално оценените микро-теми. Точен технически отговор и професионална терминология се оценяват отделно. Ако имаме само един въпрос: частична оценка, не „цялата категория усвоена“.

## Phase 1 — Core (най-честите Java/Spring решения)

| ID | Pattern | Сигнал | Проверка |
| --- | --- | --- | --- |
| DP-01 | Strategy | growing pricing/report format logic | нова policy без редакция на orchestration |
| DP-02 | Adapter | legacy API протича в domain | изолиране на чуждия договор, mapping failure |
| DP-03 | Facade | controller управлява 4 subsystems | един use-case boundary, без God Service |
| DP-04 | Builder | много optional arguments и invariants | invalid build не успява |
| DP-05 | Observer | independent local reactions | synchronous vs transactional event semantics |
| DP-06 | Chain of Responsibility | validation/filter pipeline | stop-on-reject и порядок |
| DP-07 | Decorator | optional cross-cutting layer | failure се propagatе-ва, ред на wrappers |
| DP-08 | Proxy | controlled access, caching, Spring AOP | self-invocation и contract limitations |

**Материал:** [Creational](creational/README.md) · [Structural](structural/README.md) · [Behavioral](behavioral/README.md) · [Spring mapping](SPRING-MAPPING.md). **Checkpoint:** `KATA-01`–`KATA-04` от [Refactoring Katas](REFACTORING-KATAS.md).

## Phase 2 — Intermediate (lifecycle, construction и component trees)

| ID | Pattern | Критичен въпрос |
| --- | --- | --- |
| DP-09 | State | кой владее lifecycle transitions; не е ли enum switch по-ясен? |
| DP-10 | Template Method | inheritance hook или injected Strategy? |
| DP-11 | Command | action as object, или просто DTO/CQRS command? |
| DP-12 | Factory Method | extension point от subclass ли избира product-а? |
| DP-13 | Abstract Factory | имаме ли истинско семейство от съвместими products? |
| DP-14 | Composite | как ограничаваме цикли/невалидна структура на дървото? |
| DP-15 | Singleton | class-level identity или Spring bean scope? |

**Checkpoint:** `KATA-05`–`KATA-06`. Обясни разликите [Strategy vs State, Factory variants, Proxy vs Decorator](PATTERN-COMPARISONS.md).

## Phase 3 — Advanced / rarely needed, но полезни за четене на сложен код

| ID | Pattern | Необходимо доказателство |
| --- | --- | --- |
| DP-16 | Prototype | deep-copy isolation и owner на mutable references |
| DP-17 | Iterator | lazy vs eager traversal, duplicate page/stop semantics |
| DP-18 | Bridge | два независими axes на промяна |
| DP-19 | Mediator | coordination без God Object и compensation boundaries |
| DP-20 | Memento | snapshot consistency/undo без leaking private state |
| DP-21 | Visitor | стабилна hierarchy, сравнение със sealed + switch |
| DP-22 | Flyweight | измерен object count и споделено immutable state |
| DP-23 | Interpreter (GoF extra) | explicit grammar/AST vs parsing arbitrary text |
| DP-24 | Null Object (application idiom) | no-op е допустим договор, не скрит error |
| DP-25 | Data Mapper (application pattern) | точно преобразуване на модели/единици и invariants |

**Изпълними допълнения:** [Advanced patterns](advanced/README.md). **Checkpoint:** `KATA-07`–`KATA-08`.

## Phase 4 — Capstone: комбинирай patterns, вместо да ги прилагаш на сила

[Spring Boot Mastery Lab](mastery-lab/README.md) е интегриран, изпълним пример. Наивен `LegacyReportWorkflow` е оставен като baseline, а `ReportWorkflow` разделя responsibilities. Има реален HTTP request → validation → strategy registry → adapter → audit decorator, unit + MockMvc integration tests.

**Задача:** получаваш заявка за нов format `MARKDOWN` + нов archive provider. Първо предложи най-простото решение. Сравни измененията в наивния и композиран design, върни error contract, отрицателните тестове и оценка на цена (брой нови classes, coupling, operational и debugging cost). **Без** ненужни messaging/DB/transaction abstractions в този lab.

## Definition of Done — за всеки микроурок

- [ ] Посочен е **реален проблем**, naive вариант и точният failure/design pressure.
- [ ] Мога да предложа решение **без** pattern и да кажа защо може да е достатъчно.
- [ ] Мога да обоснова избора и кога **не** го използвам.
- [ ] Мога да посоча конкретна полза, цена и алтернатива.
- [ ] Разбирам ownership, lifecycle, error/transaction semantics.
- [ ] Имам изпълнимо упражнение и проверен negative/edge case.
- [ ] Мога да обясня решението с точна инженерна терминология.
- [ ] Резултатът е записан в PROGRESS **само след реален самостоятелен отговор**.

**Категорично:** написан README, компилиращ код, минаващ CI или отметка в roadmap не означават, че човекът е преминал диагностиката.
