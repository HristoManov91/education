# P1 · Project Valhalla: Value Objects, Identity и JDK 28 Compatibility

> **Java 25 — runnable JUnit + Java 28 Early Access — отделен preview lab** · Mid+ → Early Senior · 45–60 минути + експерименти.  
> **Допълнителен deep dive:** [José Paumard и Rémi Forax — Will Valhalla Fix Tony's Billion Dollar Mistake?](https://www.youtube.com/watch?v=vLDbzmiT7Xs), предоставен SRT (~2:40:57), и раздел 7.1.
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
| JDK28 `LoadableDescriptors`: същият Java25 source, нов compiler metadata | [LoadableDescriptorsProbe.java](jdk28-ea/LoadableDescriptorsProbe.java) / [verify-valhalla-layout.sh](jdk28-ea/verify-valhalla-layout.sh) | preview ON/OFF classfile comparison и same business output |
| Nullable value object с два payload размера | [NullableFlatLayoutPreview.java](jdk28-ea/NullableFlatLayoutPreview.java) | JDK28 EA positive assertions; **без** гаранция за flattening |
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

### 7.1 Ново P1 допълнение — „Will Valhalla Fix Tony's Billion Dollar Mistake?“ (Paumard / Forax)

**Източник:** [José Paumard и Rémi Forax, Devoxx 2026](https://www.youtube.com/watch?v=vLDbzmiT7Xs), предоставен auto-generated SRT (~2:40:57); по-долу съпоставяме техните примери с [JEP 401 / JDK 28 EA спецификацията](https://cr.openjdk.org/~dlsmith/jep401/jep401-20260717/specs/value-objects-jvms.html), защото езикът на лекцията понякога опростява JVM ограниченията. **Това не е нов JVM benchmark, нито production Java25 API.**

#### A. Scalarization, nullable heap flattening и atomicity — три различни механизма

| Тема | Какво реално означава | Къде се вижда |
| --- | --- | --- |
| **JIT scalarization** | Компилаторът може да представя value object като отделни компоненти и да избегне някои heap allocations или междинни wrappers. Това **не** е равнозначно на „всеки value object стои на stack“. | В [JMH/JIT обучението](../../jvm/performance-evolution/BIG-RUNTIMES-JIT-ABSTRACTIONS.md), само след измерване |
| **Heap flattening** | Полета или елементи на масив могат да съдържат самите value components вместо reference към отделен heap object. При правилни условия locality и memory footprint са по-добри. | JDK 28 EA HotSpot implementation-specific; **не** Java language guarantee |
| **Nullable marker** | `value record` и `value class` са *reference types*; поле/масив с такъв тип **може да е `null`**. Flat representation се нуждае от marker/padding, освен ако nullity не се управлява другояче. | [JEP401 JVMS / LoadableDescriptors](https://cr.openjdk.org/~dlsmith/jep401/jep401-20260717/specs/value-objects-jvms.html) |
| **Atomicity** | Когато mutable holder/array е едновременно достъпен от нишки, новата цяла стойност трябва да се вижда атомарно, не като смес от стари/нови полета. Това ограничава допустимия flat layout. | HotSpot `NULLABLE_ATOMIC_FLAT` vs `NULLABLE_NON_ATOMIC_FLAT` избори |

Лекторите показват `Song(title, duration)`. При **compressed 32-bit reference** за title, `short duration` заема 16 бита, плюс marker от 8 бита: **32+16+8 = 56 bits**, които *могат* да се поберат в 64-bit atomic word. Замени `short` с `int` и получаваш **32+32+8 = 72 bits** — този **nullable atomic** layout вече не се побира в 64 bits. Това е илюстрация за конкретен HotSpot/CPU/compressed-reference model, **не универсален Java типово-семантичен праг**.

```text
При разглеждан 64-bit atomic storage word (илюстрация):

NARROW value:  compressed reference 32 | short 16 | null marker 8 = 56 bits
WIDE value:    compressed reference 32 | int   32 | null marker 8 = 72 bits
                                                  → не се побира атомарно
```

**Съществено уточнение спрямо устното опростяване на 98–101 мин:** „над 64 бита никога не се flatten-ва“ **не е вярно за всички полета**. Оптимизации като `NULLABLE_NON_ATOMIC_FLAT` може да се използват за **строго инициализирани final полета**, за които не съществува състезание между четене и следващ запис. Това е обяснено и в [OpenJDK промяната за final layouts (JDK-8374800)](https://bugs.openjdk.org/browse/JDK-8374800). Изборът на layout и exact byte count може да се променя с EA build, CPU, flags, GC и клас. Не кодирай business logic, сериализиран двоичен формат или SLA върху него.

#### B. Защо flattening може да е *по-бавно*

**[~103–108 мин](https://youtu.be/vLDbzmiT7Xs?t=6180):** Paumard и Forax сравняват малък масив (~100 елемента) и голям (~1 млн.). При малък масив pointer-based достъпът в техния сценарий може да спечели от липсата на маски/shift/unpacking; при голям масив dense storage компенсира цената на unpacking чрез по-добра cache locality и по-малко pointer chasing. Самите автори подчертават **trade-off**, а не универсален победител. В транскрипта има обсъждане на точка на пресичане между около 1k и 10k елемента **за конкретния им benchmark**, не общ JDK лимит.

**Същински performance капан:** Java `Stream` и generic `List<T>` могат да имат различен boxing/reference representation от специализиран `T[]`; microbenchmark на едното **не доказва** ефективността на другото. GC pressure, compressed oops, CPU cache, warm-up/JIT, null-marker decoding, allocation и data size трябва да се сравняват отделно.

**Не препоръчваме:** да сменяш `int` с `short` в реалния бизнес модел само заради възможен flattening. Така може да нарушиш domain range, serialization contracts или да прехвърлиш проблем към overflow. Препоръката е: първо domain correctness, после dataset-specific benchmark.

#### C. Кога прекомпилацията помага, без да е необходима за коректност

**[~115–123 мин](https://youtu.be/vLDbzmiT7Xs?t=6900):** Java class loading е отложен (lazy); без допълнителни подсказки JVM може да не знае, че полето използва value class, когато трябва да избере storage layout. Новият `LoadableDescriptors` class-file attribute **позволява, но не задължава**, JVM да зареди типа по-рано и да разгледа flat encoding. **Стар Java25 bytecode може да остане семантично правилен**, но нова JDK28 preview компилация може да отвори по-добър optimization path.

**Реално proof в същия Education модул:** [JDK 28 EA — LoadableDescriptors сравнение](JDK28-EA-LAB.md#p2--loadabledescriptors-един-изходен-код-два-class-file-режима) с [Java25-compatible probe](jdk28-ea/LoadableDescriptorsProbe.java), [preview value-record nullable сценарий](jdk28-ea/NullableFlatLayoutPreview.java) и [скрипт](jdk28-ea/verify-valhalla-layout.sh), който изпълнява и сравнява bytecode metadata чрез `javap -verbose`. **Класовото metadata е observable; физическият layout/ускорение не се доказват от attribute-а.**

**Официална дефиниция:** [JEP401 JVMS `4.7.32](https://cr.openjdk.org/~dlsmith/jep401/jep401-20260717/specs/value-objects-jvms.html) допуска class/interface descriptors за early loading. `LoadableDescriptors` не е reflection metadata contract, който принуждава VM да flatten-ва.

#### D. Tony Hoare's null mistake остава — разграничение от бъдещите идеи

[~129–158 мин](https://youtu.be/vLDbzmiT7Xs?t=7740): изследват се **два еволюционни варианта**, които **не са JDK 28 preview features**:

1. **`Song!`**: изследван видим non-null тип/оператор. Той би могъл да променя overload resolution и API evolution: ако библиотека промени сигнатурата и потребител прекомпилира кода, избраният overload може да се смени. Лекторите отбелязват опасността „работи без прекомпилация; при нова компилация поведението е различно“. Не го представяме като одобрен Java синтаксис.
2. **Primitive classes / primitive values**: проучвана възможност за класове без identity **и** с default zero-like value, която би улеснила non-null storage и някои non-atomic layouts, но изисква компромиси с encapsulation и binary compatibility. Не е част от JEP401/539. Потенциални future оператори/специализирани generics също не са Java28 feature.

**Ключовата поука:** `value record` днес не означава `null-free`. [P3 Valhalla/Null future-watch](../../JAVA-NEXT-TECH-RADAR.md#p3--valhalla-null-restricted-storage-и-primitive-classes) отбелязва proposal status. В Java 25 устойчивото решение е explicit constructor/validation за required business fields и `Objects.requireNonNull`, а не синтаксис, който JDK25/28 все още няма.

#### Decision contract: подходящата стратегия

| Избор | Кога | Кога НЕ | Печалби | Загуби / по-просто | Runnable proof |
| --- | --- | --- | --- | --- | --- |
| Стандартен Java25 `record` + validation | Стабилен DTO/domain contract днес | Ако същинската същност е identity/lifecycle | Ясни инварианти | Няма гарантирано flattening; [MoneySnapshot](src/main/java/bg/hristomanov/education/dop/valhalla/MoneySnapshot.java) | [ValhallaReadinessTest](src/test/java/bg/hristomanov/education/dop/valhalla/ValhallaReadinessTest.java) |
| JDK28 preview `value record` | Изследваш identity-free semantics и JDK upgrade | Java25 production и object identity/locks/weak refs | Potential layout/JIT advantages | Preview/compatibility риск, няма обещан speedup; обикновен `record` | [EA integration](JDK28-EA-LAB.md) |
| LoadableDescriptors / recompile | Валидираш class-file hints при миграция към JDK28 | Искаш portable guaranteed field layout | JVM може да опознае field type по-рано | Compiler/toolchain dependency; simple correctness regression tests | [JDK28 script](jdk28-ea/verify-valhalla-layout.sh) |
| Null-restricted / primitive proposal | Architectural watch, бъдещ JEP design | Като днешен runnable syntax, non-null business validation | Може да подобри storage | Силни language/binary compatibility trade-offs; explicit validation днес | **Няма runnable production feature**, само [P3 radar](../../JAVA-NEXT-TECH-RADAR.md) |

**Упражнения:**
1. Защо 32+16+8 bits могат да се поберат в един atomic word, а 32+32+8 не? Кога това изчисление не е релевантно?
2. Защо `final` строго инициализирано поле може да има различни atomicity constraints от mutable holder?
3. Защо 100-item и 1m-item benchmark могат да дадат противоположно класиране на flat/indirect layouts?
4. Какво показва `javap LoadableDescriptors` и какво **не** доказва?
5. Как `Song!` би могъл да наруши source/API evolution и защо не го въвеждаме в Java28 обучителния source?
6. При Spring Boot + Oracle: имаме бавна SQL заявка. Какъв проблем (ако изобщо) решава Valhalla? Защо не сменяме integer types без profiling и domain validation?

**Статус:** P1 концепции от лекцията, P2 реален JDK28 EA class-file/JUnit-style CLI proof и P3 future watch. Няма нов P1 course, няма JMH speedup „потвърден“ от нас.

---

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
