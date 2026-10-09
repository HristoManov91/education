# JAVA-FND-05 (част 2) — Autoboxing, Unboxing, Integer Caching & Null Safety

**Тема:** автоматични преобразувания между `int` и `Integer`, различните механизми на `==`, `Integer.equals()`, кешираните wrapper стойности и риска от `NullPointerException` при unboxing.

**Ключови термини:** primitive, wrapper type, boxing conversion, unboxing conversion, reference identity, numerical equality, `Integer` cache, `NullPointerException`.

## Защо това е важно

В Java backend приложения често ползваме `Integer` в DTO-та, ORM entities, mapping и външни API-та, където стойността може да бъде `null`. Автоматичните преобразувания между примитивен и wrapper тип правят кода удобен, но могат да променят семантиката на `==` или да предизвикат `NullPointerException` на неочакван ред. Освен това кешираните малки `Integer` стойности могат да прикрият грешно сравнение на wrapper обекти с `==`.

## Mental model за 30 секунди

- **Autoboxing:** Java може автоматично да преобразува `int` стойност в `Integer` референция, например `Integer number = 127;`. Това е *boxing conversion*; не е задължително да се създава нов обект за всяка конверсия.
- **Unboxing:** Java може автоматично да преобразува `Integer` в `int`, например `int number = boxed;` или когато сравнява `Integer` с `int`. Не е нужен изричен cast.
- **`Integer == Integer`:** два операнда от референтен тип → проверка за **identity** на обектите, а не за числово равенство.
- **`Integer == int`:** единият операнд е примитивен → Java прилага *binary numeric promotion*, включително unboxing на wrapper-а, и сравнява **числовите стойности**.
- **Кеширане:** boxing на константна `int` стойност между **-128 и 127** включително задължително дава еднаква референция при еднаква стойност. Извън този диапазон **не разчитай** на идентичността на независимо боксирани обекти; имплементациите могат да кешират и повече.
- **`Integer.equals(Object)`:** сравнява логически `Integer` стойности, когато аргументът е от същия wrapper тип; примитивният аргумент се боксира при нужда.
- **`null`:** сравнението на wrapper с `null` проверява референция; опитът за **unboxing на `null`** хвърля `NullPointerException`.

## Изпълним пример — Въпрос 5.2

```java
public class Main {
    public static void main(String[] args) {
        Integer first = 127;
        Integer second = 127;

        Integer boxed = 1000;
        int primitive = 1000;

        Integer missing = null;

        System.out.println(first == second);
        System.out.println(boxed == primitive);
        System.out.println(first.equals(primitive));
        System.out.println(missing == null);
        System.out.println(missing == 0);
        System.out.println("END");
    }
}
```

Изпълнението отпечатва **четири** булеви стойности:

```text
true
true
false
true
```

На реда `missing == 0` възниква **`NullPointerException`**. Точният текст на exception/stack trace е зависим от runtime и обкръжението. **`END` не се отпечатва.**

### Причинно-следствен анализ, ред по ред

1. **`first == second`:** и двата операнда са `Integer`, следователно `==` проверява дали референциите сочат към един и същ обект. Стойността `127` е константна и влиза в гарантирания диапазон за споделено boxing представяне, затова сравнението е `true`. `==` **не преминава автоматично към value equality**, когато и двата операнда са `Integer`.
2. **`boxed == primitive`:** единият операнд е `Integer`, другият — `int`. Правилата за числово равенство извършват unboxing на `boxed` и сравняват `1000 == 1000`, затова резултатът е `true`.
3. **`first.equals(primitive)`:** `equals()` приема `Object`. Примитивният `1000` се боксира като `Integer`, а `Integer.equals()` проверява съответния wrapper тип и равенство на числовите стойности. `127` не е `1000` → `false`.
4. **`missing == null`:** референтно сравнение без unboxing; `missing` е `null` → `true`.
5. **`missing == 0`:** сравняваме `Integer` и примитивен `int`; Java опитва unboxing на `missing` и хвърля `NullPointerException`, защото референцията е `null`. Изпълнението не достига последния `println`.

### Трите ключови контраста

```java
Integer first = 127;
Integer second = 127;
int number = 127;

System.out.println(first == second); // true: identity на гарантирано споделен cached обект
System.out.println(first == number); // true: unboxing, после числово сравнение
System.out.println(first.equals(number)); // true: boxing на int, после логическо сравнение
```

Въпреки че резултатите са еднакви, **причините са различни**. При wrapper-to-wrapper `==` е опасен за сравнение по стойност, защото извън гарантирания диапазон не трябва да приемаме нито `true`, нито `false` за всички JVM конфигурации.

### `null` — защитено сравнение срещу опасно unboxing

```java
import java.util.Objects;

Integer quantity = null;

System.out.println(quantity == null);        // true
System.out.println(Objects.equals(quantity, 0)); // false, без unboxing на null

// int result = quantity + 1; // NullPointerException при unboxing
int safe = (quantity == null) ? 0 : quantity;
```

**Внимание:** `Objects.equals(quantity, 0)` е null-safe логическо сравнение, но това **не** прави всички аритметични операции с nullable wrapper безопасни. За бизнес логика обмисли дали `null` значи „липсва“, „нула“ или невалидна заявка и валидирай според контракта.

## Професионален отговор за колега или интервю

> Autoboxing и unboxing са автоматични преобразувания между примитивен тип като `int` и съответния wrapper `Integer`. При `Integer == Integer` операторът сравнява идентичност на обекти; при `Integer == int` автоматично разопакова wrapper-а и сравнява числови стойности. Кеширането може да направи `==` между wrapper-и привидно работещо — за boxing на константи от -128 до 127 идентичността дори е гарантирана — но това не е заместител на `equals()` за логическо равенство. При unboxing на `null` получаваме `NullPointerException`; затова nullable wrapper стойности трябва да се валидират преди числови операции.

## Текст за слушане

Autoboxing и unboxing са автоматичните преобразувания между числовите примитиви и техните wrapper класове. Например когато Java трябва да запише int стойност в Integer променлива, езикът прилага boxing conversion. А когато използваме Integer там, където е необходим int, Java може автоматично да разопакова числовата стойност. Не е задължително да пишем изричен cast. Тази автоматичност е удобна, но трябва да знаем при кой израз кое преобразуване се извършва.

За да предвидим правилно операторa двойно равно, първо определяме статичните типове на двата операнда. Ако и двата са Integer, извършваме сравнение по идентичност на обектите. Ако единият е Integer, а другият е int, правилата за числовото сравнение автоматично разопаковат wrapper-а и сравняват две примитивни стойности. Това е причината два визуално сходни реда код да се подчиняват на напълно различни механизми.

Особен капан е кеширането на малки Integer стойности. Когато боксираме константна int стойност от минус сто двадесет и осем до сто двадесет и седем, Java гарантира, че едни и същи стойности ще имат еднакви референции след boxing. Така две Integer променливи със стойност сто двадесет и седем могат да бъдат равни и чрез двойно равно. Но това е равенство на референции, не сравнение по числова стойност. Извън гарантирания диапазон JVM може, но не е длъжна, да споделя референциите. Следователно за логическо сравнение на wrapper обекти не разчитаме на двойно равно.

Класът Integer override-ва метода equals, за да сравнява числовата стойност на друг Integer обект. Ако предадем int като аргумент, Java може да го боксира, защото методът приема Object. При това трябва да помним, че equals и двойно равно не са взаимозаменяеми, независимо че в някои примери връщат един и същ резултат.

Накрая, null. Ако Integer променлива е null, сравнението ѝ с null е безопасно, защото проверява референцията. Но ако сравним същата променлива с примитивното число нула или я използваме в аритметична операция, Java може да опита unboxing. При null няма обект, от който да прочете int стойност, и се хвърля NullPointerException. В реален backend код това е причина да валидираме nullable полета в DTO-та и да не приемаме, че wrapper тип е еквивалентен на примитивния.

Практическият навик е да питаме три неща. Първо, какви са статичните типове на двата операнда? Второ, ще се извърши ли boxing или unboxing? Трето, възможно ли е някоя от wrapper референциите да е null? Тази кратка проверка предотвратява много неприятни production бъгове.

## Recall — без да четеш бележката

1. Обясни защо `Integer` и `Integer` със стойност `127` могат да дадат `true` с `==`, а `Integer` срещу `int` използва друг механизъм. Какво е гарантирано при кеширането?
2. Кога `Integer` стойност `null` е безопасна в `==` и кога същата референция може да причини `NullPointerException`?

## Официални източници

- [Java Language Specification 25 — §5.1.7 Boxing Conversion](https://docs.oracle.com/javase/specs/jls/se25/html/jls-5.html#jls-5.1.7)
- [Java Language Specification 25 — §5.1.8 Unboxing Conversion](https://docs.oracle.com/javase/specs/jls/se25/html/jls-5.html#jls-5.1.8)
- [Java Language Specification 25 — §15.21.1 Numerical Equality Operators](https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#jls-15.21.1)
- [Java Language Specification 25 — §15.21.3 Reference Equality Operators](https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#jls-15.21.3)
- [Java 25 API — Integer.equals](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/Integer.html#equals(java.lang.Object))
- [Java 25 API — Objects.equals](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/Objects.html#equals(java.lang.Object,java.lang.Object))
