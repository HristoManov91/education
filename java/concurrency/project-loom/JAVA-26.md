# Java 26 — как се променя Structured Concurrency примерът

Основната лаборатория в тази директория умишлено се компилира с **Java 25 LTS**. Този файл показва как същите идеи изглеждат в **Java 26**, без да принуждаваме целия `education` project да премине на JDK 26.

Причината да пазим отделна бележка е, че **Structured Concurrency все още е preview API** и между preview версиите има source-level промени. Това е нормално: preview API може да бъде променяно преди да стане финално.

> В учебния проект не използваме `var` в нашите кодови примери. Изписваме explicit типовете, за да се вижда директно какво връщат `StructuredTaskScope.open(...)`, `fork(...)`, `join()` и останалите нови API-та.

## Най-важната разлика за нашия пример

В Java 25 използваме:

```java
StructuredTaskScope.Joiner.<CreditScore>anySuccessfulResultOrThrow()
```

В Java 26 методът е преименуван на:

```java
StructuredTaskScope.Joiner.<CreditScore>anySuccessfulOrThrow()
```

Тоест Java 26 еквивалентът на нашия `CreditScoreClient#getFirstSuccessfulScore(...)` е:

```java
public CreditScore getFirstSuccessfulScore(UUID customerId) {
    try (StructuredTaskScope<CreditScore, CreditScore> scope = StructuredTaskScope.open(
            StructuredTaskScope.Joiner.<CreditScore>anySuccessfulOrThrow())) {

        scope.fork(() -> getScore(customerId, "provider-a"));
        scope.fork(() -> getScore(customerId, "provider-b"));

        return scope.join();
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("Credit score lookup was interrupted", e);
    }
}
```

Тук explicit type-ът `StructuredTaskScope<CreditScore, CreditScore>` показва две различни неща:

- първият `CreditScore` е допустимият result type на fork-натите subtasks;
- вторият `CreditScore` е резултатът, който `join()` връща при тази Joiner policy.

Бизнес поведението е същото: интересува ни **първата успешно приключила subtask**; ако всички fail-нат, `join()` приключва с failure.

За всички основни Java 25 Joiner policy-та виж [`JOINER-POLICIES.md`](./JOINER-POLICIES.md).

## Java 25 → Java 26: важни API промени

| Тема | Java 25 | Java 26 | Практическо значение |
| --- | --- | --- | --- |
| First successful result | `anySuccessfulResultOrThrow()` | `anySuccessfulOrThrow()` | Нашият код изисква rename при migration към 26 |
| `allSuccessfulOrThrow()` result | `Stream<Subtask<T>>` | `List<T>` | На Java 26 получаваме директно резултатите, вместо да map-ваме `Subtask::get` |
| Timeout policy | timeout се обработва от scope/join механизма | `Joiner` има `onTimeout()` hook | custom Joiner може да участва в timeout outcome-а |
| Scope configuration | preview configuration API | configuration API е доизпипан; 2-arg `open` използва configuration operator | по-ясно конфигуриране на timeout/thread factory/name |

## Пример: всички успешни резултати

### Java 25

```java
try (StructuredTaskScope<String, Stream<StructuredTaskScope.Subtask<String>>> scope =
        StructuredTaskScope.open(StructuredTaskScope.Joiner.<String>allSuccessfulOrThrow())) {

    scope.fork(() -> "A");
    scope.fork(() -> "B");

    List<String> values = scope.join()
            .map(StructuredTaskScope.Subtask::get)
            .toList();
}
```

`join()` връща `Stream<Subtask<String>>`, затова после четем стойностите чрез `Subtask#get()`.

### Java 26

```java
try (StructuredTaskScope<String, List<String>> scope =
        StructuredTaskScope.open(StructuredTaskScope.Joiner.<String>allSuccessfulOrThrow())) {

    scope.fork(() -> "A");
    scope.fork(() -> "B");

    List<String> values = scope.join();
}
```

На Java 26 `allSuccessfulOrThrow()` вече връща директно `List<T>`, което прави common case-а по-кратък. Explicit scope type-ът показва тази API промяна много по-ясно от `var`.

## Пример: timeout конфигурация в Java 26

Java 26 позволява configuration operator при `open(...)`, например:

```java
StructuredTaskScope.Joiner<String, List<String>> joiner =
        StructuredTaskScope.Joiner.<String>allSuccessfulOrThrow();

try (StructuredTaskScope<String, List<String>> scope = StructuredTaskScope.open(
        joiner,
        configuration -> configuration.withTimeout(Duration.ofSeconds(2)))) {

    scope.fork(this::callServiceA);
    scope.fork(this::callServiceB);

    List<String> result = scope.join();
}
```

Това е полезно за request-oriented backend код, защото timeout-ът принадлежи на **цялата structured операция**, вместо всяка child задача да има напълно независим lifecycle.

## Трябва ли основният проект да мине на Java 26?

На този етап — **не е необходимо**.

За `education` основата Java 25 LTS е по-подходяща:

- Java 25 е LTS версията, която сме избрали за проекта;
- Virtual Threads и Scoped Values, които изучаваме, са стабилни;
- Structured Concurrency така или иначе остава preview и в Java 26;
- можем да следим развитието на preview API чрез такива version notes, без да сменяме baseline-а на целия repository.

Когато Structured Concurrency стане final API или решим да сменим основната LTS версия, тогава има смисъл production-like кодът да бъде мигриран.

## Връзка с основния код

- Java 25 реализацията е в [`BankClients.java`](./bank-api/src/main/java/bg/hristomanov/education/loom/bankapi/client/BankClients.java), `CreditScoreClient#getFirstSuccessfulScore(...)`.
- Основните Java 25 Joiner стратегии са в [`JOINER-POLICIES.md`](./JOINER-POLICIES.md).
- Цялото обяснение на use case-а е в [`README.md`](./README.md), секциите за `StructuredTaskScope` и `Joiner`.

## Официални източници

- Java 25 `StructuredTaskScope.Joiner`: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/concurrent/StructuredTaskScope.Joiner.html
- Java 26 `StructuredTaskScope`: https://docs.oracle.com/en/java/javase/26/docs/api/java.base/java/util/concurrent/StructuredTaskScope.html
- Java 26 `StructuredTaskScope.Joiner`: https://docs.oracle.com/en/java/javase/26/docs/api/java.base/java/util/concurrent/StructuredTaskScope.Joiner.html
- Java 26 Structured Concurrency guide: https://docs.oracle.com/en/java/javase/26/core/structured-concurrency.html
- Оригиналният demo project, който вече използва JDK 26: https://github.com/balkrishnarawool/SpringBootLoom

---

Този файл е **version note**, а не отделен build target. Основният код остава Java 25 и се проверява от CI с JDK 25.
