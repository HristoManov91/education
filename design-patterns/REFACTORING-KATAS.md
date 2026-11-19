# Design Patterns — Refactoring Katas (чети казуса, не отгатвай името)

> Ката = първо **behavioral characterization tests**, после минимален refactor, после negative/edge tests. Сложността е реален разход. Ако едно `if` е достатъчно, може да е правилното решение.

## KATA-01 — Разклоняваща се политика за export (P1)

```java
String export(Report r, String format) {
  if ("CSV".equals(format)) return csv(r);
  if ("TEXT".equals(format)) return text(r);
  throw new IllegalArgumentException("format");
}
```

Новите формати може да нараснат от 2 на 15, но днес са 2. Опиши **кога оставяш този код** и кога въвеждаш extension point. Работещото упражнение е [ReportWorkflow](mastery-lab/src/main/java/bg/hristomanov/education/patterns/mastery/ReportWorkflow.java), а простият baseline е [LegacyReportWorkflow](mastery-lab/src/main/java/bg/hristomanov/education/patterns/mastery/legacy/LegacyReportWorkflow.java). Добави нов format, без да редактираш успешния use-case flow. Тест: unsupported format ≠ silently default.

## KATA-02 — Legacy courier API навсякъде (P1)

```java
var legacy = courier.quote(zip, grams);
return new Quote(legacy.getPriceStotinki() / 100, legacy.getCode());
```

Умишлени проблеми: `int` division, чужд модел, разпилян error mapping. Започни от [Adapter](structural/README.md). Не добавяй retry, ако не познаваш idempotency/error contract. Tests: unit conversion 250 cents → 2.50, negative/unknown provider result, caller не познава чуждите DTOs.

## KATA-03 — Валидация след side effect (P1)

```java
archive.save(report);
if (report.owner() == null) throw new IllegalArgumentException();
```

Премести правилата преди архивиране. Покажи with tests, че на invalid input архивът не се извиква. Ако rules имат priority, определи реда изрично. Изход: [ReportValidation](mastery-lab/src/main/java/bg/hristomanov/education/patterns/mastery/validate/ReportValidation.java) и [stop-on-reject test](mastery-lab/src/test/java/bg/hristomanov/education/patterns/mastery/ReportWorkflowTest.java).

## KATA-04 — Audit wrapper крие грешки (P1)

```java
try { return remote.save(body); }
catch (Exception ex) { audit("failed"); return "success"; }
```

Промени wrapper contract-а така, че failure не е успешна операция. Тест: audit failure event, exception propagation, **никакъв** success receipt. Решение за реално compliance auditing ще изисква durable storage, не in-memory trail.

## KATA-05 — Boolean-driven lifecycle (P1)

```java
boolean ready, signed, cancelled;
if (ready && !signed && !cancelled) { ... }
```

Дефинирай валидните състояния/преходи. Сравни `enum + switch`, sealed records и State hierarchy. Напиши тест за забранен преход. Pattern е оправдан, ако **поведението** по state варира достатъчно, а не защото има enum.

## KATA-06 — Singleton с tenant state (P1)

```java
static final Flags INSTANCE = new Flags();
INSTANCE.setCurrentTenant(tenant);
```

Направи concurrent scenario с tenant A/B, докажи leakage, предложи explicit request context/immutable policy. Не пренасяй tenant state в singleton Spring bean. Тестът трябва да се проваля при shared mutable state.

## KATA-07 — Неизмерена оптимизация (P2)

Колега предлага Flyweight за 300 малки objects, а друг предлага Prototype вместо constructor. Какво трябва да **измерим**, кои references трябва да останат independent, и кога да отхвърлим и двете идеи? Налични: [Flyweight](structural/flyweight/README.md), [Prototype](creational/prototype/README.md).

## KATA-08 — Правила, десетични числа и граници (P2)

Три отделни решения:
- eligibility `BG && amount >= 100`: кога AST Interpreter, кога обикновена predicate?
- optional audit: кога Null Object, кога грешка в конфигурацията?
- `amountCents -> BigDecimal`: кога Data Mapper и какво е lossless conversion?

Започни от [Advanced examples](advanced/README.md), добави negative tests (invalid AST, compliance no-op, overflow/precision). Не въвеждай DSL parser, ORM или rule engine без ново изискване.

## Общ rubric

| Доказателство | Успех |
| --- | --- |
| Correctness | happy + negative + boundary tests доказват contract |
| Design | ясно ownership/variation, минимален coupling |
| Alternatives | поне един по-прост вариант и причина да бъде избран/отхвърлен |
| Trade-offs | 1 реален разход и 1 реална полза, без измислени benchmark числа |
| Communication | точно назовани semantics, не само имена на патерни |

**Команди:** `mvn -pl design-patterns/advanced test`; `mvn -pl design-patterns/mastery-lab test`; или `mvn -f design-patterns/pom.xml test` (JDK 25).
