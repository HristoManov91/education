# Design Patterns Atlas — Java/Spring

Това е **големият учебен проект за design patterns** в `education`.

Основният вход е видеото **[7 Design Patterns EVERY Developer Should Know](https://www.youtube.com/watch?v=BJatgOiiht4)** на ForrestKnight. То представя седем много полезни patterns — Singleton, Builder, Factory, Facade, Adapter, Strategy и Observer — и ги поставя в трите класически групи: creational, structural и behavioral.

Нашата цел е по-различна от „да научим имената“.

> Целта е да погледнем production Java/Spring код и да можем да кажем:  
> **„Тук проблемът има формата на Strategy.“**,  
> а не:  
> **„Искам някъде да използвам Strategy, защото го научих.“**

---

# ВХОД В ТЕМАТА

## 1. Реалният казус

В един backend проект с времето започват да се появяват неща като:

```text
if (paymentType == CARD) ...
else if (paymentType == BANK_TRANSFER) ...
else if (paymentType == CASH) ...
```

или:

```text
new ReportRequest(from, to, true, false, "PDF", null, null, ...)
```

или:

```text
controller
  → inventory service
  → payment service
  → shipping service
  → audit service
  → notification service
```

или integration код, който навсякъде знае странния model на външната система.

Тези решения често са напълно разумни в началото. Проблемът идва, когато започне variation:

- още един payment type;
- още един provider;
- още една validation rule;
- още един lifecycle state;
- още един side effect;
- още един начин за construction;
- още един cross-cutting concern.

Design patterns са **имена на повтарящи се форми на такива проблеми и на доказани начини да разделим отговорностите**.

## 2. Защо често вече ги използваме несъзнателно

В реален Java/Spring код много patterns се появяват естествено:

- interface + няколко implementations + избор по type → често **Strategy**;
- wrapper със същия interface → може да е **Decorator** или **Proxy**;
- service, който крие няколко subsystem calls → често **Facade**;
- translator около third-party client → **Adapter**;
- list от validators/filters → **Chain of Responsibility**;
- `ApplicationEventPublisher` + listeners → Observer-like design;
- `@Transactional`, `@Cacheable`, `@Async` → Spring proxy-based infrastructure.

Да знаем имената е полезно не за да звучим академично, а защото получаваме **общ инженерeн vocabulary**:

> „Тук вместо switch можем да имаме Strategy registry.“

е много по-кратко от 15 минути обяснение на цялата структура.

## 3. Какво ще научим

За всеки pattern търсим отговор на седем въпроса:

1. Какъв реален проблем го поражда?
2. Как изглежда naive вариантът?
3. Кога naive вариантът започва да боли?
4. Какъв е mental model-ът на pattern-а?
5. Как изглежда production-like Java код?
6. Къде срещаме същата идея в Spring/JDK ecosystem-а?
7. Кога pattern-ът е overengineering?

---

# Голямата картина

```text
CREATIONAL
Как създаваме objects?
        ↓
Singleton / Builder / Factory Method / Abstract Factory

STRUCTURAL
Как свързваме objects и interfaces?
        ↓
Adapter / Facade / Decorator / Proxy / Composite

BEHAVIORAL
Как objects си разпределят поведението и комуникират?
        ↓
Strategy / Observer / Chain of Responsibility /
State / Template Method / Command
```

## Mental model за трите категории

### Creational

Не мисли:

> „Как да скрия `new`?“

Мисли:

> **„Кой трябва да знае как се създава този object и колко сложен е construction contract-ът?“**

### Structural

Не мисли:

> „Как да направя повече wrappers?“

Мисли:

> **„Как да свържа съществуващи компоненти, без consumer-ът да поеме чужда сложност?“**

### Behavioral

Не мисли:

> „Къде да сложа interface?“

Мисли:

> **„Коя част от поведението варира и кой трябва да притежава избора/lifecycle-а ѝ?“**

---

# Структура на проекта

```text
design-patterns/
├── README.md
├── PATTERN-COMPARISONS.md
├── SPRING-MAPPING.md
├── pom.xml
│
├── creational/
│   ├── README.md
│   └── src/
│       ├── .../singleton/
│       ├── .../builder/
│       ├── .../factorymethod/
│       └── .../abstractfactory/
│
├── structural/
│   ├── README.md
│   └── src/
│       ├── .../adapter/
│       ├── .../facade/
│       ├── .../decorator/
│       ├── .../proxy/
│       └── .../composite/
│
└── behavioral/
    ├── README.md
    └── src/
        ├── .../strategy/
        ├── .../observer/
        ├── .../chain/
        ├── .../state/
        ├── .../templatemethod/
        └── .../command/
```

Maven modules са по **тип pattern**, а не по един module за всеки pattern. Така структурата остава ясна, без 20+ дребни `pom.xml` файла.

---

# Покритие

| Категория | Pattern | Във видеото | В проекта | Защо е важен |
| --- | --- | :---: | :---: | --- |
| Creational | Singleton | ✅ | ✅ | lifecycle / shared instance |
| Creational | Builder | ✅ | ✅ | сложен object construction |
| Creational | Factory Method | Factory | ✅ | polymorphic object creation |
| Creational | Abstract Factory | — | ✅ | family от съвместими objects |
| Structural | Adapter | ✅ | ✅ | third-party / legacy integrations |
| Structural | Facade | ✅ | ✅ | use-case boundary над subsystems |
| Structural | Decorator | — | ✅ | добавяне на behavior чрез composition |
| Structural | Proxy | — | ✅ | controlled access / AOP / caching |
| Structural | Composite | — | ✅ | tree structures с един contract |
| Behavioral | Strategy | ✅ | ✅ | заменяем algorithm |
| Behavioral | Observer | ✅ | ✅ | one-to-many reactions |
| Behavioral | Chain of Responsibility | — | ✅ | filters / validators / handlers |
| Behavioral | State | — | ✅ | behavior според lifecycle state |
| Behavioral | Template Method | — | ✅ | фиксиран algorithm skeleton |
| Behavioral | Command | — | ✅ | action като object |

Така първата версия има **15 patterns**.

## Останалите GoF patterns — roadmap, не checklist

Не ги добавяме само за да кажем „имаме 23/23“.

Следващи кандидати:

- Prototype;
- Bridge;
- Flyweight;
- Iterator;
- Mediator;
- Memento;
- Visitor;
- Interpreter.

Всеки ще влезе, когато можем да му дадем пример, който има смисъл за Java/backend работа.

---

# Много важно: „Factory“ не е едно конкретно нещо

В разговорен код често казваме:

> „направих factory“

за class като:

```text
PaymentProcessorFactory.create(type)
```

Това често е **Simple Factory** — полезен idiom, но не е отделен GoF pattern.

GoF има:

## Factory Method

Subclass/implementation решава **кой concrete product** да създаде.

В проекта:

[`PaymentProcessorCreator.java`](./creational/src/main/java/bg/hristomanov/education/patterns/creational/factorymethod/PaymentProcessorCreator.java)

## Abstract Factory

Създаваме **семейство от свързани products**.

В проекта:

[`CommerceFactory.java`](./creational/src/main/java/bg/hristomanov/education/patterns/creational/abstractfactory/CommerceFactory.java)

Тази разлика е важна, защото думата „factory“ сама по себе си не е достатъчно точна при design discussion.

---

# README → код

## Creational

| Pattern | Основен код | Test |
| --- | --- | --- |
| Singleton | [`CountryCodeRegistry.java`](./creational/src/main/java/bg/hristomanov/education/patterns/creational/singleton/CountryCodeRegistry.java) | [`CreationalPatternsTest.java`](./creational/src/test/java/bg/hristomanov/education/patterns/creational/CreationalPatternsTest.java) |
| BAD global singleton | [`BadMutableGlobalFeatureFlags.java`](./creational/src/main/java/bg/hristomanov/education/patterns/creational/singleton/BadMutableGlobalFeatureFlags.java) | — |
| Builder | [`ReportRequest.java`](./creational/src/main/java/bg/hristomanov/education/patterns/creational/builder/ReportRequest.java) | [test](./creational/src/test/java/bg/hristomanov/education/patterns/creational/CreationalPatternsTest.java) |
| Factory Method | [`PaymentProcessorCreator.java`](./creational/src/main/java/bg/hristomanov/education/patterns/creational/factorymethod/PaymentProcessorCreator.java) | [test](./creational/src/test/java/bg/hristomanov/education/patterns/creational/CreationalPatternsTest.java) |
| Abstract Factory | [`CommerceFactory.java`](./creational/src/main/java/bg/hristomanov/education/patterns/creational/abstractfactory/CommerceFactory.java) | [test](./creational/src/test/java/bg/hristomanov/education/patterns/creational/CreationalPatternsTest.java) |

## Structural

| Pattern | Основен код | Test |
| --- | --- | --- |
| Adapter | [`LegacyCourierAdapter.java`](./structural/src/main/java/bg/hristomanov/education/patterns/structural/adapter/LegacyCourierAdapter.java) | [`StructuralPatternsTest.java`](./structural/src/test/java/bg/hristomanov/education/patterns/structural/StructuralPatternsTest.java) |
| Facade | [`CheckoutFacade.java`](./structural/src/main/java/bg/hristomanov/education/patterns/structural/facade/CheckoutFacade.java) | [test](./structural/src/test/java/bg/hristomanov/education/patterns/structural/StructuralPatternsTest.java) |
| Decorator | [`NotificationSenderDecorator.java`](./structural/src/main/java/bg/hristomanov/education/patterns/structural/decorator/NotificationSenderDecorator.java) | [test](./structural/src/test/java/bg/hristomanov/education/patterns/structural/StructuralPatternsTest.java) |
| Proxy | [`CachingProductCatalogProxy.java`](./structural/src/main/java/bg/hristomanov/education/patterns/structural/proxy/CachingProductCatalogProxy.java) | [test](./structural/src/test/java/bg/hristomanov/education/patterns/structural/StructuralPatternsTest.java) |
| Composite | [`Bundle.java`](./structural/src/main/java/bg/hristomanov/education/patterns/structural/composite/Bundle.java) | [test](./structural/src/test/java/bg/hristomanov/education/patterns/structural/StructuralPatternsTest.java) |

## Behavioral

| Pattern | Основен код | Test |
| --- | --- | --- |
| Strategy | [`DiscountCalculator.java`](./behavioral/src/main/java/bg/hristomanov/education/patterns/behavioral/strategy/DiscountCalculator.java) | [`BehavioralPatternsTest.java`](./behavioral/src/test/java/bg/hristomanov/education/patterns/behavioral/BehavioralPatternsTest.java) |
| Observer | [`OrderEventPublisher.java`](./behavioral/src/main/java/bg/hristomanov/education/patterns/behavioral/observer/OrderEventPublisher.java) | [test](./behavioral/src/test/java/bg/hristomanov/education/patterns/behavioral/BehavioralPatternsTest.java) |
| Chain | [`OrderValidationHandler.java`](./behavioral/src/main/java/bg/hristomanov/education/patterns/behavioral/chain/OrderValidationHandler.java) | [test](./behavioral/src/test/java/bg/hristomanov/education/patterns/behavioral/BehavioralPatternsTest.java) |
| State | [`Order.java`](./behavioral/src/main/java/bg/hristomanov/education/patterns/behavioral/state/Order.java) | [test](./behavioral/src/test/java/bg/hristomanov/education/patterns/behavioral/BehavioralPatternsTest.java) |
| Template Method | [`AbstractOrderImportJob.java`](./behavioral/src/main/java/bg/hristomanov/education/patterns/behavioral/templatemethod/AbstractOrderImportJob.java) | [test](./behavioral/src/test/java/bg/hristomanov/education/patterns/behavioral/BehavioralPatternsTest.java) |
| Command | [`Command.java`](./behavioral/src/main/java/bg/hristomanov/education/patterns/behavioral/command/Command.java) | [test](./behavioral/src/test/java/bg/hristomanov/education/patterns/behavioral/BehavioralPatternsTest.java) |

---

# Как да учим проекта

Препоръчаният ред не е „прочети всички дефиниции“.

За всеки pattern:

```text
1. прочети проблема
2. виж naive/bad формата
3. познай кое точно варира
4. отвори production-like кода
5. пусни теста
6. сравни със сходния pattern
7. намери аналогия в Spring/твоя код
8. реши кога НЕ би го използвал
```

## Препоръчан ред

### Първо

1. Strategy
2. Adapter
3. Facade
4. Builder

Те се разпознават най-лесно в application code.

### После

5. Observer
6. Chain of Responsibility
7. Decorator
8. Proxy

Тук започва по-интересната composition/framework част.

### След това

9. State
10. Template Method
11. Command
12. Factory Method
13. Abstract Factory
14. Composite
15. Singleton

Singleton е нарочно по-назад — лесен е като syntax, но често се използва там, където dependency injection е по-добрият design.

---

# Най-важните сравнения

Отвори [`PATTERN-COMPARISONS.md`](./PATTERN-COMPARISONS.md).

Там са подробно:

- Strategy vs State;
- Adapter vs Facade vs Decorator vs Proxy;
- Observer vs pub/sub;
- Factory Method vs Abstract Factory vs Simple Factory;
- Template Method vs Strategy;
- Decorator vs Chain of Responsibility.

Точно тези граници са по-важни от memorization на definition.

---

# Как това се връзва със Spring

Отвори [`SPRING-MAPPING.md`](./SPRING-MAPPING.md).

Няколко ключови примера:

- Spring `singleton` bean scope **не е идентичен** с GoF Singleton;
- Spring MVC има реален `HandlerAdapter`;
- Spring AOP е proxy-based;
- `@Transactional`, `@Cacheable`, `@Async` разчитат на proxy semantics;
- `ApplicationEventPublisher` / `@EventListener` дават observer-like in-process events;
- lists от еднакви Spring beans много естествено реализират Strategy registry или Chain.

---

# Pattern ≠ цел

Най-опасният момент при учене на patterns е:

> „Научих Decorator. Къде да го използвам?“

Това обръща причинно-следствената връзка.

Правилният въпрос е:

> „Имам object със стабилен contract, но искам optional/composable behavior около него. Decorator ли е добра форма на проблема?“

## Patternitis

Сигнали за overengineering:

- interface с една implementation без реална variation point;
- Factory, която само прави `return new X()`;
- Builder за record с две полета;
- Observer за operation, която всъщност изисква synchronous success/failure contract;
- State hierarchy за два boolean-а;
- Command objects, които никога няма да бъдат queued/retried/audited;
- global Singleton вместо normal injected dependency.

Patterns намаляват complexity само когато **структурната им цена е по-малка от problem complexity-то, което премахват**.

---

# Как да стартираме

От root:

```bash
mvn -f design-patterns/pom.xml test
```

Само категория:

```bash
mvn -pl design-patterns/creational test
mvn -pl design-patterns/structural test
mvn -pl design-patterns/behavioral test
```

Тестовете доказват semantics, а не само compilation.

---

# Какво точно решихме

В началото design patterns можеха да изглеждат като:

```text
име + UML + definition
```

Този проект ги превръща в:

```text
production problem
→ naive code shape
→ variation point
→ pattern
→ Java implementation
→ test
→ Spring analogy
→ trade-offs
→ similar-pattern comparison
```

Това е по-близо до начина, по който реално ги използваме като програмисти.

---

# Mental model за запомняне

1. **Pattern е име на повтарящ се design problem + proven structure, не готов code snippet.**
2. **Не започвай от pattern-а. Започвай от variation/problem-а.**
3. **Най-добрият знак, че си разбрал pattern, е да можеш да кажеш и кога НЕ го искаш.**
4. **В Spring използваш много patterns през framework-а, дори без да ги имплементираш ръчно.**

---

# Практичен checklist за code review

```text
[ ] Кое точно нещо в този код варира?
[ ] Variation-ът compile-time ли е или runtime?
[ ] Имаме ли growing switch/if tree?
[ ] Consumer-ът знае ли твърде много за object construction?
[ ] Изтича ли third-party model в domain-а?
[ ] Един service знае ли твърде много subsystem details?
[ ] Имаме ли cross-cutting behavior, който може да бъде wrapper/proxy?
[ ] Имаме ли lifecycle rules, разпръснати в if/switch?
[ ] Имаме ли pipeline от validators/filters?
[ ] Има ли side effects, които могат да бъдат observers?
[ ] Pattern-ът намалява ли coupling, или само добавя класове?
[ ] Има ли по-прост language/framework feature?
[ ] Ясно ли е как pattern-ът се тества?
```

---

# Оригинални източници

- ForrestKnight — **7 Design Patterns EVERY Developer Should Know**:  
  https://www.youtube.com/watch?v=BJatgOiiht4
- Gamma, Helm, Johnson, Vlissides — **Design Patterns: Elements of Reusable Object-Oriented Software**
- Refactoring.Guru — Design Patterns:  
  https://refactoring.guru/design-patterns
- Spring Framework — Bean Scopes:  
  https://docs.spring.io/spring-framework/reference/core/beans/factory-scopes.html
- Spring Framework — AOP Proxying:  
  https://docs.spring.io/spring-framework/reference/core/aop/proxying.html
- Spring Framework — ApplicationContext events:  
  https://docs.spring.io/spring-framework/reference/core/beans/context-introduction.html
- Spring MVC — Special Bean Types / HandlerAdapter:  
  https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-servlet/special-bean-types.html
