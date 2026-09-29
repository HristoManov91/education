# Creational patterns — кой притежава object creation-а?

Creational patterns не са просто техники за избягване на `new`.

Те решават въпроси като:

- кой трябва да знае concrete type-а;
- колко сложен е construction contract-ът;
- има ли family от objects, които трябва да са съвместими;
- кой управлява lifecycle-а на shared instance.

В този module:

- Singleton;
- Builder;
- Factory Method;
- Abstract Factory.

---

# 1. Singleton

## Реалният проблем

Имаме resource/registry, за който искаме едно shared instance.

Naive подход:

```java
public static final Something INSTANCE = new Something();
```

Това е просто, но веднага възникват въпросите:

- кой управлява lifecycle-а;
- mutable ли е state-ът;
- как dependency-то се заменя в тест;
- какво означава „един“ при няколко classloaders/contexts;
- има ли нужда изобщо class-ът сам да контролира това?

## Good Java singleton

[`CountryCodeRegistry.java`](./src/main/java/bg/hristomanov/education/patterns/creational/singleton/CountryCodeRegistry.java)

Използваме enum:

```text
CountryCodeRegistry.INSTANCE
```

Предимствата са language-level:

- една enum constant instance;
- безопасна initialization semantics;
- няма custom double-checked locking;
- serialization semantics са контролирани от enum model-а.

## BAD global mutable singleton

[`BadMutableGlobalFeatureFlags.java`](./src/main/java/bg/hristomanov/education/patterns/creational/singleton/BadMutableGlobalFeatureFlags.java)

Проблемът не е само syntax-ът.

```text
Class A ─┐
Class B ─┼→ global mutable state
Test 1  ─┤
Test 2  ─┘
```

Dependencies са скрити, а state leakage между тестове става лесен.

## Spring singleton ≠ GoF Singleton

Spring singleton scope е:

> една bean instance за конкретна bean definition в конкретен container.

Това е container lifecycle, не class-level global instance.

Затова в Spring почти винаги предпочитаме injected stateless singleton bean пред ръчно `getInstance()`.

## Кога има смисъл

- truly process-wide immutable registry;
- stateless utility object с identity;
- enum-based strategy registry, ако DI не е нужен.

## Кога НЕ

- service dependencies в Spring;
- mutable request/user state;
- state, който трябва лесно да се mock-ва;
- просто „защото само една instance ни трябва“.

---

# 2. Builder

## Проблемът

[`BadReportRequest.java`](./src/main/java/bg/hristomanov/education/patterns/creational/builder/BadReportRequest.java)

```java
new BadReportRequest(
    from,
    to,
    true,
    false,
    "PDF",
    filters
);
```

Какво означават двата boolean-а без IDE hints?

При повече optional properties call site-ът става positional protocol.

## Builder

[`ReportRequest.java`](./src/main/java/bg/hristomanov/education/patterns/creational/builder/ReportRequest.java)

```java
ReportRequest request = ReportRequest
        .between(from, to)
        .includeDetails()
        .format("PDF")
        .addFilter("PAID")
        .build();
```

Builder ни дава:

- named construction steps;
- optional fields;
- immutable final product;
- validation на една boundary — `build()`.

## Кога record/static factory е по-добър

Не правим Builder автоматично.

За:

```java
record Money(BigDecimal amount, Currency currency) {}
```

Builder е излишен.

Може да е достатъчно:

```text
Money.zero(EUR)
ReportRequest.forMonth(...)
User.guest()
```

## Code review сигнал

Builder има смисъл, ако constructor-ът е започнал да изглежда като конфигурационен protocol.

---

# 3. Factory Method

## Проблемът

Workflow-ът знае как да работи с `PaymentProcessor`, но не иска да hard-code-не concrete implementation.

Naive:

```text
if CARD → new CardPaymentProcessor()
if BANK → new BankTransferPaymentProcessor()
```

Това смесва две причини за промяна:

1. payment workflow;
2. object creation policy.

## Pattern

[`PaymentProcessorCreator.java`](./src/main/java/bg/hristomanov/education/patterns/creational/factorymethod/PaymentProcessorCreator.java)

```text
PaymentProcessorCreator
  ├→ process(request)
  └→ createProcessor()   ← factory method

CardPaymentProcessorCreator
  └→ CardPaymentProcessor

BankTransferPaymentProcessorCreator
  └→ BankTransferPaymentProcessor
```

Base creator работи с interface. Subclass-ът решава product-а.

## Защо това е различно от Simple Factory

Simple Factory:

```text
PaymentProcessorFactory.create(type)
                         ↓
                      switch
```

Factory Method използва polymorphism за creation decision-а.

И двете могат да бъдат добър design. Просто не са едно и също.

## Кога си струва

- framework/base workflow трябва да остане independent от product type;
- subclasses/plugins определят concrete creation;
- creation е extension point.

## Кога не

Ако имаме един прост switch на едно място и той рядко се променя, Simple Factory може да е по-четим.

---

# 4. Abstract Factory

## Проблемът

Понякога не създаваме един object, а **семейство от съвместими objects**.

Например за регион:

```text
BG
├→ BG tax rules
└→ BG/EUR payment gateway

US
├→ US tax rules
└→ US/USD payment gateway
```

Ако consumer-ът независимо избира всеки component, може да направи несъвместима комбинация.

## Pattern

[`CommerceFactory.java`](./src/main/java/bg/hristomanov/education/patterns/creational/abstractfactory/CommerceFactory.java)

Factory contract:

```text
createTaxCalculator()
createPaymentGateway()
```

Concrete families:

- [`BulgarianCommerceFactory.java`](./src/main/java/bg/hristomanov/education/patterns/creational/abstractfactory/BulgarianCommerceFactory.java)
- [`UsCommerceFactory.java`](./src/main/java/bg/hristomanov/education/patterns/creational/abstractfactory/UsCommerceFactory.java)

## Mental model

Factory Method:

> „Кой concrete product създаваме?“

Abstract Factory:

> „Кое family от related products използваме?“

## Кога е полезен

- provider-specific families;
- tenant-specific infrastructure;
- platform-specific components;
- environment-specific implementations, когато compatibility matters.

## Цена

Добавя interfaces и factory hierarchy.

Ако products не са реално family, pattern-ът може само да усложни creation-а.

---

# 5. Как го доказваме

[`CreationalPatternsTest.java`](./src/test/java/bg/hristomanov/education/patterns/creational/CreationalPatternsTest.java)

Тестовете доказват:

- enum Singleton връща същата instance;
- Builder валидира invariant при `build()`;
- различни Factory Method creators създават различни processors;
- Abstract Factory създава съвместими regional products.

---

# 6. Creational decision guide

```text
Искам един controlled shared instance?
→ Singleton (но първо провери дали DI scope не е по-добър)

Object construction има много optional/configurable полета?
→ Builder

Base workflow трябва да остави subclass/plugin да избере product?
→ Factory Method

Трябва да избера цяла family от свързани products?
→ Abstract Factory
```

---

# 7. Code review checklist

```text
[ ] Consumer-ът знае ли concrete classes, които не би трябвало да знае?
[ ] Object creation logic разпръснат ли е по много callers?
[ ] Constructor-ът станал ли е positional protocol?
[ ] Builder-ът решава ли реален проблем или само добавя boilerplate?
[ ] Factory-то има ли истинска variation point?
[ ] Abstract Factory products реално ли са family?
[ ] Singleton state-ът immutable/stateless ли е?
[ ] В Spring защо не използваме нормален injected singleton bean?
```

---

# Какво да запомня

1. Creational patterns управляват **knowledge за construction**, не просто `new`.
2. Builder решава readability/invariants при сложен construction.
3. Factory Method делегира concrete product creation към subtype.
4. Abstract Factory избира family от products.
5. Singleton е lifecycle decision с висока coupling цена.
6. Spring singleton scope е container concept, различен от GoF Singleton.

---

# Оригинални източници

- ForrestKnight — 7 Design Patterns EVERY Developer Should Know:  
  https://www.youtube.com/watch?v=BJatgOiiht4
- Design Patterns: Elements of Reusable Object-Oriented Software
- Refactoring.Guru — Creational Design Patterns:  
  https://refactoring.guru/design-patterns/creational-patterns
- Spring Framework — Bean Scopes:  
  https://docs.spring.io/spring-framework/reference/core/beans/factory-scopes.html
