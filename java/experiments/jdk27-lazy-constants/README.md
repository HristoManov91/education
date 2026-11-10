# P2 · Java 27 Preview — Lazy Constants / JEP 531

> Devoxx 2026 [1:14:00–1:17:45](https://youtu.be/QBQ11aL2aZc?t=4440), Piotr Przybył & Marit van Dijk. **Отделна Java 27 preview лаборатория**, не част от Java 25 Maven reactor. Java 25 използва друг по-ранен preview API; не копирай code от една версия в друга.

## 1. Проблемът: голям final lookup, който може да не потрябва

Сървис има тежко за построяване lookup table / formatter / codec. Eager `static final` инициализация прави startup по-бавен дори когато дадена функция не се използва. Ръчно `double-checked locking` е сложно, а lazy Spring bean и holder idiom имат различна семантика.

В Java 27 `java.lang.LazyConstant<T>` (`JEP 531`, **third preview**) предлага `LazyConstant.of(Supplier)` + `.get()`. При първи get само един конкурентен thread пресмята стойността; останалите чакат и получават същата безопасно публикувана reference. След успешно изчисляване тя **не се преизчислява**. Holder-ът трябва да е стабилно достижим, най-често `static final`/`final`; самата стойност `T` може да е mutable — гаранцията е за holder-а, не deep immutability.

## 2. Runnable код и self-check

- [LazyConstantProbe.java](LazyConstantProbe.java) — self-verifying Java source: counters, lazy start, concurrency, sticky initialization failure; не се използва JUnit от Java25 reactor.
- [Java 27 CI workflow](../../../.github/workflows/java27-lazy-constants.yml) компилира и изпълнява под точната JDK 27 preview версия.

```bash
java -version    # must say 27
mkdir -p /tmp/lazy27
javac --enable-preview --release 27 -d /tmp/lazy27 java/experiments/jdk27-lazy-constants/LazyConstantProbe.java
java --enable-preview -cp /tmp/lazy27 LazyConstantProbe
```

### Negative scenario: неуспешна първа инициализация

В текущата Java 27 preview спецификация **първото изключение променя lazy constant в error state**: `.get()` хвърля `NoSuchElementException`, първия път с original cause, а следващите извиквания **не retry-ват Supplier**. `null` и recursive self-get също са отказ. Това е съществено различно от по-ранни preview варианти — използвай версия-специфичната документация. Тестът е успешен само когато supplier се извика точно веднъж дори при failure.

## 3. Сравнение и decision contract

| Вариант | Кога | Кога НЕ / цена |
| --- | --- | --- |
| `static final` eager | Евтина и винаги нужна стойност | Излишен startup/heap при rarely used данни |
| **Initialization-on-demand holder** (stable Java) | Статичен singleton без preview, lazy при first field access | Не е per-instance; classloading семантика |
| **Spring `@Lazy` bean** | Lifecycle на Spring dependency, DI/init cost | Proxy/bean lifecycle; не е идентично с Java holder семантика |
| **`LazyConstant<T>`** (Java27 preview) | Контролиран JDK27 експеримент, immutable reference needed on demand | Preview rollout/compatibility, sticky errors, no cancellation/timeout, retained content |

**Проблем:** скъпа неизползвана eager initialization. **Печалба:** delayed compute и safe publication. **Загуби:** първият request поема latency, failure state е sticky, няма automatic timeout/cancellation, стойността се задържа силно в паметта. **По-прост избор:** static holder или Spring lazy bean в Java25 production. **Доказателство:** [self-check probe](LazyConstantProbe.java) + CI Java27; за performance е нужен отделен startup/p99 benchmark.

## 4. Изходни въпроси

- Защо `LazyConstant` не е retry policy и не трябва да държи `closeable` без ownership план?
- Какво става при null Supplier result, failure и recursive initialization?
- Кога `@Lazy` bean е по-уместен от езиков holder?

## Оригинални източници

- [Devoxx, 1:14:00](https://youtu.be/QBQ11aL2aZc?t=4440).
- [JEP 531 — Lazy Constants (Third Preview)](https://openjdk.org/jeps/531).
- [Java 27 LazyConstant API](https://docs.oracle.com/en/java/javase/27/docs/api/java.base/java/lang/LazyConstant.html).
- [Java 27 Lazy Constants guide](https://docs.oracle.com/en/java/javase/27/core/lazy-constants.html).
