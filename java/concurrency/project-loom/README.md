# Project Loom — Virtual Threads, Structured Concurrency и Scoped Values

Практическа Java/Spring лаборатория, базирана на видеото **Beyond Virtual Threads: Structured Concurrency** и оригиналния SpringBootLoom demo project. Целта не е да копира оригинала, а да възпроизведе идеите му с Java 25, актуален Spring Boot и обяснения на български.

## Какво ще научим

- каква е разликата между platform thread, OS thread, virtual thread и carrier thread;
- защо virtual threads са полезни при голям брой blocking I/O операции;
- защо virtual threads не трябва да се pool-ват като скъп ресурс;
- как Spring Boot може да обработва заявки с virtual threads;
- как `StructuredTaskScope` свързва lifecycle-а на parent задача с child задачите;
- как `fork -> join` прави паралелния control flow видим в структурата на кода;
- как `Joiner.anySuccessfulResultOrThrow()` реализира „първият успешен резултат печели“ в Java 25;
- как Structured Concurrency се сравнява с `CompletableFuture`;
- как `ScopedValue` пренася request context без да го подаваме през всеки method parameter;
- кои ограничения остават дори когато thread-овете вече са евтини.

## Версии в тази лаборатория

- Java: **25 LTS**
- Spring Boot: **4.1.1**
- Build: Maven
- Structured Concurrency: **preview API** в Java 25 — затова Maven конфигурацията включва `--enable-preview`.
- Virtual Threads: финална Java функционалност от Java 21.
- Scoped Values: финална функционалност в Java 25.

Оригиналният `SpringBootLoom` repository към момента използва JDK 26 и Spring Boot 4.0.x. Тук умишлено оставаме на Java 25 LTS и използваме Java 25 API-то, за да бъде лабораторията подходяща за дългосрочната `education` база.

### Важна version-specific разлика

Текущият оригинален пример на автора използва `Joiner.anySuccessfulOrThrow()`. В **Java 25** официалното API се казва `Joiner.anySuccessfulResultOrThrow()`, затова нашият пример използва Java 25 името. Това е пример защо при учебен код не копираме сляпо source code от друга JDK версия.

Конфигурацията за Java 25 и preview API е в [`project-loom/pom.xml`](./pom.xml).

## Структура

```text
project-loom/
├── bank-api/       # приложението, в което изучаваме Loom API-тата
├── bank-services/  # бавни dummy downstream услуги
└── README.md
```

`bank-services` симулира външни HTTP услуги с различна latency. `bank-api` събира тези данни и изчислява примерна оферта за кредит.

## README → код

Това README е главната учебна история. Когато искаш да видиш реалната реализация на дадена идея, използвай директните линкове по-долу. В отделните раздели също има линкове към конкретния код, за да не се налага да го търсиш по package-ите.

| Концепция | Production-like код / конфигурация | Доказателство / тест |
| --- | --- | --- |
| Spring Boot върху Virtual Threads | [`application.properties`](./bank-api/src/main/resources/application.properties), [`LoanApplicationController.java`](./bank-api/src/main/java/bg/hristomanov/education/loom/bankapi/controller/LoanApplicationController.java) | [`VirtualThreadTest.java`](./bank-api/src/test/java/bg/hristomanov/education/loom/bankapi/VirtualThreadTest.java) |
| Loan application orchestration | [`LoanApplicationService.java`](./bank-api/src/main/java/bg/hristomanov/education/loom/bankapi/service/LoanApplicationService.java) | стартирай `POST /api/loan-applications` |
| `fork -> join` / Structured Concurrency | [`StructuredCustomerInfoLoader.java`](./bank-api/src/main/java/bg/hristomanov/education/loom/bankapi/service/StructuredCustomerInfoLoader.java) | поведението се наблюдава и в request логовете |
| First-successful `Joiner` | [`BankClients.java`](./bank-api/src/main/java/bg/hristomanov/education/loom/bankapi/client/BankClients.java) — `CreditScoreClient` | симулирани provider-и в [`DemoBankController.java`](./bank-services/src/main/java/bg/hristomanov/education/loom/services/DemoBankController.java) |
| Structured Concurrency срещу `CompletableFuture` | [`StructuredCustomerInfoLoader.java`](./bank-api/src/main/java/bg/hristomanov/education/loom/bankapi/service/StructuredCustomerInfoLoader.java) | [`CompletableFutureCustomerInfoLoader.java`](./bank-api/src/main/java/bg/hristomanov/education/loom/bankapi/service/CompletableFutureCustomerInfoLoader.java) |
| `ScopedValue` request context | [`RequestContext.java`](./bank-api/src/main/java/bg/hristomanov/education/loom/bankapi/context/RequestContext.java), binding в [`LoanApplicationController.java`](./bank-api/src/main/java/bg/hristomanov/education/loom/bankapi/controller/LoanApplicationController.java) | [`RequestContextTest.java`](./bank-api/src/test/java/bg/hristomanov/education/loom/bankapi/context/RequestContextTest.java) |
| Наследяване на request context към child tasks | [`StructuredCustomerInfoLoader.java`](./bank-api/src/main/java/bg/hristomanov/education/loom/bankapi/service/StructuredCustomerInfoLoader.java), логване в [`BankClients.java`](./bank-api/src/main/java/bg/hristomanov/education/loom/bankapi/client/BankClients.java) | [`RequestContextTest.java`](./bank-api/src/test/java/bg/hristomanov/education/loom/bankapi/context/RequestContextTest.java) |
| Бавни downstream услуги за експериментите | [`DemoBankController.java`](./bank-services/src/main/java/bg/hristomanov/education/loom/services/DemoBankController.java) | стартирай `bank-services` локално |

## 1. Проблемът — blocking кодът е лесен за четене, но platform threads са скъпи

Класическият Spring MVC код е удобен, защото следва естествен последователен control flow:

```text
request
  -> load customer
  -> load accounts
  -> load loans
  -> load credit score
  -> calculate offer
```

Проблемът идва, когато хиляди заявки прекарват голяма част от времето си в чакане на база данни или HTTP услуга. При традиционния thread-per-request модел platform thread заема OS thread, докато операцията чака.

Virtual thread е Java `Thread`, но JVM може да го **unmount-не** от carrier/platform thread, когато той чака подходяща blocking I/O операция, и да използва carrier thread-а за друга работа.

Това позволява да запазим простия blocking стил на програмиране, без да държим огромен брой тежки OS threads.

### Важно: virtual threads не са „по-бързи threads“

Те подобряват **scalability/throughput при много едновременно чакащи задачи**, а не правят CPU операцията по-бърза.

Ако имаме:

```text
100 000 virtual threads
        |
        v
HikariCP maxPoolSize = 20
```

пак имаме максимум около 20 едновременни DB операции през този pool. Същото важи за HTTP connection pools, rate limits, semaphore-и, downstream capacity и CPU.

## 2. Virtual Threads в Spring Boot

**Виж кода:** [`application.properties`](./bank-api/src/main/resources/application.properties), [`LoanApplicationController.java`](./bank-api/src/main/java/bg/hristomanov/education/loom/bankapi/controller/LoanApplicationController.java), [`VirtualThreadTest.java`](./bank-api/src/test/java/bg/hristomanov/education/loom/bankapi/VirtualThreadTest.java).

В `bank-api/src/main/resources/application.properties`:

```properties
spring.threads.virtual.enabled=true
spring.main.keep-alive=true
```

Endpoint-ът `GET /api/thread-info` връща текущия thread и `virtual=true/false`, за да можем да проверим поведението, вместо само да вярваме на конфигурацията.

### Защо не pool-ваме virtual threads

Platform threads са тежък и ограничен ресурс и затова исторически използваме pools.

Virtual threads са евтини и идеята е **thread per task**. Да създадем pool от 100 virtual threads само връща изкуствено ограничение, което Loom се опитва да премахне.

Ако искаме да ограничим достъпа до реален scarce resource, ограничаваме самия ресурс — например connection pool, rate limiter или semaphore — не броя virtual threads.

## 3. Loan application use case

**Виж orchestration-а:** [`LoanApplicationController.java`](./bank-api/src/main/java/bg/hristomanov/education/loom/bankapi/controller/LoanApplicationController.java) → [`LoanApplicationService.java`](./bank-api/src/main/java/bg/hristomanov/education/loom/bankapi/service/LoanApplicationService.java) → [`StructuredCustomerInfoLoader.java`](./bank-api/src/main/java/bg/hristomanov/education/loom/bankapi/service/StructuredCustomerInfoLoader.java).

```text
POST /api/loan-applications
        |
        v
   load Customer
        |
        +---------------------------+
        |             |             |
        v             v             v
    Accounts        Loans       Credit Score
                                  |       |
                                  v       v
                            provider-a provider-b
                                  \       /
                           first successful
        |             |             |
        +-------------+-------------+
                      |
                      v
                calculate Offer
```

След като customer е известен, accounts, loans и credit score са независими I/O операции и могат да вървят паралелно.

Dummy downstream поведението е в [`DemoBankController.java`](./bank-services/src/main/java/bg/hristomanov/education/loom/services/DemoBankController.java), където услугите нарочно имат различна latency, за да можем да наблюдаваме concurrent поведението.

## 4. Structured Concurrency

**Основен пример:** [`StructuredCustomerInfoLoader.java`](./bank-api/src/main/java/bg/hristomanov/education/loom/bankapi/service/StructuredCustomerInfoLoader.java).

Основният код е:

```java
try (var scope = StructuredTaskScope.open()) {
    var accountsTask = scope.fork(() -> accountClient.getAccounts(customer.id()));
    var loansTask = scope.fork(() -> loanClient.getLoans(customer.id()));
    var scoreTask = scope.fork(() -> creditScoreClient.getFirstSuccessfulScore(customer.id()));

    scope.join();

    return new CustomerInfo(
            accountsTask.get(),
            loansTask.get(),
            scoreTask.get());
}
```

### `fork`

Разделяме една parent задача на няколко child задачи. При default configuration `StructuredTaskScope` използва virtual threads за subtasks.

В реалния class коментарът непосредствено пред `fork(...)` обяснява **защо** точно тези три операции могат да бъдат паралелни — те са независими и основно чакат downstream I/O.

### `join`

Това е ясната граница, на която паралелните пътища отново се събират. Parent задачата не трябва да „избяга“, докато нейните child задачи още живеят.

Default `StructuredTaskScope.open()` в Java 25 използва fail-fast policy за subtasks: ако някоя required child задача fail-не, `join()` приключва с failure и останалата работа се cancel-ва.

### `try-with-resources`

Scope-ът има lexical lifetime. Когато излезем от блока, lifecycle-ът на concurrent работата е приключил. Това е една от големите разлики спрямо свободно създадени futures/tasks, чиито lifecycle може да стане трудно проследим.

## 5. „Първият успешен резултат печели“ с Joiner

**Виж реализацията:** [`BankClients.java`](./bank-api/src/main/java/bg/hristomanov/education/loom/bankapi/client/BankClients.java), class `CreditScoreClient`, method `getFirstSuccessfulScore(...)`.

Имаме два credit score provider-а. За офертата е достатъчен един валиден резултат.

```java
try (var scope = StructuredTaskScope.open(
        StructuredTaskScope.Joiner.<CreditScore>anySuccessfulResultOrThrow())) {

    scope.fork(() -> getScore(customerId, "provider-a"));
    scope.fork(() -> getScore(customerId, "provider-b"));

    return scope.join();
}
```

В dummy услугата `provider-b` отговаря по-бързо. Можеш да видиш симулацията в [`DemoBankController.java`](./bank-services/src/main/java/bg/hristomanov/education/loom/services/DemoBankController.java).

След успешния резултат joiner-ът може да прекрати нуждата от останалата sibling работа според lifecycle-а на scope-а.

Ако единият provider fail-не, другият все още може да даде успешен резултат. Ако всички subtasks fail-нат, `join()` приключва с `StructuredTaskScope.FailedException`.

## 6. CompletableFuture срещу Structured Concurrency

**Сравни директно двата файла:**

- Structured вариант: [`StructuredCustomerInfoLoader.java`](./bank-api/src/main/java/bg/hristomanov/education/loom/bankapi/service/StructuredCustomerInfoLoader.java)
- CompletableFuture вариант: [`CompletableFutureCustomerInfoLoader.java`](./bank-api/src/main/java/bg/hristomanov/education/loom/bankapi/service/CompletableFutureCustomerInfoLoader.java)

`CompletableFuture` не е „лош“ API — той е полезен, особено когато действително изграждаме async pipeline. При task-oriented request logic обаче lifecycle, cancellation и context propagation по-лесно се раздалечават от lexical structure на бизнес операцията.

Тук има и конкретна разлика със `ScopedValue`: bindings се наследяват от StructuredTaskScope child threads. При произволно създадени threads от `newVirtualThreadPerTaskExecutor()` нашият comparative пример трябва експлицитно да capture-не и re-bind-не request context-а.

Structured Concurrency е особено естествена, когато мислим така:

> Тази операция има три child операции и всички принадлежат на същата parent операция.

## 7. Scoped Values

**Виж кода:** [`RequestContext.java`](./bank-api/src/main/java/bg/hristomanov/education/loom/bankapi/context/RequestContext.java), binding-а в [`LoanApplicationController.java`](./bank-api/src/main/java/bg/hristomanov/education/loom/bankapi/controller/LoanApplicationController.java) и тестовете в [`RequestContextTest.java`](./bank-api/src/test/java/bg/hristomanov/education/loom/bankapi/context/RequestContextTest.java).

Вместо да подаваме `requestId` през всеки метод:

```text
controller(requestId)
  -> service(requestId)
      -> loader(requestId)
          -> client(requestId)
```

използваме:

```java
private static final ScopedValue<RequestMetadata> METADATA = ScopedValue.newInstance();
```

и ограничен binding:

```java
ScopedValue.where(METADATA, metadata)
        .call(() -> loanApplicationService.apply(request));
```

Кодът надолу по call stack-а може да прочете metadata чрез `RequestContext.current()`.

### Защо не ThreadLocal

За еднопосочно request metadata ScopedValue има важни свойства:

- стойността е достъпна само в определения dynamic scope;
- caller-ът контролира binding-а;
- след края на scope-а стойността автоматично вече не е bound;
- inheritance към StructuredTaskScope child threads е част от structured модела;
- не изисква ръчно `remove()` cleanup като типичните ThreadLocal patterns.

[`RequestContextTest.java`](./bank-api/src/test/java/bg/hristomanov/education/loom/bankapi/context/RequestContextTest.java) доказва както bounded lifetime-а, така и inheritance към structured child thread.

## 8. ScopedValue + StructuredTaskScope

**Проследи целия път:** [`LoanApplicationController.java`](./bank-api/src/main/java/bg/hristomanov/education/loom/bankapi/controller/LoanApplicationController.java) → [`RequestContext.java`](./bank-api/src/main/java/bg/hristomanov/education/loom/bankapi/context/RequestContext.java) → [`StructuredCustomerInfoLoader.java`](./bank-api/src/main/java/bg/hristomanov/education/loom/bankapi/service/StructuredCustomerInfoLoader.java) → [`BankClients.java`](./bank-api/src/main/java/bg/hristomanov/education/loom/bankapi/client/BankClients.java).

Controller-ът bind-ва request metadata. След това `StructuredCustomerInfoLoader` създава child tasks. Binding-ът на ScopedValue се наследява от тези structured child threads, затова `AccountClient`, `LoanClient` и `CreditScoreClient` могат да логват същия `requestId` без параметърът да бъде прокарван ръчно.

```text
request scope
  requestId = X
      |
      +-- accounts virtual thread ---- sees X
      +-- loans virtual thread ------- sees X
      +-- credit score scope
             +-- provider-a ---------- sees X
             +-- provider-b ---------- sees X
```

## 9. Кога Virtual Threads помагат

Подходящи са най-вече когато приложението има много concurrent задачи, които прекарват съществено време в blocking I/O:

- JDBC;
- HTTP calls;
- file/network I/O;
- request-per-thread server workloads.

В тази лаборатория blocking HTTP calls се виждат директно в [`BankClients.java`](./bank-api/src/main/java/bg/hristomanov/education/loom/bankapi/client/BankClients.java), който използва Spring `RestClient` към dummy downstream услугата.

## 10. Кога не са магическо решение

Virtual threads не премахват:

- DB connection pool лимита;
- downstream rate limits;
- API quotas;
- CPU saturation;
- lock contention;
- лоши SQL заявки;
- неправилна архитектура.

CPU-bound работа няма автоматично да стане по-бърза само защото е стартирана в повече virtual threads.

## 11. Как да стартираме

### Prerequisites

- JDK 25
- Maven 3.9+

От root директорията на repository-то:

```bash
mvn clean verify
```

Maven настройките за preview Structured Concurrency API са в [`project-loom/pom.xml`](./pom.xml).

След това от `java/concurrency/project-loom` стартирай dummy downstream услугите:

```bash
mvn -pl bank-services spring-boot:run
```

Във втори terminal:

```bash
mvn -pl bank-api spring-boot:run
```

Провери virtual thread:

```bash
curl http://localhost:8080/api/thread-info
```

Примерна loan заявка:

```bash
curl -X POST http://localhost:8080/api/loan-applications \
  -H 'Content-Type: application/json' \
  -d '{
    "customerId": "2b8d8f54-e104-4d21-97de-6ef9a78db392",
    "amount": 25000,
    "purpose": "home renovation"
  }'
```

В логовете на `bank-api` трябва да се виждат различни virtual threads, но един и същ request id за structured child операциите. Кодът, който логва тези стойности, е в [`BankClients.java`](./bank-api/src/main/java/bg/hristomanov/education/loom/bankapi/client/BankClients.java).

## 12. Как го доказваме

В `src/test` има executable проверки, към които можеш да отидеш директно:

- [`VirtualThreadTest.java`](./bank-api/src/test/java/bg/hristomanov/education/loom/bankapi/VirtualThreadTest.java) — създаване и разпознаване на virtual thread;
- [`RequestContextTest.java`](./bank-api/src/test/java/bg/hristomanov/education/loom/bankapi/context/RequestContextTest.java) — bounded lifetime на ScopedValue binding-а и inheritance към StructuredTaskScope child thread.

Следващи полезни тестове са cancellation/error propagation и first-successful credit score стратегията.

## 13. Какво да запомня

1. Virtual thread е Java `Thread`, не callback или reactive abstraction.
2. Virtual threads са евтини — обичайният модел е thread-per-task, не pool от virtual threads.
3. Те са най-полезни при много blocking I/O, а не като ускорител за CPU-bound работа.
4. Structured Concurrency групира related child tasks в един lifecycle.
5. `fork -> join` прави concurrent control flow видим и ограничен.
6. Joiner описва политиката за резултатите — например всички успешни или първия успешен.
7. ScopedValue е добър избор за immutable/request context, който се предава еднопосочно надолу.
8. ScopedValue и StructuredTaskScope работят естествено заедно чрез inheritance към structured child threads.
9. Loom премахва thread scarcity като архитектурен натиск, но не премахва scarcity на DB connections, CPU и downstream ресурси.
10. Избирай concurrency модел според структурата на задачата, а не защото даден API е по-нов.

## 14. Упражнения

1. В [`DemoBankController.java`](./bank-services/src/main/java/bg/hristomanov/education/loom/services/DemoBankController.java) направи `provider-b` да хвърля exception и провери дали `provider-a` все още може да спечели.
2. Направи и двата credit score provider-а да fail-нат и проследи exception-а до HTTP response-а.
3. Добави timeout към scope configuration в [`StructuredCustomerInfoLoader.java`](./bank-api/src/main/java/bg/hristomanov/education/loom/bankapi/service/StructuredCustomerInfoLoader.java).
4. Добави тест, който доказва, че трите customer-info операции стартират concurrently.
5. Добави variant, който използва само sequential blocking calls, и измери latency разликата.
6. Сложи semaphore около downstream операция и наблюдавай как virtual threads чакат, без това да премахва business лимита.

## Оригинални източници

### Материалът, от който е създадена лабораторията

- YouTube видео — **Beyond Virtual Threads: Structured Concurrency**: https://www.youtube.com/watch?v=2L7zLdHeyY0
- Оригинален demo repository — **balkrishnarawool/SpringBootLoom**: https://github.com/balkrishnarawool/SpringBootLoom
- Structured Concurrency вариант: https://github.com/balkrishnarawool/SpringBootLoom/tree/with-structured-concurrency
- CompletableFuture вариант: https://github.com/balkrishnarawool/SpringBootLoom/tree/with-completable-future
- Scoped Values вариант: https://github.com/balkrishnarawool/SpringBootLoom/tree/with-scoped-values
- Custom Joiners примери: https://github.com/balkrishnarawool/SpringBootLoom/tree/custom-joiners

### Официални Java източници

- JEP 444 — Virtual Threads: https://openjdk.org/jeps/444
- JEP 505 — Structured Concurrency (Fifth Preview, JDK 25): https://openjdk.org/jeps/505
- JEP 506 — Scoped Values: https://openjdk.org/jeps/506
- Java 25 `StructuredTaskScope` API: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/concurrent/StructuredTaskScope.html
- Java 25 `StructuredTaskScope.Joiner` API: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/concurrent/StructuredTaskScope.Joiner.html
- Java 25 Structured Concurrency guide: https://docs.oracle.com/en/java/javase/25/core/structured-concurrency.html
- Java 25 `ScopedValue` API: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/ScopedValue.html
- Java 25 Scoped Values guide: https://docs.oracle.com/en/java/javase/25/core/scoped-values.html

### Официални Spring източници

- Spring Boot project page / current stable release: https://spring.io/projects/spring-boot
- Spring Boot reference documentation: https://docs.spring.io/spring-boot/reference/
- Spring Boot `Threading` API и `spring.threads.virtual.enabled`: https://docs.spring.io/spring-boot/api/java/org/springframework/boot/thread/Threading.html

---

Тази лаборатория е учебна интерпретация. Оригиналните идеи и demo scenario са кредитирани по-горе; кодът тук е преработен и коментиран специално за `education` repository-то.
