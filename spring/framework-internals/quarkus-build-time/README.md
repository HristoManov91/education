# P2 — Quarkus Build-time Metaprogramming: Jandex → BuildItem → Gizmo

> **Практически Quarkus 3.40 LTS extension + JUnit + HTTP тест** · Java 25 · отделен Maven reactor  
> **Оригинал:** [Mario Fusco, Francesco Nigro и Georgios Andrianakis — The Quarkus Advantage, Devoxx 2026](https://www.youtube.com/watch?v=SgK0zMNayq0), предоставен автоматичен SRT (до приблизително 02:28) и [оригиналните 102 слайда](https://speakerdeck.com/mariofusco/the-quarkus-advantage-turning-build-time-metaprogramming-into-runtime-performances).  
> **Наша адаптация:** примерът с `@GreetingDefinition` е собствена малка лаборатория. Лекторите демонстрират REST dispatchers, Netty и JSON serializers, а не този greeting extension.

**Цел:** не изучаваме Quarkus като заместител на Spring Security/JPA. Изучаваме как framework-ът открива metadata при **augmentation** (подготовката при build), генерира изпълним код и оставя на runtime само необходимото.

## 1. Реален казус — динамична операция при всяко стартиране или заявка

Имаме REST услуга, която открива анотирани класове, строи собствен модел, намира implementation чрез reflection и после обслужва заявки. Ако сканирането се повтори при всяко стартиране или в горещия request path (честия път при обработката), хабим CPU, памет и startup време.

```text
Наивен подход:
package application → startup: scan + inspect annotations + reflect → handle request

Quarkus extension:
package application → augmentation: index → validate → generate bytecode
                                    ↓
                              app runtime JAR
                                    ↓
                         HTTP → CDI-generated bean → direct interface call
```

**Bad, но правдоподобно:** „Reflection е бавен, значи ще го заменим навсякъде с ръчно генериран код.“ Това не е достатъчна аргументация: Java 18+ реализира core reflection върху MethodHandles, JIT резултатът зависи от call-site профилите, а code generation увеличава сложността на build, debugging и съвместимостта.

**Good:** откриваме точно каква информация е известна при build, отделяме deployment и runtime ownership (кой носи отговорността за всеки етап), проверяваме генерирания код с integration test и мерим performance само при реален bottleneck.

### Какво ще умеем

1. Да разграничаваме Quarkus `augmentation` от JVM startup/runtime и GraalVM Native Image компилация.
2. Да намираме конкретна анотация с **Jandex** без runtime class loading.
3. Да разбираме DAG между `@BuildStep` и `SimpleBuildItem`: процесорът не разчита на реда на методите.
4. Да създадем истински **CDI bean с Gizmo**, открит от Quarkus по време на build.
5. Да докажем, че Quarkus приложението инжектира и изпълнява генерирания bean с HTTP тест.
6. Да свържем идеята с megamorphic dispatch, JIT profiling и Jackson regression — без да твърдим, че нашият greeting пример възпроизвежда benchmark числата на лекторите.

## 2. Архитектура: три Maven модула

```text
quarkus-build-time/
  runtime/            → API @GreetingDefinition + GreetingMessage; visible to app
  deployment/         → BuildStep + Jandex + BuildItem + Gizmo; NOT on runtime classpath
  integration-tests/  → annotated model + REST resource + real @QuarkusTest
```

Quarkus extension descriptor указва на framework-а коя deployment dependency съответства на runtime extension. Приложението добавя **само runtime artifact**; Quarkus зарежда deployment artifact по време на augmentation.

**Критична граница:** това е **истински Quarkus extension** с отделен parent [`pom.xml`](pom.xml), но **не** е добавен към root `education/pom.xml`. Така не пренасяме Quarkus BOM, augmentation и съответните Maven зависимости към всеки Spring Boot PR. Специалният CI workflow се задейства само за Quarkus модула.

### README → код и тест

| Концепция | Изпълнимо | Доказателство |
| --- | --- | --- |
| Runtime API | [`GreetingDefinition.java`](runtime/src/main/java/bg/hristomanov/education/quarkus/runtime/GreetingDefinition.java) / [`GreetingMessage.java`](runtime/src/main/java/bg/hristomanov/education/quarkus/runtime/GreetingMessage.java) | REST тестът проверява, че няма ръчно имплементиран bean |
| Extension registration | [runtime `pom.xml`](runtime/pom.xml), [`quarkus-extension.yaml`](runtime/src/main/resources/META-INF/quarkus-extension.yaml) | Maven `extension-descriptor` при build |
| Jandex, BuildSteps, Gizmo | [`GreetingBuildProcessor.java`](deployment/src/main/java/bg/hristomanov/education/quarkus/deployment/GreetingBuildProcessor.java) | Реална augmentation при Quarkus integration test |
| BuildItem между стъпките | [`GreetingModelBuildItem.java`](deployment/src/main/java/bg/hristomanov/education/quarkus/deployment/GreetingModelBuildItem.java) | Producer → consumer като build graph dependency |
| Fail-fast validation | [`GreetingRules.java`](deployment/src/main/java/bg/hristomanov/education/quarkus/deployment/GreetingRules.java) | [`GreetingRulesTest.java`](deployment/src/test/java/bg/hristomanov/education/quarkus/deployment/GreetingRulesTest.java) |
| Анотиран application model | [`GreetingModel.java`](integration-tests/src/main/java/bg/hristomanov/education/quarkus/app/GreetingModel.java) | Реален build-time Jandex lookup |
| Generated CDI bean → REST | [`GreetingResource.java`](integration-tests/src/main/java/bg/hristomanov/education/quarkus/app/GreetingResource.java) | [`GreetingResourceTest.java`](integration-tests/src/test/java/bg/hristomanov/education/quarkus/app/GreetingResourceTest.java) с `@QuarkusTest` |
| IntelliJ request | [`generated-greeting.http`](integration-tests/http/generated-greeting.http) | Ръчно стартирано приложение |
| JVM/JIT throughput scenario | [P1 инженерният казус](../../../java/jvm/performance-evolution/QUARKUS-JACKSON-JIT-REGRESSION.md) | [dispatch JUnit](../../../java/jvm/performance-evolution/src/test/java/bg/hristomanov/education/performance/quarkus/HandlerDispatchLabTest.java), [JMH](../../../java/jvm/performance-evolution-benchmarks/src/main/java/bg/hristomanov/education/performance/benchmarks/HandlerDispatchBenchmark.java) |

## 3. Как работи BuildStep графът

**Step A — Jandex анализ:** `GreetingBuildProcessor.indexGreeting(CombinedIndexBuildItem)` използва `IndexView` и `DotName` за `@GreetingDefinition`. Извлича точно един `String`, без да създава потребителския class с `Class.forName`. Липсващ, повторен или празен greeting → изрична build-time грешка, а не късен HTTP 500.

**Step B — BuildItem:** резултатът е `GreetingModelBuildItem extends SimpleBuildItem`. Това е **immutable state** (неизменяемо състояние) за следващите Quarkus стъпки. Quarkus подрежда build steps по produced/consumed items. Не трябва да споделяме mutable field между стъпките.

**Step C — Gizmo:** `generate(GreetingModelBuildItem, BuildProducer<GeneratedBeanBuildItem>)` създава клас, имплементиращ `GreetingMessage`, със `@Singleton` и метод `message()`, който връща предварително валидираната константа. `GeneratedBeanGizmoAdaptor` регистрира bytecode с ArC (Quarkus CDI).

**Step D — Runtime:** `GreetingResource` инжектира `GreetingMessage` и извиква `generated.message()`. Няма per-request Jandex анализ, `Class.forName` или `Method.invoke`. CDI може да създаде bean-а при runtime, но неговият **клас и метод** са генерирани при build.

**Обхват:** това е демонстрация на **compile-time constant data**, не генериран high-performance Jackson serializer. Няма proof за намален RSS/GC/latency само от този пример.

## 4. Реално стартиране и тестове

Нужни са JDK 25, Maven 3.9+ и достъп до Maven Central. За стабилна база е избран **Quarkus 3.40.1.2 LTS** (достъпен към 18.11.2026), вместо Quarkus 4 prerelease от лекцията.

От корена на Education:

```bash
mvn -B --no-transfer-progress \
    -f spring/framework-internals/quarkus-build-time/pom.xml verify
```

Това изпълнява:
- `GreetingRulesTest` — single/zero/two/blank/oversized metadata проверки.
- `GreetingResourceTest` — Quarkus augmentation, генериран CDI bean, injection и реален `GET /generated-greeting`.

Ако Quarkus Maven runtime отчете промяна в 3.40.x ABI или plugin contract, фиксираме **конкретния stack trace и версия**, а не го заглушаваме.

За ръчна HTTP заявка, след веднъж изпълнено `mvn install` от изолирания parent:

```bash
cd spring/framework-internals/quarkus-build-time/integration-tests
mvn quarkus:dev
curl -i http://localhost:8080/generated-greeting
```

Или използвай [IntelliJ HTTP Client файла](integration-tests/http/generated-greeting.http).

<details>
<summary>Примерен резултат</summary>

```text
HTTP/1.1 200 OK
Content-Type: text/plain;charset=UTF-8

Hello from build-time augmentation
```

Конкретни динамични HTTP headers може да се различават.
</details>

### Отрицателен build-time experiment

В scratch branch прибави втори клас с `@GreetingDefinition` в integration-tests или смени стойността с празен текст. Очаквай Quarkus augmentation failure, а не успешно стартирано приложение. **Това е ръчна проверка**, не отделен `@QuarkusTest` отрицателен integration тест; unit тестовете проверяват точно този validation contract.

## 5. Jandex, BuildItem, Gizmo — значими варианти

| Техника | Проблем | Кога да | Кога НЕ | Печалба | Trade-off / по-проста алтернатива | Test |
| --- | --- | --- | --- | --- | --- | --- |
| **Jandex** | Извличане на annotation/type metadata | Потребителските класове са налични при augmentation | Ако приложението трябва да чете произволен нов class/JAR без rebuild | Без runtime scanning, предвидим модел | Не всички външни dependencies са индексирани по подразбиране; използвай `IndexDependencyBuildItem` или обикновен reflection за малък runtime lookup | Quarkus HTTP test |
| **SimpleBuildItem** | Свързване на един producer с consumers | Има точно един модел/резултат | Ако има N независими producers → `MultiBuildItem` | Автоматичен DAG и type-safe handoff | Build-stage coupling; по-просто — един build step | [Validation test](deployment/src/test/java/bg/hristomanov/education/quarkus/deployment/GreetingRulesTest.java) |
| **Gizmo GeneratedBean** | Предварително подготвена CDI реализация | Горещ runtime path и известна при build структура | Ако прост написан на ръка bean е достатъчен | Премахва runtime model-building, може да помогне на JIT | Bytecode debug, class generation, upgrade compatibility; алтернатива — normal `@Singleton` | Quarkus REST integration |
| **Recorder** | Startup state, зависещ от вече изчислен build модел | Имаме lifecycle-sensitive startup операция | Тривиален constant bean без setup | Записва изпълнение за static/runtime init | Init timing/serialization constraints; не ни е нужен в този пример | [Quarkus guide](https://quarkus.io/guides/writing-extensions/) |
| **Spring AOT** | Предварително изчислена Spring конфигурация | Имаме Spring Boot, искаме generated bean registration/runtime hints | Няма причина за смяна на framework | Запазваме Spring стек | Различни extension points/граници; [Spring AOT docs](https://docs.spring.io/spring-framework/reference/core/aot.html) | Отделен Spring integration lab, не този |

**Нюанс:** не обещаваме, че „генерираният код винаги е по-бърз“. За HotSpot JIT call-site history, code size, inlining, register spilling и loop unrolling могат да обърнат резултата. Точно това доказва [Jackson/JIT анализът](../../../java/jvm/performance-evolution/QUARKUS-JACKSON-JIT-REGRESSION.md).

## 6. Quarkus и Spring Boot: точните граници

Spring Boot 4/Spring Framework също имат AOT mechanisms за context и configuration. Нашето сравнение не е „Spring runtime, Quarkus compile-time“. Разликите са в **структурата и покритието на build-time augmentation**, CDI/ArC срещу Spring DI, bytecode/code generation extension APIs и cost на миграция.

Не бихме мигрирали Java 21/Spring Boot/Oracle/QueryDSL приложение към Quarkus само заради този урок. Първо измерваме startup, RSS, p99 и DB wait; оценяваме Testcontainers, security, gateway, persistence и операционна сложност. Стандартният Java25/Spring CI **не** компилира това Quarkus приложение, освен при промени в този модул.

**Малки мисловни правила:**
1. Ако поведението е известно при build и се ползва често, проучи build-time модел; иначе предпочитай прост runtime код.
2. Ако няма възпроизводимо performance доказателство, не заменяй reflection/DI с code generation.
3. Ако приложението вече е Spring Boot, сравни Spring AOT и Quarkus extension API **без** да започваш миграция от модата.

## 7. Практически изход и review checklist

В началото имахме runtime scanner на анотации. Сега го замества Jandex анализ при build и генериран bean с директен метод. **Доказателството е Quarkus HTTP тестът**, не само компилацията на Java class.

Прегледай: разделен ли е deployment от runtime classpath; правилен ли е Jandex index; има ли точно един producer; имаме ли clean failure при duplicate marker; излъчена ли е реална CDI реализация; минава ли HTTP тестът; има ли version pin; какво става при rebuild/config change; нужна ли е усложнената extension архитектура?

За самопроверка: [EXERCISES](EXERCISES.md) · [NotebookLM audio prompt](NOTEBOOKLM-PROMPT.md).

## Оригинални източници

- [Devoxx 2026 видео](https://www.youtube.com/watch?v=SgK0zMNayq0) + предоставен SRT (само приблизително до 02:28; видеото е по-дълго).
- [Mario Fusco — оригинални слайдове](https://speakerdeck.com/mariofusco/the-quarkus-advantage-turning-build-time-metaprogramming-into-runtime-performances), особено 17–46, 67–102. Слайдовете и transcript описват **Quarkus 4 main/Jackson 3**, а нашият lab е **Quarkus 3.40 LTS**, така че API/Gizmo syntax е version-specific.
- [Quarkus 3.40 — Writing Your Own Extension](https://quarkus.io/version/3.40/guides/writing-extensions/), [CDI integration — generated bean](https://quarkus.io/version/3.40/guides/cdi-integration/), [Quarkus Releases](https://quarkus.io/releases/).
- [Spring AOT](https://docs.spring.io/spring-framework/reference/core/aot.html) · [JEP 416 Reflection](https://openjdk.org/jeps/416) · [JVM/JIT казус](../../../java/jvm/performance-evolution/QUARKUS-JACKSON-JIT-REGRESSION.md) · [нашия GraalVM Lab](../../../java/jvm/graalvm-native-image/README.md).

**Граница на твърденията:** тази лаборатория демонстрира механизма, но не замества референтните Quarkus REST/JSON source files и не е реплика на конференционния benchmark.
