# JAVA-COL-01 — List, Set, Map, equality и hash collisions

**Ключови термини:** List, Set, Map, uniqueness, equality contract, hashing, hash collision, `equals()`, `hashCode()`, record components, autoboxing, `Map.get(Object)`, deduplication by business key.

## Защо тази концепция е важна

При обработка на данни от база, batch jobs, DTO списъци и кешове трябва да е еднозначно дали „един и същ“ обект означава **съвпадение на всички полета**, или **съвпадение на бизнес ключ** (например идентификатор). Грешен `equals/hashCode` contract или неправилно разбиране на hash collisions може да доведе до погрешно премахване на дубликати, проблеми с ключове и некоректни справки.

## Mental model за 30 секунди

- `List` съхранява елементи в последователност и **допуска дубликати**. Поддръжката на `null` зависи от имплементацията: `ArrayList` допуска `null`; `List.of` **не допуска** `null` и е unmodifiable.
- `Set` има контракт за уникални елементи. При `HashSet`/`LinkedHashSet` операцията използва **`hashCode()` за намиране на кандидатите** и **`equals()` за разграничаване на равни от различни елементи**. Колизия **не означава** дубликат. Други Set реализации имат различна стратегия: `TreeSet` ползва ordering чрез `Comparator`/`Comparable`.
- За коректна hash-базирана работа: **ако `a.equals(b)` е `true`, `a.hashCode()==b.hashCode()` трябва да е `true`**. Обратното не следва. Равен hash е възможен за различни обекти.
- `Map<K,V>` държи **един mapping за даден ключ**. `put(K,V)` заменя стойността за съществуващ ключ. `get(Object)` приема `Object`, затова грешен тип при четене може да се компилира; резултатът/изключението зависи от контракта и имплементацията.
- При `record Task(long id, String status)` генерираното равенство включва **и двата компонента**. Ако бизнес уникалността е само по `id`, обикновено ползвай `Map<Long,Task>` с изрично избрана политика за конфликт.

## Изпълним пример — диагностика Q15 (2026-10-14)

```java
import java.util.*;

public class Main {
    record Task(long id, String status) {}

    public static void main(String[] args) {
        Task a = new Task(7L, "NEW");
        Task b = new Task(7L, "SENT");
        Task c = new Task(8L, "NEW");

        List<Task> tasks = new ArrayList<>(List.of(a, b, c));
        Set<Task> unique = new LinkedHashSet<>(tasks);
        Map<Long, Task> byId = new LinkedHashMap<>();

        for (Task task : tasks) {
            byId.put(task.id(), task);
        }

        System.out.println(tasks.size());
        System.out.println(unique.size());
        System.out.println(byId.size());
        System.out.println(byId.get(7L).status());
        System.out.println(byId.get(7));
        System.out.println(unique.contains(new Task(7L, "NEW")));
    }
}
```

**Изход:**

```text
3
3
2
SENT
null
true
```

### Причина → последица

1. `List.of(a,b,c)` съдържа три non-null елемента; `new ArrayList<>(...)` създава изменяем списък със същите три референции.
2. `LinkedHashSet` пази реда на първо добавяне и проверява **равенството на целите record стойности**. `Task(7,"NEW")` и `Task(7,"SENT")` са различни по `status`, така че всички три обекта остават.
3. `byId.put(task.id(),task)` добавя записите с ключове `7L`, `7L`, `8L`. Вторият запис с ключ `7L` **замества стойността** `Task(7,"NEW")` с `Task(7,"SENT")`. Остават два ключа. При insertion-order `LinkedHashMap` замяната не мести съществуващия ключ на края.
4. `byId.get(7L)` използва `Long` ключ и връща последния запис със статус `SENT`.
5. `byId.get(7)` компилира, защото `Map.get` приема `Object`. Литералът `7` е `int` и се boxing-ва до `Integer`, който **не е равен** на `Long(7L)`; `LinkedHashMap` връща `null`.
6. `unique.contains(new Task(7L,"NEW"))` е `true` — новата record инстанция е **равна по компоненти** на `a`. Не изискваме една и съща референция.

Генерираните `equals` и `hashCode` на record спазват component-value равенството. Това не изисква конкретна вътрешна имплементация чрез извикване на `Objects.equals` за всяко поле; важен е договорът на генерираните методи.

## Hash collision не е дубликат

```java
record User(int id) {
    @Override public int hashCode() { return 42; }
}

Set<User> users = new HashSet<>();
users.add(new User(1));
users.add(new User(2));
users.add(new User(3));

System.out.println(users.size());  // 3
```

Трите `User` имат един и същ hash, но `equals` различава записите по `id`. Колизията влошава евентуално ефективността, **не коректността**, ако `equals/hashCode` contract е спазен. Неправилно е да се твърди, че `HashSet` отхвърля елементи единствено заради съвпадащ hash.

Проблемът в обратната посока е съществен: ако `equals` смята два обекта за равни, а `hashCode` връща различни стойности, hash-базираните колекции могат да не ги намерят като дубликати. Ако обект, използван като ключ, е mutable и полетата за equality/hash се изменят след добавяне, lookup също може да се счупи.

## `null` и четене с различен тип ключ

```java
List<String> a = new ArrayList<>();
a.add(null);
a.add(null);              // валидно

// List.of("A", null);   // NullPointerException при създаване

Map<Long, String> byId = new HashMap<>();
byId.put(7L, "TASK");
System.out.println(byId.get(7));  // null: Integer != Long
System.out.println(byId.get(7L)); // TASK
```

Сигнатурите на `Map` се различават:

```java
V put(K key, V value);
V get(Object key);
```

Компилаторът проверява типовете при `put` според `K`, но `get` има умишлено широк API (исторически запазен при generics). Някои имплементации може да хвърлят `ClassCastException` за неподходящ ключ; не обещавай универсално `null`. В Map, който позволява `null` стойности, `get` връща `null` и при липсващ ключ, и при съществуващ ключ с `null` стойност: разграничи чрез `containsKey()`.

## Практически избор в backend система

Ако бизнес идентичността за дадено batch изчисление е само `id`, използвай:

```java
Map<Long, Task> tasksById = new LinkedHashMap<>();

for (Task task : tasks) {
    tasksById.put(task.id(), task);       // политика: последният печели
}
```

Но преди това изясни **conflict policy**: последен (`put`), първи (`putIfAbsent`), най-актуален според поле за време (`merge` с коректен merge function), или отказ при неочакван дубликат. Изричното правило е по-важно от механичното премахване на дубликати.

Override на record `equals/hashCode` само по `id` е технически възможен, но променя **глобалната семантика на равенство**: два `Task` с различен статус биха се считали равни навсякъде. Предпочитай `Map` за контекстно бизнес дедуплициране; override само ако equality by ID действително е invariant на домейна.

## Професионално обяснение — интервю или колега

> `List` запазва всички елементи, включително равни. `Set` гарантира уникалност, като при hash-базираните реализации `hashCode` води до кандидатите, а `equals` решава дали елементът вече присъства. Колизията на hash стойности е нормална и не прави обектите равни. `record` по подразбиране сравнява всички компоненти, така че две задачи с едно ID, но различен статус, не са равни. Ако процесът изисква само един запис по ID, използвам `Map<Long,Task>`, като отделно уточнявам кой запис има предимство. Сигнатурата `get(Object)` позволява и неподходящ тип ключ да се компилира; тук `Integer(7)` не съвпада с `Long(7)`.

## Текст за слушане

Да започнем с най-честата backend задача: имаме три реда от база. Два са за един и същ идентификатор седем, но единият е със статус NEW, а другият със статус SENT. Третият е за идентификатор осем. В списък, тоест List, остават три елемента. List не премахва дубликати. Ако обаче използваме Set, трябва да определим какво означава равен обект.

В нашия случай Task е Java record с два компонента: id и status. Генерираният equals сравнява и двата. Значи задачите с id седем, но с различен статус, не са равни и Set запазва и двете. Hash-базираният Set използва hashCode, за да локализира подходящите кандидати, а equals за реалното равенство. Много важно: две различни стойности могат да имат еднакъв hashCode. Това се нарича hash collision. HashSet няма да изхвърли втория обект само защото hash стойността съвпада. Вместо това ще различи обектите чрез equals.

Ако бизнес правилото е само един ред за даден id, използваме Map с id като ключ. Вмъкването на следваща задача с вече съществуващ ключ замества стойността на този ключ. Затова за id седем остава задачата със статус SENT и в Map има два записа. Не забравяй, че политиката последният печели е избор, който трябва да отговаря на бизнес изискванията. Може да искаме първи запис, най-актуален запис или изключение.

Когато търсим ключ, синтаксисът също е важен. Числото седем без L е int и при подаване на get се boxing-ва до Integer. Ключовете в Map са Long. Integer седем и Long седем не са равни, затова get връща null. Това се компилира, защото методът Map.get приема Object, не задължително декларирания generic тип за ключовете. Погледни API контракта, а не само вида на декларацията. Накрая запомни, че ArrayList може да съдържа null, дори повече от веднъж, но List.of забранява null и ще хвърли NullPointerException още при конструиране.

## Recall — за следващ учебен етап, без подсказване

1. Два обекта имат един и същ `hashCode()`, но `equals()` връща `false`. Какво става при последователното им добавяне в `HashSet` и защо?
2. Ако `Map<Long,Task>` съдържа ключ `7L`, защо `get(7)` е валидно Java извикване, и как бихме избрали между last-wins, first-wins и exception при дубликат в данни от база?

## Официални източници

- [JDK 25 — List](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/List.html)
- [JDK 25 — Set](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/Set.html)
- [JDK 25 — HashSet](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/HashSet.html)
- [JDK 25 — Map](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/Map.html)
- [JDK 25 — LinkedHashMap](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/LinkedHashMap.html)
- [Java Language Specification 25 — Record Classes §8.10](https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#jls-8.10)
