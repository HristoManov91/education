# Java 25 `StructuredTaskScope.Joiner` policies

Този файл е локална учебна справка за policy-тата (правилата за завършване) на `StructuredTaskScope` в Java 25.

Идеята е при четене на кода да не виждаме само **коя** policy е избрана, а да разбираме:

- какви основни алтернативи има;
- кога `join()` приключва;
- какво връща `join()`;
- как scope-ът реагира при failure (грешка в child задача);
- кога останалата работа може да бъде cancel-ната;
- защо конкретният ни пример използва точно определена policy.

> `StructuredTaskScope` и `Joiner` са preview API в Java 25. За разликите с Java 26 виж [`JAVA-26.md`](./JAVA-26.md).

## Основният mental model

`fork()` казва **каква child работа стартираме**, а `Joiner` определя **кога вече имаме достатъчно резултат, за да считаме structured операцията за приключила**.

```text
child A ───────┐
child B ───────┼──> Joiner policy ──> резултатът от join()
child C ───────┘
```

Различните scenarios имат различно значение на „готови сме“:

```text
Всички трябва да успеят
        ≠
Първият успешен е достатъчен
        ≠
Чакаме всички независимо от грешките
        ≠
Спираме, когато custom условие стане true
```

---

## Сравнение на основните Java 25 policy-та

| Policy | Кога `join()` може да приключи | Поведение при child failure | Какво връща `join()` | Подходящо за |
| --- | --- | --- | --- | --- |
| `awaitAllSuccessfulOrThrow()` | когато всички успеят или някоя fail-не | cancel-ва ненужната останала работа; `join()` хвърля `FailedException` | `Void` / `null` | всички резултати са задължителни и може да са от различни типове |
| `allSuccessfulOrThrow()` | когато всички успеят или някоя fail-не | cancel-ва ненужната останала работа; `join()` хвърля `FailedException` | `Stream<Subtask<T>>` | всички резултати са задължителни и са от един и същ тип |
| `anySuccessfulResultOrThrow()` | при първия успешен резултат или когато всички fail-нат | отделен failure не е фатален; хвърля само ако всички fail-нат | `T` | race/fallback между provider-и, mirror-и или други алтернативни източници |
| `awaitAll()` | когато всички subtasks приключат | не cancel-ва scope-а само заради failure; `join()` не хвърля заради child failure | `Void` / `null` | независими side effects или случаи, в които после сами разглеждаме individual outcomes |
| `allUntil(predicate)` | когато всички приключат или predicate-ът върне `true` | поведението зависи от predicate-а и състоянието на subtasks | `Stream<Subtask<T>>` | custom early-stop условие, което стандартните policy-та не покриват |

Официална Java 25 документация:

- `StructuredTaskScope`: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/concurrent/StructuredTaskScope.html
- `StructuredTaskScope.Joiner`: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/concurrent/StructuredTaskScope.Joiner.html
- Structured Concurrency guide: https://docs.oracle.com/en/java/javase/25/core/structured-concurrency.html

---

## 1. `awaitAllSuccessfulOrThrow()` — всички са задължителни

Тази policy казва:

> „Имам няколко child операции и крайният резултат е валиден само ако **всички** са успешни.“

Ако една subtask fail-не, scope-ът може да cancel-не останалата ненужна работа и `join()` хвърля `StructuredTaskScope.FailedException`.

`join()` не връща самите резултати. След успешния `join()` четем всеки `Subtask#get()` отделно.

Това е особено подходящо, когато child задачите връщат **различни типове**:

```java
try (StructuredTaskScope<Object, Void> scope = StructuredTaskScope.open()) {
    StructuredTaskScope.Subtask<List<Account>> accountsTask =
            scope.fork(() -> accountClient.getAccounts(customer.id()));

    StructuredTaskScope.Subtask<List<Loan>> loansTask =
            scope.fork(() -> loanClient.getLoans(customer.id()));

    StructuredTaskScope.Subtask<CreditScore> creditScoreTask =
            scope.fork(() -> creditScoreClient.getFirstSuccessfulScore(customer.id()));

    scope.join();

    return new CustomerInfo(
            accountsTask.get(),
            loansTask.get(),
            creditScoreTask.get());
}
```

`StructuredTaskScope.open()` без explicit Joiner е еквивалентно на `Joiner.awaitAllSuccessfulOrThrow()`.

### Защо точно това използваме в `StructuredCustomerInfoLoader`

Нашият `CustomerInfo` изисква:

```text
accounts      ── задължителни ─┐
loans         ── задължителни ─┼──> CustomerInfo
credit score  ── задължителен ─┘
```

Един успешен резултат не компенсира другите два. Затова policy от типа „първият успешен печели“ би била грешна за този outer scope.

---

## 2. `allSuccessfulOrThrow()` — всички са задължителни и са от един тип

Semantics са близки до `awaitAllSuccessfulOrThrow()`:

- всички subtasks трябва да успеят;
- при първия relevant failure scope-ът може да cancel-не останалата ненужна работа;
- `join()` хвърля при failure.

Разликата е в резултата: в Java 25 `join()` връща `Stream<Subtask<T>>`.

```java
try (StructuredTaskScope<String, Stream<StructuredTaskScope.Subtask<String>>> scope =
        StructuredTaskScope.open(StructuredTaskScope.Joiner.<String>allSuccessfulOrThrow())) {

    scope.fork(this::loadNameA);
    scope.fork(this::loadNameB);

    List<String> names = scope.join()
            .map(StructuredTaskScope.Subtask::get)
            .toList();
}
```

Това е удобно, когато имаме серия еднакви операции, например няколко `Callable<String>` или няколко заявки, които всички връщат един и същ DTO type.

> В Java 26 тази policy вече връща директно `List<T>`. Това е една от preview API промените, описани в [`JAVA-26.md`](./JAVA-26.md).

---

## 3. `anySuccessfulResultOrThrow()` — първият успешен печели

Тази policy казва:

> „Не ми трябват всички резултати. Достатъчен ми е **който и да е първи успешен** резултат.“

```java
try (StructuredTaskScope<CreditScore, CreditScore> scope = StructuredTaskScope.open(
        StructuredTaskScope.Joiner.<CreditScore>anySuccessfulResultOrThrow())) {

    scope.fork(() -> getScore(customerId, "provider-a"));
    scope.fork(() -> getScore(customerId, "provider-b"));

    return scope.join();
}
```

Ако `provider-a` fail-не след 100 ms, това още не е failure на целия scope. `provider-b` може да върне успешен резултат след 450 ms.

Ако `provider-b` успее, останалата работа вече не е нужна и scope-ът може да я cancel-не.

Само ако **всички** subtasks fail-нат, `join()` хвърля.

### Защо точно това използваме в `CreditScoreClient`

```text
provider A ─┐
            ├──> нужен е първият успешен CreditScore
provider B ─┘
```

Тук двата provider-а са алтернативни източници на един и същ тип резултат. Нямаме бизнес причина да чакаме и двата, след като вече имаме валиден score.

---

## 4. `awaitAll()` — чакаме всички, без fail-fast при child failure

Тази policy е различна, защото child failure сам по себе си **не cancel-ва scope-а** и `join()` не хвърля само защото някоя child задача е fail-нала.

```java
try (StructuredTaskScope<Object, Void> scope = StructuredTaskScope.open(
        StructuredTaskScope.Joiner.awaitAll())) {

    StructuredTaskScope.Subtask<Void> auditTask = scope.fork(() -> {
        writeAudit();
        return null;
    });

    StructuredTaskScope.Subtask<Void> metricsTask = scope.fork(() -> {
        publishMetrics();
        return null;
    });

    scope.join();

    // Тук можем сами да преценим как да обработим individual outcomes.
}
```

Подходящо е, когато:

- операциите са независими;
- искаме всяка да получи шанс да приключи;
- failure на една не прави другите безсмислени;
- или subtasks основно правят side effects, вместо да връщат комбиниран business result.

Това **не** е правилният избор за нашия `CustomerInfo`, защото там missing accounts/loans/score означава непълен business result.

---

## 5. `allUntil(predicate)` — custom early-stop условие

`allUntil(...)` приема predicate (условие), което се извиква при completion на subtask. Когато predicate-ът върне `true`, scope-ът може да бъде cancel-нат и да не чака останалите subtasks.

Mental model:

```text
result A ──┐
result B ──┼──> predicate(result/state) ── true? ──> stop/cancel rest
result C ──┘
```

Това е useful, когато бизнес/техническото условие е по-специфично от:

- „всички успешни“;
- „първият успешен“;
- „чакай всички“.

Примерна идея е search fan-out към няколко независими източника, където спираме, когато вече сме събрали достатъчно качествени резултати.

Важно: `Joiner` трябва да моделира **обща concurrency policy**, а не да поема произволна business logic.

---

## 6. Custom `Joiner`

Ако built-in вариантите не са достатъчни, можем да имплементираме:

```java
StructuredTaskScope.Joiner<T, R>
```

Основните hooks в Java 25 са:

```java
boolean onFork(Subtask<? extends T> subtask)
boolean onComplete(Subtask<? extends T> subtask)
R result() throws Throwable
```

`onFork(...)` и `onComplete(...)` могат да сигнализират, че scope-ът трябва да бъде cancel-нат. `result()` определя какъв краен резултат или exception получава `join()`.

Custom Joiner има смисъл само когато имаме reusable concurrency policy, която не може ясно да се моделира с built-in вариантите.

---

## Как избираме policy practically

Може да мислим с тези въпроси:

```text
Трябват ли ми ВСИЧКИ резултати?
│
├── Да
│   │
│   ├── Всички трябва ли да са успешни?
│   │   │
│   │   ├── Да
│   │   │   ├── различни типове → awaitAllSuccessfulOrThrow()
│   │   │   └── един и същ тип → allSuccessfulOrThrow()
│   │   │
│   │   └── Не / искам сам да анализирам outcomes → awaitAll()
│   │
│   └──
│
└── Не
    │
    ├── Първият успешен достатъчен ли е? → anySuccessfulResultOrThrow()
    │
    └── Имам друго early-stop условие → allUntil(predicate) или custom Joiner
```

За нашата лаборатория:

```text
StructuredCustomerInfoLoader
→ awaitAllSuccessfulOrThrow()
→ защото accounts + loans + credit score са всички задължителни

CreditScoreClient
→ anySuccessfulResultOrThrow()
→ защото който и да е първи успешен provider е достатъчен
```

---

## Какво да запомня

1. `StructuredTaskScope` определя lifecycle-а на child задачите; `Joiner` определя policy-то за завършване.
2. `open()` без Joiner използва `awaitAllSuccessfulOrThrow()` semantics.
3. `awaitAllSuccessfulOrThrow()` е много подходящо за различни result types, когато всички са задължителни.
4. `allSuccessfulOrThrow()` е удобно за еднакви result types, когато всички са задължителни.
5. `anySuccessfulResultOrThrow()` е race/fallback policy — първият успешен резултат е достатъчен.
6. `awaitAll()` не прави fail-fast при child failure.
7. `allUntil(predicate)` позволява custom short-circuit условие.
8. Custom `Joiner` е за reusable concurrency policy, не за скриване на business logic.
9. Изборът на Joiner трябва да следва business requirement-а.
10. При preview API винаги проверяваме version-specific официалната документация.
