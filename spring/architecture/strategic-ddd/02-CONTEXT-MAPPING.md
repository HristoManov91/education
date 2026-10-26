# 1.2 — Context Mapping, Anti-Corruption Layers & Integration Relationships

> **Tech Radar → P1 / Deep Dive · 25–35 минути · Mid+ → Early Senior**
>
> Видео: [20:33–30:40 — зависимости и Context Mapping](https://www.youtube.com/watch?v=eFSEg54mwVU&t=1233s), [33:06–40:00 — communication & processes](https://www.youtube.com/watch?v=eFSEg54mwVU&t=1986s).
>
> Предпоставка: [1.1 — Strategic DDD и Bounded Contexts](01-STRATEGIC-DDD-AND-BOUNDARIES.md).

## 1. Реалният казус: „Service A извиква B“ не казва кой зависи от кого

На dashboard имаме dependency diagram:

```text
Claims Service ──GET /contracts/{id}──► Contracts Service
```

От това можем да видим **runtime communication direction**, но не и:

- Кой business outcome зависи от качеството на данните?
- Кой е upstream за **модела и промените**?
- Кой определя contract schema и има право да откаже change?
- Кой ще понесе schema/API migration при нова законова дефиниция?
- Има ли действително нужда от synchronous request при всяка щета?

**Context Mapping** картографира тези **model/dependency relationships**; service call graph не е контекстна карта.

## 2. Upstream & Downstream не са HTTP client/server

**Downstream**: контекстът, който **трябва да се адаптира** към upstream model/decisions, за да реализира собственото си поведение.

**Upstream**: контекстът, който определя published language, capabilities или модел, към който downstream трябва да се съобрази.

Посоката не е непременно същата като network call direction.

```text
         schema/semantic influence
Contracts (upstream) ───────────► Claims (downstream)

Claims (HTTP caller) ──GET──────► Contracts (HTTP server)
```

Дори ако Contracts **публикува event** към Claims, посоката на model influence остава същата. Broker заменя транспортната зависимост, но не премахва **schema ownership**.

В различни integration relationships ролите могат да се сменят. Не обозначавай целия service като „винаги upstream“ за всички способности.

## 3. Основните relations, които трябва да различаваме

| Relationship | Кой определя модела? | Кой се адаптира? | Кога е оправдано? |
| --- | --- | --- | --- |
| **Conformist** | Upstream | Downstream приема upstream language/model | Малък, стабилен downstream; приемаме coupling-а |
| **Anti-Corruption Layer (ACL)** | Всеки контекст има собствен модел | Adapter превежда upstream → downstream | Domain meanings са различни, downstream трябва да е защитен |
| **Customer–Supplier** | Upstream supplier; downstream има negotiated requirements | Teams планират published contract | Downstream има право да влияе на upstream roadmap |
| **Shared Kernel** | Два teams споделят малка част | И двата координират промени | Семантиката действително е обща и стабилна |
| **Partnership** | Взаимна координация | Два екипа поемат общ delivery outcome | Тясна бизнес взаимозависимост |

**Други полезни patterns:** Open Host Service и Published Language (стабилен публичен integration contract), Separate Ways (без integration, когато цената надхвърля ползата), Big Ball of Mud (legacy boundary, който трябва да защитаваме). Не е нужно да прилагаме всички имена; нужно е да разбираме отношенията.

### 3.1 Conformist — простотата понякога печели

**Тестовият BAD/Conformist baseline** импортва директно upstream types:

[NaiveConformistClaimsChecker.java](integration-lab/src/test/java/bg/hristomanov/education/strategicddd/integration/bad/NaiveConformistClaimsChecker.java)

```java
boolean isEligible(ContractSnapshot external) {
    return external.status() == ContractStatus.ACTIVE;
}
```

Това може да бъде **съзнателно допустимо**, когато downstream приема чуждата семантика, upstream model е стабилен и цената на превод не е оправдана.

**Цената:** при промяна на ContractStatus Claims трябва да се адаптира. Ако domain rules се развиват независимо, получаваме скъп coupling.

Conformist не означава „винаги лош“. Лошо е **несъзнателно** да вярваме, че асинхронният transport осигурява автономия на модела.

### 3.2 Anti-Corruption Layer — semantic isolation, не DTO ceremony

Нашата учебна реализация:

```text
Contracts ContractSnapshot + ContractStatus
             |
             v
ContractToClaimsAclAdapter (integration boundary)
             |
             v
Claims ContractFacts(agreementId, coveredAssetVin,
                    contractActive, upstreamRevision)
             |
             v
ClaimsEligibilityService
```

Код: [ContractToClaimsAclAdapter.java](integration-lab/src/main/java/bg/hristomanov/education/strategicddd/integration/ContractToClaimsAclAdapter.java).

**Claims domain** import-ва единствено [ContractFactsPort](claims-domain/src/main/java/bg/hristomanov/education/strategicddd/claims/ContractFactsPort.java) и [ContractFacts](claims-domain/src/main/java/bg/hristomanov/education/strategicddd/claims/ContractFacts.java).

Когато upstream добави поле за customer segment, Claims **няма причина да знае**. Ако upstream промени meaning на ACTIVE, adapter-ът трябва да се преразгледа — ACL **не** е магия срещу semantic breaking changes.

**GoF Adapter vs DDD ACL:** GoF Adapter превежда интерфейси; ACL може да превежда **термини, statuses, invariants, units, lifecycle и error semantics**.

### 3.3 Customer–Supplier — downstream има influence

Ако Claims е критичен клиент на Contracts, можем да договорим published facts с ясни compatibility правила.

Конкретно договорете:

- schema versioning и deprecated fields;
- compatible enum/status semantics;
- период на предварително известяване;
- consumer-driven contract tests;
- error contract, SLO и owner team за breaking changes.

### 3.4 Shared Kernel — координация по изключение

Минимален Shared Kernel може да съдържа стабилен value object за currency, **ако значението наистина е идентично**.

**Не** слагай целия Vehicle, Contract или JPA model в enterprise shared library. Shared Kernel означава shared change ownership и ограничение за независими releases.

### 3.5 Неудобната context map

```text
Service A ─ model influence ─► B ─ shared kernel ─ C ◄─ model influence ─ D
```

Ако B/C си делят голям kernel, а A/D ги принуждават към несъвместими промени, възниква **semantic change conflict**. На network graph това изглежда като обикновени извиквания; контекстната карта показва dependency ownership.

## 4. Правилните въпроси преди избор на транспорт

1. **What needs to be decided?** Кое бизнес решение и с какви invariants?
2. **Source of truth:** кой притежава фактите?
3. **Model influence:** кой се адаптира при промяна на семантиката?
4. **Decision rights:** имаме ли право да променяме upstream contract?
5. **Availability/freshness:** трябва ли резултатът да е максимално актуален?
6. **Failure semantics:** ако upstream е недостъпен, спира ли този use case?
7. **Evolution:** как ще мигрираме при независими deployments?

**Relationship first → transport second.**

## 5. Data replication или synchronous calls?

Лекторът посочва, че многобройните synchronous reads могат да показват, че **нужните за локалното решение факти не са вътре в границата**.

| Вариант | Полза | Цена |
| --- | --- | --- |
| **Synchronous upstream lookup** | Достъп до latest known upstream facts | Runtime coupling, latency, retry/timeout и availability dependency |
| **Replicated local read model** | Local decision, по-ниска runtime coupling | Eventual consistency, backfill, ordering, schema evolution, privacy |
| **Shared read-only reference DB** | Полезно за стабилни, versioned справочни данни | Schema/SQL/permissions coupling и vendor lock-in |

### Реализирано A: synchronous ACL

[ContractToClaimsAclAdapter](integration-lab/src/main/java/bg/hristomanov/education/strategicddd/integration/ContractToClaimsAclAdapter.java) чете [ContractRegistry](contracts-domain/src/main/java/bg/hristomanov/education/strategicddd/contracts/ContractRegistry.java) и превежда към Claims vocabulary. В лабораторията е in-process; при физическо разделяне би бил HTTP/gRPC adapter.

### Реализирано B: claims-owned projection

[ReplicatedContractFactsProjection](integration-lab/src/main/java/bg/hristomanov/education/strategicddd/integration/ReplicatedContractFactsProjection.java) пази само нужните на Claims facts:

```text
ContractRegistry (authoritative)
   └─ publication (lab: method call; prod: event/outbox)
      └─ ACL translation
         └─ Claims local projection (agreementId, coveredAssetVin, active, revision)
            └─ ClaimsEligibilityService (no synchronous Contracts lookup)
```

**Тестът доказва:** ContractRegistry преминава ACTIVE → TERMINATED, но projection остава стара, докато новото event не пристигне. По-стар revision не може да overwrite-не по-нов; duplicate delivery е безопасно за тази локална in-memory операция.

**Не доказва:** transactional publication, at-least-once event delivery, persistent offset, restart, complete domain correctness или schema migration. За тези гаранции използвай [Outbox/Inbox](../../messaging/outbox-inbox/README.md).

**Mental model:** copied data ≠ duplicate ownership. Contracts притежава truth, Claims държи **derived local projection** с explicit freshness contract.

## 6. Transport следва relationship и consistency requirements

```text
In-process calls → common failure/transaction boundary, ниска latency
Synchronous API → request/reply, но availability coupling
Async events     → decoupled scheduling, но delivery/schema coupling
Replication      → local decisions, но stale data и recovery policy
```

Няма технология, която автоматично премахва организационните зависимости.

За long-running workflows вече имаме:
- [Saga](../../messaging/saga/README.md): choreography/orchestration и compensations;
- [Temporal Deep Dive](../../messaging/saga/TEMPORAL-CANCELLATION-DEEP-DIVE.md): cancellation races.

Задачата на Strategic DDD е **кои bounded contexts участват и кой притежава домейн решението**, не как да имплементираме Saga повторно.

**Нюанс:** central orchestrator е logical coordination point, но не задължително единична физическа точка на отказ.

## 7. BFF, service layers и един service „само като база“

Лекцията предупреждава да не превръщаме BFF → domain API → data API в задължителна техническа йерархия. BFF често е consumer-specific, а domain-oriented service защитава business model. Нов use case трябва да се постави според **ownership**, не да се избутва механично през слоевете.

**Друга спорна, но полезна идея:** read-only reference database понякога може да се предлага пряко на доверени consumers. Това е допустимо само с ясни read permissions, versioned schema, query limits и стабилен owner. Shared write database между независими teams създава тежък coupling. Не превръщай това в default архитектура.

## 8. Упражнение: нарисувай context map

Идва fleet partner с нови status codes. Contracts обслужва няколко teams, а Claims трябва да решава дали да допусне case до review.

Сравни:

- **Conformist:** Claims приема upstream statuses.
- **ACL:** Claims запазва собствена vocabulary; adapter превежда.
- **Customer–Supplier:** Teams договарят versioned published language.

Отговори на:
1. Кой е upstream, независимо от HTTP caller?
2. Какво трябва да се промени във facts owner?
3. При кой вариант ClaimsEligibilityService трябва да import-ва ContractStatus?
4. При 30 секунди event lag допустимо ли е да допуснем review на terminated contract?
5. Как monitor-ваш lag и mismatch? Каква е repair policy?

Код за сравнение: [ConformistComparisonTest](integration-lab/src/test/java/bg/hristomanov/education/strategicddd/integration/ConformistComparisonTest.java) и [StrategicDddScenarioTest](integration-lab/src/test/java/bg/hristomanov/education/strategicddd/integration/StrategicDddScenarioTest.java).

## 9. Senior-level code review

- [ ] Разграничена ли е посоката на HTTP/events от model influence?
- [ ] Downstream има ли собствен vocabulary, ако semantics са различни?
- [ ] Published facts имат ли owner, versioning и compatibility policy?
- [ ] ACL е един ясно именуван integration boundary?
- [ ] Max staleness изрично дефиниран ли е за business use case?
- [ ] Replica има ли dedupe, ordering, replay, monitoring и privacy controls?
- [ ] Shared Kernel умишлено малък ли е?
- [ ] Погледнахме ли дали in-process modular model ще е по-евтин?

**Design challenge:** Partner иска до 100 ms решение, Contracts API p99 е 300 ms, понякога 5xx. Local projection може да реши latency, но е stale. Ако действието е финансово необратимо, какви допълнителни проверки или компенсации изискваш?

**Изход:** Context Mapping е **карта на промяната и влиянието**; Conformist приема чужд модел; ACL защитава свой; local projection купува автономия срещу eventual-consistency отговорности.

Следва: [1.3 — изпълнима Java/Spring лаборатория](03-LABS.md).

## Оригинални източници

- [Devoxx Belgium 2026](https://www.youtube.com/watch?v=eFSEg54mwVU)
- [DDD Crew — Context Mapping catalog](https://github.com/ddd-crew/context-mapping)
- [DDD Crew — relationship definitions](https://virtualddd.com/ddd-crew/context-mapping/)
- [Martin Fowler — Bounded Context](https://martinfowler.com/bliki/BoundedContext.html)
- [Microservices.io — Transactional Outbox](https://microservices.io/patterns/data/transactional-outbox.html)
