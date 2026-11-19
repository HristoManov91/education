# Spring Boot Design Patterns Capstone — report generation and archive

> **Runnable Java 25 / Spring Boot 4.1.1 / JUnit 5 / MockMvc.** Това е teaching application, **не** готов production документен архив. Няма истински PDF, database, auth, durable audit, retry, transaction, idempotency или надеждна доставка. Не тълкувай mock archive като истинска външна система.

## 1. Реален проблем (входен казус)

Един backend endpoint приема report request. Преди запис трябва да:
1. провери задължителни business данни;
2. избере формат без huge switch;
3. генерира безопасно съдържание;
4. архивира през legacy API с различен contract;
5. запише optional audit trace;
6. върне receipt **само когато архивирането е успешно**.

Наивният подход събира всички тези отговорности в един метод. В [LegacyReportWorkflow.java](src/main/java/bg/hristomanov/education/patterns/mastery/legacy/LegacyReportWorkflow.java) `if/else` branch-ове са приемливи за два формата, но при растяща variation смесват policy, transport, validation и lifecycle. Освен това наивният CSV renderer не неутрализира spreadsheet formula payloads.

```text
HTTP POST /api/reports
  → ReportController
    → ReportWorkflow (Facade)
      → ReportValidation (stop-on-reject rule chain)
      → RendererRegistry (Strategy: TEXT | CSV)
      → ArchiveGateway (stable outbound contract)
        → AuditedArchiveGateway (Decorator, does not swallow failures)
          → LegacyArchiveAdapter (Adapter: String -> UTF-8 bytes)
            → LegacyArchiveClient (in-memory fake)
      → ReportReceipt (only after successful archive)
```

## 2. Decision cards (не прилагаме pattern само заради името)

| Решение | Какъв проблем решава / кога | Кога НЕ и по-проста алтернатива | Полза | Цена / trade-off | Proof |
| --- | --- | --- | --- | --- | --- |
| Strategy registry | Много render formats растат независимо от workflow | Само 2 стабилни case-а: `switch` е достатъчен | extension без промяна в facade | повече types, registration/duplicate handling | [unit](src/test/java/bg/hristomanov/education/patterns/mastery/ReportWorkflowTest.java) |
| Rule chain | Order-dependent early reject, без archive side effect | 1–2 validations: inline check/Bean Validation | local rules, short-circuit | ordering и chain complexity | [stop test](src/test/java/bg/hristomanov/education/patterns/mastery/ReportWorkflowTest.java) |
| Adapter | Legacy archives accept `byte[]`, domain uses strings | Съвместим client interface: direct delegation | stable dependency direction | mapping code, error translation | [archive proof](src/test/java/bg/hristomanov/education/patterns/mastery/ReportWorkflowTest.java) |
| Decorator | Optional cross-cutting trace around archive | Не е задължителен durable audit; иначе transactional/outbox design | open/close principle, no service pollution | wrapper ordering и error semantics | [failure test](src/test/java/bg/hristomanov/education/patterns/mastery/ReportWorkflowTest.java) |
| Facade | One business use case coordinates several operations | Trivial pass-through service can be skipped | simple controller, testable orchestration | риск от God Service | [unit + HTTP](src/test/java/bg/hristomanov/education/patterns/mastery/ReportHttpTest.java) |
| Spring DI | Explicit composition of policies, gateway and rules | plain Java `new` is fine in tiny script | replaceable dependencies and isolated tests | container/config cost | [composition root](src/main/java/bg/hristomanov/education/patterns/mastery/ReportWiring.java) |

### Spring ≠ GoF magic

`@Configuration` показва **composition root**, не „Spring implements all patterns for us“. `RendererRegistry`, validators, decorators и adapters са plain Java; unit tests работят без Spring. MVC тестът доказва, че wiring и JSON contract са реални.

## 3. README → code → tests

| Boundary | Код | Доказателство |
| --- | --- | --- |
| Request/receipt | [ReportRequest](src/main/java/bg/hristomanov/education/patterns/mastery/model/ReportRequest.java), [ReportReceipt](src/main/java/bg/hristomanov/education/patterns/mastery/model/ReportReceipt.java) | [HTTP validation](src/test/java/bg/hristomanov/education/patterns/mastery/ReportHttpTest.java) |
| Policy/Strategy | [RendererRegistry](src/main/java/bg/hristomanov/education/patterns/mastery/render/RendererRegistry.java), [CSV renderer](src/main/java/bg/hristomanov/education/patterns/mastery/render/CsvReportRenderer.java) | duplicate registration, unknown format, CSV escaping |
| Chain | [ReportValidation](src/main/java/bg/hristomanov/education/patterns/mastery/validate/ReportValidation.java) | short-circuit test |
| Adapter | [LegacyArchiveAdapter](src/main/java/bg/hristomanov/education/patterns/mastery/archive/LegacyArchiveAdapter.java) | UTF-8 archive contents |
| Decorator | [AuditedArchiveGateway](src/main/java/bg/hristomanov/education/patterns/mastery/archive/AuditedArchiveGateway.java) | success/failure trace, exception propagation |
| Facade | [ReportWorkflow](src/main/java/bg/hristomanov/education/patterns/mastery/ReportWorkflow.java) | [unit proof](src/test/java/bg/hristomanov/education/patterns/mastery/ReportWorkflowTest.java) |
| Refactoring baseline | [LegacyReportWorkflow](src/main/java/bg/hristomanov/education/patterns/mastery/legacy/LegacyReportWorkflow.java) | [characterization & weakness](src/test/java/bg/hristomanov/education/patterns/mastery/LegacyRefactoringBaselineTest.java) |
| HTTP | [Controller](src/main/java/bg/hristomanov/education/patterns/mastery/ReportController.java), [Advice](src/main/java/bg/hristomanov/education/patterns/mastery/ReportErrorAdvice.java) | [MockMvc tests](src/test/java/bg/hristomanov/education/patterns/mastery/ReportHttpTest.java) |

## 4. What fails, and what is guaranteed

| Situation | Result | Guarantee and limit |
| --- | --- | --- |
| blank owner / empty items | 400 | validation runs before archive |
| unsupported format | 400 | no archive call |
| duplicate reference in fake archive | 409 | duplicate is **not** treated as idempotent success |
| downstream archive failure | exception / server error | no successful receipt; audit decorator rethrows |
| CSV quote / leading formula token | escaped / prefixed in this demo | minimal spreadsheet-oriented mitigation, not a complete security library |
| process restart | all stored entries disappear | in-memory fake only; never claim durability |

**Important distinctions:** Decorator ≠ Proxy, archive conflict ≠ idempotency, local audit trace ≠ Transactional Outbox, service Facade ≠ Saga, Spring `singleton` ≠ JVM-global Singleton.

## 5. Reproduce

Requires JDK 25, Maven and network access to resolve dependencies.

```bash
mvn -pl design-patterns/mastery-lab test
mvn -pl design-patterns/mastery-lab spring-boot:run
```

Then POST:

```bash
curl -i -X POST http://localhost:8080/api/reports \
  -H 'Content-Type: application/json' \
  -d '{"reference":"R-42","owner":"Ada","items":["A","B"],"format":"CSV"}'
```

Expected: HTTP **201** with `reference`, `archiveId=LEGACY:R-42`, `format=CSV` and `contentLength`. Second POST with same reference gets **409**, not 201.

## 6. Guided independent work (без готови решения)

1. Преди да гледаш композицията, отвори `LegacyReportWorkflow` и напиши 3 design pressures; състави minimal patch като алтернатива на изцяло нов pattern.
2. Добави `MARKDOWN` strategy. **Не променяй** `ReportWorkflow`. Тест: escaped user content, unsupported format 400, duplicate renderer fails at wiring.
3. Добави втори `ArchiveGateway` (fake file storage), избран при конфигурация. Ако selection logic навлиза в domain, обоснови защо е проблем.
4. Добави `ReportValidation.Rule` за лимит на items. Напиши negative test, че archive client изобщо не е извикан.
5. Провокирай downstream exception и докажи, че няма 201, няма загуба на причината и няма false success audit.
6. Сравни колко files/classes променяш при нов format при legacy и при compositional design; не приемай по-малко LOC за автоматично качество.

### Exit criteria

Обясни **за всяко** използвано решение: проблем, кога да се приложи, кога да НЕ се приложи, полза, загуба, алтернатива, код и тест. Докажи с negative test поне един failure boundary. Не присвоявай "усвоено" само защото лабораторията се компилира.

Свързано: [Mastery Track](../MASTERY-TRACK.md) · [Refactoring Katas](../REFACTORING-KATAS.md) · [Backend Patterns Roadmap](../BACKEND-PATTERNS-ROADMAP.md).
