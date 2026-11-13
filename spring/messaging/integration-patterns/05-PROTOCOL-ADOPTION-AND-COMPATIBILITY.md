# P1 · Protocol Adoption: Deploy Readers Before Writers

> **Java 25 + JUnit 5 + JSON wire roundtrip с вече наличния test-scoped Jackson 3.** Източник: [Guust Ysebie — Engineering Lessons from Working on Software Older Than Me](https://www.youtube.com/watch?v=VaT7DgmVSms), Devoxx Belgium 2026, предоставен SRT, около **08–12 и 14–21 минути**. Лекторът говори за adoption на формати и PDF readers/writers; **нашата Java лаборатория е собствено приложение на принципа към събития**, не demo от презентацията.

## 1. Казус: новият формат е по-добър, но старите consumers още съществуват

Java/Spring Order Service публикува събитие с versioned JSON payload. Ново изискване добавя `currency`, защото освен EUR вече обработваме USD. При **V1** няма поле `currency` и неговата исторически договорена стойност е **EUR**. **V2** добавя explicit `currency`.

**Bad:** producer започва да публикува V2 веднага след внедряване на кода, защото новата версия минава unit tests. Старият строг consumer отхвърля полето или не разбира новата версия. По-опасен вариант: изпращаме USD като V1, а стар consumer го прочита като EUR — **тиха бизнес корупция**.

**Good:** надграждаме readers, преди да активираме writers; поддържаме capability registry/compatibility matrix и feature flag, а неподдържана V2 функция **fail-ва**, вместо да се downgrade-не тихо.

## 2. Реалният Java25 model и contract

[OrderEventEvolution.java](src/main/java/bg/hristomanov/education/integrationpatterns/evolution/OrderEventEvolution.java) е Java-only модел: две wire versions, синтетични orders, receiver capabilities и staged producer. Няма network discovery и няма Kafka: това **не е production capability registry**.

~~~json
// V1 — conceptual: implicit EUR; comments are not valid JSON
{"schemaVersion":1,"orderId":"ORD-10","amountCents":1250}
~~~

~~~json
{"schemaVersion":2,"orderId":"ORD-11","amountCents":1750,"currency":"USD"}
~~~

В JUnit тестовете map payload-ите се **сериализират и десериализират като реален JSON чрез Jackson**, вместо да сравняваме само Java objects. Няма нов runtime dependency.

Старият consumer умишлено има **strict schema** (exact field set). Някои други съществуващи consumers са tolerant към добавени полета; **не предполагай**, че всички клиенти се държат като нашия. Съществуващият [AsyncAPI contract test](03-ASYNCAPI-CONTRACT-FIRST.md) вече доказва optional-field compatibility в schema. **Новото в този урок е rollout стратегията**, mixed recipients и fail-closed semantic downgrade.

## 3. Четирите compatibility комбинации

| Producer → Consumer | Резултат в нашия executable lab | Защо |
| --- | --- | --- |
| **Old → Old** | PASS | V1 payload; implicit EUR semantics |
| **Old → New** | PASS | V2 consumer първо се разширява да чете и V1 |
| **New V2 → Old** | **EXPECTED FAIL** | Строгият V1 consumer отхвърля V2 schema; затова не активираме V2 за него |
| **New V2 → New** | PASS | V2 с explicit currency; USD става валиден |

### По-важно от матрицата: безопасната поредица

~~~text
Step 0: all V1 producers/consumers → V1 only
Step 1: upgrade ALL consumers to V1+V2 readers; producer still emits V1
Step 2: enable V2 writer only after verified recipients/capabilities
Step 3: observe adoption, errors, dead-letter queue and rollback readiness
Step 4: stop emitting V1 only when no legacy recipients require it
~~~

Когато има Kafka fan-out към два consumers, writer трябва да използва **сечението на поддържаните версии**, а не union. В [`commonFormats`](src/main/java/bg/hristomanov/education/integrationpatterns/evolution/OrderEventEvolution.java) за recipients `{V1}` и `{V1,V2}` общият формат е **V1**. След миграцията на всички става `{V1,V2}` и producer може да включи V2.

В реална система **Kafka няма автоматично да предостави нашия Set<Format> contract**: capability registry, deployment guarantees, consumer groups и schema registry/compatibility policies се проектират отделно. При retained messages и replay readers трябва да продължат да четат V1, дори когато producer вече пише V2.

## 4. README → код → доказателство

| Scenario | Test |
| --- | --- |
| Четирите комбинации, с реален JSON roundtrip | [OrderEventEvolutionTest.java](src/test/java/bg/hristomanov/education/integrationpatterns/evolution/OrderEventEvolutionTest.java) |
| Mixed recipients → fallback V1 → upgrade всички → V2 | `mixedConsumersForceV1UntilTheLastOldConsumerIsMigrated` |
| Feature flag OFF, въпреки поддържан V2 | `featureFlagOffPreservesV1EvenIfAllConsumersSupportV2` |
| USD не се „превръща“ мълчаливо в EUR при V1 fallback | `disablingV2AfterPublishingUsdMustNotSilentlyChangeCurrency` |
| Неизвестен формат, празен recipient registry или несъвместими версии | `producerFailsClosedWhenThereIsNoSharedFormat`, `missingRecipientsCannotBeAssumedCompatible` |
| Decimal truncation, некоректни payload fields/currency | negative JUnit тестове; require exact field set |

Изпълни от корена:

~~~bash
mvn -pl spring/messaging/integration-patterns \
  -Dtest=OrderEventEvolutionTest test

# или за всички integration pattern contracts и симулации:
mvn -pl spring/messaging/integration-patterns verify
~~~

**Не поставяй тук `@JsonIgnoreProperties(ignoreUnknown=true)` като универсален fix**. Това може да е разумна политика за *добавени* optional fields, но не решава breaking semantic changes, enum behavior, amount units или schema drift. За този пример е необходим изричен version contract.

## 5. Correctness vs actual adoption

Зеленият producer unit test доказва, че **един** producer може да кодира V2. Ние искаме доказателство, че:
- всички засегнати consumers са обновени или имат подходящ fallback;
- старите stored events могат да бъдат replay-нати през новите readers;
- emitted V2 е **забранено** докато има V1-only subscriber;
- metrics: V1/V2 traffic, decode failure, unknown payload, latency, dead letters и rollback;
- feature flag rollback не подменя USD като EUR; изисква безопасен отказ или отделна договорена стратегия.

**Не обещаваме 100% availability**, докато няма действителен Kafka deployment, registration, integration tests и реален consumer inventory. Тази лаборатория доказва **чистата contract/rollout логика** с Java25/JUnit, не network topology.

## 6. Decision contract

**Проблем:** независимо внедрявани producers и consumers не могат синхронно да сменят договор.

**Кога:** event schema evolution, REST API versioning, file-format readers/writers и преходи със стари клиенти.

**Кога НЕ:** single-process API, където няма backward compatibility изискване; не усложнявай local method calls с capability registry.

**Печалба:** контролирано старо/ново поведение, rollout visibility, по-малък риск от data corruption.

**Цена:** едновременно поддържане на V1/V2, operational inventory, тестове/флагове, version lifecycle и telemetry.

**По-просто:** добави optional field само ако **всички** реални consumers са tolerant и semantics не се променят; иначе readers-first migration.

**Proof:** JUnit contract matrix и negative tests; [AsyncAPI](03-ASYNCAPI-CONTRACT-FIRST.md), [Spring HTTP API versioning](../../boot4/03-API-VERSIONING-RESILIENCE.md).

## Източници

- [Guust Ysebie — Devoxx](https://www.youtube.com/watch?v=VaT7DgmVSms), предоставен SRT (08–12, 14–21).
- [AsyncAPI 3.1.0 specification](https://www.asyncapi.com/docs/reference/specification/v3.1.0).
- [Consumer-Driven Contracts — Martin Fowler](https://martinfowler.com/articles/consumerDrivenContracts.html).
