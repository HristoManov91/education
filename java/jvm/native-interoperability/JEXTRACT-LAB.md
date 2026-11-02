# Optional hands-on — jextract: from local C header to working Java bindings

> **Самостоятелен експеримент** към [P2 FFM урок](README.md). Main Maven reactor/CI не инсталира C compiler или jextract. **Linux/macOS + Java 25 + съвместим jextract + C compiler**. Windows: WSL 2 с Linux JDK/compilers, или еквивалентен Windows native build с адаптирано DLL loading.
>
> Източник: [Ana-Maria Mihalceanu, Devoxx 2026, ~09:10–17:50](https://youtu.be/kuUeHD_k5fs?t=550) и [официален OpenJDK jextract GUIDE](https://github.com/openjdk/jextract/blob/master/doc/GUIDE.md).

## 1. Какъв проблем решава?

Имаме native C library с десетки/стотици symbols. Ръчното поддържане на `Linker.downcallHandle` за всяка C функция е повтарящо се, рисково и трудно за обновяване при нова версия на header. `jextract` чете C headers и генерира Java bindings. Това **не** прави непроверен native code safe, не превежда произволен C++ ABI и не изтегля нужните shared libraries.

Демонстрацията на Ana-Maria използва headers на **ONNX Runtime GenAI**. Нашият пример е минимална **собствена C библиотека**, за да няма изискване за LLM, модели, ONNX native binaries или downloads в CI.

## 2. README → native source → generated code → proof

| Част | Файл |
| --- | --- |
| Публичен C ABI contract | [native_math.h](jextract/native_math.h) |
| Native implementation | [native_math.c](jextract/native_math.c) |
| Готово извикване на **генерирани**, а не ръчно написани bindings | [GeneratedBindingSmoke.java](jextract/GeneratedBindingSmoke.java) |
| Повтаряеми Linux/macOS команди | [run-linux-macos.sh](jextract/run-linux-macos.sh) |
| Ръчен FFM вариант (сравнение) | [NativeCStringLength.java](src/main/java/bg/hristomanov/education/ffi/NativeCStringLength.java) |

Native API:

```c
int edu_square(int value);
int edu_add(int left, int right);
```

Използваме само **малки контролирани стойности**: C signed overflow е undefined behavior; не го използвай за Java correctness testing. Native smoke очаква `7*7=49` и `12+30=42`.

## 3. Как се изпълнява

Инсталирай JDK 25 и jextract binary, съвместим с него, от [официалните jextract builds](https://jdk.java.net/jextract/) или следвай [OpenJDK jextract README](https://github.com/openjdk/jextract). jextract **не е част от стандартния JDK**. Провери:

```bash
java -version
javac -version
jextract --version
cc --version
```

От repository root:

```bash
bash java/jvm/native-interoperability/jextract/run-linux-macos.sh
```

Скриптът:
1. проверява `cc`, `jextract`, `java`, `javac` и JDK 25;
2. компилира **локалния** [native_math.c](jextract/native_math.c) в `target/native/libeducation_math.so` (Linux) или `.dylib` (macOS);
3. извиква `jextract --target-package … --output target/jextract-generated --library :ABS_PATH native_math.h`;
4. компилира всички генерирани Java класове + [GeneratedBindingSmoke](jextract/GeneratedBindingSmoke.java) с `--release 25`;
5. изпълнява smoke тест с `--enable-native-access=ALL-UNNAMED`, който извиква и двете C функции.

Очаквано при успешен native run:

```text
PASS: Java reached two functions in the local C library via jextract
```

Скриптът работи с **генерирани** source/binaries само в `target/`. Generated code не се commit-ва и не влиза в основния `mvn verify`. Няма гаранция, че нова jextract EA версия няма да промени опции или generated API; ако това стане, сверявай [официалния GUIDE](https://github.com/openjdk/jextract/blob/master/doc/GUIDE.md) и pin-ни подходящ tool build за своето JDK.

### Ръчно сравнение с Java 25 FFM

```bash
mvn -pl java/jvm/native-interoperability test
```

Този **стандартен** lab използва native libc `strlen` без jextract и е напълно отделен от `edu_add/edu_square`. Това са две **различни** демонстрации, не „jextract генерира NativeCStringLength“.

## 4. BAD / GOOD и отрицателен сценарий

BAD: ръчно генериран „почти същият“ Java method signature, с който случайно подаваш `long` за C `int` или освобождаваш returned native pointer с грешна функция. JVM не може да гарантира безопасност при такъв ABI mismatch.

GOOD: използвай **header-а от същата версия** като native binary, регенерирай bindings при смяна на C API, тествай library loading и реалните native results. При missing `jextract`/`cc`, script-ът **отказва с ясен error**, вместо да използва остарели generated sources. При missing library generated bindings не могат да извикат native symbol — това е правилен failure, не ситуация, която се прикрива чрез fallback на Java hardcoded result.

**Реален риск:** `--library :ABS_PATH` иска native binary да съществува на конкретната машина. За production CI, Docker или дистрибутиран JAR пътят и loader policy трябва да бъдат конфигурирани отделно за OS/architecture, вместо да се hardcode-ват от dev лаптоп.

## 5. Decision contract

| Подход | Използвай | Не използвай | Печалба ↔ разход |
| --- | --- | --- | --- |
| Ръчен FFM | 1–3 native функции, разбираема ABI | Много structs/callbacks/headers | Малък код ↔ ръчна поддръжка на сигнатури |
| jextract | Голям поддържан C API с headers | Само C++ ABI или непроверени native builds | Генериране ↔ инструмент/clang/toolchain/version lock |
| Java SDK | Поддържан готов API за целта | Не expose-ва нужната native функционалност | Поддръжка ↔ зависимости и release cadence |
| Отделен native процес | Isolation на crash, resource limits, untrusted input | IPC надвишава допустимия overhead | Process boundary ↔ latency/ops complexity |

**Безопасност:** винаги използвай trusted C source/native library, затваряй ресурси в правилния ред и не тествай грешна ABI signature „за да видим какво ще стане“ — тя може да срине процеса.

## 6. Въпроси за самопроверка

1. Защо един и същ C header и native library трябва да се version-ват заедно?
2. Какво се променя при нова функция в header-а, ако използваме jextract?
3. Коя част изисква `--enable-native-access` и защо?
4. Защо `jextract` не превръща ONNX Runtime GenAI в pure Java library?
5. Как би направил CI coverage за Linux/macOS/Windows, без да го налагаш на всичките 58 образователни модула?

## Оригинални източници

- [Devoxx — Java and the Next Generation of Programming Models](https://www.youtube.com/watch?v=kuUeHD_k5fs).
- [OpenJDK jextract GUIDE](https://github.com/openjdk/jextract/blob/master/doc/GUIDE.md).
- [jextract optional builds](https://jdk.java.net/jextract/).
- [JEP 454 — FFM](https://openjdk.org/jeps/454).
