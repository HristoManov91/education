# P1 · Big Runtimes Make Efficient Programs — JVM, JIT и abstraction cost

> Deep technical lesson (Mid+ → Early Senior) · Java 25, JUnit 5, JMH и Spring Boot/JFR lab.  
> **Източник:** [Ron Pressler — Devoxx 2026](https://www.youtube.com/watch?v=vhDwy1eCXbs), анализ на предоставен Gemini-generated SRT (00:00–49:57; автоматичният текст може да съдържа неточности).  
> **Място:** разширение на [Modern JVM Performance / Project Leyden](README.md), [Benchmark Correctness](BENCHMARK-CORRECTNESS.md) и [JFR Troubleshooting](../jfr-troubleshooting/README.md); **не** нов notebook.

## Реален казус

В голям Spring Boot backend типове протоколи/операции се увеличават от 1 до 10. При code review някой предлага да заменим интерфейсите с проверки по concrete type, понеже „virtual calls са бавни“, а всички краткоживеещи DTO да се повторно използват, „за да няма GC“.

Интуитивно тези предложения звучат разумно. Но с тях:
- всяко ново бизнес поведение може да изисква редакция на централна специализирана функция;
- споделян mutable object може да промени данните на вече изпратен/запазен response;
- производителността може да остане същата или да се влоши, защото HotSpot има собствени оптимизации;
- микробенчмаркът не показва реалния HTTP p99.

**Какво искаме:** коректен, разширяем код, който JVM може да оптимизира, и доказателства кога локалната ръчна оптимизация си заслужава.

## README → код

| Концепция | Изпълним код | Тест / измерване |
| --- | --- | --- |
| Shape abstraction, virtual call | [ShapeDispatch.java](src/main/java/bg/hristomanov/education/performance/runtime/ShapeDispatch.java) | [ShapeDispatchTest.java](src/test/java/bg/hristomanov/education/performance/runtime/ShapeDispatchTest.java) |
| BAD: manual specialization | [ManualShapeDispatch.java](src/main/java/bg/hristomanov/education/performance/runtime/ManualShapeDispatch.java) | Нов subtype в [ShapeDispatchTest.java](src/test/java/bg/hristomanov/education/performance/runtime/ShapeDispatchTest.java) |
| Warm-up и смяна на типовете | [ShapeDispatchScenario.java](src/main/java/bg/hristomanov/education/performance/runtime/ShapeDispatchScenario.java) | [JMH ShapeDispatchBenchmark](../performance-evolution-benchmarks/src/main/java/bg/hristomanov/education/performance/benchmarks/ShapeDispatchBenchmark.java) |
| Fresh vs reused object | [MutableEvent.java](src/main/java/bg/hristomanov/education/performance/runtime/MutableEvent.java) | [MutableEventTest.java](src/test/java/bg/hristomanov/education/performance/runtime/MutableEventTest.java), [JMH AllocationReuseBenchmark](../performance-evolution-benchmarks/src/main/java/bg/hristomanov/education/performance/benchmarks/AllocationReuseBenchmark.java) |
| Същият core алгоритъм в HTTP/JFR | [RuntimeAreaService.java](../jfr-troubleshooting/src/main/java/bg/hristomanov/education/jfr/service/RuntimeAreaService.java) и [controller](../jfr-troubleshooting/src/main/java/bg/hristomanov/education/jfr/controller/RuntimeAreaController.java) | [service test](../jfr-troubleshooting/src/test/java/bg/hristomanov/education/jfr/service/RuntimeAreaServiceTest.java), [IntelliJ .http](../jfr-troubleshooting/http/jfr-lab.http) |

## 1. Какво всъщност означава „по-бърз програмен език“?

[06:22–08:57](https://youtu.be/vhDwy1eCXbs?t=382). Pressler прави важна разлика: „теоретично съществува бърза програма на даден език“ не означава, че е реалистично да я напишем и да я поддържаме с наличните хора, бюджет и време. По-високите абстракции намаляват coupling (обвързаността между компонентите), но оптимизацията често се нуждае от конкретна имплементация.

**Runtime** е код и механизми, които се изпълняват покрай приложението: class loading, interpreter/JIT, memory management, telemetry. Голям runtime не означава автоматично бавен runtime.

### Термини

| Термин | Практическо значение |
| --- | --- |
| Monomorphic call site | Конкретно място на извикване вижда предимно един receiver тип |
| Polymorphic call site | На същото място се наблюдават няколко receiver типа |
| Type profiling | JVM събира информация кои класове и branches се изпълняват |
| Speculative/guarded inlining | Вгражда извикване въз основа на предположение и проверка |
| Deoptimization | JVM изоставя специализиран изпълним код, когато допускането стане невалидно |
| Escape analysis | Анализ дали обект може да се наблюдава извън локалния scope; може да позволи allocation elimination |
| TLAB | Thread-local allocation buffer — евтин път за allocation на много малки обекти |
| Write barrier | Поддръжка на вътрешни GC структури при определени промени на референции |

## 2. Абстракция срещу специализация — кога HotSpot помага

[08:57–13:00](https://youtu.be/vhDwy1eCXbs?t=537) и [20:00–26:45](https://youtu.be/vhDwy1eCXbs?t=1200).

**BAD (но на пръв поглед логичен):** в [ManualShapeDispatch](src/main/java/bg/hristomanov/education/performance/runtime/ManualShapeDispatch.java) изреждаме Circle, Rectangle и Triangle с instanceof и изчисляваме area според конкретния клас. В някои натоварвания това *може* да е по-бързо, но изисква промяна на общата функция при всяка нова Shape имплементация. Regression тестът добавя нов Shape и доказва, че ръчната функция отказва, докато абстрактната работи.

**GOOD (първи избор):** [ShapeDispatch.doubleArea(Shape)](src/main/java/bg/hristomanov/education/performance/runtime/ShapeDispatch.java) използва интерфейс и оставя HotSpot да наблюдава типовете. На горещ call site HotSpot може да inline-не конкретна impl, с проверка на runtime предположението. Когато профилът се промени, JVM може да deoptimize-не, да събере нов профил и да компилира отново.

~~~text
Bytecode → interpretation / profiling → JIT specialization
                                  ├─ допускане валидно → optimized machine code
                                  └─ допускане грешно → deoptimization / recompilation
~~~

Това е **възможен** runtime сценарий, не гаранция за всяка версия, метод или стартиране. В реални програми има missed inlining, profile pollution и разходи за warm-up/JIT.

### Lab A: наблюдение на inlining

От repository root (JDK 25):

~~~bash
mvn -pl java/jvm/performance-evolution -am test

java -XX:+UnlockDiagnosticVMOptions \
  -XX:+PrintCompilation -XX:+PrintInlining \
  -cp java/jvm/performance-evolution/target/classes \
  bg.hristomanov.education.performance.runtime.ShapeDispatchScenario 100000
~~~

Търси ShapeDispatch, doubleArea и конкретните Shape.area реализации в логовете. **PrintInlining не доказва сам по себе си, че е имало конкретна deoptimization.** Сценарият е за наблюдение, не е benchmark.

## 3. JIT срещу AOT — различни оптимизационни бюджети

Динамичният JIT може да види реалния workload, но плаща warm-up, compilation CPU/RAM и понякога deoptimization. Предварителната компилация (AOT) може да помогне със startup или с по-предвидим deployment, но често знае по-малко за текущия execution profile.

| Подход | Кога | Компромис |
| --- | --- | --- |
| HotSpot JIT | Дългоживеещи backend процеси | Warm-up/compilation ресурс; предположенията може да се променят |
| JVM с Leyden AOT cache | Startup и class-loading bottleneck | Training, съвместимост на cache с build/JDK и обновяване |
| Native Image | Short-lived workloads, startup/footprint constraints | Build/compatibility сложност; различен steady-state throughput |

Изпълнимото обучение за AOT cache вече е в [Leyden](README.md). Не го дублираме.

## 4. Moving GC, allocation и защо „reuse е винаги по-бързо“ е мит

[27:07–38:45](https://youtu.be/vhDwy1eCXbs?t=1627): generational moving GC (поколенчески колектор, който мести обекти) може да събира мъртви обекти на групи, да подобри locality и да направи young-generation allocation много евтина. Повече heap headroom може да намали CPU за управление на паметта, но да увеличи RSS и разходите. GC не е безплатен, а различните GC имат различни pause/throughput trade-offs.

[45:00–49:57](https://youtu.be/vhDwy1eCXbs?t=2700): в Q&A лекторът подчертава, че повторното изменение на дълго живеещи, промотирани обекти може да струва write barriers и GC bookkeeping. **Твърдението му „винаги е по-бързо да създаваш нов обект“ не е универсален benchmark резултат.**

[MutableEvent](src/main/java/bg/hristomanov/education/performance/runtime/MutableEvent.java) показва fresh object и thread-confined reuse. [MutableEventTest](src/test/java/bg/hristomanov/education/performance/runtime/MutableEventTest.java) доказва correctness риска: стар alias сочи към същия променен обект. Ако е нужен нов DTO, просто създай нов; ако е скъп външен ресурс (например JDBC connection), pooling може да има смисъл.

### Lab B: JMH + allocation profiling

~~~bash
mvn -pl java/jvm/performance-evolution-benchmarks -am -DskipTests package

java -jar java/jvm/performance-evolution-benchmarks/target/benchmarks.jar \
  ShapeDispatchBenchmark -wi 3 -i 5 -f 2 -prof gc

java -jar java/jvm/performance-evolution-benchmarks/target/benchmarks.jar \
  AllocationReuseBenchmark -wi 3 -i 5 -f 2 -prof gc
~~~

Тук проверяваме **ns/op + bytes/op**, а не предварително зададена победа. Условията трябва да са еднакви (JDK build, GC, heap, CPU, JMH forks). Blackhole прави резултата наблюдаем, но пак провери дали JIT не елиминира алокациите. Reuse обектът не е гарантирано в old generation — провери с JFR/GC данни. Двата варианта не са семантично взаимозаменяеми, когато client кодът държи references.

Пълни команди и ограничения: [JMH runbook](../performance-evolution-benchmarks/README.md).

## 5. Lab C: същият ShapeDispatch зад Spring Boot HTTP и JFR

JMH мери изолиран метод, не response latency. В [JFR Spring Boot lab](../jfr-troubleshooting/README.md) добавяме endpoint, който извиква **същия** ShapeDispatch.sumDoubleAreas като JMH, но през Spring MVC:

~~~bash
mvn -pl java/jvm/jfr-troubleshooting -am -DskipTests install
mvn -pl java/jvm/jfr-troubleshooting spring-boot:run
~~~

В отделен терминал:

~~~bash
curl "http://localhost:8080/api/jfr/runtime/areas?shapeTypes=1&rounds=5000"
curl "http://localhost:8080/api/jfr/runtime/areas?shapeTypes=3&rounds=5000"
~~~

<details><summary>Примерен резултат за shapeTypes=1 и rounds=5 (не за 5000)</summary>

~~~json
{"shapeTypes":1,"rounds":5,"evaluatedShapes":5,"doubledAreaSum":125.66370614359172}
~~~

</details>

За възпроизводими HTTP заявки използвай [jfr-lab.http](../jfr-troubleshooting/http/jfr-lab.http). За анализ на JVM поведението:

~~~bash
jcmd -l
jcmd <PID> JFR.start name=runtime settings=profile
# Генерирай достатъчно последователни HTTP requests.
jcmd <PID> JFR.dump name=runtime filename=runtime-dispatch.jfr
jcmd <PID> JFR.stop name=runtime
jfr summary runtime-dispatch.jfr
jfr view hot-methods runtime-dispatch.jfr
~~~

**Не сравнявай директно JMH ns/op с HTTP p99.** За реален p95/p99, success rate, throughput и таймаути е необходим отделен load generator и ясно дефиниран request arrival model. Виж [Benchmark Correctness](BENCHMARK-CORRECTNESS.md).

## 6. Decision contract — кога, кога НЕ, какво печелим и губим

| Техника | Решава | Кога използваме | Кога НЕ | Trade-off |
| --- | --- | --- | --- | --- |
| Интерфейс + JIT | Модулен business contract | Повечето развиващи се backend domains | Профилиран критичен hotspot, който JIT не може да оптимизира | Локалност на промени ↔ warm-up/deopt |
| Ръчни type checks | Специален hotspot | Само при измерена печалба и bounded type hierarchy | Extensible plugins / много видове | Евентуално ускорение ↔ coupling, пропуснат subtype |
| Fresh short-lived DTO | Независима стойност за request | Normal Java model | Скъпи buffers и външни ресурси | Чист ownership ↔ алокации и GC |
| Reuse / object pool | Амортизира скъпа инициализация | DB connections, някои buffers | Обикновени споделяни DTO-та | По-малко creation ↔ mutation/retention/contended state |
| JMH | Локална performance хипотеза | Isolated method benchmark | Доказателство за HTTP SLA | Контрол ↔ нереалистичен контекст |
| JFR | CPU/allocations/GC/compilation evidence | Dev и production diagnostics | Самостоятелен заместител на metrics/load test | Прозрачност ↔ profiling overhead |

**По-простата алтернатива:** започни с обичаен Java код, функционални тестове и профилиране. Не въвеждай специализация или pool без доказан bottleneck.

## 7. Какво е ново и кое не е готово за Java 25

- **Java 25 (baseline):** JIT, GC, JFR и Leyden AOT cache; Compact Object Headers opt-in. Използвай [съществуващото обучение](README.md).
- **JDK 27:** промени в object headers и JFR, описани в [Java Evolution Guide](../../JAVA-EVOLUTION-GUIDE.md).
- **JEP 401 / Value Objects:** preview за JDK 28, **не** Java 25 production API; не обещава всеобщо flattening.
- **JEP 544 / AOT Code Compilation** и **JEP 546 / ZGC adaptive heap:** version-sensitive roadmap, проследяваме точния JEP статус, не включваме непотвърдени flags в runnable lab.

## 8. Задачи за самостоятелна проверка

1. Добави четвърта Shape имплементация. Защо единият вариант работи без промени, а другият не?
2. Сравни JMH при 1, 2 и 3 типа. Какво наистина означава резултатът?
3. При Fresh и Reuse виж gc.alloc.rate.norm; как доказваш или опровергаваш old-generation хипотезата?
4. Профилирай HTTP endpoint с JFR. Коя част от request-а JMH изобщо не измерва?
5. В code review как би обосновал специализацията, нейните алтернативи и rollback?

**Изходен mental model:** големият runtime може да намалява цената на high-level abstractions и memory management в реални еволюиращи програми. Не гарантира най-бързия резултат навсякъде. **Профилирай преди и след всяка оптимизация.**

### Production/code-review checklist

- [ ] Ясен симптом и метрика: latency / throughput / CPU / RSS / allocations?
- [ ] Представителен workload и baseline recording?
- [ ] Unit tests доказват коректността и extensibility?
- [ ] Отделно JMH измерване и HTTP/JFR проверка, когато са релевантни?
- [ ] Известни JDK/GC/heap/OS/CPU условия?
- [ ] Назовани trade-offs, ограничения и rollback?

## Оригинални източници

- [Devoxx 2026 — Big Runtimes Make Efficient Programs, Ron Pressler](https://www.youtube.com/watch?v=vhDwy1eCXbs).
- [OpenJDK JMH](https://github.com/openjdk/jmh) — официален benchmark harness.
- [JDK 25 GC Tuning Guide](https://docs.oracle.com/en/java/javase/25/gctuning/).
- [JDK Flight Recorder](https://docs.oracle.com/en/java/javase/25/jfapi/).
- [JEP 514](https://openjdk.org/jeps/514), [JEP 515](https://openjdk.org/jeps/515), [JEP 401](https://openjdk.org/jeps/401), [JEP 544](https://openjdk.org/jeps/544), [JEP 546](https://openjdk.org/jeps/546).
- [Свързан урок за benchmark correctness](BENCHMARK-CORRECTNESS.md), [Leyden](README.md), [JFR](../jfr-troubleshooting/README.md).

[NotebookLM P1 prompt](BIG-RUNTIMES-NOTEBOOKLM-PROMPT.md).