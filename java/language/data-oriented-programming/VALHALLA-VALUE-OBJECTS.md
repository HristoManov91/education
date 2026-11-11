# P1 · Project Valhalla: Value Objects, Identity и JDK 28 Compatibility

> **Java 25 — runnable JUnit + Java 28 Early Access — отделен preview lab** · Mid+ → Early Senior · 45–60 минути + експерименти.  
> **Лекция:** [Nicolai Parlog — Java Next, Devoxx Belgium 2026](https://www.youtube.com/watch?v=ufhs9THbYZI), основно [24:23–39:53](https://youtu.be/ufhs9THbYZI?t=1463); допълнителна връзка към [Ron Pressler — Big Runtimes](../../jvm/performance-evolution/BIG-RUNTIMES-JIT-ABSTRACTIONS.md).  
> **Актуализация към 2026-11-02:** [JEP 401](https://openjdk.org/jeps/401) / [JEP 539](https://openjdk.org/jeps/539) са **preview в JDK 28 EA**, не Java 25. JDK 28 GA още не е излязъл; preview API/поведение може да се промени.  
> **Произход:** ориентировъчни времена и идея — предоставен автоматичен Gemini SRT; семантичните изключения и съвместимостта — [OpenJDK Quality Outreach, 27.10.2026](https://inside.java/2026/10/27/quality-heads-up/) и [JDK 28 preview JLS](https://download.java.net/java/early_access/jdk28/docs/specs/value-objects-jls.html).

## 1. Реален проблем: Java 25 код работи, но при JDK 28 preview логиката се променя

Имаме backend с множество протоколи, DTO snapshots и плащания. Използваме `LocalDate` като ключ и правим отчет по дата. Стари utility класове понякога използват `==` за сравнение, `WeakHashMap<LocalDate, ...>` за кеш или `synchronized(date)` за заключване.

Това е крехко **още в Java 25**, защото `LocalDate` е value-based class (тип, чиито инстанции трябва да се третират като взаимозаменяеми при еднаква стойност). **JDK 28 с включен preview прави риска видим**: много value-based JDK класове действително стават value classes (без identity). Същият стар bytecode може да има различно поведение в зависимост от runtime флага!

### Какво ще можеш да обясниш

1. Identity object срещу value object и защо `record` **не означава** автоматично `value record`.
2. Защо `==` за value objects е *statewise equivalence* (сравнение на непосредственото състояние, рекурсивно по `==` за reference полета), докато record `equals()` използва логическо сравнение на components.
3. Защо value objects не могат да се заключват, проследяват с `WeakReference` или използват като weak keys.
4. Защо Valhalla *отваря възможности* за flattening/scalarization, без да обещава автоматично flat `List<ValueMoney>` или универсално ускорение.
5. Как да направиш **двойно JDK 28 compatibility тестване** с preview ON/OFF, без да нарушиш Java 25 main CI.

## 2. README → код → тестове

| Концепция | Работещ Java 25 пример | Проверка |
| --- | --- | --- |
| Immutable business values + validation | [MoneySnapshot.java](src/main/java/bg/hristomanov/education/dop/valhalla/MoneySnapshot.java) | [ValhallaReadinessTest.java](src/test/java/bg/hristomanov/education/dop/valhalla/ValhallaReadinessTest.java) |
| Ръчно създаване на нов record, вместо mutation | [PaymentSnapshot.java](src/main/java/bg/hristomanov/education/dop/valhalla/PaymentSnapshot.java) | original unchanged, validation and currency invariants |
| Стойностно equality и HashMap вместо identity-keyed map | [ValueSafeOperations.java](src/main/java/bg/hristomanov/education/dop/valhalla/ValueSafeOperations.java) | два отделни `LocalDate` → една бизнес група |
| Един Java 25 class file, две JDK 28 поведения | [IdentityPreviewProbe.java](jdk28-ea/IdentityPreviewProbe.java) | [JDK 28 EA workflow](../../../.github/workflows/valhalla-jdk28-ea.yml), preview OFF и ON |
| Новият JDK 28 `value record` | [ValueObjectsPreview.java](jdk28-ea/ValueObjectsPreview.java) | compilation + executable assertions на JDK 28 EA |
| Ръчно повторение на preview lab | [JDK28-EA-LAB.md](JDK28-EA-LAB.md) | terminal commands / очаквани резултати |
| Record constructor early-`this`: Java 25 bytecode работи, JDK 28 preview source отказва | [LegacyRecordConstructorProbe.java](jdk28-ea/LegacyRecordConstructorProbe.java) | [EA workflow](../../../.github/workflows/valhalla-jdk28-ea.yml): positive runtime и expected compilation failure |

## 3. Какво се променя в type system

**Identity object** (обект с отделима идентичност) може да има две инстанции със същите полета, но различна identity. Операцията `==` за такива обекти е reference identity; монитор, `wait/notify`, identity references и weak references имат смисъл.

**Value object** (обект без identity) описва стойност. JDK може да замества една инстанция с друга, ако те са *statewise equivalent*; `==` в JDK 28 preview има специални правила. Value class трябва да има state, който не се променя след конструиране; не можеш да синхронизираш върху инстанцията и тя не може да е referent на `java.lang.ref`.

| Конструкция | Java 25 | JDK 28 preview ON | Защо е важно |
| --- | --- | --- | --- |
| `record Money(long cents)` | Обикновен identity record със generated equals | **Остава обикновен record**, ако не е деклариран като value | Records са *syntax за прозрачни immutable данни*, не гаранция за липса на identity |
| `value record Money(long cents)` | Невалиден синтаксис | Value object — statewise `==` | Има допълнителни ограничения, preview |
| `LocalDate.of(...)` ×2 | `==` наблюдава identity; `equals` сравнява дата | `==` вече може да е `true` при еднаква стойност | Безопасният избор за бизнес код е `equals` |
| `new WeakReference<>(localDate)` | Работи (но е лош value-cache design) | `IdentityException` | Weak keys/references изискват identity |
| `synchronized(localDate)` | Нежелателно — value-based API предупреждава | `IdentityException` или compile error | Заключвай върху отделен, контролиран identity lock |
| Използване на `HashMap<LocalDate,...>` | Правилно | Правилно | `equals/hashCode` съответстват на business key |

**Внимание:** `IdentityHashMap` и `WeakHashMap` не са едно и също. Първото е identity-keyed, второто използва value equality, но със слаби reference keys. И двете са неподходящи за този business date ключ, по различни причини.

## 4. BAD / GOOD в съществуващ Java 25 backend

BAD — реалистично изглеждащ кеш:

~~~java
// Не пиши това за бизнес дата — различните LocalDate инстанции
// нямат задължение да бъдат еднакъв identity key.
Map<LocalDate, String> cache = new IdentityHashMap<>();
cache.put(LocalDate.of(2026, 11, 2), "batch");
String result = cache.get(LocalDate.of(2026, 11, 2)); // may be null
~~~

GOOD — `Map<LocalDate, Long>` в [ValueSafeOperations.sumByDueDate](src/main/java/bg/hristomanov/education/dop/valhalla/ValueSafeOperations.java), `Objects.equals` и immutable snapshots. Тестът доказва, че две логически равни дати попадат в една група. **Важен бизнес инвариант:** групиране само по дата допуска **единствена валута в batch-а**; методът изрично отхвърля смесване EUR/USD. За многовалутни справки използвай композитен ключ `(dueDate, currency)` или отделни суми по валута — никога не събирай различни валути като безразмерни числа.

BAD — ръчно изменяне на shared snapshot, вместо нов immutable object; alias-ът у другата нишка вижда неочаквана промяна.

GOOD — [PaymentSnapshot.withAmount](src/main/java/bg/hristomanov/education/dop/valhalla/PaymentSnapshot.java) реконструира нов record с валидациите му, без да променя стария. Това е **Java 25 имплементация**, не нов wither синтаксис.

~~~bash
mvn -pl java/language/data-oriented-programming test
~~~

[Шест JUnit теста](src/test/java/bg/hristomanov/education/dop/valhalla/ValhallaReadinessTest.java) проверяват equality, копиране, групиране, домейн ограничения и overflow; **не** фиксират performance speedups.

## 5. Лаборатория: един class file, два runtime режима

По [официалното предупреждение от 27.10.2026](https://inside.java/2026/10/27/quality-heads-up/) при preview ON **30 стандартни JDK класа** се държат като value classes: wrapper класовете (`Integer`, `Long` и други), `Optional`, част от `java.time` (`LocalDate`, `Instant`, `Duration` и други), `Number` и `Record`. Това важи и за **код, компилиран без preview** на по-стар JDK!

Изпълни [JDK28-EA-LAB.md](JDK28-EA-LAB.md). В [IdentityPreviewProbe.java](jdk28-ea/IdentityPreviewProbe.java) един и същ Java 25-compatible файл се компилира веднъж до Java 25 bytecode; после се изпълнява с JDK 28:
- preview OFF → `LocalDate` identity поведение; `WeakReference` работи;
- preview ON → statewise `==` и `IdentityException` при weak reference;
- `LocalDate.equals` продължава да е вярно в двата режима.

В [ValueObjectsPreview.java](jdk28-ea/ValueObjectsPreview.java) се компилират `value record ValueMoney` и `value record ValuePrice`. Това показва **нюанс, който не бива да се пропуска**:

- `ValueMoney(500) == ValueMoney(500)` → `true` (само primitive поле).
- Две `ValuePrice(500, new String("EUR"))` → `equals()` може да е `true`, но `==` е `false`, защото reference field-ът `String` се сравнява по `==`, не по `String.equals`.

Нито едно от горните **не означава**, че новият `value` синтаксис може да се компилира в Java 25. [GitHub Actions workflow-ът](../../../.github/workflows/valhalla-jdk28-ea.yml) използва отделно **OpenJDK 28 Early Access**, без да променя основния Maven reactor.

## 6. Опасности при миграцията

| Риск | Как да го откриеш | Как да го поправиш |
| --- | --- | --- |
| `==` върху value-based класове | Статичен анализ + тест в двата режима | `equals()` за logical value, `==` само ако изрично търсиш identity на identity object |
| `synchronized(value)`, `wait/notify` | Code search и preview execution | Отделен `Object` lock / `ReentrantLock` със собствен lifecycle |
| `WeakReference` / `WeakHashMap` върху стойности | JDK28 preview → `IdentityException` | Обмисли ownership, TTL/size-based cache и стабилен key contract |
| Reflection mutation (`setAccessible`) | Preview execution и integration tests | Immutable construction + mapper/constructor, без заобикаляне на final полета |
| Record constructor използва `this` преди полетата да са присвоени | JDK28 компилация с preview ON | Рефакторирай към field initialization-first / подходящ explicit canonical constructor |
| Framework/library code прави identity предположения | Тестове на зависимостите на JDK28 EA | Upgrade библиотека, bug report, временно preview OFF |
| JDK 28 feature се бърка с Java 25 LTS | Maven release boundary и отделен EA workflow | Остави основните модули на Java 25 |

При record constructors ограничението е за **компилация** с preview: стар code, вече компилиран без preview, запазва изпълнение на досегашната конструкция. Не твърдим, че всички records автоматично спират да работят.

## 7. Performance: защо flattening НЕ е гарантирано

**Допълнение от [Ask the Architects, ~41–44 мин](https://www.youtube.com/watch?v=ewCPBgOrLAE?t=2460):** за даден `value class` реалното разположение в heap и scalarization/flattening са **HotSpot implementation choices**, не Java spec гаранции и не задължително видими през публично преносимо API. Архитектите споменават JVM-specific debug flags, но **не обявяват нов стандартизиран layout inspector**. Използвай JFR/JOL (с ясни ограничения) и VM diagnostics за конкретен build; не градѝ бизнес correctness или serialized binary format върху наблюдаван layout.


[29:30–35:20 от лекцията](https://youtu.be/ufhs9THbYZI?t=1770) прави най-важното разграничение. **Value semantics** дава възможност на JVM да представя данни по-компактно, но не гарантира **flattening** (съхранение на полетата директно вместо чрез reference). Има ограничения за nullable references, atomicity (атомарност на обновяването), indirection, arrays, boxing/erasure и static type visibility.

Дори с `value record Complex(double real, double imaginary)`:

~~~java
// JDK 28 preview idea: allowed type parameter, NOT promised packed storage.
List<Complex> numbers = new ArrayList<>();
~~~

Понастоящем `ArrayList` е обвързан със стандартния Java generics/erasure модел; **не** обещавай dense packed `Complex` fields. `List<int>`, generalized/specialized generics, null restrictions и user-defined operators са **възможни бъдещи направления**, не JDK 28 preview functionality. Бъдещи издания могат да ги променят или отменят.

При решение `value record` първо попитай **„този domain тип наистина ли няма identity?“**, а не „дали ще е 20% по-бърз“. Сравни с [Big Runtimes](../../jvm/performance-evolution/BIG-RUNTIMES-JIT-ABSTRACTIONS.md), където доказваме, че performance е workload-specific.

## 8. Решение: кога да използвам, кога НЕ, какво печеля/губя

| Подход | Проблем | Кога използваме | Кога НЕ | Печалба ↔ цена |
| --- | --- | --- | --- | --- |
| Java 25 `record` | Immutable, прозрачни business snapshots | Днес, в production | Ако identity/lifecycle и mutable state са централни | Малко boilerplate, stable ↔ reference identity остава |
| JDK 28 `value record` (preview) | Тип без identity и потенциално по-плътно представяне | Обучение/EA compatibility, експерименти със стойностен domain | Production Java 25, identity-based API, нужда от гарантирано flattening | По-ясна семантика ↔ preview instability и ecosystem migration |
| Immutable reconstruction / `withAmount` | Безопасна нова версия на business state | Малки records с invariants | Когато state mutation е част от aggregate lifecycle | Коректност и ясна история ↔ създаване на нов обект |
| Equality/HashMap | Стабилно business grouping | Логически сравними ключове | Ако умишлено ни трябва physical identity | Portable correctness ↔ евентуални equals/hash costs |
| JDK28 dual-mode test | Preview може да промени стар код | При обновяване на runtime/frameworks/libraries | Ако нямаш отделна EA test среда за целия проект | Ранно откриване ↔ допълнителна CI поддръжка |

**По-прост избор:** остани с обикновен Java 25 record и `equals` за business equality; използвай preview Value Objects само след нужда от тяхната **семантика**, а не спекулативно за performance.

## 9. Практични упражнения и Senior checkpoint

1. Каква е разликата между `record` и `value record` и може ли обикновен record да разчита на `==`?
2. Защо `ValuePrice.equals()` и `ValuePrice ==` могат да имат различни резултати?
3. Какво ще стане с `WeakReference<LocalDate>` при preview ON, без да прекомпилираме стария bytecode?
4. Какво става, ако framework синхронизира на `Integer` или `Optional`? Защо е имало warning-и преди Valhalla?
5. Доказва ли `value record`, че `ArrayList<ValueMoney>` съхранява стойностите flat? Защо не?
6. Покажи в [PaymentSnapshot](src/main/java/bg/hristomanov/education/dop/valhalla/PaymentSnapshot.java) как се запазват invariants при „with“ операция и добави негативен JUnit тест.

### Code review checklist

- [ ] Изяснили сме дали domain concept има identity или е стойност?
- [ ] Бизнес сравненията разчитат на `equals` и правилни key semantics, а не на `==`?
- [ ] Няма locks/weak references/reflection final field mutation върху value-based типове?
- [ ] JDK 28 EA preview OFF и ON се тестват отделно?
- [ ] Preview синтаксисът **не** влиза в Java 25 main build?
- [ ] Разграничаваме JDK delivered preview, proposed JEP и далечна идея?
- [ ] Не твърдим flattening/latency/throughput без измерване?

**Изход към входния проблем:** можеш да запазиш ясния immutable domain model още на Java 25, да избегнеш identity ловушките, и да подготвиш миграция към preview без внезапни correctness регресии.

## Оригинални източници

- [Nicolai Parlog, Devoxx Belgium 2026 — Java Next](https://www.youtube.com/watch?v=ufhs9THbYZI); [слайдове от автора](https://slides.nipafx.dev/java-next/2026-10-02-jcp/).
- [Project Valhalla — официални спецификации/примери](https://github.com/openjdk/valhalla-docs).
- [JEP 401 — Value Objects (Preview)](https://openjdk.org/jeps/401).
- [JEP 539 — Strict Field Initialization in the JVM](https://openjdk.org/jeps/539).
- [JDK 28 EA — Value Objects JLS draft](https://download.java.net/java/early_access/jdk28/docs/specs/value-objects-jls.html).
- [OpenJDK Quality Outreach — JDK 28 Value Classes compatibility, 27.10.2026](https://inside.java/2026/10/27/quality-heads-up/).
- [JDK 28 EA downloads](https://jdk.java.net/28/).
- [Java 25 baseline DOP](README.md), [Java Evolution Guide](../../JAVA-EVOLUTION-GUIDE.md), [NotebookLM аудио prompt](VALHALLA-NOTEBOOKLM-PROMPT.md).
