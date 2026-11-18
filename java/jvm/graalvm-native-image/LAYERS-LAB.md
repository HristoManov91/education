# P2 — Native Image Layers: една обща база, две приложения

> **Ръчен native експеримент** с GraalVM 25.1+ и Native Build Tools 1.1.19. Трите реални Maven проекта са в [layers-lab](layers-lab/pom.xml), извън основния CI. Източник: Alina Yurenko и Thomas Wuerthinger, Devoxx 2026, SRT 12:00–19:50.

## 1. Реален казус: защо изобщо са нужни слоеве

Две Java приложения използват една и съща версия на JDK и голям набор стабилни библиотеки. При традиционна Native Image компилация всяко приложение компилира нужните си зависимости. Искаме общата база да може да се използва повторно.

```text
base-layer/pom.xml
      └─ native:layer-create → java-base.nil (build) + libbase.so (runtime)
                                  │                    │
                       english-service         spanish-service
                       english-native           spanish-native
                                  └── shared base ────┘
```

**Native Image Layers не са Docker Layers.** Файлът `.nil` е вход за следваща native компилация, докато споделената native библиотека трябва да бъде доставена при изпълнение. Docker layers оптимизират преноса/съхранението на файлове, но сами по себе си не правят компилирания код общ за няколко процеса.

**Bad подход:** споделяме Docker layer и очакваме автоматично по-малко RAM при 10 независими executables. **Good:** отделен producer за общия native код + реални consumers, работещ loader и измерен PSS.

## 2. README → код

| Роля | Конфигурация и код | Доказателство |
| --- | --- | --- |
| Самостоятелен Maven reactor | [layers-lab/pom.xml](layers-lab/pom.xml) | Създава три модула |
| Base producer | [base-layer/pom.xml](layers-lab/base-layer/pom.xml) | `layer-create` за `java.base` |
| Consumer A | [english-service/pom.xml](layers-lab/english-service/pom.xml), [EnglishService.java](layers-lab/english-service/src/main/java/bg/hristomanov/education/graalvm/layers/EnglishService.java) | `READY greeting=Hello!` |
| Consumer B | [spanish-service/pom.xml](layers-lab/spanish-service/pom.xml), [SpanishService.java](layers-lab/spanish-service/src/main/java/bg/hristomanov/education/graalvm/layers/SpanishService.java) | `READY greeting=Hola!` |
| Измерване | [linux_memory.py](scripts/linux_memory.py) | [test_tools.py](scripts/test_tools.py) валидира parser |

## 3. Стъпка по стъпка

### Първо: JVM функционална проверка

От корена на Education:

```bash
cd java/jvm/graalvm-native-image/layers-lab
mvn clean package
java -cp english-service/target/classes bg.hristomanov.education.graalvm.layers.EnglishService
java -cp spanish-service/target/classes bg.hristomanov.education.graalvm.layers.SpanishService
```

Очакваме `READY greeting=Hello!` и `READY greeting=Hola!`. Това проверява Java кода, **не native layer compilation**.

### Второ: Native build (изрично ръчен)

Изисква Java 25 GraalVM 25.1+, native-image toolchain и съвместима ОС/архитектура:

```bash
native-image --version
mvn -Pnative -DskipTests clean package
```

Producer прикачва `.nil` и платформено зависим runtime ZIP. Consumer модулите декларират `<type>nil</type>` и `<useLayers>`, за да използват общата база. Native plugin е с `<extensions>true</extensions>` и в aggregator, за да се регистрира типът `nil` навреме. Това следва [официалния Maven Layered Native Images guide](https://graalvm.github.io/native-build-tools/latest/layered-images.html).

**Граница:** конфигурацията е предоставена за изпълнение, но не е native-verified в основния Java 25 CI. Без действителен build log не твърдим, че GraalVM е компилирал успешно примерите. При експеримента записваме версия, ОС, архитектура, build време и output.

### Трето: RSS, PSS и Private на Linux

В лекцията (15–19 мин) са показани приблизително 200 MB Micronaut base, 2 MB app слой и по-малък PSS на приложение при 10 процеса. **Това са сценични измервания, а не резултати от нашата Java-only лаборатория.**

- **RSS**: всички resident memory pages на процес, включително споделените.
- **PSS**: собствените страници + пропорционален дял от shared страниците. **PSS не е само private памет.**
- **Private**: Private_Clean + Private_Dirty. Сумираният RSS може да брои shared pages повече от веднъж.

От `java/jvm/graalvm-native-image` на Linux:

```bash
python3 scripts/linux_memory.py --hold-ms 10000 \
  --layer-root layers-lab/base-layer/target/native/layers/java-base \
  ./layers-lab/english-service/target/english-native

python3 scripts/linux_memory.py --hold-ms 10000 \
  --layer-root layers-lab/base-layer/target/native/layers/java-base \
  ./layers-lab/english-service/target/english-native \
  ./layers-lab/spanish-service/target/spanish-native
```

[Скриптът](scripts/linux_memory.py) подава `LD_LIBRARY_PATH`, чете `/proc/<pid>/smaps_rollup` и изчислява сумарни RSS, PSS и Private. Сравнявай sum PSS при едно и две приложения с идентичен idle/load режим. За macOS/Windows са необходими други loader настройки/инструменти; този скрипт е **само за Linux**.

**Negative packaging test:** в изолирана среда премахни runtime shared library и стартирай потребителя; очаквай OS loader failure, не Java exception. След това върни библиотеката и провери изхода. При production delivery публикувай build artifact и runtime ZIP отделно, а не разчитай на локалния Maven target.

## 4. Decision contract

| Въпрос | Решение |
| --- | --- |
| Какъв проблем решава? | Повторно компилиране и дублирани native кодови страници при стабилни общи зависимости |
| Кога? | Много services, едни и същи JDK/dependency версии, управляван base, измерен memory/build bottleneck |
| Кога НЕ? | Едно приложение, чести промени в base, различни ОС/CPU/libc, сложен неконтролируем deployment |
| Печалба | Потенциално по-нисък сумарен PSS и reusable build artifact |
| Компромиси | Допълнителен build/storage cost, версия/ABI, shared libraries, rollback |
| По-просто | Един standalone Native Image; JVM + Leyden AOT cache; Docker caching |
| Как доказваме? | Реален native build; два коректни READY outputs; повторени PSS/RSS сравнения и loader negative test |

**Checklist:** еднакви OS/CPU/ABI, версия на GraalVM, нужните JDK модули, .nil/ZIP retention, checksums, loader path, canary и rollback.

## Оригинални източници

[Devoxx GraalVM Next / SRT](https://www.youtube.com/watch?v=OLQ98Ko4p3Q) 12–19 мин, [Native Build Tools Layer Guide](https://graalvm.github.io/native-build-tools/latest/layered-images.html), [JVM in Containers](../performance-evolution/JVM-IN-CONTAINERS.md).
