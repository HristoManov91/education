# Design Patterns, които вече виждаме в Spring

Този файл не твърди, че всеки Spring class е „чиста textbook имплементация“ на GoF pattern. Framework design-ът често комбинира няколко идеи.

Целта е по-практична:

> Когато видиш pattern-а в нашия plain Java пример, да разпознаеш сходния design pressure в Spring.

---

# 1. Singleton — Spring singleton scope не е GoF Singleton

Spring документацията изрично прави разликата.

## GoF Singleton

Class-ът сам контролира:

- constructor access;
- instance creation;
- global access;
- обикновено една instance на ClassLoader.

Наш пример:

[`CountryCodeRegistry.java`](./creational/src/main/java/bg/hristomanov/education/patterns/creational/singleton/CountryCodeRegistry.java)

## Spring singleton bean

Container-ът контролира lifecycle-а.

```text
ApplicationContext
  └→ една instance за конкретната bean definition
```

Това е **per-container / per-bean**, не JVM-global singleton.

Официална документация:

https://docs.spring.io/spring-framework/reference/core/beans/factory-scopes.html

## Практична последица

В Spring application обикновено предпочитаме:

```text
constructor injection
+ singleton-scoped stateless bean
```

пред:

```text
MyService.getInstance()
```

Dependency-то остава видимо и тестируемо.

---

# 2. Strategy — interface + List от beans

Това е един от най-често използваните patterns в Spring application code.

Примерна форма:

```text
interface PaymentStrategy

CardPaymentStrategy   @Component
BankPaymentStrategy   @Component
CashPaymentStrategy   @Component

PaymentService(List<PaymentStrategy> strategies)
```

После строим registry:

```text
PaymentType → PaymentStrategy
```

Това е почти същият mental model като:

[`DiscountCalculator.java`](./behavioral/src/main/java/bg/hristomanov/education/patterns/behavioral/strategy/DiscountCalculator.java)

Когато добавим нов strategy bean, често не пипаме orchestration service-а.

---

# 3. Observer — ApplicationEventPublisher / @EventListener

Spring позволява in-process application events.

Conceptual flow:

```text
service
  ↓ publish
ApplicationEventPublisher
  ↓
@EventListener A
@EventListener B
```

Официална документация:

https://docs.spring.io/spring-framework/reference/core/beans/context-introduction.html

Наш plain Java пример:

[`OrderEventPublisher.java`](./behavioral/src/main/java/bg/hristomanov/education/patterns/behavioral/observer/OrderEventPublisher.java)

## Важно

Spring application event не става distributed event само защото го наричаме event.

По подразбиране мислим за in-process coupling/lifecycle, а не за Kafka durability/replay semantics.

---

# 4. Proxy — един от най-важните Spring patterns

Spring AOP е proxy-based.

Официална документация:

https://docs.spring.io/spring-framework/reference/core/aop/proxying.html

Conceptually:

```text
caller
  ↓
Spring proxy
  ↓
before / around advice
  ↓
target bean
  ↓
after advice
```

Това стои зад много инфраструктурни features:

- transactions;
- method caching;
- async execution;
- security/AOP concerns.

## Защо self-invocation проблемът вече има смисъл

```text
external caller
→ proxy
→ target.methodA()

methodA()
→ this.methodB()
```

Второто извикване е директно върху `this`, не минава отново през proxy boundary.

Затова annotation върху `methodB()` може да не получи очакваното proxy advice.

След като разбереш Proxy pattern-а, това вече не е „странно Spring правило“, а следствие от object graph-а.

Наш explicit proxy:

[`CachingProductCatalogProxy.java`](./structural/src/main/java/bg/hristomanov/education/patterns/structural/proxy/CachingProductCatalogProxy.java)

---

# 5. Adapter — Spring MVC HandlerAdapter

Spring MVC има реален type с име `HandlerAdapter`.

Неговата роля е DispatcherServlet да може да работи с различни handler forms, без core dispatcher-ът да знае детайлите как всеки конкретен handler се извиква.

Официална документация:

https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-servlet/special-bean-types.html

Това е много добър framework-level mental model за Adapter:

```text
DispatcherServlet
     ↓
HandlerAdapter
     ↓
конкретен handler model
```

Наш пример:

[`LegacyCourierAdapter.java`](./structural/src/main/java/bg/hristomanov/education/patterns/structural/adapter/LegacyCourierAdapter.java)

---

# 6. Chain of Responsibility — filters / interceptors / security chain

Типичен request pipeline:

```text
request
 ↓
filter 1
 ↓
filter 2
 ↓
filter 3
 ↓
handler
```

Всеки step може:

- да продължи;
- да short-circuit-не;
- да добави context;
- да reject-не.

Spring MVC `HandlerInterceptor` също има преди/след lifecycle и `preHandle` може да прекрати chain-а.

Официална документация:

https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-servlet/handlermapping-interceptor.html

Наш validation chain:

[`OrderValidationHandler.java`](./behavioral/src/main/java/bg/hristomanov/education/patterns/behavioral/chain/OrderValidationHandler.java)

---

# 7. Facade — application service като use-case boundary

Facade не изисква специален Spring annotation.

Много application services естествено играят тази роля:

```text
Controller
  ↓
CheckoutService / CheckoutFacade
  ├→ inventory
  ├→ payment
  └→ shipping
```

Това е полезно, когато boundary-то е business use case, а не просто „service, който делегира към service“.

Наш пример:

[`CheckoutFacade.java`](./structural/src/main/java/bg/hristomanov/education/patterns/structural/facade/CheckoutFacade.java)

---

# 8. Factory — BeanFactory и container-managed creation

Spring IoC container-ът по дефиниция поема голяма част от object creation responsibility.

`BeanFactory` е framework factory abstraction, но не трябва механично да казваме:

> „Spring = Factory Method pattern.“

По-точно е:

> Spring използва factory/registry/DI design идеи, за да отдели construction от business consumers.

Това е същият design pressure, който упражняваме в:

- Factory Method;
- Abstract Factory;
- dependency injection.

---

# 9. Template Method — framework lifecycle hooks

Template Method се появява, когато framework/base class контролира flow-а, а extension point-ът override-ва определени стъпки.

В Spring ecosystem-а има много „template“ APIs, но не всяко нещо с име `Template` трябва автоматично да бъде етикетирано като textbook Template Method.

По-важно е да разпознаем структурата:

```text
framework owns lifecycle
→ user code supplies selected steps/callbacks
```

Наш explicit пример:

[`AbstractOrderImportJob.java`](./behavioral/src/main/java/bg/hristomanov/education/patterns/behavioral/templatemethod/AbstractOrderImportJob.java)

---

# 10. Decorator — wrappers около стабилен contract

В application code често имаме:

```text
MetricsClient(
  RetryClient(
    RealClient
  )
)
```

или:

```text
AuditedService(
  RealService
)
```

Това е Decorator-style composition, ако:

- wrapper-ите имат същия contract;
- целта е capability layering;
- могат да се комбинират.

Наш пример:

[`NotificationSenderDecorator.java`](./structural/src/main/java/bg/hristomanov/education/patterns/structural/decorator/NotificationSenderDecorator.java)

---

# 11. Най-полезният Spring mental model

Когато framework behavior ти изглежда „магически“, питай:

```text
Има ли proxy?
Има ли adapter?
Има ли strategy registry?
Има ли chain?
Има ли event observers?
Container-ът ли притежава object creation/lifecycle?
```

Много Spring internals стават значително по-разбираеми, когато design patterns вече не са само UML diagrams.
