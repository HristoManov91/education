# JAVA-FND-04 — String vs StringBuilder: immutability, mutation и reassignment

**Ключови термини:** immutable, mutable, concat, append, new object, reference reassignment, garbage collection.

## Защо това е важно

В Java еднакво изглеждащи операции за „добавяне на текст“ имат различни последици. Ако игнорираме връщаната стойност на `String.concat()`, губим резултата. Ако използваме споделен `StringBuilder`, можем неочаквано да променим съдържание, което друг код вижда.

## Най-важното за 30 секунди

- `String` е **immutable**: след създаване съдържанието на конкретния `String` обект не може да се промени.
- `text.concat(" World")` с **непразен аргумент** връща `String`, представящ новата комбинация. Не променя стария обект. Присвояването към `text` е **reassignment на променливата**.
- `StringBuilder` е **mutable**. `builder.append(" World")` променя **същия `StringBuilder` обект** и връща референция към него.
- `builder = new StringBuilder("New")` пренасочва само съответната променлива. Другите референции към стария builder остават насочени към него.
- GC не означава „всяка стара стойност веднага или непременно ще бъде събрана“. Важни са **достижимостта** и решенията на runtime-а; литералите могат да се запазят чрез String Pool.

## Изпълним пример — Въпрос 3

```java
public class Main {

    static void modify(String text, StringBuilder builder) {
        text = text.concat(" World");

        builder.append(" World");

        builder = new StringBuilder("New");
        builder.append(" Value");
    }

    public static void main(String[] args) {
        String text = "Hello";
        StringBuilder builder = new StringBuilder("Hello");

        modify(text, builder);

        System.out.println(text);
        System.out.println(builder);
    }
}
```

**Изход:**

```text
Hello
Hello World
```

### Проследяване на обектите

1. В `main` `text` сочи към `String` със съдържание `Hello`; `builder` сочи към изменяем `StringBuilder` със съдържание `Hello`.
2. В метода `text` и `builder` са **нови локални параметри** с копирани стойности на референциите.
3. `text.concat(" World")` връща нов резултат `Hello World`. Присвояването го запазва **само в локалния** `text`. Оригиналната променлива `text` от `main` продължава да сочи към `Hello`.
4. `builder.append(" World")` изменя **първоначалния `StringBuilder` обект** до `Hello World`. Променливата `builder` в `main` все още сочи към него.
5. `builder = new StringBuilder("New")` създава **друг** builder и пренасочва **локалната** променлива. `builder.append(" Value")` изменя само този втори обект до `New Value`.
6. При връщане от метода `main` вижда `Hello` и `Hello World`.

**Вариант от въпроса:** ако запишем само `text.concat(" World");` без присвояване, изходът в `main` остава същият. В този пример просто игнорираме върнатата стойност.

### Проверка на идентичността на builder-а

```java
StringBuilder original = new StringBuilder("A");
StringBuilder result = original.append("B");

System.out.println(original == result); // true
System.out.println(original);           // AB
```

`append()` връща **същия екземпляр**; не прави нов `StringBuilder`. При разрастване е възможно да се замени **вътрешният буфер** (implementation detail), без да се заменя обектът `StringBuilder`.

### Как да го кажем без подвеждане

| Неточно | Правилно |
| --- | --- |
| „`String` се променя и сочи към нова стойност.“ | „`String` обектът остава неизменен; променливата може да получи референция към друг `String` обект.“ |
| „`StringBuilder.append()` създава нов builder и променя референцията.“ | „`append()` променя текущия mutable обект и връща същия екземпляр.“ |
| „Старият `String` задължително се събира от GC.“ | „Обект може да стане недостижим; моментът и фактът на последващо събиране не са гарантирани.“ |
| „Винаги е по-добре да ползваме `StringBuilder` вместо `+`.“ | „`StringBuilder` е подходящ за многократно конструиране; за прост код `+` е четим и JVM може да го оптимизира.“ |

## Професионален отговор (за колега или интервю)

> `String` е неизменяем тип. `concat()` не променя съществуващия обект, а при непразен аргумент връща нов резултат, който тук присвояваме на локалния параметър `text`. Това не променя променливата в `main`. `StringBuilder` е изменяем тип: `append()` модифицира същия обект, към който сочат локалният параметър и променливата в `main`, и връща референция към този обект. Когато след това присвоим нов `StringBuilder` на локалния `builder`, пренасочваме само параметъра. Оригиналният builder остава със съдържание `Hello World`.

## Текст за слушане

String и StringBuilder се използват за работа с текст в Java, но имат различен модел на изменение. String е неизменяем. След създаване даден String обект запазва своето съдържание. Ако извикаме concat с непразен аргумент, получаваме резултат в друг String обект. За да използваме новия текст, трябва да запазим върнатата референция. Дори да присвоим тази референция на променлива, самият първоначален String обект не се изменя.

StringBuilder, за разлика от String, е изменяем. Когато извикаме append, той добавя символите към съществуващия builder и връща референция към същия обект. Това означава, че две променливи, които сочат към общ StringBuilder, ще виждат промяната, независимо коя от тях е използвана за append.

Ако вътре в метод присвоим нов StringBuilder на параметъра, ще пренасочим само локалната променлива. Извикващият метод ще продължи да вижда стария обект с всички изменения, направени преди пренасочването. Тук се съчетават две правила: Java предава аргументите по стойност, а промените в споделен изменяем обект се виждат през всички референции към него.

За практиката е важно да различаваме immutable операция, която връща нов резултат, от mutation, която променя вече съществуващ обект. Тази разлика помага да предвиждаме странични ефекти и да избираме по-ясни API-та.

## Провери се без подсказване

1. Защо `text.concat(" World");` без използване на върнатата стойност не променя `text`?
2. Какво точно се променя при `builder.append(" World")` и какво при `builder = new StringBuilder("World")`?

## Официални източници

- [Java 25 — String API, concat](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/String.html#concat(java.lang.String))
- [Java 25 — StringBuilder API, append и capacity](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/StringBuilder.html)
- [Java Language Specification 25 — Method Invocation Expressions](https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#jls-15.12)
