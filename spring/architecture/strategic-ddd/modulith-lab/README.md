# Port Clearance — executable Spring Modulith lab

> **Java 25 / Spring Boot 4.1.1 / Spring Modulith 2.1.1**
>
> [Back to P1 lesson: Spring Modulith](../05-SPRING-MODULITH.md) · [EventStorming Workshop](../04-EVENTSTORMING-WORKSHOP.md)

## Входният проблем

Имаме три **бизнес способности** в една система. Искаме **един deployable** с module boundaries, а не три микросървиса или един огромен package с произволни взаимни imports.

Port of Rotterdam лекцията показва мотивацията за такава архитектура. **Това приложение е самостоятелен учебен модел**, не реално пристанищно решение, не законово правило и не код на Port of Rotterdam.

## Scope

```text
HarbourModulithApplication [1 process]
├── health (API, events, internal implementation)
├── dangerousgoods (API, events, internal implementation)
└── clearance (API, event listeners, internal in-memory projection)
    depends ONLY on health::events and dangerousgoods::events
```

**Всички класове са в един Maven module/JAR**. Spring Modulith открива application modules от *direct subpackages* под Spring Boot root package.

## README → source → proof

| Концепция | Код | Тест |
| --- | --- | --- |
| Spring Boot composition root | [HarbourModulithApplication](src/main/java/bg/hristomanov/education/portmodulith/HarbourModulithApplication.java) | [PortClearanceEndToEndSpringTest](src/test/java/bg/hristomanov/education/portmodulith/PortClearanceEndToEndSpringTest.java) |
| Public Health module API | [HealthAssessmentOperations](src/main/java/bg/hristomanov/education/portmodulith/health/HealthAssessmentOperations.java) | [HealthAssessmentModuleTest](src/test/java/bg/hristomanov/education/portmodulith/health/HealthAssessmentModuleTest.java) |
| Named published event interface | [health/events/package-info.java](src/main/java/bg/hristomanov/education/portmodulith/health/events/package-info.java) | Health test + architecture test |
| Public Dangerous Goods API | [DangerousGoodsOperations](src/main/java/bg/hristomanov/education/portmodulith/dangerousgoods/DangerousGoodsOperations.java) | [DangerousGoodsModuleTest](src/test/java/bg/hristomanov/education/portmodulith/dangerousgoods/DangerousGoodsModuleTest.java) |
| Explicit dependency restrictions | [clearance/package-info.java](src/main/java/bg/hristomanov/education/portmodulith/clearance/package-info.java) | [HarbourModulithArchitectureTest](src/test/java/bg/hristomanov/education/portmodulith/HarbourModulithArchitectureTest.java) |
| Owned read model and event handling | [ClearanceProjection](src/main/java/bg/hristomanov/education/portmodulith/clearance/internal/ClearanceProjection.java) | [OperationalClearanceModuleTest](src/test/java/bg/hristomanov/education/portmodulith/clearance/OperationalClearanceModuleTest.java) |

## Business test cases

1. No assessments → **PENDING**.
2. Health approved, goods missing → **PENDING**.
3. Goods approved, health missing → **PENDING**.
4. Both approved (either order) → **CLEARED**.
5. Any negative assessment → **DENIED**.
6. Events for another vessel ID **не трябва** да променят настоящото решение.

Това са illustrative decisions. Реалният clearance процес може да има допълнителни actor approvals, policies, exceptions, revisions и audit requirements.

## Run

```bash
mvn -pl spring/architecture/strategic-ddd/modulith-lab -am test
mvn --no-transfer-progress verify
```

Не изисква Docker, PostgreSQL, Redis, Keycloak, Kafka или външни secrets.

## Bad/good exercise

**BAD:** във `clearance.internal` импортни `health.internal.HealthAssessmentService`. Java class е public, така че самият Java compiler може да го приеме, но Spring Modulith dependency verification трябва да се провали.

**GOOD:** clearance чете само типове от `health.events` / `dangerousgoods.events` named public interfaces. След възстановяване на кода Architecture Test става зелен.

### Защо не просто Mockito tests?

- @ApplicationModuleTest стартира **реални Spring beans** вътре в един module.
- PublishedEvents доказва published in-process events.
- Full-context test доказва composition на modules.
- ApplicationModules.verify() проверява реалния dependency graph.

JUnit успех **не доказва**:
- реална DB durability, транзакционни гаранции;
- persistent outbox, consumer offsets или at-least-once delivery;
- HTTP/messaging integration между реални deployments;
- JPA transaction/locking/consistency или production correctness;
- производителност и по-ниски operational costs.

Ако използваме events през Kafka след бъдеща extraction, ще трябва да решим delivery, ordering, versioning, retries и idempotency отделно. Сравни [Outbox / Inbox](../../../messaging/outbox-inbox/README.md) и [Domain Events](../../../events/domain-events/README.md).

## Learning exit

Трябва да можеш да кажеш:
- защо module е повече от package name;
- как публичният Java class може да е забранен module API;
- какво добавя @ApplicationModuleTest спрямо @SpringBootTest;
- защо named interface е по-тесен contract от „достъп до целия Health module“;
- как да отделим compile-time decoupling от runtime consistency;
- кога извличането на отделен microservice е justified.

## Original sources

- [Mario Klaver / Devoxx 2026](https://youtu.be/v86SfHA5ISw).
- [Spring Modulith 2.1 Fundamentals](https://docs.spring.io/spring-modulith/reference/fundamentals.html).
- [Spring Modulith Integration Testing](https://docs.spring.io/spring-modulith/reference/testing.html).
- [Spring Modulith Verification](https://docs.spring.io/spring-modulith/reference/verification.html).
