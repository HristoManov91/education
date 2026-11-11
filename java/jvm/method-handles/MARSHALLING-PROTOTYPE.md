# P2 — Linked record marshalling: версия, ownership и security

> Съпътстваща лаборатория към [Method Handles — P1](README.md). Темата за marshalling е представена като **разработвана идея** от Viktor Klang в [Devoxx 2026](https://www.youtube.com/watch?v=4ck-T2gaMQ4), приблизително 28–39 мин. Представеният по-долу wire format и Java 25 код са **наше учебно моделиране**, не официалният формат или API на евентуален бъдещ JDK Marshalling framework.

## 1. Проблемът — защо общата Reflection сериализация е неудобна?

Сервисът изпраща много `Point` обекти. При всяко съобщение да търсим всички record components, getters, constructors и да проверяваме типа е излишна подготовка, когато схемата е фиксирана. Още по-лошо, историческата Java native serialization може да приема самоописващи class names/graphs и да нарушава границите на encapsulation, което е сериозен риск при недоверен вход.

**Naive:** автоматично `Class.forName(untrustedType)`, `setAccessible(true)` и обща рекурсивна инстанциация от bytes. Няма да имплементираме този рисков вариант.

**Good за учебния ни проблем:** allowlist на **два конкретни records**, фиксирана схема, явна версия, точен размер и предварително linked getters/constructors с `MethodHandles.publicLookup`. Няма динамична class loading от получените байтове.

## 2. Wire contract — умишлено тесен

```text
Version V1:
  byte 0      : 0x01
  bytes 1–4   : signed int x (BIG_ENDIAN)
  bytes 5–8   : signed int y (BIG_ENDIAN)
  total       : EXACTLY 9 bytes

Version V2:
  byte 0      : 0x02
  bytes 1–4   : signed int x
  bytes 5–8   : signed int y
  bytes 9–12  : signed int z
  total       : EXACTLY 13 bytes
```

**Важно:** този формат няма header с магически bytes, checksum, max frame streaming, nested types, nullable fields, crypto, authentication или обратна/напред съвместимост отвъд V1/V2. **НЕ използвай wire format-а за production protocol**. Границите тук са лесни за доказване, не production-complete.

## 3. Link once — как го реализираме

[`VersionedPointMarshaller.java`](src/main/java/bg/hristomanov/education/handles/VersionedPointMarshaller.java):

1. При инициализация проверяваме `RecordComponent[]` за точните names `x/y` (V1) или `x/y/z` (V2) и точния тип `int`.
2. `MethodHandles.publicLookup().findVirtual(...)` връща accessors; веднъж ги адаптираме до общия `(Object)int` тип.
3. `findConstructor(...)` + `asSpreader(int[].class, count)` дава специализирано `(int[])Object` извикване (съответно 2 или 3 integers).
4. Съхраняваме два immutable schema обекта в immutable maps по **Java `Class`** и по **version number**.
5. При `encode(record)` няма нови reflective lookups; при `decode(frame)` валидираме allowlisted version и **точния frame length** преди конструктор.
6. `upgradeV1(PointV1)` е **явна миграция** към V2 (`z=0`). Decode на V1 НЕ връща автоматично V2.

```java
PointV1 oldPoint = new PointV1(7, 8);
byte[] frame = VersionedPointMarshaller.encode(oldPoint);
Object decoded = VersionedPointMarshaller.decode(frame); // PointV1
PointV2 migrated = VersionedPointMarshaller.upgradeV1((PointV1) decoded);
```

Това не е аналог на всички capabilities на JSON/Protobuf/Avro и не извършва network transport.

## 4. Run / negative cases

```bash
mvn -pl java/jvm/method-handles -Dtest=VersionedPointMarshallerTest test
```

[JUnit](src/test/java/bg/hristomanov/education/handles/VersionedPointMarshallerTest.java) доказва:

- точен Big Endian payload за V1 и round-trip на V1/V2, включително отрицателни стойности;
- непознат Java type и schema version = отказ (без динамично зареждане на клас);
- празен, прекалено къс и прекалено дълъг frame = отказ;
- `null` вход/миграция = отказ;
- `V1 → V2` изисква explicit policy, не просто ново име на класа;
- следващи извиквания използват същата linked schema, но не споделят mutable резултатни byte buffers.

**Не доказват:** compatibility с автора на marshalling предложението, безопасност на всяка native библиотека или поддръжка на arbitrary object graphs. За реални услуги изисквай versioned schema, transport framing, budgets, security review и съвместима библиотека.

## 5. Decision contract

| Въпрос | Отговор |
| --- | --- |
| Проблем | Повторна Reflection discovery/boxing и implicit class instantiation при много съобщения |
| Кога | затворени, добре дефинирани DTO типове; обучителна демонстрация на linked accessors |
| Кога НЕ | недоверени arbitrary types, complex object graphs, production без mature format |
| Печалба | демонстрираме cache-ната schema/MethodHandles и строг type allowlist |
| Загуба | ръчна schema еволюция, ownership, ограничен format, поддръжка на промени |
| По-проста алтернатива | нормални конструктори, Jackson/Protobuf/Avro при реален production use case |
| Proof | [Java25 JUnit](src/test/java/bg/hristomanov/education/handles/VersionedPointMarshallerTest.java), не speedup benchmark |

**Изход:** знаем защо „link once“ има стойност при повтаряна структура, без да си представяме, че бъдещ JDK Marshalling API вече съществува в Java 25.

## Оригинални източници

- [Devoxx 2026: Teaching an Old Handle New Tricks](https://www.youtube.com/watch?v=4ck-T2gaMQ4), supplied SRT, Viktor Klang segment.
- [MethodHandle and invokeExact (Java 25)](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/invoke/MethodHandle.html).
- [Lookup permissions (Java 25)](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/invoke/MethodHandles.Lookup.html).
- [Java Object Serialization filtering (background)](https://docs.oracle.com/en/java/javase/25/core/serialization-filtering1.html).
