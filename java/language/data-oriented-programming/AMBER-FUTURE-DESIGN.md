# P2 · Project Amber — next language design, WITHOUT premature production adoption

> Разширение на [Data-Oriented Programming](README.md), не нов Java language курс.  
> Лекция: [Nicolai Parlog — Java Next, Devoxx 2026](https://www.youtube.com/watch?v=ufhs9THbYZI), [01:00–10:25](https://youtu.be/ufhs9THbYZI?t=60).
>
> **Java 25** остава основа за работещия код. Подбраният proposed syntax е **само illustration**, ако изрично е маркиран като такъв.

## Реален казус

Имаме immutable `PaymentSnapshot` record с 3 components. Искаме да променим сумата, без да загубим validation, но boilerplate-ът расте и често погрешно правим mutable DTO.

Вече имаме [Java 25 работещ пример](src/main/java/bg/hristomanov/education/dop/valhalla/PaymentSnapshot.java) с `withAmount`, който извиква canonical constructor, не променя оригинала, проверява business invariants и има [JUnit тест](src/test/java/bg/hristomanov/education/dop/valhalla/ValhallaReadinessTest.java). Това е **по-простото стабилно решение днес**.

## Devoxx 2026 — Gavin Bierman: проверимо обучение срещу proposal статус

[What's happening with Project Amber?](https://www.youtube.com/watch?v=Go76-9Q51wU) (приблизително 52 мин, предоставен автоматичен SRT) надгражда data-oriented programming с exact numeric conversions, реконструкция на записи и идеи за data interfaces/user-defined deconstruction. **През ноември 2026 това не са три нови финализирани Java25 възможности.**

- **P1 / Java25 runnable:** [Record Reconstruction & Invariants](RECORD-RECONSTRUCTION-INVARIANTS.md) — `ParallelTaskBatch.replaceTogether` валидира едновременно зависими компоненти; `List<TaskPriority>` е структурно по-сигурна алтернатива. [Source](src/main/java/bg/hristomanov/education/dop/amber/RecordReconstructionInvariantLab.java) + [JUnit](src/test/java/bg/hristomanov/education/dop/amber/RecordReconstructionInvariantLabTest.java).
- **P1 / Java25 runnable:** [Numeric Conversion Exactness](NUMERIC-CONVERSION-EXACTNESS.md) — `int → float → int` може лъжливо да изглежда без загуба поради saturating narrowing. [Source](src/main/java/bg/hristomanov/education/dop/amber/NumericConversionExactnessLab.java) + [10 000 независими IEEE754 oracle cases](src/test/java/bg/hristomanov/education/dop/amber/NumericConversionExactnessLabTest.java). Няма preview `instanceof float` в Java25 пример.
- **P2 / стабилен Java25 API + предложение:** [Data interfaces и deconstruction](DATA-INTERFACES-AND-DECONSTRUCTION.md) — [CoordinateView](src/main/java/bg/hristomanov/education/dop/amber/CoordinateView.java) и [package-private concrete class](src/main/java/bg/hristomanov/education/dop/amber/CoordinateViews.java) с [JUnit](src/test/java/bg/hristomanov/education/dop/amber/CoordinateViewsTest.java); `case CoordinateView(int x,int y)` **не е валиден Java25 record pattern**.

**Извод:** „данните първо“ значи правилни типове и валидни състояния, не автоматично разкриване на всеки internal field. Невъведен синтаксис не е production dependency.

## README → код

| Идея | Реален Java 25 код | Доказателство |
| --- | --- | --- |
| Immutable reconstruction / manual wither | [PaymentSnapshot.java](src/main/java/bg/hristomanov/education/dop/valhalla/PaymentSnapshot.java) | [ValhallaReadinessTest.java](src/test/java/bg/hristomanov/education/dop/valhalla/ValhallaReadinessTest.java) |
| Record patterns, sealed alternatives, guards | [MedicationOperations.java](src/main/java/bg/hristomanov/education/dop/MedicationOperations.java) | [DataOrientedProgrammingTest.java](src/test/java/bg/hristomanov/education/dop/DataOrientedProgrammingTest.java) |
| JDK 28 value objects, **друго понятие** | [P1 Valhalla lesson](VALHALLA-VALUE-OBJECTS.md) | [EA compatibility lab](JDK28-EA-LAB.md) |

## 1. Primitive patterns: точна/lossless conversion

> **Важно обновление — 11.11.2026:** [Gavin Bierman и Angelos Bimpoudis обявяват](https://www.mail-archive.com/amber-spec-experts%40openjdk.org/msg00923.html), че preview функционалността **Primitive Types in Patterns, instanceof, and switch** няма да продължи: 6th-preview draft е withdrawn, а feature ще бъде премахната от **JDK 28**, дори с `--enable-preview`. JDK23/24/25/26/27 previews са исторически достъпни *само на съответните JDK версии*. Не препоръчваме adoption, migration към този syntax или JDK28 code samples с primitive patterns.


[JEP 530](https://openjdk.org/jeps/530) е **четвърти preview в JDK 26**; JDK 27 има пети preview ([JEP 532](https://openjdk.org/jeps/532)). **Тази lineage вече НЕ води към JDK28 preview или окончателен API.** В лекцията Nicolai показва `int x = 16_777_217` и проверка `x instanceof float f`. Това не е lossless conversion, защото стойността не може да бъде точно представена в `float`.

**Идея, НЕ Java 25 snippet:**

~~~java
// Исторически JDK 26/27 preview, НЕ е валидно за JDK 28:
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

- **Constant Patterns (предложение към 11.11.2026)** — бъдещо вложено `case Point(0, int y)` вместо `case Point(int x, int y) when x == 0`. Официалният [withdrawal announcement](https://inside.java/2026/11/11/withdraw-primitive-patterns-preview/) ги посочва като **отделна посока**, не като доставен replacement. В Java25 използвай стабилен `when` guard.
- **Pattern declarations и deconstruction assignments (предложения)** — желаното използване на pattern извън `switch`, включително в `for`, има незавършени scope/type правила. `for (Point(int x, int y) : points)` **не е стабилен Java25 syntax**; използвай обикновен `for (Point point : points)` и accessors.
- **Record reconstruction (предложение)** — [Brian Goetz, March 2026](https://www.mail-archive.com/amber-spec-experts%40openjdk.org/msg00877.html) подчертава final-state constructor invariants при промени в няколко компонента; `with` и `record.new` са evolving design, не Java25 API. [Runnable Java25 lab](RECORD-RECONSTRUCTION-INVARIANTS.md) показва защо два независими wither-а не са достатъчни.
- **Data interfaces / carrier classes (предложения)** — публичното state shape и user-defined deconstruction биха позволили pattern matching върху типове, които не са records, но изискват ясни правила за encapsulation, invariants и compatibility. [P2 Java25 пример](DATA-INTERFACES-AND-DECONSTRUCTION.md) показва само публичен accessor interface и скрита реализация, **не** бъдещ pattern API.
- **String Templates и type-class/user-defined arithmetic идеи** — развиващи се или оттеглени дизайни. Не ги представяме като Java25 final features; Valhalla generics/numerics могат допълнително да променят primitive-pattern посоката.

## Richard Groß (Devoxx 2026): „Destructuring“ като симетрия на construction

[Видео + предоставен SRT](https://www.youtube.com/watch?v=wcghmWdu-HY) (~14–25 мин): лекторът мотивира **user-defined deconstruction на обикновени класове, primary constructor, pattern matching и class/record reconstruction**. В концептуален план класът има public logical shape, което не трябва да издава реалните private fields. Това би улеснило pattern matching и serializer/framework integrations, ако API и invariant semantics са уточнени.

**Статус — предложен дизайн, не финализирана Java 25/26 функция.** Не публикувай `case PublicTimestamp(String s)`, „primary class constructor“ или `object with { ... }` като работещи Java25 snippets. Лекторът също отбелязва, че няма вътрешен достъп до roadmap-а на Amber. Възможното бъдещо премахване/deprecation на Java Serialization **не е гарантиран JDK release milestone**.

**Стабилни доказателства днес:** [Java25 public String / private long class](src/main/java/bg/hristomanov/education/dop/amber/PublicTimestamp.java) с [JUnit](src/test/java/bg/hristomanov/education/dop/amber/PublicTimestampTest.java), [native deserialization legacy-vs-record](DESERIALIZATION-AND-ENCAPSULATION.md) и [JDK26 final-field policy](../../jvm/final-field-integrity/README.md). Не дублираме вече наличните Amber reconstruction и data-interface chapters.

## 4. Decision contract

| Техника | Решава | Кога | Кога НЕ | Печалба ↔ цена |
| --- | --- | --- | --- | --- |
| Stable Java 25 records + manual withers | Immutable state updates с invariants | Сега, production | Бизнес update изисква aggregate command, не snapshot | Ясно поведение ↔ повторение на код |
| Primitive patterns preview **withdrawn for JDK28** | Safe primitive matching (historical JDK23–27 preview only) | Анализ на стар preview код, не нов adoption | Java25 production и **всякакъв JDK28 build** | Изразителност ↔ feature е изтеглена; използвай explicit conversion checks |
| Derived record creation proposal | Boilerplate при реконструкция | Да се следи като roadmap | Да се копира syntax в production | Кратък код ↔ недоставена спецификация |
| Carrier/deconstructible classes proposal | Explicit data shape за някои non-record класове | Design research / Tech Radar | За backend service с капсулирани side effects | По-богато pattern matching ↔ неизвестни семантични граници |

**Практично:** ако трябва да добавиш трети вариант на `PaymentSnapshot`, напиши и тествай явен factory/wither вместо да чакаш proposal. Реалната задача е да запазиш domain invariants, не да минимизираш символи.

## Senior checkpoint

1. Защо `withAmount()` трябва да извиква constructor и какво става при invalid currency?
2. Защо `record Point(int x,int y)` с `case Point(0, int y)` не е валиден Java 25 код?
3. Как `instanceof float` се различава от explicit cast?
4. Кои ограничения бихме изисквали от публичен deconstructible class?

## Оригинални източници

- [Devoxx Java Next](https://www.youtube.com/watch?v=ufhs9THbYZI) · [слайдове на автора](https://slides.nipafx.dev/java-next/2026-10-02-jcp/).
- [OpenJDK Amber](https://openjdk.org/projects/amber/) · [JEP 468](https://openjdk.org/jeps/468) · [JEP 530](https://openjdk.org/jeps/530).
- [Oracle JDK 26 Primitive Patterns — Preview](https://docs.oracle.com/en/java/javase/26/language/primitive-types-patterns-instanceof-switch.html).
- [Gavin Bierman/Angelos Bimpoudis, 11.11.2026 — official Amber mailing-list notice](https://www.mail-archive.com/amber-spec-experts%40openjdk.org/msg00923.html).
- [Gavin Bierman — What's happening with Project Amber? (Devoxx 2026)](https://www.youtube.com/watch?v=Go76-9Q51wU), provided transcript.
- [Devoxx 2026 official abstract](https://m.devoxx.com/events/dvbe26/talks/21655/whats-happening-with-project-amber).
- [Brian Goetz — Derived Record Creation / Reconstruction update (March 2026)](https://www.mail-archive.com/amber-spec-experts%40openjdk.org/msg00877.html).
- [Amber — Data Oriented Programming, Beyond Records](https://www.mail-archive.com/amber-spec-experts%40openjdk.org/msg00829.html).
- [Data-Oriented Programming — existing executable lab](README.md).
