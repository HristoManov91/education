# Tech Radar · Java Next 2026: Babylon, Detroit, Panama, Loom, Lilliput

> [Nicolai Parlog — Java Next, Devoxx Belgium 2026](https://www.youtube.com/watch?v=ufhs9THbYZI).  
> **Оценка:** обзорната лекция P2; Babylon и Detroit **P3 / Watch**. Обновено: **2026-11-02**.  
> Това е **проектна бележка, НЕ runnable lab**. Не добавяме Maven модул или production dependency, защото API-тата още се развиват.

## Реален казус

Java backend получава изискване „да използваме GPU за някои изчисления“ или „Python скрипт да изпълнява бизнес логика директно в JVM“. Едновременно чуваме, че Java може да работи с native библиотеки чрез Panama и вече има virtual threads.

Това са **четири различни проблема**. Не всяко „Java работи с друг език/хардуер“ означава един и същ API или зрелост.

## Технологии и реални решения

| Проект | Какво решава | Примерен use case | Кога НЕ | Приоритет |
| --- | --- | --- | --- | --- |
| **Babylon — Code Reflection / HAT** | Представя Java код като code model за анализ/трансформация към чужди execution models (GPU, DSL, data processing) | Малък, изчислително тежък kernel с доказан bottleneck | Обикновени CRUD/SQL/HTTP услуги; не приемай, че OOP код автоматично работи на GPU | **P3 Watch** |
| **Detroit — JSR-223 engines for V8/CPython** | Експериментална интеграция на JavaScript/Python runtimes през Java scripting API | Контролирани scripts за локална автоматизация/изчисления | Недоверени скриптове в production без изолация, memory/CPU limits, permission model и support plan | **P3 Watch** |
| **Panama — FFM (Final)** | Safe-ish, explicit native calls и memory segments без JNI boilerplate | Вече съществуваща C library с осмислен интерфейс | Ако Java библиотека покрива задачата; изисква native lifecycle/ABI error review | **Покрито** |
| **Panama — Vector API (Incubator)** | Явен SIMD за data-parallel loops | Числови масиви, image/signal workloads с измерен bottleneck | Обичайна бизнес логика, API stability constraints | **Покрито / версия note** |
| **Loom — Virtual Threads / Structured Concurrency** | По-прост concurrent blocking I/O, parent-child task lifecycle | Висок concurrency с I/O waiting | CPU-bound работа без resources; външните pool limits остават | **Покрито** |
| **Lilliput — Compact Headers** | Намалява object header footprint | Много малки heap objects | Оптимизация без heap/GC baseline; CPU trade-off | **Покрито** |

## 1. Babylon — какво действително предлага

[39:53–45:33](https://youtu.be/ufhs9THbYZI?t=2393). Лекторът описва **code reflection** (получаваме модел на програмата, не само метаданни за клас/метод). Проекти като **HAT** (Heterogeneous Accelerator Toolkit) експериментират с трансформиране на подходящ Java код към GPU kernels.

Потенциален workflow:

~~~text
Java computation (подходящ ограничен kernel)
   → compiler-generated code model
   → analysis / transformation
   → GPU-specific compilation and execution
~~~

**Не е обещание** за автоматично GPU ускоряване на Spring services, JDBC или произволна OOP програма. За GPU трябва data-parallel дизайн, memory transfer budget, подходящи data structures и измервания. Babylon/HAT са exploratory и не е редно да се използват като final public JDK API на Java 25.

### Кога бихме го преместили от P3 към P2/P1

При конкретен GPU/data-processing use case с ясно профилиран CPU bottleneck, актуален incubator API и поддържан target runtime, плюс сравнение срещу Java Vector API/native библиотека и fallback решение.

**Печалба:** Java tooling/tests преди трансформация. **Цена:** compiler/toolchain maturity, специфично data layout, driver/GPU compatibility, дебъгване на device side.

## 2. Detroit — какво е, и защо не сега

[49:57–50:31](https://youtu.be/ufhs9THbYZI?t=2997). Detroit е нов OpenJDK проект за **`javax.script` / JSR-223 engine** интеграция с **V8 JavaScript** и **CPython Python**, а не нов Java езиков syntax и не еквивалент на GraalVM polyglot.

Това е ранно engineering направление. За production въпросите са:
- Какъв е API contract и lifecycle за V8/CPython instances?
- Как са ограничени CPU, memory, file/network access и native extensions?
- Как управляваме GIL/concurrency, Java ↔ Python/JS type mapping и exceptions?
- Дали наистина трябва да изпълняваме скрипта в една JVM, или **отделен процес/услуга** дава по-добра isolation boundary?

**Кога си струва:** контролирани вътрешни scripts, където interoperability носи реална стойност и рисковете са решени. **Кога НЕ:** като обещана безопасна sandbox среда за user-supplied scripts. **По-проста алтернатива:** отделен Python worker/process с ясни input/output contracts и permissions.

### Кога да се преоцени

След стабилен engine lifecycle API, предсказуем dependency/build и licensing модел, интеграционни тестове и доказана безопасна изолация за нашия deployment.

## 3. Неща, които НЕ дублираме

- [Panama / Vector API — Java Evolution Guide](JAVA-EVOLUTION-GUIDE.md) — FFM е final от Java 22; Vector API остава в incubation и може да се развие след Valhalla. Няма нужда от нов lab само за тази обзорна лекция.
- [Project Loom lab](concurrency/project-loom/README.md) — Virtual Threads са final в 21; structured concurrency се развива по JDK версия. Виж [Java 28 version note](concurrency/project-loom/JAVA-28.md).
- [Project Leyden / Compact Object Headers](jvm/performance-evolution/README.md) — доставени header improvements и startup lab; [нова JDK 28 native-code бележка](jvm/performance-evolution/LEYDEN-JDK28-AOT-CODE.md).
- [Project Valhalla — P1 урок](language/data-oriented-programming/VALHALLA-VALUE-OBJECTS.md) — реалната нова материална добавка от презентацията.
- [Project Amber — P2 design note](language/data-oriented-programming/AMBER-FUTURE-DESIGN.md) — withers/carrier classes, без преждевременен syntax.

## Decision checklist: приемане на експериментална технология

- [ ] Има действителен workload и измерен bottleneck/интеграционна нужда?
- [ ] Stable, preview, incubator или prototype? Версията и поддръжката са изрично записани?
- [ ] Кой ще носи operational ownership и security patching?
- [ ] Има ли по-прост вариант със стабилен Java 25 API или отделен процес?
- [ ] Има rollback, e2e test, perf baseline и access control?
- [ ] Разделяме „интересен R&D demo“ от production adoption?

**Практично заключение:** записваме Babylon/Detroit като Watch; не инвестираме в runnable labs без реален trigger. Основната инвестиция от Java Next е Valhalla и в по-малка степен Leyden/Amber.

## Оригинални източници

- [Java Next — Devoxx Belgium 2026](https://www.youtube.com/watch?v=ufhs9THbYZI) и [слайдове от Nicolai Parlog](https://slides.nipafx.dev/java-next/2026-10-02-jcp/).
- [OpenJDK Project Babylon](https://openjdk.org/projects/babylon/) и [Babylon/HAT repository](https://github.com/openjdk/babylon).
- [OpenJDK Project Detroit](https://openjdk.org/projects/detroit/).
- [OpenJDK Project Panama](https://openjdk.org/projects/panama/), [JEP 454 — FFM](https://openjdk.org/jeps/454).
- [OpenJDK Project Loom](https://openjdk.org/projects/loom/) и [JEP 444](https://openjdk.org/jeps/444).
- [JEP 519 — Compact Object Headers](https://openjdk.org/jeps/519).
