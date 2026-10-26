# 1.3 — Hands-on: Strategic DDD на Java 25 и Spring Boot 4

> **P1 / Practical Architecture Lab · около 45–60 минути с упражнения**
>
> Преди лабораторията прочети [1.1 — Service Boundaries](01-STRATEGIC-DDD-AND-BOUNDARIES.md) и [1.2 — Context Mapping](02-CONTEXT-MAPPING.md).

## 1. Казус и acceptance criteria

**Business scenario:** клиент иска leasing offer за конфигурируем автомобил, след което Contracts регистрира конкретен произведен автомобил по VIN, а Claims трябва да реши дали договорът може да премине към допълнителен claim review.

**Какво трябва да е вярно:**

- **AC1:** Quoting работи без VIN или ContractRegistry.
- **AC2:** Contracts валидира физическия VIN; това не е Quoting правило.
- **AC3:** Claims не import-ва типовете на Contracts или Quoting.
- **AC4:** Може да сменим външния contract reader без промяна в Claims domain.
- **AC5:** Наивният Conformist baseline и ACL могат да произведат еднакъв резултат, но различен coupling.
- **AC6:** При projection replication има **изрично демонстриран stale-read window**.
- **AC7:** По-стар и duplicate published facts не презаписват по-нов revision.
- **AC8:** Независимите Maven модули плюс ArchUnit отказват нежелани import dependencies.
- **AC9:** Един Spring Boot 4 application може да сглоби различните модели, без да ги слива.
- **AC10:** Лабораторията не твърди, че демонстрира реални distributed services, broker или durable database.

## 2. Какво действително стартираме

```text
                     integration-lab (Boot composition)
                        ┌─────────────────────┐
                        │ StrategicDddApp     │
                        │                     │
                        ├── QuotingService ───┼── quoting-domain
                        │                     │
                        ├── ContractRegistry ─┼── contracts-domain
                        │                     │
                        └── ClaimsService ────┼── claims-domain
                                 ▲             │
                                 │ Claims port │
                        ContractToClaimsAclAdapter (integration code)
                                 │
                                 └──── ContractRegistry
```

Това е един deployment. Domain projects **са истински Maven artifacts**, не просто packages, и не зависят един от друг на compile time.

### Структура и files

```text
strategic-ddd/
├── pom.xml                     aggregator
├── quoting-domain/
│   ├── pom.xml
│   └── src/main/.../quoting/
│       ├── VehicleConfiguration.java
│       ├── LeaseQuote.java
│       └── QuotingService.java
├── contracts-domain/
│   └── src/main/.../contracts/
│       ├── RegisteredVehicle.java
│       ├── ContractStatus.java
│       ├── ContractSnapshot.java
│       └── ContractRegistry.java
├── claims-domain/
│   └── src/main/.../claims/
│       ├── ContractFacts.java
│       ├── ContractFactsPort.java
│       ├── CoverageDecision.java
│       └── ClaimsEligibilityService.java
└── integration-lab/
    ├── src/main/.../integration/
    │   ├── ContractToClaimsAclAdapter.java
    │   ├── ReplicatedContractFactsProjection.java
    │   └── StrategicDddApplication.java
    └── src/test/.../integration/
        ├── StrategicDddScenarioTest.java
        ├── ConformistComparisonTest.java
        ├── DomainBoundaryArchitectureTest.java
        ├── SpringWiringIntegrationTest.java
        └── bad/NaiveConformistClaimsChecker.java
```

## 3. Run commands

От repository root:

```bash
# Интеграционен lab и Maven upstream modules
mvn -pl spring/architecture/strategic-ddd/integration-lab -am test

# Пълна проверка на всички education modules
mvn --no-transfer-progress verify
```

Умишлено **не** стартираме Docker, PostgreSQL, Oracle, Kafka или Testcontainers — тук проверяваме **границите и семантиката**, а не persistence/network layer.

## 4. Experiment 1 — две значения на „Vehicle“

Прегледай:

- [VehicleConfiguration.java](quoting-domain/src/main/java/bg/hristomanov/education/strategicddd/quoting/VehicleConfiguration.java)
- [QuotingService.java](quoting-domain/src/main/java/bg/hristomanov/education/strategicddd/quoting/QuotingService.java)
- [RegisteredVehicle.java](contracts-domain/src/main/java/bg/hristomanov/education/strategicddd/contracts/RegisteredVehicle.java)

**Quoting:**

```java
LeaseQuote quote = new QuotingService().quote(
        new VehicleConfiguration("EV-TOURING", Set.of("WINTER", "PANORAMA")));
```

**Очакване:** 399.00 + 25.00 + 38.00 = 462.00 EUR/month. Нито един VIN не е нужен. [Тест за offer configuration](integration-lab/src/test/java/bg/hristomanov/education/strategicddd/integration/StrategicDddScenarioTest.java) проверява стойността.

**Contracts:**

```java
RegisteredVehicle physical = new RegisteredVehicle(
        "WVWZZZ1JZXW000001", "CA1234AB", "Electric Touring");
```

**Пробвай** с невалиден VIN. Конструкторът ще го откаже, но това **не променя правилата на Quoting**.

**Причина:** едни и същи думи могат да принадлежат на различни модели. Няма универсална „vehicle entity“, с която да удовлетворим и конфигуратора, и регистрирания asset.

## 5. Experiment 2 — BAD Conformist срещу GOOD ACL

**Naive:** [NaiveConformistClaimsChecker.java](integration-lab/src/test/java/bg/hristomanov/education/strategicddd/integration/bad/NaiveConformistClaimsChecker.java) директно използва upstream ContractSnapshot и ContractStatus.

**ACL:** [ContractToClaimsAclAdapter.java](integration-lab/src/main/java/bg/hristomanov/education/strategicddd/integration/ContractToClaimsAclAdapter.java) мапва upstream types към [ContractFacts.java](claims-domain/src/main/java/bg/hristomanov/education/strategicddd/claims/ContractFacts.java).

**ClaimsService** вижда само собствения [ContractFactsPort](claims-domain/src/main/java/bg/hristomanov/education/strategicddd/claims/ContractFactsPort.java).

**Тест:** [ConformistComparisonTest.java](integration-lab/src/test/java/bg/hristomanov/education/strategicddd/integration/ConformistComparisonTest.java).

И двата подхода връщат същия бизнес outcome **днес**. Различава се не boolean стойността, а **кой трябва да се промени**, когато Contracts published language се промени утре.

### Exercise

1. Създай нова contract status стойност, например PENDING_SIGNATURE, като отделен учебен branch.
2. Опиши правилната Claims semantics за този status.
3. Сравни колко файлове се променят при Conformist и при ACL.
4. Не променяй production code само за да удовлетвориш unit тест — обоснови semantic mapping.

## 6. Experiment 3 — Data replication и stale read window

Прегледай [ReplicatedContractFactsProjection.java](integration-lab/src/main/java/bg/hristomanov/education/strategicddd/integration/ReplicatedContractFactsProjection.java).

**Timeline:**

```text
T1 Contracts.activate()       ──► ACTIVE revision=1
T2 Claims projection receives ──► ACTIVE revision=1
T3 Contracts.terminate()      ──► TERMINATED revision=2
T4 Claims local query         ──► вижда ACTIVE (stale!)
T5 Projection receives rev=2  ──► вижда TERMINATED
T6 Late event rev=1 arrives   ──► ignored (do not downgrade)
```

JUnit доказателство:
[replicatedProjectionHasAnExplicitStaleReadWindow + staleAndDuplicateEventsCannotUndoANewerPublishedFact](integration-lab/src/test/java/bg/hristomanov/education/strategicddd/integration/StrategicDddScenarioTest.java).

**Trade-off:** Claims може да вземе решение без runtime lookup към Contracts, но трябва да има изисквания за max lag, event versioning, replay и reconciliation.

**Не бъркай** това с реален Kafka consumer: тук event arrival е обикновено method call. В production добави **Outbox + Inbox + persisted offset + monitoring**, иначе можеш тихо да загубиш промени.

### Exercise

Въведи на хартия scenario с duplicate rev=2 и out-of-order rev=1. Каква е коректната версия? После симулирай липсваща rev=3 завинаги: само compare-and-swap на revision **не може** да възстанови липсващо event. Как ще откриеш и поправиш това?

## 7. Experiment 4 — Maven compile boundaries + ArchUnit

Ключовото не е анотацията, а че:

- `quoting-domain/pom.xml` НЕ зависи от `contracts-domain` и `claims-domain`.
- `claims-domain/pom.xml` НЕ зависи от `contracts-domain`.
- **Само** `integration-lab/pom.xml` има dependencies към трите.
- Домейн класовете **нямат Spring/JPA imports**.

[DomainBoundaryArchitectureTest.java](integration-lab/src/test/java/bg/hristomanov/education/strategicddd/integration/DomainBoundaryArchitectureTest.java) проверява:

1. Quoting/Contracts/Claims не import-ват другите domain modules или integration layer.
2. Нито един domain model не зависи от Spring/JPA.
3. Няма циклични package dependencies в избрания application slice graph.

**Proof experiment:** в отделен branch опитай да import-неш ContractSnapshot вътре в ClaimsEligibilityService. Независимият Maven module трябва да откаже компилацията; това е истинска dependency boundary, не style preference.

**Защо ArchUnit въпреки Maven?** Maven пази explicit artifact graph; ArchUnit следи package/design restrictions при бъдещи реорганизации и при ситуации, когато няколко contexts са в общ модул. Двата подхода са допълващи се.

## 8. Experiment 5 — Spring Boot като composition root

Код: [StrategicDddApplication.java](integration-lab/src/main/java/bg/hristomanov/education/strategicddd/integration/StrategicDddApplication.java).

```text
@Bean QuotingService
@Bean ContractRegistry
@Bean ContractFactsPort -> ContractToClaimsAclAdapter
@Bean ClaimsEligibilityService -> ContractFactsPort
```

Това не означава, че Spring controller трябва да знае upstream DTO. Spring сглобява компонентите; **Claims model остава независим**.

Тест: [SpringWiringIntegrationTest.java](integration-lab/src/test/java/bg/hristomanov/education/strategicddd/integration/SpringWiringIntegrationTest.java).

Тук няма HTTP endpoint — не добавяме инфраструктурна демонстрация, която не помага на конкретния architectural lesson.

## 9. Кога бихме извлекли Contracts физически?

Ако изнесем Contracts в отделен deployable, **не трябва** да копираме ContractSnapshot директно в Claims domain.

**Вариант A:** сменяме ContractToClaimsAclAdapter с HTTP/REST reader, оставяме ContractFactsPort непроменен. Добавяме timeout, retry policy, bulkhead, auth, versioned published API и contract tests.

**Вариант B:** Contracts публикува versioned events/CDC/outbox, а Claims използва local replicated projection. Добавяме durable messaging и freshness policy.

**Вариант C:** остава един modular Spring Boot deployment, ако е по-евтин и отговаря на ownership, risk и scalability. Това е **напълно валиден outcome**.

В никой вариант не бива да разделяш физически само защото един bounded context има „твърде много класове“.

## 10. Упражнение със senior-level trade-off

**Problem:** Fleet contract updates са критични за прихода. Retail quoting може да има 10× traffic spikes. Някой предлага общ FleetRetailCreditService, защото pricing правила са подобни.

Обоснови дизайн:

- Колко bounded contexts виждаш? Защо?
- Къде трябва да бъде shared model и кога Shared Kernel е опасен?
- Искаме ли общ deployable или отделни processes? Какво SLO/risk evidence ще събереш?
- Ако стартираме modular monolith, колко нови network hops получаваме при извличане?
- Как ще докажем границата с tests и code owners?

## 11. Кога NOT to use тази архитектура

Излишни отделни bounded contexts са overengineering за прост CRUD, един owner и една ясна транзакция. ACL също може да е излишен, когато consumer съзнателно приема стабилен upstream model и няма собствен domain vocabulary.

Много важно: **кодът демонстрира design decisions, не production trustworthiness**. In-memory registry и projection не са durable, не се възстановяват след restart и не са разпределени между реални services. Contracts ACTIVE не гарантира реално insurance coverage; в урока резултатът е само ELIGIBLE_FOR_REVIEW.

## 12. Кратък финален quiz

1. Защо компилирането на claims-domain независимо от contracts-domain е смислено?
2. Как една domain concept може да има два валидни различни model shape-а?
3. Къде стои ACL и защо не пренасяме ContractSnapshot през него?
4. Защо ревизии защитават срещу out-of-order events, но не срещу изгубени?
5. Каква е разликата между „самостоятелен Maven module“ и „автономен microservice“?
6. Кога **умишлен Conformist** е по-доброто инженерно решение?
7. Как ще отговориш на proposed refactor към „общ VehicleService“?

## Изход

**Реалният проблем беше distributed semantic coupling, не липса на REST endpoints.**

Решихме го с различни модели, downstream-owned port, explicit ACL и тестваеми compile boundaries. Показахме data replication като **отделно решение с реална цена**. Ако решим да извличаме services, вече знаем **коя граница защитаваме и какъв contract трябва да пазим**.

Източници: [Devoxx talk](https://www.youtube.com/watch?v=eFSEg54mwVU), [Martin Fowler Bounded Context](https://martinfowler.com/bliki/BoundedContext.html), [DDD Crew context maps](https://github.com/ddd-crew/context-mapping), [ArchUnit](https://www.archunit.org/userguide/html/000_Index.html), [Spring Modulith verification](https://docs.spring.io/spring-modulith/reference/verification.html).
