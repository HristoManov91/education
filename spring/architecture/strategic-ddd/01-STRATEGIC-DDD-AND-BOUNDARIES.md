# 1.1 — Strategic DDD, Ownership & Service Boundaries

> **P1 / Fundamentals · 20–30 минути първо преминаване · Mid+ → Early Senior**
>
> Видео: [00:35–20:20](https://www.youtube.com/watch?v=eFSEg54mwVU&t=35s) и [30:41–45:00](https://www.youtube.com/watch?v=eFSEg54mwVU&t=1841s).
>
> Тази глава е за **как взимаме архитектурно решение**, не за готова анотация или framework.

## 1. Началният симптом — услуги има, автономия няма

Имаме лизингова платформа. Екипът по офертите иска да добави нова опция към електромобил. Вместо само Quoting да се промени, се налага:

- Contracts да добави поле в общата Vehicle entity;
- Claims да обнови DTO, защото ползва същата схема;
- три teams да координират release;
- пет HTTP calls да извличат vehicle данни за едно business решение.

Архитектурата е „разпределена“, но не е **автономна**. Това не се поправя автоматично чрез повече services, Gateway, Kafka или обща shared library.

**Цел:** промяна в Quoting, която не променя физическия автомобил, **да не принуждава** Contracts/Claims да се адаптират.

## 2. Концепции, които не бива да смесваме

| Термин | Какво означава | Какво НЕ означава |
| --- | --- | --- |
| **Domain** | Бизнес проблемното пространство (leasing) | Java package, service или база |
| **Subdomain** | Част от бизнес проблема (quoting, contracts, claims, credit) | Един задължителен deployable |
| **Core subdomain** | Диференциращото за бизнеса | Най-голямото repository |
| **Supporting subdomain** | Нужно, но не ключово конкурентно предимство | Винаги може да се изнесе без анализ |
| **Generic subdomain** | Стандартизируема commodity функция | Непременно външен SaaS |
| **Bounded Context (BC)** | Граница, в която един бизнес модел/език са последователни | Физическа машина или автоматично „един BC = един microservice“ |
| **Ubiquitous Language** | Общият, недвусмислен език на експертите и разработчиците в един BC | Един централен корпоративен речник за всички контексти |
| **Maven module** | Compile/build граница | Самостоятелен runtime |
| **Microservice / deployable** | Независимо изпълняваема/deployable система със своя contract | Доказателство, че е добре моделирана |
| **Team ownership** | Отчетен екип, операции, решения и право на еволюция | Просто записано име в Jira |

**Strategic DDD** пита *къде са границите и как контекстите си влияят*. **Tactical DDD** пита *как моделираме entity, value object, aggregate, domain event* вътре в дадения bounded context.

Лекцията изрично подчертава, че **не е задължително да внедриш всички tactical DDD patterns**, за да използваш стратегическите въпроси.

## 3. Ownership — най-често пропусканата част

**Две различни ownership измерения:**

1. **Team owns a service:** един accountable team има decision rights, maintenance, on-call/operation responsibilities, deployment и дългосрочна грижа за модела.
2. **Service owns capabilities/data/rules:** услугата може да вземе собственото си бизнес решение чрез контролирани данни, а не да пита пет други teams „може ли“.

Антипример:

```text
Team A променя VehicleService за offer configuration.
Team B променя VehicleService за leasing contract.
Team C променя VehicleService за claims reporting.
Няма собственик, който да може да каже „това не принадлежи тук“.
```

Общият repository може да има code owners и пак да има лоши **business boundaries**. Екипът трябва да има както отговорност за инцидентите, така и **право да откаже чужда бизнес логика**.

### Решение за review

Преди да създадеш service, попитай:

- **Who changes it?** Кои хора и workflows променят правилата?
- **Who decides?** Кой бизнес owner определя правилата?
- **Who operates it?** Кой наблюдава отказите и възстановяването?
- **Who owns the authoritative data?** Къде живее източникът на истина?
- **Who pays for a breaking change?** Кой трябва да адаптира модела си?

Ако нямаш ясен отговор, първо реши ownership-а; Kubernetes namespace няма да го измисли вместо теб.

## 4. Cohesion — кои бизнес правила се променят заедно

Лекторът описва две симетрични грешки:

**Грешка A — свързани неща са разпръснати.** Транзакционно свързани правила са в три deployables. Всяка feature изисква координация; един logical operation става distributed Saga без нужда.

**Грешка B — несвързани неща са заедно.** Accounting batch засяда и товари общата DB, а Quoting latency се влошава въпреки липсата на бизнес връзка.

**Практична граница:** функционалност, данни, invariants и промени трябва да бъдат съгласувани. Но може да има независима причина за отделяне: security, regulatory isolation, blast radius, performance, legal sovereignty или различна deploy cadence.

### Feature-change heuristic

Разгледай последните 20 реални промени:

1. Кои файлове/таблици/teams са се променили заедно?
2. Кои промени изискват обща DB transaction?
3. Кои промени изискват едновременно пускане на няколко услуги?
4. Колко често една бизнес feature преминава през няколко services?

Честото съвместно изменение не е автоматично доказателство, но е силен сигнал за неправилно разделена cohesion.

## 5. Bounded Context — граница на смисъла, не на кодовите редове

Една и съща дума може да значи различни неща:

| Контекст | „Vehicle“ означава | Какви инварианти са важни |
| --- | --- | --- |
| **Quoting** | Модел + избрани фабрични опции | Валидна конфигурация, съвместими опции, цена на офертата |
| **Contracts** | Конкретен произведен автомобил | Реален VIN, регистрация, връзка с договор |
| **Claims** | Asset, за който трябва да има данни при разглеждане на щета | Идентификация и релевантно договорно състояние |

**Лошо решение:** обща canonical Vehicle entity от 60 полета, използвана във всички контексти. Дори без distributed calls тя привързва модела на Quoting към решенията на Claims.

**По-добро:** ограничени модели с подходящ език, които могат да имат дублирани атрибути, но различна семантика и lifecycle.

**Реален пример:** [VehicleConfiguration.java](quoting-domain/src/main/java/bg/hristomanov/education/strategicddd/quoting/VehicleConfiguration.java) не притежава VIN; [RegisteredVehicle.java](contracts-domain/src/main/java/bg/hristomanov/education/strategicddd/contracts/RegisteredVehicle.java) изисква VIN. Тестът [quotingCanCalculateVehicleConfigurationWithoutVinOrContract](integration-lab/src/test/java/bg/hristomanov/education/strategicddd/integration/StrategicDddScenarioTest.java) доказва разликата.

Не означава, че никога няма да превеждаме данни между тях — означава, че **публичният integration contract и ACL не трябва да се бъркат с общ вътрешен модел**.

## 6. Защо Bounded Context ≠ Microservice

Лекцията разглежда три допустими варианта:

**Вариант 1: два bounded contexts, два deployables.** Например retail и fleet credit models имат независими owners, много различни правила и cadence. Разделянето може да е полезно.

**Вариант 2: два bounded contexts, един deployable.** Моделите и езикът се различават, но операционната и build цена на разделянето не е оправдана. Два модула със самостоятелни контракти + общо стартиране могат да са правилният избор.

**Вариант 3: един bounded context, два deployables.** Същият бизнес модел може да изисква физическо отделяне по независима причина — регионално изискване, 90% от revenue в един сегмент, capacity/risk isolation. Важно е да запазим правилната ownership семантика и да не създадем случайни competing models.

**Няма кардиналност 1:1.** Това не е оправдание да създаваме произволни граници; всяка трябва да има named reason.

### Decision matrix

| Ситуация | Вероятно по-добър старт |
| --- | --- |
| Стабилна бизнес граница, един team, тесни транзакции | Modular monolith (отделни модули, един процес) |
| Различни teams + ясни contracts + независими releases | Отделни deployables |
| Споделен модел, но независим risk/capacity | Разделен deployment със съгласувана model ownership |
| Проста read-only reference база | Контролирана data service/read access — **само след** оценка на schema coupling, security и portability |
| Просто CRUD с една база | Локална транзакция; DDD церемонията може да е излишна |

## 7. „Микро“ не е метрика

**Не броим endpoints, редове код или таблици** като критерий дали service е прекалено голям.

Мисли за **coherence и manageability**: едно парче може да съдържа много capabilities, ако те принадлежат на един бизнес контекст и един accountable team може да ги управлява.

Обратното — service с един endpoint може да е лоша граница, ако зависи синхронно от седем други services.

## 8. Какво се чупи при физическо извличане

В modular monolith:

```text
Local method call (microseconds scale)
→ shared transaction
→ exceptions и method-level contracts
```

След extract:

```text
HTTP/network hop
→ partial failure, timeout, retries
→ independent commits, data consistency, versioned API
→ tracing, auth, backpressure, deploy rollout
```

**Модулната архитектура улеснява reasoning-а**, но не означава, че физическото извличане е евтино. Не забравяй мрежовата и организационната цена.

## 9. Инженерен mini-exercise

Екип A иска да добави „добавено оборудване към оферта“. Екип B иска да регистрира сменени табели на физически автомобил. В общ VehicleService двата екипа пипат един и същ DTO.

Опиши:

1. Какви са subdomains и bounded contexts?
2. Кои полета принадлежат на кой контекст?
3. Кой трябва да притежава source-of-truth данните за VIN и за pricing options?
4. Как ще предотвратиш coordinated release без ненужно distributed deployment?
5. Ако fleet получи 100× load, това ли е достатъчно основание да го отделим физически? При какви SLO и constraints?

**Референтна проверка:** сравни с [README→код картата](README.md#5-изпълнима-архитектура) и [реалния Java/Spring lab](03-LABS.md).

## 10. Въпроси за самопроверка (не гледай кода първо)

- Може ли два bounded contexts да споделят един deployment, без да споделят domain model?
- Кога една business capability трябва да държи своите данни, а кога controlled external lookup е допустим?
- Как ще разбереш, че имаш ownership problem, а не network/configuration problem?
- Защо общ DTO/общ database schema може да е по-силна зависимост от HTTP call?
- Какво **не** решава Maven compile boundary, когато teams използват обща физическа database?

## 11. Изход и code-review checklist

**Когато видиш нов microservice PR**, първият ти въпрос не трябва да е „има ли REST endpoint?“. Питай:

- [ ] Новият service притежава ли самостоятелно business rules и decisions?
- [ ] Coherence на модела вътре в границата по-голяма ли е от cohesion с други модули?
- [ ] Има ли owner team с decision rights?
- [ ] Има ли нови distributed consistency задължения и оправдани ли са?
- [ ] По-евтин modular monolith вариант обсъден ли е?
- [ ] Използваме ли evidence от реалния change/deployment history вместо „micro is always better“?

**Mental model:** първо моделираш **решенията и ownership-а**, после определяш **модулите**, и чак след това избираш **deployments/transport**.

## Оригинални източници

- [Devoxx — Microservices Without the Distributed Mess](https://www.youtube.com/watch?v=eFSEg54mwVU)
- [Martin Fowler — Bounded Context](https://martinfowler.com/bliki/BoundedContext.html)
- [Eric Evans — Domain-Driven Design Reference](https://www.domainlanguage.com/ddd/reference/)
- [Spring Modulith — Modulith verification](https://docs.spring.io/spring-modulith/reference/verification.html)
