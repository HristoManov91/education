# P2/P3 — Project Crema: Byte Buddy и класове, генерирани след Native Image build

> **Реална JVM лаборатория с JUnit**, плюс **експериментален ръчен native build**. Източник: Alina Yurenko и Thomas Wuerthinger, Devoxx 2026, около 20:00–35:25.

## Реален проблем: closed-world assumption (затворен свят)

Имаме framework, който по време на изпълнение генерира нов bytecode чрез Byte Buddy. Класът **не е съществувал при native компилацията**. Класическата Native Image схема трябва да познава предварително класовете, които приложението ще използва. Обикновено JVM няма подобно ограничение.

**Bad:** приемаме, че добавянето на reflection JSON за един клас разрешава и произволно генериране на нови класове. Това са **различни проблеми**. Reachability metadata за вече съществуващ клас ≠ runtime class definition.

**Good:** първо създаваме класове със случайно променяща се идентичност и доказваме JVM семантика; после опитваме Native Image с експерименталното `-H:+RuntimeClassLoading` и записваме ограниченията и реалните резултати. Не обещаваме production готовност.

## Какво показват лекторите

- 20–24 мин: open-world base compilation и Byte Buddy, което генерира ново име за клас на всяко стартиране.
- 25–27 мин: интерпретатор за новия Java bytecode и възможен JIT при достатъчно често изпълнение.
- 27–30 мин: Java source launcher върху предварително компилиран JDK и `javac`; това е демонстрация на конкретен build, **не стандартна Java 25 команда**.
- 30–35 мин: специализиран предварително компилиран Micronaut runtime, който зарежда променящи се приложения от source или JAR. Това **не** означава, че обикновен Spring Boot JAR автоматично работи по същия начин.

Лекторите дават числа (около 10/35/64/150 ms) от различни demo сценарии. **Не ги смесваме** и не ги използваме като наши performance гаранции.

## README → код

| Цел | Код | Доказателство |
| --- | --- | --- |
| Runtime-generated class с Byte Buddy | [`CremaDemo.java`](crema-lab/src/main/java/bg/hristomanov/education/graalvm/crema/CremaDemo.java) | [`CremaDemoTest.java`](crema-lab/src/test/java/bg/hristomanov/education/graalvm/crema/CremaDemoTest.java) |
| Експериментален native profile | [`crema-lab/pom.xml`](crema-lab/pom.xml) | Отделен ръчен GraalVM Native Image build, **не** CI proof |

Кодът използва `new ByteBuddy()`, дефинира метод `announce()`, зарежда генерирания клас с `ClassLoadingStrategy.Default.WRAPPER` и го извиква. С `AtomicLong` генерираме уникално име при всяко извикване. Това е **конкретен нов клас**, не reflection към предварително известен Java class.

### Стъпка 1. JVM тестове, без GraalVM

От корена на Education:

```bash
mvn -pl java/jvm/graalvm-native-image/crema-lab -am test
cd java/jvm/graalvm-native-image/crema-lab
mvn -DskipTests package

java -cp target/classes:$(find ~/.m2/repository/net/bytebuddy/byte-buddy/1.18.17 -name 'byte-buddy-1.18.17.jar' -print -quit) \
  bg.hristomanov.education.graalvm.crema.CremaDemo Alina Devoxx
```

На Windows/macOS classpath разделителят може да е различен. По-лесният вариант е да изпълниш `CremaDemo.main()` директно от IntelliJ след Maven import; така IDE подава правилния dependency classpath.

Положителни и отрицателни tests: коректен announcement, **различни class names** при последователни runtime calls и отказ за невалидни входове.

### Стъпка 2. Native експеримент — само manual

Изисква конкретна GraalVM дистрибуция/версия с активна `RuntimeClassLoading` поддръжка (за първи път демонстрирана в 25.1) и съвместим native-image toolchain. Не всички features на Byte Buddy са гарантирано поддържани.

```bash
cd java/jvm/graalvm-native-image/crema-lab
native-image --version
mvn -Pcrema-native -DskipTests package
./target/crema-greeting Alina Devoxx
```

В профила има `-H:+RuntimeClassLoading`. Дори native build да мине, **провери действително ново име и runtime output** и добави native regression tests за нужните classloader сценарии. Ако build не мине заради конкретна GraalVM/Byte Buddy версия или unsupported API, материалът остава маркиран като експеримент, а не заглушаваме грешката.

**JIT е отделна опция:** в някои GraalVM версии може да се активира `-H:+GraalJITCompileAtRuntime` (известен и като Ristretto в документацията). Не го включваме по подразбиране, защото има runtime resource и compatibility trade-offs; не бива да твърдим, че всяко runtime-loaded method автоматично се JIT компилира.

## Decision card

| Въпрос | Отговор |
| --- | --- |
| Проблем | Нужда от класове, непознати при AOT build |
| Използвай | Plug-in architecture, dynamic Byte Buddy класове; R&D spike с конкретна GraalVM версия |
| Не използвай | Стандартен Spring CRUD, production SLA без доказан native compatibility |
| Печелим | Native compiled stable base + нов runtime bytecode |
| Губим | Interpreter overhead, избор за JIT и GC, сложна debug/config/agent матрица |
| По-просто | JVM + Leyden; статични Java класове/records; build-time generated code |
| Доказателство | [JUnit тестове за Byte Buddy](crema-lab/src/test/java/bg/hristomanov/education/graalvm/crema/CremaDemoTest.java) + реални native build/runtime logs |

### Production предупреждения

- Динамично зареденият код не е sandbox; не позволявай непроверен input да генерира произволен executable code.
- Dependency version, ClassLoader parent strategy, reflection/resources и native access permissions са важни отделно.
- За workaround с instrumentation/class transformation разгледай [Java Agents lab](../java-agent-instrumentation/README.md); `RuntimeClassLoading` не прави всички agent APIs автоматично съвместими.
- JIT/dynamic bytecode може да променят startup, memory и steady throughput. Не разчитай на 10 ms от сцената.

## Източници

[Alina Yurenko и Thomas Wuerthinger — GraalVM Next](https://www.youtube.com/watch?v=OLQ98Ko4p3Q), потребителски SRT 20–35 мин; [GraalVM 25.1 release notes](https://www.graalvm.org/release-notes/25.1/); [GraalVM 25.3 release notes](https://www.graalvm.org/release-notes/25.3/); [GraalVM Native Image metadata](https://www.graalvm.org/latest/reference-manual/native-image/metadata/).
