# Additional patterns: Interpreter, Null Object, Data Mapper

> **Status:** Java 25 Maven module with runnable JUnit tests; actual CI results are tracked in the pull request. These are **three different concepts**, not three missing items from the 22-pattern Refactoring.Guru catalog. The original GoF book includes **Interpreter** as the 23rd pattern.

**Източници и доказателства:** [общата проверена библиография и evidence map](../SOURCES-AND-EVIDENCE.md#2-evidence-map--новите-три-теми). Разграничаваме оригиналния GoF intent, конкретен JDK 25 API contract и нашия собствен test proof.

## Case 1 — Interpreter (GoF): a tiny eligibility-rule grammar

**Problem.** A policy combines elementary predicates, e.g. “country BG AND amount at least 100”. Repeating nested switches in the caller makes the policy hard to represent and test independently. **Naive:** build rules by concatenating SQL or evaluating free-form user expressions (unsafe and unnecessary for this use case).

**Choice.** Model grammar nodes as data: `CountryIs`, `AmountAtLeast`, `And`, `Or`, `Not`. Each node interprets itself against `PolicyContext`. It handles short-circuiting and nested ASTs without parsing arbitrary text.

**Use when:** the rule language is small, stable, and explicitly modeled. **Do not use when:** a few `if` statements suffice or users need a large language with parsing, authoring, error recovery, resource quotas and security hardening. Consider validated configuration or a vetted rules engine instead.

**Gain:** testable compositional rules. **Cost:** node types, grammar maintenance, possible AST depth/CPU hazards if externally constructed. **Limits:** no parser, no persistence and no untrusted-text interpreter; AST nodes are constructed by trusted Java code.

Sources: *Gang of Four*, **Design Patterns** (Addison-Wesley, 1994; Interpreter като 23-и патерн) и [Oracle Java 25 — sealed classes](https://docs.oracle.com/en/java/javase/25/language/sealed-classes-interfaces.html). Това е доверен AST, не string expression evaluator.

Code: [RuleExpression.java](src/main/java/bg/hristomanov/education/patterns/advanced/interpreter/RuleExpression.java) · [positive/negative tests](src/test/java/bg/hristomanov/education/patterns/advanced/interpreter/RuleExpressionTest.java).

## Case 2 — Null Object: optional behavior, not a failed result

**Problem.** An optional telemetry/audit callback creates repeated nullable checks in a service.

**Choice.** Use `AuditSink.NoOp.INSTANCE` as an intentionally disabled **optional** observer; both real and no-op sinks share the interface. `RegistrationService` still requires a non-null dependency.

**Use when:** silent omission is an explicit valid contract, e.g. local non-critical telemetry. **Do not use when:** audit is mandatory for compliance, billing or security, or when “no result” must be explicit. Use `Optional`, sealed states or a failing implementation if absence is meaningful.

**Gain:** simpler code paths. **Cost:** may conceal a missing mandatory behavior if misconfigured. **Test:** real sink receives event; no-op runs; invalid input does not emit an event.

Sources: [Martin Fowler — Special Case](https://martinfowler.com/eaaCatalog/specialCase.html) (сродна идея, не идентична на всички Null Object употреби) и [Oracle Objects.requireNonNull](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/Objects.html).

Code: [AuditSink.java](src/main/java/bg/hristomanov/education/patterns/advanced/nullobject/AuditSink.java) · [RegistrationService.java](src/main/java/bg/hristomanov/education/patterns/advanced/nullobject/RegistrationService.java) · [tests](src/test/java/bg/hristomanov/education/patterns/advanced/nullobject/RegistrationServiceTest.java).

## Case 3 — Data Mapper: boundary between storage representation and domain

**Problem.** A database row stores a monetary amount as EUR cents, while the domain exposes a validated `BigDecimal` amount. Passing rows straight through leaks storage constraints into business code.

**Choice.** `InvoiceMapper` translates both directions and validates precision and overflow. **Use when:** domain and storage have meaningfully different shapes/lifecycles, especially with legacy databases. **Do not use when:** a simple DTO projection or JPA entity already meets the boundary needs.

**Gain:** controlled conversion and invariant protection. **Cost:** mapping boilerplate and keeping schemas synchronized. **Alternatives:** MapStruct for mechanical mapping, JPA mapping or SQL DTO projections for simpler read models. This example is deliberately EUR-only: it is **not** a generalized money model; other currencies may have different minor units.

Sources: [Martin Fowler — Data Mapper](https://martinfowler.com/eaaCatalog/dataMapper.html) за DB/domain decoupling и [Oracle Java 25 — BigDecimal](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/math/BigDecimal.html) за lossless scale / exact conversion.

Code: [InvoiceMapper.java](src/main/java/bg/hristomanov/education/patterns/advanced/mapper/InvoiceMapper.java) · [round-trip, precision/overflow tests](src/test/java/bg/hristomanov/education/patterns/advanced/mapper/InvoiceMapperTest.java).

## Try it

```bash
mvn -pl design-patterns/advanced test
```

### Exercise without answer key

1. Change the eligibility rule to “BG AND (amount ≥ 100 OR not US)”; predict truth values before running tests.
2. Decide whether `NoOp` is acceptable for security access logs. Justify the trade-off and replace it with an explicit failure policy.
3. Introduce a rounding bug into the mapper, write a failing test with EUR 1.001, then restore lossless conversion.

**Review questions:** Which invariants cross the boundary? What happens on invalid input? What simpler alternative works? Why would you **not** use this pattern here?
