# P2 — Architecture Decision-Making: колко архитектура действително ни е нужна?

> **Допълнение**, не нов курс по Hexagonal Architecture. Източник: [Ties van de Ven — The Hexagonal Hangover, Devoxx Belgium 2026](https://www.youtube.com/watch?v=m9viRAIRAUQ).  
> **За кого:** Java/Spring Backend, Mid+ → Early Senior. **Приоритет:** P2 — инженерна преценка при проектиране и code review.  
> **Предварителна основа:** [Ports & Adapters / Hexagonal Architecture](./README.md). Не добавяме дублиращ Maven module, защото основните ports, adapters и тестове вече съществуват.  
> **Основен въпрос:** „Каква конкретна промяна или риск оправдава цената на следващата абстракция?“

## 1. Входен production казус: добавяме архитектура „за всеки случай“

Имаме Spring Boot приложение с REST endpoint за поръчки, service и Spring Data JPA repository. Има един екип, една база и прости бизнес правила.

Някой предлага:

```text
HTTP Controller
  → HTTP request mapper
    → Input port interface
      → Application use-case implementation
        → Output port interface
          → Persistence mapper
            → JPA adapter
              → Spring Data Repository
```

Предложението **не е грешно по дефиниция**: може да изолира домейн кода, да осигури ясна compile-time граница и да улесни различни входни канали. Но за този конкретен случай трябва да попитаме какво печелим **сега** и какво ни струва да поддържаме допълнителните класове, мапери и зависимости.

Това е „махмурлукът“ от заглавието на лекцията: заради страха от бъдещи промени въвеждаме толкова много защитни слоеве, че настоящата задача става по-трудна за разбиране.

Лекторът дава показателен разказ: проект с множество вътрешни абстракции и message queues е решавал сравнително проста задача — препращане на два вида Kafka съобщения. По-простата реализация е била много по-бърза за завършване, но с по-ограничена бъдеща extensibility. **Това е илюстрация на trade-off, а не доказателство, че Kafka abstraction или generalization винаги са лоши.**

## 2. Четири критерия за преценка

| Критерий | Какво означава | Практически въпрос |
| --- | --- | --- |
| **Cohesion** (свързаност по отговорност) | Доколко нещата в един компонент обслужват обща смислена задача | „Защо тези операции се променят заедно?“ |
| **Coupling** (зависимост) | Как и доколко компонентите разчитат на други компоненти/договори | „Ако сменя това, кой още ще трябва да променя?“ |
| **Distance** (архитектурна дистанция) | Колко граници/транслации минаваме, за да проследим един use case | „Колко места трябва да отворя, за да разбера как работи?“ |
| **Volatility** (честота и характер на промените) | Кои части вероятно ще се променят и колко скъпи ще са тези промени | „Срещу каква реалистична промяна се защитаваме?“ |

Това е **ментален модел**, не готова математическа функция за „качество на архитектурата“. Лекторът описва coupling през две измерения — depth и connectivity — и свързва архитектурната болка с volatility. Ползвай го като въпросник за решения, не като общоприета научна формула или автоматична оценка на repository.

## 3. Cohesion: не само „всичко е свързано с Order“

Лекцията описва прогресия от accidental cohesion („сложихме го в `utils`“) през logical cohesion („всички controllers на едно място“) и model cohesion („всичко около `Order`“) към functional cohesion (операции около конкретна бизнес цел).

```text
technical grouping:
  controller/
  service/
  repository/

business-oriented grouping:
  order/placement/
  order/cancellation/
  order/inquiry/
```

Вторият вариант може да подобри local reasoning (разбиране на конкретна функционалност, без да разглеждаш цялата система). Но **functional cohesion не означава един class и една папка за всеки метод**: операции, които поддържат общи invariants и еднакъв lifecycle, често трябва да останат заедно.

Имена на директории не гарантират правилна граница. Провери реалното поведение: бизнес правила, промени, транзакции, ownership и тестове.

## 4. Coupling: явни и скрити зависимости

Не всички зависимости се виждат като `import` или constructor parameter.

- **Явна compile-time dependency:** `OrderApplicationService` извиква `ProductCatalogPort`. Виж [реалния application service](./core/src/main/java/bg/hristomanov/education/hexagonal/application/OrderApplicationService.java).
- **Скрита functional dependency:** клас A записва mutable global state, а клас B го чете; промяна в A чупи B без очевидна source dependency.
- **Temporal coupling** (зависимост от реда/времето): един процес първо трябва да запише файл/съобщение, иначе другият не може да работи.
- **Contract coupling** (зависимост през определен договор): типизирано API, съобщение със schema, HTTP endpoint с дефинирани параметри и очаквано поведение.

Проблеми при contracts: подписът на метод или интерфейс рядко описва изцяло бизнес семантика, latency/timeout, consistency, authorization и failure behavior. **Contract tests**, versioning и explicit nonfunctional expectations помагат да покрием тези пропуски.

### Защо Java interface невинаги е необходим — но невинаги е излишен

Лекторът предупреждава за „interface за всичко“. Това е полезно, но твърдението „интерфейсът не добавя договор“ е прекалено силно, ако се тълкува буквално.

Интерфейсът:

- **дефинира type-level contract** и налага съвместими method signatures при компилация;
- може да отдели compile-time dependencies: application/core да знае port contract, но не и JPA implementation;
- позволява повече от една имплементация, без това да е задължително условие за смислен port.

Същевременно **не** гарантира поведенческа substitutability, runtime correctness, latency или пълна portability. Ако `OrderService` и `OrderServiceImpl` са в един модул, има само един реален consumer и няма нужда от boundary, добавеният interface може да не носи достатъчно стойност.

**Правилният въпрос не е „колко реализации имаме?“, а „каква независима граница искаме да защитим?“**

### Слаб/силен договор: реалният конфликт

Ако много consumers използват един API, всеки допълнителен обещан детайл ограничава бъдещите промени на provider-а. От гледна точка на consumer-а обаче повече предвидими гаранции намаляват риска. Това е реално напрежение, не задача с универсално решение.

Проектиране: експонирай **минималния необходим public surface**, но документирай **достатъчно силни семантични гаранции** за безопасно ползване. „Най-малък договор“ не означава „най-неясен договор“.

## 5. Distance: локална яснота срещу глобална сложност

При отделяне на controller, mapper, use-case, port и adapter всяка част може да стане по-лесна за четене поотделно. Но да разбереш един request end-to-end означава да минеш през още файлове, скокове между интерфейси и преобразувания на модели.

```text
Печалба: локално разбиране + ограничаване на въздействието на промяната
Цена: повече навигация + mapping + повече код за поддръжка
```

Дистанцията между два метода в един class не е еквивалентна на дистанция между модули, екипи или remote services. В последния случай добавяме latency, failure modes, distributed consistency и deployment coordination.

Не оптимизирай само „малко класове“. Оптимизирай **разбираем и стабилен поток на една бизнес операция**, без да излагаш ненужно вътрешни детайли.

## 6. Volatility: къде е оправдана инвестицията

Лекторът поставя **промените** в центъра на архитектурната преценка. Практически:

- Ако външен доставчик променя API често, adapter/ACL може да спести много координирани промени.
- Ако екранът просто показва таблица, няколко mapping слоя може да са излишни.
- Ако една domain rule е стабилна, но критична, тя **пак** може да заслужава ясна граница, силни инварианти, security и тестове — дори да се променя рядко.

Затова оценяваме не само **вероятността от промяна**, а и **последствията от грешка**, compliance, correctness, ownership и цената на поддръжката. Твърдението „не се променя, значи няма нужда от архитектура“ не е общовалидно.

---

# Два практически code-review казуса

## 7. Казус A — малък Spring Boot CRUD

**Контекст:** административна функционалност: две–три JPA entities, един REST endpoint family, една стабилна база, малко бизнес правила, един екип.

**Предложение на review:** да отделим `core`, `port-in`, `port-out`, REST adapter, JPA adapter и два допълнителни DTO mapper-а.

**Проверка на решението:**

1. **Cohesion:** операциите са прости и принадлежат на една смислена функционалност; могат да останат в един feature package.
2. **Coupling:** `@Service` → `JpaRepository` е съзнателна и евтина зависимост за този scope.
3. **Distance:** отделните ports и mapping paths увеличават навигацията повече, отколкото изолират реални промени.
4. **Volatility:** няма установена нужда от алтернативни входове, различни persistence providers или независимо развиващ се domain core.

**Решение:** за момента pragmatic layered/feature-based структура:

```text
order/
  OrderController
  OrderService
  OrderRepository
  OrderEntity
  OrderResponse
```

`OrderResponse` има смисъл като API contract, ако не искаме persistence entity да изтича към клиентите. Това **не задължава** въвеждане на отделен domain entity с допълнителен mapper.

**Какво би променило решението?** Нарастваща бизнес логика, няколко inbound channels, интеграции, нестабилен schema/provider, независимо тестван core, зависимости между teams или доказани проблеми при промени. Тогава добави конкретна boundary постепенно, а не прави rewrite „за да стане hexagonal“.

**Code-review заключение:** „При сегашните изисквания интерфейсите и допълнителните mappings нямат ясна възвръщаемост. Ще запазим отделен HTTP response contract и ще въведем port, когато се появи конкретна integration/domain boundary.“

## 8. Казус B — поръчки и нестабилен външен каталог

**Контекст:** поръчката се изчислява по product data. Днес информацията идва от локална JPA таблица, но нов външен catalog API може да замени/допълни източника. Product provider е друг owner и има собствено schema и release cadence.

В нашия lab вече има:

- [`ProductCatalogPort`](./core/src/main/java/bg/hristomanov/education/hexagonal/port/out/ProductCatalogPort.java) — contract, owned от core;
- [`OrderApplicationService`](./core/src/main/java/bg/hristomanov/education/hexagonal/application/OrderApplicationService.java) — business logic, което не знае JPA/HTTP;
- [`JpaProductCatalogAdapter`](./adapter-out-jpa/src/main/java/bg/hristomanov/education/hexagonal/adapter/out/jpa/JpaProductCatalogAdapter.java) — adapter, който превежда JPA data към наш `Product`;
- [`UseCaseConfiguration`](./bootstrap/src/main/java/bg/hristomanov/education/hexagonal/bootstrap/UseCaseConfiguration.java) — composition root.

Core contract-ът реално изглежда така:

```java
public interface ProductCatalogPort {
    Optional<Product> findBySku(String sku);
}
```

Това е **съкратен откъс** от съществуващия interface (в проекта има и `getRequired` default method).

**Проверка на решението:**

1. **Cohesion:** `place order` трябва да решава какво е валидна поръчка, а не да познава JSON schema на каталог.
2. **Coupling:** външният provider може да промени полета, DTO и API; adapter + translation ограничават влиянието към core-а, **ако необходимата информация остава налична**.
3. **Distance:** имаме допълнителен port/adapter, но проследяването на request flow е предвидимо и този слой защитава реален риск.
4. **Volatility:** integration contract може да се променя независимо; отделянето има конкретна полза.

**Решение:** `ProductCatalogPort` е оправдан. При нов provider добавяме **нов adapter**, contract/integration tests и wiring; не редактираме `OrderApplicationService`, докато domain нуждата му остава същата.

**Граница на защитата:** ако външният provider изобщо премахне необходимата цена или даде данни с различна бизнес семантика, mapper не може магически да възстанови информацията. Тогава са нужни domain policy, fallback/availability решение или промяна на contract-а.

**И още един trade-off:** наличието на един оправдан port **не означава**, че целият малък project трябва непременно да се раздели на четири Maven modules. Lab-ът демонстрира максимално видима boundary за учебни цели; production може да използва по-лека организация със същото dependency правило.

**Code-review заключение:** „Този port защитава конкретна нестабилна външна интеграция и позволява core-ът да остане независим от data source. Запазваме го, но избягваме допълнителни празни wrapper-и.“

---

## 9. Decision checklist, който можеш да приложиш утре

Преди нов interface, layer, module или service, отговори:

- [ ] **Проблем:** коя текуща болка или реалистична промяна адресираме?
- [ ] **Cohesion:** остава ли бизнес отговорността ясна или я разпръскваме?
- [ ] **Coupling:** кое source/runtime/semantic coupling прекъсваме? Кое остава?
- [ ] **Distance:** колко нови hop-ове, mappings и ownership граници въвеждаме?
- [ ] **Volatility:** колко често се променя тази граница? Колко скъпо е да се счупи?
- [ ] **Contract:** имаме ли точна семантика, error cases, timeouts, versioning?
- [ ] **Alternatives:** достатъчни ли са един class, feature package, един adapter или директно Spring Data?
- [ ] **Evidence:** как ще сравним промени в код, testability, blast radius и debugging?
- [ ] **Exit strategy:** можем ли да добавим boundary по-късно без голям rewrite?
- [ ] **Decision:** какво избираме сега и кога бихме преразгледали избора?

### Минимален Architecture Decision Record (ADR)

```markdown
## Context
Какъв use case и каква промяна/риск имаме?

## Options
A: директен service + repository
B: конкретен port + adapter
C: отделен модул / пълна hexagonal boundary

## Decision
Избираме B заради нестабилния външен Product Catalog API.

## Consequences
+ Независим core и localized mapping
- Повече interfaces/tests/wiring

## Revisit when
Появят се допълнителни adapters, нови domain invariants
или mapper/adapter complexity надхвърли ползата.
```

## 10. Какво да не запомняме като догма

| Прекалено общо твърдение | По-точно инженерно правило |
| --- | --- |
| „Всеки interface е overengineering.“ | Interface е полезен при смислена type/dependency boundary, включително с една реализация. |
| „Hexagonal е винаги по-добра.“ | Избирай архитектура по промени, критичност, coupling, testability и цена. |
| „Ако кодът не се променя, архитектурата няма стойност.“ | Correctness, reliability, security и auditability са важни и при стабилен код. |
| „Mapper ни защитава от всяка upstream промяна.“ | Mapper покрива representational changes, не гарантира възстановяване на загубена информация. |
| „Повече abstraction винаги намалява coupling.“ | Може да премести dependency и да увеличи distance и support cost. |
| „Две classes се променят заедно, значи има лоша граница.“ | Co-change е сигнал за проверка, не окончателно доказателство. |

## 11. За преговор и технически изказ

**Професионална формулировка:**

> „Не оценявам архитектурата по броя слоеве или наличието на интерфейси. Първо определям business responsibility и очакваните промени; после анализирам coupling, contract guarantees, distance и цената на поддръжката. Избирам най-простата архитектура, която защитава действителните инварианти и рискове, и документирам при какви условия ще я променим.“

**Самопроверка (не е проведена диагностика):** защити с 2–3 изречения защо `OrderServiceImpl` може да е излишен interface, а `ProductCatalogPort` полезен; обясни какво става, ако доставчикът премахне информация, която изисква бизнес логиката; сравни цена на промяна при директен JPA read и при пълен 7-стъпков adapter/port call chain.

## 12. Връзка с изпълнимите лаборатории и източници

Този документ **не добавя нов код и не твърди, че е изпълнил тестове**. Използвай съществуващия [Hexagonal lab](./README.md) и сравни алтернативите в [упражненията му](./README.md#упражнения). За теми с повече от един bounded context виж [Strategic DDD / Context Mapping](../strategic-ddd/README.md), а за pragmatically enforced modules — [Spring Modulith](../strategic-ddd/05-SPRING-MODULITH.md).

Оригинални източници:

- [Ties van de Ven — The Hexagonal Hangover, Devoxx 2026 (видео)](https://www.youtube.com/watch?v=m9viRAIRAUQ). Лекторският модел е **качествена евристика**, не доказана числова метрика.
- [Alistair Cockburn — Hexagonal Architecture (оригинална концепция)](https://alistair.cockburn.us/hexagonal-architecture).
- [Martin Fowler — Refactoring, code smells & architecture](https://martinfowler.com/refactoring/).
- [Spring Framework — Dependency Injection](https://docs.spring.io/spring-framework/reference/core/beans/dependencies/factory-collaborators.html).
- [Jakarta Persistence specification](https://jakarta.ee/specifications/persistence/3.2/jakarta-persistence-spec-3.2).

**Резюме:** не изграждай maximum architecture. Изграждай **достатъчна архитектура за известните и правдоподобните рискове**, така че следващият човек да може да проследи целия use case и да го промени безопасно.

