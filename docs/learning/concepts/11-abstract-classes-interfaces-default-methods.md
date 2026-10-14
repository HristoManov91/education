# JAVA-OOP-03 — Abstract Classes, Interfaces, Default Methods

**Термини:** abstract class, interface, contract, instance field, constructor chain, inherited default implementation, overriding, static type, runtime dispatch, composition.

## Проблем и мотивация

Искаме няколко различни обекта да предоставят общ API договор, но някои от тях споделят и полета, конструкторна инициализация и готово поведение. Интерфейсът определя договор и може да предоставя ограничена обща логика; абстрактният клас може да моделира реална обща основа със състояние. Тези инструменти могат да се комбинират.

## Mental model за 30 секунди

- **Интерфейс:** договор с абстрактни instance методи, плюс възможни `default`, `static` и `private` методи; **няма instance полета**. Всички полета в интерфейс са неявно `public static final`.
- **Абстрактен клас:** може да има instance полета, конструктори, concrete методи и abstract методи. Самият клас не може да бъде инстанциран директно, независимо дали декларира абстрактни методи.
- Конкретен subclass може да наследи готови методи от superclass или default методи от интерфейс; необходимо е всички останали абстрактни instance методи да бъдат реализирани.
- Класът има един непосредствен superclass, но може да имплементира множество интерфейси.
- Компилаторът гледа **static type** за достъпността на методите. Runtime dispatch избира реализацията на override-натите instance методи спрямо действителния клас на обекта.

## Изпълним пример — Въпрос 10 (2026-10-12)

```java
public class Main {
    public static void main(String[] args) {
        Task first = new EmailTask("42");
        Describable second = new SmsTask("99");

        System.out.println(first.info());
        System.out.println(first.description());
        System.out.println(second.description());
        System.out.println(second.code());
    }
}

interface Describable {
    String code();

    default String description() {
        return "I:" + code();
    }
}

abstract class Task implements Describable {
    private final String code;

    Task(String code) {
        this.code = code;
    }

    @Override
    public String code() {
        return code;
    }

    abstract String type();

    String info() {
        return type() + ":" + description();
    }
}

class EmailTask extends Task {
    EmailTask(String code) {
        super(code);
    }

    @Override
    String type() {
        return "EMAIL";
    }
}

class SmsTask extends Task {
    SmsTask(String code) {
        super(code);
    }

    @Override
    String type() {
        return "SMS";
    }

    @Override
    public String description() {
        return "S:" + code();
    }
}
```

**Изход:**

```text
EMAIL:I:42
I:42
S:99
99
```

## Защо точно този изход?

1. `new EmailTask("42")` конструира един конкретен `EmailTask` обект и извиква `Task(String)` чрез `super(code)`. `Task` инициализира `private final String code`. Съществуването на конструктор в абстрактен клас не позволява `new Task("42")`.
2. `Task` имплементира метода `Describable.code()` с `public String code()`. `EmailTask` наследява тази реализация.
3. `Task.info()` извиква `type()`; за реален обект `EmailTask` dynamic dispatch достига до `EmailTask.type()` → `EMAIL`.
4. `description()` не е override-нат в `EmailTask`, затова се използва интерфейсният `default Describable.description()`. Той извиква `code()`, имплементиран в `Task` → `42`. Получаваме `EMAIL:I:42`.
5. `first.description()` дава `I:42`.
6. `second.description()` намира override-натата имплементация от `SmsTask` → `S:99`. `second.code()` достига до concrete `Task.code()` → `99`.
7. `second.info()` е **compile-time error**, защото декларираният тип `Describable` няма `info()`. `((Task) second).info()` е допустимо и отпечатва `SMS:S:99`: действителният обект е `SmsTask`, а cast не създава или променя обекта.

## Какви методи и полета може да има интерфейс?

В Java 25 интерфейс може да има **четири вида методи**:

```java
interface Describable {
    int MAX_LENGTH = 64; // implicit public static final field

    String code();       // implicit public abstract instance method

    default String description() { // public instance method
        return "I:" + normalizedCode();
    }

    private String normalizedCode() { // private instance helper
        return code().trim();
    }

    static boolean valid(String code) { // static method
        return code != null && !code.isBlank();
    }
}
```

`code()` и `description()` са **instance**, а не `static` методи. Интерфейсните полета са неявно `public static final` и не са индивидуално състояние на екземпляр. `Describable.valid("42")` извиква статичния метод. `private` методите могат да се използват като помощна логика вътре в интерфейса, но не се наследяват. Интерфейс няма конструктор. Имплементацията на `public` интерфейсен метод в клас не може да има по-ограничен достъп.

## Особеност за конструкторите в Java 25

Старото правило, че извикването на `super(...)` или `this(...)` винаги трябва да бъде първата инструкция на конструктора, **вече не е универсално вярно**.

С Java 25 и **JEP 513 Flexible Constructor Bodies** могат да се изпълняват определени инструкции преди извикването на родителски конструктор, например валидация:

```java
EmailTask(String code) {
    if (code == null || code.isBlank()) {
        throw new IllegalArgumentException("Invalid code");
    }

    super(code);
}
```

В Java 25 това е валидно. Ранният контекст на конструиране има ограничения относно достъпа до `this` / незавършилия обект. Обичайното правило, че създаването на subclass включва и инициализация на superclass, остава валидно.

## Дизайн — кога кое?

| Нужда | Подход |
| --- | --- |
| Общ договор за независими имплементации | Интерфейс, напр. `PaymentProcessor` |
| Малка споделена реализация, без instance състояние | Интерфейсен `default` метод, ако е подходящ за договора |
| Реално общо състояние, constructor invariant, базова логика или template method | Абстрактен клас |
| Само повтаряща се логика без истинска is-a връзка | Composition / отделна зависимост вместо наследяване |

Дизайнът не изисква абстрактен клас за всеки интерфейс. В Spring често се инжектира зависимост чрез интерфейс, а конкретната имплементация остава независима. Избирай abstract class само когато има смислен общ модел със състояние или поведение.

## Професионална формулировка

> Интерфейсът е договор между потребителя на API и неговите имплементации. Той може да има абстрактни instance методи, default, static и private методи, но не може да притежава instance полета. Абстрактният клас може да съдържа общо instance състояние, конструктор, concrete и abstract методи, като не може да бъде инстанциран директно. В нашия пример Task съхранява code и реализира code() от Describable. EmailTask наследява default реализацията на description(), а SmsTask я override-ва. Интерфейсът е подходящ за общ договор, а abstract class — когато сродни класове действително споделят състояние и реализация.

## Текст за слушане

Интерфейсите и абстрактните класове са два начина да формулираме общо поведение, но те не са еднакви. Интерфейсът определя договор за това какви операции трябва да са достъпни. Може да съдържа abstract instance методи, които имплементиращите класове трябва да осигурят, както и готови default методи. Отделно може да има static и private помощни методи. Полетата в интерфейсите са статични и final — интерфейсът няма индивидуално instance състояние.

Абстрактният клас предоставя обща основа на сродни класове. Може да има private полета, конструктор и обща бизнес логика, но не може директно да се инстанцира. Когато създадем EmailTask, конструкторът му извиква родителския конструктор на Task, който инициализира полето code. Класът Task реализира метода code от интерфейса Describable, а EmailTask добавя своята реализация на type.

Методът info е дефиниран в Task. При извикване върху EmailTask той изпълнява метода type от EmailTask и default description от Describable. А вътре в description се използва вече реализираният в Task метод code. По тази причина се получава EMAIL двуеточие I двуеточие четиридесет и две.

Класът SmsTask има друга версия на description, която заменя поведението по подразбиране и връща S двуеточие деветдесет и девет. Ако променливата е декларирана като Describable, компилаторът позволява само членовете, видими през този интерфейс. След cast към Task можем да извикаме info, но cast не създава нов обект.

При Java 25 трябва да помним и още една особеност — flexible constructor bodies. Старото ограничение super да е задължително първата инструкция е отпаднало за допустимите ранни инструкции, например проверка на аргументи. Все още има ограничения върху използването на текущия обект до завършване на съответната фаза на инициализация.

Когато проектираме система, използваме интерфейс за общ договор на различни имплементации. Използваме абстрактен клас, ако тези имплементации имат истинско общо състояние, инварианти и базова логика. Ако просто искаме да преизползваме някаква логика между несродни класове, composition често е по-гъвкаво от наследяването.

## Самопроверка за по-късен етап

1. Какви методи са допустими в съвременен интерфейс и кои от тях са instance методи? Защо интерфейсното поле не може да е обикновено instance поле?
2. Защо `new Task("x")` е невалидно, но `new EmailTask("x")` използва `Task(String)`? Кое правило за `super(...)` се промени в Java 25?

## Официални източници

- [JLS 25 — Interfaces](https://docs.oracle.com/javase/specs/jls/se25/html/jls-9.html), Sections 9.3–9.4
- [JLS 25 — Classes](https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html)
- [Java SE 25 — Flexible Constructor Bodies](https://docs.oracle.com/en/java/javase/25/language/flexible-constructor-bodies.html)
