# Design Patterns — diagnostic scenario bank (без готови отговори)

Тези казуси проверяват разпознаване на **проблем**, избор **между алтернативи** и доказване. Задавай **един въпрос наведнъж** по [Assessment Protocol](../docs/learning/ASSESSMENT-PROTOCOL.md). След отговор — две независими оценки, техническа и професионален изказ; не започвай автоматично следващия. Не показвай pattern name или отговор преди опита на обучавания.

За всеки въпрос изискваме:
1. Кой точно invariant/variation се променя и какъв е проблемът?
2. Кой е най-простият работещ дизайн?
3. Кой pattern може да помогне **само при нужда**? Кога не?
4. Какъв negative/edge test ще докаже, че не сме влошили поведението?

## Junior → Mid (разбиране на причината)

### DP-Q01 — Цена по тип клиент
```java
BigDecimal price(Customer c, BigDecimal base) {
  if (c.isVip()) return base.multiply(new BigDecimal("0.9"));
  return base;
}
```
Промените са рядкост, а има точно два типа. Трябва ли нов interface с две реализации? Какво би променило решението ти?

### DP-Q02 — Превод на чужд договор
Услугата работи със `ShippingQuote`, но нов courier връща `getAmountStotinki()` и `getDeliveryZoneCode()`. Къде трябва да стоят unit conversion, provider error mapping и retries? Кой не трябва да знае чуждите типове?

### DP-Q03 — Проста верига
Преди запис на отчет изпълняваме validation, ACL authorization, ownership check и business invariant. Кога една подредена колекция от handlers е добра идея и как проверяваме, че след reject няма side effects?

### DP-Q04 — Случай с null
Липсващ billing provider може ли безопасно да се замени с no-op? Как различаваш „няма задължително действие“ от „разрешено е да няма действие“?

## Mid (pattern selection)

### DP-Q05 — Същият interface, различна цел
Един wrapper добавя metrics, втори кешира отговор, а трети превежда legacy формат. Как би назовал отговорностите и защо еднакъв синтаксис на delegate **не** означава еднакъв pattern?

### DP-Q06 — Lifecycle vs algorithm
`RiskReport` има `DRAFT → WAITING_SIGNATURE → SIGNED`; policy за export е `CSV/PDF`. От какво зависи изборът във всеки случай? Кой е owner на allowed transitions? Кога enum/switch е по-лекото решение?

### DP-Q07 — Factory terminology
Една `ReportRendererFactory.create(format)` съдържа `switch`. Колегата я нарича Factory Method. Как би изяснил точното понятие, без да пренаписваш код само за да „спазиш патерна“?

### DP-Q08 — Spring proxy
`@Transactional` method извиква друг `@Transactional(REQUIRES_NEW)` method върху `this`. Какво се случва с boundary и защо? Как би го тествал без фалшива транзакция?

## Early Senior (failure modes, trade-offs)

### DP-Q09 — Events vs reliable delivery
След `@EventListener` имейлът е изпратен, но DB commit-ът е rollback-нат. Достатъчен ли е Observer? Какво точно би променил в реакцията, transaction phase и delivery guarantees?

### DP-Q10 — Code review на Facade
`CheckoutFacade` има 40 зависимости, транзакции, HTTP calls, retries, rules и notification handling. Как разбираш дали е удобно use-case API или превърнат в God Object?

### DP-Q11 — Runtime identity и shared state
Има `enum Singleton` с mutable tenant configurations. Защо това е проблем при concurrent requests/tests? Как се различава от injected singleton-scoped Spring bean?

### DP-Q12 — Pattern или feature на езика
Йерархията `sealed interface Payment` има 3 подтипа и 1 operation; предлагат Visitor. Как би сравнил Visitor с exhaustive switch/pattern matching и какво става, когато се добавя нов подтип?

### DP-Q13 — Two-dimensional change
Изпращаме `ALERT/REMINDER` чрез `EMAIL/SMS/PUSH`. В кои случаи са оправдани две ортогонални абстракции, а в кои случаи само ще умножим класовете?

### DP-Q14 — Idempotency ≠ Cache/Decorator
POST към external archive връща timeout след реално изпълнен запис. Client retry прави duplicate record. Кое е correctness проблемът? Защо cache proxy или audit decorator сами не решават duplicate side effects?

## Групов checkpoint (4–8 микро-теми)

Предостави `LegacyReportWorkflow` от [Spring Boot Capstone](mastery-lab/README.md). Изискай:
- failure inventory преди refactor;
- 2 варианта (минимален patch / compositional design);
- собствен unit test за негативен сценарий;
- професионално обяснение на избора и най-големия компромис.

**Записвай само доказано знание.** При непроверен отговор: "непроверено"; не измисляй оценка за целия Design Patterns раздел.
