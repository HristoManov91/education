# Ports & Adapters / Hexagonal Architecture

Този модул събира на едно място много от concepts, които вече използвахме отделно:

- Dependency Injection;
- Repository;
- Adapter;
- application services;
- transaction boundaries;
- REST;
- JPA;
- test doubles;
- composition root.

Целта не е да превърнем всеки Spring project в архитектурна матрьошка.

Целта е да разберем **dependency direction-а** и кога isolation-ът от infrastructure носи реална стойност.

---

# ВХОД В ТЕМАТА

## Реалният казус

Имаме order use case:

```text
REST request
→ place order
→ load product prices
→ persist order
→ return result
```

Naive Spring implementation лесно става:

```text
@RestController
→ @Service
→ JpaRepository
→ JPA entities
```

а service-ът започва да знае:

- Spring annotations;
- JPA entities;
- repository technology;
- HTTP DTOs;
- transaction annotations;
- framework lifecycle.

Това може да е напълно достатъчно за малък CRUD system.

Проблемът идва, когато business core-ът трябва да бъде:

- long-lived;
- testable без framework;
- reuse-нат от REST и batch;
- независим от конкретна persistence technology;
- по-стабилен от външните integration choices.

---

# Оригиналният intent

Alistair Cockburn описва Ports & Adapters през 2005 г. с много ясен intent:

> application-ът да може да работи без UI или database и да бъде driven от automated tests, users, batch scripts или други programs.

Ключовата идея е:

```text
OUTSIDE
   ↓ adapters
PORTS
   ↓
APPLICATION CORE
```

и обратно:

```text
APPLICATION CORE
   ↓ ports
ADAPTERS
   ↓
OUTSIDE SYSTEMS
```

Core-ът не трябва да знае какво има отвъд port-а.

---

# Hexagonal не означава шест ports

Шестоъгълникът е визуален инструмент.

Броят 6 няма architectural значение.

Cockburn изрично обяснява, че целта на hexagon drawing-а е:

- да подчертае inside vs outside;
- да избегне мисленето само в едномерни top/bottom layers;
- да има място за произволен брой ports/adapters.

Може да имаме 2, 3, 4 или повече ports.

Не броим страните на рисунката.

---

# Port се определя по purpose, не по technology

Лошо именуване:

```text
RestPort
JpaPort
KafkaPort
```

Това описва technology.

По-смислено:

```text
PlaceOrderUseCase
GetOrderUseCase
OrderRepositoryPort
ProductCatalogPort
```

Port-ът описва conversation, от която application-ът има нужда.

Adapter-ът описва technology, която се включва към този port.

---

# Driving и Driven sides

Оригиналният материал прави полезно разграничение.

## Driving / Primary adapters

Те стартират use case.

Примери:

- REST controller;
- CLI;
- batch job;
- automated system test;
- message consumer.

В lab-а:

```text
OrderRestController
→ PlaceOrderUseCase
→ GetOrderUseCase
```

## Driven / Secondary adapters

Application-ът ги извиква.

Примери:

- database;
- external HTTP API;
- message broker;
- email provider;
- filesystem.

В lab-а:

```text
OrderApplicationService
→ OrderRepositoryPort
→ ProductCatalogPort
```

а JPA implement-ва двата output ports.

---

# Структура на module-а

```text
hexagonal-architecture/
├── core/
│   ├── domain/
│   ├── application/
│   ├── port/in/
│   └── port/out/
│
├── adapter-in-rest/
│   └── REST DTO + Controller
│
├── adapter-out-jpa/
│   └── JPA entities + Spring Data + adapters
│
└── bootstrap/
    ├── Spring Boot application
    ├── @Bean wiring
    ├── transaction decorators
    └── demo data / HTTP scenarios
```

---

# Dependency graph

Това е най-важната част:

```text
adapter-in-rest ─────┐
                    ↓
                   core
                    ↑
adapter-out-jpa ─────┘

bootstrap
  ├→ core
  ├→ adapter-in-rest
  └→ adapter-out-jpa
```

Core НЕ зависи от adapters.

И това не е само convention в README.

Maven module graph-ът го enforce-ва.

`core/pom.xml` няма:

- Spring;
- Spring Boot;
- Spring Data;
- Jakarta Persistence;
- Web MVC.

Само Java + test dependencies.

---

# Framework-free core

Основният use case е:

- [`OrderApplicationService.java`](./core/src/main/java/bg/hristomanov/education/hexagonal/application/OrderApplicationService.java)

Той е plain Java:

```java
public final class OrderApplicationService
        implements PlaceOrderUseCase, GetOrderUseCase {

    private final OrderRepositoryPort orderRepository;
    private final ProductCatalogPort productCatalog;
    ...
}
```

Няма:

```text
@Service
@Transactional
JpaRepository
EntityManager
@Entity
@RestController
```

Core unit test-ът го стартира директно с `new`.

Няма Spring context.

Няма H2.

Няма database.

---

# Input ports

Input port-овете са application API-то:

- [`PlaceOrderUseCase.java`](./core/src/main/java/bg/hristomanov/education/hexagonal/port/in/PlaceOrderUseCase.java)
- [`GetOrderUseCase.java`](./core/src/main/java/bg/hristomanov/education/hexagonal/port/in/GetOrderUseCase.java)

REST adapter-ът не вика implementation class-а директно.

Той зависи от port contract-а.

Това позволява:

```text
REST Controller ─┐
Batch Adapter    ├→ PlaceOrderUseCase
Test Adapter     ┘
```

без application logic да знае кой е caller-ът.

---

# Output ports

Core-ът дефинира от какво има нужда:

- [`OrderRepositoryPort.java`](./core/src/main/java/bg/hristomanov/education/hexagonal/port/out/OrderRepositoryPort.java)
- [`ProductCatalogPort.java`](./core/src/main/java/bg/hristomanov/education/hexagonal/port/out/ProductCatalogPort.java)

Това е Dependency Inversion:

```text
core
→ depends on abstraction

JPA adapter
→ implements abstraction
```

Не:

```text
core
→ depends on JpaOrderRepositoryAdapter
```

---

# Кой притежава port interface-а?

Обикновено application/core side.

Защо?

Port-ът описва:

> какъв conversation е нужен на application-а.

Например:

```java
interface ProductCatalogPort {
    Optional<Product> findBySku(String sku);
}
```

Core не иска:

```text
JPA
SQL
REST
Mongo
```

Той иска:

> намери ми product по SKU.

Adapter-ът се наглася към тази нужда.

---

# REST е adapter, не application core

REST module:

- [`OrderRestController.java`](./adapter-in-rest/src/main/java/bg/hristomanov/education/hexagonal/adapter/in/rest/OrderRestController.java)

Ролята му е translation:

```text
HTTP JSON
→ PlaceOrderHttpRequest
→ PlaceOrderCommand
→ input port
→ OrderView
→ OrderHttpResponse
```

REST-specific concerns остават отвън:

- HTTP status;
- request body;
- path variables;
- exception → HTTP mapping.

Core не знае, че HTTP съществува.

---

# JPA е adapter, не domain model

JPA module има отделни persistence classes:

- `JpaOrderEntity`;
- `JpaOrderLineEntity`;
- `JpaProductEntity`;
- Spring Data repositories.

Adapter:

- [`JpaOrderRepositoryAdapter.java`](./adapter-out-jpa/src/main/java/bg/hristomanov/education/hexagonal/adapter/out/jpa/JpaOrderRepositoryAdapter.java)

прави translation:

```text
Domain Order
↔
JPA Order Entity
```

Това означава:

```text
domain model
≠
persistence model
```

не защото винаги трябва да дублираме models, а защото тук искаме да демонстрираме ясна infrastructure boundary.

---

# Data Mapper връзката

JPA adapter-ът practically използва Data Mapper idea:

```text
Order
→ JpaOrderEntity

JpaOrderEntity
→ Order
```

Това пази domain type-овете без JPA annotations.

Цена:

- mapping code;
- duplicate structures;
- повече files.

Полза:

- domain model не е constrained от ORM;
- persistence schema може да еволюира отделно;
- core test-овете не виждат Hibernate.

Не го правим механично за всеки CRUD проект.

---

# Composition Root

Някой все пак трябва да знае всички concrete pieces.

Това е outermost bootstrap module.

Spring container-ът е composition mechanism.

- [`UseCaseConfiguration.java`](./bootstrap/src/main/java/bg/hristomanov/education/hexagonal/bootstrap/UseCaseConfiguration.java)

```text
OrderRepositoryPort
← JpaOrderRepositoryAdapter

ProductCatalogPort
← JpaProductCatalogAdapter

OrderApplicationService
← receives the ports

REST controller
← receives input ports
```

Spring Framework `@Configuration` + `@Bean` е много естествен начин да направим това explicit.

---

# Защо core service няма `@Service`?

Не защото `@Service` е лош annotation.

А защото този lab иска да докаже силната форма:

> application core може да бъде framework-independent.

Spring може да управлява plain Java object чрез:

```java
@Bean
OrderApplicationService coreOrderApplication(...) {
    return new OrderApplicationService(...);
}
```

Dependency Injection не изисква domain/application class-ът да знае container-а.

---

# Transaction boundary без `@Transactional` в core

Това е един от най-полезните детайли в lab-а.

Core use case:

```text
OrderApplicationService.place()
```

не носи Spring annotation.

Outermost bootstrap добавя decorator:

- [`TransactionalPlaceOrderUseCase.java`](./bootstrap/src/main/java/bg/hristomanov/education/hexagonal/bootstrap/TransactionalPlaceOrderUseCase.java)

```text
REST
→ TransactionalPlaceOrderUseCase
→ OrderApplicationService
→ output ports
```

Decorator-ът използва `TransactionTemplate`.

Така:

- transaction policy е explicit;
- core остава plain Java;
- use-case boundary пак е transaction boundary.

---

# Това ли е задължително production решение?

Не.

Много добър Spring application може да има:

```java
@Service
@Transactional
class OrderApplicationService { ... }
```

и пак да следва основния Hexagonal dependency intent.

Важно е да различим:

```text
architectural principle
vs
purity level
```

Framework-free core е полезен, когато isolation-ът има реална стойност.

Не е религиозно изискване.

---

# Test adapter като първокласен adapter

Core test:

- [`OrderApplicationServiceTest.java`](./core/src/test/java/bg/hristomanov/education/hexagonal/OrderApplicationServiceTest.java)

използва:

```text
InMemoryOrderRepository
InMemoryProductCatalog
```

Това не са mocks на Spring.

Това са test adapters към същите output ports.

Flow:

```text
JUnit
→ input port / application service
→ in-memory output adapters
```

Точно това е една от основните цели в оригиналния Ports & Adapters pattern.

---

# Integration test

Bootstrap test:

- [`HexagonalArchitectureIntegrationTest.java`](./bootstrap/src/test/java/bg/hristomanov/education/hexagonal/HexagonalArchitectureIntegrationTest.java)

доказва:

```text
PlaceOrderUseCase
→ plain core
→ JpaProductCatalogAdapter
→ JpaOrderRepositoryAdapter
→ H2
```

и отделно:

```text
OrderRestController
→ input port
→ core
→ adapters
```

Тоест unit test-ът доказва isolated core, а integration test-ът доказва wiring-а.

---

# Ports & Adapters ≠ просто interfaces навсякъде

Anti-pattern:

```text
OrderServiceInterface
OrderMapperInterface
OrderValidatorInterface
OrderFactoryInterface
OrderCalculatorInterface
```

без реални substitution boundaries.

Port трябва да съществува за **meaningful conversation across application boundary**.

Не за всеки class.

---

# Колко input ports?

Не е нужно:

```text
1 interface = 1 method = 1 use case
```

във всеки проект.

Възможни styles:

- interface per use case;
- cohesive group of use cases;
- command/query handler APIs;
- application facade.

Важно е boundary-то да е ясно и stable.

Cockburn също подчертава, че няма магически правилен брой ports.

---

# Layered Architecture vs Hexagonal

Traditional layered shape:

```text
Controller
↓
Service
↓
Repository
↓
Database
```

Това не е автоматично лошо.

Проблемът е, ако dependency direction practically стане:

```text
business logic
→ framework
→ persistence details
```

Hexagonal изрично моделира:

```text
outside
→ adapters
→ application boundary

application
→ owned ports
→ adapters
→ outside
```

Фокусът е inside/outside, не top/bottom.

---

# Hexagonal vs Onion Architecture

Onion Architecture подчертава:

> dependencies point inward toward the core.

Това е много близък dependency principle.

Useful distinction:

```text
Hexagonal
→ ports/conversations + adapters + inside/outside

Onion
→ concentric layers + inward dependency rule
```

Реални systems често комбинират terminology.

Не спорим за diagram-а; гледаме dependencies.

---

# Hexagonal vs Clean Architecture

Clean Architecture също подчертава:

- policy вътре;
- details отвън;
- dependency direction inward;
- frameworks като implementation detail.

Има различна vocabulary/layering, но същото семейство ideas.

Важно:

> Hexagonal, Onion и Clean не са три npm dependencies, които трябва да 'инсталираме'.

Те са architectural models за control на coupling.

---

# Hexagonal vs GoF Adapter

GoF Adapter:

> превежда един interface към друг interface, който client-ът очаква.

Hexagonal Architecture използва adapter idea систематично на application boundaries.

Пример:

```text
OrderRepositoryPort
← JpaOrderRepositoryAdapter
```

Това е едновременно:

- infrastructure adapter в Hexagonal architecture;
- adapter-like object design.

Но Hexagonal е architectural pattern, не просто един wrapper class.

---

# Hexagonal vs Repository

Repository е един конкретен output-side abstraction.

Hexagonal казва къде се намира той в по-голямата dependency picture.

```text
core
→ OrderRepositoryPort
← JPA adapter
```

Repository pattern и Hexagonal architecture се допълват.

---

# Hexagonal + CQRS

CQRS може да живее вътре в hexagon-а:

```text
input ports
├→ command use cases
└→ query use cases

output ports
├→ write repository
└→ read-model gateway
```

Adapters могат да бъдат:

- REST;
- Kafka consumer;
- JPA;
- Elasticsearch;
- Redis.

CQRS определя read/write responsibility separation.

Hexagonal определя inside/outside dependency boundary.

---

# Hexagonal + Outbox / Messaging

Message consumer може да бъде driving adapter:

```text
Kafka consumer
→ HandleOrderCreatedUseCase
```

Message publisher може да бъде driven adapter:

```text
application
→ IntegrationEventPublisherPort
← Outbox adapter
```

Така broker API не влиза в core-а.

---

# Hexagonal + external HTTP API

Core port:

```text
CreditScorePort
```

Adapters:

```text
ExperianHttpAdapter
FakeCreditScoreAdapter
CachedCreditScoreAdapter
```

Application-ът не трябва да знае:

- URL;
- JSON shape;
- OAuth details;
- HTTP status codes.

Това остава adapter concern.

---

# Кога има реална полза

Силни сигнали:

- complex domain logic;
- several inbound channels;
- external integrations се сменят;
- long-lived system;
- core трябва да се test-ва изолирано;
- team boundaries;
- need for explicit architecture boundaries;
- persistence model и domain model се дърпат в различни посоки.

---

# Кога е overengineering

За:

```text
simple CRUD admin
3 entities
one REST API
one stable DB
minimal business logic
```

може да получим повече ceremony от value.

Цена:

- повече modules;
- mapping;
- interfaces;
- composition wiring;
- navigation overhead.

Не печелим senior points от максимален брой abstractions.

---

# Common mistakes

## 1. Core imports Spring Data

```text
core → JpaRepository
```

Boundary вече е пробита.

## 2. REST DTO влиза в domain

```text
ControllerRequest
→ core everywhere
```

HTTP contract става domain contract.

## 3. JPA entity е единственият model без съзнателно решение

Това може да е ОК, но тогава не твърдим, че persistence е напълно isolated.

## 4. Output port е technology-named

```text
OraclePort
KafkaPort
```

Core вече описва implementation detail.

## 5. Port за всяка малка method

Получаваме interface explosion.

## 6. Composition root липсва

Някой трябва explicit да избере:

```text
which adapter implements which port
```

Иначе dependencies се появяват чрез service locator/global lookups.

---

# Runtime dependency vs source dependency

Core runtime очевидно използва JPA adapter-а през port.

Но source-code dependency е:

```text
JpaOrderRepositoryAdapter
→ OrderRepositoryPort
```

а не:

```text
OrderApplicationService
→ JpaOrderRepositoryAdapter
```

Това е inversion-ът.

Runtime call direction и compile-time dependency direction не са едно и също.

---

# Transaction boundaries

Hexagonal Architecture не казва автоматично:

> transaction трябва да е точно тук.

Но use-case boundary е често добро място.

Options:

- framework annotation върху application service;
- transaction decorator;
- explicit UnitOfWork port;
- transaction orchestration в outer layer.

Lab-ът избира decorator, за да държи core-а framework-free.

Това е teaching choice, не universal law.

---

# Persistence mapping trade-off

Separate domain/JPA models си струват повече при:

- complex domain invariants;
- legacy schema;
- multiple persistence stores;
- ORM constraints that distort domain;
- long-lived domain model.

По-малко при:

- CRUD entity ≈ database row;
- simple schema;
- mapping doubles code without benefit.

Hexagonal Architecture позволява separation; не те задължава винаги да я максимизираш.

---

# Architecture enforcement

Най-добрият architecture document не е достатъчен, ако build-ът позволява всичко.

Тук Maven graph-ът прави:

```text
core
cannot compile against
adapter modules
```

без да добавим reverse dependency.

Това е по-силен guard от package naming alone.

По-големи systems могат допълнително да използват:

- ArchUnit;
- JPMS;
- module boundaries;
- Spring Modulith;
- static architecture checks.

---

# Как да стартираме

Целият hexagonal lab:

```bash
mvn -f spring/architecture/hexagonal-architecture/pom.xml test
```

От root reactor:

```bash
mvn -pl spring/architecture/hexagonal-architecture -am test
```

Само framework-free core:

```bash
mvn -pl spring/architecture/hexagonal-architecture/core test
```

Spring Boot application:

```bash
mvn -pl spring/architecture/hexagonal-architecture/bootstrap -am spring-boot:run
```

HTTP examples:

- [`bootstrap/http/hexagonal-demo.http`](./bootstrap/http/hexagonal-demo.http)

---

# Какво доказват тестовете

## Core unit test

```text
JUnit
→ OrderApplicationService
→ InMemoryOrderRepository
→ InMemoryProductCatalog
```

без Spring/database.

## Bootstrap integration test

```text
Spring
→ input port
→ core
→ JPA adapters
→ H2
```

и:

```text
REST adapter
→ input port
→ core
```

Отделно test-ът проверява, че `OrderApplicationService` няма Spring/JPA annotations.

---

# Mental model за запомняне

1. **Ports са purposeful application contracts.**
2. **Adapters превеждат technology към/от тези contracts.**
3. **Driving adapters извикват application-а; driven adapters се извикват от application-а.**
4. **Core owns the abstractions it needs from outside.**
5. **Compile-time dependencies point inward.**
6. **REST/JPA/Kafka са details, не business core.**
7. **Composition root знае concrete implementations.**
8. **Hexagonal не означава шест ports и не означава interface за всеки class.**
9. **Framework-free core е option with trade-offs, не dogma.**
10. **Build-enforced boundaries са по-надеждни от arrows in diagrams.**

---

# Как да разпозная казуса

```text
[ ] Business service imports ли JPA/HTTP/broker classes?
[ ] Domain logic може ли да се тества без Spring context?
[ ] Има ли повече от един inbound channel?
[ ] External provider model изтича ли навътре?
[ ] Persistence technology определя ли domain API?
[ ] Има ли clear application boundary/use cases?
[ ] Core-owned contracts ли са outbound dependencies?
[ ] Adapter-ите заменяеми ли са?
[ ] Composition root explicit ли е?
[ ] Build tool enforce-ва ли dependency direction?
```

---

# Code-review checklist

```text
[ ] Port-ът именуван ли е по purpose, а не technology?
[ ] Driving adapter съдържа ли business logic?
[ ] Driven adapter съдържа ли domain decisions?
[ ] Core import-ва ли Spring/JPA/Web?
[ ] HTTP DTO изтича ли в core?
[ ] JPA entity изтича ли в input port?
[ ] Output interface owned ли е от core side?
[ ] Transaction boundary ясна ли е?
[ ] Mapping code носи ли реална isolation value?
[ ] Interface съществува ли за meaningful boundary?
[ ] Има ли isolated core test?
[ ] Има ли adapter integration test?
[ ] Architecture rules enforce-ват ли се механично?
```

---

# Упражнения

1. Добави second driving adapter — batch/CLI — към `PlaceOrderUseCase`.
2. Добави in-memory production adapter за `OrderRepositoryPort` чрез profile.
3. Замени JPA product catalog с external HTTP adapter без промяна в core.
4. Добави `InventoryPort` и fake/JPA adapters.
5. Добави Outbox-driven `IntegrationEventPublisherPort`.
6. Направи CQRS query port с Elasticsearch adapter.
7. Добави ArchUnit rule като втори architecture guard.
8. Сравни framework-free core срещу pragmatic core с `@Service/@Transactional`.
9. Пресметни mapping boilerplate-а при domain=JPA model и separate models.
10. Направи architecture decision record кога този pattern не си струва.

---

# Version notes

- Java 25;
- Spring Boot 4.1.1;
- Spring Framework / Spring Data чрез Boot dependency management;
- H2 само за integration lab;
- core module: no Spring/JPA/Web dependencies.

---

# Оригинални източници

- Alistair Cockburn — Hexagonal Architecture, original 2005 article: https://alistair.cockburn.us/hexagonal-architecture
- Jeffrey Palermo — The Onion Architecture, Part 1: https://jeffreypalermo.com/2008/07/the-onion-architecture-part-1/
- Martin Fowler — Badri on Hexagonal Rails: https://martinfowler.com/articles/badri-hexagonal/
- Spring Framework — IoC / Dependency Injection: https://docs.spring.io/spring-framework/reference/core/beans/introduction.html
- Spring Framework — Java-based Configuration: https://docs.spring.io/spring-framework/reference/core/beans/java.html
- Spring Data JPA 4.1.1: https://docs.spring.io/spring-data/jpa/reference/

---

# Изходен въпрос

Когато видиш controller → service → repository architecture, не питай първо:

> Hexagonal ли е или layered?

Питай:

> **Кои са стабилните business/application contracts, кои неща са външни details, и сочат ли compile-time dependencies към core-а или core-ът е започнал да зависи от REST/JPA/Kafka конкретики?**
