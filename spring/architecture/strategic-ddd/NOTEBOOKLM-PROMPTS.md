# NotebookLM — P1 учебни аудио prompts за Strategic DDD / Context Mapping

Това са **три самостоятелни аудио урока**, съобразени с новия модул в education. Материалът е основан на Devoxx лекцията, но е разширен с реални инженерни аргументи и изпълними Java/Spring примери.

## Източници, които да добавиш в NotebookLM

- Оригинална лекция: https://www.youtube.com/watch?v=eFSEg54mwVU
- Наш [README — курс и overview](README.md)
- [1.1 Strategic DDD](01-STRATEGIC-DDD-AND-BOUNDARIES.md)
- [1.2 Context Mapping](02-CONTEXT-MAPPING.md)
- [1.3 Labs](03-LABS.md)
- [Martin Fowler — Bounded Context](https://martinfowler.com/bliki/BoundedContext.html)
- [DDD Crew — Context Mapping](https://github.com/ddd-crew/context-mapping)
- За hands-on: [QuotingService.java](quoting-domain/src/main/java/bg/hristomanov/education/strategicddd/quoting/QuotingService.java), [RegisteredVehicle.java](contracts-domain/src/main/java/bg/hristomanov/education/strategicddd/contracts/RegisteredVehicle.java), [ClaimsEligibilityService.java](claims-domain/src/main/java/bg/hristomanov/education/strategicddd/claims/ClaimsEligibilityService.java), [ACL](integration-lab/src/main/java/bg/hristomanov/education/strategicddd/integration/ContractToClaimsAclAdapter.java), [Architectural Tests](integration-lab/src/test/java/bg/hristomanov/education/strategicddd/integration/DomainBoundaryArchitectureTest.java).

**Не въвеждай несъществуващи benchmark резултати, внедрявания или production guarantees.** Модулът е учебна in-process Java/Spring лаборатория.

---

## P1 · Audio 1 — Strategic DDD, Ownership & Service Boundaries

**Целева дължина:** около 30–40 минути.

### Prompt

```text
Създай задълбочена техническа аудио лекция на български, ниво силен Mid → Senior Java/Spring backend инженер.

Основните източници са Devoxx лекцията "Microservices Without the Distributed Mess" и нашият файл 01-STRATEGIC-DDD-AND-BOUNDARIES.md. Целта не е обзор на microservices framework-и, а умение да проектираме правилните граници на бизнес модели и услуги.

Започни с реален лизингов казус: Quoting, Contracts, Claims, няколко екипа, общ Vehicle model, coordinated releases. Обясни подробно защо Kafka/HTTP Gateway/по-малки services не могат сами да поправят semantic coupling.

Разграничи domain, subdomain, core/supporting/generic subdomain, bounded context, ubiquitous language, team boundary, module, runtime deployment. Обясни защо тези граници НЕ са 1:1 и защо едни и същи думи като Vehicle могат да имат различни значения. Сравни configurable offer vehicle срещу physical VIN-bearing vehicle.

Покажи как ownership работи в две направления: team accountability + decision rights и service ownership на capabilities, rules и authoritative data. Обясни от кои реални operational symptoms разбираме, че ownership липсва.

Разгледай трите валидни комбинации от bounded contexts и deployables: 2 BC / 2 services, 2 BC / 1 service, 1 BC / 2 deployments по risk/scale/legal причина. Добави пример за modular monolith като разумен default и за пет local calls, които след extraction стават network hops.

За всяка концепция използвай: production symptom → naive решение → защо се проваля → правилен mental model → alternatives и trade-offs. Не препоръчвай microservices или DDD като задължителна архитектура.

Обясни техническите английски термини точно, но говори на естествен български. Не чети Java код като диктовка; обяснявай boundary и причината за кода.

Включи 5 самопроверки без да даваш веднага верните отговори, и завърши с design challenge: дали централен VehicleService е добра идея при Quoting/Contracts/Claims.

Не твърди, че един Maven module автоматично дава production microservice autonomy.
```

---

## P1 · Audio 2 — Context Mapping, ACL & Upstream/Downstream

**Целева дължина:** около 30–40 минути.

### Prompt

```text
Направи задълбочена българска аудио лекция (Mid+ → Senior) за Strategic DDD Context Mapping по нашия файл 02-CONTEXT-MAPPING.md и Devoxx лекцията.

Започни от грешното допускане, че service dependency graph с HTTP стрелки ни казва кой зависи от кого. Проследи подробно как се различават runtime call direction и model influence direction. Обясни upstream и downstream без да ги свеждаш до HTTP client/server.

Покрий Conformist, Anti-Corruption Layer (ACL), Customer–Supplier, Partnership, Shared Kernel, Open Host Service, Published Language и Separate Ways. За всеки дай business example, positives/negatives, owner на промяната и как се мигрира contract.

Сравни тестовия NaiveConformistClaimsChecker с реалния ContractToClaimsAclAdapter: upstream ContractSnapshot и ContractStatus срещу downstream-owned ContractFacts и ContractFactsPort. Обясни, че еднакъв резултат днес не значи еднаква устойчивост утре. Не наричай Conformist автоматично antipattern.

Детайлно разгледай: synchronous lookup, local projection/replication, async message contracts, read-only database data service. Обясни freshness, eventual consistency, stale reads, idempotent replay, revision handling, source-of-truth, Outbox/Inbox и защо message broker не премахва semantic coupling.

Използвай flow на събитията: ACTIVE rev=1, TERMINATED rev=2, local projection още ACTIVE, късно rev=1 събитие. Обясни как version check предпазва от downgrade и защо НЕ поправя липсващи събития или загубени delivery.

Свържи темата със съществуващите education modules Hexagonal Architecture, CQRS, Saga/Temporal, Domain Events и Outbox/Inbox, но НЕ повтаряй тези лекции. Поставяй акцент върху бизнес граници и relationship decisions.

Обяснявай професионално, стъпка по стъпка с точни термини. Не измисляй production benchmarks. Завърши с code-review checklist и senior scenario: Claims има 100ms SLO, Contracts има 300ms p99, local projection е stale. Как избираме между надеждност, freshness и latency?
```

---

## P1 · Audio 3 — Practical Architecture Lab: Java 25 / Spring Boot 4

**Целева дължина:** около 20–30 минути, задълбочено упражнение, без четене на дълги кодови фрагменти.

### Prompt

```text
Превърни 03-LABS.md и свързаните Java файлове в практическа лекция за senior-oriented Java/Spring архитектурно code review.

Обясни executable архитектурата: Maven aggregator strategic-ddd, quoting-domain, contracts-domain, claims-domain и integration-lab. Подчертай защо домейните са независими pure-Java artifacts, а Spring Boot integration module е composition root и един-единствен deployable.

Проследи конкретния happy path: конфигурация EV-TOURING с опции WINTER и PANORAMA дава месечна оферта 462 EUR; отделно Contracts регистрира VIN; Claims иска собствен ContractFacts model. Обясни какво се чупи при shared universal Vehicle entity.

След това сравни NaiveConformistClaimsChecker с ContractToClaimsAclAdapter и покажи dependency direction. Дай практичен review въпрос: "Ако upstream enum се промени, кой code трябва да променим и защо?".

Разгледай RepeatedContractFactsProjection (правилното име във файловете е ReplicatedContractFactsProjection), contract revision monotonicity и stale-read window. Отбележи, че това е симулирана репликация чрез method call, НЕ истински брокер и НЕ durable съхранение.

Обясни как Maven dependencies и DomainBoundaryArchitectureTest (ArchUnit) защитават границите; покажи какъв опит за забранен import ще се провали.

Разкажи защо SpringWiringIntegrationTest доказва едно runtime приложение с различни модели, а не разпределена система. Не твърди реални network failures, crash recovery, DB isolation или latency measurements.

Предложи три практични упражнения за слушателя: промяна на upstream contract status, извличане на Contracts като HTTP adapter без промяна в Claims domain, и изчисление на freshness SLO за local projection.

Когато обясняваш код, обяснявай execution flow и правилата, а не чети символи. Завърши с пет технически въпроса за самопроверка без отговорите веднага.
```

---

## Проверка на завършването

Материалите **не се считат за усвоени**, само защото NotebookLM е генерирал аудио. След всяко аудио използваме текущия [диагностичен протокол](../../../docs/learning/ASSESSMENT-PROTOCOL.md): практичен въпрос, професионална корекция на термини и периодичен преговор. По този начин темата се интегрира в цялото education обучение.
