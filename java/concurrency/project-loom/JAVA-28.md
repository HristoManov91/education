# Structured Concurrency — Java 25 → 26 → 27 → 28

> **Статус към 21.10.2026:** JEP 543 е targeted / интегриран в разработката на JDK 28 и предлага Structured Concurrency като **permanent API без допълнителни промени спрямо JDK 27**. Java 28 **не е GA**; финалната версия се очаква през 2027 г. Това е справка, не Java 28 runnable модул.
>
> Основният `education` код и CI остават на **Java 25 LTS** с `--enable-preview`. Не мигрираме текущите лаборатории на незавършена версия само заради новината.

## Защо е полезна тази бележка

Истинският migration риск е при source compatibility между preview версиите. Докато Virtual Threads (JDK 21) и Scoped Values (JDK 25) вече са permanent, API-то `StructuredTaskScope` продължи да се променя до JDK 27.

Не смесвай синтаксис от различни JDK версии в един executable пример.

| Област | Java 25 (нашият runnable проект) | Java 26 | Java 27 / JEP 543 за JDK 28 |
| --- | --- | --- | --- |
| Статус | Fifth Preview — JEP 505 | Sixth Preview — JEP 525 | Seventh Preview — JEP 533; JEP 543 прави API-то permanent в 28 |
| `StructuredTaskScope` type | `<T, R>` | `<T, R>` | `<T, R, R_X extends Throwable>` |
| First-successful | `anySuccessfulResultOrThrow()` | `anySuccessfulOrThrow()` | `anySuccessfulOrThrow()` |
| `allSuccessfulOrThrow()` резултат | `Stream<Subtask<T>>` | `List<T>` | `List<T>` |
| `allUntil(...)` резултат | `Stream<Subtask<T>>` | `List<Subtask<T>>` | `List<Subtask<T>>` |
| Обработка на failure | `StructuredTaskScope.FailedException` | `StructuredTaskScope.FailedException` | `ExecutionException` по подразбиране; може custom exception supplier |
| Timeout | `StructuredTaskScope.TimeoutException` се хвърля от `join()` | `Joiner` получава нов timeout hook | `Joiner.timeout()` определя outcome; стандартният failure е `ExecutionException` с `CancelledByTimeoutException` като cause |
| `allUntil` при timeout | `join()` хвърля `TimeoutException`; **не връща partial result** | Зависи от политиката на Joiner | `allUntil` връща subtasks, включително `UNAVAILABLE` за незавършилите |
| `--enable-preview` | Да | Да | Да в 27; **не** за final API в 28 |

Таблицата описва разликите, важни за нашите примери, не е пълен release changelog. API-то за JDK 28 е от предложението JEP 543 / early draft документацията, а не от GA.

## Пример: Java 25, всички резултати са задължителни

Работещата лаборатория използва:

```java
try (StructuredTaskScope<Object, Void> scope = StructuredTaskScope.open()) {
    StructuredTaskScope.Subtask<String> left =
            scope.fork(() -> fetchLeft());
    StructuredTaskScope.Subtask<Integer> right =
            scope.fork(() -> fetchRight());

    scope.join(); // Java 25: FailedException при failure
    use(left.get(), right.get());
}
```

## Еквивалентен shape на JDK 28 (справочно, НЕ е компилиран тук)

```java
try (StructuredTaskScope<Object, Void, ExecutionException> scope =
             StructuredTaskScope.open()) {
    StructuredTaskScope.Subtask<String> left =
            scope.fork(() -> fetchLeft());
    StructuredTaskScope.Subtask<Integer> right =
            scope.fork(() -> fetchRight());

    scope.join(); // JDK 28: ExecutionException с original cause
    use(left.get(), right.get());
}
```

И при двете версии scope-ът е owner на child задачите. `close()` изчаква те да приключат; timeout или cancel **не** убиват насила блокиращ код, който игнорира interruption.

## Как partial results се променят

В [нашата Java 25 лаборатория](./VIRTUAL-THREADS-LABS.md) използваме `allUntil(predicate)` и **отделна deadline subtask**, тъй като scope `withTimeout` на JDK 25 би довел до `TimeoutException` и не би върнал partial stream.

В JDK 27 / планираното финално JDK 28 API `Joiner.allUntil(_ -> false)` може да бъде комбиниран директно с `Configuration.withTimeout(budget)`; `join()` връща `List<Subtask<T>>` и при timeout. Обработваме само subtasks със `SUCCESS`, а `UNAVAILABLE` не бива да се чете чрез `get()`.

Това опростява кода, **но не отменя** необходимостта да следим cancellation propagation и eventual cleanup. В частност `close()` може да отнеме повече от budget-а при некооперативни блокиращи операции.

## Кога да мигрираме

1. Изчакваме реална необходимост от JDK 28 / официалното му издание.
2. Мигрираме едновременно preview signatures, exception handling и Joiner result types; не правим механична замяна само на един метод.
3. Проверяваме с тестове failure, interruption, timeout и parent-child observability.
4. Едва след успешно `mvn verify` на желаната JDK версия премахваме `--enable-preview` за вече permanent features, без да го премахваме за други ползвани preview API-та.

## Източници

- **JEP 543 — Structured Concurrency:** https://openjdk.org/jeps/543
- **JEP 533 — Seventh Preview (JDK 27):** https://openjdk.org/jeps/533
- **JEP 525 — Sixth Preview (JDK 26):** https://openjdk.org/jeps/525
- **JEP 505 — Fifth Preview (JDK 25):** https://openjdk.org/jeps/505
- **JDK 27 API, StructuredTaskScope:** https://docs.oracle.com/en/java/javase/27/docs/api/java.base/java/util/concurrent/StructuredTaskScope.html
- **JDK 27 API, Joiner (вкл. timeout и allUntil):** https://docs.oracle.com/en/java/javase/27/docs/api/java.base/java/util/concurrent/StructuredTaskScope.Joiner.html
- **JDK 28 proposed API draft (Alan Bateman):** https://cr.openjdk.org/~alanb/sc-jdk28/api/java.base/java/util/concurrent/StructuredTaskScope.html
- **Devoxx — Structured Concurrency Looms, Bateman/Klang:** https://www.youtube.com/watch?v=aow56RgptLA
