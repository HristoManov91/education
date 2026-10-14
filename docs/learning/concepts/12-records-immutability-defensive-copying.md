# JAVA-OOP-03 — Records, Compact Constructors, Defensive Copying и DTO Design

**Термини:** record component, implicitly final record class, component field, accessor, canonical constructor, compact canonical constructor, reference equality, value equality, shallow immutability, shallow copy, unmodifiable collection, defensive copy, value object, DTO.

## Защо е важно

Когато създаваме REST DTO, projection или value object, искаме ясен договор за компонентите, предвидимо `equals/hashCode` и контрол върху това дали външен код може да изменя данните ни след конструиране. Самият `record` прави полетата `private final`, но **не прави достижимите през тях обекти автоматично immutable**. Затова при компоненти с mutable стойности е важно да разбираме defensive copying.

## Mental model за 30 секунди

- `record OrderView(long id, List<String> items)` е клас, не просто синтаксис за getters. Неявно е `final` и наследява `java.lang.Record`; може да имплементира интерфейси, но не може да разширява обикновен клас.
- За всеки компонент получаваме `private final` поле и публичен accessor със **същото име**, например `id()` и `items()`, а не `getId()`.
- Ако не предоставим собствени реализации, record осигурява `equals(Object)`, `hashCode()`, `toString()` и canonical constructor.
- **Compact canonical constructor** използва неявните параметри от header-а; позволява валидация и нормализация. **След нормалното приключване на тялото му** component полетата се присвояват неявно от текущите параметри.
- `List.copyOf(mutableList)` осигурява **unmodifiable snapshot** на елементните референции. Не е deep copy и не гарантира нов екземпляр при вече unmodifiable списък. Не позволява `null` елементи, нито `null` аргумент.
- `record` сам по себе си гарантира **shallow immutability на компонентните полета**, а не дълбока неизменяемост на всяко реферирано състояние.
- Избирай `record` когато modeling-ът е **value-oriented** с фиксирани компоненти; обикновен клас при mutable state/lifecycle, а **JPA Entity не може да бъде record**.

## Изпълним пример — Q11, 2026-10-12

```java
import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        List<String> source = new ArrayList<>();
        source.add("Book");

        OrderView first = new OrderView(10L, source);
        OrderView second = new OrderView(10L, List.of("Book"));

        source.add("Pen");

        System.out.println(first.items());
        System.out.println(second.items());
        System.out.println(first.equals(second));
        System.out.println(first == second);
        System.out.println(first.id());

        try {
            first.items().add("Ruler");
            System.out.println("ADDED");
        } catch (UnsupportedOperationException e) {
            System.out.println("BLOCKED");
        }
    }
}

record OrderView(long id, List<String> items) {
    OrderView {
        if (id <= 0) {
            throw new IllegalArgumentException("Invalid ID");
        }
        items = List.copyOf(items);
    }
}
```

**Изход:**

```text
[Book]
[Book]
true
false
10
BLOCKED
```

### Причина → следствие, стъпка по стъпка

1. `source` е mutable `ArrayList`, съдържащ `Book`.
2. В първия `OrderView` compact constructor-ът валидира `id` и променя стойността на параметъра `items`, като го насочва към `List.copyOf(source)` — нов unmodifiable snapshot за този mutable вход.
3. Във втория record `List.of("Book")` вече предоставя unmodifiable списък; `List.copyOf` има право да го използва директно.
4. `source.add("Pen")` изменя само оригиналния `ArrayList`, а `first.items()` продължава да е `[Book]`. `second.items()` също е `[Book]`.
5. Record равенството сравнява съответните компоненти: `10L` е равно на `10L` и двете `List` са равни по елементи и ред, дори ако са различни имплементации. Следователно `first.equals(second)` → `true`.
6. `first == second` сравнява референции към отделно конструирани record обекти → `false`.
7. `id()` връща `10`. `items()` връща unmodifiable списък; `add("Ruler")` хвърля `UnsupportedOperationException` по време на изпълнение и catch отпечатва `BLOCKED`.

### Алтернатива без `List.copyOf()`

При празно тяло `OrderView { }` първият record запазва първоначалната референция към `source`. След `source.add("Pen")` неговите items са `[Book, Pen]`. Вторият record продължава да е `[Book]`. Тогава:

```text
[Book, Pen]
[Book]
false
false
10
ADDED
```

След `first.items().add("Ruler")` и оригиналният `source` ще съдържа `[Book, Pen, Ruler]`. Това е *aliasing* и отсъствие на defensive copy.

## Compact canonical constructor — параметри, полета и `this`

Тялото `OrderView { ... }` **е самият canonical constructor**, а не подготвителен конструктор, който извиква втори.

**Концептуално** Java осигурява при успешно приключване на тялото:

```java
OrderView(long id, List<String> items) {
    if (id <= 0) {
        throw new IllegalArgumentException("Invalid ID");
    }

    items = List.copyOf(items);
    this.id = id;
    this.items = items;
}
```

В compact варианта **не изписваме** тези последни присвоявания. Ако изрично напишем `this.items = ...`, получаваме compile-time error: забранено е присвояване на component fields в compact constructor. Директното четене на `this.items` преди неявната инициализация също не е допустимо (полето не е definitely assigned).

Това **не е глобална забрана за използване на `this`** в compact constructor; допустими употреби могат да съществуват, но използването на instance поведение преди установяване на компонентите е опасно. Използвай **параметрите по име** при валидиране и нормализация.

**Професионален принцип:** `validate → normalize parameters → implicit field assignment`.

## Immutability: три различни гаранции

| Механизъм | Гарантира |
| --- | --- |
| `final` component field | Не може да се присвои нова референция след инициализация |
| `List.copyOf` | Структурна неизменяемост на резултатния списък и независим snapshot от mutable вход |
| Deep immutability | Не могат да се изменят и mutable вложените обекти; може да изисква отделно копиране/ immutable модели |

Например:

```java
record Box(List<StringBuilder> items) {
    Box { items = List.copyOf(items); }
}

StringBuilder mutable = new StringBuilder("Book");
Box box = new Box(List.of(mutable));

mutable.append(" Updated");
System.out.println(box.items().get(0)); // Book Updated
```

`List.copyOf` копира елементните **референции**, а не самите `StringBuilder` обекти. За `List<String>` в основната задача е достатъчно, защото `String` е immutable.

## Равенство и `hashCode()` — точна семантика

Record `equals` се определя чрез сравнение на съответните компоненти:

- При референтни компоненти — проверка за `null` и, когато не са null, извикване на `equals` на реферирания компонент. Не се гарантира буквално генериране на `Objects.equals()` в bytecode.
- При примитивни — семантика като wrapper `compare`. Особено при `float`/`double` това има значение за `NaN` и знака на нулата; не е достатъчно да казваме безусловно „използва `==`“.
- `List.equals` сравнява съответните елементи в реда им. Класовете на имплементациите не е необходимо да съвпадат.
- `int[]` не имплементира content-based `equals`: `record Data(int[] a) {}` с два различни масива `{1, 2}` има `false` от `equals`.
- Ако състоянието на вложени mutable обекти се промени, `equals/hashCode` на record-а могат да променят резултата си. Особено опасно за ключове на `HashMap`.

## Практически избор в Spring Boot

| Случай | Предпочитан дизайн | Мотив |
| --- | --- | --- |
| REST request/response DTO | Често `record` | Ясна структура, стойностна семантика, валидиране без setters |
| Spring Data JPA class-based projection | `record` | Constructor-based mapping, неизменяем резултат |
| Value object (`Money`, координати) | `record` | Инварианти и value semantics; операция може да връща нов record |
| JPA `@Entity` | Обикновен клас | Jakarta Persistence не позволява entity да е `record`: нужни са специфични изисквания за entity lifecycle, не-final клас и no-arg constructor |
| Stateful service / mutable entity / клас с наследяване на реализация | Обикновен клас | Mutable lifecycle и поведение, които не пасват на `final` record |

Валидацията **сама по себе си** не прави record неподходящ. Можем да имаме дори сложни проверки и методи в record, стига фиксираните му компоненти и value semantics да представят домейна правилно.

**Нюанс за JPA 3.2:** record може да бъде `@Embeddable` или тип на композитен идентификатор при съответните условия, но **не** `@Entity`. Това не бива да се смесва с DTO projections.

## Професионално обяснение

> Java record е клас за ясно моделиране на фиксиран набор компоненти с value-oriented семантика. Всеки компонент има private final поле и публичен accessor, а compiler/runtime предоставят canonical constructor, equals, hashCode и toString при липса на собствени реализации. Compact canonical constructor позволява да валидираме или нормализираме неявните параметри, като след нормалното му приключване полетата се инициализират автоматично. Record не осигурява deep immutability: при mutable компоненти трябва да предотвратим нежелано aliasing чрез защитни копия и при нужда копиране на вложени обекти. В Spring Boot предпочитам record за DTO, projections и value objects; JPA entities остават обикновени класове с различен lifecycle.

## Текст за слушане

Record в Java е специален вид клас, подходящ за моделиране на структури от данни с фиксирани компоненти. Ако декларираме OrderView с дълъг идентификатор и списък от артикули, компилаторът предоставя полетата, публични методи за достъп, canonical constructor и реализации на equals, hashCode и toString. Методи за достъп се наричат id и items, без префикс get.

Компактният конструктор е кратка форма на canonical constructor. В него имаме параметрите, които са описани в record header-а. Първо можем да валидираме идентификатора и да нормализираме параметъра items. Когато тялото приключи успешно, компилаторът присвоява актуалните параметри към съответните component fields. Това не означава, че compact constructor извиква втори canonical constructor. Вътре не трябва сами да присвояваме this точка items. Самият this не е забранен навсякъде, но компонентните полета още не са присвоени.

В примера source е променяем ArrayList с Book. В конструктора извикваме List.copyOf, за да получим unmodifiable snapshot. След като добавим Pen към source, първият record продължава да съдържа само Book. Същото важи и за втория record. Затова equals дава true, защото id стойностите са еднакви, а списъците са равни по съдържание. Но операторът за сравнение на референции дава false, защото имаме два отделни record обекта.

Когато извикаме add върху unmodifiable списъка, получаваме UnsupportedOperationException. Ако не бяхме използвали List.copyOf, първият record и source щяха да споделят един mutable списък и всяка структурна промяна на source щеше да се вижда и през record. Това е aliasing и може да наруши очакванията за стабилност на обекта.

Но List.copyOf не прави deep copy. Ако вложен елемент е StringBuilder, неговото съдържание остава изменяемо, дори ако структурата на списъка е unmodifiable. Затова immutable record не означава автоматично неизменяемо достижимо състояние. При String е по-лесно, защото String самият е immutable.

В Spring Boot използваме records за REST входни и изходни DTO, value objects и projections, когато описваме стойности, а не живот на entity с променящо се състояние. При JPA entity имаме идентичност и persistence lifecycle, затова record не е допустим за entity. Валидацията не е сама по себе си причина да изберем клас: records могат да съдържат проверки, нормализация и собствени бизнес методи. Решаващ е моделът на състоянието и неговата изменяемост.

## Проверка без подсказване — по-късен етап

1. При compact constructor кога се инициализират `this.id` и `this.items`? Кое е забранено изрично и защо `items = List.copyOf(items)` е допустимо?
2. Какво гарантира `List.copyOf` и защо списък от `StringBuilder` може да изглежда променен, въпреки че самият списък е unmodifiable? Кога предпочиташ record пред клас за DTO?

## Официални източници

- [JLS 25 — Record Classes](https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#jls-8.10)
- [JLS 25 — Compact Canonical Constructors](https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#jls-8.10.4.2)
- [Java SE 25 — List.copyOf](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/List.html#copyOf(java.util.Collection))
- [Oracle — Creating Unmodifiable Collections](https://docs.oracle.com/en/java/javase/25/core/creating-immutable-lists-sets-and-maps.html)
- [Spring Data JPA — Projections](https://docs.spring.io/spring-data/jpa/reference/repositories/projections.html)
- [Jakarta Persistence 3.2 — Entity Class](https://jakarta.ee/specifications/persistence/3.2/jakarta-persistence-spec-3.2)
