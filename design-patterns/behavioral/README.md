# Behavioral patterns — къде живее вариращото поведение?

Behavioral patterns са най-близо до ежедневния service code.

Те отговарят на въпроси като:

- как да сменям algorithm без growing switch;
- как няколко consumers да реагират на event;
- как request да мине през pipeline;
- как lifecycle state да определя позволените операции;
- как да фиксирам workflow, но да оставя extension points;
- как да превърна action в object.

В този module:

- Strategy;
- Observer;
- Chain of Responsibility;
- State;
- Template Method;
- Command.

---

# 1. Strategy

## Naive shape

```text
if REGULAR ...
else if VIP ...
else if EMPLOYEE ...
else if PARTNER ...
```

Проблемът не е самият `if`.

Проблемът идва, когато всеки branch започне да има:

- различни dependencies;
- различни tests;
- различен lifecycle;
- различна release frequency.

## Pattern

[`DiscountStrategy.java`](./src/main/java/bg/hristomanov/education/patterns/behavioral/strategy/DiscountStrategy.java)

[`DiscountCalculator.java`](./src/main/java/bg/hristomanov/education/patterns/behavioral/strategy/DiscountCalculator.java)

```text
DiscountCalculator
      ↓
Map<CustomerSegment, DiscountStrategy>
      ├→ Regular
      └→ VIP
```

Context-ът знае contract-а, не algorithm details.

## Spring form

Много често:

```java
Service(List<MyStrategy> strategies)
```

Spring inject-ва implementations, а ние строим registry.

## Кога НЕ

Не заменяй `switch` с 12 classes, ако logic-ът е по един ред и няма independent variation.

---

# 2. Observer

## Проблемът

След order placement искаме:

- email;
- audit;
- analytics;
- loyalty points.

Naive:

```text
OrderService
  → email
  → audit
  → analytics
  → loyalty
```

OrderService става owner на всеки side effect.

## Pattern

[`OrderEventPublisher.java`](./src/main/java/bg/hristomanov/education/patterns/behavioral/observer/OrderEventPublisher.java)

```text
OrderPlacedEvent
      ↓
publisher
  ├→ Email listener
  └→ Audit listener
```

Publisher-ът не знае конкретната reaction logic.

## Важен caveat

Observer не означава автоматично asynchronous.

Нашият plain Java publisher е synchronous.

Spring `ApplicationEventPublisher` също не трябва да се бърка с durable broker.

## Кога не

Ако business transaction изисква:

> „payment НЕ се счита за успешен, ако ledger write fail-не“

това може да е пряк synchronous dependency, а не „side-effect observer“.

---

# 3. Chain of Responsibility

## Проблемът

Имаме validation:

```text
customer?
amount?
country?
fraud?
limit?
permission?
```

Един метод постепенно става 150 реда.

## Pattern

[`OrderValidationHandler.java`](./src/main/java/bg/hristomanov/education/patterns/behavioral/chain/OrderValidationHandler.java)

```text
CustomerPresent
    ↓
PositiveTotal
    ↓
SupportedCountry
```

Всеки handler:

- знае една rule;
- може да stop-не chain-а;
- иначе предава request-а нататък.

## Framework analogy

- servlet filters;
- Spring Security filter chain;
- MVC interceptors;
- validation pipelines.

## Variation

Някои chains stop-ват при първи success, други при first failure, трети aggregate-ват всички results.

Pattern-ът не диктува business policy-то.

---

# 4. State

## Naive shape

```java
switch (status) {
    case PENDING -> ...
    case PAID -> ...
    case SHIPPED -> ...
    case CANCELLED -> ...
}
```

Ако имаме един switch на едно място — това може да е напълно ОК.

Проблемът е, когато същите state rules се повтарят в:

- pay();
- ship();
- cancel();
- refund();
- edit();
- controller;
- validation service.

## Pattern

[`Order.java`](./src/main/java/bg/hristomanov/education/patterns/behavioral/state/Order.java)

```text
Order
 ↓ current state
PendingState
PaidState
ShippedState
CancelledState
```

Всеки state знае кое е позволено и към какъв state може да transition-не.

## Strategy vs State

Strategy:

> caller/config избира algorithm.

State:

> object lifecycle-ът променя behavior-а.

Повече: [`../PATTERN-COMPARISONS.md`](../PATTERN-COMPARISONS.md).

---

# 5. Template Method

## Проблемът

Имаме CSV и JSON import.

И двата трябва да спазват:

```text
load
→ parse
→ validate
→ persist
→ optional after hook
```

Не искаме subclass да забрави validate или да промени критичния order.

## Pattern

[`AbstractOrderImportJob.java`](./src/main/java/bg/hristomanov/education/patterns/behavioral/templatemethod/AbstractOrderImportJob.java)

Base class притежава:

```java
public final ImportResult execute(...)
```

Subclasses override-ват само variation points.

- [`CsvOrderImportJob.java`](./src/main/java/bg/hristomanov/education/patterns/behavioral/templatemethod/CsvOrderImportJob.java)
- [`JsonOrderImportJob.java`](./src/main/java/bg/hristomanov/education/patterns/behavioral/templatemethod/JsonOrderImportJob.java)

## Trade-off

Template Method използва inheritance.

Ако искаме runtime composition на parsing/validation/persistence policies, Strategy/composition може да е по-добра.

---

# 6. Command

## Проблемът

Имаме action:

```text
create invoice for order O-42
```

Ако action-ът е просто method call, caller-ът трябва да го изпълни веднага.

Но какво ако искаме:

- queue;
- retry;
- schedule;
- audit;
- history;
- batch;
- undo?

Тогава е полезно action-ът да стане object.

## Pattern

[`Command.java`](./src/main/java/bg/hristomanov/education/patterns/behavioral/command/Command.java)

Concrete:

[`CreateInvoiceCommand.java`](./src/main/java/bg/hristomanov/education/patterns/behavioral/command/CreateInvoiceCommand.java)

Invoker:

[`CommandBus.java`](./src/main/java/bg/hristomanov/education/patterns/behavioral/command/CommandBus.java)

```text
caller
  ↓ creates command
CommandBus
  ↓ execute
CreateInvoiceCommand
  ↓
InvoiceService
```

## Backend analogy

Application-layer command/handler architectures и queues често използват сходен mental model, макар конкретната архитектура да не е буквално textbook GoF Command.

---

# 7. Как го доказваме

[`BehavioralPatternsTest.java`](./src/test/java/bg/hristomanov/education/patterns/behavioral/BehavioralPatternsTest.java)

Доказва:

- Strategy избира различен pricing algorithm;
- Observer fan-out-ва един event към два listeners;
- Chain stop-ва при first validation failure;
- State enforce-ва lifecycle transitions;
- Template Method запазва stable workflow order;
- Command може да бъде изпълнен и записан от invoker.

---

# 8. Decision guide

```text
Различни algorithms за една операция?
→ Strategy

Много reactions на един event?
→ Observer

Последователни handlers/validators, които могат да stop/forward?
→ Chain of Responsibility

Behavior според lifecycle state?
→ State

Stable workflow + overridable steps?
→ Template Method

Искам operation/action да стане object?
→ Command
```

---

# 9. Code review checklist

```text
[ ] Growing switch представя ли истинска strategy variation?
[ ] Event listener-ите side effects ли са или critical transaction steps?
[ ] Chain order-ът explicit ли е?
[ ] Chain stop/continue policy ясна ли е?
[ ] State rules повторени ли са на много места?
[ ] Template base class контролира ли важен invariant/lifecycle?
[ ] Наследяването нужно ли е или composition е по-гъвкава?
[ ] Command object-ът ще бъде ли queued/retried/audited, или е ceremony?
```

---

# Какво да запомня

1. Strategy сменя **algorithm**.
2. Observer разкача **publisher от reactions**.
3. Chain моделира **ordered processing pipeline**.
4. State мести **lifecycle-dependent behavior** в state objects.
5. Template Method пази **stable algorithm skeleton**.
6. Command прави **action first-class object**.
7. Pattern трябва да следва variation point-а, не обратното.

---

# Оригинални източници

- ForrestKnight — 7 Design Patterns EVERY Developer Should Know:  
  https://www.youtube.com/watch?v=BJatgOiiht4
- Design Patterns: Elements of Reusable Object-Oriented Software
- Refactoring.Guru — Behavioral Design Patterns:  
  https://refactoring.guru/design-patterns/behavioral-patterns
- Spring Framework — ApplicationContext events:  
  https://docs.spring.io/spring-framework/reference/core/beans/context-introduction.html
- Spring MVC — HandlerInterceptor:  
  https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-servlet/handlermapping-interceptor.html
