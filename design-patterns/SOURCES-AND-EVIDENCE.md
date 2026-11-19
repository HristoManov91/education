# Design Patterns — проверени източници и проверими доказателства

> **Източниците** обосновават понятията и trade-offs. **Тестовете** проверяват конкретната ни Java/Spring имплементация. Нито документ, нито green CI доказва лично усвоено знание или production readiness.

## 1. Кои източници считаме за authoritative

| Приоритет | Източник | За кои твърдения го използваме | Ограничения |
| --- | --- | --- | --- |
| Първичен (класически) | **Gamma, Helm, Johnson, Vlissides**, *Design Patterns: Elements of Reusable Object-Oriented Software*, Addison-Wesley, 1994 | Original GoF pattern intent и **23** патерна, включително Interpreter | Не дефинира Spring/JDK 25 semantics; не налагаме структура от 1994 г. върху всяка съвременна задача |
| Каноничен учебен каталог | [Refactoring.Guru — Design Patterns](https://refactoring.guru/design-patterns) | Pattern intent/applicability, comparisons, **22 patterns в техния каталог** | Не включва Interpreter в каталога от 22; не е официална Java/Spring документация |
| Автор/първоизточник за enterprise patterns | [Martin Fowler — Patterns of Enterprise Application Architecture](https://martinfowler.com/eaaCatalog/) | Data Mapper, Special Case (близък на Null Object) и граници между домейн и storage | Обсъжда архитектурни trade-offs, не е спецификация на MapStruct/JPA |
| Официална Spring документация | [Spring Framework — Dependency Injection](https://docs.spring.io/spring-framework/reference/core/beans/dependencies/factory-collaborators.html), [Bean Scopes](https://docs.spring.io/spring-framework/reference/core/beans/factory-scopes.html), [AOP Proxying](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html) | DI, bean lifecycle, scope, self-invocation и инфраструктурни wrappers | Spring аналогиите не са непременно точни textbook GoF имплементации |
| Официална Spring Boot документация | [Testing Spring Boot Applications](https://docs.spring.io/spring-boot/reference/testing/spring-boot-applications.html) | `@SpringBootTest`, `@AutoConfigureMockMvc` и MVC tests | **MockMvc не стартира реален network server**; това не е E2E test |
| Официална Java 25 документация | [BigDecimal](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/math/BigDecimal.html), [Objects](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/Objects.html), [Sealed types](https://docs.oracle.com/en/java/javase/25/language/sealed-classes-interfaces.html) | Decimal precision, exact conversion, null rejection, closed AST hierarchies | Нашият mapper е EUR-only; Java sealed types не заместват автоматично цял interpreter/rules engine |
| Security research reference | [OWASP CSV Injection](https://community.owasp.org/attacks/CSV_Injection) | Spreadsheet formula injection, escaping и дългосрочни caveats | **Няма универсален escape** за всички spreadsheet apps; apostrophe+quotes не е надеждна защита при Excel save/reopen |

### Границата на доказателствата

- **Оригинална концепция**: книги + Martin Fowler + Refactoring.Guru.
- **Framework semantics**: Spring и Oracle documentation, не блог без version context.
- **Изпълнимо доказателство**: собствен JUnit/MockMvc source и резултат от GitHub Actions.
- **Production guarantees**: **не** са доказани. In-memory archive, local audit, CSV example и малки AST правила са образователни модели.

## 2. Evidence map — новите три теми

| Тема | Основа | Конкретен код и negative tests | Изрично не твърдим |
| --- | --- | --- | --- |
| Interpreter (23-и GoF) | GoF book + [Java 25 sealed types](https://docs.oracle.com/en/java/javase/25/language/sealed-classes-interfaces.html) | [RuleExpression](advanced/src/main/java/bg/hristomanov/education/patterns/advanced/interpreter/RuleExpression.java), [JUnit](advanced/src/test/java/bg/hristomanov/education/patterns/advanced/interpreter/RuleExpressionTest.java) | Няма string parser, untrusted-code execution, limits за произволно дълбок AST или rules engine |
| Null Object | [Fowler — Special Case](https://martinfowler.com/eaaCatalog/specialCase.html), Java `Objects.requireNonNull` | [AuditSink / NoOp](advanced/src/main/java/bg/hristomanov/education/patterns/advanced/nullobject/AuditSink.java), [JUnit](advanced/src/test/java/bg/hristomanov/education/patterns/advanced/nullobject/RegistrationServiceTest.java) | NoOp не е допустим за **задължителен** audit/security/billing |
| Data Mapper | [Fowler — Data Mapper](https://martinfowler.com/eaaCatalog/dataMapper.html), [Java 25 BigDecimal](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/math/BigDecimal.html) | [InvoiceMapper](advanced/src/main/java/bg/hristomanov/education/patterns/advanced/mapper/InvoiceMapper.java), [JUnit precision/overflow](advanced/src/test/java/bg/hristomanov/education/patterns/advanced/mapper/InvoiceMapperTest.java) | Това е mapping на **persistence-shaped row**, не доказан actual database/ORM integration |

## 3. Evidence map — Spring Boot capstone

| Design decision | Източник | Доказателство/тест |
| --- | --- | --- |
| Вариращ render algorithm → Strategy | [Refactoring.Guru — Strategy](https://refactoring.guru/design-patterns/strategy) | [Registry + unit](mastery-lab/src/test/java/bg/hristomanov/education/patterns/mastery/ReportWorkflowTest.java) (вкл. duplicate strategy и unsupported format) |
| Несъвместим legacy contract → Adapter | [Refactoring.Guru — Adapter](https://refactoring.guru/design-patterns/adapter) | [LegacyArchiveAdapter](mastery-lab/src/main/java/bg/hristomanov/education/patterns/mastery/archive/LegacyArchiveAdapter.java) + UTF-8 content assertion |
| Validation stops on rejection → Chain | [Refactoring.Guru — Chain of Responsibility](https://refactoring.guru/design-patterns/chain-of-responsibility) | [short-circuit test](mastery-lab/src/test/java/bg/hristomanov/education/patterns/mastery/ReportWorkflowTest.java) |
| Archive instrumentation → Decorator | [Refactoring.Guru — Decorator](https://refactoring.guru/design-patterns/decorator) | Audit success/failure events + propagation test |
| Simple use-case boundary → Facade | [Refactoring.Guru — Facade](https://refactoring.guru/design-patterns/facade) | [ReportWorkflow](mastery-lab/src/main/java/bg/hristomanov/education/patterns/mastery/ReportWorkflow.java) + HTTP 201/400/409 proof |
| Explicit wiring → DI/composition | [Spring dependency resolution](https://docs.spring.io/spring-framework/reference/core/beans/dependencies/factory-collaborators.html) | [ReportWiring](mastery-lab/src/main/java/bg/hristomanov/education/patterns/mastery/ReportWiring.java) and [MockMvc](mastery-lab/src/test/java/bg/hristomanov/education/patterns/mastery/ReportHttpTest.java) |
| Formula-like CSV cells | [OWASP CSV Injection](https://community.owasp.org/attacks/CSV_Injection) | [CsvReportRenderer](mastery-lab/src/main/java/bg/hristomanov/education/patterns/mastery/render/CsvReportRenderer.java) + escaping test. **Only a demonstrational mitigation**, not a security guarantee |
| External HTTP behavior at controller level | [Spring Boot Testing](https://docs.spring.io/spring-boot/reference/testing/spring-boot-applications.html) | [MockMvc tests](mastery-lab/src/test/java/bg/hristomanov/education/patterns/mastery/ReportHttpTest.java); no network listener in tests |

## 4. Какво се проверява при review/CI

```bash
# Java 25 / Maven; source-level pattern assertions and Spring MVC integration
mvn -B --no-transfer-progress -f design-patterns/pom.xml test
```

[Targeted GitHub Actions workflow](../.github/workflows/design-patterns-mastery.yml) изпълнява reactor-а. Пълният workflow на Education остава отделна проверка. При изменение на branch head старите зелени проверки **не** доказват новия commit: изискват се нови зелени checks, съвместим `main`, review на новия diff.

**Source quality rule:** външен линк не е доказателство за performance numbers или production-grade semantics. Ако такова твърдение се появи в урок, добави конкретна собствена измерима проверка или изрично го маркирай като хипотеза.
