# P1 · Deserialization, Constructor Invariants и Integrity by Default

> **Java 25 · stable JDK serialization/JUnit labs + отделен Java 26 JEP 500 CLI/CI lab.**
> Източник: [Richard Groß — Destructuring Is the Future of Java's Encapsulation, Devoxx 2026](https://www.youtube.com/watch?v=wcghmWdu-HY), предоставен SRT (~36 мин). Лекционните примери за `Adult(17)` и future class patterns са адаптирани към собствени работещи JUnit проверки. **Бъдещият Amber синтаксис е proposal, не Java 25 feature.**

## 1. Реален казус: „Конструкторът забранява 17, а обектът е на 17“

Нашият backend приема обект `Adult`, чийто constructor допуска само възраст 18–120. Нормалното `new Adult(17)` е невъзможно. Но получаваме **native Java serialized object**, който съдържа `age=17`, възстановява се без грешка и преминава надолу към business логиката. Това е нарушен **object invariant**, независимо дали по мрежата има TLS и кой е подал байтовете.

**Bad, на пръв поглед логично:** проверяваме само в constructor и вярваме, че всяка технология за `new`/restore винаги го използва.

**Root cause:** `ObjectInputStream` по правило **НЕ извиква constructor-а на обикновения Serializable клас** (извиква се no-args constructor на първия non-Serializable superclass); полетата се възстановяват от stream. Не бъркай с `Externalizable` и **record deserialization**, чиито правила са различни.

**Good:** ако типът е прозрачен immutable data carrier, `record` преминава през **canonical constructor** при native deserialization. Ако трябва да остане обикновен клас, специално `readObject` може да проверява възстановеното състояние. **И при двете:** не приемай недоверени Java serialized streams за безопасни — първият избор е изобщо да не използваш този формат на untrusted boundary.

## 2. Какво ще научиш

- Да възпроизведеш constructor bypass със съвсем конкретен **еднополев** serialized stream, не само да повториш твърдението от лекцията.
- Да разбереш кога `record` invoke-ва canonical constructor, кога `InvalidObjectException` обвива отхвърлената стойност и защо това е по-силен state invariant, но не гаранция за липса на deserialization gadgets.
- Да покажеш `readObject` validation за non-record тип и `ObjectInputFilter` allowlist/resource budget.
- Да различаваш **native Java Object Serialization** от Jackson property/creator-based mapping и от Hibernate/JPA construction (друг lifecycle).
- Да диагностицираш deep reflective **final-field mutation** през Java 25/26 и [JEP 500](../../jvm/final-field-integrity/README.md), без да смесваш `opens`, private field **read** и final field **write**.
- Да различаваш **public logical data shape** и **private storage layout**, без да ползваш невъведен class deconstructor syntax.

## 3. README → код → negative/positive proof

| Семантика | Java25 production-like учебен код | Доказателство |
| --- | --- | --- |
| Legacy Serializable constructor bypass | [DeserializationBoundaryLab.LegacyAdult](src/main/java/bg/hristomanov/education/dop/amber/DeserializationBoundaryLab.java) | [legacy stream test](src/test/java/bg/hristomanov/education/dop/amber/DeserializationBoundaryLabTest.java) |
| Record canonical constructor on valid/invalid streams | [RecordAdult](src/main/java/bg/hristomanov/education/dop/amber/DeserializationBoundaryLab.java) | Същият test class: constructor count, `InvalidObjectException` и immutable component |
| Explicit legacy guard `readObject` | [GuardedAdult](src/main/java/bg/hristomanov/education/dop/amber/DeserializationBoundaryLab.java) | Invalid restored age rejected, valid deserialization accepted |
| `ObjectInputFilter` class + stream limits | [JUnit filter](src/test/java/bg/hristomanov/education/dop/amber/DeserializationBoundaryLabTest.java) | Unknown `ArrayList` rejected while `RecordAdult` allowed |
| Public string / private epoch milliseconds | [PublicTimestamp](src/main/java/bg/hristomanov/education/dop/amber/PublicTimestamp.java) | [PublicTimestampTest](src/test/java/bg/hristomanov/education/dop/amber/PublicTimestampTest.java) |
| `final` reflective write | [FinalFieldMutationDemo](../../jvm/final-field-integrity/FinalFieldMutationDemo.java) | [Java 25/26 script](../../jvm/final-field-integrity/run-lab.sh) + [CI](../../../.github/workflows/amber-final-field-integrity.yml) |

### Изпълни

```bash
# От repository root, JDK25; няма нови Maven modules или зависимости:
mvn -pl java/language/data-oriented-programming \
  -Dtest=DeserializationBoundaryLabTest,PublicTimestampTest test

# Отделен CLI experiment със съответния JDK в PATH:
bash java/jvm/final-field-integrity/run-lab.sh
```

## 4. Как доказваме нарушен object invariant

[Тестът](src/test/java/bg/hristomanov/education/dop/amber/DeserializationBoundaryLabTest.java) прави:

1. `new LegacyAdult(17)` → `IllegalArgumentException`.
2. `new LegacyAdult(19)` → валиден обект, записан в native `ObjectOutputStream`.
3. **Само за учебния тип с точно едно `int` поле:** проверява, че последните 4 байта са `19`, после ги подменя с `17`. Това е deterministic **fixture**, не универсален binary patcher за Java serialization.
4. `ObjectInputStream.readObject()` → `LegacyAdult(age=17)`; constructor invocation counter не се променя.
5. При `RecordAdult` със същата промяна `readObject()` извиква canonical constructor, който отказва 17, и `ObjectInputStream` хвърля `InvalidObjectException` с причина `IllegalArgumentException`.
6. `GuardedAdult` остава обикновен клас, но със `readObject` guard отхвърля същата атака.

**Защо counter?** Доказваме и *защо* legacy примерът успява — без проверка само на връщаната стойност. Няма static fixture state в serialization payload, а byte patch е **проверен преди модификацията**. При промяна на serialized shape тестът трябва да FAIL-не, не да „поправя“ произволни байтове.

### `ObjectInputFilter` е defense-in-depth, не permission system

Филтърът от JUnit използва *allowlist* за `RecordAdult` и ограничение за depth, references и stream bytes. `ArrayList` е reject-нат. Реални приложения трябва да избират allowlist според точния бизнес protocol и да имат общи stream/array limits. Филтърът **не прави Java native deserialization на недоверени данни inherently safe**; най-лесното безопасно решение е приемане на строг JSON/CBOR schema + входна валидация вместо произволни обекти.

### Какво е различно при Jackson и JPA?

- **Jackson JSON → Java:** зависи от mapper настройките, `@JsonCreator`, canonical/explicit constructors, record support и setter/field visibility. Не твърди „Jackson винаги заобикаля constructor“. Вътрешният mutability/validation contract се доказва **за конкретните версии и конфигурации**.
- **JPA/Hibernate:** entity lifecycle, proxies, persistence hydration и no-args constructor constraints са **различен модел** от native `ObjectInputStream`. Не сменяй всяка JPA entity с record. Важи отделно access control и entity invariants тестове.
- **Serialized records:** canonical constructor е силна state защита, **не security sandbox**: malicious object graphs, ресурсна злоупотреба, невалидни nested objects и compatibility concerns изискват допълнителен threat model.

**Проверено с:** [Oracle Java 25 Serialization Specification](https://docs.oracle.com/en/java/javase/25/docs/specs/serialization/input.html), [Java 25 Records](https://docs.oracle.com/en/java/javase/25/language/records.html) и [ObjectInputFilter](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/io/ObjectInputFilter.html).

## 5. Encapsulation ≠ просто private fields

Лекторът противопоставя три различни понятия:

- `private` скрива полетата от нормален Java source код, но access може да бъде отворен чрез reflective API според module/openness policy;
- `final` прави assignment след constructor забранено за нормален Java код, но **на някои обикновени класове** deep reflection исторически е позволявала мутация на instance fields;
- **JPMS `exports/opens`** контролира visibility/deep reflection; `opens` не е всеобщо permission за illegal final-field mutation след JEP 500.

Ключовият JEP500 upgrade risk: **Java 26 warn by default; debug със stack; deny throwing `IllegalAccessException`; explicit module opt-in като миграционно изключение.** [Пълен test matrix и документация](../../jvm/final-field-integrity/README.md). За record fields reflective write вече е забранен — [Java25 JUnit](src/test/java/bg/hristomanov/education/dop/amber/DeserializationBoundaryLabTest.java) доказва това без preview.

**Не обобщавай „reflection е грешна“:** четенето на полета, framework inspection, JavaBeans accessors и constructor invocation са различни от *писане във final поле*. JEP 500 има точен scope.

## 6. Деконструкция на класове, различни от records

[PublicTimestamp](src/main/java/bg/hristomanov/education/dop/amber/PublicTimestamp.java) приема `String timestamp`, валидира `Instant.parse`, съхранява **private final `long epochMillis`**, а навън връща `utcText()`. `withUtcText()` извиква отново конструктора и не expose-ва representation. [JUnit](src/test/java/bg/hristomanov/education/dop/amber/PublicTimestampTest.java) проверява нормализиране и невалидна реконструкция.

Това е **стабилен Java 25** вариант, не user-defined deconstruction. При record `record PublicTimestamp(long epochMillis)` компонентите му са видим public API contract; класът предоставя различна **logical view**. Richard мотивира бъдещите Amber class patterns, reconstruction и serialization-friendly destructuring, но няма стандартизиран Java 25 `case PublicTimestamp(String x)` или built-in class wither. [Съществуващото P2 проучване](DATA-INTERFACES-AND-DECONSTRUCTION.md) разглежда същата посока с `CoordinateView`.

## 7. Lombok/Builder/Wither — уточнение от Q&A (~26–33 мин)

Лекторът изрично казва, че не е използвал Lombok и разсъждава как `@With` може да работи. **Неговата хипотеза, че Lombok може да мутира стария обект, не е факт.** Според [официалния Lombok `@With`](https://projectlombok.org/features/With), generated wither разчита на constructor с **всички полета в декларационния им ред** и създава нов обект. Това обаче НЕ гарантира автоматично business invariants, ако auto-generated constructor само assigns fields или business invariant зависи от няколко компонента.

**Bad:** generic builder влиза в междинно невалидно състояние и го публикува, или `@With` реконструира през constructor без домейн валидация.

**Good:** immutable validated constructor/factory + атомарно обновяване на всички зависими полета; [Record Reconstruction lab](RECORD-RECONSTRUCTION-INVARIANTS.md) вече доказва случая. Не добавяме Lombok като нова Maven зависимост само за тази сравнителна бележка.

## 8. Decision contract

| Подход | Какъв проблем решава | Кога | Кога НЕ | Печалби и цена |
| --- | --- | --- | --- | --- |
| Java 25 `record` + constructor | Transparent snapshot/invariants при native deserialization | DTO/value object с публични компоненти | Скрито representation, JPA-managed mutable entity | simpler semantics ↔ public shape / shallow immutability |
| Non-record `readObject` guard | Миграция на legacy Serializable клас | Трябва да се поддържа legacy format | Нов untrusted HTTP endpoint | state check ↔ ръчна, често забравяна validation и gadget risk |
| ObjectInputFilter | Ограничение за класове и resources | Наследена система, която няма как веднага да премахне native streams | Като единствен security boundary | defense-in-depth ↔ policy maintenance |
| JDK26 `deny` | Намира зависимости от незаконни reflective final writes | Framework upgrade / migration CI | Да се включва като общ permission workaround | future compatibility ↔ пренаписване на legacy integrations |
| Public facade + private storage | Стабилен API, който не издава implementation | Library с implementation freedom | Domain type естествено е data-transparent record | compatibility ↔ boilerplate |

**Изход:** вместо да вярваме, че constructor винаги е извикан, имаме доказателство за всеки жизнен път на обекта. За нови untrusted boundaries предпочитаме schema-controlled data, а не Java serialization. За class/data pattern предложения следим Amber, но пишем стабилен код днес.

## Източници

- [Richard Groß — Destructuring Is the Future of Java's Encapsulation](https://www.youtube.com/watch?v=wcghmWdu-HY), user-supplied SRT, 01–16 min + Q&A 26–33 min.
- [Oracle Java Object Serialization Spec, Object Input](https://docs.oracle.com/en/java/javase/25/docs/specs/serialization/input.html).
- [JEP 500 — Prepare to Make Final Mean Final](https://openjdk.org/jeps/500) и [Oracle Java26 migration guide](https://docs.oracle.com/en/java/javase/26/migrate/preparing-final-field-mutation-restrictions.html).
- [Project Amber — Beyond Records](https://www.mail-archive.com/amber-spec-experts%40openjdk.org/msg00829.html).
- [Lombok @With official documentation](https://projectlombok.org/features/With).
