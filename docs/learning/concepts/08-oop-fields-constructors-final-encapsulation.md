# JAVA-OOP-01 — Fields, Constructors, `this`, `private` и `final`

**Обхват:** първоначална диагностика за класове, инициализация на полета, засенчване на параметри, access modifiers и `final`. Бизнес инвариантът `quantity >= 0` и защитата му при всички промени са разгледани в завършения follow-up.

**Термини:** instance field, constructor, parameter shadowing, field default initialization, `this`, encapsulation, object invariant, `final`, instance initializer, static initializer, definite assignment.

## Защо тази концепция е важна

Клас може да се компилира успешно, но да запази неправилно състояние, ако параметър засенчи поле. `private` и `final` помагат да управляваме достъпа и присвояванията, но сами по себе си **не доказват**, че обектът винаги съдържа валидни бизнес данни.

## Mental model за 30 секунди

- Параметър на конструктор, който има същото име като поле, го **засенчва**. Изразът `quantity = quantity` присвоява параметъра на самия него, а не променя полето. `this.quantity = quantity` изрично избира полето на текущия обект.
- Неинициализираните instance полета от примитивен тип получават стойности по подразбиране, например `int → 0`. Това не прави програмата семантично правилна.
- `private` ограничава достъпа до поле от външен код. **Не** задължава класа да предоставя setter; често е по-добре поведението да е през смислени публични методи.
- `final` върху примитивно поле забранява повторно присвояване след инициализация. При reference поле забранява **пренасочването на референцията**, но не прави посочвания mutable обект immutable.
- `final` **instance field** може да бъде инициализирано в декларацията, instance initializer или конструктор. *Blank final instance field* означава, че няма полеви initializer и изисква определено присвояване по правилата на Java. `static final` се инициализира в декларацията или `static { }`; не в конструктор.
- **Object invariant** е условие за валидно състояние на обекта, което дизайнът на публичните операции трябва да пази. Проверка само при конструиране не е автоматична гаранция за по-късните състояния.

## Изпълним пример — първоначален въпрос №6

```java
public class Main {
    public static void main(String[] args) {
        Order order = new Order(42L, 5);
        order.addItems(3);
        System.out.println(order.getId());
        System.out.println(order.getQuantity());
    }
}

class Order {
    private final long id;
    private int quantity;

    public Order(long id, int quantity) {
        this.id = id;
        quantity = quantity; // параметърът засенчва полето; self-assignment
    }

    public void addItems(int amount) {
        quantity += amount;
    }

    public long getId() {
        return id;
    }

    public int getQuantity() {
        return quantity;
    }
}
```

**Реален изход:**

```text
42
3
```

### Механизмът стъпка по стъпка

1. След заделяне и инициализация на instance данните `id` и `quantity` имат съответните стойности по подразбиране. `id` е blank `final` поле и задължително получава стойност в конструктора.
2. `this.id = id` задава `42L` на instance полето.
3. В `quantity = quantity` и двете срещания на идентификатора сочат към **параметъра** на конструктора. Полето `this.quantity` остава `0`.
4. `addItems(3)` увеличава това поле от `0` на `3`.
5. Конструкторът трябва да задава `this.quantity = quantity`. При такава **само синтактична поправка** този конкретен пример би отпечатал `42` и `8`. Това **не е** завършен production-safe модел за инвариант.

## `private`, `final` и инициализатори

```java
class Examples {
    private final long id;
    private final StringBuilder audit = new StringBuilder("created");

    private static final String KIND;

    static {                        // static initializer
        KIND = "ORDER";
    }

    {                               // instance initializer
        audit.append(":initialized");
    }

    Examples(long id) {
        this.id = id;               // blank final instance поле
    }

    String auditText() {
        return audit.toString();
    }

    void updateAudit() {
        audit.append(":updated");   // позволено: променя обекта
        // audit = new StringBuilder(); // не е позволено: final референцията не може да се пренасочи
    }
}
```

Разликата е между **присвояване на референция** и **mutation на съществуващ обект**. При `final StringBuilder` можем да извикаме `append()`, но не да присвоим друг builder на полето. За `static final` и `final` instance fields има различни правила за определено присвояване (*definite assignment*).

## Завършен follow-up: object invariant и всички пътища за промяна

След като поправим конструктора с `this.quantity = quantity`, **това не гарантира**, че `quantity` остава неотрицателно при всички операции.

Два независими случая:

```java
Order first = new Order(42L, 5);
first.addItems(-10); // математически резултат -5

Order second = new Order(43L, Integer.MAX_VALUE);
second.addItems(1); // int overflow → Integer.MIN_VALUE
```

При стария `quantity += amount` двата резултата са съответно **-5** и **-2147483648**. Първият нарушава инварианта чрез отрицателен аргумент, вторият — при overflow.

### Защо проверката само на крайния резултат е недостатъчна

```java
int newQuantity = this.quantity + amount;
if (newQuantity < 0) {
    throw new IllegalArgumentException("Invalid quantity");
}
this.quantity = newQuantity;
```

При неотрицателно начално количество този код открива и двата конкретни случая, но не отхвърля всеки **невалиден аргумент**. Например при `quantity = 5` и `amount = -2` резултатът е `3`: инвариантът `quantity >= 0` се запазва, но е нарушена семантиката на метод за **добавяне** на артикули.

Разграничаваме три различни гаранции:

- **Precondition:** `amount >= 0` или `amount > 0`, ако бизнес правилото забранява нулева операция.
- **Object invariant:** всички валидни състояния на обекта удовлетворяват `quantity >= 0`, включително след всяка успешна публична операция.
- **Overflow safety:** аритметиката се проверява **преди присвояване** в полето.

### Защитена реализация с Math.addExact

```java
public final class Order {
    private final long id;
    private int quantity;

    public Order(long id, int quantity) {
        if (quantity < 0) {
            throw new IllegalArgumentException("Initial quantity cannot be negative");
        }

        this.id = id;
        this.quantity = quantity;
    }

    public void addItems(int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Amount cannot be negative");
        }

        int newQuantity = Math.addExact(this.quantity, amount);
        this.quantity = newQuantity;
    }

    public long getId() {
        return id;
    }

    public int getQuantity() {
        return quantity;
    }
}
```

`Math.addExact(int, int)` хвърля **ArithmeticException**, ако математическата сума не се побира в `int`. Отрицателният аргумент се отхвърля с **IllegalArgumentException**. Полето се актуализира само след успешното изчисление — така при неуспех остава непроменено.

Алтернатива при неотрицателни `quantity` и `amount` е предварителната проверка `amount > Integer.MAX_VALUE - quantity`. Тя позволява и изричен домейн exception. Не разчитай на случаен отрицателен резултат след wrap-around като основен механизъм за откриване на overflow.

**Encapsulation:** публичен setter не е задължителен. Ако го има, той също трябва да гарантира инварианта. Ако няма, `addItems()` е смислена бизнес операция за контролирана промяна.

**Професионално обяснение:** „Инвариантът трябва да се спазва не само при конструиране, но и след всеки успешно завършил публичен метод. При добавяне проверявам входния аргумент, откривам overflow преди записването и едва след това присвоявам новата стойност. При грешка състоянието остава непроменено.“

## Професионална формулировка

> Параметърът на конструктора `quantity` засенчва едноименното instance поле. Изразът `quantity = quantity` е присвояване на параметъра към него самия и не променя полето. Неинициализираното поле `int quantity` остава със стойност по подразбиране `0`, затова `addItems(3)` води до `3`, не `8`. Правилното присвояване на полето е `this.quantity = quantity`. `private` капсулира достъпа, а `final` предотвратява повторно присвояване на поле, но не прави автоматично mutable рефериран обект immutable и не осигурява бизнес валидност на състоянието. Инвариантите трябва да се пазят във всички разрешени операции, които могат да ги нарушат.

## Свързан текст за слушане

Когато създаваме Java обект, конструкторът има задача да инициализира полетата на екземпляра. Не бива обаче да смесваме параметрите на конструктора с неговите полета. Ако параметър и поле имат едно и също име, параметърът засенчва полето. Когато напишем куонтити равно на куонтити, всъщност присвояваме параметъра на самия него. Полето не се променя и запазва стойността си по подразбиране, която за int е нула. За да изберем полето на текущия обект, използваме this точка куонтити.

Модификаторът private ограничава директния достъп до полето. Важно е да не приемаме, че всяко private поле трябва да има setter. Ако дадем публичен setter, той също става начин за промяна на вътрешното състояние. Добрата капсулация не е механично добавяне на getter и setter, а добре определено публично поведение, което запазва правилата на обекта.

Модификаторът final има различен ефект в зависимост от това какво съхранява променливата. При примитивния тип не можем повторно да присвоим различна стойност. При референтния тип не можем да пренасочим полето към друг обект, но ако самият обект е изменяем, можем да променим състоянието му. Например final референция към StringBuilder може да извиква append. Когато инициализираме final поле, трябва да спазваме правилата за определено присвояване. Нестатичните полета могат да се инициализират с полеви инициализатор, instance initializer или конструктор, а static final полета използват контекста на статичната инициализация.

Накрая, бизнес инвариантът е условие за допустимото състояние на обекта. Ако искаме определено числово поле да запазва ограниченията си, трябва да мислим не само за конструктора, но и за публичния интерфейс, през който състоянието може да се изменя. В последващата проверка разгледахме отрицателен аргумент и integer overflow. Проверката на получения резултат не е равносилна на проверка на бизнес контракта. При addItems първо валидираме входа и аритметиката и едва тогава променяме полето.

## Recall — без подсказване

1. Защо `quantity = quantity` не променя instance полето при едноименен параметър и какво ще отпечата `getQuantity()` след `addItems(3)`?
2. Каква е разликата между `final long id`, `final StringBuilder audit` и `static final String KIND` по отношение на инициализацията и повторните присвоявания?
3. Защо `quantity >= 0` и `amount >= 0` са две различни правила и как ще защитиш `addItems` от integer overflow?

## Официални източници

- [Java Language Specification 25 — Fields and Field Initialization](https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#jls-8.3)
- [Java Language Specification 25 — final Fields](https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#jls-8.3.1.2)
- [Java Language Specification 25 — Instance Initializers](https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#jls-8.6)
- [Java Language Specification 25 — Static Initializers](https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#jls-8.7)
- [Java Language Specification 25 — Definite Assignment](https://docs.oracle.com/javase/specs/jls/se25/html/jls-16.html#jls-16.9)
- [Java 25 API — Math.addExact(int, int)](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/Math.html#addExact(int,int))
