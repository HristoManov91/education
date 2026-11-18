# P2 — GraalVM Next: Native Image, Layers, Crema и Web Image

> **Devoxx Belgium 2026 · Alina Yurenko и Thomas Wuerthinger · Java 25 LTS baseline**  
> Източник: [GraalVM Next: Layers, Crema, and WebAssembly in Action](https://www.youtube.com/watch?v=OLQ98Ko4p3Q), анализ на предоставения `GraalVM_Next_Alina_Yurenko_OLQ98Ko4p3Q.srt`. Показаните от лекторите числа са **илюстрация на тяхната демонстрация, не резултати от нашите тестове**.
>
> **Статус:** стандартните Java 25 JUnit тестове са предназначени за root CI; експерименталните **Native Image Layers**, **Crema native** и **Web Image** остават **manual/opt-in**. Няма автоматизиран GraalVM native CI и не твърдим успешна native компилация без реален GraalVM build log.

## 1. Реалният казус — десет backend услуги и ресурсен бюджет

Имаме 10 сходни Java микросървиса: еднакъв JDK, версия на framework, Jackson, logging, Netty и общ корпоративен SDK. Приложенията работят дълго, но при deployment едновременно подгряват JIT, увеличават потреблението на RAM и подлагат Kubernetes на memory pressure (натиск върху паметта). Чуваме, че Native Image „стартира мигновено и използва малко RAM“.

Проблемът е, че тази реклама **не решава автоматично**:
- native build-овете са скъпи като CPU/RAM/време и могат да се забавят от reflection (достъп до класове/конструктори/методи по име);
- дублира се компилацията на стабилните 90+% общ код при всеки service;
- startup не е throughput и малък heap не е малък RSS/PSS;
- closed-world assumption (всички класове са известни при компилация) пречи на класове, генерирани при runtime, напр. с Byte Buddy;
- доставка на shared native layer е различна от доставка на самостоятелен executable или Docker image layer.

### Защо ни интересува

Ако deployment често създава краткоживеещи workers, startup/readiness и baseline RSS може да са важни. Ако услугата е дългоживееща и ограничава Oracle connection pool, оптимизацията на startup може да няма бизнес ефект. Целта е да направим **верен избор**, не безусловно да преминем към Native Image.

**Bad решението:** „Щом GraalVM ни дава по-бърз старт, компилираме всичко native и насилствено прехвърляме reflection към статична конфигурация.“ Изглежда разумно, докато не се появи динамичен plugin, грешна metadata, по-дълъг CI, несъвместим native agent, проблем при security patch или неочаквана загуба на steady-state throughput.

**Good решението:** първо детерминистичен workload и тестове; после отделно измерваме JVM, Leyden AOT cache и Native Image с еднакви входни данни; ако няколко приложения имат стабилни общи библиотеки, изследваме Layers; ако има нужда от непознати при build-time класове, изследваме експерименталния Crema; за браузър/Wasm — отделен P3 decision.

## 2. Точно кои теми вземаме от лекцията

| Време в SRT | Какво показват лекторите | Нашият материал |
| --- | --- | --- |
| 00:50–06:50 | Native Image AOT, Graal JIT, polyglot/Oracle DB и показани memory/image improvements между техни версии | Тук само допълваме [JIT/Leyden знанията](../performance-evolution/README.md) |
| 07:00–11:30 | AI-assisted native compatibility, Reachability Metadata Repository, автоматизирани library tests, заявки за нови Maven coordinates | [Compatibility + reflection lab](NATIVE-IMAGE-COMPATIBILITY.md) |
| 12:00–19:50 | Една обща компилирана основа, 10 Micronaut apps, PSS/RSS и app layers | [Истински 2-app Maven Layer producer/consumers](LAYERS-LAB.md) |
| 20:00–35:25 | Crema: нови класове след build, Byte Buddy, interpreter/JIT, source/JAR runner и Micronaut runtime | [Byte Buddy/JUnit + manual Crema native](CREMA-EXPERIMENT.md) |
| 35:25–40:00 | Spring приложение, компилирано за браузър с Web Image и JS wrapper | [P3 Web Image note](WEB-IMAGE-RADAR.md) |
| 40:00–45:00 | Ритъм на releases, roadmap, custom runtimes и бъдещи GC идеи | Watch-only; **не** обещаваме все още неиздадени GC възможности |

**Корекции/граници на SRT:** автоматичното разпознаване пише „GrowVM“, „Kramer“ и „Micronode“, но техническите термини са **GraalVM**, **Crema** и **Micronaut**. Лекторите на 16:38 наричат PSS „private memory“, но *PSS ≠ private memory*: това е private + пропорционален дял на shared pages. Бъдещите release обещания от сцената не са API contract.

## 3. Mental model: три различни пътя

```text
Java classes / JAR
  ├─ HotSpot JVM + JIT                  → needs JVM, dynamic class loading, runtime optimizations
  ├─ JVM + Leyden AOT cache             → still JVM, precomputed loading/linking/profiles
  └─ GraalVM Native Image AOT           → native executable, closed-world by default
       ├─ traditional single binary    → full self-contained application (OS libs still apply)
       ├─ Native Image Layers           → base .nil build artifact + runtime shared libraries
       └─ Crema (experimental)          → interpreter / optional JIT for NEW runtime-loaded bytecode

Separate target:
  └─ Web Image (experimental)           → JS wrapper + Wasm for a JS host
```

- **GraalVM JIT** работи във JVM и може да оптимизира warm code. **Native Image** компилира предварително; нито единият модел не е универсално по-бърз за всякакъв workload.
- **Project Leyden** запазва HotSpot/JVM и ползва AOT cache. Не е компилация до native executable.
- **Layers** са разделяне на *native build/runtime artifacts*, не просто Docker cache. Layered app има runtime зависимости към предоставените shared libraries.
- **Crema** добавя runtime class loading, но не премахва всички ограничения на Native Image или нуждата от съвместими библиотеки.
- **Web Image** не е Brewlet или standalone WASI deployment; ползва JavaScript wrapper.

## 4. README → изпълним код

| Тема | Код / конфигурация | Тест / експеримент |
| --- | --- | --- |
| Един и същ Java 25 workload за runtime сравнение | [`RuntimeGreetingApp.java`](src/main/java/bg/hristomanov/education/graalvm/RuntimeGreetingApp.java), [`GreetingProvider.java`](src/main/java/bg/hristomanov/education/graalvm/GreetingProvider.java), [`StaticGreeting.java`](src/main/java/bg/hristomanov/education/graalvm/StaticGreeting.java) | [`RuntimeGreetingAppTest.java`](src/test/java/bg/hristomanov/education/graalvm/RuntimeGreetingAppTest.java) |
| Reflection compatibility | [`GreetingFactory.java`](src/main/java/bg/hristomanov/education/graalvm/GreetingFactory.java), [`ReflectionGreeting.java`](src/main/java/bg/hristomanov/education/graalvm/ReflectionGreeting.java), [`reachability-metadata.json`](src/main/resources/META-INF/native-image/bg.hristomanov.education/graalvm-native-image/reachability-metadata.json) | Същият [JUnit test](src/test/java/bg/hristomanov/education/graalvm/RuntimeGreetingAppTest.java), [manual native checklist](NATIVE-IMAGE-COMPATIBILITY.md) |
| Native Image Maven profile, опционален | [`pom.xml`](pom.xml) (`native-lab`), Native Build Tools 1.1.19 | Ръчен native build / compare, **не в CI** |
| Native Image Layers: един JDK base, два executable consumers | [`layers-lab/pom.xml`](layers-lab/pom.xml) + [producer](layers-lab/base-layer/pom.xml) + [English](layers-lab/english-service/pom.xml) / [Spanish](layers-lab/spanish-service/pom.xml) | [`LAYERS-LAB.md`](LAYERS-LAB.md) и [`linux_memory.py`](scripts/linux_memory.py) |
| Crema — динамичен клас с Byte Buddy | [`CremaDemo.java`](crema-lab/src/main/java/bg/hristomanov/education/graalvm/crema/CremaDemo.java) | [`CremaDemoTest.java`](crema-lab/src/test/java/bg/hristomanov/education/graalvm/crema/CremaDemoTest.java) и [experiment guide](CREMA-EXPERIMENT.md) |
| Измервания | [`measure_startup.py`](scripts/measure_startup.py), [`linux_memory.py`](scripts/linux_memory.py) | [`test_tools.py`](scripts/test_tools.py) |
| Web Image | [`WEB-IMAGE-RADAR.md`](WEB-IMAGE-RADAR.md) | **Architecture/watch-only** — не е измислен Wasm runnable test |

**Обхват на изпълнимите примери:** минимални Java 25 приложения, за да изолираме VM/native ефекта; **не са Spring Boot microservices и не доказват Spring Boot native support**. За реална Spring интеграция използвай същите метрики върху [официалния GraalVM Spring demo](https://github.com/graalvm/graalvm-demos/tree/master/native-image/microservices/spring-hello-rest-maven-layered), след като свериш реалния път/версия. При framework workload са нужни отделни Spring AOT, endpoint и integration tests. Micronaut числата от сцената не са валидни baseline за нашия малък пример.

## 5. Вътрешна Java 25 лаборатория (без GraalVM)

Пусни от **root на education**:

```bash
mvn -pl java/jvm/graalvm-native-image -am test
python3 -m unittest discover -s java/jvm/graalvm-native-image/scripts -p 'test_*.py'
```

След това от директорията на модула:

```bash
java -cp target/classes bg.hristomanov.education.graalvm.RuntimeGreetingApp \
  --name=Ada --iterations=3

java -cp target/classes bg.hristomanov.education.graalvm.RuntimeGreetingApp \
  --name=Ada --iterations=3 --reflection
```

Очаквани резултати (статичен и reflection режим имат **един и същ бизнес резултат**):

```text
READY greeting=Hello, Ada! checksum=33 mode=static
READY greeting=Hello, Ada! checksum=33 mode=reflection
```

Тестовете проверяват и invalid config / non-allowlisted class. Вграденият `--hold-ms=10000` служи **само за memory sampling**; не използвай такъв процес за startup сравнение.

## 6. Runtime сравнение: JVM → Leyden cache → Native Image

От директорията `java/jvm/graalvm-native-image` с **Java 25 JDK**:

### JVM baseline

```bash
python3 scripts/measure_startup.py --runs 10 -- \
  java -cp target/classes bg.hristomanov.education.graalvm.RuntimeGreetingApp \
  --iterations=50000
```

### Leyden AOT cache (същите класове и workload)

```bash
java -XX:AOTCacheOutput=target/graalvm-lab.aot \
  -cp target/classes bg.hristomanov.education.graalvm.RuntimeGreetingApp \
  --iterations=50000

python3 scripts/measure_startup.py --runs 10 -- \
  java -XX:AOTCache=target/graalvm-lab.aot \
  -cp target/classes bg.hristomanov.education.graalvm.RuntimeGreetingApp \
  --iterations=50000
```

### Native Image (изрично опционален ръчен build)

Нужен е **GraalVM 25.x native-image** и platform toolchain (C compiler, linker) със съответната архитектура:

```bash
java -version
native-image --version
mvn -Pnative-lab -DskipTests package

python3 scripts/measure_startup.py --runs 10 -- \
  ./target/runtime-greeting --iterations=50000
```

**Няма гаранция, че Native Image ще има по-добър steady-state throughput.** Нашето `checksum` е correctness oracle (проверка за същия резултат), а не throughput benchmark: цикълът е твърде прост и компилаторът може да го оптимизира. За p95/p99, QPS и GC добави еднакъв Spring endpoint/реален workload със закачен load generator и [Benchmark Correctness](../performance-evolution/BENCHMARK-CORRECTNESS.md). Не сравнявай sample с реална система и не публикувай „ускорение“ без съпоставим harness.

**Правила за честно сравнение:** еднакъв Java bytecode/input, еднаква машина, включени/изключени другите натоварвания, CPU governor/thermal state, поредни повторения, редуване на режимите, p50 + разсейване, process wall-clock vs READY отделно, RSS/PSS/CPU; CPU build time и image size записваме отделно. Ако кешът не е съвместим, отбелязваме failure/invalid comparison, не го игнорираме.

## 7. Кога да изберем кой подход

| Техника | Проблем / кога | Кога НЕ | Полза | Цена / по-проста алтернатива | Доказателство |
| --- | --- | --- | --- | --- | --- |
| **HotSpot JVM/JIT** | Дългоживееща бизнес услуга с динамични библиотеки и добра observability | Cold-start доминира SLO и memory budget е критичен | Гъвкав runtime, mature tooling | Warm-up и JDK image; [Java 25 baseline](src/test/java/bg/hristomanov/education/graalvm/RuntimeGreetingAppTest.java) |
| **Leyden AOT cache** | Измерен JVM startup/linking bottleneck, искаме да запазим JVM | Няма startup симптом или cache compatibility проблем | Потенциално по-малък startup/warm-up | Training step/cache invalidation; [съществуващият Leyden lab](../performance-evolution/README.md) |
| **Native Image** | Многократни кратки стартирания, serverless/CLI/sidecar, строг RSS/scale-to-zero budget | Dynamic loading или native build cost надвишават ефекта; няма evidence | AOT startup и footprint потенциал | Build CPU/RAM, metadata, native debug; JVM+Leyden е по-проста алтернатива |
| **Native Image Layers** | Много приложения със стабилни общи dependencies, възможен shared memory benefit | Едно приложение или различни stack версии / неуправляем runtime deployment | Reuse base и PSS sharing | Отделен producer/versioning, shared libraries, cache storage; [2-service experiment](LAYERS-LAB.md) |
| **Crema** | Непознат runtime-generated bytecode или голям устойчив AOT framework + често променящ се code | Строг production SLA без compatibility evidence, работи без native | Позволява нови класове в Native Image | Interpreter/JIT и експериментални ограничения; [Crema experiment](CREMA-EXPERIMENT.md) |
| **Web Image** | Java logic на JS-hosted Wasm target с потвърдена browser capability | Обикновен REST backend / standalone WASI очакване | Java в JS host | JS wrapper, support и size; [P3](WEB-IMAGE-RADAR.md) |

## 8. Връщане към казуса и план за действие

**Какво решихме:** имаме един изолиран Java 25 workload с автоматични correctness тестове; два native layer consumers за ръчно компилиране; Byte Buddy dynamic class loader, чието JVM поведение е доказуемо; инструменти за сравними startup и PSS/RSS измервания. Когато трябва да вземем решение за реалните 10 услуги, сравняваме **цялата deployment система**, а не само „Binary X е малко“.

1. Първо измери JVM baseline и готовия Leyden лабораторен пример.
2. Ако startup/RSS са реален bottleneck, направи отделен GraalVM 25.x native build и повтори измерванията.
3. Ако има много идентични dependency stacks, пусни ръчно Layers и виж сумарния PSS на 1 срещу 2 процеса; **не** приемай 30 MB от презентацията за прогнозен резултат.
4. Ако има runtime-generated класове, прочети Crema experimental limitations; native build само като изследователски spike.
5. Ако целиш браузър, не преминавай към Web Image без работещ JS-host target и проверена съвместимост.

**Code review checklist:** измерена цел? same inputs/checksum? native build версия? reflection metadata + negative tests? AOT cache provenance? shared layer ABI/artifact compatibility? OS/arch/linker? runtime libs packaged? RSS vs PSS? повторения и error bars? fallback/rollback? GitHub Actions разходи?

## Оригинални източници

- [Лекцията на Yurenko/Wuerthinger](https://www.youtube.com/watch?v=OLQ98Ko4p3Q), Devoxx 2026, 47-минутен SRT (получен от потребителя).
- [GraalVM Native Image](https://www.graalvm.org/latest/reference-manual/native-image/), [Native Build Tools Maven](https://graalvm.github.io/native-build-tools/latest/maven-plugin.html), [Layered Native Images — guide](https://graalvm.github.io/native-build-tools/latest/layered-images.html).
- [Reachability Metadata](https://www.graalvm.org/latest/reference-manual/native-image/metadata/), [Tracing Agent](https://www.graalvm.org/latest/reference-manual/native-image/Agent/), [Native Image demo repository](https://github.com/graalvm/graalvm-demos).
- [GraalVM 25.1 release notes — runtime class loading](https://www.graalvm.org/release-notes/25.1/), [GraalVM 25.3](https://www.graalvm.org/release-notes/25.3/), [GraalVM Web Image](https://www.graalvm.org/latest/reference-manual/web-image/).
- [Project Leyden lab](../performance-evolution/README.md), [JVM in Containers](../performance-evolution/JVM-IN-CONTAINERS.md), [Benchmark Correctness](../performance-evolution/BENCHMARK-CORRECTNESS.md), [Brewlet WebAssembly case](../../../docs/case-studies/006-brewlet-java-oci-runtime.md).

**Версия и source discipline:** изводите за сценичните демонстрации идват от SRT; синтаксисът/версиите на инструментите и production ограниченията са сверени с официална документация. **Не представяме** conference numbers, future GC roadmap или AI-agent обещания за наш лично измерен резултат.
