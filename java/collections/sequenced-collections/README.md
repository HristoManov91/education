# P2 · Sequenced Collections (Java 21+, runnable Java 25)

> Focused lecture 15–25 мин. Devoxx 2026, [1:58–2:05](https://youtu.be/zC2tbq7S5UE?t=7080).  
> **Feature:** [JEP 431, JDK 21](https://openjdk.org/jeps/431) — вече stable при Java 25.

## Production-like problem

Работим с ordered business events, по които ни трябва **първи**, **последен** и **обърнат изглед**. Стар код приема `List`, въпреки че алгоритъмът иска само encounter order:

```java
String last = list.get(list.size() - 1);
Collections.reverse(list); // MUTATES data and surprises caller
```

**Добре е да зависим от най-точния contract, а не от конкретната implementation.** `SequencedCollection` изразява order + first/last/reversed, без да изисква index-based access.

## 1. Новите contracts

- `SequencedCollection<E>` → `getFirst/getLast`, `addFirst/addLast`, `removeFirst/removeLast`, `reversed()`.
- `SequencedSet<E>` → уникалност + encounter order, напр. `LinkedHashSet`.
- `SequencedMap<K,V>` → first/last entry, putFirst/putLast и sequenced views, напр. `LinkedHashMap`.

**ВАЖНО:** interface method може да хвърля `UnsupportedOperationException`. Наличието му **не гарантира**, че конкретната структура поддържа mutator със съответната семантика. `TreeSet` е **sorted** — да добавиш произволен елемент "в началото" би нарушило comparator order; `addFirst`/`addLast` не се поддържат.

## 2. BAD → GOOD

### BAD — side effects върху original list

```java
Collections.reverse(original);
return original.get(0);
```

Ако `original` е споделен reference, caller вижда променен ред.

### GOOD A — reversed view, когато искаме live backing

```java
SequencedCollection<String> view = original.reversed();
view.removeFirst(); // премахва original LAST element
```

**Особено важно:** `reversed()` е **view**, а не snapshot. Промените през оригиналната структура са видими през reversed view и обратно — ако операцията е поддържана.

### GOOD B — defensive immutable snapshot

```java
List<String> newestFirst = List.copyOf(original.reversed());
```

Тук snapshot **не следва** бъдещите промени на оригиналния списък. Това е полезно при четене на stable state за business decisions.

## 3. Readme → код → тест

- [CollectionOrders.java](src/main/java/bg/hristomanov/education/sequenced/CollectionOrders.java) — generic SequencedCollection API, LinkedHashSet, LinkedHashMap, reversed view;
- [CollectionOrdersTest.java](src/test/java/bg/hristomanov/education/sequenced/CollectionOrdersTest.java) — reversed backing, snapshot isolation, linked access order, ordered map, unsupported operations върху TreeSet.

```bash
mvn -pl java/collections/sequenced-collections test
```

## 4. Нюанс за алгоритмите

[Data Structures & Algorithms — linear structures](../../algorithms/02-linear-data-structures/README.md) вече покрива List/Deque и asymptotic costs. Sequenced API **не унифицира алгоритмичната сложност** на всички implementations.

Не приемай, че `getFirst` е O(1) за всяка възможна class, че `reversed()` копира, или че `addFirst` винаги е позволен.

### Лабораторно упражнение

1. Направи `List<String>` с A, B, C и запази reversed view.
2. Премахни `view.removeFirst()`. Коя стойност изчезва от оригинала?
3. Направи `List.copyOf(original.reversed())` и промени оригинала — остава ли копието същото?
4. Опитай `new TreeSet<String>().addLast("D")` и назови точния exception и причината.
5. Замени `List` parameter с `SequencedCollection` в функция, която използва само first/last. Какво печелим/губим?

## Кога да не го използваме

- Бизнес API не обещава encounter order — обикновена `Collection`/`Set` е достатъчна.
- Искаме sorted semantics — използвай `NavigableSet` или `SortedSet` за comparator-based operations.
- Искаме immutable projection — не връщай live reversed view от mutable internal state.
- В performance-sensitive код — измери конкретния collection implementation, не избирай интерфейс като proxy за speed.

**Mental model:** Sequenced е contract за encounter order; не е нова universal collection implementation.

## Original sources

- [Devoxx 2026](https://youtu.be/zC2tbq7S5UE?t=7080)
- [JEP 431 — Sequenced Collections](https://openjdk.org/jeps/431)
- [Java 25 SequencedCollection API](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/SequencedCollection.html)
- [Java 25 SequencedSet API](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/SequencedSet.html)
- [Java 25 SequencedMap API](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/SequencedMap.html)
