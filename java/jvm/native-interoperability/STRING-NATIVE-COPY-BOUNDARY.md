# P2 — `String` → native UTF-8: copy/ownership вместо опасно zero-copy

> По [Ask the Architects, Devoxx 2026](https://www.youtube.com/watch?v=ewCPBgOrLAE), приблизително 28:40–36:30 от предоставения SRT. Архитектите обсъждат защо `String` не трябва да разкрива freely mutable internal storage на native code: immutable Java semantics, Compact Strings encoding и moving GC. **Следващата Java25 лаборатория е наш учебен пример**, не демонстрирана функция/поддържан zero-copy String API.

## Реален казус и correctness граница

Spring service изпраща текстови данни към native SDK. Някой предлага `"да дадем pointer към backing bytes на String и да избегнем копието"`. Това изглежда по-бързо, но може да наруши immutable contract, lifetime и thread ownership. В Java Unicode `String.length()` е брой UTF-16 code units, а C `strlen` е брой bytes преди NUL. `"A😊"`: 3 units срещу 5 UTF-8 bytes.

**Good:** създай собствен native UTF-8 C-string с `Arena`, предай контролирания `MemorySegment`, затвори arena след call. **Bad:** пази pointer към memory, която JVM може да премести, или допускай native code да модифицира споделено `String` storage.

| Доказателство | Source | Test |
| --- | --- | --- |
| UTF-16 units != UTF-8 bytes, NUL-terminator | [StringCopyBoundaryLab](src/main/java/bg/hristomanov/education/ffi/StringCopyBoundaryLab.java) | [StringCopyBoundaryLabTest](src/test/java/bg/hristomanov/education/ffi/StringCopyBoundaryLabTest.java) |
| Native copy е отделна mutable memory и не променя `String` | `Arena.allocateFrom(String)` | същите JUnit тестове |
| `null`/embedded NUL отказ + after-close safety | `StringCopyBoundaryLab` | JUnit negative tests |
| Реален C ABI downcall + cache-нат handle | [NativeCStringLength](src/main/java/bg/hristomanov/education/ffi/NativeCStringLength.java) | [NativeCStringLengthTest](src/test/java/bg/hristomanov/education/ffi/NativeCStringLengthTest.java) |
| Wrong-thread + OOB constraints | [NativeMemorySafetyTest](src/test/java/bg/hristomanov/education/ffi/NativeMemorySafetyTest.java) | Java25 JUnit |

```bash
mvn -pl java/jvm/native-interoperability -Dtest=StringCopyBoundaryLabTest test
```

**Внимание:** този test не доказва броя физически allocation/copy операции вътре в `Arena.allocateFrom`, JIT или `String` impl. Документира observable contract — native memory има отделен lifetime, native bytes са mutable, Java String остава immutable. Стандартният код не получава API, който връща pointer към `String.value`. При много чести извиквания измервай JMH+JFR native/RSS, не само `System.nanoTime`.

## Decision contract

- **Кога да:** трябва реален native C string, UTF-8/ABI са изричен contract; собствеността върху native memory е ясна.
- **Кога НЕ:** обикновен Java HTTP header, JSON или DB операция с готов висококачествен API; няма доказан copy bottleneck.
- **Печалба:** correctness, lifetime control, стабилно поведение независимо от вътрешния `String` layout.
- **Цена:** encoding/copy allocation, native memory и scope management; не е „zero-copy“.
- **Алтернатива:** JDK `HttpClient`, UTF-8 `ByteBuffer`, готов SDK или `jextract` за голям native API.

**Изход:** вместо да търсим незащитен pointer, избираме безопасна ownership граница, потвърдена с 4 нови JUnit теста и съществуващите FFM integration tests.

## Оригинални източници

- [Ask the Architects](https://www.youtube.com/watch?v=ewCPBgOrLAE), supplied SRT.
- [Java 25 FFM Arena](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/foreign/Arena.html).
- [Java 25 MemorySegment](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/foreign/MemorySegment.html).
- [Java 25 String](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/String.html).
