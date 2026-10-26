# 1.5 — Spring Modulith: executable boundaries и vertical module tests

> **P1 / Java 25 · Spring Boot 4.1.1 · Spring Modulith 2.1.1 · 30–40 минути + executable lab**
>
> Source: [Devoxx Belgium 2026 — Mario Klaver, 29:00–37:30](https://youtu.be/v86SfHA5ISw?t=1740). Следвай първо [1.4 EventStorming](04-EVENTSTORMING-WORKSHOP.md). Това е надграждане на [Strategic DDD](01-STRATEGIC-DDD-AND-BOUNDARIES.md), не още едно общо обяснение на bounded contexts.

## 1. Реалният проблем

В [EventStorming workshop](04-EVENTSTORMING-WORKSHOP.md) открихме три business capabilities: Health Assessment, Dangerous Goods Assessment и Operational Clearance.

**BAD A:** правим три микросървиса, въпреки че един екип носи отговорност, не се налага независим scaling и всяка промяна изисква cross-service coordination.

**BAD B:** правим един Spring Boot процес, но всички classes могат да import-ват чужд internal service/repository. Това е **unstructured monolith**, а не модулна архитектура.

**GOOD:** един deployment, но с изрични **module APIs**, недостъпни internal packages, allowed dependencies и module-level testing, така че CI да открива architectural drift.

**EventStorming открива границите, Spring Modulith проверява техните зависимости в кода.**

## 2. Нашата изпълнима архитектура

[modulith-lab/README.md](modulith-lab/README.md) съдържа изпълнимия Java code, package map и тестовете.

```text
modulith-lab (ONE Maven artifact / ONE Spring Boot deployable)
└── bg.hristomanov.education.portmodulith
    ├── HarbourModulithApplication
    ├── health/
    │   ├── HealthAssessmentOperations            [public module API]
    │   ├── events/HealthAssessed                  [named interface "events"]
    │   └── internal/HealthAssessmentService      [not an exported module API]
    ├── dangerousgoods/
    │   ├── DangerousGoodsOperations
    │   ├── events/DangerousGoodsAssessed          [named interface "events"]
    │   └── internal/DangerousGoodsAssessmentService
    └── clearance/
        ├── OperationalClearanceQueries
        ├── ClearanceStatus
        ├── package-info.java                     [allowed event dependencies]
        └── internal/ClearanceProjection
```

**Локален module contract:**

```text
health.events ─────────────┐
                           ▼
                    clearance.internal
                           ▲
dangerousgoods.events ─────┘

Forbidden: clearance → health.internal / dangerousgoods.internal
```

Това **не е реалният технологичен стек или бизнес алгоритъм на Port of Rotterdam**. Учебното правило е, че clearance е CLEARED само когато **и двете** проверки са positive; при false е DENIED, а при непълна информация — PENDING.

## 3. Named interfaces и позволени зависимости

[clearance/package-info.java](modulith-lab/src/main/java/bg/hristomanov/education/portmodulith/clearance/package-info.java):

```java
@ApplicationModule(
    allowedDependencies = {"health::events", "dangerousgoods::events"}
)
package bg.hristomanov.education.portmodulith.clearance;
```

[health/events/package-info.java](modulith-lab/src/main/java/bg/hristomanov/education/portmodulith/health/events/package-info.java):

```java
@NamedInterface("events")
package bg.hristomanov.education.portmodulith.health.events;
```

Публичният Java modifier на class **не означава автоматично**, че class е публичен **module API**. Ако чужд модул импортира `health.internal.HealthAssessmentService`, Java може да компилира, но архитектурната проверка трябва да го отхвърли.

[HarbourModulithArchitectureTest.java](modulith-lab/src/test/java/bg/hristomanov/education/portmodulith/HarbourModulithArchitectureTest.java):

```java
@Test
void modulesHaveNoCyclesAndCrossModuleImportsUseExplicitInterfaces() {
    ApplicationModules.of(HarbourModulithApplication.class).verify();
}
```

Това проверява реалните module dependencies: cycles, unauthorized internal references и explicit allowedDependencies. **Не проверява** дали разделението съответства на бизнеса, или дали async processing е durable.

**Break-it exercise:** добави в [ClearanceProjection](modulith-lab/src/main/java/bg/hristomanov/education/portmodulith/clearance/internal/ClearanceProjection.java) constructor parameter с type `HealthAssessmentService` от чуждия internal package. Очаква се `ApplicationModules.verify()` да сигнализира нарушение. Не оставяй такъв import в main.

## 4. @ApplicationModuleTest — vertical slices

За разлика от MVC slice (`@WebMvcTest`), `@ApplicationModuleTest` bootstrap-ва **само конкретния business module** (и нужната Spring infrastructure). Test class е вътре в package-а на този модул.

| Test | Scope | Какво доказва |
| --- | --- | --- |
| [HealthAssessmentModuleTest](modulith-lab/src/test/java/bg/hristomanov/education/portmodulith/health/HealthAssessmentModuleTest.java) | STANDALONE health | Реален business logic + event publication, без clearance bean |
| [DangerousGoodsModuleTest](modulith-lab/src/test/java/bg/hristomanov/education/portmodulith/dangerousgoods/DangerousGoodsModuleTest.java) | STANDALONE goods | Публикуване на accepted/denied assessment |
| [OperationalClearanceModuleTest](modulith-lab/src/test/java/bg/hristomanov/education/portmodulith/clearance/OperationalClearanceModuleTest.java) | STANDALONE clearance | Event listeners и local projection behavior |
| [PortClearanceEndToEndSpringTest](modulith-lab/src/test/java/bg/hristomanov/education/portmodulith/PortClearanceEndToEndSpringTest.java) | Full Spring Boot context | Реални producers и consumer в един process |

```java
@ApplicationModuleTest
class HealthAssessmentModuleTest {
    @Autowired HealthAssessmentOperations assessments;

    @Test
    void publishesAHealthAssessmentBusinessFact(PublishedEvents events) {
        HealthAssessed result = assessments.assess("vessel-health-01", false);
        assertThat(events.ofType(HealthAssessed.class)).contains(result);
    }
}
```

`@ApplicationModuleTest` има **STANDALONE**, **DIRECT_DEPENDENCIES** и **ALL_DEPENDENCIES** режими. Ако standalone тест изисква десетки mocks от други modules, това е повод да преразгледаш responsibilities, не автоматично да стартираш всички зависимости.

## 5. Cross-module events — защо и кога?

Producer implementation използва `ApplicationEventPublisher.publishEvent(HealthAssessed)`, а consumer `@EventListener` слуша public event contract.

```text
health.assess(vessel-42, clear)
    └─ HealthAssessed(vessel-42, true) ─► ClearanceProjection → PENDING

goods.assess(vessel-42, complete documentation)
    └─ DangerousGoodsAssessed(vessel-42, true) ─► ClearanceProjection → CLEARED
```

Това намалява **compile-time coupling към producer implementation**, но:
- consumer остава semantic dependent на published event schema;
- **Spring Application Events са synchronous по подразбиране** — listener може да доведе до runtime failure/call-stack coupling;
- in-memory ConcurrentHashMap не дава durable projections, revisions, event ordering, crash recovery или multiple-instance consistency;
- съществуването на `@EventListener` **не** доказва Kafka integration, outbox или retries.

Важен Senior-level избор: когато нужен immediate consistent result във вътрешен процес, **direct module API call може да е по-ясен от event**. Events не са догма. Ако модул бъде извлечен в нов deployment, трябва изрично да се решат outbox, deduplication, async delivery, stale reads и observability. Виж [Outbox/Inbox](../../messaging/outbox-inbox/README.md).

## 6. Как се различава от предишния Strategic DDD lab?

[Старият integration-lab](integration-lab/) показва **отделни Maven domain artifacts**, compile-time domain independence, ACL срещу Conformist и локален projection.

**Този** пример демонстрира **package-based modules в един artifact**, public vs internal interfaces, Modulith named contracts и vertical testing. Двата подхода могат да се комбинират, но нито един не изисква да имаме по един microservice за всеки bounded context.

## 7. Runbook

От root на education:

```bash
# Modulith + неговият parent reactor:
mvn -pl spring/architecture/strategic-ddd/modulith-lab -am test

# Всички Strategic DDD модули:
mvn -pl spring/architecture/strategic-ddd -am verify

# Целият repository / CI:
mvn --no-transfer-progress verify
```

Кодът е Java 25 + Spring Boot 4.1.1 и Modulith 2.1.1 (версиите са зададени в [pom.xml](modulith-lab/pom.xml)). Няма база, брокер, Docker или production credentials.

### Senior review

1. Как `@NamedInterface` се различава от Java visibility и Java Platform Module System?
2. Как `ApplicationModules.verify()` проверява цикли и internal imports?
3. Защо `@ApplicationModuleTest` не доказва цялата бизнес интеграция?
4. Какво е различното между in-process event и persisted integration event?
5. Кога съвместният deployment е по-подходящ от 3 микросървиса?
6. Ако tomorrow extracting health към HTTP service, какъв contract и migration plan трябва да дефинираме?

## 8. Препоръчителен decision checklist

- [ ] Business owner-ът потвърждава границите (не само архитектът).
- [ ] Един модул има clear responsibility и право да еволюира своите правила.
- [ ] Публични module APIs и named event interfaces; без internal imports.
- [ ] CI проверява `ApplicationModules.verify()`.
- [ ] Има standalone + full context integration tests.
- [ ] Events са избрани според нуждата от integration и consistency.
- [ ] Не обещаваме independent deployment или durable delivery без доказателства.
- [ ] Extraction се основава на ownership, release, runtime, compliance и scale evidence.

**Изходен mental model:** Strategic DDD намира *семантичната граница*, Spring Modulith я прави *изпълнимо архитектурно правило* в нашата Java codebase.

## Оригинални източници

- [Devoxx Belgium 2026](https://youtu.be/v86SfHA5ISw).
- [Spring Modulith — Fundamentals](https://docs.spring.io/spring-modulith/reference/fundamentals.html).
- [Spring Modulith — Verification](https://docs.spring.io/spring-modulith/reference/verification.html).
- [Spring Modulith — Testing](https://docs.spring.io/spring-modulith/reference/testing.html).
- [Spring Boot Generations / Modulith compatibility](https://spring.io/projects/generations/).
