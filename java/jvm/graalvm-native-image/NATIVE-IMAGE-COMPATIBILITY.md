# P2 — Native Image Compatibility: reflection, metadata и AI

> **Източник в транскрипта:** Alina Yurenko / Thomas Wuerthinger, 07:00–11:30 — AI-assisted native compatibility и Reachability Metadata Repository. Нашата Java 25 лаборатория е учебно разширение, а не копие от сцената.

## Реален казус / защо съществува темата

При JVM динамично име на клас от конфигурация може да бъде заредено чрез `Class.forName()`, а public constructor — извикан с reflection (динамичен достъп). Native Image статично анализира достижимите класове. Ако не знае, че приложението ще поиска този constructor, JVM тестът може да минава, а native executable да се проваля.

**Bad:** генерираме metadata с tracing agent от един happy-path и приемаме, че всички комбинации на configuration/plugins вече работят. Agent вижда само реално изпълнените code paths.

**Good:** конкретна metadata, allowlist на динамичния вход, JVM + native positive/negative tests и version-specific provider verification.

## README → код

| Концепция | Клас / metadata | Проверка |
| --- | --- | --- |
| Разрешен dynamic class name | [`GreetingFactory.java`](src/main/java/bg/hristomanov/education/graalvm/GreetingFactory.java) | [`RuntimeGreetingAppTest.java`](src/test/java/bg/hristomanov/education/graalvm/RuntimeGreetingAppTest.java) |
| Reflectively created class | [`ReflectionGreeting.java`](src/main/java/bg/hristomanov/education/graalvm/ReflectionGreeting.java) | test за еднакъв бизнес резултат при static/reflection |
| Reachability metadata | [`reachability-metadata.json`](src/main/resources/META-INF/native-image/bg.hristomanov.education/graalvm-native-image/reachability-metadata.json) | manual Native Image build, опционален `native-lab` профил в [`pom.xml`](pom.xml) |

## Как работи регистрацията

Точно нашата регистрация е:

```json
{
  "reflection": [
    {
      "type": "bg.hristomanov.education.graalvm.ReflectionGreeting",
      "allPublicConstructors": true
    }
  ]
}
```

Файлът под `META-INF/native-image/<groupId>/<artifactId>` се включва при native build. Той **не създава нов клас на runtime** и **не е sandbox** за неконтролиран Java код. Затова [`GreetingFactory.java`](src/main/java/bg/hristomanov/education/graalvm/GreetingFactory.java) има allowlist (списък на разрешените реализациите) и type check.

**JVM проверка:**

```bash
mvn -pl java/jvm/graalvm-native-image -am test
cd java/jvm/graalvm-native-image
java -cp target/classes bg.hristomanov.education.graalvm.RuntimeGreetingApp \
  --name=Ada --iterations=3 --reflection
```

**Native проверка (само ръчно с GraalVM 25.x):**

```bash
mvn -Pnative-lab -DskipTests package
./target/runtime-greeting --name=Ada --iterations=3 --reflection
```

Очакван бизнес резултат: `checksum=33` и `mode=reflection`. Този native сценарий **не е доказан от стандартния Java 25 CI**, докато не бъдат приложени действителни native build/run logs.

### Negative experiment

В **отделно scratch копие**, не в `main`, временно премахни `reachability-metadata.json`, почисти `target`, компилирай native и опитай `--reflection`. В зависимост от GraalVM версията и достижимостта анализаторът **може** да отхвърли reflection/constructor, но failure не е универсално гарантиран: компилаторът може да открие някои константни patterns. Затова сравнявай реални error logs (`MissingReflectionRegistrationError` / `ClassNotFoundException`), а не обещавай конкретно exception поведение.

## Кога коя техника

| Техника | Проблем | Кога да / кога НЕ | Плюсове / компромиси | По-проста алтернатива / доказателство |
| --- | --- | --- | --- | --- |
| Ръчна reachability config | Малък, известен reflection path | Да: стабилен class contract; не: стотици dynamic plugins | Контролирани members; maintenance и риска от overregistration | Статично извикване; [JSON + JUnit](src/main/resources/META-INF/native-image/bg.hristomanov.education/graalvm-native-image/reachability-metadata.json) |
| Tracing Agent | Непълна информация за динамичните извиквания | Да: representative integration tests; не: един happy-path | Реални наблюдавани paths; coverage gaps | Native-ready library / static config; [официален tracing agent](https://www.graalvm.org/latest/reference-manual/native-image/Agent/) |
| Reachability Metadata Repository | Липсва third-party config | Да: има библиотека и версия; не: собствен непроверен plugin | Повторна употреба и екосистемни тестове; version drift | Избери native-ready dependency; [repo guide](https://www.graalvm.org/latest/reference-manual/native-image/guides/use-reachability-metadata-repository-maven/) |
| Crema runtime class loading | Нов, генериран **след build** bytecode | Да: R&D сценарий; не: недоказан production SLA | Dynamic loading; experimental cost | JVM + reflection; [Crema experiment](CREMA-EXPERIMENT.md) |

## AI-assisted конфигуриране

Лекторите описват AI-generated tests и конфигурации за много Maven библиотеки. Това **не** означава, че произволна библиотека има автоматично 100% native coverage. Запази следния review protocol:

1. Провери точните Maven coordinates, версии и официалните native release notes.
2. Генерирай положителни и отрицателни tests за dynamic access, resource files и proxy routes.
3. Провери **JVM и native** runtime, вкл. незасечените от agent paths; без native run не пишем „compatible“.
4. Прегледай metadata за ненужно разрешени reflective operations, initialization side effects и секрети по време на build.
5. Върни reusable configuration upstream само с възпроизводими native integration tests.

**Извод:** metadata е договор за достъпност в образа, не механизъм за кодова изолация или магическа съвместимост.

## Оригинални източници

[Alina Yurenko и Thomas Wuerthinger — GraalVM Next](https://www.youtube.com/watch?v=OLQ98Ko4p3Q), SRT 07–11 мин; [GraalVM Reachability Metadata](https://www.graalvm.org/latest/reference-manual/native-image/metadata/); [Tracing Agent](https://www.graalvm.org/latest/reference-manual/native-image/Agent/); [Native Build Tools Maven Plugin](https://graalvm.github.io/native-build-tools/latest/maven-plugin.html).
