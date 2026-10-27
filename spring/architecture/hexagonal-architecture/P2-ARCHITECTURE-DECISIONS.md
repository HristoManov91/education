# P2 — Architecture Decision-Making: кога абстракцията си струва?

> **Допълваща глава**, не нов курс. Източник: [Ties van de Ven — The Hexagonal Hangover, Devoxx Belgium 2026](https://www.youtube.com/watch?v=m9viRAIRAUQ).  
> **Ниво:** Mid+ → Early Senior Java/Spring Backend. **Приоритет:** P2.  
> **Предварителен материал:** [Ports & Adapters / Hexagonal Architecture](./README.md). Вече имаме runnable Java 25/Spring Boot lab; тук учим **как да решим дали изобщо ни трябва такава структура**.

## 1. Входен казус — „да го направим hexagonal за всеки случай“

Имаме Spring Boot CRUD функционалност: REST, service, JPA repository, една база и един екип. Предлагат да добавим:

```text
Controller → Request Mapper → Input Port → Use Case
→ Output Port → Domain/JPA Mapper → JPA Adapter → Repository
```

Това **може** да предпази бизнес логиката от инфраструктурата. Но вече имаме повече класове, транслации и навигация. Ако очакваната промяна никога не настъпи, плащаме цената без ползата.

Лекторът описва подобна крайност: сложен Kafka проект с множество абстракции, чиято реална задача е била препращане на два типа съобщения. По-простото решение се оказало по-бързо за реализация, но по-малко разширяемо. Това е **trade-off** (компромис между предимства и цена), не доказателство срещу архитектурните слоеве.

**Основен въпрос:** коя конкретна промяна, зависимост или риск оправдава допълнителната абстракция?

## 2. Четирите измерения от лекцията

| Понятие | Какво оценяваме | Въпрос за code review |
| --- | --- | --- |
| **Cohesion** (вътрешна свързаност) | Дали компонентът обслужва една смислена отговорност | „Кое принадлежи и се променя заедно?“ |
| **Coupling** (зависимости) | Как промяна в A влияе върху B | „Кой ще трябва да се промени, ако сменим API/модел?“ |
| **Distance** (дистанция) | Колко граници и преобразувания минава една операция | „Колко файла/системи отварям, за да проследя request?“ |
| **Volatility** (изменчивост) | Вероятност и цена на бъдещи промени | „Какво се променя често или е критично да не се счупи?“ |

Лекторът представя coupling чрез **depth × connectivity** и говори за архитектурната болка като функция на лоша структура и честота на промени. Това е **качествен mental model**, а не общоприета измерима формула. Не присвоявай произволни числови оценки, за да „докажеш“ решение.

### Cohesion: групирай по бизнес отговорност

```text
technical packages:   controllers / services / repositories
business packages:    orders / payments / catalog
use-case focus:       place order / cancel order / purchase inquiry
```

В лекцията това преминава от accidental („всичко в `utils`“) през logical/model към functional cohesion (организация около бизнес действие). Но **functional cohesion ≠ class за всеки метод**. Операции, които поддържат общи invariants и транзакция, могат да останат заедно. Папките сами по себе си не гарантират правилни boundaries.

### Coupling: видимите imports са само част от проблема

Виждаме compile-time dependency, когато `OrderApplicationService` използва [`ProductCatalogPort`](./core/src/main/java/bg/hristomanov/education/hexagonal/port/out/ProductCatalogPort.java). Но има и скрито coupling:

- shared mutable state, чиято промяна чупи други компоненти;
- temporal coupling (например consumer очаква файл/съобщение да е готово преди да стартира);
- обща database schema или зависимост от конкретната бизнес семантика на външно API;
- отражение чрез reflection или downcast към конкретна implementation.

**Contract coupling** (зависимост чрез явен договор) може да направи промяната по-предвидима, но Java method signatures не описват цялото поведение, latency, transaction guarantees, security и failure semantics.

### Distance: локалното опростяване има глобална цена

Един mapper или port може да улесни разбирането на отделен component (local reasoning). Но целият request flow вече минава през повече стъпки (global cognitive load). Дистанция през class не е същото като през remote service: второто добавя networking failures, latency и deployment coordination.

### Volatility: пази нестабилните граници, не всяка възможна граница

Нестабилен външен API може да заслужава adapter; стабилен CRUD може да не заслужава отделни domain и persistence модели. Но „рядко се променя“ **не** означава „не е важно“: correctness, security, compliance и критични invariants също са причини да отделим отговорности.

## 3. Интерфейсът: полезен contract или ненужен wrapper?

Тук лекцията има силна практическа идея, но не бива да я превръщаме в догма.

**Излишна абстракция:** `OrderService` → `OrderServiceImpl` в един модул, един consumer, без друга dependency boundary; интерфейсът не добавя яснота или substitutability.

**Оправдан интерфейс:** `ProductCatalogPort` се дефинира в `core`, а [`JpaProductCatalogAdapter`](./adapter-out-jpa/src/main/java/bg/hristomanov/education/hexagonal/adapter/out/jpa/JpaProductCatalogAdapter.java) го реализира. Така [`OrderApplicationService`](./core/src/main/java/bg/hristomanov/education/hexagonal/application/OrderApplicationService.java) **не зависи от JPA implementation**, макар да има само един production adapter.

Интерфейсът дава реален **type-level/compile-time contract**, но не гарантира коректно поведение и не описва всички nonfunctional expectations. Следователно:

> **Не добавяме interface заради броя класове и не го премахваме само защото има една implementation. Избираме го според необходимата dependency boundary.**

За публичен API искаме **възможно най-малък необходим contract surface** (по-лесна еволюция за provider-а), но **достатъчно ясни behavioural guarantees** (за consumers). Минимален не означава неясен. Маперът може да изолира преименувано поле, но не може да възстанови премахната бизнес информация.

---

## 4. Казус A — малък CRUD с един екип

**Контекст:** 3 entities, стабилна база, REST, минимални бизнес правила. Предложение: отделни input/output ports, два adapter-а, domain/JPA model и mapper за всяка операция.

**Оценка:** слаба нужда от нова boundary; допълнителният distance/mapping надхвърля очакваната полза. Препоръчваме по-проста feature-based/layered структура:

```text
order/
  OrderController
  OrderService
  OrderRepository
  OrderEntity
  OrderResponse
```

`OrderResponse` все още е полезен за отделяне на HTTP contract от persistence модела. Отделен domain model обаче **не е автоматично нужен**.

**Кога преразглеждаме?** Появят се сложни business invariants, няколко inbound channels, независим домейн, реално нестабилна интеграция или проблем при change impact/testability.

**Примерен code-review отговор:** „Оставяме простата структура, защото не сме посочили конкретна промяна, която новите ports предотвратяват. При нов integration boundary ще я добавим целенасочено.“

## 5. Казус B — поръчка използва външен продуктов каталог

**Контекст:** днес цените идват от JPA, утре може да идват от външен Product Catalog с различен owner, schema и release cadence.

В [нашия executable lab](./README.md) тази граница вече съществува:

- [`ProductCatalogPort`](./core/src/main/java/bg/hristomanov/education/hexagonal/port/out/ProductCatalogPort.java) — нуждата на приложението: намиране на продукт по SKU;
- [`OrderApplicationService`](./core/src/main/java/bg/hristomanov/education/hexagonal/application/OrderApplicationService.java) — използва core-овия contract, не JPA/HTTP API;
- [`JpaProductCatalogAdapter`](./adapter-out-jpa/src/main/java/bg/hristomanov/education/hexagonal/adapter/out/jpa/JpaProductCatalogAdapter.java) — превежда persistence data в наш `Product`;
- [`UseCaseConfiguration`](./bootstrap/src/main/java/bg/hristomanov/education/hexagonal/bootstrap/UseCaseConfiguration.java) — избира реалния adapter.

Съкратен откъс от съществуващия port (в проекта има и default метод `getRequired`):

```java
public interface ProductCatalogPort {
    Optional<Product> findBySku(String sku);
}
```

**Оценка:** външният provider има независимо развитие. Port + adapter локализират representation changes и правят core тестируем без JPA/HTTP. **Цената** е допълнителен mapper и wiring. Тук има правдоподобна полза.

**Ограничение:** ако доставчикът премахне цена или промени значението ѝ, adapter не може магически да запълни missing information. Нужни са бизнес решение, нов contract или fallback. И един оправдан port **не налага** четири Maven modules в production — това е обучителен избор в lab-а.

**Примерен code-review отговор:** „Запазваме `ProductCatalogPort`, защото предпазва core от конкретен независим external API. Няма да добавяме допълнителни празни wrappers без доказана нужда.“

---

## 6. Decision checklist (запази за реален code review)

- [ ] Какъв **конкретен проблем или риск** адресира новата абстракция?
- [ ] Кои бизнес правила трябва да се променят **заедно**?
- [ ] Коя compile-time, runtime или semantic dependency **намаляваме**?
- [ ] Кои нови mappings, hops, interfaces и тестове ще поддържаме?
- [ ] Колко чести и скъпи са очакваните промени? Какво е **критично** дори при редки промени?
- [ ] Договорът описва ли errors, timeouts, данни и поведение, не само method signature?
- [ ] Има ли по-прост вариант — class, feature package, конкретен port, не цял framework?
- [ ] При какъв измерим сигнал ще **преразгледаме** решението?

### Малък Architecture Decision Record (ADR)

```markdown
## Context
Нестабилен Product Catalog provider; бизнес логиката не трябва да зависи от DTO schema.

## Options
A. Direct JPA/HTTP dependency
B. Core-owned ProductCatalogPort + adapter
C. Full new framework / generic integration layer

## Decision
B — изолираме точно външната зависимост.

## Consequences
+ Ясна граница, отделни adapter/contract tests
- Mapping и wiring разход

## Revisit
Ако contract-ът/semantics се променят или overhead-ът стане прекомерен.
```

## 7. Кои твърдения да НЕ приемаме буквално

| Опростено твърдение | Инженерно уточнение |
| --- | --- |
| „Java interface няма стойност.“ | Има compile-time contract и може да пази dependency direction, но не гарантира поведение. |
| „Hexagonal е най-добра винаги.“ | Избира се според coupling, промени, рискове и разходи. |
| „Стабилният код няма нужда от архитектура.“ | Critical correctness/security boundaries съществуват и при рядко променян код. |
| „Mapper изолира всяка промяна.“ | Изолира някои representational промени, не загуба на необходима информация. |
| „Classes се променят заедно → лош design.“ | Co-change е сигнал за проверка, не автоматично доказателство. |

**Професионален изказ:**

> „Оценявам архитектурните граници по cohesion, coupling, navigation cost и реалистичните промени. Избирам най-простата организация, която защитава нужните бизнес инварианти и интеграционни contracts, и записвам кога ще я преразгледаме.“

**Самопроверка (не е извършена диагностика):** защо интерфейсът `OrderService` може да е излишен пред `OrderServiceImpl`, а `ProductCatalogPort` — полезен? Защо преименувано поле и изцяло отпаднала цена не са еднаква upstream промяна? Как преценяваме дали повече слоеве са оправдани?

## 8. Източници и връзки

Това е **документално надграждане**; не добавя код или нови автоматични тестове. Практическата основа е [Hexagonal lab](./README.md); за по-сложни business boundaries виж [Strategic DDD](../strategic-ddd/README.md).

- [Оригиналното видео — Ties van de Ven, *The Hexagonal Hangover*](https://www.youtube.com/watch?v=m9viRAIRAUQ).
- [Alistair Cockburn — Hexagonal Architecture](https://alistair.cockburn.us/hexagonal-architecture).
- [Spring Framework — Dependency Injection](https://docs.spring.io/spring-framework/reference/core/beans/dependencies/factory-collaborators.html).
- [Martin Fowler — Refactoring](https://martinfowler.com/refactoring/).

**За запомняне:** достатъчна архитектура за известните и правдоподобните рискове, не максимална архитектура за всяко хипотетично бъдеще.

