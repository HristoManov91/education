# P1 · Java Method Handles: от Core Reflection до FFM

> **Лекция:** [Teaching an Old Handle New Tricks: From Core Reflection to FFM — Maurizio Cimadamore и Viktor Klang](https://www.youtube.com/watch?v=4ck-T2gaMQ4), Devoxx Belgium 2026, около 51 мин. Анализиран е **предоставеният автоматичен SRT транскрипт**. Кодът, marshalling прототипът и JUnit/JMH тестовете са наши адаптации, **не** демонстрационен код на авторите.  
> **Runtime baseline:** Java 25 LTS, Maven, JUnit 5; няма нови native dependencies в този модул. FFM разширението е в съществуващия [native-interoperability](../native-interoperability/README.md).

## 1. Реален казус — многократно извикваме една и съща динамична операция

В backend application обработваме съобщения с еднаква структура и извикваме динамичен business метод. Прост вариант с `java.lang.reflect.Method` върши работа, но ако при всяко съобщение търсим метода, проверяваме типа му и преобразуваме аргументи, смесваме **подготовката** на операцията с **изпълнението**. Същият въпрос изниква при FFM (native ABI/signature) и при marshalling на известна схема.

**Първоначалният проблем:** как веднъж да проверим достъпа/сигнатурата и да изградим типизирана operation, която после използваме много пъти, без да създадем security loophole или да твърдим недоказан speedup?

```text
BAD:   request -> discover method/schema -> check access/types -> invoke -> repeat discovery
GOOD:  startup/link phase -> check Lookup/MethodType/schema -> cached MethodHandle
                                      |
      request 1 -> invokeExact --------+
      request 2 -> invokeExact --------+  same linked target
      request 3 -> invokeExact --------+
```

**Какво ще научим:** `MethodType`, `MethodHandles.Lookup`, `findVirtual`, `invokeExact` vs `invoke`, `WrongMethodTypeException`, access capabilities, `guardWithTest`, `CallSite` / `StringConcatFactory`, различните exception semantics на Core Reflection и Method Handles, и връзката с FFM и експериментален marshalling.

## 2. Времева карта на оригиналната лекция

| Ориентир от SRT | Демонстрация на авторите | Какво добавя Education |
| --- | --- | --- |
| 02:30–07:30 | Reflection и JVM linkage / различни execution costs | една бизнес операция: direct, cached reflection и handles |
| 07:30–14:00 | `MethodType`, `Lookup`, `invokeExact`; Reflection реимплементиран на MH (Java 18) | actual typed handles, missing access/method, return mismatch и exception tests |
| 15:00–21:30 | String concatenation, `invokedynamic` и CallSite | real StringConcatFactory target + `javap -v` |
| 21:30–28:00 | FFM native downcalls и signature-aware linking | реален `strlen`: cache vs re-link experiment, ABI + concurrency tests |
| 28:00–39:00 | Viktor Klang: развиван marshalling дизайн и record structure | **собствен** строго versioned/allowlisted V1/V2 binary model с JUnit |
| 42:00–48:30 | Combinators, bytecode generation и future code models | `guardWithTest` и decision guide |
| 48:30–51:30 | security/encapsulation/serialized types | negative Lookup tests, no arbitrary classes, unknown versions rejected |

**Граница:** marshalling частта е **проучван проект/идея**, не общодостъпен официален `java.marshalling` API на Java 25. Същото важи и за евентуален Babylon code model → MethodHandle pipeline: отделен evolving project, не runnable стандартен Java 25 API.

## 3. Mental model: какво означава „link once, execute many“

**`MethodType`** описва конкретна JVM сигнатура (return + parameters). `MethodHandles.Lookup` извършва символично търсене и проверка на достъп, след което връща **`MethodHandle` capability**. `invokeExact()` има *signature-polymorphic* bytecode call site: типовете на аргументите **и на return expression-а** трябва да съвпаднат точно. `invoke()` позволява адаптации, еквивалентни на `asType` (например unboxing), но не бива да приемаме, че всяко преобразуване е допустимо.

```java
MethodHandle translate = MethodHandles.lookup().findVirtual(
        Point.class,
        "translate",
        MethodType.methodType(Point.class, int.class, int.class));

// findVirtual adds receiver: (Point,int,int)Point
Point next = (Point) translate.invokeExact(new Point(1, 2), 3, 4);
// Point[x=4, y=6]

// Even with the same dynamic Point, this is a WRONG exact return shape:
// Object wrong = (Object) translate.invokeExact(new Point(1, 2), 3, 4);
```

**Положителен пример:** [`HandleInvocationLab.java`](src/main/java/bg/hristomanov/education/handles/HandleInvocationLab.java) и [JUnit](src/test/java/bg/hristomanov/education/handles/HandleInvocationLabTest.java). В този Java25 пример `Point.translate` използва `Math.addExact` и запазва overflow semantics при всеки dispatch path.

### Code → tests → доказателство

| Problem/contract | Java 25 implementation | JUnit / инструмент |
| --- | --- | --- |
| Direct vs Reflection vs exact/adapted/uncached invocation | [HandleInvocationLab](src/main/java/bg/hristomanov/education/handles/HandleInvocationLab.java), [Point](src/main/java/bg/hristomanov/education/handles/Point.java) | [HandleInvocationLabTest](src/test/java/bg/hristomanov/education/handles/HandleInvocationLabTest.java): exact signature, missing member, overflow/error wrapping |
| Access при link и capability при invoke | [HandleAccessLab](src/main/java/bg/hristomanov/education/handles/HandleAccessLab.java) | [HandleAccessLabTest](src/test/java/bg/hristomanov/education/handles/HandleAccessLabTest.java): public Lookup отказва private; privileged leaked handle работи (**уязвим дизайн, само демонстрация**) |
| Combinators: предварително композирано guard правило | [HandleCombinatorLab](src/main/java/bg/hristomanov/education/handles/HandleCombinatorLab.java) | [HandleCombinatorLabTest](src/test/java/bg/hristomanov/education/handles/HandleCombinatorLabTest.java): boundary/negative parity със стандартен `if` |
| `StringConcatFactory` + `CallSite` | [StringConcatLab](src/main/java/bg/hristomanov/education/handles/StringConcatLab.java) | [StringConcatLabTest](src/test/java/bg/hristomanov/education/handles/StringConcatLabTest.java) + `javap -v` |
| Linked record components и explicit V1/V2 | [VersionedPointMarshaller](src/main/java/bg/hristomanov/education/handles/VersionedPointMarshaller.java) | [VersionedPointMarshallerTest](src/test/java/bg/hristomanov/education/handles/VersionedPointMarshallerTest.java); [детайли и security](MARSHALLING-PROTOTYPE.md) |
| FFM: native downcall link веднъж | [NativeCStringLength](../native-interoperability/src/main/java/bg/hristomanov/education/ffi/NativeCStringLength.java) | [NativeCStringLengthTest](../native-interoperability/src/test/java/bg/hristomanov/education/ffi/NativeCStringLengthTest.java) |
| Performance без фалшив speedup | [MethodDispatchBenchmark](../performance-evolution-benchmarks/src/main/java/bg/hristomanov/education/performance/benchmarks/MethodDispatchBenchmark.java), [FfmLinkageBenchmark](../performance-evolution-benchmarks/src/main/java/bg/hristomanov/education/performance/benchmarks/FfmLinkageBenchmark.java) | [Общ JMH harness](../performance-evolution-benchmarks/README.md), не JUnit speed threshold |

## 4. Лаборатория A — пряк метод, Reflection и MethodHandle

**Naive (но напълно допустим при рядко извикване):** `Point.class.getMethod("translate", int.class, int.class)` и `method.invoke(point, dx, dy)` при всеки request. Можем да кешираме `Method` отделно; **JDK 18+ Reflection сам вече използва Method Handles (JEP 416)** — твърдението „MethodHandle винаги е по-бърз“ е НЕВЯРНО без benchmark.

**Good при повторяем динамичен call site:** `private static final MethodHandle PRELINKED_TRANSLATE`, lookup при initialization, после `invokeExact`. След проверката на достъп handle-ът е *capability*, а не wrapper, който повтаря проверката за права при всяко извикване.

Изпълнение от root:

```bash
mvn -pl java/jvm/method-handles verify

java -cp java/jvm/method-handles/target/classes \
  bg.hristomanov.education.handles.HandleLessonMain
# POINT=Point[x=4, y=6]
# CONCAT=order=widget qty=3
# ROUND_TRIP=PointV2[x=3, y=4, z=5]
```

**Negative case 1:** `invokeExact` върху `(Point,int,int)Point` с очакван `Object` резултат хвърля `WrongMethodTypeException`, независимо че `Point` е `Object`.

**Negative case 2:** при `Math.addExact` overflow `MethodHandle` препраща `ArithmeticException` директно, а `Method.invoke` обвива exception в `InvocationTargetException`. Тези различни contracts са важни при framework error handling.

## 5. Лаборатория B — capability-based security

В [HandleAccessLab](src/main/java/bg/hristomanov/education/handles/HandleAccessLab.java) `publicLookup()` не може да създаде handle за private `Vault.reveal`. Привилегирован nestmate `lookup()` **може**, след което демонстративно връща handle на чужд caller. Този caller може да извика private метода без нов access check.

**Никога не прави публичен API, който връща handle към secret/business authorizations.** Ако прехвърляш handle, прехвърляш и **правото да го използваш**, а не само metadata pointer. Същата идея важи за `MethodHandles.privateLookupIn` и JPMS `opens`: то не е общ bypass на encapsulation, а изисква съответните привилегии.

Полза: капсулиране на безопасно проверена операция. Цена: capability leakage е сериозен security риск. Алтернатива: нормален метод и публичен бизнес API с изрични authorization checks.

## 6. Лаборатория C — combinators и StringConcatFactory

[`HandleCombinatorLab`](src/main/java/bg/hristomanov/education/handles/HandleCombinatorLab.java) показва `MethodHandles.guardWithTest(condition, branchA, branchB)`, което композираме веднъж. **Не** заменяй обикновен `if` с MH граф само заради стила: използвай го, когато вече изграждаш динамична инфраструктура и имаш истински performance/compatibility изисквания.

[`StringConcatLab`](src/main/java/bg/hristomanov/education/handles/StringConcatLab.java) изгражда `CallSite` чрез `StringConcatFactory.makeConcatWithConstants` и извиква target-а многократно. Реалният Java `+` е по-прост и правилен за бизнес код; компилаторът може да използва `invokedynamic` под капака. Можеш да го видиш:

```bash
javap -v -p -classpath java/jvm/method-handles/target/classes \
  bg.hristomanov.education.handles.StringConcatLab \
  | grep -E 'InvokeDynamic|StringConcatFactory|BootstrapMethods'
```

`StringConcatFactory` е **стабилна JDK функционалност от Java 9**, не preview String Templates. Не правим втори курс по `StringBuilder`.

## 7. FFM и marshalling: къде има реална допълнителна полза

В [native-interoperability](../native-interoperability/README.md) запазваме `Linker`/ABI validation и `Arena` per call, но кешираме **само** проверения downcall `MethodHandle` при първото използване. Ако платформата не поддържа `size_t`/symbol, отказваме явно, без да проваляме зареждането на класа с `ExceptionInInitializerError`. Има отделен benchmark вариант, който умишлено прави re-link на всяко извикване, само за сравнение.

В [VersionedPointMarshaller](src/main/java/bg/hristomanov/education/handles/VersionedPointMarshaller.java) откриваме само **два позволени record типа** и създаваме getter/constructor handles **веднъж**. Фиксираният wire формат няма произволни имена на класове и проверява версия/размер преди конструиране. [MARSHALLING-PROTOTYPE.md](MARSHALLING-PROTOTYPE.md) обяснява ограниченията и защо това не е общ Java Serialization replacement.

## 8. Benchmark — измерваме скъпа подготовка срещу многократно изпълнение

```bash
mvn -pl java/jvm/performance-evolution-benchmarks -am -DskipTests package

java --add-modules jdk.incubator.vector \
  -jar java/jvm/performance-evolution-benchmarks/target/benchmarks.jar \
  MethodDispatchBenchmark -wi 3 -i 5 -f 2 -prof gc

# FFM: само ако OS/ABI има 64-bit strlen и е разрешен native access:
java --enable-native-access=ALL-UNNAMED --add-modules jdk.incubator.vector \
  -jar java/jvm/performance-evolution-benchmarks/target/benchmarks.jar \
  FfmLinkageBenchmark -wi 3 -i 5 -f 2 -prof gc
```

`MethodDispatchBenchmark` отчита четири различни форми на еднакъв `Point.translate`: direct, cached Reflection, cached MethodHandle, lookup+invoke на всяка операция. `FfmLinkageBenchmark` отделно сравнява кеширан `strlen` handle срещу per-call native re-link.

**Не сравнявай ns/op на различни бизнес операции или различни OS ABI.** Съхранявай JDK build, CPU/architecture, warm-up, forks, raw results и `-prof gc`. JIT може да inline-не/constant-fold-не различни компоненти; при реална услуга имаме HTTP/DB/I/O/waiting. Това са **хипотези**, а не предварително доказани проценти. [Пълният протокол е тук](../performance-evolution/BENCHMARK-CORRECTNESS.md).

## 9. Decision contract и senior checkpoint

| Избор | Какъв проблем | Кога да | Кога НЕ | Какво печелим | Цена / по-проста алтернатива | Доказателство |
| --- | --- | --- | --- | --- | --- | --- |
| Direct Java | static известна операция | основният случай | runtime-dynamic methods | прост код и JIT optimizations | няма динамични bindings; използвай пряк метод | Point + JUnit |
| Core Reflection | framework introspection, metadata, редки calls | лесен dynamic access | много повторни lookups на hot path без profiling | широко/познато API | boxing, error wrapper; кеширай `Method` | InvocationLabTest |
| MethodHandle | reusable, signature-typed dynamic operation | известна сигнатура, много calls, права при linkage | статичен direct call или недоверен consumer на privileged capability | composition, адаптация, потенциална JIT specialization | strict types, security/diagnostic complexity; `Method` или direct call | handle/error/security JUnit + JMH |
| Generated class/bytecode | сложни съставни операции, където MH граф вече не е подходящ | доказан ограничител и maintenance owner | няма measured need | контрол на генерирания dispatch | class loading/debugging/version debt; започни с MH | нови сравнения, не тази лаборатория |
| FFM downcall cache | много native calls със същия ABI | стабилен символ и signature, профилиран cost | dynamic signature/несъвместима платформа | избягваме repeated linking | native ABI risk; Java SDK/jextract | native JUnit + JMH |
| Versioned marshalling prototype | обучение за linked schema и restrictive wire parsing | затворени/контролирани DTO видове | произволен untrusted object graph, production format | small verified contract | schema migration/compatibility costs; Jackson/Protobuf | V1/V2+negative tests |

**Край на входния казус:** умеем да извлечем скъпото discovery/linkage от цикъла, да проверим сигнатури, достъп и business semantics, и да измерим различните execution варианти. Не можем да обещаем, че всеки MethodHandle е по-бърз, нито че експерименталният marshalling проект вече е стандартен.

**Self-check / review:** Какви са exact argument **и return** types? Кой има достъп до `Lookup` и вече създадения handle? Кешираме ли само safe reusable linkage, но не и краткоживеещ MemorySegment? Как се различават target exception и Reflection wrapper? Запазен ли е validation/ABI контрактът? Измерили ли сме JIT/profile ефекта? Коя външна спецификация оправдава binary schema? Има ли negative tests и rollback?

## Оригинални източници

- [Maurizio Cimadamore & Viktor Klang — Devoxx 2026 talk](https://www.youtube.com/watch?v=4ck-T2gaMQ4) — user-provided automatic SRT.
- [JEP 416 — Reimplement Core Reflection with Method Handles (Java 18)](https://openjdk.org/jeps/416).
- [MethodHandle — Java 25](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/invoke/MethodHandle.html) · [Lookup and access control](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/invoke/MethodHandles.Lookup.html).
- [MethodHandles combinators — Java 25](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/invoke/MethodHandles.html).
- [StringConcatFactory — Java 25](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/invoke/StringConcatFactory.html).
- [Linker and FFM API — Java 25](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/foreign/Linker.html).
- [OpenJDK JMH](https://github.com/openjdk/jmh).
