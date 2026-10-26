# NotebookLM — два P1 уроци от Port of Rotterdam (Devoxx 2026)

**Входни източници за NotebookLM:** [Mario Klaver / Devoxx: Taming Microservice Overload](https://www.youtube.com/watch?v=v86SfHA5ISw), [04-EVENTSTORMING-WORKSHOP.md](04-EVENTSTORMING-WORKSHOP.md), [05-SPRING-MODULITH.md](05-SPRING-MODULITH.md), [modulith-lab/README](modulith-lab/README.md), [Port of Rotterdam Case 004](../../../docs/case-studies/004-port-of-rotterdam-modular-monolith.md), официалната [EventStorming](https://www.eventstorming.com/) и [Spring Modulith](https://docs.spring.io/spring-modulith/reference/) документация.

Създай **две отделни** висококачествени аудио лекции на български. Не генерирай 50-минутно обобщение, което повтаря общите DDD концепции от вече разработените 1.1, 1.2, 1.3 материали.

## P1 Audio A — EventStorming: откриване на правилните граници (35–45 мин)

```text
Подготви задълбочен български технически урок за Senior Java/Spring developer на тема "EventStorming: how to discover business boundaries before designing microservices". Използвай оригиналната Port of Rotterdam лекция от ~18:00 до 29:00 и нашия 04-EVENTSTORMING-WORKSHOP.md.

Започни от реaлния проблем: 90+ services при пет екипа; красиви service имена, но едно бизнес решение е разпиляно през множество синхронни/асинхронни dependencies. Не повтаряй дефинициите за Bounded Context и Upstream/Downstream от предишния ни Strategic DDD курс — акцентът е върху discovery процеса.

Обясни трите нива Big Picture, Process Modelling и Software Design EventStorming. Разкажи стъпка по стъпка как фасилитираме workshop: участници (бизнес, operational и технически), chaotic exploration, domain events в минало време, timeline, pivotal events, swim lanes, actors, commands, policies, read models, hotspots, storytelling/validation и candidate subdomains. Отдели business facts от technical events; не превръщай 1 sticky note в 1 Kafka topic или 1 bounded context в 1 microservice.

Проведи мислено целия пример с vessel clearance: health declaration, dangerous goods, decision ownership, паралелни проверки и missing/error branches. Покажи как възникват противоречия в бизнеса и защо facilitator трябва да пази hotspots вместо да измисля business rules. Упражнението е учебна симулация, НЕ фактически регламент на Rotterdam.

Премини към architecture design: кои boundary hypotheses могат да се валидират с change history, owner responsibilities, cross-service coordination и собственост на decision/data. Разгледай опасността от технически диаграми преди business discovery.

Завърши с 7 senior interview/review въпроса, workshop checklist и критерии кога EventStorming е оправдан или е твърде тежък. Изисквай професионална и точна терминология на български, реалистични trade-offs и example-driven обяснение. Не претендирай, че workshop автоматично доказва границите или че организационното подобрение вече е измерено.
```

## P1 Audio B — Spring Modulith: code-enforced architecture and module testing (35–45 мин)

```text
Създай задълбочено техническо аудио обучение на български за Java 25 / Spring Boot 4.1.1 / Spring Modulith 2.1.1, базирано на видеото от 29:00 до края, нашия 05-SPRING-MODULITH.md, modulith-lab/README.md и Case Study 004.

Входен проблем: бизнес граници вече са открити, но ако просто преместим всички classes в един Spring Boot jar, ще имаме unstructured monolith; ако създадем три нови микросървиса, плащаме distributed operational costs, които не винаги носят полза.

Обясни един deployable, три бизнес модула: health, dangerousgoods, clearance. Проследи точно package структурата, public module API срещу internal packages, @ApplicationModule, @NamedInterface("events"), @ApplicationModule(allowedDependencies={"health::events","dangerousgoods::events"}). Защо public Java class не е автоматично публичен бизнес API?

Покажи как ApplicationModules.of(HarbourModulithApplication.class).verify() прави архитектурния contract изпълним: rejects internal cross-module imports, module dependency cycles и unauthorized dependencies. Дай упражнение с deliberately broken import към health.internal; обясни кой тест се проваля и защо.

Сравни Spring @WebMvcTest (horizontal technical slice), Spring Modulith @ApplicationModuleTest (vertical domain slice), standalone / direct dependencies / all dependencies bootstrap, PublishedEvents test, and full @SpringBootTest. Проследи нашия Java code и тестовете: producers emit public HealthAssessed/DangerousGoodsAssessed, clearance owns its local projection and returns PENDING/CLEARED/DENIED.

Критично обясни, че synchronous in-process ApplicationEventPublisher не е Kafka, не гарантира durable delivery, replay, ordering или crash recovery и може да остане runtime coupled през listener exceptions. Разграничи compile-time dependency decoupling от temporal/data coupling; обсъди direct calls срещу events като trade-off, не догма. Свържи с нашите Outbox/Inbox, Domain Events, Strategic DDD и existing Maven/ArchUnit modularity labs, без да дублираш техните цели.

Завърши с реалния Port of Rotterdam case: incremental migration без изключване на 24/7 system, new capability into new domain, merge coupled services, split capabilities across old service boundaries. Подчертавай, че improvement на lead time/toil е заявена хипотеза, не публично измерен доказан резултат.

Изведи конкретен migration-review checklist и 10 практични инженерни въпроса. Разграничи кои guarantees се доказват от нашите JUnit tests и кои изискват DB/broker/production measurements. Не измисляй performance gains или legal maritime rules.
```

## Learning flow

1. Аудио A → отговори на въпроса „Как намираме бизнес границите?“.
2. Аудио B → проследи runnable source и изпълни Spring Modulith tests.
3. Следвай [assessment protocol](../../../docs/learning/ASSESSMENT-PROTOCOL.md) — по 1 реален diagnostic scenario за EventStorming и Modulith, с професионална корекция на техническия изказ.
4. NotebookLM audio създава learning artifact, **не доказателство за усвоени знания**.
