# P1 — Java 25: lossless `int → float` и капанът `cast → reverse cast`

> **По Gavin Bierman, [What’s happening with Project Amber?](https://www.youtube.com/watch?v=Go76-9Q51wU)** (Devoxx Belgium 2026, ~20–28 мин от предоставения SRT). Лекцията демонстрира *exact numeric conversion* като важен дизайн проблем. **Нашият код е стандартен Java 25 и не използва preview primitive patterns.** На [11 ноември 2026 г.](https://inside.java/2026/11/11/withdraw-primitive-patterns-preview/) е потвърдено, че Primitive Types in Patterns preview няма да присъства в JDK28. Математическият проблем и безопасното конвертиране остават реални.

## 1. Реален проблем — загуба на числова информация без очевидна грешка

Потребителски DTO съдържа `int`, но стар Java/FFI/data API приема `float`. Някой проверява, че `int -> float -> int` връща оригиналната стойност и обявява преобразуването за lossless.

```java
int id = Integer.MAX_VALUE;              // 2147483647
float floating = (float) id;             // 2147483648.0f, загуба на точност!
boolean naive = ((int) floating == id);  // true (заради saturating float -> int)
```

**Root cause:** Java `float` има 24-bit significand (включително скрития бит). Той **не може** точно да представи всеки `int` с 32 бита. Освен това Java преобразуването `float → int` **насища** стойността до `Integer.MAX_VALUE`, когато е над максимума. Така погрешният round-trip може да мине, въпреки че загубата е истинска.

### Работещо, ограничено по контракт решение

```java
static boolean exactIntToFloat(int value) {
    return (double) (float) value == (double) value;
}
```

Това работи **за точно този типове**: всяка 32-битова `int` стойност има точно представяне като `double`, а `float → double` е точно. Не е универсален рецепт за `long → double`, `double → float`, `BigDecimal → double` или валута/SQL `NUMBER`.

## 2. README → код → независимо доказателство

| Контракт | Source | JUnit |
| --- | --- | --- |
| Наивният round trip е невалидно доказателство | [NumericConversionExactnessLab.java](src/main/java/bg/hristomanov/education/dop/amber/NumericConversionExactnessLab.java) `misleadingRoundTrip` | [NumericConversionExactnessLabTest.java](src/test/java/bg/hristomanov/education/dop/amber/NumericConversionExactnessLabTest.java): `Integer.MAX_VALUE` |
| Проверка с double promotion и fail-closed conversion | `exactlyRepresentableAsFloat`, `requireExactFloat` | 2^24 boundary, `Integer.MIN_VALUE`, negative values, true/false cases |
| **Независим математически oracle** | Не използва `float` при изчисляване на очакваната стойност | 10 000 fixed-seed random integers, IEEE-754 битова точност |

Изпълнение:

```bash
mvn -pl java/language/data-oriented-programming \
  -Dtest=NumericConversionExactnessLabTest test
```

### Как работи независимият oracle

За `int` число с абсолютна стойност `a > 0` намираме позицията на най-старшия set бит: `exponent = floor(log2(a))`. В `binary32` има 24 significant bits. Ако `exponent >= 24`, точно представимите цели числа трябва да са кратни на `2^(exponent-23)`. Тестът проверява само ниските битове на `long abs`, **не извиква production алгоритъма и не прави `float` cast**.

Това е вътрешно независимо доказателство за `int → float`; не е „потвърден JIT performance benchmark“. Например `2^24 = 16_777_216` е точно представимо, но `2^24+1 = 16_777_217` не е. `Integer.MIN_VALUE` е точно представимо, но `Integer.MIN_VALUE+1` не е.

## 3. Кога да използваме подхода

| Проблем | Кога | Кога НЕ | Печалба | Цена / по-проста алтернатива |
| --- | --- | --- | --- | --- |
| Числово преобразуване `int→float` | конкретен ABI/API изисква `float` и е важна точността | произволен numeric conversion или фиксирани decimal финансови суми | fail-closed данни, no silent rounding | extra validation; запази `int`/`long` или използвай `BigDecimal` |
| Примитивни pattern previews | **само исторически анализ** на JDK23–27 | нов Java25 production код и JDK28 | разбиране на design lessons | withdrawn preview; използвай explicit checks |

За Oracle `NUMBER`/BigDecimal → `float`/double трябва отделна domain-specific политика за precision/scale, за rounding mode и null handling. **Не заменяй `BigDecimal` с `float` в парични изчисления.**

## 4. Senior checkpoint

1. Защо `((int)(float)Integer.MAX_VALUE) == Integer.MAX_VALUE` е `true`, въпреки че floating value е 2147483648?
2. Какъв независим oracle можеш да напишеш без самото проверявано `float` преобразуване?
3. Ще работи ли същото `(double)` сравнение универсално за `long → float`? Защо не?
4. Кога `rounding` е **допустим и документиран**, а не грешка?

**Край:** идеята от оттеглен preview остава полезна за Java 25 DTO/FFI числова безопасност. Не изграждаме лаборатория върху syntax, който е оттеглен в JDK28.

## Източници

- [What’s happening with Project Amber? — Gavin Bierman](https://www.youtube.com/watch?v=Go76-9Q51wU), supplied SRT.
- [JLS §5.1.2 Widening Primitive Conversion](https://docs.oracle.com/javase/specs/jls/se25/html/jls-5.html#jls-5.1.2).
- [JLS §5.1.3 Narrowing Primitive Conversion](https://docs.oracle.com/javase/specs/jls/se25/html/jls-5.html#jls-5.1.3).
- [JEP 507 — Primitive Types in Patterns (JDK 25 preview)](https://openjdk.org/jeps/507).
- [Inside Java — Withdrawing Primitive Patterns Preview (11.11.2026)](https://inside.java/2026/11/11/withdraw-primitive-patterns-preview/).
