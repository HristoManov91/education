# Pattern comparisons — кое с кое се бърка

Този файл е нарочно отделен. В реален code review най-трудното обикновено не е да дефинираме pattern, а да разграничим **две структури, които изглеждат почти еднакво в кода**.

---

# 1. Strategy vs State

И двете често изглеждат така:

```text
Context
  ↓
interface
  ↓
Concrete implementation A / B / C
```

Разликата е **защо implementation-ът се сменя**.

## Strategy

Въпросът е:

> „Кой algorithm/policy искаме да използваме за тази операция?“

Пример:

```text
DiscountCalculator
  → RegularDiscountStrategy
  → VipDiscountStrategy
```

Caller/configuration избира policy.

Код: [`DiscountCalculator.java`](./behavioral/src/main/java/bg/hristomanov/education/patterns/behavioral/strategy/DiscountCalculator.java)

## State

Въпросът е:

> „Какво е позволено да прави object-ът в текущия си lifecycle state?“

Пример:

```text
Order
PENDING → PAID → SHIPPED
    ↘ CANCELLED
```

Самият context сменя state-а в резултат на операции.

Код: [`Order.java`](./behavioral/src/main/java/bg/hristomanov/education/patterns/behavioral/state/Order.java)

## Кратко правило

```text
Strategy = сменям algorithm
State    = object-ът сменя behavior с lifecycle-а си
```

---

# 2. Adapter vs Facade vs Decorator vs Proxy

Това са четири различни намерения, въпреки че често има wrapper object.

| Pattern | Interface | Цел |
| --- | --- | --- |
| Adapter | различен → нашия | превод между несъвместими contracts |
| Facade | нов, по-прост | скрива сложен subsystem |
| Decorator | същият | добавя composable behavior |
| Proxy | същият | контролира достъпа до real subject |

## Adapter

```text
Our ShippingProvider
        ↓
LegacyCourierAdapter
        ↓
LegacyCourierClient (друг API/model)
```

Код: [`LegacyCourierAdapter.java`](./structural/src/main/java/bg/hristomanov/education/patterns/structural/adapter/LegacyCourierAdapter.java)

## Facade

```text
CheckoutFacade.checkout()
    ├→ Inventory
    ├→ Payment
    └→ Shipping
```

Код: [`CheckoutFacade.java`](./structural/src/main/java/bg/hristomanov/education/patterns/structural/facade/CheckoutFacade.java)

## Decorator

```text
Metrics(
  Audit(
    EmailSender
  )
)
```

Всички имплементират `NotificationSender`.

Код: [`NotificationSenderDecorator.java`](./structural/src/main/java/bg/hristomanov/education/patterns/structural/decorator/NotificationSenderDecorator.java)

## Proxy

```text
Consumer
  ↓
ProductCatalog interface
  ↓
CachingProxy
  ↓
RemoteProductCatalog
```

Proxy решава **дали/кога/как** да достъпи real subject-а.

Код: [`CachingProductCatalogProxy.java`](./structural/src/main/java/bg/hristomanov/education/patterns/structural/proxy/CachingProductCatalogProxy.java)

---

# 3. Decorator vs Proxy

Най-честото объркване.

И двата:

- имат същия interface като wrapped object;
- държат delegate;
- могат да изпълнят code before/after delegate call.

Разликата е intent.

## Decorator

Consumer-ът съзнателно **композира capabilities**.

```text
sender =
  metrics(
    audit(
      email
    )
  )
```

Може да имаме 0, 1 или 5 decorators.

## Proxy

Consumer-ът често не трябва дори да знае, че има proxy.

Proxy-то контролира access:

- lazy loading;
- caching;
- security;
- remote call;
- transaction;
- AOP advice.

Spring AOP е отличен реален пример защо Proxy трябва да се разбира добре.

---

# 4. Decorator vs Chain of Responsibility

И двете могат да бъдат „списък/wrapper от handlers“.

## Decorator

Обикновено всеки decorator **извиква delegate-а** и добавя behavior около него.

```text
metrics → audit → sender
```

Всички layers участват.

## Chain

Handler може да реши:

- обработвам;
- reject-вам;
- предавам нататък.

```text
customer validation
   ↓ success
amount validation
   ↓ success
country validation
```

При failure chain-ът може да спре.

---

# 5. Observer vs Pub/Sub

## Observer

Класическата форма има логическа връзка publisher → observers.

Нашият пример е in-process:

```text
OrderEventPublisher
  ├→ Email listener
  └→ Audit listener
```

## Pub/Sub

При message broker:

```text
producer
  ↓
topic/exchange
  ↓
consumer A / B / C
```

Producer и consumers могат:

- да са в различни процеси;
- да не работят едновременно;
- да имат retries;
- да имат durable delivery;
- да имат independent deployment.

Затова:

> Spring `ApplicationEventPublisher` не е „малък Kafka“.

Pattern идеята е сродна, operational semantics са съвсем различни.

---

# 6. Template Method vs Strategy

И двете отделят stable от varying behavior.

## Template Method — inheritance

Base class притежава algorithm skeleton-а:

```text
load
→ parse (override)
→ validate
→ persist
→ hook
```

Subclass променя стъпки.

Код: [`AbstractOrderImportJob.java`](./behavioral/src/main/java/bg/hristomanov/education/patterns/behavioral/templatemethod/AbstractOrderImportJob.java)

## Strategy — composition

Context получава object, който реализира varying algorithm.

```text
DiscountCalculator
    ↓
DiscountStrategy
```

## Правило

```text
Template Method = variation чрез inheritance
Strategy        = variation чрез composition
```

Composition е по-гъвкава runtime. Template Method е полезен, когато искаме base class да контролира lifecycle/skeleton-а.

---

# 7. Factory terminology

## Simple Factory

```java
PaymentProcessor create(PaymentType type)
```

Един object централизира construction switch-а.

Полезно, но не е отделен GoF pattern.

## Factory Method

Base creator декларира creation method, subclass решава concrete product.

Код: [`PaymentProcessorCreator.java`](./creational/src/main/java/bg/hristomanov/education/patterns/creational/factorymethod/PaymentProcessorCreator.java)

## Abstract Factory

Един factory object създава **family** от related products.

Код: [`CommerceFactory.java`](./creational/src/main/java/bg/hristomanov/education/patterns/creational/abstractfactory/CommerceFactory.java)

---

# 8. Builder vs constructor vs static factory

Не всеки object има нужда от Builder.

## Constructor / record

Предпочитай при:

- малко required полета;
- ясни типове;
- няма сложни invariants.

## Static factory

Полезна, когато името носи semantics:

```text
Money.zero(EUR)
User.guest()
ReportRequest.forMonth(...)
```

## Builder

Полезен при:

- много optional параметри;
- fluent readable construction;
- staged construction;
- validation в `build()`;
- immutable final object.

Builder за две полета е noise.

---

# 9. Най-бързият decision tree

```text
Имам чужд interface?
→ Adapter

Имам сложен subsystem и искам един use-case API?
→ Facade

Имам същия interface и искам optional layers?
→ Decorator

Имам същия interface и искам access control / AOP / lazy / cache?
→ Proxy

Имам сменяем algorithm?
→ Strategy

Behavior-ът зависи от lifecycle state?
→ State

Имам pipeline, който може да stop/forward?
→ Chain

Искам много independent reactions на event?
→ Observer

Искам stable workflow, но вариращи steps?
→ Template Method

Искам action да стане first-class object?
→ Command
```
