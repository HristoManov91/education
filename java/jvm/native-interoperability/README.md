# P2 · Java Native Interoperability — Foreign Function & Memory API (FFM) + jextract

> **Java 25 LTS · Maven · JUnit 5 · ~55 мин обучение (20 fundamentals + 20 deep dive + 15 hands-on).**  
> Източник: [Ana-Maria Mihalceanu — Java and the Next Generation of Programming Models, Devoxx Belgium 2026](https://www.youtube.com/watch?v=kuUeHD_k5fs); предоставеният Gemini SRT, приблизително [08:00–17:50](https://youtu.be/kuUeHD_k5fs?t=480) (FFM, jextract и ONNX Runtime GenAI). Автоматичният транскрипт има грешки в изписването (напр. „onyx“ вместо **ONNX**, „gextract“ вместо **jextract**), затова Java API/командите са сверени с OpenJDK/Oracle.  
> **Цел:** Mid+ → Early Senior Java backend разработчик. Production-like Java 25 examples, отделен optional native tool; без голям LLM download, GPU, credentials и специални native библиотеки в Maven CI.

## 1. Реален проблем — Java service трябва да извика чужд runtime

При интеграция с външен C/C++ SDK, медийна библиотека или native AI inference engine Java кодът не може директно да извика произволен exported C symbol като нормален Java метод. Трябва ни начин за:
- **ABI-съвместимо извикване** (Application Binary Interface: сигнатури, указатели, регистри, native calling convention);
- предаване и прочитане на buffers с точен **layout** и **lifetime**;
- коректно затваряне на native ресурси, независимо дали request-ът е успешен;
- проверка на platform/toolchain и защита срещу неподходящи входни данни.

Лекторката демонстрира **Java → FFM → генерирани jextract bindings → native ONNX Runtime GenAI → локален модел**. Това е конкретен *interop pattern*, не препоръка всеки Java AI backend да пише FFM bindings. При нас възпроизвеждаме същите принципи със **стандартна C функция**, без model downloads.

### Bad first reaction

„Да вземем native pointer, да го пазим в singleton, да познаем C сигнатурата и да изпълняваме inference от всички Spring threads.“

Тук се смесват 4 риска: грешна ABI signature, невалиден pointer, неясен ownership и паралелен достъп след освобождаване. Java FFM има проверки за **lifetime/bounds/thread ownership на управляваните MemorySegments**, но **не прави произволен native C code memory-safe** и не проверява истинската C сигнатура вместо нас. Грешен descriptor може да срине JVM.

### Good engineering choice

1. Първо избери дали въобще ти трябва native interop (може вече да има стабилен Java SDK).
2. Ако има C ABI: валидирай типове, архитектура, версия и ownership contract.
3. Използвай `Linker` + `FunctionDescriptor` за прост API или `jextract` за богат header.
4. Allocations с `Arena`, не споделяй confined segments между нишки и затваряй своевременно.
5. Test happy path, boundary, negative lifetime/thread cases и platform fallback.
6. Native access е **явно** enabled; не го включвай без нужда за цялата application.

## 2. README → код → тестове

| Какво доказваме | Java 25 код | Реален тест |
| --- | --- | --- |
| C function downcall + MethodHandle | [NativeCStringLength.java](src/main/java/bg/hristomanov/education/ffi/NativeCStringLength.java) | [NativeCStringLengthTest.java](src/test/java/bg/hristomanov/education/ffi/NativeCStringLengthTest.java) |
| Runnable standalone Java 25 main | [FfmDemo.java](src/main/java/bg/hristomanov/education/ffi/FfmDemo.java) | C downcall + off-heap sum in terminal (Linux x64 CI platform) |
| UTF-8 байтове срещу Java UTF-16 символи | `NativeCStringLength.utf8Length` | Unicode тест, без изкуствени benchmark стойности |
| Не допускаме embedded NUL и null | `NativeCStringLength` | Негативни JUnit тестове |
| Off-heap memory / ValueLayout / int arithmetic | [NativeIntBuffer.java](src/main/java/bg/hristomanov/education/ffi/NativeIntBuffer.java) | [NativeMemorySafetyTest.java](src/test/java/bg/hristomanov/education/ffi/NativeMemorySafetyTest.java) |
| Bounds, closed arena, confined vs shared thread access | `Arena / MemorySegment` | OOB, after-close, WrongThread и cross-thread read |
| C header → generated Java source → native library | [jextract/native_math.h](jextract/native_math.h), [native_math.c](jextract/native_math.c), [GeneratedBindingSmoke.java](jextract/GeneratedBindingSmoke.java) | [run-linux-macos.sh](jextract/run-linux-macos.sh) — **отделно, optional** |
| Main Maven CI/JUnit без външна C toolchain | [pom.xml](pom.xml) | `mvn -pl java/jvm/native-interoperability test` |

## 3. Fundamentals — как работи FFM downcall

[Реалният пример](src/main/java/bg/hristomanov/education/ffi/NativeCStringLength.java) извиква `size_t strlen(const char *value)`. Потокът е:

```text
Java String
   → validate null / embedded NUL
   → Arena.ofConfined() + arena.allocateFrom(text)  [UTF-8 + terminator]
   → Linker.nativeLinker() + defaultLookup("strlen")
   → FunctionDescriptor(size_t, pointer) + MethodHandle
   → C strlen(...)
   → Java long result
   → try-with-resources closes arena and invalidates memory
```

При 64-битовата демонстрационна ABI предпоставка `size_t` е 8 bytes; **това не е универсално правило за всеки C target**. Примерът проверява `Linker.canonicalLayouts().get("size_t")`. Ако платформата е друга, отказваме вместо да извикваме C функция с грешна signature. Symbology/availability на `strlen` в default lookup зависи от платформата; JUnit приема този изричен capability boundary.

**Защо `strlen("A😊")` връща 5, а Java `"A😊".length()` връща 3?** C обработва UTF-8 byte sequence с NUL terminator; Java `String.length()` брои UTF-16 code units. Това е коректност, не performance нюанс.

### Как се изпълнява (Java 25)

```bash
mvn -pl java/jvm/native-interoperability test
```

`pom.xml` задава `--enable-native-access=ALL-UNNAMED` **само на тестовата JVM**. В production модул с `module-info.java` предпочитай по-тесен `--enable-native-access=<module-name>`; не изискваме включване на native access глобално за всички образователни модули.

За **реален standalone Java 25 запуск** от repository root (с включен native access само за тази JVM):
```bash
mvn -pl java/jvm/native-interoperability -DskipTests package
java --enable-native-access=ALL-UNNAMED \
  -cp java/jvm/native-interoperability/target/classes \
  bg.hristomanov.education.ffi.FfmDemo
```
Очакваш `UTF-8 bytes via C strlen: 5` и `Off-heap int sum: 42` при поддържана 64-битова платформа с наличен `strlen`.

**Native access** е силна capability: FFM downcalls са restricted, а неправилен C signature/unsafe pointer usage може да повреди паметта. Този flag не sandbox-ва native библиотеката. Никога не зареждай непроверен binary от непознат източник.

## 4. Deep dive — ownership, bounds и lifetime

| Arena | Кой има достъп? | Кой управлява lifetime? | Практично |
| --- | --- | --- | --- |
| `Arena.ofConfined()` | Само собственикът (thread) | Explicit close от owner thread | По подразбиране за кратък request-local native buffer |
| `Arena.ofShared()` | Различни threads, с координация | Explicit close | Cross-thread access, когато lifetime е ясно управляван |
| `Arena.ofAuto()` | Различни threads | GC lifecycle, не explicit close | Когато auto lifetime е подходящо, но имаш по-слаб контрол |
| `Arena.global()` | Различни threads | Никога не се затваря | Само съзнателно дългоживеещи buffers; риск от leak |

[NativeMemorySafetyTest](src/test/java/bg/hristomanov/education/ffi/NativeMemorySafetyTest.java) доказва:
- `segment.get(JAVA_INT, offset)` отвъд `byteSize` хвърля `IndexOutOfBoundsException`;
- достъп до segment след `arena.close()` хвърля `IllegalStateException`;
- confined segment в друг thread хвърля `WrongThreadException`;
- shared arena позволява read в друг thread докато arena е жива;
- бизнес `Math.addExact` продължава да пази overflow invariant — FFM не оправя бизнес грешки.

**Внимание:** MemorySegment API има *spatial/temporal/thread safety* за собствените управлявани segments. Но `MemorySegment.reinterpret`, raw pointers, external native allocation, ABI mismatches и JNI/native C code също носят unsafe рискове. Лекторката говори за намаляване на ненужните копирания; **zero-copy НЕ се гарантира автоматично от FFM** — определят го API, ownership, memory layout и runtime.

## 5. jextract — премахване на ръчното ABI описание, когато има C headers

[Лекция 09:10–15:00](https://youtu.be/kuUeHD_k5fs?t=550): jextract чете C header-и и генерира Java FFM bindings. В ONNX demo native GenAI library/engine/model dependencies се конфигурират отделно; не са едно и също.

Нашата [jextract лаборатория](JEXTRACT-LAB.md) използва миниатюрни `edu_square(int)` и `edu_add(int,int)`, компилирани от собствен C код. `jextract` генерира Java класове в `target/`, след което Java smoke пример извиква C функциите без собствен JNI wrapper.

**jextract е отделен EA/tool build, не стандартна част от Java 25 `javac`.** Версията му трябва да съответства на JDK 25. Той е **optional** и генерираният код/С binary НЕ се commit-ват. Няма jextract dependency в main Maven reactor и не твърдим, че този optional toolchain вече е бил изпълнен в CI.

## 6. Архитектурното решение: Java SDK срещу собствен FFM

| Избор | Кога | Кога НЕ | Ползи ↔ загуби |
| --- | --- | --- | --- |
| **Готов Java SDK** (напр. ONNX Runtime Java) | Поддържан runtime/API/platform и нужните inference operations | SDK липсва или не expose-ва необходим native feature | По-малко ABI код ↔ library release/dependency constraints |
| **Ръчен FFM** | Няколко ясно документирани C функции, platform control | Сложна callback/struct API без поддръжка | Пълен контрол ↔ ABI и lifetime maintenance |
| **jextract bindings** | Голяма/често обновявана C header API | Нямаш съвместим extractor/toolchain или native binaries | По-малко ръчно писане ↔ generated API lifecycle, native packaging |
| **JNI** | Legacy native library / special VM-level integration | Нов обикновен C API, за който FFM е подходящ | Съвместимост ↔ C/JNI boilerplate, memory bugs |
| **Отделен native процес/service** | Тежки/недоверени библиотеки, isolation, CPU/GPU scheduling | Изискване за нисък in-process overhead | Fault isolation ↔ IPC/deployment/serialization overhead |

**ONNX SDK нюанс:** ONNX Runtime Java API (`OrtEnvironment`, `OrtSession`, `OnnxTensor`) вече е наличен и е нормален първи избор за стандартни ONNX модели. Демонстрацията на лекторката използва **ONNX Runtime GenAI C API** и generated bindings, за да илюстрира FFM. Тези два use case-а не са идентични, а Java и native GenAI API version/availability се проверяват отделно.

## 7. Кога НЕ бих избрал FFM в Java backend

- Ако бизнес задачата се решава с обикновен Java API и няма native bottleneck;
- ако native dependency прави build/release/OS/architecture непропорционално сложен;
- ако не знаем memory ownership, destruction order и thread-safety contract;
- ако library interface е C++, а нямаме стабилен C ABI wrapper (jextract чете C headers);
- ако native crashes на process-level не са допустими — тогава отделен service/process може да е по-безопасен;
- ако целта е само да „ускорим“ приложение без baseline и profiling — FFM не гарантира latency/throughput speedup.

**По-простата алтернатива** е първо Java SDK, а ако няма такъв, малък и ясно ограничен native adapter.

## 8. Babylon/ONNX — защо НЕ влиза в Java 25 executable lab

[Лекция 21:00–36:40](https://youtu.be/kuUeHD_k5fs?t=1260) разглежда `@Reflect`, `FuncOp`, traversal на операции/blocks и трансформиране на Java изчисления в ONNX graph model. Това е **друг подход**, различен от FFM downcall. Разширили сме вече съществуващия [Java Next Tech Radar](../../JAVA-NEXT-TECH-RADAR.md) с архитектурния case study, а не дублираме курс по Babylon.

Няма обещано или показано „Java → GPU автоматично“ за тази демонстрация. ONNX използва Protobuf за **сериализация на graph/model**, а in-memory tensors/runtime buffers не са задължително Protobuf между операторите.

## 9. Hands-on и Senior-level checkpoint

**Упражнение 1:** при `"A😊"` обясни защо Java връща 3, а native `strlen` 5 и как се кодира C terminator.  
**Упражнение 2:** промени `NativeIntBuffer` така, че да чете след `close()`; напиши JUnit, който очаква `IllegalStateException`.  
**Упражнение 3:** какво трябва да направиш, ако получиш native pointer, чиято памет принадлежи на C library, а не на Java arena? Защо не можеш просто да го освободиш с произволен allocator?  
**Упражнение 4:** генерирай bindings от [native_math.h](jextract/native_math.h) с jextract и пусни [smoke example](jextract/GeneratedBindingSmoke.java). Какво се променя при нов C header?  
**Упражнение 5:** сравни готов ONNX Java SDK с директен FFM adapter за inference в Spring Boot; разгледай ABI, shipping binaries, model versioning, threads, cleanup и observability.

### Code review checklist

- [ ] Има конкретен interop problem и не е наличен достатъчен Java SDK?
- [ ] ABI проверен: C function signature, `size_t`, pointers, alignment, platform?
- [ ] Native-access scope и dependency trust ясно дефинирани?
- [ ] Memory ownership, allocation/free pair и close order documented?
- [ ] Negative JUnit tests за bounds/lifetime/thread issues?
- [ ] Cross-platform constraints изрични и CI не изисква външен LLM?
- [ ] Модели/ONNX opsets, формат и runtime version compatibility проверени?
- [ ] Не твърдим zero-copy, crash safety или speedup без доказателства?

**Изход:** вече знаем как да прекосим Java/native boundary с FFM, какво точно пази MemorySegment/Arena, кога генераторът jextract е полезен и защо Babylon/ONNX code transformation е друга, по-незряла архитектурна тема.

## Оригинални източници

- [Ana-Maria Mihalceanu — Java and the Next Generation of Programming Models](https://www.youtube.com/watch?v=kuUeHD_k5fs) — FFM/jextract/ONNX GenAI и Babylon Code Reflection (с транскрипт, предоставен за анализа).
- [JEP 454 — Foreign Function & Memory API (final in Java 22)](https://openjdk.org/jeps/454).
- [Java 25 `Linker` API](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/foreign/Linker.html).
- [Java 25 Memory Segments and Arenas — Oracle](https://docs.oracle.com/en/java/javase/25/core/memory-segments-and-arenas.html).
- [OpenJDK jextract official guide + samples](https://github.com/openjdk/jextract/blob/master/doc/GUIDE.md).
- [ONNX Runtime Java SDK](https://onnxruntime.ai/docs/get-started/with-java).
- [ONNX IR Specification — graph/protobuf vs internal runtime representation](https://onnx.ai/onnx/repo-docs/IR.html).
- [Project Babylon](https://openjdk.org/projects/babylon/) и [OpenJDK Babylon examples](https://github.com/openjdk/babylon).
- [Java Next Tech Radar](../../JAVA-NEXT-TECH-RADAR.md) — P3 case study за code reflection.
