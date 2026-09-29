# Iterator — обхождане без consumer-ът да знае структурата

## Реалният казус

Искаме:

```java
for (OrderSummary order : orders) {
    process(order);
}
```

Но отдолу orders идват от paginated DB/REST source:

```text
page 0
page 1
page 2
...
```

Без Iterator business code-ът започва да знае:

- page number;
- page size;
- hasMore;
- кога да fetch-не следваща страница;
- как да пази cursor.

## Mental model

> **Iterator отделя traversal state/algorithm-а от business consumer-а.**

В проекта:

- [`OrderPageSource.java`](../src/main/java/bg/hristomanov/education/patterns/behavioral/iterator/OrderPageSource.java);
- [`PagedOrderIterable.java`](../src/main/java/bg/hristomanov/education/patterns/behavioral/iterator/PagedOrderIterable.java);
- [`IteratorPatternTest.java`](../src/test/java/bg/hristomanov/education/patterns/behavioral/iterator/IteratorPatternTest.java).

Consumer-ът вижда `Iterable<OrderSummary>`, а iterator-ът lazy-load-ва pages.

## Защо това е полезно в backend

Iterator не е само `List.iterator()`.

Практични случаи:

- paginated external API;
- database cursor;
- file records;
- tree traversal;
- batched object storage listing;
- graph traversal.

## Iterator vs Stream

Java Stream е по-високо ниво за pipeline processing.

Iterator е по-ниският traversal contract и е полезен, когато сам контролираш:

- lazy page fetching;
- cursor state;
- external resource lifecycle;
- custom traversal order.

Не дръж DB cursor/resource без ясен close lifecycle само защото Iterator API изглежда удобно.

## Кога да го използвам

- traversal logic се повтаря;
- collection/source е сложен;
- consumer-ът не трябва да вижда вътрешната структура;
- искаме различни traversal strategies.

## Кога НЕ

Обикновен `List` + `for-each` вече има Iterator. Не създавай custom Iterator без custom traversal problem.

## Checklist

```text
[ ] Consumer-ът знае ли paging/cursor details?
[ ] Traversal code дублира ли се?
[ ] Iterator lazy ли е и това ясно ли е?
[ ] Какво става при I/O error по средата?
[ ] Има ли resource, който трябва да се close-не?
[ ] Може ли iteration да бъде повторена?
```

## Източници

- Refactoring.Guru — Iterator: https://refactoring.guru/design-patterns/iterator
- Java Iterable / Iterator API
