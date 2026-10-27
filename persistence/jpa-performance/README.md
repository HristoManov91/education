# P1 Deep Dive — When ORM Becomes OMG: Spring Boot, JPA и database performance

> **Източник:** Devoxx Belgium 2026, Jos Roseboom — [When ORM Becomes OMG: Performance Pitfalls in JPA and Friends](https://www.youtube.com/watch?v=ePBmAdRYl10).  
> **Формат:** самостоятелен подробен урок + практическо ръководство; не е нов Maven module. Изпълнимите демонстрации са в [оригиналния FunFactStore](https://github.com/JosRoseboom/funfactstore), а част от фундаменталните тестове — в [Repository + Unit of Work](../repository-unit-of-work/README.md).  
> **Приоритет на лекцията:** P1 (висока практическа стойност). **Учебен roadmap:** P0 `DB-10`–`DB-14`, `SPR-04` и P1 Performance. Статусът на ученето **не е „усвоено“**, докато няма проверка.  
> **Време:** 3 сесии по 20–30 минути + лабораториите в [EXERCISES.md](./EXERCISES.md).  
> **За слушане:** [NotebookLM prompt](./NOTEBOOKLM-PROMPT.md).

## 1. Реалният казус — една бавна заявка блокира и други endpoint-и

Имаме Spring MVC приложение с PostgreSQL/Oracle, Spring Data JPA, Hibernate и HikariCP. Потребител извиква endpoint, който:

1. намира имейл адрес от база данни;
2. извиква външна услуга за проверка на потребителя (400 ms е **примерна**, не измерена стойност);
3. чете последна покупка от базата;
4. изпраща външно известие (700 ms е **примерна** стойност).

Кодът изглежда нормално:

```java
@Transactional
public PurchaseSummary loadPurchaseSummary(String username) {
    String email = userRepository.findEmail(username).orElseThrow();
    riskClient.check(email);                         // външно I/O
    PurchaseSummary summary = purchaseRepository.lastFor(username);
    notificationClient.notifyInterestedCustomer(username); // външно I/O
    return summary;
}
```

Проблемът не е непременно в самите SQL заявки. Ако **физическата JDBC връзка** е заемана през дългите външни операции, десет паралелни заявки могат да изчерпят pool с десет connections. Следващите чакат connection, макар базата да не работи активно. При зададен `connectionTimeout` могат да последват и откази.

Важно: връзка (`Connection`), логическа транзакция (`transaction`), `EntityManager`/persistence context и HTTP request **не са едно и също нещо**.

**Цел:** да оптимизираме времето на реално заемане на connection и броя SQL заявки, без да счупим transaction correctness.

## 2. Какво трябва да можеш след урока

- Да обясниш кога JDBC connection се взема от HikariCP и кога се връща.
- Да разграничиш `@Transactional`, `spring.jpa.open-in-view` и `spring.datasource.connection-fetch=lazy`.
- Да избираш правилната граница на транзакция, особено при външно I/O.
- Да откриваш скрити SQL заявки зад JPA отношения и N+1.
- Да сравняваш `JOIN FETCH`, `@EntityGraph`, DTO projection и batch fetching.
- Да докажеш промяна чрез SQL logs, query counts, pool metrics и интеграционен/натоварващ тест.

## 3. Mental model: пет различни механизма

```text
HTTP request ──────────────── може да е по-дълъг ────────────────┐
  Spring transaction ──────── business consistency boundary ───┤
    Persistence context ───── Identity Map / dirty checking ───┤
      JDBC connection lease ── само времето със заета връзка ──┤
        SQL statements ────── реално изпълнение срещу DB ──────┘
```

Границите зависят от конфигурация и execution path и **невинаги са еднакво вложени**. `Open EntityManager in View` (OSIV) може да държи `EntityManager` отворен през web request и извън service транзакция. Това **не доказва само по себе си**, че една и съща физическа JDBC връзка е заемана през цялото време — трябва да измерим acquisition/release поведението.

Термини:

| Термин | Точно значение |
| --- | --- |
| `Connection pool` | Ограничен набор преизползваеми физически JDBC connections |
| `Connection checkout / lease` | Интервалът от вземане на връзка от pool до връщането ѝ |
| `Persistence context` | Контекст от managed JPA entities с identity и dirty-checking semantics |
| `Fetch plan` | Кои асоциации/колони зареждаме за конкретна операция |
| `N+1` | Една първоначална SQL заявка и допълнителни заявки при зареждане на отношения |
| `DTO projection` | Пряко избиране на необходимите стойности в специален read model |

**Правило:** SQL latency и `connection hold time` (време на задържане на връзката) са различни величини. И двете влияят върху throughput.

---

# Част I — Connections и transaction boundaries

## 4. HikariCP: защо имаме pool

Установяването на нова физическа връзка е по-скъпо от връщането на свободна такава от pool. В лекцията авторът използва 1000 повторения след warm-up и демонстрира разлика от порядъка на **хиляди пъти** в конкретната си среда. Това **не е универсален benchmark** и не може да се пренася директно към production.

`HikariCP maximumPoolSize` по подразбиране е **10** (ако не е променен). Не „решаваме“ изчерпан pool чрез сляпо увеличаване на размера: по-голям pool товари и DB сървъра.

Наблюдавай едновременно:

- **active** connections — заети в момента;
- **idle** connections — свободни;
- **pending** requests — чакащи за връзка;
- **acquire time / connection timeout** — колко чака request-ът;
- **usage/hold time**, HTTP p95/p99, DB CPU, locks и throughput.

В Spring Boot Actuator + Micrometer можеш да следиш Hikari metrics; точните имена/наличност зависят от версия и registry. Само `spring.jpa.show-sql=true` не показва кой и колко време държи физическа връзка.

## 5. OSIV: удобство, което може да скрие проблем

За web/JPA конфигурации Spring Boot по подразбиране регистрира `OpenEntityManagerInViewInterceptor`, ако не е изключен. Това позволява lazy loading и след края на service transaction, например от controller или view.

```properties
spring.jpa.open-in-view=false
```

**Защо в повечето нови backend API проекти предпочитаме false:** ясно разграничение между transactional data access и rendering/serialization; по-малко неочаквани SQL заявки извън service слоя; по-лесно откриване на лош fetch plan.

Но не е достатъчно да сменим property в legacy приложение. Първо намери къде controller, Jackson serializer, Thymeleaf template или mapper все още четат lazy relations. След `false` това може да доведе до `LazyInitializationException`, ако се опитваш да инициализираш detached proxy. **Решението е explicit fetch / projection в правилната транзакция**, не безконтролно включване на OSIV.

В демото на лектора изключването на OSIV променя момента на освобождаване на връзката. Това е **наблюдение от конкретната конфигурация**, не универсална гаранция, че OSIV винаги държи физическа връзка до края на HTTP request-а.

## 6. Lazy acquisition — кога реално взимаме връзката

При resource-local transaction Hibernate може да се наложи да вземе physical connection още при начало на transaction, например за `setAutoCommit(false)`. Hibernate поддържа настройката:

```properties
spring.datasource.hikari.auto-commit=false
spring.jpa.properties.hibernate.connection.provider_disables_autocommit=true
```

**Важно ограничение:** това е безопасно **само ако** действителният connection provider наистина връща connections с изключен auto-commit. Невярно зададеното `provider_disables_autocommit=true` може да доведе до SQL извън очакваната транзакция. Не го копирай без проверка на Spring Boot/Hibernate версията и auto-configuration — някои версии го настройват автоматично.

**Spring Boot 4.1+ предоставя по-прозрачна опция** за auto-configured pooled datasource:

```properties
spring.datasource.connection-fetch=lazy
```

`LazyConnectionDataSourceProxy` отлага **реалния** checkout до първия JDBC statement, като запазва логически transaction control (`readOnly`, auto-commit и isolation settings се прилагат при реалния checkout).

```text
transaction begin ── remote API ── FIRST SQL ── SQL ── transaction end
                  ↑ няма physical   ↑ checkout          ↑ release
                    checkout
```

Това е особено полезно за пътища с early return/cache hit или external call **преди първата SQL операция**. **Не решава** ситуацията „SQL → remote HTTP call → SQL“ в една транзакция: връзката обичайно остава заемана между заявките до края на transaction.

`@Transactional(readOnly=true)` е hint/режим за транзакцията; **не** е гаранция, че няма да има connection checkout, SQL, lock или flush в произволна конфигурация.

## 7. Good вариант — граница около database work, не около мрежови чакания

Наивното `@Transactional` върху целия orchestration метод държи една и съща бизнес транзакция по време на външно I/O. Ако consistency requirements позволяват отделни четения:

```java
@Service
public class PurchaseInquiryService {

    private final TransactionTemplate transactionTemplate;
    private final UserRepository userRepository;
    private final PurchaseRepository purchaseRepository;
    private final RiskClient riskClient;

    // Конструкторът получава dependencies чрез DI.

    public PurchaseSummary inquire(String username) {
        String email = transactionTemplate.execute(status ->
                userRepository.findEmail(username).orElseThrow());

        riskClient.check(email); // извън DB transaction

        return transactionTemplate.execute(status ->
                purchaseRepository.lastFor(username));
    }
}
```

Това е **илюстративен** excerpt, не готов class с dependencies. Умишлено няма `@Transactional` върху `inquire()`, иначе външната transaction би обхванала и двете callback операции при обичайното `REQUIRED` поведение.

**Алтернатива:** два отделни Spring service метода с `@Transactional`, извиквани през Spring proxy. Самоизвикване (`self-invocation`) на `@Transactional` метод в същия class не задейства proxy-based transaction advice.

**Trade-off за correctness:** две отделни read transactions може да видят различни snapshots. Ако изискването е атомарна промяна на няколко реда, не разделяй транзакцията само за performance. Ако трябва да публикуваш събитие надеждно след commit, разгледай [Transactional Outbox](../../spring/messaging/outbox-inbox/README.md), а не remote call насред транзакцията.

**Не бъркай:** „махнахме физическата връзка от времето на HTTP чакането“ с „направихме системата по-бърза за един потребител“. Първото оптимизира основно **resource occupancy** (колко дълго държим ограничен ресурс), често и throughput/latency под натоварване.

---

# Част II — Hibernate/JPA fetch plans

## 8. Скритите SQL заявки зад getters

JPA default за `@ManyToOne` и `@OneToOne` е `EAGER`. Hibernate може да направи JOIN или допълнителен SELECT в зависимост от начина на зареждане. `EAGER` **не означава** задължително „един JOIN“.

Обикновено започваме с explicit:

```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "admin_id", nullable = false)
private AppUser admin;
```

Това отлага зареждането на `AppUser`, но **не прави автоматично fetch plan-а оптимален**. Ако след това итерираме и четем `funFact.getAdmin().getDomain()`, пак можем да получим N+1. За някои `@OneToOne` отношения (особено inverse/non-owning side) lazy поведението зависи от mapping-а и Hibernate enhancement-а; проверявай реалните SQL заявки.

## 9. Persistence context — Identity Map, не магическа DB изолация

В един persistence context една database entity identity има един managed Java object:

```java
AppUser first = entityManager.find(AppUser.class, adminId);
AppUser second = entityManager.find(AppUser.class, adminId);
boolean sameInstance = first == second; // true в същия persistence context
```

Това намалява ненужното повторно entity loading и дава **identity consistency** за вече управляваната entity instance. В лекцията авторът демонстрира повторни референции към едни и същи `AppUser` обекти.

**Критично разграничение:** първото ниво на Hibernate cache **не сменя database isolation level на `REPEATABLE READ`** и не гарантира, че всички нови/скаларни SQL queries или отношения виждат непроменено състояние. `READ COMMITTED` си остава DB isolation policy. Mental model: стабилна managed object identity ≠ snapshot isolation на цялата база.

Преди да продължиш: [изпълнимият Identity Map тест](../repository-unit-of-work/src/test/java/bg/hristomanov/education/uow/RepositoryUnitOfWorkTest.java).

## 10. N+1: един прост loop може да задейства десетки SQL заявки

```java
List<FunFact> facts = funFactRepository.findAll();
for (FunFact fact : facts) {
    String domain = fact.getAdmin().getDomain();
    // В зависимост от fetch plan-а тук може да има SQL.
}
```

Без правилен fetch plan SQL поведението може да е:

```sql
SELECT * FROM fun_fact;
SELECT * FROM app_user WHERE id = ?;
SELECT * FROM app_user WHERE id = ?;
-- ...
```

Броят допълнителни queries **не е задължително точно броят редове**: ако няколко `FunFact` обекта сочат към същия `AppUser` в един persistence context, повторното fetch-ване може да бъде спестено. Сложността обаче остава зависима от броя различни lazy dependencies и входните данни.

### Good за to-one: explicit JOIN FETCH

```java
@Query("select f from FunFact f join fetch f.admin")
List<FunFact> findAllWithAdmin();
```

Един SQL fetch зарежда и нужния `admin` за тази операция. Използвай `left join fetch`, ако връзката е nullable и трябва да запазиш редове без свързан обект. Аналог: `@EntityGraph(attributePaths = "admin")`.

**Забележка:** не прилагай механично `join fetch` върху две големи `@OneToMany` колекции; може да получиш голям Cartesian product/row multiplication. При collection fetch + pagination Hibernate може да прави pagination in memory или да изисква друга стратегия. Алтернативи: two-step page-by-IDs + fetch, batch/subselect fetching или projection.

**Decision guide:**

| Сценарий | Обичайно добро решение |
| --- | --- |
| Редактирам entities и ми трябва `admin` | Managed entities + to-one `JOIN FETCH` или `@EntityGraph` |
| Показвам списък само с 3–5 scalar полета | DTO/record projection |
| Имам няколко големи collection associations | Разделени queries, batch/subselect, два етапа |
| Работя със страницирани резултати | Първо проверявам SQL pagination и fetch plan |
| Рядко използвам relation | `LAZY`, с explicit fetch в нужния use case |

## 11. DTO projections — често по-добри от entity graph за read-only API

Това е **read model** (модел на конкретния изход), не managed entity:

```java
public record PurchaseRow(
        Instant purchasedAt,
        String factText,
        String supportEmail
) {}
```

```java
@Query("""
    select new bg.example.PurchaseRow(p.purchasedAt, f.text, a.email)
    from Purchase p
    join p.funFact f
    join f.admin a
    where p.customer.username = :username
    """)
List<PurchaseRow> findPurchasedFacts(String username);
```

Класът `bg.example.PurchaseRow` е **placeholder** — постави истинския fully-qualified package при пренасяне на примера. Запитването е илюстрация на JPQL constructor expression (`select new ...`).

Ползи: очакваните полета са видими в query, не materialize-ваме ненужни managed entities, няма обхождане на graph от mapper, по-малко риск от допълнителни SELECT-и. При правилен query обичайно имаме един SQL, но **доказвай**, не приемай по презумпция.

Варианти: JPQL constructor projection (стандартен), Spring Data interface-based projection (често alias-/Tuple-based), native SQL + DTO mapping. Native class-based projection е **version/provider dependent** — при различни типове/ред на колоните използвай `@SqlResultSetMapping` или подходящ mapper. JPQL е по-портируем между PostgreSQL/Oracle, но не покрива всички vendor-specific SQL възможности.

**MapStruct не е виновен по подразбиране за N+1.** Mapper може да задейства lazy getters, ако му подадем неподходящ entity graph. Проблемът е **fetch plan + достъп до associations**. Ръчната проекция често прави това по-очевидно, но MapStruct остава валиден избор при подходящо заредени данни.

## 12. Dirty checking, save() и отношенията — без догми

Managed entity промени се откриват от Hibernate при flush (често преди commit) и `save()` не е задължителен за всяка промяна. Това вече е доказано в [RepositoryUnitOfWorkTest](../repository-unit-of-work/src/test/java/bg/hristomanov/education/uow/RepositoryUnitOfWorkTest.java). Не прави обратното крайно заключение „никога `save()`“ — нови/detached entities, contract-и и различни adapters имат друга семантика.

`@ManyToMany` не е забранен. Но ако join таблицата съдържа timestamp, status, quantity или друга business информация, **изрична link entity** (`Purchase` вместо скрита join table) обикновено дава по-ясен модел, constraints и audit. Избирай mappings според domain-а и access patterns, не според краткостта на annotations.

---

# Част III — Доказателство, performance и практическо решение

## 13. README → работещ код и първоизточник

Този документ е **урок**, не твърди, че добавя нови изпълнени автоматични тестове. Реалните demonstrations са:

| Тема | Код | Проверка |
| --- | --- | --- |
| Connection lifecycle | [FunFactStore configuration](https://github.com/JosRoseboom/funfactstore/blob/master/src/main/resources/application.properties) | [ShopControllerIntegrationTest](https://github.com/JosRoseboom/funfactstore/blob/master/src/test/java/com/easingyou/funfactstore/fact/shop/ShopControllerIntegrationTest.java) + Hikari timing/debug |
| Lazy relationship + fetch join | [FunFact.java](https://github.com/JosRoseboom/funfactstore/blob/master/src/main/java/com/easingyou/funfactstore/fact/FunFact.java) | [FunFactRepo.java](https://github.com/JosRoseboom/funfactstore/blob/master/src/main/java/com/easingyou/funfactstore/fact/FunFactRepo.java) |
| Identity Map / dirty checking / rollback | [PersistenceContextProbeService](../repository-unit-of-work/src/main/java/bg/hristomanov/education/uow/service/PersistenceContextProbeService.java) | [RepositoryUnitOfWorkTest](../repository-unit-of-work/src/test/java/bg/hristomanov/education/uow/RepositoryUnitOfWorkTest.java) |
| Приложна лаборатория | [EXERCISES.md](./EXERCISES.md) | Документиране на actual query count, timings, limitations |

За точната версия на примера на автора: неговият актуален `pom.xml` посочва **Spring Boot 4.1.0, Java 26 и PostgreSQL/Testcontainers**. Нашият основен repository остава **Java 25**; **не смесвай** двата toolchain-а без отделно обновяване и верификация.

## 14. Как мерим честно

Формулирай хипотеза преди промяна и наблюдавай **същите данни/натоварване**:

1. **Baseline:** SQL count, actual SQL/joins, connection checkout/release timestamps, pool active/pending, HTTP latency p50/p95/p99.
2. **Една промяна:** например OSIV off; после lazy acquisition; после tighter transaction boundary; после projection. Не сменяй всичко наведнъж.
3. **Verification:** повтори при същия вход и конкурентност, със warm-up; не бъркай микробенчмарк със съществено production подобрение.
4. **Correctness:** проверявай еднакъв business output, rollback semantics, concurrency и errors. Testcontainers с **истински PostgreSQL** е по-показателен за JDBC/SQL runtime от H2.
5. **DB plan:** `EXPLAIN (ANALYZE, BUFFERS)` за PostgreSQL; `DBMS_XPLAN`/actual plan за Oracle, при уместни права и среда. По-малко queries не гарантира по-ниска обща цена.

Примерни варианти за observability:

```properties
logging.level.org.hibernate.SQL=DEBUG
logging.level.com.zaxxer.hikari=DEBUG
# Само временно за диагностика; parameter-value logging може да изведе чувствителни данни.
# spring.jpa.properties.hibernate.generate_statistics=true
```

Hibernate `Statistics` / `StatementInspector` са подходящи за тестови probes на броя заявки. За pool наблюдение използвай Hikari/Actuator/Micrometer metrics или внимателно добавен checkout tracker. Не оставяй широк `root=DEBUG` в production заради overhead и data leakage.

При race/load тестове отделяй startup/warm-up и injection-а на външни latency, и сравнявай capacity спрямо `maximumPoolSize`. Няма универсален правилен размер за pool.

## 15. Production ограничения

- Connection fetch lazy **не** разделя голяма транзакция на по-малки и **не** освобождава checkout-натата връзка между два SQL statement-а.
- `JOIN FETCH` може да увеличи пренасяните данни; to-many relationships и pagination изискват особен контрол.
- Изключването на OSIV може да извади наяве latent `LazyInitializationException`; този проблем е **сигнал за липсващ fetch plan**, не доказателство, че OSIV трябва да се включи отново.
- DTO projection не заменя entities във всеки use case. При domain logic, writes, locking и change tracking entity model има реална стойност.
- Разделянето на DB транзакциите не е безплатно: внимавай за consistency, retries, външни side effects и idempotency.
- Нито една ORM оптимизация не компенсира липсващи индекси, лош SQL план, блокировки или наситен DB server.

## 16. Завръщаме се към началния казус

**Преди:** една `@Transactional` операция чете от DB, чака два външни API call-а и пак чете DB; при определени настройки връзката стои заемана, а views/mappers могат да задействат допълнителни SQL заявки.

**След:** ясно идентифицираме transaction/connection lifecycle, отлагаме първия checkout, държим външното I/O извън DB transaction **където consistency го допуска**, а за read paths използваме предварително планиран fetch/DTO projection. Доказваме ефекта по **време на checkout, query count, p95/p99 и correctness**, не само с „работи на моя компютър“.

### Mental model за 30 секунди

1. **`@Transactional` дефинира consistency boundary; physical JDBC checkout е отделен lifecycle.**
2. **`LAZY` отлага зареждане, а fetch plan определя колко SQL заявки ще изпълним.**
3. **За read API проектирай нужните данни преди да обходиш entity graph.**
4. **Optimization без measurements и проверена семантика е предположение.**

### Code-review checklist

- [ ] Ясно ли е **кога** взимаме и връщаме physical connection?
- [ ] Има ли remote HTTP/file/network call между две SQL операции в една транзакция?
- [ ] OSIV конфигурацията и lazy access boundaries известни ли са?
- [ ] Има ли `@Transactional` на прекалено широк method/class? Self-invocation?
- [ ] `readOnly` / auto-commit assumptions проверени ли са за конкретните версии?
- [ ] Кое relation е `LAZY`/`EAGER` и защо за този use case?
- [ ] Какъв е реалният SQL count за 1, 10 и 100 results?
- [ ] Има ли to-many fetch + pagination / row explosion?
- [ ] Нужен ли е managed entity или само 3 scalar полета в DTO?
- [ ] Проверени ли са индексът, execution plan, DB-specific types и concurrency?
- [ ] Има ли baseline и измерване **след** промяната?

### Професионална формулировка за интервю / code review

> „Когато видя бавен JPA endpoint, първо разделям проблема на SQL cost, fetch plan и connection pool occupancy. Проверявам transaction boundary и OSIV, кога Hibernate взима физическа връзка, и дали по време на транзакцията се чака външно I/O. След това измервам query count и проверявам за N+1. При четене на конкретни полета предпочитам DTO projection; при работа с managed entities избирам explicit fetch plan според access pattern. Прилагам промени по една и ги доказвам с интеграционни/натоварващи тестове и DB execution plans.“ 

## 17. Следващи стъпки

1. Направи [практическите лаборатории](./EXERCISES.md) с baseline/results и изрични изводи.
2. Слушай [NotebookLM аудио урока](./NOTEBOOKLM-PROMPT.md), без да го приемаш за доказателство за mastery.
3. После премини диагностичните микро-теми `DB-11`, `DB-12`, `DB-13`, `DB-14` и `SPR-04`; резултати се записват **само след действителни отговори**.

## 18. Оригинални източници

- [Видео: Devoxx Belgium 2026 — Jos Roseboom, When ORM Becomes OMG](https://www.youtube.com/watch?v=ePBmAdRYl10)
- [Официална страница на лекцията (Devoxx)](https://m.devoxx.com/events/dvbe26/talks/22905/when-orm-becomes-omg-performance-pitfalls-in-jpa-and-friends)
- [Авторски код — FunFactStore](https://github.com/JosRoseboom/funfactstore)
- [Слайдове на автора](https://easingyou.com/whenORMbecomesOMG/)
- [Spring Boot — SQL Databases / Lazy Connection Proxy](https://docs.spring.io/spring-boot/reference/data/sql.html)
- [Spring Boot 4.1 Release Notes — Lazy JDBC connection fetching](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.1-Release-Notes)
- [Hibernate User Guide — connections, fetching, dirty checking](https://docs.hibernate.org/orm/7.1/userguide/html_single/Hibernate_User_Guide.html)
- [Hibernate `JdbcSettings.CONNECTION_PROVIDER_DISABLES_AUTOCOMMIT`](https://docs.hibernate.org/orm/7.1/javadocs/org/hibernate/cfg/JdbcSettings.html)
- [Spring Data JPA — projections](https://docs.spring.io/spring-data/jpa/reference/4.0/repositories/projections.html)
- [Jakarta Persistence 3.2 specification](https://jakarta.ee/specifications/persistence/3.2/jakarta-persistence-spec-3.2)
- [HikariCP — Configuration](https://github.com/brettwooldridge/HikariCP/wiki/Configuration)

**Версионно правило:** при промени към по-нови Spring Boot/Hibernate версии препроверявай `spring.datasource.connection-fetch`, auto-commit интеграцията, fetch behavior и native projection mapping в официалните release notes.
