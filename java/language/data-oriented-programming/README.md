# P1 · Data-Oriented Programming (Java 25)

> **Modern Java in Practice — The Features That Matter**, Devoxx Belgium 2026, Nicolai Parlog & Falk Sippach: [05:00–36:00](https://youtu.be/zC2tbq7S5UE?t=300).  
> **Baseline:** Java 25 LTS, stable language features, no preview flags.
>
> **Target:** strong Mid → Early Senior. 1.1 Fundamentals (25 min) + 1.2 Deep Dive (25 min) + runnable lab (20 min).

## Реален проблем: валидирана business data влиза през система, но може да е невалидна по дизайн

Система приема план, който съдържа няколко **алтернативни** типа dosage: таблетки (сутрин/обед/вечер) или инфузия (ml/min). Наивна Java entity държи *всички* полета едновременно:

```java
class LegacyDose {
    boolean infusion;
    int morningTablets;
    int eveningTablets;
    int millilitres;
    int minutes;
}
```

Какво означават едновременно `infusion=true` и `morningTablets=2`? Какво става, когато се добави трети тип? Всяка услуга има свои if-ове и някой лесно може да пропусне branch.

Това е **invalid state representable by the model**. DTO validation на входа помага, но не отстранява невъзможните комбинации от самата type system.

## 1.1 Основни понятия

| Идея | Java 25 механизъм | Коректен mental model |
| --- | --- | --- |
| **Product type** | `record Tablets(int morning, int noon, int evening)` | A **и** B **и** C заедно, прозрачно immutable state |
| **Sum type** | `sealed interface Dose permits Tablets, Infusion` | A **или** B, затворен набор от допустими форми |
| **Exhaustiveness** | `switch (dose)` без `default` | Компилаторът проверява всички permitted subtype-ове |
| **Record pattern** | `case Tablets(int morning, int noon, int evening)` | Type check + деконструкция на компонентите |
| **Guarded pattern** | `case Tablets(...) when ...` | Допълнителен predicate; след него нужен general case |
| **Validation at boundary** | Compact constructor | Валидните record instance-и се създават през constructor invariant |
| **Behavior separate from data** | `MedicationOperations` | Полезно при **стабилни алтернативи + много операции** |

### Изпълними класове

```text
Dose (sealed interface)
  ├── Tablets (record)
  └── Infusion (record)

Medication (record Drug + Dose)
Prescription (record List<Medication> с defensive copy)
MedicationOperations (switch expressions, guard, nested record pattern)
```

**Моделът е учебен — не използвай тези примерни стойности за медицинско решение.** Няма връзка с клинични протоколи.

## 1.2 Deep Dive — what gets enforced at compile time?

### BAD: default hides a newly added subtype

```java
return switch (dose) {
    case Tablets t -> "tablets";
    default -> "unknown";
};
```

Ако добавиш `Patch` към `permits`, compile-ът остава зелен, но вече реална бизнес операция може да стане "unknown" без review.

### GOOD: exhaustive switch без default

В [MedicationOperations.java](src/main/java/bg/hristomanov/education/dop/MedicationOperations.java):

```java
return switch (dose) {
    case Tablets(int morning, int noon, int evening) ->
            "tablets:" + morning + "/" + noon + "/" + evening;
    case Infusion(int ml, int minutes) ->
            "infusion:" + ml + "ml/" + minutes + "min";
};
```

Добави `record Patch(...) implements Dose` в упражнение и промени `permits`. Компилаторът ще поиска да обработиш новия подтип в `switch`. Това е **design-time** гаранция; не е runtime reflection, Mockito или "магьосване" в Spring.

### Guards и dominance

```java
case Tablets(int morning, int noon, int evening)
        when medication.drug() == Drug.CAFFEINE && morning > 2 -> true;
case Tablets(int morning, int noon, int evening) ->
        Math.addExact(Math.addExact(morning, noon), evening) > 3;
```

**Guarded** case не покрива всички Tablets; затова след него трябва общ case за останалите. Разменянето на реда ще доведе до dominance/неподходяща pattern ordering: unguarded case би покрил целия тип преди специалния.

### Null semantics

`switch (null)` хвърля NullPointerException, освен ако синтактично е обработен `case null`. Нашите constructors не допускат null за Medication/Dose, така че **не крий невалидния вход с "default"**.

### Records are not deeply immutable

Record има final reference към components, но `record Prescription(List<Medication>)`, който пази mutable ArrayList reference, може да бъде **mutated отвън**.

Нашата [Prescription.java](src/main/java/bg/hristomanov/education/dop/Prescription.java) използва `List.copyOf` в compact constructor. Това създава unmodifiable shallow snapshot; не прави immutable вътрешни mutable елементи. В конкретния модел елементите са immutable records и enum.

### Overflow/invariants

Таблетният compact constructor проверява nonnegative counts, nonzero total и използва `Math.addExact`, за да не превърне integer overflow в на пръв поглед коректен резултат. Това надгражда вече изучаваните primitive overflow и record invariants от [Concept Notes](../../../docs/learning/concepts/README.md).

## 1.3 OOP vs DOP — trade-off, не религия

| Въпрос | Класически polymorphic OOP | Data-Oriented Programming |
| --- | --- | --- |
| Добавяш нов **вариант** | Нов subtype може да имплементира стабилен interface behavior | Нов permitted subtype налага обновяване на всички exhaustive operations |
| Добавяш нова **операция** | Обикновено трябва метод/Visitor в много класове | Просто нова функция/switch върху затворения модел |
| Encapsulation | Скрито internal state + поведение | Прозрачно, конструирано и валидирано immutable state |
| Подходящо | Open plugin hierarchies, evolving polymorphic behaviors | JSON/events/AST/business alternatives с **стабилен** набор варианти |

Разграничи DOP от **DTO с public mutable fields**: прозрачната форма на данните не означава липса на invariants.

## Runnable labs

| Какво доказваме | Код | JUnit |
| --- | --- | --- |
| Sealed algebra and exhaustive switch | [Dose / Tablets / Infusion](src/main/java/bg/hristomanov/education/dop/Dose.java) | [DataOrientedProgrammingTest](src/test/java/bg/hristomanov/education/dop/DataOrientedProgrammingTest.java) |
| Guard, record patterns | [MedicationOperations](src/main/java/bg/hristomanov/education/dop/MedicationOperations.java) | guards, deconstruction |
| Defensive copy | [Prescription](src/main/java/bg/hristomanov/education/dop/Prescription.java) | input mutation + accessor blocking |
| Constructor invariants and overflow | [Tablets](src/main/java/bg/hristomanov/education/dop/Tablets.java) | rejection/exception |
| Structural equality | [Medication](src/main/java/bg/hristomanov/education/dop/Medication.java) | record equality |

```bash
mvn -pl java/language/data-oriented-programming test
```

### Break-it exercise

1. Добави `record Patch(int milligrams) implements Dose` с invariant.
2. Добави Patch в `permits`, но **не променяй** `MedicationOperations`.
3. Обясни точния compilation error за exhaustive switches и защо е желан.
4. Допълни операциите и теста. Провери кое отново е compile-time guarantee и кое трябва да се покрие с JUnit.

## Кога НЕ бих избрал DOP

- Наборът от subtype-ове е **отворен за външни plugins**;
- Правилото и вътрешното състояние се развиват заедно в инкапсулиран aggregate;
- Един централен switch с 70 case-а създава god-function;
- Входните данни са untrusted — валидирай ги преди domain record construction и обработвай malformed external input ясно.

## Senior code review checklist

- [ ] Има ли невъзможни комбинации, които сега можем да забраним с sealed alternatives?
- [ ] Добавянето на нов variant принуждава ли всички consumers да се ревизират?
- [ ] Дали `default` не скрива пропуск в business decision?
- [ ] Compact constructor гарантира ли състоянието, включително integer overflow и null?
- [ ] Shallow vs deep immutable разграничени ли са?
- [ ] Тази domain hierarchy стабилна ли е или plugins трябва да могат да се добавят свободно?

**Изход:** моделирай състоянието така, че да е възможно най-трудно да създадеш invalid object; използвай exhaustive compiler checking, когато списъкът с alternatives е намерено-ограничен и контролиран.

## Оригинални източници

- [Devoxx 2026 — Modern Java in Practice](https://youtu.be/zC2tbq7S5UE)
- [JEP 440 — Record Patterns](https://openjdk.org/jeps/440)
- [JEP 441 — Pattern Matching for switch](https://openjdk.org/jeps/441)
- [JEP 409 — Sealed Classes](https://openjdk.org/jeps/409)
- [JEP 395 — Records](https://openjdk.org/jeps/395)
- [Inside Java — Data-Oriented Programming](https://inside.java/2024/05/23/dop-v1-1-introduction/)
- [Java 25 Language Specification](https://docs.oracle.com/javase/specs/jls/se25/html/)
