# Structural patterns — как свързваме components без да разнасяме сложността?

Structural patterns се занимават с composition:

- как свързваме interfaces;
- как скриваме subsystem complexity;
- как добавяме behavior около existing object;
- как контролираме достъпа;
- как моделираме tree от objects с еднакъв contract.

В този module:

- Adapter;
- Facade;
- Decorator;
- Proxy;
- Composite.

---

# 1. Adapter

## Реален проблем

Нашият domain иска:

```text
ShippingProvider.quote(postalCode, weightKg)
→ ShippingQuote(priceEur)
```

Third-party legacy API дава:

```text
calculatePriceInEuroCents(zipCode, weightGrams)
→ int
```

Ако разнесем conversion logic-а навсякъде:

```text
controller/service A → grams/cents details
controller/service B → grams/cents details
controller/service C → grams/cents details
```

third-party model-ът заразява domain-а.

## Pattern

[`LegacyCourierAdapter.java`](./src/main/java/bg/hristomanov/education/patterns/structural/adapter/LegacyCourierAdapter.java)

```text
Our domain
  ↓ ShippingProvider
LegacyCourierAdapter
  ↓
LegacyCourierClient
```

Adapter превежда:

- interface;
- units;
- model;
- naming;
- понякога errors/exceptions.

## Code review сигнал

Ако business layer говори с чужди DTO-та и provider-specific enums, вероятно integration boundary липсва.

---

# 2. Facade

## Реален проблем

Controller прави:

```text
inventory.reserve()
payment.charge()
shipping.schedule()
audit.log()
...
```

Скоро caller-ът знае:

- orchestration order;
- subsystem dependencies;
- error semantics;
- кои calls са част от един use case.

## Pattern

[`CheckoutFacade.java`](./src/main/java/bg/hristomanov/education/patterns/structural/facade/CheckoutFacade.java)

```text
Controller
   ↓
checkout(request)
   ↓
CheckoutFacade
   ├→ Inventory
   ├→ Payment
   └→ Shipping
```

Facade не е „God Service“.

Тя трябва да изразява ясна simplified boundary.

## Какво НЕ решава

Facade не прави distributed transaction atomic.

Ако payment успее, shipping fail-не, пак трябва да имаме:

- compensation;
- retry;
- saga;
- business failure policy.

Pattern-ът скрива API complexity, не отменя failure semantics.

---

# 3. Decorator

## Проблемът

Имаме стабилен contract:

```text
NotificationSender.send()
```

и искаме optional capabilities:

- audit;
- metrics;
- retry;
- tracing.

Naive вариант:

```text
EmailSender
  + audit code
  + metrics code
  + retry code
  + tracing code
```

Получаваме един class с много несвързани причини за промяна.

## Pattern

[`NotificationSenderDecorator.java`](./src/main/java/bg/hristomanov/education/patterns/structural/decorator/NotificationSenderDecorator.java)

```text
MetricsDecorator(
  AuditDecorator(
    EmailSender
  )
)
```

Всеки layer:

- има същия interface;
- държи delegate;
- добавя behavior;
- може да бъде композиран.

## Сила

Composition вместо inheritance.

Можем runtime да подредим layers.

## Риск

Order може да има значение:

```text
retry(metrics(real))
≠
metrics(retry(real))
```

Затова decorator composition трябва да бъде intentional.

---

# 4. Proxy

## Проблемът

Искаме object, който изглежда като real service, но контролира access до него.

Примери:

- lazy loading;
- security;
- cache;
- remote transport;
- transaction;
- metrics/AOP.

## Pattern

[`CachingProductCatalogProxy.java`](./src/main/java/bg/hristomanov/education/patterns/structural/proxy/CachingProductCatalogProxy.java)

```text
Consumer
 ↓ ProductCatalog
CachingProductCatalogProxy
 ↓ ProductCatalog
RemoteProductCatalog
```

Consumer-ът работи със същия contract.

Proxy-то решава кога real subject-ът реално да бъде извикан.

## Spring връзка

Spring AOP използва proxies.

Следователно concepts като:

- external proxy call;
- target;
- self-invocation;

не са просто Spring trivia. Те са Proxy semantics.

Официална документация:

https://docs.spring.io/spring-framework/reference/core/aop/proxying.html

---

# 5. Composite

## Проблемът

Имаме tree:

```text
Office bundle
├→ Desk set
│  ├→ Keyboard
│  └→ 2 x Mouse
└→ Cable
```

Искаме caller-ът да може да пита:

```text
component.total()
```

без да знае дали component е leaf или container.

## Pattern

[`PriceComponent.java`](./src/main/java/bg/hristomanov/education/patterns/structural/composite/PriceComponent.java)

Leaf:

[`ProductLine.java`](./src/main/java/bg/hristomanov/education/patterns/structural/composite/ProductLine.java)

Composite:

[`Bundle.java`](./src/main/java/bg/hristomanov/education/patterns/structural/composite/Bundle.java)

```text
PriceComponent
  ├→ ProductLine
  └→ Bundle
       └→ List<PriceComponent>
```

## Къде се среща

- menus;
- file trees;
- organization hierarchy;
- product bundles;
- nested rules;
- expression trees.

---

# 6. Най-важното разграничение

| Pattern | Основен въпрос |
| --- | --- |
| Adapter | „Как да преведа чужд contract към нашия?“ |
| Facade | „Как да дам по-прост API върху subsystem?“ |
| Decorator | „Как да добавя composable behavior?“ |
| Proxy | „Как да контролирам достъпа до real object?“ |
| Composite | „Как да третирам leaf и tree еднакво?“ |

За повече: [`../PATTERN-COMPARISONS.md`](../PATTERN-COMPARISONS.md).

---

# 7. Как го доказваме

[`StructuralPatternsTest.java`](./src/test/java/bg/hristomanov/education/patterns/structural/StructuralPatternsTest.java)

Доказва:

- Adapter конвертира kg → grams и cents → EUR;
- Facade скрива checkout orchestration;
- decorators могат да се stack-ват;
- caching proxy извиква remote catalog само веднъж;
- Composite сумира nested tree през общ interface.

---

# 8. Code review checklist

```text
[ ] Third-party model изтича ли в domain layer?
[ ] Caller-ът знае ли твърде много subsystem стъпки?
[ ] Един class трупа ли audit/metrics/retry върху core responsibility?
[ ] Wrapper-ът променя interface, добавя behavior или контролира access?
[ ] Proxy ли е или Decorator?
[ ] Facade-ът use-case boundary ли е, или просто God Service?
[ ] Имаме ли tree structure, пълна с instanceof checks?
```

---

# Какво да запомня

1. Adapter **превежда**.
2. Facade **опростява**.
3. Decorator **надгражда behavior**.
4. Proxy **контролира access**.
5. Composite **унифицира leaf и container**.
6. Wrapper syntax-ът може да е сходен; intent-ът определя pattern-а.

---

# Оригинални източници

- ForrestKnight — 7 Design Patterns EVERY Developer Should Know:  
  https://www.youtube.com/watch?v=BJatgOiiht4
- Design Patterns: Elements of Reusable Object-Oriented Software
- Refactoring.Guru — Structural Design Patterns:  
  https://refactoring.guru/design-patterns/structural-patterns
- Spring Framework — AOP Proxying:  
  https://docs.spring.io/spring-framework/reference/core/aop/proxying.html
- Spring MVC — HandlerAdapter / special bean types:  
  https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-servlet/special-bean-types.html
