# Project Loom — Virtual Threads, Structured Concurrency и Scoped Values

Практическа Java/Spring лаборатория по идеите от видеото **Beyond Virtual Threads: Structured Concurrency** и оригиналния `SpringBootLoom` demo project.

Целта тук не е просто да имаме код, който използва нови Java API-та. Идеята е след време да можеш да отвориш този README и да си припомниш:

- **какъв проблем решава Project Loom**;
- какво реално е virtual thread и как се различава от platform/OS thread;
- защо virtual threads подобряват throughput, но не правят отделната операция „по-бърза“;
- защо Structured Concurrency е нещо повече от „още един начин да пуснем няколко задачи паралелно“;
- защо `ScopedValue` съществува, при положение че отдавна имаме `ThreadLocal`;
- как трите идеи работят заедно в реалистичен Spring Boot request flow;
- кога Loom е добър избор и кога няма да реши проблема ни.

> Основният проект е на **Java 25 LTS**. Structured Concurrency е preview API в Java 25 и затова build-ът използва `--enable-preview`.

---

## 1. Голямата картина: какъв проблем решава Project Loom

Класическият Java/Spring backend често е написан в много естествен synchronous стил:

```java
var customer = customerClient.getCustomer(id);
var accounts = accountClient.getAccounts(id);
var loans = loanClient.getLoans(id);
return calculateOffer(customer, accounts, loans);
```

Този стил е лесен за четене, дебъгване и reasoning: методът започва отгоре, изпълнява стъпките една по една и приключва отдолу.

Проблемът исторически не е бил самият blocking стил, а **цената на thread-а**, върху който blocking кодът чака.

При традиционния модел:

```text
HTTP request
    ↓
Java platform thread
    ↓
OS thread
    ↓
чака HTTP / DB / file I/O
```

ако имаме много едновременни заявки, които основно чакат I/O, започваме да държим много скъпи OS threads почти без да вършат CPU работа.

Thread pools намаляват цената от постоянното създаване на threads, но **не премахват ограничението в броя им**. Ако pool-ът има 200 threads, 201-вата задача чака свободен thread, дори машината да има свободен CPU и единствената причина първите 200 задачи да стоят е бавен downstream HTTP call.

Project Loom атакува точно този проблем чрез няколко свързани идеи:

```text
Project Loom
├── Virtual Threads
│   └── евтин thread-per-task модел
│
├── Structured Concurrency
│   └── parent/child lifecycle за concurrent задачи
│
└── Scoped Values
    └── безопасно предаване на immutable context надолу по call tree-а
```

Трите неща не са една и съща функционалност:

- **Virtual Threads** решават проблема с цената и мащаба на threads;
- **Structured Concurrency** решава проблема с организацията и lifecycle-а на concurrent работата;
- **Scoped Values** решават проблема с контекст, който трябва да се вижда надолу по call stack-а и в structured child threads.

---

## 2. Термини, които трябва да са напълно ясни

| Термин | Какво означава |
| --- | --- |
| **OS thread** | Thread, управляван от операционната система. Сравнително скъп ресурс. |
| **Platform thread** | Класически Java `Thread`, който практически е 1:1 вързан към OS thread докато живее. |
| **Virtual thread** | Java `Thread`, управляван основно от JVM. Не е постоянно вързан към един OS thread. |
| **Carrier thread** | Platform thread, върху който в даден момент JVM изпълнява virtual thread. |
| **Mount** | Virtual thread започва/продължава да се изпълнява върху carrier thread. |
| **Unmount** | Virtual thread временно освобождава carrier-а, обикновено докато чака подходящ blocking operation. |
| **Task** | Логическа единица работа. При virtual threads естественият модел е един virtual thread за една task. |

Virtual thread **не е callback abstraction**. Той е истински `java.lang.Thread`, затова работят познатите concepts като stack trace, interruption и `Thread.currentThread()`.

---

## 3. Защо Virtual Threads могат да мащабират много повече

Platform thread традиционно държи OS thread за целия си живот. Ако извикаме бавен HTTP endpoint и чакаме 900 ms, OS thread-ът е ангажиран през това време.

Virtual thread работи различно:

```text
Virtual thread #A
     │ mount
     ▼
Carrier platform thread #1
     │ прави CPU работа
     │ blocking HTTP read
     ▼
Virtual thread #A се unmount-ва
Carrier #1 е свободен
     │
     ├── изпълнява Virtual thread #B
     ├── после Virtual thread #C
     └── ...

когато I/O на #A е готово:
Virtual thread #A се mount-ва отново
(не е задължително върху същия carrier)
```

Това е M:N модел: много virtual threads могат да споделят по-малък брой platform/OS threads.

### Какво печелим

Печелим най-вече когато:

1. имаме **много concurrent задачи**;
2. задачите прекарват значителна част от времето си в **чакане**, а не в CPU calculations.

Типични примери са JDBC, HTTP calls, file/network I/O и request-per-thread server workloads.

### Какво НЕ печелим

Virtual threads не ускоряват CPU-bound код. Ако 8-core машина има 10 000 задачи, които всяка правят тежък calculation, 10 000 virtual threads няма да създадат повече CPU cores.

Разграничението е:

```text
latency = колко време отнема една операция
throughput = колко операции можем да обслужим за единица време
```

Virtual threads са основно инструмент за **scalability и throughput**, а не за намаляване на CPU execution time.

### Интуиция с Little's Law

Грубо:

```text
concurrency ≈ throughput × latency
```

Ако искаме 2 000 requests/second и средният request чака 0.5 s downstream I/O:

```text
2 000 × 0.5 = ~1 000 едновременно активни requests
```

При thread-per-request модел това означава около 1 000 едновременно живи threads. Virtual threads правят този модел много по-практичен.

---

## 4. Защо НЕ трябва да pool-ваме Virtual Threads

При platform threads pool-ът съществува, защото thread-ът е скъп:

```text
1000 tasks
   ↓
pool от 50 platform threads
   ↓
50 работят, останалите чакат
```

Virtual thread е евтин и замисълът е:

```text
1000 tasks
   ↓
1000 virtual threads
```

Затова `Executors.newVirtualThreadPerTaskExecutor()` създава нов virtual thread **за всяка task**.

Ако имаме реален downstream лимит, например външна услуга допуска максимум 20 concurrent calls, не правим „pool от 20 virtual threads“. Ограничаваме истинския scarce resource чрез `Semaphore`, connection pool или rate limiter.

```text
100 000 virtual threads
        ↓
HikariCP maxPoolSize = 20
        ↓
максимум 20 активни DB connections
```

Virtual threads махат **thread scarcity**, не махат scarcity на DB connections, HTTP pools, quotas, CPU, memory, locks и downstream capacity.

---

## 5. Virtual Threads в тази Spring Boot лаборатория

**Код:** [`application.properties`](./bank-api/src/main/resources/application.properties), [`LoanApplicationController.java`](./bank-api/src/main/java/bg/hristomanov/education/loom/bankapi/controller/LoanApplicationController.java), [`VirtualThreadTest.java`](./bank-api/src/test/java/bg/hristomanov/education/loom/bankapi/VirtualThreadTest.java).

Настройката е:

```properties
spring.threads.virtual.enabled=true
spring.main.keep-alive=true
```

### `spring.threads.virtual.enabled=true`

Казваме на Spring Boot да използва virtual threads там, където auto-configuration-ът поддържа този threading mode. Така request processing-ът може да изпълнява controller логиката върху virtual thread.

### Защо имаме `spring.main.keep-alive=true`

Virtual threads са daemon threads. Daemon threads сами по себе си не държат JVM процеса жив. Keep-alive настройката гарантира, че application process-ът остава активен и когато execution-ът е върху virtual daemon threads.

### Как проверяваме, а не просто предполагаме

`GET /api/thread-info` връща информация за `Thread.currentThread()` и `isVirtual()`. Това е учебен endpoint; в production обикновено не бихме expose-вали такъв endpoint.

**IntelliJ HTTP Client:** [`http/loom-demo.http`](./http/loom-demo.http) → request `threadInfo`. Отвори файла и натисни ▶ в gutter-а до заявката. Файлът съдържа и автоматична проверка, че `virtual == true`.

За най-бърза проверка IntelliJ разпознава и командата директно в Markdown и показва Run икона до нея:

```bash
curl http://localhost:8080/api/thread-info
```

<details>
<summary>Примерен резултат, ако само четеш материала</summary>

```json
{
  "thread": "VirtualThread[#42,tomcat-handler-0]/runnable@ForkJoinPool-1-worker-1",
  "virtual": true
}
```

Полето `thread` е динамично — номерът, името и carrier thread-ът могат да са различни при всяко изпълнение. Важната част за тази проверка е `"virtual": true`.

</details>

---

## 6. Нашият business scenario

Имаме loan application:

```text
POST /api/loan-applications
        │
        ▼
load Customer
        │
        ├───────────────┬────────────────────┐
        ▼               ▼                    ▼
   load Accounts    load Loans       load Credit Score
                                          │
                                  ┌───────┴────────┐
                                  ▼                ▼
                              provider-a       provider-b
                                  │                │
                                  └── first successful
        │               │                    │
        └───────────────┴────────────────────┘
                        │
                        ▼
                 calculate Offer
```

**Orchestration:** [`LoanApplicationController.java`](./bank-api/src/main/java/bg/hristomanov/education/loom/bankapi/controller/LoanApplicationController.java) → [`LoanApplicationService.java`](./bank-api/src/main/java/bg/hristomanov/education/loom/bankapi/service/LoanApplicationService.java) → [`StructuredCustomerInfoLoader.java`](./bank-api/src/main/java/bg/hristomanov/education/loom/bankapi/service/StructuredCustomerInfoLoader.java).

Dummy latency-ите са нарочно различни:

- accounts: ~700 ms;
- loans: ~900 ms;
- credit provider A: ~1200 ms;
- credit provider B: ~450 ms.

Ако изпълним accounts + loans + успешния score **последователно**:

```text
700 + 900 + 450 = ~2050 ms
```

Ако независимите операции стартират едновременно:

```text
max(700, 900, 450) = ~900 ms
```

без малкия overhead и първоначалния `load Customer`.

Това е причината concurrency да има смисъл тук: операциите са **независими I/O waits**. Не трябва механично да fork-ваме всичко; ако B зависи от A, те не са паралелни само защото можем да създадем още thread.

---

## 7. Structured Concurrency — какъв проблем решава

Virtual threads ни дават евтини threads, но сами по себе си не казват **как са свързани задачите**.

Можем да направим:

```java
executor.submit(taskA);
executor.submit(taskB);
executor.submit(taskC);
```

но после трябва сами да отговорим:

- Кой е owner на тези tasks?
- Кога вече не са нужни?
- Ако една fail-не, трябва ли другите да продължат?
- Ако HTTP request-ът бъде прекъснат, какво става с child tasks?
- Къде точно се чака приключването им?

Structured Concurrency третира concurrent tasks подобно на method calls: child операцията принадлежи към lexical scope-а на parent операцията.

```text
method call
└── structured scope
    ├── child task A
    ├── child task B
    └── child task C

method-ът не приключва нормално,
докато structured child работата му не е приключила/cancel-ната
```

Това прави lifecycle-а видим директно в структурата на кода.

---

## 8. `StructuredTaskScope` стъпка по стъпка

**Основен файл:** [`StructuredCustomerInfoLoader.java`](./bank-api/src/main/java/bg/hristomanov/education/loom/bankapi/service/StructuredCustomerInfoLoader.java)

```java
try (var scope = StructuredTaskScope.open()) {
    var accountsTask = scope.fork(() -> accountClient.getAccounts(customer.id()));
    var loansTask = scope.fork(() -> loanClient.getLoans(customer.id()));
    var creditScoreTask = scope.fork(() -> creditScoreClient.getFirstSuccessfulScore(customer.id()));

    scope.join();

    return new CustomerInfo(
            accountsTask.get(),
            loansTask.get(),
            creditScoreTask.get());
}
```

### 8.1 `StructuredTaskScope.open()`

Отваряме scope, който става owner на child tasks. В Java 25 default policy е fail-fast: ако required subtask fail-не, останалата работа се cancel-ва и `join()` завършва с failure.

### 8.2 `scope.fork(...)`

`fork()` означава:

> „Тази работа е child на текущата structured операция и може да върви concurrently с другите children.“

Получаваме `Subtask<T>` handle. Трите fork-а са правилни, защото всички вече имат `customer.id()`, не зависят един от друг и са предимно blocking HTTP I/O.

### 8.3 `scope.join()`

`join()` е мястото, където parent thread казва:

> „Стартирах child работата. Сега ми трябват резултатите ѝ, преди да продължа.“

Имаме една ясна structural join point вместо scattered waits.

### 8.4 `task.get()` след `join()`

След успешен `join()` взимаме стойностите от subtasks и ги събираме обратно в business object `CustomerInfo`.

### 8.5 Защо е `try-with-resources`

Scope-ът има lexical lifetime. Кодът визуално показва началото и края на concurrent операцията. Това е едно от основните значения на **structured**.

### 8.6 Какво става при interruption

`join()` може да хвърли `InterruptedException`. Правим:

```java
Thread.currentThread().interrupt();
```

преди да хвърлим application exception, защото interrupt е cooperative cancellation signal. Ако го „изядем“, код по-нагоре може да загуби информацията, че операцията е била поискана за прекратяване.

---

## 9. Joiner — policy за това какво означава „готови сме“

При credit score имаме два provider-а и business requirement-ът е:

> Използвай първия **успешен** резултат.

Това не е „първият приключил“. Ако provider A fail-не след 100 ms, а provider B върне валиден score след 450 ms, искаме B.

**Код:** [`BankClients.java`](./bank-api/src/main/java/bg/hristomanov/education/loom/bankapi/client/BankClients.java), `CreditScoreClient#getFirstSuccessfulScore(...)`.

```java
try (var scope = StructuredTaskScope.open(
        StructuredTaskScope.Joiner.<CreditScore>anySuccessfulResultOrThrow())) {

    scope.fork(() -> getScore(customerId, "provider-a"));
    scope.fork(() -> getScore(customerId, "provider-b"));

    return scope.join();
}
```

### Timeline в demo-то

```text
0 ms       provider-a start
0 ms       provider-b start
450 ms     provider-b SUCCESS → CreditScore(735)
           joiner има достатъчен резултат
           останалата sibling работа вече не е нужна
```

Ако единият provider fail-не, другият още може да спечели. Ако всички fail-нат, няма успешен резултат и `join()` завършва с failure.

Joiner държи policy-то на едно място: кои tasks стартираме, кога имаме достатъчно резултат и какво става с останалата работа.

---

## 10. Structured Concurrency не е просто „по-кратък CompletableFuture“

Сравни:

- [`StructuredCustomerInfoLoader.java`](./bank-api/src/main/java/bg/hristomanov/education/loom/bankapi/service/StructuredCustomerInfoLoader.java)
- [`CompletableFutureCustomerInfoLoader.java`](./bank-api/src/main/java/bg/hristomanov/education/loom/bankapi/service/CompletableFutureCustomerInfoLoader.java)

`CompletableFuture` е валиден API; разликата е в programming model-а.

| Тема | CompletableFuture | Structured Concurrency |
| --- | --- | --- |
| Основна abstraction | бъдещ резултат / pipeline | task tree / parent-child lifecycle |
| Lexical scope | не е задължително | основна идея |
| Cancellation | моделира се отделно | част от scope policy/lifecycle |
| Context propagation | често допълнителна грижа | ScopedValue inheritance към structured children |
| Blocking style | често async chain | естествен thread-per-task blocking style |
| Подходящо за | async pipelines, composition | request-oriented task trees |

Не заменяме автоматично всяко `CompletableFuture`; избираме abstraction според структурата на проблема.

---

## 11. Scoped Values — защо изобщо са ни нужни

Искаме `requestId` да се вижда в controller → service → loader → clients.

Можем да го подаваме като parameter навсякъде, което е explicit, но за cross-cutting immutable context може да замърси method signatures.

Исторически често се използва `ThreadLocal`, но за този use case има недостатъци:

- lifetime-ът не е автоматично вързан към lexical operation;
- трябва внимателно `remove()` cleanup;
- далечен callee може да mutate-не стойността;
- грешен cleanup при pools може да leak-не context;
- inheritance към child threads е по-тежък модел.

`ScopedValue` е за **one-way, bounded context propagation**.

**Код:** [`RequestContext.java`](./bank-api/src/main/java/bg/hristomanov/education/loom/bankapi/context/RequestContext.java)

```java
private static final ScopedValue<RequestMetadata> METADATA = ScopedValue.newInstance();
```

Binding:

```java
ScopedValue.where(METADATA, metadata)
        .call(operation);
```

Мисли за това като:

> „За динамичния execution scope на тази операция тази стойност е достъпна надолу по call tree-а.“

```text
RequestContext.call(metadata, operation)
│
├── service вижда metadata
├── loader вижда metadata
├── structured child A вижда metadata
├── structured child B вижда metadata
│
└── след края на call(...) binding-ът вече не съществува
```

---

## 12. Защо bind-ваме RequestContext в Controller-а

**Код:** [`LoanApplicationController.java`](./bank-api/src/main/java/bg/hristomanov/education/loom/bankapi/controller/LoanApplicationController.java)

Controller-ът е request boundary:

```java
var metadata = new RequestMetadata(UUID.randomUUID());
return RequestContext.call(metadata, () -> loanApplicationService.apply(request));
```

Така целият business flow е вътре в един context scope.

Не bind-ваме чак в client-а, защото sibling operations няма да споделят общ request context. Не пазим metadata в mutable singleton field, защото concurrent requests биха си презаписвали данните.

---

## 13. ScopedValue + StructuredTaskScope

Тук двете Loom идеи се комбинират естествено:

```text
request scope (requestId=X)
│
├── accounts virtual thread ───── sees X
├── loans virtual thread ──────── sees X
└── credit score scope ────────── sees X
    ├── provider-a ────────────── sees X
    └── provider-b ────────────── sees X
```

Не подаваме `requestId` като parameter и не правим ръчно copy във всеки structured child.

[`RequestContextTest.java`](./bank-api/src/test/java/bg/hristomanov/education/loom/bankapi/context/RequestContextTest.java) доказва inheritance поведението.

---

## 14. Защо CompletableFuture примерът re-bind-ва context-а

`CompletableFutureCustomerInfoLoader` използва `Executors.newVirtualThreadPerTaskExecutor()`.

Тези threads са virtual, но **не са structured children** на текущ scope. Затова comparative примерът capture-ва metadata и го bind-ва отново за всяка async task.

Това е важна разлика: **virtual thread** и **structured child thread** не са синоними.

---

## 15. End-to-end walkthrough на една заявка

1. Spring приема `POST /api/loan-applications`.
2. Controller-ът генерира `requestId` и отваря `ScopedValue` binding.
3. `LoanApplicationService` зарежда customer — това е prerequisite за останалите calls.
4. `StructuredCustomerInfoLoader` fork-ва accounts, loans и credit score.
5. `CreditScoreClient` отваря nested structured scope с два provider-а.
6. First-successful Joiner избира първия валиден score.
7. Outer scope чака required customer-info частите.
8. Връщаме се към обикновен sequential business code и изчисляваме offer.
9. `RequestContext.call(...)` приключва и ScopedValue binding-ът вече не е active.

Получаваме execution tree, който следва business call tree-а:

```text
HTTP request
└── LoanApplicationService
    └── StructuredCustomerInfoLoader scope
        ├── accounts
        ├── loans
        └── CreditScoreClient scope
            ├── provider-a
            └── provider-b
```

---

## 16. Blocking I/O тук е умишлено

Използваме Spring `RestClient`, който е synchronous/blocking HTTP client.

Virtual threads ни позволяват да запазим прост blocking code:

```java
var result = restClient.get()
        .uri(...)
        .retrieve()
        .body(...);
```

без всеки чакащ request да изисква отделен тежък OS thread за целия wait.

Това не прави reactive programming безсмислено. Reactive streams дават други свойства като backpressure и stream composition. Loom просто премахва една историческа причина да приемаме callback/reactive complexity за обикновен request/response I/O.

---

## 17. Cancellation и interruption

Cancellation е част от correctness-а. Ако request вече не е нужен, child operations също трябва да могат да спрат.

Java използва interruption като cooperative signal. Затова при `InterruptedException` възстановяваме interrupt status-а:

```java
Thread.currentThread().interrupt();
```

И `DemoBankController#simulateLatency()` използва `Thread.sleep(...)` нарочно: sleep е interruptible и прави cancellation поведението наблюдаемо.

---

## 18. `synchronized` и старият pinning проблем

По-стари Loom материали предупреждават, че blocking вътре в `synchronized` може да pin-не virtual thread към carrier-а.

**JEP 491**, доставен в Java 24, променя monitor implementation-а така, че monitor-based `synchronized` scenarios вече не причиняват стария тип pinning. На Java 25 не трябва механично да заменяме `synchronized` с `ReentrantLock` само заради стария Loom съвет.

Native/foreign code и специфични blocking случаи все още заслужават внимание; важното е да четем guidance-а спрямо конкретната JDK версия.

---

## 19. Production ограничения, които Loom не премахва

- **DB pool:** 20 connections пак означават около 20 едновременни DB операции.
- **Downstream capacity:** service с лимит 100 req/s не става service за 10 000 req/s.
- **CPU:** encryption, compression и тежки calculations остават CPU-bound.
- **Memory:** virtual threads са леки, но не са безплатни.
- **Lock contention:** повече threads не премахват shared critical section.
- **Лош SQL:** virtual thread не поправя full table scan.

---

## 20. Кога бих използвал тази комбинация

Добър кандидат:

- Spring MVC application;
- много synchronous HTTP/JDBC calls;
- request fan-out към независими downstream services;
- искаме simple blocking code;
- имаме request metadata/correlation context;
- concurrency tree-ът има ясни parent/child граници.

По-малко подходящо:

- CPU-heavy batch processing;
- естествено event/stream-oriented pipeline;
- много малко concurrency;
- bottleneck е downstream resource, а не threads.

---

## 21. README → код

| Концепция | Production-like код / конфигурация | Доказателство / тест |
| --- | --- | --- |
| Spring Boot Virtual Threads | [`application.properties`](./bank-api/src/main/resources/application.properties), [`LoanApplicationController.java`](./bank-api/src/main/java/bg/hristomanov/education/loom/bankapi/controller/LoanApplicationController.java) | [`VirtualThreadTest.java`](./bank-api/src/test/java/bg/hristomanov/education/loom/bankapi/VirtualThreadTest.java), [`threadInfo` HTTP request](./http/loom-demo.http) |
| End-to-end orchestration | [`LoanApplicationService.java`](./bank-api/src/main/java/bg/hristomanov/education/loom/bankapi/service/LoanApplicationService.java) | [`loanApplication` HTTP request](./http/loom-demo.http) |
| `fork → join` | [`StructuredCustomerInfoLoader.java`](./bank-api/src/main/java/bg/hristomanov/education/loom/bankapi/service/StructuredCustomerInfoLoader.java) | logs + tests |
| First-successful Joiner | [`BankClients.java`](./bank-api/src/main/java/bg/hristomanov/education/loom/bankapi/client/BankClients.java) | [`DemoBankController.java`](./bank-services/src/main/java/bg/hristomanov/education/loom/services/DemoBankController.java) |
| ScopedValue request context | [`RequestContext.java`](./bank-api/src/main/java/bg/hristomanov/education/loom/bankapi/context/RequestContext.java), [`LoanApplicationController.java`](./bank-api/src/main/java/bg/hristomanov/education/loom/bankapi/controller/LoanApplicationController.java) | [`RequestContextTest.java`](./bank-api/src/test/java/bg/hristomanov/education/loom/bankapi/context/RequestContextTest.java) |
| CompletableFuture comparison | [`CompletableFutureCustomerInfoLoader.java`](./bank-api/src/main/java/bg/hristomanov/education/loom/bankapi/service/CompletableFutureCustomerInfoLoader.java) | сравни със structured loader-а |
| Dummy latency/cancellation | [`DemoBankController.java`](./bank-services/src/main/java/bg/hristomanov/education/loom/services/DemoBankController.java) | локално изпълнение |
| Java 26 API delta | [`JAVA-26.md`](./JAVA-26.md) | — |

---

## 22. Как да стартираме

### Prerequisites

- JDK 25
- Maven 3.9+

```bash
mvn clean verify
```

Стартирай dummy services:

```bash
cd java/concurrency/project-loom
mvn -pl bank-services spring-boot:run
```

В друг terminal:

```bash
mvn -pl bank-api spring-boot:run
```

### Най-удобно: IntelliJ HTTP Client

Готовите заявки са в [`http/loom-demo.http`](./http/loom-demo.http). В IntelliJ IDEA отвори файла и използвай ▶ до:

- `threadInfo` — проверява `/api/thread-info` и автоматично assert-ва `virtual == true`;
- `loanApplication` — пуска целия loan flow и проверява успешния demo response.

Така не е нужен Postman и не трябва ръчно да сглобяваш headers/body при всяко четене на лабораторията.

### Проверка директно от README

IntelliJ може да показва Run action в gutter-а и за runnable командите в Markdown. За thread endpoint-а:

```bash
curl http://localhost:8080/api/thread-info
```

Loan application:

```bash
curl -X POST http://localhost:8080/api/loan-applications \
  -H 'Content-Type: application/json' \
  -d '{
    "customerId": "2b8d8f54-e104-4d21-97de-6ef9a78db392",
    "amount": 25000,
    "purpose": "home renovation"
  }'
```

<details>
<summary>Примерен response за loan application</summary>

```json
{
  "customerId": "2b8d8f54-e104-4d21-97de-6ef9a78db392",
  "approved": true,
  "amount": 25000,
  "annualInterestRate": 4.20,
  "reason": "Demo offer calculated from the first successful credit score"
}
```

</details>

Гледай логовете за различни virtual threads, един и същ `requestId` и близки start моменти на independent calls.

---

## 23. Какво доказват тестовете

`VirtualThreadTest` доказва, че създаденият thread действително е virtual.

`RequestContextTest` доказва две по-важни семантики:

1. ScopedValue binding-ът има ограничен lifetime;
2. structured child task наследява binding-а.

Това доказва самата concurrency семантика, а не просто че Spring context-ът стартира.

---

## 24. Какво да запомня

1. Virtual thread е Java `Thread`, но не държи OS thread за целия си lifetime.
2. Virtual threads дават **scale**, не по-бърз CPU execution.
3. При I/O-heavy backend-и thread-per-request отново става практичен при висока concurrency.
4. Virtual threads не се pool-ват; scarce resources се ограничават директно.
5. Structured Concurrency моделира concurrent work като parent/child task tree.
6. `fork()` стартира независим child work; `join()` е ясната точка на събиране.
7. Joiner описва policy — например „първият успешен резултат“.
8. Cancellation/interruption са част от correctness-а.
9. ScopedValue е за bounded, one-way immutable context propagation.
10. Virtual thread не означава автоматично structured concurrency.
11. Loom не премахва DB pool, CPU, rate-limit или downstream bottlenecks.
12. JDK версията е важна — preview API-тата се развиват.

---

## 25. Упражнения

1. Направи provider B да fail-не и виж дали A ще даде резултат.
2. Направи и двата provider-а да fail-нат и проследи exception flow-а.
3. Добави тест за sequential срещу concurrent latency.
4. Добави timeout policy към structured scope-а.
5. Добави четвърта независима downstream операция.
6. Ограничѝ downstream услуга със `Semaphore(2)` и изпрати много requests.
7. Замени structured loader-а с `CompletableFutureCustomerInfoLoader` и сравни context propagation-а.
8. Направи един child call CPU-heavy и измери защо virtual threads не му помагат.
9. Използвай JFR/thread dump, за да разгледаш virtual threads и task relationships.

---

## 26. Java 25 срещу Java 26

Основният runnable код е Java 25. Upstream примерът вече използва Java 26 и Structured Concurrency API-то се е променило:

```java
// Java 25
Joiner.anySuccessfulResultOrThrow()
```

срещу:

```java
// Java 26
Joiner.anySuccessfulOrThrow()
```

Подробно: [`JAVA-26.md`](./JAVA-26.md).

---

## Оригинални източници

### Материалът, от който е създадена лабораторията

- YouTube — **Beyond Virtual Threads: Structured Concurrency**: https://www.youtube.com/watch?v=2L7zLdHeyY0
- Оригинален demo repository — `balkrishnarawool/SpringBootLoom`: https://github.com/balkrishnarawool/SpringBootLoom
- Structured Concurrency branch: https://github.com/balkrishnarawool/SpringBootLoom/tree/with-structured-concurrency
- CompletableFuture branch: https://github.com/balkrishnarawool/SpringBootLoom/tree/with-completable-future
- Scoped Values branch: https://github.com/balkrishnarawool/SpringBootLoom/tree/with-scoped-values
- Custom Joiners branch: https://github.com/balkrishnarawool/SpringBootLoom/tree/custom-joiners

### Официални Java източници

- JEP 444 — Virtual Threads: https://openjdk.org/jeps/444
- JEP 491 — Synchronize Virtual Threads without Pinning: https://openjdk.org/jeps/491
- JEP 505 — Structured Concurrency (Fifth Preview, Java 25): https://openjdk.org/jeps/505
- JEP 506 — Scoped Values: https://openjdk.org/jeps/506
- Java 25 Structured Concurrency guide: https://docs.oracle.com/en/java/javase/25/core/structured-concurrency.html
- Java 25 `StructuredTaskScope` API: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/concurrent/StructuredTaskScope.html
- Java 25 `StructuredTaskScope.Joiner` API: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/concurrent/StructuredTaskScope.Joiner.html
- Java 25 Scoped Values guide: https://docs.oracle.com/en/java/javase/25/core/scoped-values.html
- Java 25 `ScopedValue` API: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/ScopedValue.html

### Официални Spring източници

- Spring Boot: https://spring.io/projects/spring-boot
- Spring Boot reference: https://docs.spring.io/spring-boot/reference/
- Spring Boot Threading API: https://docs.spring.io/spring-boot/api/java/org/springframework/boot/thread/Threading.html

---

Business scenario-то е опростено нарочно, за да държи фокуса върху concurrency модела, а не върху banking domain логиката.
