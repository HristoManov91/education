# JAVA-OOP-01 — Fields, Constructors, `this`, `private` и `final`

**Обхват:** първоначална диагностика за класове, инициализация на полета, засенчване на параметри, access modifiers и `final`. Проверката на бизнес инварианта при всички промени е **в процес**; тук не публикуваме готово решение на неприключилия follow-up.

**Термини:** instance field, constructor, parameter shadowing, field default initialization, `this`, encapsulation, object invariant, `final`, instance initializer, static initializer, definite assignment.

## Защо тази концепция е важна

Клас може да се компилира успешно, но да запази неправилно състояние, ако параметър засенчи поле. `private` и `final` помагат да управляваме достъпа и присвояванията, но сами по себе си **не доказват**, че обектът винаги съдържа валидни бизнес данни.

## Mental model за 30 секунди

- Параметър на конструктор, който има същото име като поле, го **засенчва**. Изразът `quantity = quantity` присвоява параметъра на самия него, а не променя полето. `this.quantity = quantity` изрично избира полето на текущия обект.
- Неинициализираните instance полета от примитивен тип получават стойности по подразбиране, например `int → 0`. Това не прави програмата семантично правилна.
- `private` ограничава достъпа до поле от външен код. **Не** задължава класа да предоставя setter; често е по-добре поведението да е през смислени публични методи.
- `final` върху примитивно поле забранява повторно присвояване след инициализация. При reference поле забранява **пренасочването на референцията**, но не прави посочвания mutable обект immutable.
- Blank `final` **instance field** трябва да се инициализира определено чрез initializer/instance initializer/конструктор; blank `static final` — чрез field initializer или `static { }` в класа. Static initializer не инициализира instance `final` поле.
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

## Професионална формулировка

> Параметърът на конструктора `quantity` засенчва едноименното instance поле. Изразът `quantity = quantity` е присвояване на параметъра към него самия и не променя полето. Неинициализираното поле `int quantity` остава със стойност по подразбиране `0`, затова `addItems(3)` води до `3`, не `8`. Правилното присвояване на полето е `this.quantity = quantity`. `private` капсулира достъпа, а `final` предотвратява повторно присвояване на поле, но не прави автоматично mutable рефериран обект immutable и не осигурява бизнес валидност на състоянието. Инвариантите трябва да се пазят във всички разрешени операции, които могат да ги нарушат.

## Свързан текст за слушане

Когато създаваме Java обект, конструкторът има задача да инициализира полетата на екземпляра. Не бива обаче да смесваме параметрите на конструктора с неговите полета. Ако параметър и поле имат едно и също име, параметърът засенчва полето. Когато напишем куонтити равно на куонтити, всъщност присвояваме параметъра на самия него. Полето не се променя и запазва стойността си по подразбиране, която за int е нула. За да изберем полето на текущия обект, използваме this точка куонтити.

Модификаторът private ограничава директния достъп до полето. Важно е да не приемаме, че всяко private поле трябва да има setter. Ако дадем публичен setter, той също става начин за промяна на вътрешното състояние. Добрата капсулация не е механично добавяне на getter и setter, а добре определено публично поведение, което запазва правилата на обекта.

Модификаторът final има различен ефект в зависимост от това какво съхранява променливата. При примитивния тип не можем повторно да присвоим различна стойност. При референтния тип не можем да пренасочим полето към друг обект, но ако самият обект е изменяем, можем да променим състоянието му. Например final референция към StringBuilder може да извиква append. Когато инициализираме final поле, трябва да спазваме правилата за определено присвояване. Нестатичните полета могат да се инициализират с полеви инициализатор, instance initializer или конструктор, а static final полета използват контекста на статичната инициализация.

Накрая, бизнес инвариантът е условие за допустимото състояние на обекта. Ако искаме определено числово поле да запазва ограниченията си, трябва да мислим не само за конструктора, но и за публичния интерфейс, през който състоянието може да се изменя. Тази част от диагностиката се разглежда отделно след получаване на собствено обяснение.

## Recall — без подсказване

1. Защо `quantity = quantity` не променя instance полето при едноименен параметър и какво ще отпечата `getQuantity()` след `addItems(3)`?
2. Каква е разликата между `final long id`, `final StringBuilder audit` и `static final String KIND` по отношение на инициализацията и повторните присвоявания?

## Официални източници

- [Java Language Specification 25 — Fields and Field Initialization](https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#jls-8.3)
- [Java Language Specification 25 — final Fields](https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#jls-8.3.1.2)
- [Java Language Specification 25 — Instance Initializers](https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#jls-8.6)
- [Java Language Specification 25 — Static Initializers](https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#jls-8.7)
- [Java Language Specification 25 — Definite Assignment](https://docs.oracle.com/javase/specs/jls/se25/html/jls-16.html#jls-16.9)
