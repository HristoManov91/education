# JAVA-OOP-02 — Наследяване, overriding, overloading и полиморфизъм

**Термини:** declared/static/compile-time type, runtime class, inheritance, field hiding, method overriding, method overloading, dynamic dispatch, cast, ClassCastException.

## Защо е важно

При Java backend код интерфейс или родителски клас често се използва като статичен тип на променлива, докато реалният обект е конкретна имплементация. Трябва да различаваме **проверка и избор по време на компилация** от **избор на override-ната реализация по време на изпълнение**, както и полета от методи. Това помага при дизайн на API, dependency injection, proxy обекти и debugging.

## Mental model за 30 секунди

- В `Animal pet = new Dog();` типът **отляво** е *declared/static type* на променливата `pet` — `Animal`. Изразът **отдясно** създава `Dog` обект; действителният (*runtime*) тип на обекта е `Dog`. Лявата страна не е „поведението“ на обекта.
- Компилаторът използва **статичния тип на израза**, за да установи достъпните членове, да провери типова съвместимост и да направи **overload resolution**.
- При обичайни override-нати **instance методи** действителният тип на обекта определя коя реализация се изпълнява (*dynamic method dispatch*).
- **Полетата не се override-ват**. Ако subclass декларира поле със същото име, получаваме **field hiding**: в обекта съществуват отделни полета `Animal.name` и `Dog.name`; достъпът се определя статично.
- `(Dog) pet` не създава, мутира или превръща обекта в друг клас. Cast изразът има компилационен тип `Dog`, а при изпълнение има проверка за съвместимост; при несъвместим обект се хвърля `ClassCastException`.
- **Overloading:** няколко метода с едно име и различни параметри, напр. `speak()` и `speak(String)`. **Overriding:** subclass предоставя нова реализация на наследен instance метод със съвместима сигнатура.

## Изпълним пример — диагностичен Q8, 2026-10-12

```java
public class Main {
    public static void main(String[] args) {
        Animal pet = new Dog();

        System.out.println(pet.name);
        System.out.println(pet.speak());
        System.out.println(pet.description());

        Dog dog = (Dog) pet;
        System.out.println(dog.name);
        System.out.println(dog.speak("Loud"));
    }
}

class Animal {
    String name = "Animal";

    String speak() {
        return "Unknown";
    }

    String description() {
        return name + ": " + speak();
    }
}

class Dog extends Animal {
    String name = "Dog";

    @Override
    String speak() {
        return "Woof";
    }

    String speak(String prefix) {
        return prefix + " Woof";
    }
}
```

**Реален изход:**

```text
Animal
Woof
Animal: Woof
Dog
Loud Woof
```

### Причина и последица, стъпка по стъпка

1. `new Dog()` създава един `Dog` обект. Той съдържа наследената част от `Animal` и собствената част от `Dog`, включително **два отделни** `name` instance field-а.
2. `pet.name`: изразът `pet` е със статичен тип `Animal`, затова field access е към `Animal.name` → `Animal`. Полета не участват във virtual dispatch.
3. `pet.speak()`: компилаторът вижда `Animal.speak()`, но виртуалното извикване се изпълнява върху обект `Dog`. Най-специфичната override-ната реализация `Dog.speak()` връща `Woof`.
4. `pet.description()`: `Dog` не е override-нал `description()`, затова се изпълнява наследеният `Animal.description()`. В този **метод, деклариран в `Animal`**, простото име `name` е свързано с `Animal.name` (статичен избор на поле). Извикването на `speak()` обаче е върху същия действителен `Dog` обект и dispatch-ва към `Dog.speak()` → `Animal: Woof`.
5. `Dog dog = (Dog) pet`: runtime проверката успява, защото обектът действително е `Dog`. `dog` и `pet` продължават да реферират **един и същ** обект. Cast-ването не променя runtime типа/състоянието на обекта и не създава копие.
6. `dog.name`: статичният тип на `dog` е `Dog` и се избира скритото `Dog.name` → `Dog`.
7. `dog.speak("Loud")`: overload-ът `Dog.speak(String)` е видим и се извиква → `Loud Woof`.

**Отделна проверка:**

```java
Animal pet = new Dog();
// pet.speak("Loud");  // compile-time error: няма speak(String) в Animal
System.out.println(((Dog) pet).speak("Loud")); // Loud Woof
```

За първия израз Java не търси методи от runtime класа, за да „добави“ възможни overload-и към статичния API на `Animal`. Това е разликата между **compile-time method selection** и **runtime overriding dispatch**.

Ако действителният обект е `Animal`, а не `Dog`:

```java
Animal pet = new Animal();
Dog dog = (Dog) pet; // ClassCastException по време на изпълнение
```

## Типични неточни формулировки

- „Отляво на присвояването стои поведението.“ — Не. Типът на променливата задава **compile-time видимите операции**, не променя runtime типа на обекта.
- „Cast-ването променя поведението на обекта.“ — Не. То позволява използване на референтния израз с друг компилационен тип (при валидна конверсия), без да променя реферирания обект.
- „Референцията `Animal` не знае за `Dog`.“ — По-точно: компилаторът проверява наличието на `speak(String)` чрез **статичния тип `Animal`**, а метод с тази сигнатура в `Animal` няма.
- „Двете `name` полета са override-нати.“ — Не. Полетата са **скрити** (*field hiding*), докато `speak()` е override-нат.

## Професионално обяснение — интервю или колега

> При `Animal pet = new Dog()` референтната променлива има статичен тип `Animal`, а самият обект има действителен тип `Dog`. Компилаторът използва статичния тип, за да установи достъпните методи, полета и overload-и. За override-натите instance методи по време на изпълнение се избира реализацията спрямо действителния клас на обекта. Полетата не са полиморфни: едноименно поле в наследника скрива полето на родителя, а достъпът до тях се определя статично. Cast към `Dog` не създава нов обект и не променя съществуващия; при съвместим runtime тип просто позволява по-специфичен тип за израза.

## Текст за слушане

Да разгледаме Java израза Animal pet равно на new Dog. От лявата страна декларираме статичния тип на променливата — Animal. От дясната страна създаваме конкретен обект от класа Dog. Това не са две различни версии на обекта и не означава, че той сменя поведението си. Обектът остава Dog през цялото си съществуване.

По време на компилация Java трябва да знае дали извикването на даден метод е позволено. За тази цел гледа типа на израза, през който извикваме метода. В нашия пример променливата pet е Animal, затова компилаторът разпознава speak без аргументи, но не и speak с аргумент String, който е добавен само в Dog.

Когато изпълнението действително извика override-нат instance метод, JVM използва динамично диспечиране, за да избере реализацията за действителния тип на обекта. Затова pet точка speak изпълнява Dog точка speak и връща Woof.

Но при полетата правилото е различно. Полето с име name в Dog не замества полето name в Animal. Това са отделни полета, а директният достъп до поле се определя статично. Затова pet точка name означава полето от Animal. Ако извикаме унаследения метод description, той достъпва полето name, което е декларирано в Animal, но при извикване на speak все пак се изпълнява override-натата Dog реализация. Изходът е Animal, двоеточие, Woof.

Накрая правим cast към Dog и присвояваме резултата на нова променлива dog. Cast-ването не прави нов обект. Двете променливи сочат към същия Dog. Разликата е, че изразът dog има статичен тип Dog и компилаторът вече позволява да използваме неговите специфични методи и полета. Ако runtime обектът не е съвместим с Dog, cast-ването завършва с ClassCastException.

## Q9 — Overload resolution и runtime dispatch (2026-10-12)

При `Animal first = new Dog(); Dog second = new Dog(); Animal secondAsAnimal = second;` имаме различни статични типове, но и трите променливи не сочат към един обект: `first` сочи към първия `Dog`, а `second` и `secondAsAnimal` — към втория `Dog`.

```java
class Animal {
    String interact(Animal other) { return "Animal -> Animal"; }
}
class Dog extends Animal {
    @Override
    String interact(Animal other) { return "Dog -> Animal"; }
    String interact(Dog other) { return "Dog -> Dog"; }
}
```

| Извикване | Избран overload при компилация | Runtime реализация / резултат |
| --- | --- | --- |
| `first.interact(second)` | `interact(Animal)` — статичният тип на получателя е `Animal` | `Dog.interact(Animal)` → `Dog -> Animal` |
| `((Dog) first).interact(second)` | `interact(Dog)` — типът на получателя и аргумента е `Dog` | `Dog.interact(Dog)` → `Dog -> Dog` |
| `((Dog) first).interact(secondAsAnimal)` | `interact(Animal)` — аргументът има статичен тип `Animal` | `Dog.interact(Animal)` → `Dog -> Animal` |
| `((Dog) first).interact((Dog) secondAsAnimal)` | `interact(Dog)` — типът на cast израза на аргумента е `Dog` | `Dog.interact(Dog)` → `Dog -> Dog` |

В същата задача `second == secondAsAnimal` е `true` (един и същ обект). След извеждане на `BEFORE`, `Animal ordinary = new Animal(); Dog wrong = (Dog) ordinary;` предизвиква `ClassCastException` и `AFTER` не се изпълнява.

**Механизмът:** Изборът на приложим overload е **compile-time**, по статичните типове на изразите. За избрания instance метод override-натата реализация се определя чрез **runtime dispatch** според действителния тип на обекта *получател*. Runtime типът на *аргумента* не прави автоматичен втори overload selection.

**Правилна йерархия:** `Animal` е по-общ superclass; `Dog` — по-специфичен subclass. `Dog → Animal` е *upcast/widening reference conversion*; `Animal → Dog` — *downcast/narrowing reference conversion*. Downcast може да се компилира, но да се провали при изпълнение, ако конкретният обект не е съвместим с `Dog`. Cast не създава и не променя обект; променя compile-time типа на израза.

**Професионално обяснение:** „Първо компилаторът избира метода по видимите сигнатури и статичните типове на изразите. После JVM изпълнява override-натата реализация спрямо runtime типа на получателя. Cast може да промени статичния тип на израза и избора на overload, но не и самия обект.“

**Аудио допълнение:** Когато Dog предоставя interact с Animal и interact с Dog, Java избира между двата overload-а при компилация. Ако аргументът е деклариран Animal, се избира версията с Animal, независимо че действителният обект може да е Dog. След това, ако получателят действително е Dog, неговият override за interact с Animal се изпълнява чрез динамично диспечиране. Ако направим cast на аргумента към Dog, компилаторът може да избере по-специфичния overload. Това не променя обекта и при несъвместим действителен обект хвърля ClassCastException.

**Recall:** Защо `dog.interact(a)` и `dog.interact((Dog) a)` могат да извикат различни overload-и, дори когато `a` и `dog` реферират обекти от един и същ клас?


## Провери се без подсказване

1. Как обясняваш с точни термини защо `Animal pet = new Dog(); pet.speak();` може да извика метода от `Dog`, но `pet.speak("Loud")` не се компилира?
2. В `Animal.description()` защо директният достъп до `name` е към поле от `Animal`, докато извикването `speak()` достига до `Dog`? Променя ли `(Dog) pet` някое от тези правила?

## Официални източници

- [Java Language Specification 25 — Classes](https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html), включително 8.3 (Fields) и 8.4 (Methods)
- [JLS 25 — Expressions](https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html), 15.11 (Field Access), 15.12 (Method Invocation) и 15.16 (Cast Expressions)
- [JLS 25 — Inheritance, Overriding, and Hiding](https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#jls-8.4.8)
