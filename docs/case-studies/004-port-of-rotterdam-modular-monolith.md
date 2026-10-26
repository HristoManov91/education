# Case Study 004 — Port of Rotterdam: от microservice overload към modular monoliths

> **P2 / Engineering Architecture Modernization Case Study**
>
> **Източник:** [Mario Klaver — Taming Microservice Overload: Our Journey to Modular Monoliths, Devoxx Belgium 2026](https://www.youtube.com/watch?v=v86SfHA5ISw) (около 50 минути). Лекторът е solution architect в Port of Rotterdam. Казусът е **описан от самия екип**, но не съдържа публично потвърдени измервания за крайното improvement.
>
> **Важно:** тези миграционни решения и числата за броя услуги са от лекцията. Примерните рискове, тестови сценарии и control gates са наш инженерeн анализ, а не твърдения, че Port of Rotterdam е реализирал точно тези тестове/метрики.

## 1. Симптом / контекст

Исторически системата на пристанището преминава от централизирано приложение към много малки микросървиси. Има основателни причини:

- различни екипи могат да release-ват независимо;
- определени части на системата могат да се мащабират отделно;
- техническият код се разделя на по-управляеми pieces.

С течение на времето микросървисите стават **над 90 за само 5 екипа**. Реалната operational тежест расте: библиотечни upgrades, runtime/containers, security fixes, deployment pipelines, observability, tracing, coordinating features и technical debt.

**Ключов извод:** микросървисите са решили първоначални проблеми, но **не гарантират**, че техническото разпределение следва бизнес отговорностите.

Числото 90 само по себе си **не е анти-pattern threshold**: проблемът е в съотношението с екипите, domain coupling, cadence, toil и dependency graph. Не го превръщай в универсално правило „не повече от N услуги“.

## 2. Root cause — „ясни имена, грешни граници“

### На диаграмата

```text
VesselEntryService:
  business mission — may vessel enter?

BerthSafetyService:
  business mission — can assigned berth accept vessel?
```

### В реалното поведение

Двата сервиса си разменят множество REST/events и се нуждаят един от друг за едно и също бизнес решение. Едното приложение изглежда „отговорно“, но другото фактически държи част от решението; промените преминават през много services/teams.

**Операционен индикатор:** една business change story изисква updates в 3–5 deployables, manual synchronization на API versions, coordinated releases и distributed traces за разбиране на прост процес.

Това не се поправя, ако добавим още REST gateway, Kafka topic или дадем нови имена на същите deployables.

## 3. Failed intuitions / неефективни подходи

1. **„Още по-малки услуги“** — ако делим по технически способности, можем да увеличим runtime/network/deployment overhead.
2. **„Service per technical capability“** — без business decision ownership изграждаме communication graph, не bounded contexts.
3. **„Мигрираме 1 стар microservice към 1 нов microservice“** — това запазва старите неправилни отговорности.
4. **„Kafka/event-driven премахва coupling-а“** — заменя synchronous calls, но event schema/semantics, ordering и availability остават.
5. **„Сливаме всички в един jar“** — ако няма ограничения върху internal imports и module APIs, връщаме стария unstructured monolith.

## 4. Какво избират

### A. Повторно откриване на бизнес граници

С **Big Picture EventStorming** бизнес експерти, operations и engineers описват събития, участници, pivotal transitions, domain capabilities и изключения. От получените факти екипът дефинира кандидат subdomains, които подлежат на валидация, вместо просто да запази service inventory.

### B. По-малко deployables, повече explicit modules

Когато domain boundaries още се уточняват, е по-евтино да бъдат локални **application modules**, които могат да се преместват между packages без distributed migration. **Spring Modulith** въвежда module APIs, named interfaces, dependency verification и module tests.

**Не твърдим „1 subdomain винаги означава 1 deployment“** като DDD правило. Това е временна **архитектурна стратегия**, избрана в описания case за по-лесно изменение и възможно бъдещо извличане.

### C. Platform engineering/starter conventions

Лекторът описва platform engineering усилие за стандартни building blocks: security, messaging, inbox/outbox, observability. Това намалява repetitive setup, но не бива да превръща business models в споделена enterprise common library.

**Platform engineering не замества ясното model ownership.** Първото улеснява execution на инфраструктурни concerns; второто определя кой взема бизнес решение и кой може да го променя.

## 5. Поетапна миграция без спиране на 24/7 системата

Продукцията не може просто да бъде изключена и преизградена; „магазинът трябва да остане отворен“. Лекторът разграничава няколко случая с нарастваща сложност.

### Сценарий A — нова функция към правилния нов домейн

Нова ISPS-related assessment (в лекцията) има ясна отговорност в новата Port Clearance зона. Новият модул слуша подходящи събития от legacy потока, изчислява резултат и го подава на clearance decision.

**Най-лесният migration pattern:** не пренасяме непременно стар код; пишем новата capability правилно и създаваме временен integration adapter към legacy event stream.

**Рискове:** dual sources of truth, event latency, schema drift, rollback. Измери behavior equivalence и потвърди реалните business rules.

### Сценарий B — консолидиране на „женени“ услуги

Две services, които се извикват многократно за едно решение, се прегрупират в един deployable с **различими internal modules**.

Първо е възможно временно да се запазят съществуващи messaging flows, за да не се променят паралелно всички contracts. След миграцията се премахват излишните network hops **само след regression проверки**.

**Senior nuance:** по-малко мрежови calls може да намалят латентност, но не е доказателство за автоматично по-коректни транзакции или съгласуван модел.

### Сценарий C — една техническа услуга съдържа функции от много subdomains

Това е най-трудният случай. Не „мигрираме Service A“, а **преместваме capability X**, която преди това е била разпръсната в A, B, C и D. Останалите responsibilities остават временно в legacy system.

Изисква explicit migration mapping, business tests, source-of-truth transition и anti-corruption/compatibility adapters.

```text
Old:
service A = capability X + Y
service B = capability X + Z
service C = capability X + W

Transition:
new CapabilityX module ← phased data/code/contract migration
old A/B/C still own Y/Z/W

End state:
new capability module owns X
old X implementations retired only after controlled cutover
```

**Най-важният извод:** миграционната единица често е **бизнес capability**, не microservice artifact.

## 6. Кои рискове бих изисквал да обсъдим преди реална миграция?

Това е наш checklist, не твърдение за процеса в Rotterdam:

| Риск | Какво може да стане | Праг за cutover |
| --- | --- | --- |
| Legacy event delay/duplicates | Старото и новото decision state се разминават | Idempotency, ordering/freshness tests |
| Data ownership | Две системи приемат writes за същите факти | Explicit single writer / ownership switch |
| Partial migration | Половината workflow в стара, половината в нова система | Contract/integration tests на end-to-end flow |
| Rollback | След cutover старата система вече не разбира данните | Tested rollback/forward fix plan |
| Operational visibility | Не можем да различим legacy/new errors | Correlation IDs, traces, alerts и runbook |
| Performance | Премахваме мрежови hops, но DB becomes bottleneck | Benchmark Correctness + production telemetry |
| Compliance | Важен domain constraint се изпуска | Signed-off business acceptance scenarios |

Свързани наши материали: [Context Mapping/ACL](../../spring/architecture/strategic-ddd/02-CONTEXT-MAPPING.md), [Outbox/Inbox](../../spring/messaging/outbox-inbox/README.md), [Saga](../../spring/messaging/saga/README.md), [Benchmark Correctness](../../java/jvm/performance-evolution/BENCHMARK-CORRECTNESS.md).

## 7. Доказани резултати срещу хипотези

Лекторът изрично казва, че към момента на презентацията **няма количествена оценка**, която да доказва очакваното подобрение. Екипът очаква по-малко координация, по-бързи промени и по-ниска поддръжка, но това са **хипотези, не измерени ефекти**.

Не съобщаваме измислено „60% по-бърз delivery“ или „50% по-ниски разходи“.

### Предложена measurement strategy (наша)

| Hypothesis | Leading indicators | Outcome / decision signal |
| --- | --- | --- |
| По-локални промени | Брой service/module changes per business story | Намален median/p90 lead time for change |
| По-малко operational toil | Components updated per JDK upgrade, manual rollouts, alerts | Engineer-hours per dependency/security release |
| По-малко runtime coupling | Cross-service sync hops и coordinated deployments | Failed deployments/change failure rate |
| По-добро ownership | Unassigned business rules, спорни decision rights | Incident triage time, ownership reassignments |
| Добра еволюция на модулите | Internal imports и cycles (test verification) | Modularity rules remain green across releases |

**Не пренаписвай baseline**, за да изглежда подобрението по-голямо. Събирай data от before/after periods с подобен traffic и comparable team/project constraints; migration process itself temporarily raises costs.

## 8. Recognition checklist: кога този case е приложим?

Приложим е, когато:
- едни и същи екипи притежават твърде много deployables, без достатъчна бизнес автономия;
- много services постоянно участват в едно decision;
- release lead time се влияе от cross-service coordination;
- домейните са разкъсани между services и няма authoritative ownership;
- CI/deploy/security/observability overhead вече е осезаем.

**Не** е директна рецепта за система, в която:
- много независими екипи наистина release-ват автономно;
- compliance/tenant/data residency налагат physical isolation;
- service-specific scaling носи измерима полза;
- различни runtime technology requirements налагат separation;
- migration cost и regression risk надхвърлят business benefit.

**Правилният архитектурен въпрос не е „микросървиси или монолит?“, а „кои boundaries могат да бъдат по-евтино управлявани заедно, без да губим needed autonomy?“**

## 9. Reproduction & learning materials

- [EventStorming Workshop P1](../../spring/architecture/strategic-ddd/04-EVENTSTORMING-WORKSHOP.md) — discovery на boundaries.
- [Spring Modulith P1 lesson](../../spring/architecture/strategic-ddd/05-SPRING-MODULITH.md) — build-enforced module boundaries.
- [Executable modulith-lab](../../spring/architecture/strategic-ddd/modulith-lab/README.md) — три модула, events, standalone module tests, architecture verification.

**Нашата лаборатория не репликира** 90-сървисна production система, реални миграции, data cutover или telemetry. Тя доказва **структурата и in-process interaction semantics** на учебните packages.

## 10. Изходна инженерна преценка

> „Първо откриваме действителните business decision boundaries. После организираме код и модули, чиито dependencies са автоматично проверими. Разделяме в допълнителни deployments само когато конкретните екипни, регулаторни, runtime или scalability изисквания оправдават distributed operational cost.“

Това е **конкретният урок от Rotterdam**, който си струва да запазим дългосрочно.

## Оригинални източници

1. [Mario Klaver, Devoxx Belgium 2026 — Taming Microservice Overload](https://www.youtube.com/watch?v=v86SfHA5ISw).
2. [Conference description / speaker — Port of Rotterdam](https://amsterdam.voxxeddays.com/talk/?id=9506).
3. [EventStorming — Alberto Brandolini](https://www.eventstorming.com/).
4. [Spring Modulith official reference](https://docs.spring.io/spring-modulith/reference/).
5. [Spring Modulith application module testing](https://docs.spring.io/spring-modulith/reference/testing.html).
