# 1.4 — EventStorming: откриване на бизнес граници преди писане на Java код

> **P1 / Strategic DDD Workshop · 30–40 минути теория + 45–60 минути практическа работа**
>
> Изходна лекция: [Mario Klaver — Taming Microservice Overload: Our Journey to Modular Monoliths, Devoxx Belgium 2026](https://www.youtube.com/watch?v=v86SfHA5ISw), [18:00–27:00](https://youtu.be/v86SfHA5ISw?t=1080). 
>
> Допълва [1.1 Bounded Contexts](01-STRATEGIC-DDD-AND-BOUNDARIES.md) и [1.2 Context Mapping](02-CONTEXT-MAPPING.md). **Не** преразказваме повторно DDD дефинициите: тук учим **как се откриват** границите и как те се валидират с хора от бизнеса.

## 1. Входен казус — защо диаграмата с микросървиси ни заблуждава

Имаме платформа, в която:

```text
VesselEntryService  → BerthSafetyService → PortClearanceService
        ↑                  ↘           ↗
        └── 3 REST calls ── MessageBroker
```

Екипите са кръстили приложенията според привидни отговорности, но *решението дали кораб може да влезе* фактически се взема от правила и информация, разпръснати между три deployables. При всяка промяна има координация, version drift и разследване по мрежата.

**BAD подход:** архитекти се събират сами и за 30 минути рисуват „новите“ services върху съществуващата техническа диаграма. Така често пренасят старите грешни граници.

**GOOD подход:** събираме хората, които познават **реалния бизнес процес**, преди да решим къде ще има Spring beans, Kafka topics, отделни Maven модули или deployments.

EventStorming не е автоматичен генератор на bounded contexts; той е **структурирана колективна дискусия**, в която неизвестните и разминаванията в бизнес разбирането стават видими.

## 2. Какво е EventStorming и кое ниво ни трябва?

Методът е създаден от [Alberto Brandolini](https://www.eventstorming.com/) и има три характерни мащаба:

| Ниво | Основен въпрос | Артефакт | Нашият избор |
| --- | --- | --- | --- |
| **Big Picture** | Какво се случва изобщо в организацията и къде се сменят отговорностите? | Времева линия от domain events, hotspots, pivotal events, actors, кандидати за subdomains | **Първата стъпка** |
| **Process Modelling** | Как работи конкретен end-to-end сценарий, включително разклонения, policies и exceptions? | Commands, read models, failure branches, downstream влияния | **Втора стъпка** |
| **Software Design** | Как моделираме consistency boundary, aggregates и API-та вътре в избрана зона? | Domain objects, invariants, commands, transaction/model boundaries | Само след разбиране на процеса |

**Честа грешка:** започваме от Aggregate и Repository, без да сме установили дали въобще сме избрали правилния бизнес контекст.

## 3. Workshop setup — кого каним и как го провеждаме

### Участници (примерна учебна организация)

- бизнес експерт по maritime operations и clearance;
- специалист по health declaration / проверки;
- експерт по dangerous goods requirements;
- операционен служител, който вижда реалните изключения;
- 1–2 Java/backend разработчици и 1 архитект;
- facilitator, който задава въпроси и не налага предварително своето решение.

**Не е необходимо** всеки да знае DDD, Java или какво е Spring Modulith. Важно е да могат да разкажат *какво се е случило и кой носи отговорност*.

В истински workshop може да ползваш огромна стена, хартия и лепящи листчета, или виртуално табло. Нотацията е средство за разговор, не цел сама по себе си.

### Starter legend — базови обозначения

| Визуален знак | Какво означава | Пример |
| --- | --- | --- |
| **Оранжев domain event** — факт в минало време | Business outcome, който *вече се е случил* | `Health Declaration Submitted` |
| **Син command** | Действие/намерение, което искаме да извършим | `Assess Dangerous Goods` |
| **Жълт actor** | Човек/роля, която започва действие | `Harbour Officer` |
| **Лилава policy** | Правило, което реагира на настъпил факт | `Once both assessments complete, evaluate clearance` |
| **Розов hotspot** | Спор, неизвестно, несигурност, failure, missing information | `Who decides if documentation is incomplete?` |
| **Pivotal event / vertical marker** | Значима промяна в бизнес процеса/phase | `Clearance Decision Recorded` |

Цветовете са помощни conventions; не твърдим, че във всички фасилитации се използва абсолютно еднакъв набор.

## 4. Big Picture — изпълни упражнението със собствен екип

**Важно:** примерният workflow долу е **илюстративен, написан за education**. Това не е официална процедура или нормативна спецификация на Port of Rotterdam.

### Стъпка A — chaotic exploration (5–10 минути)

Дай на участниците една задача:

> Запишете събития, които **вече са се случили** в жизнения цикъл на vessel clearance. Засега не се опитвайте да ги подреждате или да именувате microservices.

Примерни факти:

```text
Vessel Arrival Requested
Health Declaration Submitted
Health Assessment Completed
Dangerous Goods Declaration Submitted
Dangerous Goods Assessment Completed
Berth Assigned
Operational Clearance Granted
Operational Clearance Refused
Vessel Entry Recorded
```

**BAD:** `Call HealthService API`, `Store VesselEntity`, `Kafka Message Sent` — това са технически стъпки/операции, не непременно domain events.

**GOOD:** `Health Assessment Completed`, защото описва business fact, който има значение независимо от технологията.

### Стъпка B — timeline и missing branches (10 минути)

Подредете фактите отляво надясно и разкажете реален щастлив сценарий, след това откази/изключения.

```text
Request Received
  ↓
Health Declaration Submitted ──► Health Assessment Completed
  │
  └─ Dangerous Goods Declared ──► Goods Assessment Completed
                                      │
                     both outcomes ──┘
                                      ↓
                             Clearance Decision Recorded
                                      ↓
                             Vessel Entry Accepted/Rejected
```

Попитай:
- Кога и кой *има право* да вземе крайното решение?
- Какво става при непълна декларация?
- Може ли решението да бъде отменено след нови факти?
- Кое е **decision**, кое е **observation**, кое е **request**?
- Трябва ли health и dangerous goods да приключват успоредно?
- Има ли законово/организационно изискване за human approval?

Ако участниците не са съгласни, постави **hotspot**, а не „временно измислено“ правило, което по-късно ще изглежда като факт.

### Стъпка C — pivotal events и actors (10 минути)

Открийте моментите, които променят business phase: submission, assessment acceptance/rejection, final clearance, physical entry. Маркирайте actor ownership и места, където решенията зависят от външна информация.

**Pivotal event не означава автоматично „нов микросървис“.** Той може да показва важен преход, екипна отговорност, или просто business milestone вътре в един контекст.

### Стъпка D — clusters и candidate boundaries (10–15 минути)

Групирайте свързаните events по единен език, правила и ownership:

```text
[Health Assessment]
  - declaration received
  - condition evaluated
  - assessment completed

[Dangerous Goods]
  - goods declared
  - documentation checked
  - goods assessment completed

[Operational Clearance]
  - prerequisites collected
  - clearance granted / denied
  - decision communicated
```

Това са **candidate business capabilities**. По-късно уточнете дали представляват bounded contexts, application modules вътре в един bounded context или други разграничения. Няма универсално mapping от всеки cluster към service.

## 5. Process-level follow-up — от sticky note до въпрос за consistency

Вземи hot spot: „Dangerous Goods Assessment се променя след като Clearance е даден“.

### Разгледай decision ownership

- **Command:** `Reevaluate Clearance`
- **Triggering domain fact:** `Dangerous Goods Assessment Changed`
- **Policy:** `Any negative prerequisite means clearance cannot remain approved` — *само ако е потвърдено от реални domain owners*
- **Read model:** latest known health/goods outcomes; freshness и audit semantics
- **Business decision:** `Clearance Revoked`, ако правилата изискват това
- **Failure modes:** late event, duplicate, out-of-order, broker outage, lost record

Това директно свързва семантиката на EventStorming с нашите [Context Mapping](02-CONTEXT-MAPPING.md), [Outbox/Inbox](../../messaging/outbox-inbox/README.md) и [Domain Events](../../events/domain-events/README.md) материали.

**Критично:** event storming domain event и `@EventListener`/Kafka integration event са различни нива. Добрият бизнес модел може да се имплементира със synchronous method call, Spring in-process events или брокер според техническия contract, но не се дефинира от транспорта.

## 6. От workshop до Spring Modulith

След валидирането на clusters, в [Spring Modulith lab](05-SPRING-MODULITH.md) моделираме и тестваме:

```text
health module  ── [HealthAssessed event] ──────┐
                                               ├─► clearance module (owned decision)
dangerousgoods ── [DangerousGoodsAssessed] ────┘
```

Границите се налагат чрез `@ApplicationModule`, `@NamedInterface` и `ApplicationModules.verify()`, не само с package диаграма.

Нашият example **не е доказателство за валиден бизнес алгоритъм за реално пристанище**. Целта е да научим как се прави преход от бизнес разказ към архитектурно защитими зависимости.

## 7. Senior exercises

1. **Покажи три нови domain events**, които липсват в timeline, и кажи кой експерт трябва да потвърди семантиката им.
2. Две съществуващи услуги се извикват циклично. Доказва ли това, че трябва да бъдат един module? Какви още въпроси имаш?
3. „Health Assessment Completed“ кой го притежава и може ли clearance да редактира неговия резултат?
4. Ако модулите са заедно в един process, но с различен бизнес речник, това един bounded context ли е или два? Обясни защо deployment и BC не са равнозначни.
5. Какво ще направиш, ако два екипа не са съгласни къде е responsibility boundary?
6. Кога бихме разпределили един модул в отделен deployable: ownership, compliance, независима промяна, capacity, failure isolation?
7. Какво измерване би показало, че новите граници наистина правят организацията по-ефективна?

## 8. Кога НЕ е нужен цял EventStorming workshop?

Малка CRUD feature с ясен бизнес договор и без boundary/ownership uncertainty не изисква десет души да лепят бележки цял ден. Приложи **по-лека domain story или focused event timeline**, когато рискът е малък.

Но при legacy migration с множество конкуриращи се понятия, циклични зависимости и coordinated releases, бизнес workshop често е по-евтин от месеци рефакториране на грешни boundaries.

## 9. Крайният артефакт и review checklist

- [ ] Имаме реални хора от бизнеса и някой operational expert, не само developers.
- [ ] Domain events са past-tense business facts; технологичните calls са вторични.
- [ ] Timeline има happy + failure/reversal branches.
- [ ] Hotspots и непотвърдените бизнес правила са видими.
- [ ] Actors, commands, policies, pivotal events са назовани.
- [ ] Кандидатите за граници са групирани по decision ownership и език, не по съществуващи service names.
- [ ] Има **открити въпроси**, които ще се валидират след workshop.
- [ ] Новият модел се свързва с testable package boundaries и миграционни стъпки.
- [ ] Не се предполага, че 1 event = 1 topic или 1 cluster = 1 deployment.

**Exit mental model:** не започваме със Spring/Kafka диаграма, а със **събитията, реалните отговорности и противоречията в бизнеса**. Едва после избираме modules, APIs и deployments.

## Оригинални източници

1. [Mario Klaver — Taming Microservice Overload, Devoxx Belgium 2026](https://www.youtube.com/watch?v=v86SfHA5ISw).
2. [EventStorming — official Alberto Brandolini site](https://www.eventstorming.com/).
3. [Alberto Brandolini — Introducing EventStorming](https://www.eventstorming.com/book/).
4. [Allegro Engineering — EventStorming Workshop](https://blog.allegro.tech/2022/07/event-storming-workshops.html).
5. [Spring Modulith — Application Modules](https://docs.spring.io/spring-modulith/reference/fundamentals.html).
