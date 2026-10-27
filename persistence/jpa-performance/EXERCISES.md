# Практически лаборатории — JPA/Hibernate performance

Това е **работна тетрадка** към [основния урок](./README.md). Целта е да вземеш едно и също поведение, да видиш как го реализира Hibernate и да докажеш **какво се променя** с SQL logs, checkout timing, queries и test assertions.

**Статус:** задачите са подготвени за изпълнение; **няма твърдение, че вече са изпълнени** и няма записан успешен benchmark. Не извършвай тестовете върху production DB.

## 0. Prerequisites и безопасно стартиране

- Използвай автора [FunFactStore](https://github.com/JosRoseboom/funfactstore), защото точно този проект е демонстриран в лекцията.
- В проверения author `pom.xml` има Java **26**, Spring Boot **4.1.0** и PostgreSQL/Testcontainers. Нужни са съответният JDK, Docker/съвместим container runtime и работещ Maven wrapper. Това е **отделен sandbox**, не променя Java 25 политиката на нашия `education` repository.
- Сравни версията и профилите, преди да копираш property. Дръж лични данни/production credentials далеч от logs.

```bash
git clone https://github.com/JosRoseboom/funfactstore.git
cd funfactstore
./mvnw -Dtest=ShopControllerIntegrationTest test
```

Ако test prerequisites липсват, прочети същите source файлове и подготви хипотезите — но **не отчитай непуснат тест като PASS**.

Базови файлове:
- [application.properties](https://github.com/JosRoseboom/funfactstore/blob/master/src/main/resources/application.properties)
- [ShopControllerIntegrationTest.java](https://github.com/JosRoseboom/funfactstore/blob/master/src/test/java/com/easingyou/funfactstore/fact/shop/ShopControllerIntegrationTest.java)
- [FunFact.java](https://github.com/JosRoseboom/funfactstore/blob/master/src/main/java/com/easingyou/funfactstore/fact/FunFact.java)
- [FunFactRepo.java](https://github.com/JosRoseboom/funfactstore/blob/master/src/main/java/com/easingyou/funfactstore/fact/FunFactRepo.java)

## Lab 1 — physical connection checkout срещу HTTP lifecycle

**Входен казус:** request изпълнява кратък SELECT и после извиква бавна външна услуга.

**Baseline:** пусни Shop controller integration test с debug logging за Hibernate SQL, Hikari и авторския connection tracker (пътят към logger е записан в `application.properties` като коментирани настройки).

Запиши на хартия/в таблицата:

| Вариант | First checkout | Last return | Общо време на заета връзка | HTTP време | SQL count |
| --- | --- | --- | --- | --- | --- |
| Първоначални настройки | непроверено | непроверено | непроверено | непроверено | непроверено |
| OSIV = false | непроверено | непроверено | непроверено | непроверено | непроверено |
| Lazy acquisition / narrower transaction | непроверено | непроверено | непроверено | непроверено | непроверено |

**Експеримент:** сравни `spring.jpa.open-in-view=true` и `false`. Очакваме разлика в lifecycle-а на EntityManager и евентуално на връзката, **но действителното checkout timing зависи от настройките**. Не твърди, че OSIV always holds a physical connection до края.

**Провери correctness:** ако след `false` някой controller/template чете uninitialized relation, тестът може да покаже `LazyInitializationException`. Решавай с explicit fetch plan / DTO.

## Lab 2 — Spring Boot 4.1 lazy connection fetch

**Хипотеза:** транзакция извиква външен API и **едва след това** прави първия SELECT. При:

```properties
spring.datasource.connection-fetch=lazy
```

physical checkout трябва да се отложи до първото SQL statement.

**Стъпки:**

1. Изолирай сценарий `externalCall() → SELECT` в интеграционен тест. Външната услуга трябва да е stub/mock със стабилен latency, не реална нестабилна услуга.
2. Пусни с `eager` (default) и `lazy`, запиши checkout/return времена.
3. Смени на `SELECT → externalCall() → SELECT`, пак измери.
4. Провери, че при втория вариант `lazy` **не** връща връзката между SQL statements в същата транзакция.
5. Сравни с две къси транзакции чрез `TransactionTemplate`; отбележи какво consistency вече не гарантират.

**Внимание:** не задавай `hibernate.connection.provider_disables_autocommit=true`, ако нямаш доказателство, че реалният provider връща connections с `autoCommit=false`.

**Изходен въпрос:** Как се различава „transaction започна“ от „physical connection е взета“? Обясни без да казваш „lazy = no transaction“.

## Lab 3 — N+1, to-one LAZY и persistence context

**Вход:** няколко `FunFact` entity-та сочат към няколко различни `AppUser` admins.

1. Изпълни `findAll()`, после в транзакция прочети поле от `fact.getAdmin()`, което изисква инициализация.
2. Очакваме initial SELECT + secondary SELECT-и за различните неизтеглени admins; отчети **фактическия** брой, не приемай „точно N+1“.
3. Изпълни `findAllWithAdmin()` от авторския `FunFactRepo` — тук има `JOIN FETCH`.
4. Сравни SQL count и total DB time. Извикай `findAllWithAdmin()` след `entityManager.clear()` или в отделен persistence context, за да не скрива first-level cache резултата.
5. Увеличи броя записи и промени cardinality: 100 facts към 1 admin срещу 100 facts към 100 admins.

**Очакван mental model:**

```text
findAll → access lazy association → potential secondary selects
findAllWithAdmin → select with joined admin → no later lazy select for admin
```

**Не прави грешен извод:** „Hibernate кешира, значи N+1 няма значение“. Cache само може да намали броя дублирани loads в конкретен context.

## Lab 4 — DTO projection вместо entity graph за read endpoint

**Цел:** read API връща `purchaseDate`, `factText` и `supportEmail` без да materialize-ва излишни managed entities.

1. Напиши record с трите полета.
2. Напиши JPQL `select new fully.qualified.Dto(...)` с нужните joins.
3. Сравни с `findPurchases() → loop → getFunFact().getAdmin().getEmail()`.
4. Измери: брой SQL, избрани колони, rows returned, HTTP latency и памет при реалистичен набор.
5. Включи отрицателен тест: смени constructor signature/тип и докажи, че query validation или startup/integration test открива несъответствието.
6. Пренеси към native SQL вариант и **отделно** провери JDBC time types и alias mapping. За Oracle се увери как се мапват `DATE` / `TIMESTAMP` спрямо PostgreSQL, вместо да разчиташ на същите Java types без проверка.

Изрично опиши trade-off: DTO е отлично за четене, но няма dirty-checking/identity semantics на managed entity.

## Lab 5 — Spring Boot/JPA semantics в нашия executable module

От root на `education`, с **Java 25**:

```bash
mvn -pl persistence/repository-unit-of-work -am test
```

Действителни вече съществуващи тестове:
- [managed entity update without `save`](../repository-unit-of-work/src/test/java/bg/hristomanov/education/uow/RepositoryUnitOfWorkTest.java)
- [Identity Map within transaction](../repository-unit-of-work/src/test/java/bg/hristomanov/education/uow/RepositoryUnitOfWorkTest.java)
- [`flush` followed by exception → rollback](../repository-unit-of-work/src/test/java/bg/hristomanov/education/uow/RepositoryUnitOfWorkTest.java)
- [detached entity changes are not dirty-checked](../repository-unit-of-work/src/test/java/bg/hristomanov/education/uow/RepositoryUnitOfWorkTest.java)

Те доказват **семантика**, но **не** са benchmark за Hikari/OSIV/N+1. Не ги представяй като доказателство за подобрение на pool utilization.

## Lab 6 — малък Senior production incident

Има 10 connections, 30 concurrent requests, всяка операция прави SELECT (15 ms), чака remote risk service (500 ms), прави втори SELECT (15 ms), и приключва.

Задачата не е само „вдигни pool на 30“. Отговори:

1. Кога и за колко време е заемана връзката в `SELECT → remote → SELECT`?
2. Ще помогне ли `spring.datasource.connection-fetch=lazy` след **първия** SELECT?
3. Какви transaction consistency trade-offs носи разцепването на операцията?
4. Как би измерил `active`, `pending`, `acquire duration` и p95/p99?
5. Какви са възможните коренни причини, ако active=10, pending>0, но DB CPU е нисък?
6. Ако endpoint чете само 3 fields, трябва ли да му връщаме цели JPA entities?
7. Ако има `JOIN FETCH` на две collection associations и pagination, какво може да се влоши?

**Очакван изход:** кратък diagnosis report с baseline, root cause, proposed change, measurement plan, correctness risks и rollback plan. Това е по-реалистична Senior проверка от въпрос „какво значи FetchType.LAZY“.

## Checklist преди да отчетеш lab като завършен

- [ ] Има reproducible setup, commit/version и описани prerequisites.
- [ ] Записани са реални observed timings/SQL counts — без измислени числа.
- [ ] Измерени са поне два варианта на **един и същ** use case.
- [ ] Не са смесени H2/Oracle/PostgreSQL резултати.
- [ ] Проверени са correctness и integration constraints.
- [ ] Формулирани са ясно trade-offs и кое **не** решава промяната.
- [ ] Можеш да обясниш разликата между transaction, persistence context и physical connection.

**Преход към диагностика:** [DB-11 → DB-14 в roadmap](../../docs/learning/ROADMAP.md). Няма автоматична промяна в `PROGRESS.md` — тя следва **действителен отговор и проверка**.
