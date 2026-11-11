# Tech Radar · Java Next 2026: Babylon, Detroit, Panama, Loom, Lilliput

> [Nicolai Parlog — Java Next, Devoxx Belgium 2026](https://www.youtube.com/watch?v=ufhs9THbYZI).  
> **Оценка:** обзорната лекция P2; Babylon и Detroit **P3 / Watch**. Обновено: **2026-11-02**.  
> Това е **проектна бележка, НЕ runnable Babylon/Detroit lab**. Не добавяме техни production dependencies, защото API-тата още се развиват. **Изключение за стабилната FFM технология:** вече имаме отделен [P2 runnable Java 25 FFM & jextract lab](jvm/native-interoperability/README.md), базиран на друга Devoxx лекция.

## Реален казус

Java backend получава изискване „да използваме GPU за някои изчисления“ или „Python скрипт да изпълнява бизнес логика директно в JVM“. Едновременно чуваме, че Java може да работи с native библиотеки чрез Panama и вече има virtual threads.

Това са **четири различни проблема**. Не всяко „Java работи с друг език/хардуер“ означава един и същ API или зрелост.

## Технологии и реални решения

| Проект | Какво решава | Примерен use case | Кога НЕ | Приоритет |
| --- | --- | --- | --- | --- |
| **Babylon — Code Reflection / HAT** | Представя Java код като code model за анализ/трансформация към чужди execution models (GPU, DSL, data processing) | Малък, изчислително тежък kernel с доказан bottleneck | Обикновени CRUD/SQL/HTTP услуги; не приемай, че OOP код автоматично работи на GPU | **P3 Watch** |
| **Detroit — JSR-223 engines for V8/CPython** | Експериментална интеграция на JavaScript/Python runtimes през Java scripting API | Контролирани scripts за локална автоматизация/изчисления | Недоверени скриптове в production без изолация, memory/CPU limits, permission model и support plan | **P3 Watch** |
| **Panama — FFM (Final)** | Safe-ish, explicit native calls и memory segments без JNI boilerplate | Вече съществуваща C library с осмислен интерфейс | Ако Java библиотека покрива задачата; изисква native lifecycle/ABI error review | **[P2 runnable FFM/jextract](jvm/native-interoperability/README.md)** |
| **Panama — Vector API (Incubator)** | Явен SIMD за data-parallel loops | Числови масиви, image/signal workloads с измерен bottleneck | Обичайна бизнес логика, API stability constraints | **[P2 runnable lab](jvm/vector-api/README.md)** |
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

### P3 Case Study 2026 — Java Code Reflection → ONNX graph (Ana-Maria Mihalceanu)

**Допълнение без нов runnable Babylon module:** [Ana-Maria Mihalceanu — Java and the Next Generation of Programming Models, Devoxx 2026](https://youtu.be/kuUeHD_k5fs?t=1260), приблизително **21:00–36:40** от автоматичния transcript.

**Реален проблем:** вече можем да изпълним локален AI модел с чужд inference runtime, но как да **изследваме/модифицираме самото изчисление** на Java език, вместо да доставяме само непрозрачен model binary?

Лекторката разграничава две демонстрации:

1. **FFM + jextract** — генерирани Java bindings от native ONNX Runtime GenAI headers, последователни calls за tokenizer, inference/sampling и изрично destroy на native resources. [Имаме отделен P2 Java 25 lab за този интероп механизъм](jvm/native-interoperability/README.md), без ONNX library или LLM.
2. **Project Babylon Code Reflection** — моделиране на Java изчисления (пример `ReLU`), интроспекция и трансформация на представянето им, за да се създаде **ONNX computational graph**. Това не е изпълнение на произволен Java Spring код на GPU.

#### Точният mental model за Code Reflection

```text
Java method annotated @Reflect (experimental Babylon)
    → regular java.lang.reflect.Method
    → experimental code-model FuncOp
    → operations → bodies → blocks → values / results
    → traverse & transform
    → ONNX operation graph (via generated operators, OpGen)
    → ONNX ModelProto/GraphProto serialization (via ProtoGen/Protobuf)
    → compatible ONNX runtime (CPU/GPU depending on executor/configuration)
```

При Babylon `@Reflect`, `FuncOp` и builder/traversal API са **експериментални**, версията им се мени. Не публикуваме непроверен код, който се преструва на Java 25 production API. **Обикновената Reflection API** дава metadata за methods/classes, но сама по себе си не извлича function body/control-flow/data-flow.

Защо е важно: примерът `relu(x) = max(0, x)` е нормална Java логика, но Babylon code model дава възможност за специализиран превод на изчислението в друг graph language. Това изисква **правила за семантична еквивалентност** (numeric types, control flow, supported operators, missing op mapping) и **версиониране** на ONNX operator schemas.

**OpGen** в разказа генерира Java представяния на ONNX operators от техните schema definitions; **ProtoGen** помага за Protobuf съвместимите структури. Тези са **конкретни инструменти/демонстрационен design**, не изискване на стабилния Java 25 standard library. Не ги бъркай с `jextract`, който генерира bindings от **C headers**, а не ONNX code models.

**Нюанс за ONNX:** Protobuf описва **сериализирания model/graph**; по време на inference runtime-ът може да използва други in-memory buffers/representations за tensors. ONNX форматът **не налага** protobuf serialization между всяка операция. Виж [ONNX IR specification](https://onnx.ai/onnx/repo-docs/IR.html).

#### Кога да го използваме, кога НЕ и trade-offs

| Избор | Кога | Кога НЕ | Печалба ↔ цена |
| --- | --- | --- | --- |
| Existing ONNX Runtime Java SDK | Изпълняваме съществуващ ONNX модел | Нужен е недостъпен native/GenAI-specific API | Maintained Java API ↔ native runtime/version support |
| Custom FFM + jextract adapter | Няма подходящ Java SDK или ни трябва конкретна C API функция | Обикновен backend с готов SDK | Контрол ↔ memory/ABI ownership, deployment complexity |
| Babylon Code Reflection → ONNX graph | Експериментален code-to-graph compiler, custom model operators | Production Java 25 без experimental toolchain | Изразителност и трансформации ↔ maturity, semantic correctness, tool maintenance |
| GPU via accelerator runtime | Има profiling доказателство и поддържан GPU backend | CPU workload е достатъчен или няма доказателства | Potential acceleration ↔ data transfer, hardware/runtime limits |

**Защо остава P3:** историята е чудесен архитектурен пример, но изисква експериментални JDK builds, Babylon/ONNX toolchains и model/runtime setup. Без реален backend use case този разход не оправдава нов executable module.

**Оригинални източници:** [лекцията](https://www.youtube.com/watch?v=kuUeHD_k5fs), [OpenJDK Babylon](https://github.com/openjdk/babylon), [ONNX Runtime Java SDK](https://onnxruntime.ai/docs/get-started/with-java), [ONNX IR](https://onnx.ai/onnx/repo-docs/IR.html), [P2 FFM lab](jvm/native-interoperability/README.md).

---

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

## Devoxx 2026 P3 watch — Simple JSON и Hybrid Search

- **JEP 540 / JDK 28 EA:** [Simple JSON API, incubator `jdk.incubator.json`](experiments/jdk28-simple-json/README.md) — подходящ за малки CLI/прототипи, **не** замества Jackson за Java25 Spring Boot. Няма compiled production module.
- **Hybrid Search:** [RRF runnable ranking-lab](algorithms/04-searching-sorting/HYBRID-RANK-FUSION.md) — обединява само rank positions от отделни retrievers, **без** Elasticsearch dependency, cross-tenant data или внушени retrieval quality gains.
- [Map на всички точкови follow-ups от Marit van Dijk / Piotr Przybył](MODERN-JAVA-PLAYFUL-FOLLOWUPS.md).

## P2/P3 — Method Handles и експериментален marshalling дизайн (Devoxx 2026)

[Teaching an Old Handle New Tricks — Maurizio Cimadamore & Viktor Klang](https://www.youtube.com/watch?v=4ck-T2gaMQ4) обяснява основен **стабилен** Java механизъм, върху който стъпват Core Reflection (JEP 416), `StringConcatFactory` и FFM downcalls: `Lookup`/`MethodType` → linked `MethodHandle` → много typed invocations. За **стабилната част** вече имаме [P1 Java25 runnable lesson/JUnit](jvm/method-handles/README.md) и [FFM cached strlen](jvm/native-interoperability/README.md). Това е практическо обучение, не P3 API.

Viktor разглежда **разработвана marshalling концепция**: извличане на record schema и предварително свързани getters/constructors, explicit schema versions и контрол на типовете. **Няма финализиран JDK marshalling API в Java 25, който преподаваме като production готов.** Виж [P2 Java25 строго allowlisted V1/V2 прототип](jvm/method-handles/MARSHALLING-PROTOTYPE.md). Той не е форматът от предложението, не е стандартна Java serialization и не приема произволни class names.

**Decision contract:** използвай Method Handles при повтаряща се динамична операция с известна сигнатура; не ги използвай вместо обикновен direct call без проблем. Заложи нормален Jackson/Protobuf при реален versioned wire protocol, освен ако имаш доказана нужда и owner на собствен формат. Генериран bytecode/Babylon е **отделно експериментално направление**, а не задължителен upgrade от MH. Печалба — reusable linkage; цена — exact typing, capability leakage и compatibility/format ownership.

## P3 — Ask the Architects: String Templates и withdrawn Primitive Patterns

От [Ask the Architects, Devoxx 2026](https://www.youtube.com/watch?v=ewCPBgOrLAE) (~44–47 мин):
- **String Templates:** JEP 465 беше withdrawn; въпросът за **composability на nested templates** и твърде ранно материализиране/валидиране е важен design insight. Това не означава, че в Java25/27 има stable `STR` API. **Не правим runnable lab** за неиздаден syntax; стандартна `String +`, `StringConcatFactory` и безопасна parameterization са покрити в [Method Handles](jvm/method-handles/README.md).
- **Primitive patterns:** **ново развитие на 11.11.2026 след панела:** [официално уведомление на Amber екипа](https://www.mail-archive.com/amber-spec-experts%40openjdk.org/msg00923.html) — 6th preview canceled, feature **removed in JDK28, including `--enable-preview`**. Старите JDK23–27 previews не са обещание за нов API. [Подробности](language/data-oriented-programming/AMBER-FUTURE-DESIGN.md).
- **Valhalla:** специализацията на generics/physical flattening е evolving design/VM optimization, не JDK28 production contract. [Valhalla P1 guide](language/data-oriented-programming/VALHALLA-VALUE-OBJECTS.md) остава единственият каноничен урок за value semantics.

**Какво печелим:** technology radar без фалшива adoption инструкция. **Кога не:** не пренаписваме production код за proposal/withdrawn feature. **Цена:** периодично проследяване на release notes; **по-просто:** стабилни Java25 records/patterns и нормален interpolation/SQL parameterization.

## 3. Неща, които НЕ дублираме

- [Panama / Vector API — Java Evolution Guide](JAVA-EVOLUTION-GUIDE.md) — FFM е final от Java 22; Vector API остава в incubation. [P2 FFM+jextract](jvm/native-interoperability/README.md) вече е runnable модул от лекцията на Ana-Maria, без да изграждаме голям ONNX/GPU проект.
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
