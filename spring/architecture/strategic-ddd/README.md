# Strategic DDD & Microservice Boundaries — P1 Deep Technical Lecture

> **Tech Radar → P1 | System Design / Distributed Systems | Mid+ → Early Senior**
>
> Основен източник: [Devoxx Belgium 2026 — *Microservices Without the Distributed Mess*](https://www.youtube.com/watch?v=eFSEg54mwVU), Steven Hillaert (Sofico), около 45 минути.
>
> Това е **стратегическа архитектурна тема**, а не повторение на вече разработените Saga, CQRS, Domain Events и Hexagonal Architecture уроци. Имаме **изпълнима Java 25 / Spring Boot 4.1.1 лаборатория**, а не само бележки.

## 1. Реалният казус: микросървисите се разпадат на мрежа от зависимости

Разработваме платформа за автомобилен лизинг:

```text
Quoting: конфигурация на автомобил, опции и оферта
Contracts: реален VIN, регистрация и активен лизингов договор
Claims: проверка на договорни факти при приемане на щета
```

Първоначално всичко е в общ код. Тръгваме да „правим микросървиси“:

```text
BFF → Quoting API → Contracts API → Claims API
       ↘───── Database ────────↗
```

Появяват се **coordinated deployments**, чупливи договори, ownership конфликти, излишни synchronous reads и бизнес термини, които значат различни неща.

Naive реакция: „да сложим Kafka и да разделим услугите на още по-малки“. Но брокерът не премахва semantic coupling, общото притежание на данни, контрактите и нуждата от миграции.

**Истинският въпрос:** кои **бизнес решения, данни и промени** трябва да се държат заедно, кой ги притежава и на кого позволяваме да влияе върху модела?

## 2. Цели на обучението

След модула трябва да можеш да обясниш и **защитиш на архитектурен review**:

1. Защо **microservice не е само deployment** и защо „малък service“ не е цел.
2. Разликите между **domain, subdomain, bounded context, module, deployable service и team**.
3. Защо **bounded context ≠ задължително един microservice**.
4. Как да откриеш coupling през **ownership, модел, runtime зависимости и deploy cadence**.
5. Как **context mapping** показва зависимости, които не се виждат от HTTP/брокер граф.
6. Кога използваш **Anti-Corruption Layer**, Conformist, Customer–Supplier или Shared Kernel.
7. Кога **local read projection** е по-добра от synchronous call и каква е цената на stale reads.
8. Кога да предпочетеш **modular monolith**, отделен deployable service или целенасочено споделена read-only data service.
9. Как **Maven + ArchUnit + integration tests** доказват границите, а не само ги документират.

## 3. Учебен ред — пет самостоятелни части (два P1 блока, без дублиране)

| Част | Фокус | Материал |
| --- | --- | --- |
| **1.1 Fundamentals** | Strategic DDD: model ownership, domain/subdomain, ubiquitous language, bounded contexts, deployment и risk boundaries | [01 — Strategic DDD & Service Boundaries](01-STRATEGIC-DDD-AND-BOUNDARIES.md) |
| **1.2 Deep Dive** | Context Mapping: upstream/downstream, Conformist, ACL, Customer–Supplier, Shared Kernel, data replication, communication choices | [02 — Context Mapping & Relationships](02-CONTEXT-MAPPING.md) |
| **1.3 Hands-on** | Java/Spring Boot executable case: различни значения на Vehicle, ACL срещу Conformist, local projection, ArchUnit и Spring integration | [03 — Practical Architecture Labs](03-LABS.md) |
| **1.4 Workshop — P1** | EventStorming Big Picture/Process Modelling, pivotal events, hotspots, actors и boundaries discovery | [04 — EventStorming Workshop](04-EVENTSTORMING-WORKSHOP.md) |
| **1.5 Spring Modulith — P1** | Package-based module APIs, named interfaces, allowedDependencies, verification, module tests и in-process events | [05 — Spring Modulith](05-SPRING-MODULITH.md) |

За NotebookLM: [оригиналните три P1 аудио prompts](NOTEBOOKLM-PROMPTS.md) са допълнени с [два специализирани prompts за EventStorming и Spring Modulith](NOTEBOOKLM-EVENTSTORMING-MODULITH.md). Те са обвързани с конкретните файлове и доказателства, не със свободно генерирано резюме.

**Новият Port of Rotterdam case:** [Engineering Case Study 004](../../../docs/case-studies/004-port-of-rotterdam-modular-monolith.md). Имаме [отделен modulith-lab](modulith-lab/README.md) с един Spring Boot deployable, три business packages, named event interfaces и executable Modulith boundary/module tests. Той допълва старите Maven/ArchUnit labs, не ги заменя.

## 4. Преди/след: защо един общ Vehicle модел е опасен

**BAD — една универсална entity Vehicle:**

```java
class Vehicle {
    String modelCode;
    Set<String> selectableOptions;
    String vin;
    String registrationPlate;
    ContractStatus contractStatus;
    BigDecimal advertisedMonthlyPrice;
}
```

Quoting се оказва зависим от VIN и договорни състояния, въпреки че оферта може да се изготви **преди** да е произведен конкретен автомобил. Contracts се оказва зависим от конфигуратора. При промяна на модела се координират множество екипи.

**GOOD — различни понятия в отделни model boundaries:**

```text
Quoting VehicleConfiguration: modelCode + optionCodes
          │
          │  няма общ Vehicle entity
          ▼
Contracts RegisteredVehicle: VIN + registrationPlate + modelName

Claims ContractFacts: agreementId + coveredAssetVin + contractActive
                ▲
                │ ACL translation
          Contracts ContractSnapshot
```

Това не е ненужно дублиране. Това са **различни домейн понятия**, които имат отделни invariants и evolution lifecycle.

## 5. Изпълнима архитектура

```text
strategic-ddd/  (Maven aggregator)
├── quoting-domain/     pure Java model and pricing decisions
├── contracts-domain/   pure Java physical vehicle + contract authority
├── claims-domain/      pure Java claims vocabulary + ContractFactsPort
├── integration-lab/    Spring Boot composition
│   ├── ContractToClaimsAclAdapter      (sync in-process reader)
│   ├── ReplicatedContractFactsProjection (async-like local copy)
│   └── ArchUnit / JUnit / @SpringBootTest
└── modulith-lab/      one Spring Boot deployable / three package-based modules
    ├── Health Assessment / Dangerous Goods / Operational Clearance
    ├── @NamedInterface + @ApplicationModule boundaries
    └── ApplicationModules.verify() / @ApplicationModuleTest / full-context JUnit
```

**Maven dependency direction:**

```text
quoting-domain   contracts-domain   claims-domain
      ^               ^                  ^
      │               │                  │
      └────────── integration-lab ───────┘
                          │
                    Spring Boot
```

Модулите за домейн **нямат dependency един към друг или към Spring/JPA**. Връзките се събират **само в integration module-а**, който знае двата модела и ги превежда.

**Важно разграничение:** това е **едно Spring Boot приложение / един deployable unit**, съдържащо няколко модела и модула. Целта е да научим **моделните граници**, без преждевременно да добавяме три HTTP сервиса и distributed failure modes.

### README → код → доказателство

| Концепция | Production-like код | JUnit доказателство |
| --- | --- | --- |
| Vehicle като конфигурация | [QuotingService](quoting-domain/src/main/java/bg/hristomanov/education/strategicddd/quoting/QuotingService.java), [VehicleConfiguration](quoting-domain/src/main/java/bg/hristomanov/education/strategicddd/quoting/VehicleConfiguration.java) | [StrategicDddScenarioTest](integration-lab/src/test/java/bg/hristomanov/education/strategicddd/integration/StrategicDddScenarioTest.java) |
| Vehicle като физически asset | [RegisteredVehicle](contracts-domain/src/main/java/bg/hristomanov/education/strategicddd/contracts/RegisteredVehicle.java), [ContractRegistry](contracts-domain/src/main/java/bg/hristomanov/education/strategicddd/contracts/ContractRegistry.java) | същият сценарен тест |
| Own downstream model | [ClaimsEligibilityService](claims-domain/src/main/java/bg/hristomanov/education/strategicddd/claims/ClaimsEligibilityService.java), [ContractFactsPort](claims-domain/src/main/java/bg/hristomanov/education/strategicddd/claims/ContractFactsPort.java) | сценарен тест |
| ACL translation | [ContractToClaimsAclAdapter](integration-lab/src/main/java/bg/hristomanov/education/strategicddd/integration/ContractToClaimsAclAdapter.java) | [ConformistComparisonTest](integration-lab/src/test/java/bg/hristomanov/education/strategicddd/integration/ConformistComparisonTest.java) |
| BAD Conformist baseline | [NaiveConformistClaimsChecker](integration-lab/src/test/java/bg/hristomanov/education/strategicddd/integration/bad/NaiveConformistClaimsChecker.java) | ConformistComparisonTest |
| Data duplication, revisions, stale reads | [ReplicatedContractFactsProjection](integration-lab/src/main/java/bg/hristomanov/education/strategicddd/integration/ReplicatedContractFactsProjection.java) | StrategicDddScenarioTest |
| Compile-time architecture boundary | [Maven domain modules](pom.xml) | [DomainBoundaryArchitectureTest](integration-lab/src/test/java/bg/hristomanov/education/strategicddd/integration/DomainBoundaryArchitectureTest.java) |
| Spring wiring за един deployment | [StrategicDddApplication](integration-lab/src/main/java/bg/hristomanov/education/strategicddd/integration/StrategicDddApplication.java) | [SpringWiringIntegrationTest](integration-lab/src/test/java/bg/hristomanov/education/strategicddd/integration/SpringWiringIntegrationTest.java) |

## 6. Старт

От root на repository:

```bash
mvn -pl spring/architecture/strategic-ddd/integration-lab -am test
```

Или пълна проверка на всички модули:

```bash
mvn --no-transfer-progress verify
```

Тестовете са **без Docker, Kafka и реална база**. Това е съзнателно: изолираме моделните зависимости и ги проверяваме детерминистично.

## 7. Къде се връзва с вече наученото?

| Има го в education | Какво е различното тук? |
| --- | --- |
| [Hexagonal Architecture](../hexagonal-architecture/README.md) | Hexagonal пази application core от инфраструктурата; **Strategic DDD решава кои бизнес модели и boundaries трябва да съществуват** |
| [CQRS](../cqrs/README.md) | CQRS отделя write/read models; тук избираме **кой притежава данните** и какво може да се репликира между контексти |
| [Domain Events](../../events/domain-events/README.md) | Events са integration mechanism; тук избираме правилната връзка между owners |
| [Outbox/Inbox](../../messaging/outbox-inbox/README.md) | Прави публикацията/получаването reliable; **не определя model ownership** |
| [Saga/Temporal](../../messaging/saga/README.md) | Orchestrates distributed business workflow; тук решаваме **дали изобщо са нужни отделни deployables** |
| [Design Patterns — ACL roadmap](../../../design-patterns/BACKEND-PATTERNS-ROADMAP.md) | ACL вече има реална, изпълнима реализация и тестове |

## 8. Кога да използваме и кога да НЕ използваме този дизайн

**Използвай стратегическо моделиране**, когато системата има повече от един бизнес език, много teams, сложни integration flows, coordinated releases или спорно ownership на данни.

**Не извличай нов микросървис автоматично**, когато намериш нов bounded context. Първо провери съвместните промени, нужните транзакции, latency, риск, операции и цена за поддръжка. Няколко bounded contexts могат съзнателно да живеят в един deployable; една домейн зона може да се разцепи физически по regulation/scale/isolation constraints, ако контекстите и договорите останат ясни.

**Ограничения на нашата лаборатория:**

- no runtime HTTP/network, broker, deployment pipeline, owner/team governance или contract testing across organizations;
- ContractRegistry и claims projection са **in-memory**, не durable, нямат crash recovery;
- версията на contract snapshot демонстрира monotonic ordering, **не доказва at-least-once delivery**, transactional publication или absolute correctness;
- ELIGIBLE_FOR_REVIEW **не** е истинска застрахователна преценка; активният лизингов договор не гарантира покритие.

## 9. Изходен mental model

> **Bounded context = граница на смисъла, модела и invariant-ите.**
> **Deployment boundary = граница на процеса, runtime рисковете и release lifecycle-а.**
> **Team boundary = граница на отчетността и decision rights.**
>
> Тези граници се влияят взаимно, но **не са синоними**.

Правилната стъпка не е „да направим повече микросървиси“, а „да намалим ненужната зависимост между бизнес решенията“.

### Code-review checklist

- [ ] Кой притежава бизнес правилото и authoritative data?
- [ ] Налага ли нашият API чужд model на downstream-а?
- [ ] Ако rename-на вътрешно поле, кои consumers трябва да бъдат променени?
- [ ] Има ли coupling през synchronous lookup при всяко business decision?
- [ ] Определили ли сме freshness / staleness policy за репликираните данни?
- [ ] Има ли backward compatibility / versioned published language?
- [ ] Съвпадат ли deployment/organizational boundaries с реалните причини, а не с догма?
- [ ] Може ли всеки екип да пусне собствена промяна без coordinated release?
- [ ] Какво е най-простото решение, което запазва coherence?

## 10. Оригинални източници

1. **Devoxx Belgium 2026:** [Microservices Without the Distributed Mess](https://www.youtube.com/watch?v=eFSEg54mwVU) — изходна лекция.
2. **Martin Fowler — Bounded Context:** https://martinfowler.com/bliki/BoundedContext.html
3. **DDD Crew — Context Mapping:** https://github.com/ddd-crew/context-mapping и https://virtualddd.com/ddd-crew/context-mapping/
4. **Eric Evans — Domain-Driven Design Reference:** https://www.domainlanguage.com/ddd/reference/
5. **ArchUnit User Guide (architectural tests):** https://www.archunit.org/userguide/html/000_Index.html
6. **Spring Modulith — verification (алтернативен инструмент):** https://docs.spring.io/spring-modulith/reference/verification.html
7. **Devoxx slides:** провери официалната страница на лекцията; не приписваме непотвърден примерен GitHub repository на лектора.

## 11. Финален въпрос

Предлагат ти да извлечеш VehicleService, който съдържа **всички** автомобилни данни, защото Quoting, Contracts и Claims „работят с автомобили“.

**Попитай:** *Говорим ли за една и съща бизнес концепция и решения? Кой притежава кое? Какъв модел трябва да се промени при нова конфигурация за оферти, нов VIN регистър или ново правило за щети?*

Ако отговорите са различни, **общата Vehicle entity или централният VehicleService вероятно не са правилният boundary**. Избери отделни модели и ясни relationships; чак тогава избирай процеси, транспорт и deployment.
