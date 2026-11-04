# P2 · Java 25 — Flexible Constructor Bodies (JEP 513)

> Devoxx 2026, Marit van Dijk & Piotr Przybył, [2:34:00–2:39:45](https://youtu.be/QBQ11aL2aZc?t=9240). **Java 25 stable**, без `--enable-preview`. Практическа лаборатория към [Data-Oriented Programming](README.md), не нов отделен Maven module.

## 1. Реален проблем: superclass получава невалидни данни

Изграждаме `SearchQueryBase`, който трябва да получи нормализиран текст. В Java преди 25 `super(...)` трябваше да е първата инструкция на constructor body; затова често местехме валидацията в изкуствен `static helper`, factory или superclass, което правеше control flow по-труден за четене. JEP 513 допуска **prologue** преди `super(...)` / `this(...)`, който няма достъп до още неинициализирания текущ обект.

## 2. README → код → тест

| Тема | Код | Доказателство |
| --- | --- | --- |
| Валидация преди parent constructor | [ValidatedSearchQuery.java](src/main/java/bg/hristomanov/education/dop/ValidatedSearchQuery.java) | [ValidatedSearchQueryTest.java](src/test/java/bg/hristomanov/education/dop/ValidatedSearchQueryTest.java) |
| Предотвратяване на blank/null/invalid limit | Същият class | Positive / negative JUnit 5 tests |

```java
public ValidatedSearchQuery(String rawText, int limit) {
    String normalized = Objects.requireNonNull(rawText).strip();
    if (normalized.isBlank() || limit <= 0) {
        throw new IllegalArgumentException("Invalid search query");
    }
    super(normalized);       // valid Java 25
    this.limit = limit;      // epilogue, object initialization is allowed here
}
```

Истинският код проверява и `limit <= 100`; не разчитай само на това съкратено обяснение.

```bash
mvn -pl java/language/data-oriented-programming -Dtest=ValidatedSearchQueryTest test
```

## 3. Кое НЕ е позволено в prologue-а

### BAD: reference към `this` преди `super`

```java
// НЕ компилирай с основния проект — нарочно невалиден source snippet.
class Broken extends Parent {
    Broken(String text) {
        this.validate(text); // illegal: current instance not initialized yet
        super(text);
    }
    void validate(String text) { }
}
```

Не извиквай instance method или чети instance state на текущия обект в early construction context. Можеш да обработваш параметри, локални променливи, извиквания към static helpers и да хвърляш exceptions. Извикването на `this(...)` или `super(...)` трябва да остане задължителният explicit constructor invocation, а не да бъде поставяно в непозволена conditional branch. Подробните правила и новите JDK28 strict-field-initialization preview ограничения са версия-зависими — не ги смесвай с Java25.

## 4. Decision contract

| Въпрос | Решение |
| --- | --- |
| Проблем | Неясна валидация/нормализация, скрита в helper и извикана от `super`. |
| Кога | Сложен pre-parent validation flow или нормализация, който е естествено част от subtype constructor. |
| Кога НЕ | Прост constructor без наследяване, record compact constructor (има собствен контракт), JPA entity proxy/generation без нужда. |
| Печалба | Четим lifecycle и ясна граница „валидирай → инициализирай parent → subclass“. |
| Цена | Повече правила за early construction; не може да използваш `this` преди parent. |
| По-просто | Static validating helper / factory, нормализация в parent, record compact constructor при DTO. |
| Доказателство | [Java25 JUnit](src/test/java/bg/hristomanov/education/dop/ValidatedSearchQueryTest.java) за положителни и отрицателни случаи. |

**Правило:** Ако трябва да валидираш arguments преди parent construction, избери Java 25 constructor prologue. Ако не трябва — запази простото `super(...)`. Не прави през prologue тежко blocking I/O или скрити side effects.

## Оригинални източници

- [Devoxx, 2:34:00](https://youtu.be/QBQ11aL2aZc?t=9240).
- [JEP 513](https://openjdk.org/jeps/513).
- [Java SE 25 Flexible Constructor Bodies](https://docs.oracle.com/en/java/javase/25/language/flexible-constructor-bodies.html).
- [Съществуващ Concept Note](../../../docs/learning/concepts/11-abstract-classes-interfaces-default-methods.md).
