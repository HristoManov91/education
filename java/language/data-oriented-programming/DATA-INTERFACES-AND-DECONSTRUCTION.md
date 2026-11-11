# P2 — Data interfaces и user-defined deconstruction: публичен API срещу скрита реализация

> [Gavin Bierman, What’s happening with Project Amber?](https://www.youtube.com/watch?v=Go76-9Q51wU), финалната част (~42–51 мин в предоставения SRT). **Java25 кодът тук използва само нормален interface, package-private implementation и публичен factory**, не експериментални data-interface/carrier pattern синтаксиси. [Amber: Data Oriented Programming, Beyond Records](https://www.mail-archive.com/amber-spec-experts%40openjdk.org/msg00829.html) обяснява посоката, но тя не е финален JDK25 language API.

## 1. Реалният проблем

Публична Java библиотека предоставя резултат от операция, например `CoordinateView`. Не искаме клиентските услуги да познават concrete `HiddenCoordinate` class и вътрешния representation. Искаме да можем да сменяме реализацията без да променяме извикващия код.

**Bad:** public `record InternalCoordinate(int x, int y)` като единствения public library API, ако реализацията/форматът трябва да останат скрити. Record налага прозрачен component contract и автоматично позволява pattern deconstruction. Това е **полезно**, когато данните трябва да са прозрачен public value type; **не е задължително** за всяко API.

**Good:** `public interface CoordinateView { int x(); int y(); }` и public factory, която връща само интерфейса. Реализацията `HiddenCoordinate` е package-private, с private fields.

```text
client module / source
    └── CoordinateViews.of(x, y) : CoordinateView   (public API)
                |
          internal HiddenCoordinate (package-private; private fields)
                |
       client consumes only x(), y() (not RecordPattern)
```

## 2. README → source → JUnit

| Договор | Изпълним Java25 код | Тест |
| --- | --- | --- |
| Публичен stable accessor contract | [CoordinateView.java](src/main/java/bg/hristomanov/education/dop/amber/CoordinateView.java) | [CoordinateViewsTest.java](src/test/java/bg/hristomanov/education/dop/amber/CoordinateViewsTest.java) |
| Скрита конкретна реализация и factory | [CoordinateViews.java](src/main/java/bg/hristomanov/education/dop/amber/CoordinateViews.java) | runtime class не е `public`/record, но API работи |
| Open interface допуска външна имплементация | същият `CoordinateView` | test с отделна анонимна имплементация |
| Business overflow/null invariants | `CoordinateViews.manhattanDistanceFromOrigin` | negative Math.absExact/Math.addExact JUnit |
| Истинска runtime encapsulation при named modules (различен проблем) | [P1 JPMS lab](../../jvm/jpms-encapsulation/README.md) | compiler denial, reflection `opens` negative tests |

```bash
mvn -pl java/language/data-oriented-programming \
  -Dtest=CoordinateViewsTest test
```

**Използваме normal Java method invocation** на accessor-и, не pattern matching за деструктуриране на обикновен interface. В Java 25 `case CoordinateView(int x, int y)` **не е валиден** за такъв обикновен интерфейс. `record Point(...)` може да се deconstruct-ва със stable record pattern, но носи различни public/data семантики.

## 3. Какво предлагат Gavin и Amber — все още не е delivered feature

В лекцията Gavin обсъжда data interfaces/deconstructible classes, при които класът излага концептуално структурирана форма през accessor-и и може да участва в pattern matching без да бъде `record`. Целта е да се разделят **публичното описание на данните** и **свободата на реализацията**, заедно с reconstruction и изрични invariants. Няма стандартизирано Java25 правило, което добавя deconstruction/reconstruction автоматично към всеки interface с `x()`/`y()`.

Това е езикова research посока, а не основание да имитираме API чрез reflection/private JDK interfaces или да отваряме packages с `--add-opens`. Потенциални ограничения: устойчивост на public component shape, binary/source compatibility при добавяне на component, invariant preservation и compatibility с класове, които имат identity или mutable state.

## 4. Кога / кога НЕ / trade-offs

| Подход | Кога | Кога НЕ | Печалба | Цена / по-просто |
| --- | --- | --- | --- | --- |
| `record` | прозрачни неизменяеми value objects, които клиентът естествено deconstruct-ва | искаме да скрием representation/invariants и concrete class | generated equals/hashCode и record pattern | public components contract |
| `interface` + package-private class | библиотека с implementation freedom, интеграция със стари clients | API трябва да е strict transparent data type | стабилен public API, сменяема реализация | повече code, accessor maintenance |
| `sealed interface` | истински затворен набор от подтипове с exhaustive switch | externally extensible provider/plugin API | compile-time exhaustiveness | нов subtype налага обновяване на всички exhaustive clients |
| Future data interfaces / user-defined patterns | след final JDK API и migration review | Java25 production, невъведен syntax | потенциално compositional pattern matching | proposal uncertainty/compatibility risks |

**Важно:** package-private реализация на classpath **не е security sandbox**; reflection/agents могат да наблюдават internals. За module-level strong encapsulation използвай JPMS. Също така open interface, избран тук, **позволява външни implementations**; ако нямаш нужда от тях, обмисли `sealed` или по-тесен factory API.

## 5. Изход

Научаваме защо API дизайнът не бива автоматично да разкрива всички implementation components. Реалният Java25 пример е стабилен; бъдещият Amber syntax остава Tech Radar, а тестовете доказват само описаното source-level поведение.

## Оригинални източници

- [Gavin Bierman — What’s happening with Project Amber?](https://www.youtube.com/watch?v=Go76-9Q51wU), user-provided SRT.
- [Amber — Data Oriented Programming, Beyond Records](https://www.mail-archive.com/amber-spec-experts%40openjdk.org/msg00829.html).
- [Inside Java — Carrier Classes](https://inside.java/2026/02/23/podcast-047/).
- [Java 25 record patterns](https://docs.oracle.com/en/java/javase/25/language/record-patterns.html).
- [JEP 395 — Records](https://openjdk.org/jeps/395).
