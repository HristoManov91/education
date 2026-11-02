# P2 · Project Amber — next language design, WITHOUT premature production adoption

> Разширение на [Data-Oriented Programming](README.md), не нов Java language курс.  
> Лекция: [Nicolai Parlog — Java Next, Devoxx 2026](https://www.youtube.com/watch?v=ufhs9THbYZI), [01:00–10:25](https://youtu.be/ufhs9THbYZI?t=60).
>
> **Java 25** остава основа за работещия код. Подбраният proposed syntax е **само illustration**, ако изрично е маркиран като такъв.

## Реален казус

Имаме immutable \`PaymentSnapshot\` record с 3 components. Искаме да променим сумата, без да загубим validation, но boilerplate-ът расте и често погрешно правим mutable DTO.

Вече имаме [Java 25 работещ пример](src/main/java/bg/hristomanov/education/dop/valhalla/PaymentSnapshot.java) с \`withAmount\`, който извиква canonical constructor, не променя оригинала, проверява business invariants и има [JUnit тест](src/test/java/bg/hristomanov/education/dop/valhalla/ValhallaReadinessTest.java). Това е **по-простото стабилно решение днес**.

## README → код

| Идея | Реален Java 25 код | Доказателство |
| --- | --- | --- |
| Immutable reconstruction / manual wither | [PaymentSnapshot.java](src/main/java/bg/hristomanov/education/dop/valhalla/PaymentSnapshot.java) | [ValhallaReadinessTest.java](src/test/java/bg/hristomanov/education/dop/valhalla/ValhallaReadinessTest.java) |
| Record patterns, sealed alternatives, guards | [MedicationOperations.java](src/main/java/bg/hristomanov/education/dop/MedicationOperations.java) | [DataOrientedProgrammingTest.java](src/test/java/bg/hristomanov/education/dop/DataOrientedProgrammingTest.java) |
| JDK 28 value objects, **друго понятие** | [P1 Valhalla lesson](VALHALLA-VALUE-OBJECTS.md) | [EA compatibility lab](JDK28-EA-LAB.md) |

## 1. Primitive patterns: точна/lossless conversion

[JEP 530](https://openjdk.org/jeps/530) е **четвърти preview в JDK 26**; JDK 27 продължава preview еволюцията. В лекцията Nicolai показва \`int x = 16_777_217\` и проверка \`x instanceof float f\`. Това не е lossless conversion, защото стойността не може да бъде точно представена в \`float\`.

**Идея, НЕ Java 25 snippet:**

~~~java
// JDK 26+ preview, не Java 25:
int number = 16_777_217;
if (number instanceof float exactValue) {
    // Този branch не се изпълнява за тази стойност.
}
~~~

**Кога:** безопасни numeric conversions, pattern matching върху примитиви, типизирана data processing. **Кога не:** когато Java 25 е baseline; използвай explicit range/exactness validation и тест за floating precision. Не бъркай narrowing cast с проверена lossless conversion.

## 2. Derived Record Creation / „withers“ (JEP 468)

Идеята е да можем да вземем immutable record и да произведем нов instance, като изменим само избрани components, без да пишем boilerplate constructor calls.

~~~java
// JEP 468 — ИЛЮСТРАТИВЕН strawman, не runnable Java 25/JDK 28 API:
Point changed = point with { x = 1; };
~~~

Лекторът изрично предупреждава, че работата изчаква и разговорите за **deconstruction of ordinary classes**, а не само records. Разпространяването на „carrier state“ извън records е свързано с design questions около accessors, symmetric reconstruction, encapsulation и mutable fields. Това са проектирани насоки, **не финален carrier-class syntax**.

**Кога би помогнало:** immutable DTO updates с много components. **Кога не:** когато реконструкцията променя domain invariants, transaction semantics или е просто рядка операция с 2 полета. **Печалба:** по-малко boilerplate; **цена:** proposal instability и опасност да скрием бизнес правила зад generic syntax.

Стабилната алтернатива е [PaymentSnapshot.withAmount](src/main/java/bg/hristomanov/education/dop/valhalla/PaymentSnapshot.java), който явно защитава бизнес правилото, че валутата не се сменя чрез update.

## 3. Constant patterns, deconstruction assignments, carrier classes

- **Constant patterns** — позволяват \`case Point(0, int y)\` вместо \`case Point(int x, int y) when x == 0\`. В лекцията синтаксисът е представен като sketch; следи JEP draft. Днес използвай \`when\`.
- **Deconstruction assignment** — идеи за деконструиране към съществуващи променливи; синтаксисът и scope правилата още се уточняват.
- **Deconstructible / carrier classes** — идеи за класове, които описват публично своето state shape, но не са record. Изискват ясни правила за encapsulation, invariant checking и construction symmetry.
- **String templates** — остават design effort, не ги въвеждаме като налична Java 25 функция; не обещаваме конкретен ship date.
- **Type classes / user-defined arithmetic** — по-далечни идеи на Valhalla; не ги приписвай на доставен Amber release.

## 4. Decision contract

| Техника | Решава | Кога | Кога НЕ | Печалба ↔ цена |
| --- | --- | --- | --- | --- |
| Stable Java 25 records + manual withers | Immutable state updates с invariants | Сега, production | Бизнес update изисква aggregate command, не snapshot | Ясно поведение ↔ повторение на код |
| Primitive patterns preview | Safe primitive matching | Експеримент в съответната JDK версия | Java 25 production | Изразителност ↔ preview maintenance |
| Derived record creation proposal | Boilerplate при реконструкция | Да се следи като roadmap | Да се копира syntax в production | Кратък код ↔ недоставена спецификация |
| Carrier/deconstructible classes proposal | Explicit data shape за някои non-record класове | Design research / Tech Radar | За backend service с капсулирани side effects | По-богато pattern matching ↔ неизвестни семантични граници |

**Практично:** ако трябва да добавиш трети вариант на \`PaymentSnapshot\`, напиши и тествай явен factory/wither вместо да чакаш proposal. Реалната задача е да запазиш domain invariants, не да минимизираш символи.

## Senior checkpoint

1. Защо \`withAmount()\` трябва да извиква constructor и какво става при invalid currency?
2. Защо \`record Point(int x,int y)\` с \`case Point(0, int y)\` не е валиден Java 25 код?
3. Как \`instanceof float\` се различава от explicit cast?
4. Кои ограничения бихме изисквали от публичен deconstructible class?

## Оригинални източници

- [Devoxx Java Next](https://www.youtube.com/watch?v=ufhs9THbYZI) · [слайдове на автора](https://slides.nipafx.dev/java-next/2026-10-02-jcp/).
- [OpenJDK Amber](https://openjdk.org/projects/amber/) · [JEP 468](https://openjdk.org/jeps/468) · [JEP 530](https://openjdk.org/jeps/530).
- [Oracle JDK 26 Primitive Patterns — Preview](https://docs.oracle.com/en/java/javase/26/language/primitive-types-patterns-instanceof-switch.html).
- [Data-Oriented Programming — existing executable lab](README.md).
