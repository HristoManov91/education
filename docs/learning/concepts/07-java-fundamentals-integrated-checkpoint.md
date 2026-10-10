# Java Fundamentals — интеграционен checkpoint (Q1–Q5)

**Обхват:** pass-by-value, примитивни стойности и референции, mutation vs reassignment, immutable `String` и `Integer`, mutable `StringBuilder`, `==` vs `equals()`, boxing/unboxing, cache, widening/narrowing и overflow.

**Статус на материала:** решен интеграционен пример. Оценки и индивидуални пропуски се държат единствено в [PROGRESS.md](../PROGRESS.md).

## Защо е важно

В production Java код често един метод едновременно получава примитивни стойности, mutable обекти, String стойности и wrapper параметри. Поведение, което изглежда като обикновено присвояване или инкремент, може да има различен ефект върху обектите, видими за извикващия код. Особено важно е да различаваме **промяна в обект** от **пренасочване на локална референция**.

## Mental model за 30 секунди

1. В Java всеки параметър получава **копие на стойността на аргумента**. При референтните типове това е копие на *стойността на референцията* — не копие на самия обект.
2. `String` и `Integer` са **immutable**: `concat()` и `attempts++` не променят техните съществуващи обекти. `StringBuilder.append()` е **mutation** на споделения builder.
3. `Integer attempts++`: **unboxing → int increment → boxing → reassignment** на локалния параметър. Няма mutation на стария `Integer` обект. Ако изразът е post-increment, стойността на *израза* е старата стойност, но променливата се актуализира.
4. `Integer == Integer` сравнява **identity**; при boxing на константни стойности в `-128..127` има гарантирано споделяне на референцията. `Integer == int` прави **unboxing**, след което числово сравнение.
5. `String` литерал и `new String(...)` могат да имат еднакво съдържание, но са различни обекти. `==` е identity, а `String.equals()` сравнява последователността от символи.
6. Типът на *аритметичния израз* се определя **преди присвояването**: `int + int` остава `int`. `byte += 1` пресмята с `int` и **после** стеснява към `byte`.

## Напълно изпълним пример

```java
public class Main {

    static void process(
            int count,
            String status,
            StringBuilder audit,
            Integer attempts) {

        count++;
        status = status.concat("-SENT");
        audit.append("|sent");
        attempts++;

        audit = new StringBuilder("local");

        System.out.println(count + ":" + status + ":" + attempts);
    }

    public static void main(String[] args) {
        int count = 2;
        String status = "NEW";
        StringBuilder audit = new StringBuilder("start");
        Integer attempts = 127;

        process(count, status, audit, attempts);

        System.out.println(
                count + ":" + status + ":" + audit + ":" + attempts);

        Integer otherAttempts = 127;
        System.out.println(attempts == otherAttempts);
        System.out.println(attempts == 127);

        String otherStatus = new String("NEW");
        System.out.println(status == otherStatus);
        System.out.println(status.equals(otherStatus));

        long total = Integer.MAX_VALUE + 1;

        byte marker = 127;
        marker += 1;

        System.out.println(total + ":" + marker);
    }
}
```

**Точен изход:**

```text
3:NEW-SENT:128
2:NEW:start|sent:127
true
true
false
true
-2147483648:-128
```

## Проследяване стъпка по стъпка

1. `count` в `process` е локално копие на примитивната стойност `2`. Инкрементът я прави `3`, без да промени променливата от `main`.
2. `status` в `process` започва с копирана референция към immutable `String` `"NEW"`. `concat("-SENT")` връща нов резултат; локалното присвояване към `status` не променя референцията на `main.status`.
3. `audit.append("|sent")` променя съществуващия споделен `StringBuilder` обект, който става `"start|sent"`. Последващият `audit = new StringBuilder("local")` пренасочва само локалния параметър; `main.audit` остава свързан с първоначалния изменен builder.
4. `attempts++` при `Integer` изисква **автоматичен unboxing** до `int 127`, добавя `1` (получаваме `128`), след което **boxing** връща `Integer` за локалния параметър. Първоначалният `Integer(127)` не е изменен. Получената референция за `128` е **различна** от тази за `127`, независимо дали `128` обектът е прясно алокиран или идва от разширен cache.
5. Първият `println` в `process` печата `3:NEW-SENT:128`. Вторият в `main` печата `2:NEW:start|sent:127`.
6. `attempts == otherAttempts` проверява идентичност на **два `Integer` референтни операнда**; boxing на константния `127` попада в гарантирания кеш диапазон `-128..127` → `true`.
7. `attempts == 127` сравнява `Integer` и примитивен `int`. Java прави unboxing и числовото сравнение е `127 == 127` → `true`. **Различен механизъм, същият резултат.**
8. `status == otherStatus` е `false`: литералът `"NEW"` е интерниран, а изричният `new String("NEW")` създава друг екземпляр. `status.equals(otherStatus)` е `true`, защото `String` override-ва `equals()` за сравнение по съдържание.
9. `Integer.MAX_VALUE + 1` е събиране в `int`. След overflow получаваме `-2147483648`, което едва след това се разширява до `long`; типът на целевата променлива **не предпазва** операцията. За събиране в `long` е необходим предварителен cast на операнд, например `(long) Integer.MAX_VALUE + 1`.
10. `marker += 1` пресмята `127 + 1` като `int 128` и след това извършва implicit narrowing към `byte`, което дава `-128`. Това **не е** `int` overflow. При обикновено `marker = marker + 1` за неконстантен израз би бил нужен explicit cast.

### Особеността при postfix и immutable wrapper-и

```java
Integer first = 127;
Integer second = first;

first++;

System.out.println(first);         // 128
System.out.println(second);        // 127
System.out.println(first == second); // false

Integer current = 7;
Integer before = current++;
System.out.println(before);        // 7
System.out.println(current);       // 8
```

В първата част `second` продължава да сочи стария immutable обект за `127`. Инкрементът не го изменя: `first` получава друга референция към wrapper за `128`. Във втората част postfix изразът връща **старата числова стойност**, която при присвояването към `before` се боксира. Тези семантики са отделни от механизмите за идентичност в cache-а.

## Професионален отговор за интервю или code review

> Java използва pass-by-value за всички аргументи. При reference тип параметърът получава копие на референцията. Затова промяна в споделен mutable `StringBuilder` се вижда през оригиналната референция, докато локално пренасочване на параметър не се вижда извън метода. `String` и `Integer` са immutable. По-конкретно, `Integer++` разопакова стойността, извършва аритметиката и боксира резултата, след което присвоява новата референтна стойност на локалната променлива — не изменя стария обект. При wrapper сравнения с `==` първо трябва да установим статичните типове: два `Integer` обекта се сравняват по идентичност, но `Integer` срещу `int` се сравняват числово след unboxing. Накрая, типът на аритметиката е независим от типа на променливата, която приема резултата, затова са възможни overflow и narrowing.

## Свързан текст за слушане

Представи си Java метод, който получава четири аргумента: число, текстов статус, обект StringBuilder за audit запис и Integer за брой опити. Всички аргументи се предават по стойност, но това има различни практически последствия в зависимост от типа.

Примитивното число се копира. Инкрементът му вътре в метода не променя оригиналната променлива. При String параметъра се копира референцията, но самият String е immutable. Извикването на concat връща резултат с новото съдържание, а присвояването сменя само локалната референция. StringBuilder работи другояче. Append изменя съществуващия обект, затова оригиналната референция извън метода вижда новото му съдържание. Дори ако след това локалният параметър започне да сочи нов StringBuilder, старият изменен обект остава видим за извикващия код.

С Integer инкрементът изисква специално внимание. Integer не е mutable контейнер за число. При плюс плюс Java автоматично извлича примитивната int стойност, увеличава я, после боксира резултата и присвоява получената референция на локалната променлива. Старият Integer обект не се изменя. Това ни дава същия модел на локално пренасочване като при String, макар че синтаксисът изглежда като обикновена числова промяна.

При сравненията трябва първо да проверим типовете. Когато сравняваме два Integer обекта с двойно равно, проверяваме дали референциите сочат към един и същ обект. Кеширането на малки стойности може да направи такова сравнение вярно за стойности като сто двадесет и седем. Когато сравняваме Integer и примитивен int, Java автоматично разопакова wrapper-а и сравнява числата. За String съдържанието използваме equals, защото new String създава различен екземпляр дори текстът да е същият.

Накрая, при математически изрази винаги определяме типа на операцията преди присвояването. Събиране на две int стойности се извършва в int и може да прелее, дори крайният резултат да се записва в long. При byte плюс равно първо се извършва аритметиката в int, а после резултатът се стеснява до byte. Правилният мисловен навик е във всеки ред да различаваме копиране, изменение на съществуващ обект, пренасочване на референция, автоматично boxing или unboxing, и числови преобразувания.

## Recall — без подсказване

1. При `Integer x = 127; Integer y = x; x++;` какви са стойностите на `x` и `y`, какво става с обектите и защо това **не е mutation**?
2. В метод, който получава `StringBuilder` и `Integer`, кои операции могат да променят обект, видим за caller-а? Добави по един пример за identity сравнение и за unboxing при числово сравнение.

## Свързани бележки

- [Values, References & Aliasing](01-values-references-aliasing.md)
- [Method Arguments & Pass-by-Value](02-method-arguments-pass-by-value.md)
- [String & StringBuilder](03-string-immutability-stringbuilder.md)
- [Object Identity, Equality & Null](04-object-identity-equality-null.md)
- [Numeric Promotions, Overflow & Compound Assignment](05-numeric-promotions-overflow-compound-assignment.md)
- [Autoboxing, Unboxing & Integer Caching](06-autoboxing-unboxing-integer-cache-null.md)

## Официални източници

- [Java Language Specification 25 — `15.14.2 Postfix Increment Operator ++](https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#jls-15.14.2)
- [Java Language Specification 25 — `5.1.7 Boxing Conversion](https://docs.oracle.com/javase/specs/jls/se25/html/jls-5.html#jls-5.1.7)
- [Java Language Specification 25 — `5.1.8 Unboxing Conversion](https://docs.oracle.com/javase/specs/jls/se25/html/jls-5.html#jls-5.1.8)
- [Java Language Specification 25 — `15.21 Equality Operators](https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#jls-15.21)
- [Java 25 API — Integer](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/Integer.html)
