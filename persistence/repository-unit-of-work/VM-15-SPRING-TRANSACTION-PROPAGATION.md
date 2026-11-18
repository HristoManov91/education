# VM-15 — Spring transaction propagation, AOP proxies, rollback rules и OSIV

> **Практически урок за Spring Boot 4.1.1 / Spring Framework 7 / Java 25**, надграждащ съществуващите [Repository & Unit of Work](README.md) и [JDBC/MVCC/Locking лаборатории](../../database/transaction-labs/README.md). Тук тестовете използват H2 за Spring transaction/proxy semantics, не за DB-specific MVCC или Oracle locking.

## 1. Реалният казус: хванахме exception, но нищо не се записа

Business flow регистрира поръчка, после вика отделен Spring service, който също използва `@Transactional`. Вътрешната операция хвърля `IllegalStateException`. Външният service хваща exception-а и логва warning; очаква да commit-не своя запис.

Вместо това получава `UnexpectedRollbackException`. Втори сценарий прави `REQUIRES_NEW` за audit и неговият ред **остава записан**, въпреки че външната поръчка се rollback-ва. Трети developer премества `@Transactional` метода в същия class и transaction-ът изчезва при self-invocation. Това не е „Hibernate бъг“ — това са три различни contracts.

**Ще научим:** разликата между logical propagation scope и physical DB transaction, Spring proxy interception и default rollback policy.

## 2. Ментален модел

~~~text
REST/controller
     |
     v
Spring proxy -- TransactionInterceptor -- service method
     |
     +--- invokes *another bean* via proxy ---> propagation policy
                                             REQUIRED / REQUIRES_NEW / ...
     |
     +--- this.someAnnotatedMethod() --------> bypasses proxy advice

DB transaction != JDBC lease != Hibernate persistence context != HTTP request.
~~~

Annotation `@Transactional` е **metadata**, която в proxy-mode се прилага само при извикване, минало през Spring proxy. Анотацията сама по себе си не започва транзакция при `this.internal()`.

## 3. Съществените propagation стратегии

| Режим | Какво реално прави | Използвай, когато | Не използвай, когато |
|---|---|---|---|
| `REQUIRED` (default) | Участва в съществуваща физическа транзакция или създава нова; вътрешен rollback-only може да провали outer commit | Атомарна бизнес операция с няколко стъпки | Искаш независим commit на inner |
| `REQUIRES_NEW` | Suspend outer и започва **независима** физическа транзакция | Запис, който умишлено трябва да commit-не отделно, например audit с ясно дефинирана семантика | Искаш атомарност с outer; ограничен pool при голяма паралелност |
| `NESTED` | JDBC savepoint в **същата** физическа транзакция; частичен rollback | Съвместими JDBC операции с savepoint support | Очакваш JPA nested entity flush/rollback или независим commit |
| `SUPPORTS` | Участва, ако има transaction; иначе работи без transaction | Read helper, който може да работи в двата контекста | Операция трябва да гарантира transaction |
| `MANDATORY` | Изисква вече активна transaction; иначе грешка | Защитен вътрешен метод, който никога не се вика самостоятелно | Публичен API, допустим без outer tx |
| `NOT_SUPPORTED` | Suspend-ва съществуващата transaction и работи без нея | Nontransactional slow work, когато suspend semantics са целенасочени | Трябва последователен transactional snapshot |
| `NEVER` | Отказва, ако има active transaction | Ясно разделен nontransactional code path | Общ utility, който се извиква и от transactional код |

**Важно:** `REQUIRES_NEW` може да има нужда от **още една** physical JDBC connection, докато outer държи своята. При много конкурентни requests това може да изчерпи connection pool-а. Не го използвай механично като решение на `UnexpectedRollbackException`.

## 4. Bad и good — REQUIRED: външен catch ≠ commit

~~~java
@Transactional
public void outer(String outerKey, String innerKey) {
    repository.insert(outerKey);
    try {
        innerService.insertThenFailParticipating(innerKey);
    } catch (IllegalStateException expected) {
        // Изглежда, че сме обработили грешката.
    }
} // UnexpectedRollbackException; двата INSERT са rollback-нати.
~~~

Съществуващата `REQUIRED` inner транзакция е *логически scope* на същата физическа DB transaction. След като inner proxy види unchecked failure, маркира транзакцията като rollback-only. Външният Java catch **не изчиства** rollback-only. Spring изхвърля `UnexpectedRollbackException`, за да не лъже caller-а, че commit е бил успешен.

**Кога този подход е правилен:** когато цялата бизнес операция **трябва** да е атомарна, трябва да оставим exception да прекъсне outer flow или изрично да моделираме очаквания бизнес отказ. Не превръщай exception catch в „игнорирай транзакцията“.

[Реален service code](src/main/java/bg/hristomanov/education/uow/service/VladTransactionOrchestrationService.java) · [inner service](src/main/java/bg/hristomanov/education/uow/service/VladTransactionInnerService.java) · [test](src/test/java/bg/hristomanov/education/uow/VladSpringTransactionsTest.java): `UnexpectedRollbackException` и **нула записани редове**.

## 5. REQUIRES_NEW — независимата транзакция умишлено оцелява

~~~java
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void insertIndependent(String key) {
    jdbc.update("INSERT INTO vm15_tx_events(event_key, note) VALUES (?, ?)", key, "audit");
}
~~~

В отделен Spring bean горният метод commit-ва независимо. Външният може да fail-не по-късно и да rollback-не, без да rollback-не вътрешния audit. [Тестът](src/test/java/bg/hristomanov/education/uow/VladSpringTransactionsTest.java) доказва **само inner записът е видим** след outer failure.

**Ползи:** отделен commit, освобождаване на inner locks при неговото приключване, независим timeout/isolation, ако manager поддържа.

**Недостатъци:** неатомарност, допълнителна connection capacity и уязвимост към partial-success сценарии. За надеждно публикуване на събитие извън DB често е по-подходящ [Transactional Outbox](../../spring/messaging/outbox-inbox/README.md), а не директен remote HTTP call в REQUIRES_NEW.

## 6. NESTED — JDBC savepoint, не REQUIRES_NEW

~~~text
outer BEGIN
  INSERT outer
  SAVEPOINT s1
    INSERT nested
  ROLLBACK TO s1
  INSERT after
outer COMMIT

Persisted: outer, after. Nested: rolled back.
~~~

В [VladSpringTransactionsTest.nestedJdbcSavepointRollsBackOnlyItsOwnWork](src/test/java/bg/hristomanov/education/uow/VladSpringTransactionsTest.java) **изрично** създаваме `DataSourceTransactionManager` и `TransactionTemplate(PROPAGATION_NESTED)`. Тестът доказва physical JDBC savepoint в H2. Това **не означава**, че `JpaTransactionManager` автоматично осигурява JPA-level nested persistence context semantics. Изборът на transaction manager е част от contract-а.

## 7. Proxy self-invocation — дори public annotation не е достатъчна

~~~java
public boolean indirect() { return inspected(); } // this.inspected(); no interceptor

@Transactional
public boolean inspected() {
    return TransactionSynchronizationManager.isActualTransactionActive();
}
~~~

[SelfInvocationProbe](src/main/java/bg/hristomanov/education/uow/service/VladSelfInvocationProbe.java) е реален Spring bean. [Тестът](src/test/java/bg/hristomanov/education/uow/VladSpringTransactionsTest.java) проверява **self call = false**, **call through bean proxy = true**.

**Good:** изнеси transactional метода в отделен Spring bean (или избери явен `TransactionTemplate`). Не се опитвай да поправяш проблема чрез private methods и допълнителни annotations. AspectJ weaving има различен interception model, но не е нужен за този lab.

## 8. Checked vs unchecked exceptions

Spring `@Transactional` по подразбиране rollback-ва при `RuntimeException/Error`, **не** при checked `IOException`. Тестът демонстрира, че редът **остава записан** при checked exception без `rollbackFor`, и изчезва с:

~~~java
@Transactional(rollbackFor = IOException.class)
~~~

Spring Framework позволява глобално `ALL_EXCEPTIONS` rollback default чрез `@EnableTransactionManagement(rollbackOn=ALL_EXCEPTIONS)`. Тук **не сме го включили** — упражняваме default policy + explicit override. Дори при подобна настройка explicit rollback rules имат приоритет.

**Кога да не използваме default:** ако checked business exceptions по вашия contract означават failure и не трябва да commit-ват. Предпочитай ясна проектна политика.

## 9. OSIV, readOnly и physical JDBC connection lifetime

Основният [application.properties](src/main/resources/application.properties) има `spring.jpa.open-in-view=false`. [VM-10 тестът](src/test/java/bg/hristomanov/education/uow/VladMappingTest.java) доказва lazy collection failure извън transaction, вместо да скрива fetch plan грешката чрез OSIV. Това **не доказва**, че OSIV=true държи physical JDBC connection през целия request — това е отделен lease measurement и зависи от Hibernate/Boot settings.

Също: `@Transactional(readOnly=true)` е **hint**, а не универсална забрана за INSERT/UPDATE. Hibernate flush mode, transaction read-only propagation, database enforcement и actual connection acquisition трябва да се разгледат по конкретна версия.

Свързани уроци: [VM-05 JDBC](../../database/transaction-labs/docs/VM-05-JDBC-POOL.md) и [JPA Performance](../jpa-performance/README.md).

## 10. Decision contract

| Въпрос | Резултат |
|---|---|
| Проблем | Неочаквани rollbacks, partial commits, bypassed annotations, погрешна nested isolation |
| Кога | Сложна service orchestration с повече от един transaction scope |
| Кога НЕ | Един прост CRUD метод, за който default REQUIRED е напълно достатъчен |
| Печелим | Correctness и ясно избран commit/rollback boundary |
| Губим | Сложност, риск от partial success, допълнителни connections и transaction overhead |
| Алтернатива | Кратка единична транзакция, `TransactionTemplate`, outbox, explicit business conflict policy |
| Доказателство | [6 Spring Boot/H2 tests](src/test/java/bg/hristomanov/education/uow/VladSpringTransactionsTest.java) и [VM-10 OSIV/LAZY test](src/test/java/bg/hristomanov/education/uow/VladMappingTest.java) |

## 11. README → код

| Концепция | Production-like Java | Тест |
|---|---|---|
| `REQUIRED` rollback-only | [Orchestrator](src/main/java/bg/hristomanov/education/uow/service/VladTransactionOrchestrationService.java) / [Inner](src/main/java/bg/hristomanov/education/uow/service/VladTransactionInnerService.java) | [required...](src/test/java/bg/hristomanov/education/uow/VladSpringTransactionsTest.java) |
| `REQUIRES_NEW` independent commit | [Inner](src/main/java/bg/hristomanov/education/uow/service/VladTransactionInnerService.java) | [requiresNew...](src/test/java/bg/hristomanov/education/uow/VladSpringTransactionsTest.java) |
| `NESTED` JDBC savepoint | [TransactionTemplate in test](src/test/java/bg/hristomanov/education/uow/VladSpringTransactionsTest.java) | [nestedJdbcSavepoint...](src/test/java/bg/hristomanov/education/uow/VladSpringTransactionsTest.java) |
| Self-invocation vs proxy | [SelfInvocationProbe](src/main/java/bg/hristomanov/education/uow/service/VladSelfInvocationProbe.java) | [publicTransactional...](src/test/java/bg/hristomanov/education/uow/VladSpringTransactionsTest.java) |
| Checked exception rollback policy | [Inner](src/main/java/bg/hristomanov/education/uow/service/VladTransactionInnerService.java) | [checked exception tests](src/test/java/bg/hristomanov/education/uow/VladSpringTransactionsTest.java) |
| OSIV=false lazy behavior | [application.properties](src/main/resources/application.properties) | [VladMappingTest](src/test/java/bg/hristomanov/education/uow/VladMappingTest.java) |

## 12. Стартиране, упражнения и финален checklist

~~~bash
mvn -pl persistence/repository-unit-of-work test
~~~

Не е необходим Docker. Този lab не е DB-specific isolation bench: използвай [PostgreSQL transaction lab](../../database/transaction-labs/README.md) за MVCC, row locks, deadlocks.

[Упражнения и checkpoint](VM-15-EXERCISES.md) · [NotebookLM аудио урок](NOTEBOOKLM-VLAD-VM15.md).

**Code-review checklist:** transaction manager ли е правилен? Spring proxy ли извиква annotated method? REQUIRED inner exception маркира ли rollback-only? REQUIRES_NEW създава ли partial commit и има ли pool capacity? NESTED наистина ли е JDBC savepoint? Checked exceptions дефинирани ли са? OSIV/fetch-plan границите ясни ли са? Имаме ли възпроизводим test, а не само `@Transactional` annotation?

### Оригинални източници

- Vlad Mihalcea — [Spring Transaction and Connection Management](https://vladmihalcea.com/spring-transaction-connection-management/).
- [Spring Framework 7: Transaction Propagation](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/tx-propagation.html).
- [Spring Framework 7: @Transactional Settings](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/annotations.html).
- [Spring Framework 7: AOP Proxies and self-invocation](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html).
- [Spring Boot SQL transaction reference](https://docs.spring.io/spring-boot/reference/data/sql.html).
