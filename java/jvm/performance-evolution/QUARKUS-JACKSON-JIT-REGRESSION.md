# P1 — Quarkus/Jackson: когато генерираният код е по-бавен

> **Engineering Case Study + Java 25 JUnit/JMH lab** · Devoxx 2026, Mario Fusco, Francesco Nigro и Georgios Andrianakis.  
> **Източници:** [оригинално видео](https://www.youtube.com/watch?v=SgK0zMNayq0) (предоставен SRT приблизително до 02:28) и [слайдове 40–45, 75–102](https://speakerdeck.com/mariofusco/the-quarkus-advantage-turning-build-time-metaprogramming-into-runtime-performances).  
> **Важно:** учебният HandlerDispatchLab е наша адаптация на handler-dispatch идеята, **не** реплика на действителния Jackson 3 serializer или Quarkus benchmark. Няма публикувано от нас ново HTTP throughput измерване.

## 1. Реалният казус: „Премахнах reflection — защо производителността падна?“

Quarkus генерира bytecode, който директно сериализира POJO стойности чрез Jackson. Очакването е да избегне reflection и да стане по-бърз от стандартния serializer. Но първоначалните резултати на лекторите са **по-лоши** от стария вариант.

```text
                         Same JSON API / benchmark input
                                        |
                       +----------------+----------------+
                       v                                 v
               Jackson standard                Generated serializer
              reflection-based path           direct getters / calls
                       |                                 |
                   JIT shape A                       JIT shape B
                       |                                 |
                144 019 req/s                     140 073 req/s
```

**Защо е важен случаят:** на code review лесно би минало твърдението „декомпозиран, директен код с повече inlining е по-бърз“. Това е **хипотеза**, а не доказателство: изпълнимото поведение след C2 оптимизациите зависи от профила на call sites, инлайннати тела, регистровия бюджет и горещите цикли.

## 2. Истинските измервания от лекцията

Среда от слайд 78: **OpenJDK 25, Quarkus 4 main, Jackson 3, Hyperfoil 0.29.3**. Workload: REST endpoint връща списък с 20 предварително създадени `ExtendedPerson` обекта (вкл. `Address` и `Car`). Това е специализиран сериализационен benchmark, **не** общ Spring/Quarkus или DB workload.

| Serializer | Преди корекциите | След корекциите | Промяна в същия вариант |
| --- | ---: | ---: | ---: |
| Jackson reflection-based | 144 019 req/s | 147 115 req/s | +2,2% |
| Generated reflection-free | 140 073 req/s | 151 154 req/s | +7,9% |

**Изводът не е „Gizmo винаги печели“**. Първо generated вариантът изостава; след точно определени промени изпреварва новия Jackson baseline с около **2,7%**, при този конкретен тест. Публичните слайдове не дават независими confidence intervals за универсална гаранция; числата не са наша целева производителност.

### Какво намират при разследването — източник, не наше предположение

1. В стандартния Jackson път `_writeStringSegment` се инлайнва в сравнително малък caller. Генерираният сериализатор инлайнва копия на hot loop в **по-големи caller-и**, което увеличава живия регистров state.
2. Появява се **register spilling** (стойности се прехвърлят от CPU регистрите към други регистри/памет), който влошава изпълнението на char-by-char string loop.
3. В Jackson 3 има **type/profile pollution** за `String.charAt`, включително път за RFC1123 date formatting с non-ASCII `String`. В конкретната JIT компилация това ограничава оптимизацията на горещия loop; не означава, че Unicode винаги е бавен.
4. След корекция 1 компилаторът получава възможност за **2x loop unrolling** (повтаря повече операции в една итерация).
5. След намаляване на table/bounds-check complexity JIT достига **4x unrolling** на конкретния ASCII fast path, с по-малко повторения на spill.

Това е каузална хипотеза и техническа демонстрация от екипа, подкрепена с техните профили/assembly, но не означава, че можем да „пуснем един флаг“ и да го възпроизведем на произволен JDK.

**Не променяй Jackson или JIT flags в production само защото лекторите са го направили.** Проверявай версията на Jackson, размера/shape на payload, JDK, GC, UTF-8 output, CPU и семантиката на JSON.

## 3. Вторият казус: megamorphic handler dispatch

Преди Jackson частта лекторите показват Quarkus REST dispatch. Един handler interface има много различни concrete реализации на един call site. JIT може да изпадне в **megamorphic** случай (много receiver типове), при който aggressive inline не се случва.

**Предишна оптимизация:** голяма `instanceof` cascade при всяка HTTP заявка — повтаряща се работа, крехък ред на проверките и по-бавен fallback за third-party handler.

**Новото решение:** определяне на handler kind **веднъж** при build/deployment и `tableswitch` към конкретен handler тип при request. Непознатите типове остават с безопасен generic fallback.

```text
Build / deployment: Handler type → kind byte, stored once per handler chain

Per request:
  generic:       handler.handle(ctx)
  instanceof:    if A ... else if B ... else if C ... else ...
  preclassified: switch(kind[i]) → exact typed operation | default generic
```

**Важно:** броят типове и границата за отказ от inlining не са универсален JVM specification contract. Benchmark и профилите решават.

## 4. Нашата изпълнима Java25/JUnit лаборатория

Това е **обяснителна адаптация**, която демонстрира същия decision boundary. Не е копие на Quarkus REST server handler.

| Какво | Код | Проверка |
| --- | --- | --- |
| Three dispatch strategies | [`HandlerDispatchLab.java`](src/main/java/bg/hristomanov/education/performance/quarkus/HandlerDispatchLab.java) | [`HandlerDispatchLabTest.java`](src/test/java/bg/hristomanov/education/performance/quarkus/HandlerDispatchLabTest.java) |
| Realistic fallback for extension handler | [`Chain.viaPreclassified`](src/main/java/bg/hristomanov/education/performance/quarkus/HandlerDispatchLab.java) | Unknown handler + negative input tests |
| One-time preclassification | [`Chain`](src/main/java/bg/hristomanov/education/performance/quarkus/HandlerDispatchLab.java) | Source-list mutation does not change bound chain |
| JMH execution | [`HandlerDispatchBenchmark.java`](../performance-evolution-benchmarks/src/main/java/bg/hristomanov/education/performance/benchmarks/HandlerDispatchBenchmark.java) | JVM/JIT and GC tooling; **no built-in claim of speedup** |

Сценариите са: три известни handler типа (`Add`, `Multiply`, `Xor`), generic extension fallback, chain от 0 до N handlers и input с отрицателни числа. `List.copyOf` задържа константен bound chain; това позволява kind map да бъде предварително изчислена и да не изостава от runtime mutation.

**Good тестов договор:** трите пътя трябва да дават същия резултат за всички проверени inputs. Без тази parity не може да сравняваме throughput.

### Стартиране от root на Education

```bash
mvn -pl java/jvm/performance-evolution -am test
mvn -pl java/jvm/performance-evolution-benchmarks -am package
```

След това (JDK 25, отделен експеримент, не пускай бенчмарка при всеки CI):

```bash
java --add-modules jdk.incubator.vector \
  -jar java/jvm/performance-evolution-benchmarks/target/benchmarks.jar \
  HandlerDispatchBenchmark -f 2 -wi 3 -i 5 -r 1s -w 1s -prof gc
```

Този JMH benchmark използва `@Param({"1","3","9"})` за дължината на веригата. Измерва **локална dispatch логика**, не JSON serialization, REST, Quarkus/Jackson integration, CPU memory bandwidth или throughput на микросървис.

### JFR и JIT (ръчна лаборатория, без измислен perf proof)

При нужда включи JFR профил за JMH fork-овете чрез `-jvmArgs` с отделни output filenames; не използвай еднакъв output path за два forks:

```bash
java --add-modules jdk.incubator.vector \
  -jar java/jvm/performance-evolution-benchmarks/target/benchmarks.jar \
  HandlerDispatchBenchmark -f 1 -wi 3 -i 5 \
  -jvmArgs "-XX:StartFlightRecording=filename=handler-dispatch.jfr,settings=profile"
```

После разгледай `handler-dispatch.jfr` с JDK Mission Control. За inlining decisions използвай целеви JIT diagnostic flags/async-profiler/perfasm с подкрепата на конкретния JDK и profiler. **JFR сам по себе си не доказва регистрово разпределение или assembly-level spilling.** За такъв извод са необходими assembly/perf evidence и контролирано повторение.

## 5. Decision cards

| Подход | Проблем | Кога да | Кога НЕ | Печалба | Загуба / алтернатива | Доказателство |
| --- | --- | --- | --- | --- | --- | --- |
| **Normal interface dispatch** | Разширяеми handlers с различни типове | Няма измерен hot path | Неподвижен megamorphic call site доминира CPU | Прост design, extension-friendly | JIT може да не inline-не; [JUnit](src/test/java/bg/hristomanov/education/performance/quarkus/HandlerDispatchLabTest.java) |
| **Runtime instanceof chain** | Контролирани няколко типа, локално devirtualization | 2–3 стабилни случая, proven benefit | Десетки типове или плъгини | Няма build step | Повтарящи се type checks, fragile ordering; simpler: virtual call |
| **Preclassified switch** | Known types and fixed handler chain | Профил + bound handler order са стабилни | Динамично се сменят handlers, липсва profiler evidence | Класификация веднъж | Кеш consistency, кодова сложност; fallback interface call | [JUnit + JMH](../performance-evolution-benchmarks/src/main/java/bg/hristomanov/education/performance/benchmarks/HandlerDispatchBenchmark.java) |
| **Generated JSON serializers** | Reflection и runtime metadata в горещ path | Има доказан serialization bottleneck + coverage на JSON semantics | Unknown DTO shapes, Jackson custom modules, polymorphism и DB bottleneck | Потенциална по-малка runtime работа | Голям bytecode, JIT inlining/spilling, breakage на JSON contract; по-просто: стандартен Jackson | Оригиналният Quarkus тест, **не** нашата dispatch JMH лаборатория |

**Кога НЕ си струва:** ако Oracle/JDBC pool или remote IO определя p99, пренаписването на serializer може да няма измерим user-visible ефект. Ако JSON shape е малък и редкият reflection не доминира, complexity/maintenance cost надминава ползата.

## 6. Реален production investigation checklist

1. **Reproducibility:** фиксирай JDK, Quarkus/Spring, Jackson, CPU, payload, endpoint, профили, warmup, connection reuse и Hyperfoil/JMH version.
2. **Correctness:** byte-for-byte/semantic JSON parity; missing/null, escapes, polymorphism, custom serializers, numeric/locale formatting, security.
3. **Resource attribution:** CPU, JIT/GC, allocation, network/proxy, server saturation; не смесвай open-loop и closed-loop arrivals.
4. **JIT evidence:** различни inlining logs, generated assembly на hot loops, register pressure, профили на `String.charAt` и latency distributions.
5. **Benchmark contract:** сравни baseline и candidate при еднакво натоварване, повторени независими runs с confidence/variance. Не използвай HTTP req/s като доказателство за отделен Java method.
6. **Rollback:** как се изключва experimental serializer, кои clients се засягат, как наблюдаваме p95/p99 и error rate.

Свързани материали: [Benchmark Correctness](BENCHMARK-CORRECTNESS.md), [Big Runtimes](BIG-RUNTIMES-JIT-ABSTRACTIONS.md), [Method Handles](../method-handles/README.md), [Quarkus build-time course](../../../spring/framework-internals/quarkus-build-time/README.md), [GraalVM](../graalvm-native-image/README.md).

## 7. Завръщане към казуса

Започнахме с предположението „reflection-free код → автоматично по-бърз“. Истинският случай показва, че **JIT code shape** може да доминира резултата. Нашият малък Java/JMH lab учи на повторно използваем mental model за preclassification и безопасни fallback-и, а оригиналното Jackson разследване учи **как да търсим причината**, преди да правим оптимизация.

**Три правила:** ако няма profiler evidence → не оптимизирай по догадка; ако има семантичен риск → първо negative tests; ако има измерен bottleneck → сравни реалната production-relevant метрика, не само microbenchmark.

## Оригинални източници

- [Devoxx видео](https://www.youtube.com/watch?v=SgK0zMNayq0) и потребителски auto-generated SRT.
- [Официалните слайдове на Mario Fusco](https://speakerdeck.com/mariofusco/the-quarkus-advantage-turning-build-time-metaprogramming-into-runtime-performances), слайдове 40–45 и 75–102. Benchmark числа са от слайдове 78–101.
- [JEP 416 — Reimplement Core Reflection with Method Handles](https://openjdk.org/jeps/416), [JMH reference](https://github.com/openjdk/jmh), [JFR](https://docs.oracle.com/en/java/javase/25/docs/specs/man/jfr.html).
- [Quarkus extension guide](https://quarkus.io/version/3.40/guides/writing-extensions/) и [Benchmark Correctness](BENCHMARK-CORRECTNESS.md).

**Граница на извлеченото:** предоставеният SRT е непълен спрямо официалната дължина на видеото; допълнителната информация за слайдовете и версиите е от оригиналния публичен deck и документацията. Където примерът е наша адаптация, това е обозначено.
