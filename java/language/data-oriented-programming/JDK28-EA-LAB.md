# Project Valhalla — JDK 28 EA: preview OFF vs ON

> Самостоятелна изпълнима лаборатория за [P1 Valhalla](VALHALLA-VALUE-OBJECTS.md). Не е част от Java 25 Maven reactor. Изисква **OpenJDK 28 EA**, не само Java 25 или Java 27.
>
> **Автоматизирана проверка:** [.github/workflows/valhalla-jdk28-ea.yml](../../../.github/workflows/valhalla-jdk28-ea.yml) (PR с промени в DOP + ръчен workflow_dispatch).

## Защо: еднакъв class file, различни runtime резултати

Според [официалния OpenJDK compatibility heads-up от 27.10.2026](https://inside.java/2026/10/27/quality-heads-up/) около 30 JDK класове стават value classes при \`--enable-preview\` на JDK 28, дори когато нашият код е компилиран *преди* JDK 28.

**Практически сценарий:** бизнес събития с \`LocalDate\` в кеш, framework поддържа \`WeakReference<LocalDate>\`. На стара JVM работи, при preview ON може да хвърли \`IdentityException\`.

## Изпълни върху JDK 28 EA

Свали EA JDK от [jdk.java.net/28](https://jdk.java.net/28/) или постави JDK 28 EA в IntelliJ SDK; провери \`java -version\` и \`javac -version\` да показват *28-ea*. Не е предназначено за production.

От директорията \`java/language/data-oriented-programming\`:

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

**Проверка за версия:** ако подадеш JDK 25 на \`--release 28\`, compilation ще се провали — това е желано разделение на toolchains.

## README → код

| Въпрос | Файл | Proof |
| --- | --- | --- |
| Java 25 bytecode работи ли различно с preview? | [IdentityPreviewProbe.java](jdk28-ea/IdentityPreviewProbe.java) | runtime assert + preview OFF/ON в workflow |
| \`value record\` и \`==\` | [ValueObjectsPreview.java](jdk28-ea/ValueObjectsPreview.java) | EA preview compile, standalone executable assertions |
| Бизнес логика без reference identity | [MoneySnapshot.java](src/main/java/bg/hristomanov/education/dop/valhalla/MoneySnapshot.java), [ValueSafeOperations.java](src/main/java/bg/hristomanov/education/dop/valhalla/ValueSafeOperations.java) | [ValhallaReadinessTest.java](src/test/java/bg/hristomanov/education/dop/valhalla/ValhallaReadinessTest.java) (Java 25 JUnit) |

### Допълнителен regression test: record constructor source breakage

[LegacyRecordConstructorProbe.java](jdk28-ea/LegacyRecordConstructorProbe.java) е **умишлен legacy antipattern**: compact constructor извиква \`this.toString()\`, преди compiler-generated component assignment. Една и съща source програма се компилира с Java 25 mode, но трябва да бъде отхвърлена при Java 28 preview compilation. Вече компилираният без preview class file **продължава да се изпълнява** при JDK28 с preview ON.

~~~bash
# JDK 28 Early Access, от директорията на DOP модула
javac --release 25 -d target/valhalla-25 jdk28-ea/LegacyRecordConstructorProbe.java
java --enable-preview -cp target/valhalla-25 LegacyRecordConstructorProbe

# EXPECTED COMPILATION ERROR — early access to this:
javac --release 28 --enable-preview -d target/valhalla-28 jdk28-ea/LegacyRecordConstructorProbe.java
~~~

Точно този expected-failure contract се проверява и в [GitHub Actions workflow](../../../.github/workflows/valhalla-jdk28-ea.yml). Не го мести в \`src/main/java\` — целта е да моделира source incompatibility, не production-код.

## Какво доказва и какво не

- Доказва runtime различия на \`LocalDate\` при \`==\` и \`WeakReference\` за **текущия изпълнен EA build**, и разграничението \`equals\`/statewise \`==\`.
- Не доказва performance, flattening, storage layout или окончателна стабилност на preview API.
- Workflow може да се счупи при промяна на EA build/spec или ако vendor temporarily премахне EA archive — това е сигнал за преглед, не основание да променяме Java 25 baseline.
- Промяната на preview ON може да влияе на **вече компилиран** Java 25 bytecode; този риск е принципно различен от нова compile-time keyword feature.

## Допълнително упражнение: record constructor

Прегледай [официалните ограничения за record constructors](https://inside.java/2026/10/27/quality-heads-up/#record-constructors). Ако compact constructor извика instance method преди initialized fields, компилацията с JDK28 preview ON може да откаже. Решението е да не използва \`this\` до завършване на инициализацията или да се преработи с explicit canonical constructor. Не превръщай този отрицателен пример в Java 25 production source.

## Original sources

- [JEP 401 — Value Objects Preview](https://openjdk.org/jeps/401) · [JEP 539 — Strict Field Initialization](https://openjdk.org/jeps/539).
- [JDK 28 EA JLS draft](https://download.java.net/java/early_access/jdk28/docs/specs/value-objects-jls.html).
- [OpenJDK Quality Outreach, Oct 27 2026](https://inside.java/2026/10/27/quality-heads-up/).
- [Ron Pressler — Big Runtimes](../../jvm/performance-evolution/BIG-RUNTIMES-JIT-ABSTRACTIONS.md).
