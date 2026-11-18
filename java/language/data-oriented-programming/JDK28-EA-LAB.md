# Project Valhalla — JDK 28 EA: preview OFF vs ON

> Самостоятелна изпълнима лаборатория за [P1 Valhalla](VALHALLA-VALUE-OBJECTS.md). Не е част от Java 25 Maven reactor. Изисква **OpenJDK 28 EA**, не само Java 25 или Java 27.
>
> **Автоматизирана проверка:** [.github/workflows/valhalla-jdk28-ea.yml](../../../.github/workflows/valhalla-jdk28-ea.yml) (PR с промени в DOP + ръчен workflow_dispatch).

## Защо: еднакъв class file, различни runtime резултати

Според [официалния OpenJDK compatibility heads-up от 27.10.2026](https://inside.java/2026/10/27/quality-heads-up/) около 30 JDK класове стават value classes при `--enable-preview` на JDK 28, дори когато нашият код е компилиран *преди* JDK 28.

**Практически сценарий:** бизнес събития с `LocalDate` в кеш, framework поддържа `WeakReference<LocalDate>`. На стара JVM работи, при preview ON може да хвърли `IdentityException`.

## Изпълни върху JDK 28 EA

Свали EA JDK от [jdk.java.net/28](https://jdk.java.net/28/) или постави JDK 28 EA в IntelliJ SDK; провери `java -version` и `javac -version` да показват *28-ea*. Не е предназначено за production.

От директорията `java/language/data-oriented-programming`:

~~~bash
mkdir -p target/valhalla-25 target/valhalla-28

# Един и същ Java 25 class file за двата режима на JDK 28:
javac --release 25 -d target/valhalla-25 jdk28-ea/IdentityPreviewProbe.java

java -cp target/valhalla-25 IdentityPreviewProbe identity
java --enable-preview -cp target/valhalla-25 IdentityPreviewProbe value

# Синтаксисът value record изисква именно JDK 28 preview:
javac --release 28 --enable-preview -d target/valhalla-28 jdk28-ea/ValueObjectsPreview.java
java --enable-preview -cp target/valhalla-28 ValueObjectsPreview
~~~

Очаквани success markers (без speedup или time assertions):

~~~text
PASS: JDK 28 preview OFF equality / WeakReference behavior
PASS: JDK 28 preview ON equality / WeakReference behavior
PASS: JDK 28 value records / statewise == / record.equals
~~~

**Проверка за версия:** ако подадеш JDK 25 на `--release 28`, compilation ще се провали — това е желано разделение на toolchains.

## README → код

| Въпрос | Файл | Proof |
| --- | --- | --- |
| Java 25 bytecode работи ли различно с preview? | [IdentityPreviewProbe.java](jdk28-ea/IdentityPreviewProbe.java) | runtime assert + preview OFF/ON в workflow |
| `value record` и `==` | [ValueObjectsPreview.java](jdk28-ea/ValueObjectsPreview.java) | EA preview compile, standalone executable assertions |
| Бизнес логика без reference identity | [MoneySnapshot.java](src/main/java/bg/hristomanov/education/dop/valhalla/MoneySnapshot.java), [ValueSafeOperations.java](src/main/java/bg/hristomanov/education/dop/valhalla/ValueSafeOperations.java) | [ValhallaReadinessTest.java](src/test/java/bg/hristomanov/education/dop/valhalla/ValhallaReadinessTest.java) (Java 25 JUnit) |

### Допълнителен regression test: record constructor source breakage

[LegacyRecordConstructorProbe.java](jdk28-ea/LegacyRecordConstructorProbe.java) е **умишлен legacy antipattern**: compact constructor извиква `this.toString()`, преди compiler-generated component assignment. Една и съща source програма се компилира с Java 25 mode, но трябва да бъде отхвърлена при Java 28 preview compilation. Вече компилираният без preview class file **продължава да се изпълнява** при JDK28 с preview ON.

~~~bash
# JDK 28 Early Access, от директорията на DOP модула
javac --release 25 -d target/valhalla-25 jdk28-ea/LegacyRecordConstructorProbe.java
java --enable-preview -cp target/valhalla-25 LegacyRecordConstructorProbe

# EXPECTED COMPILATION ERROR — early access to this:
javac --release 28 --enable-preview -d target/valhalla-28 jdk28-ea/LegacyRecordConstructorProbe.java
~~~

Точно този expected-failure contract се проверява и в [GitHub Actions workflow](../../../.github/workflows/valhalla-jdk28-ea.yml). Не го мести в `src/main/java` — целта е да моделира source incompatibility, не production-код.

## P2 — LoadableDescriptors: един изходен код, два class-file режима

**Източници:** [Paumard & Forax, Devoxx 2026, ~115–123 мин](https://youtu.be/vLDbzmiT7Xs?t=6900), проверено със [JEP 401 — JVMS `4.7.32](https://cr.openjdk.org/~dlsmith/jep401/jep401-20260717/specs/value-objects-jvms.html). Това е **реално изпълнимо JDK 28 Early Access сравнение**, а не Java 25 root Maven тест и **не benchmark**.

### Казус

Нашият backend държи `LocalDate` в `Schedule`. Логическият contract е „две равни дати са равни по стойност“ и остава правилен независимо от компилатора. Но JVM може да се възползва от ранно зареждане на типа, за да избере потенциално по-ефективен field layout. Как JVM разбира, че полето е value class?

`LoadableDescriptors` е class-file attribute с type descriptors, който **позволява** на VM да зареди типовете по-рано; **не налага** определен layout и **не променя** логическата семантика.

### README → код → проверка

| Сценарий | Файл | Automated check |
| --- | --- | --- |
| **Java25-compatible source**: `Schedule` има `LocalDate day`; компилираме *един и същи файл* в два режима | [LoadableDescriptorsProbe.java](jdk28-ea/LoadableDescriptorsProbe.java) | `--release 25` vs `--release 28 --enable-preview`; два `main` execution-а |
| **JDK28 preview value records**: `NarrowSong` (short) и `WideSong` (int), и двете **nullable** като field и array element | [NullableFlatLayoutPreview.java](jdk28-ea/NullableFlatLayoutPreview.java) | Preview compile/run, constructor validation, nullable array/fields, equality |
| Real bytecode metadata | [verify-valhalla-layout.sh](jdk28-ea/verify-valhalla-layout.sh) | `javap -verbose -private`: old class **няма** attr; new preview classes **имат** attr |
| CI, без нов workflow | [valhalla-jdk28-ea.yml](../../../.github/workflows/valhalla-jdk28-ea.yml) | GitHub Actions ползва официален JDK28 EA; без Java25 Maven промени |

**Ръчно стартиране**, ако вече имаш JDK 28 EA:

~~~bash
java -version            # must show 28-ea / 28
bash java/language/data-oriented-programming/jdk28-ea/verify-valhalla-layout.sh
~~~

Скриптът компилира `LoadableDescriptorsProbe.java` с `--release 25` и с `--release 28 --enable-preview`, стартира двете версии на JDK28 EA **с включен preview**, после проверява **точния classfile `LoadableDescriptors:` атрибут**, включително на вложения `Schedule` с `Ljava/time/LocalDate;`. Той не зависи от JVM-specific `-XX` layout output; ако compiler behavior се промени в следващ EA build, CI трябва да спре, вместо да „предполага“ атрибута.

**Защо имаме две компилации, но един runtime preview режим?** За да изолираме влиянието на **class-file metadata** от влиянието на `--enable-preview` върху built-in JDK класовете. Съществуващият [IdentityPreviewProbe](jdk28-ea/IdentityPreviewProbe.java) прави *другия* експеримент — **един и същ стар class file**, preview OFF vs ON. Двата експеримента не трябва да се смесват.

### Къде започва и свършва доказателството

- **Доказано:** `javac` може да включи допълнително metadata при preview compilation; старият bytecode е изпълним и семантично коректен за този input; `value records` остават nullable.
- **НЕ доказано:** HotSpot непременно е flatten-нал полето или масива; производителност при конкретна архитектура; че числото 56/64 бита важи за произволни CPU/GC/config; че EA preview API е окончателен.
- **Нюанс:** спецификацията казва, че VM **може** (`may`) да зареди описаните classes рано. `javap` дава доказателство за metadata, не резултат от GC/heap layout experiment.
- **Съхранение и atomicity:** mutable nullable fields и arrays са под atomicity constraints; строго инициализирани final value fields може да имат различен `NULLABLE_NON_ATOMIC_FLAT` layout. Виж [P1 Null marker + atomicity](VALHALLA-VALUE-OBJECTS.md#71-ново-p1-допълнение--will-valhalla-fix-tonys-billion-dollar-mistake-paumard--forax).

### Контролни въпроси

1. Защо metadata не е равнозначно на действително flattening?
2. Ако class е компилиран с Java25, трябва ли да бъде прекомпилиран, за да е коректен при JDK28 preview? (Не винаги; говорим за възможност за оптимизация, не correctness contract.)
3. Защо `value record` не премахва `null` от `Song[]`?
4. Кога смяна от `int` на `short` е неприемлива в бизнес DTO въпреки възможни layout ползи?
5. Какъв JDK28 compatibility тест се изисква при излизане от EA и обновяване на framework dependencies?

---

## Какво доказва и какво не

- Доказва runtime различия на `LocalDate` при `==` и `WeakReference` за **текущия изпълнен EA build**, и разграничението `equals`/statewise `==`.
- Не доказва performance, flattening, storage layout или окончателна стабилност на preview API.
- Workflow може да се счупи при промяна на EA build/spec или ако vendor temporarily премахне EA archive — това е сигнал за преглед, не основание да променяме Java 25 baseline.
- Промяната на preview ON може да влияе на **вече компилиран** Java 25 bytecode; този риск е принципно различен от нова compile-time keyword feature.

## Как да поправим unsafe record constructor

Прегледай [официалните ограничения за record constructors](https://inside.java/2026/10/27/quality-heads-up/#record-constructors). Ако compact constructor извика instance method преди initialized fields, компилацията с JDK28 preview ON може да откаже. Решението е да не използва `this` до завършване на инициализацията или да се преработи с explicit canonical constructor. Не превръщай този отрицателен пример в Java 25 production source.

## Original sources

- [JEP 401 — Value Objects Preview](https://openjdk.org/jeps/401) · [JEP 539 — Strict Field Initialization](https://openjdk.org/jeps/539).
- [JDK 28 EA JLS draft](https://download.java.net/java/early_access/jdk28/docs/specs/value-objects-jls.html).
- [OpenJDK Quality Outreach, Oct 27 2026](https://inside.java/2026/10/27/quality-heads-up/).
- [Ron Pressler — Big Runtimes](../../jvm/performance-evolution/BIG-RUNTIMES-JIT-ABSTRACTIONS.md).
